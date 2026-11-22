package com.skintracker.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skintracker.app.data.Catalogs
import com.skintracker.app.data.Found
import com.skintracker.app.data.Sales
import com.skintracker.app.data.Settings
import com.skintracker.app.data.Weapons
import com.skintracker.app.ui.baseOf
import com.skintracker.app.work.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Сообщение внизу экрана; action — кнопка вроде «Отменить». */
class UiMsg(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repo

    val state = repo.state
    val catalogs: StateFlow<Catalogs> = repo.catalogs
    val previewSales: StateFlow<Map<String, Sales>> = repo.previewSales

    val history: StateFlow<Map<String, List<com.skintracker.app.data.Snap>>> = repo.history

    fun loadHistory(name: String) {
        viewModelScope.launch {
            val err = try {
                repo.loadHistory(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (err != null) message.value = UiMsg(err)
        }
    }

    val refreshing = MutableStateFlow(false)
    val message = MutableStateFlow<UiMsg?>(null)
    val previewLoading = MutableStateFlow(false)

    /** Все названия, подходящие под запрос (самые популярные сверху). */
    private val _results = MutableStateFlow<List<String>>(emptyList())
    val results: StateFlow<List<String>> = _results

    /** Картинки скинов: основа названия (без износа) → ссылка. */
    private val _icons = MutableStateFlow<Map<String, String>>(repo.cachedIcons())
    val icons: StateFlow<Map<String, String>> = _icons

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    /** Для какого запроса поиск уже завершён (чтобы честно писать «ничего не найдено»). */
    private val _doneFor = MutableStateFlow("")
    val doneFor: StateFlow<String> = _doneFor

    private var searchJob: Job? = null
    private var iconJob: Job? = null
    private var iconText = ""
    private var iconStart = 0

    init {
        warmCatalog() // список скинов грузим сразу при запуске, а не когда откроют поиск
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val err = try {
                repo.refresh(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message
            }
            refreshing.value = false
            if (err != null) message.value = UiMsg(err)
        }
    }

    fun warmCatalog() {
        viewModelScope.launch {
            try {
                repo.loadCatalogs(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    fun search(q: String) {
        searchJob?.cancel()
        val query = q.trim()
        if (query.isEmpty()) {
            _results.value = emptyList()
            _searching.value = false
            _doneFor.value = ""
            return
        }
        val r = Weapons.resolve(query)
        if (r == null) {
            // запрос слишком короткий и подходит к нескольким видам оружия — ждём выбора из подсказок
            _results.value = emptyList()
            _searching.value = false
            _doneFor.value = ""
            return
        }
        searchJob = viewModelScope.launch {
            _searching.value = true
            delay(200) // ждём, пока человек допечатает слово
            try {
                if (repo.catalogs.value.isEmpty()) {
                    val err = repo.loadCatalogs(false)
                    if (err != null && repo.catalogs.value.isEmpty()) message.value = UiMsg(err)
                }
                var names = repo.searchLocal(r)
                if (names.isEmpty() && repo.catalogs.value.isEmpty()) {
                    // базы площадок недоступны — ищем через Steam
                    val page = repo.steamPage(r.text, 0)
                    addIcons(page)
                    names = page.map { it.name }
                }
                _results.value = names
                _doneFor.value = query
                // картинки берём из Steam (по одной странице за раз)
                iconText = r.text
                iconStart = 0
                loadIcons()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = UiMsg(e.message ?: "Не удалось выполнить поиск")
                _results.value = emptyList()
            }
            _searching.value = false
        }
    }

    private suspend fun addIcons(page: List<Found>) {
        val add = HashMap<String, String>()
        for (f in page) {
            val icon = f.icon ?: continue
            val b = baseOf(f.name)
            if (!_icons.value.containsKey(b)) add[b] = icon
        }
        if (add.isNotEmpty()) {
            _icons.value = _icons.value + add
            repo.saveIcons(_icons.value)
        }
    }

    private fun loadIcons() {
        iconJob?.cancel()
        val text = iconText
        val start = iconStart
        if (text.isEmpty()) return
        iconJob = viewModelScope.launch {
            val page = repo.steamPage(text, start)
            addIcons(page)
        }
    }

    /** Пользователь листает дальше — подгружаем следующую порцию картинок. */
    fun moreIcons() {
        iconStart += 40
        loadIcons()
    }

    fun loadPreviewSales(names: List<String>) {
        viewModelScope.launch {
            previewLoading.value = true
            val err = try {
                repo.loadPreviewSales(names)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message
            }
            previewLoading.value = false
            if (err != null) message.value = UiMsg(err)
        }
    }

    fun add(f: Found) {
        viewModelScope.launch {
            repo.add(f)
            message.value = UiMsg("Добавлено: ${f.name}", "Отменить") {
                viewModelScope.launch { repo.remove(f.name) }
            }
            try {
                repo.refresh(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    fun remove(name: String) {
        viewModelScope.launch {
            val removed = repo.remove(name)
            if (removed != null) {
                message.value = UiMsg("Скин убран из списка", "Отменить") {
                    viewModelScope.launch { repo.restore(removed) }
                }
            }
        }
    }

    fun setPosition(name: String, buy: Double?, sell: Double?, buyTarget: Double?) {
        viewModelScope.launch { repo.setPosition(name, buy, sell, buyTarget) }
    }

    fun saveSettings(s: Settings) {
        viewModelScope.launch {
            repo.setSettings(s)
            Scheduler.schedule(getApplication<Application>(), s.everyHours, replace = true)
        }
    }

    /** Сохраняет выбор площадок и сразу обновляет цены. */
    fun savePlatforms(s: Settings) {
        viewModelScope.launch {
            repo.setSettings(s)
            try {
                repo.refresh(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    fun sold(name: String, got: Double) {
        viewModelScope.launch {
            repo.sold(name, got)
            message.value = UiMsg("Сделка записана")
        }
    }

    fun removeTrade(at: Long) {
        viewModelScope.launch { repo.removeTrade(at) }
    }

    fun clearMessage() {
        message.value = null
    }
}
