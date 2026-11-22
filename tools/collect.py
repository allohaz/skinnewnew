"""Собирает цены Skinport и Market.CSGO и дописывает их в файлы истории.
Запускается GitHub Actions раз в 3 часа (см. .github/workflows/history.yml).
Файлы: <папка>/h/<xx>.json, где xx — первые 2 символа md5 от названия скина.
Формат: { "название скина": [[минуты_unix, цена_skinport, цена_csgo], ...] }
"""
import hashlib
import json
import os
import sys
import time

import requests

OUT = sys.argv[1] if len(sys.argv) > 1 else "data"
MAX_ITEMS = 8000        # сколько самых ходовых скинов хранить
FULL_DAYS = 10          # свежие точки храним все
KEEP_DAYS = 400         # старше — удаляем; между — по одной в сутки
HEAD = {"User-Agent": "SkinTracker-history/1.0", "Accept-Encoding": "br, gzip"}


def get(url, params=None):
    r = requests.get(url, params=params, headers=HEAD, timeout=120)
    r.raise_for_status()
    return r.json()


def skinport():
    out = {}
    try:
        for it in get("https://api.skinport.com/v1/items", {"app_id": 730, "currency": "USD"}):
            p = it.get("min_price")
            if p is not None:
                out[it["market_hash_name"]] = (float(p), int(it.get("quantity") or 0))
    except Exception as e:  # площадка могла не ответить — это не страшно
        print("skinport failed:", e)
    return out


def csgo():
    out = {}
    try:
        d = get("https://market.csgo.com/api/v2/prices/USD.json")
        for it in d.get("items", []):
            try:
                out[it["market_hash_name"]] = (float(it["price"]), int(it.get("volume") or 0))
            except Exception:
                pass
    except Exception as e:
        print("csgo failed:", e)
    return out


def shard(name):
    return hashlib.md5(name.encode("utf-8")).hexdigest()[:2]


def thin(points, now_min):
    full_from = now_min - FULL_DAYS * 1440
    drop_before = now_min - KEEP_DAYS * 1440
    res, last_day = [], None
    for p in points:
        if p[0] < drop_before:
            continue
        if p[0] >= full_from:
            res.append(p)
        else:
            d = p[0] // 1440
            if d != last_day:
                res.append(p)
                last_day = d
    return res


def main():
    sp, cs = skinport(), csgo()
    if not sp and not cs:
        print("нет данных ни с одной площадки — выходим")
        return
    names = set(sp) | set(cs)
    pop = {n: (sp.get(n, (0, 0))[1] + cs.get(n, (0, 0))[1]) for n in names}
    top = sorted(names, key=lambda n: -pop[n])[:MAX_ITEMS]
    now_min = int(time.time() // 60)
    os.makedirs(os.path.join(OUT, "h"), exist_ok=True)
    groups = {}
    for n in top:
        groups.setdefault(shard(n), []).append(n)
    for key, items in groups.items():
        path = os.path.join(OUT, "h", key + ".json")
        data = {}
        if os.path.exists(path):
            try:
                with open(path, encoding="utf-8") as f:
                    data = json.load(f)
            except Exception:
                data = {}
        for n in items:
            pts = data.get(n, [])
            pts.append([now_min, sp[n][0] if n in sp else None, cs[n][0] if n in cs else None])
            data[n] = thin(pts, now_min)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    with open(os.path.join(OUT, "updated.txt"), "w") as f:
        f.write(str(now_min))
    print("готово:", len(top), "скинов")


main()
