package com.caretrail.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Translates English text from the server into the chosen language using the backend's
 * Sarvam-backed /i18n/translate endpoint. Strings are batched, cached on the phone and
 * shown in English until the translation arrives (same behaviour as useTr on the web).
 */
@OptIn(FlowPreview::class)
class Translator(private val api: Api, private val store: Store, scope: CoroutineScope) {
    private val _lang = MutableStateFlow(store.lang)
    val lang: StateFlow<String> = _lang
    private val _cache = MutableStateFlow(if (store.lang == "en") emptyMap() else store.translations(store.lang))
    val cache: StateFlow<Map<String, String>> = _cache

    private val pending = LinkedHashSet<String>()
    private val inflight = HashSet<String>()
    private val kick = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    private val scope = scope

    init {
        scope.launch {
            kick.debounce(200).collect { flush() }
        }
    }

    fun setLang(code: String, explicit: Boolean = true) {
        if (!Langs.valid(code)) return
        store.lang = code
        if (explicit) store.langChosen = true
        synchronized(pending) { pending.clear(); inflight.clear() }
        _cache.value = if (code == "en") emptyMap() else store.translations(code)
        _lang.value = code
    }

    /** Returns the translation if known, otherwise the English text (and queues a fetch). */
    fun tr(text: String?, lang: String = _lang.value, cache: Map<String, String> = _cache.value): String {
        if (text.isNullOrEmpty()) return ""
        if (lang == "en") return text
        cache[text]?.let { return it }
        if (store.token != null) {
            val added = synchronized(pending) { if (text !in inflight && pending.add(text)) true else false }
            if (added) kick.tryEmit(Unit)
        }
        return text
    }

    private suspend fun flush() {
        val lang = _lang.value
        if (lang == "en") return
        val todo = synchronized(pending) {
            val l = pending.filter { it !in _cache.value && it !in inflight }
            pending.clear(); inflight.addAll(l); l
        }
        if (todo.isEmpty()) return
        for (chunk in todo.chunked(60)) {
            try {
                val res = api.translate(chunk, lang)
                if (_lang.value == lang) {
                    val next = _cache.value + res.translations
                    _cache.value = next
                    store.saveTranslations(lang, next)
                }
            } catch (_: Exception) {
                // Keep English; will retry next time the text appears.
            } finally {
                synchronized(pending) { inflight.removeAll(chunk.toSet()) }
            }
        }
    }
}
