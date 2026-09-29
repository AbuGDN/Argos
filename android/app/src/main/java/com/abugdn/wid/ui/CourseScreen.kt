package com.abugdn.wid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abugdn.wid.data.COURSE
import com.abugdn.wid.data.Lesson
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.repository

private const val DONE_KEY = "course_done"

/** Curso rápido: lista de lições; cada lição abre página por página e fica marcada como lida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(onBack: () -> Unit, onRegion: (String) -> Unit) {
    val prefs = LocalContext.current.repository.storage.prefs
    var done by remember { mutableStateOf(prefs.getStringSet(DONE_KEY, emptySet())!!.toSet()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val lesson = COURSE.firstOrNull { it.id == openId }

    if (lesson != null) {
        BackHandler { openId = null }
        LessonView(
            lesson,
            onClose = { openId = null },
            onFinish = {
                done = done + lesson.id
                prefs.edit().putStringSet(DONE_KEY, done).apply()
                openId = COURSE.getOrNull(COURSE.indexOf(lesson) + 1)?.takeIf { it.id !in done }?.id
            },
            onRegion = onRegion,
        )
        return
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Curso rápido", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Text(
                    "Lições curtas para entender as guerras que o Argos acompanha. Os textos vão até 2025; o que veio depois está nas notícias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                val read = COURSE.count { it.id in done }
                Text("$read de ${COURSE.size} lições lidas", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { read.toFloat() / COURSE.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = Accent,
                )
            }
            items(COURSE, key = { it.id }) { l ->
                val isDone = l.id in done
                ArgosCard(
                    "${l.icon}  ${l.minutes} MIN" + if (isDone) " · ✓ LIDA" else "",
                    titleColor = if (isDone) Ash else null,
                    onClick = { openId = l.id },
                ) {
                    Text(l.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        l.pages.first().take(120).substringBeforeLast(' ') + "…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonView(lesson: Lesson, onClose: () -> Unit, onFinish: () -> Unit, onRegion: (String) -> Unit) {
    var page by rememberSaveable(lesson.id) { mutableIntStateOf(0) }
    val last = page == lesson.pages.lastIndex
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(lesson.title, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            LinearProgressIndicator(
                progress = { (page + 1).toFloat() / lesson.pages.size },
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
            )
            Text(
                "${page + 1} de ${lesson.pages.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                lesson.pages[page],
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(top = 20.dp).clickable(enabled = !last) { page++ },
            )
            if (last && lesson.tags.isNotEmpty()) {
                Text("Acompanhe no Argos:", style = MaterialTheme.typography.labelMedium, color = Accent, modifier = Modifier.padding(bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lesson.tags.forEach { tag -> AssistChip(onClick = { onRegion(tag) }, label = { Text(TAG_LABELS[tag] ?: tag) }) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (page > 0) page-- else onClose() }) { Text(if (page > 0) "Voltar" else "Sair") }
                Button(onClick = { if (last) onFinish() else page++ }) { Text(if (last) "Concluir ✓" else "Próxima") }
            }
        }
    }
}
