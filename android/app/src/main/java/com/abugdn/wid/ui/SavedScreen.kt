package com.abugdn.wid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.repository

private const val NO_FOLDER = "\u0000sem-pasta"

/** Aba Salvos: notícias salvas, dossiês e minhas previsões. [initialSub]: 0, 1 ou 2. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(onOpen: (String) -> Unit, initialSub: Int = 0) {
    var sub by rememberSaveable(initialSub) { mutableStateOf(initialSub) }
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("Salvos", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = sub) {
                Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("★ Notícias") })
                Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("🗂 Dossiês") })
                Tab(selected = sub == 2, onClick = { sub = 2 }, text = { Text("🎯 Previsões") })
            }
            when (sub) {
                1 -> DossiersTab(onOpen)
                2 -> PredictionsTab()
                else -> SavedNews(onOpen)
            }
        }
    }
}

/** Notícias salvas com a estrela: ficam no aparelho sem prazo, com texto completo, pasta e nota. */
@Composable
private fun SavedNews(onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val saved by repo.saved.collectAsStateWithLifecycle()
    val meta by repo.savedMeta.collectAsStateWithLifecycle()
    var folder by rememberSaveable { mutableStateOf<String?>(null) }

    val folders = meta.values.mapNotNull { it.folder }.distinct().sorted()
    val hasLoose = saved.any { meta[it.id]?.folder == null }
    val shown = saved.filter { c ->
        when (folder) {
            null -> true
            NO_FOLDER -> meta[c.id]?.folder == null
            else -> meta[c.id]?.folder == folder
        }
    }

    run {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (saved.isEmpty()) {
                item { Text("Toque na ★ dentro de uma notícia para guardá-la aqui.", modifier = Modifier.padding(24.dp)) }
            }
            if (folders.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text("Tudo") }) }
                        items(folders) { f ->
                            FilterChip(selected = folder == f, onClick = { folder = if (folder == f) null else f }, label = { Text("📁 $f") })
                        }
                        if (hasLoose) {
                            item {
                                FilterChip(
                                    selected = folder == NO_FOLDER,
                                    onClick = { folder = if (folder == NO_FOLDER) null else NO_FOLDER },
                                    label = { Text("Sem pasta") },
                                )
                            }
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { c ->
                ClusterRow(c, onOpen)
                val m = meta[c.id]
                if (m != null) {
                    Text(
                        listOfNotNull(m.folder?.let { "📁 $it" }, m.note?.let { "“$it”" }).joinToString("  "),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
