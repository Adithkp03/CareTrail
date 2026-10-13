package com.caretrail.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.Langs

/** Language-aware text helpers for a screen. */
@Stable
class L(val lang: String, private val cache: Map<String, String>, private val vm: AppViewModel) {
    /** Static label (port of t()). */
    fun t(key: String): String = Langs.label(key, lang)
    /** Server/English text translated at runtime (port of useTr). */
    fun tr(text: String?): String = vm.translator.tr(text, lang, cache)
    fun care(m: Map<String, String>): String = Langs.care(m, lang)
}

@Composable
fun rememberL(vm: AppViewModel): L {
    val lang by vm.translator.lang.collectAsState()
    val cache by vm.translator.cache.collectAsState()
    return remember(lang, cache) { L(lang, cache, vm) }
}

@Composable
fun Page(
    modifier: Modifier = Modifier,
    title: String? = null,
    back: (() -> Unit)? = null,
    backLabel: String = "CareTrail",
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (back != null) {
            Text("← $backLabel", color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = back).padding(vertical = 8.dp))
        }
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = if (back != null) 4.dp else 12.dp, bottom = 4.dp))
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}

fun gap(dp: Int) = Modifier.height(dp.dp)
