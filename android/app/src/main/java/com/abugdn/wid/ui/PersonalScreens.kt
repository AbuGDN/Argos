package com.abugdn.wid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.Dossier
import com.abugdn.wid.data.Prediction
import com.abugdn.wid.data.score
import com.abugdn.wid.data.brier
import com.abugdn.wid.data.calibration
import com.abugdn.wid.repository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt", "BR"))
private fun dayOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)

// ---------------------------------------------------------------------------
// Dossiês
// ---------------------------------------------------------------------------

/** Lista de dossiês; tocar abre a linha do tempo. */
@Composable
fun DossiersTab(onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val dossiers by repo.dossiers.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    val open = dossiers.firstOrNull { it.id == openId }
    if (open != null) {
        BackHandler { openId = null }
        DossierDetail(open, onBack = { openId = null }, onOpen = onOpen)
        return
    }
    if (creating) DossierDialog(null, onDismiss = { creating = false }) { title, terms ->
        repo.createDossier(title, terms)
        creating = false
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "Um dossiê acompanha um assunto: a cada sincronização o Argos junta as notícias novas que citam os termos " +
                    "e monta uma linha do tempo, que fica guardada no celular mesmo depois que a notícia sai do feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { creating = true }, modifier = Modifier.padding(vertical = 12.dp)) { Text("＋ Novo dossiê") }
        }
        if (dossiers.isEmpty()) {
            item { Text("Nenhum dossiê ainda. Exemplos: “Programa nuclear do Irã” (Irã, nuclear, Fordow, urânio), “Reféns” (reféns, hostages).", style = MaterialTheme.typography.bodyMedium) }
        }
        items(dossiers, key = { it.id }) { d ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { openId = d.id },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("🗂 ${d.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${d.entries.size} notícias · termos: ${d.terms.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    d.entries.firstOrNull()?.let { e ->
                        Text(
                            "Última: " + repo.translator.display(e.title, e.lang),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DossierDetail(d: Dossier, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.repository
    var editing by remember { mutableStateOf(false) }
    var notes by rememberSaveable(d.id) { mutableStateOf(d.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (editing) DossierDialog(d, onDismiss = { editing = false }) { title, terms ->
        repo.updateDossier(d.copy(title = title.trim(), terms = terms))
        editing = false
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar o dossiê?") },
            text = { Text("A linha do tempo e as notas de “${d.title}” somem do celular.") },
            confirmButton = { TextButton(onClick = { repo.deleteDossier(d.id); onBack() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
    // Agrupa a linha do tempo por dia.
    val byDay = d.entries.groupBy { dayOf(it.time) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← Dossiês") }
            Text("🗂 ${d.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Termos: ${d.terms.joinToString(", ")} · desde ${dayOf(d.created)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = { editing = true }) { Text("Editar") }
                TextButton(onClick = { confirmDelete = true }) { Text("Apagar") }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Suas notas") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                minLines = 2,
            )
            if (notes != d.notes) {
                TextButton(onClick = { repo.updateDossier(d.copy(notes = notes)) }) { Text("Salvar notas") }
            }
            Text(
                "LINHA DO TEMPO · ${d.entries.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (d.entries.isEmpty()) {
                Text("Nenhuma notícia ainda. As próximas que citarem os termos aparecem aqui.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
        byDay.forEach { (day, entries) ->
            item(key = "day-$day") {
                Text(day, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            }
            items(entries, key = { "e-" + it.clusterId }) { e ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable {
                            // Ainda no feed/salvos: abre a notícia; senão, o link original.
                            if (repo.cluster(e.clusterId) != null) onOpen(e.clusterId) else if (e.url.isNotBlank()) openUrl(context, e.url)
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Text(repo.translator.display(e.title, e.lang), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${clockTime(Instant.ofEpochMilli(e.time).toString())} · ${e.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/** Criar ou editar: título e termos separados por vírgula. */
@Composable
private fun DossierDialog(current: Dossier?, onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var title by remember { mutableStateOf(current?.title.orEmpty()) }
    var terms by remember { mutableStateOf(current?.terms?.joinToString(", ").orEmpty()) }
    val list = terms.split(',').map { it.trim() }.filter { it.length >= 2 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "Novo dossiê" else "Editar dossiê") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Assunto") }, singleLine = true)
                OutlinedTextField(
                    value = terms,
                    onValueChange = { terms = it },
                    label = { Text("Termos, separados por vírgula") },
                    placeholder = { Text("Irã, nuclear, Fordow, urânio") },
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Vale em português, inglês, hebraico ou árabe. Casa com o começo da palavra: “nuclear” acha “nucleares”.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && list.isNotEmpty(), onClick = { onSave(title, list) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------
// Minhas previsões
// ---------------------------------------------------------------------------

@Composable
fun PredictionsTab() {
    val repo = LocalContext.current.repository
    val predictions by repo.predictions.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    if (adding) PredictionDialog(onDismiss = { adding = false }) { text, deadline, confidence ->
        repo.addPrediction(text, deadline, confidence)
        adding = false
    }
    val today = LocalDate.now()
    val due = predictions.filter { it.due(today) }
    val pending = predictions.filter { it.hit == null && !it.due(today) }.sortedBy { it.deadline }
    val done = predictions.filter { it.hit != null }.sortedByDescending { it.deadline }
    val s = score(predictions)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("🎯 SEU PLACAR", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold)
                    if (s.total == 0) {
                        Text("Registre um palpite sobre as guerras. Na data, o Argos pergunta se você acertou.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("${s.hits} de ${s.total} · ${s.percent}% de acerto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val conf = done.map { it.confidence }.average().toInt()
                        Text(
                            "Certeza média nos conferidos: $conf%. " + when {
                                s.percent + 10 < conf -> "Você anda confiante demais."
                                s.percent > conf + 10 -> "Você acerta mais do que imagina."
                                else -> "Sua certeza bate com seus acertos."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        brier(predictions)?.let { b ->
                            Text(
                                "Nota de calibração (Brier): ${"%.2f".format(java.util.Locale("pt", "BR"), b)} · " + when {
                                    b <= 0.12 -> "ótima"
                                    b <= 0.2 -> "boa"
                                    b < 0.25 -> "razoável"
                                    else -> "pior que chutar 50%"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                "Quanto menor, melhor: 0 é perfeito e 0,25 é o que dá chutar sempre 50%. Leva em conta a certeza de cada palpite.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            calibration(predictions).forEach { (label, hits, total) ->
                                Text(
                                    "Certeza $label: acertou $hits de $total (${hits * 100 / total}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            FilledTonalButton(onClick = { adding = true }, modifier = Modifier.padding(vertical = 12.dp)) { Text("＋ Nova previsão") }
        }
        if (due.isNotEmpty()) {
            item { SectionLabel("PARA CONFERIR AGORA", alert = true) }
            items(due, key = { it.id }) { p -> PredictionRow(p, askResult = true) }
        }
        if (pending.isNotEmpty()) {
            item { SectionLabel("EM ABERTO") }
            items(pending, key = { it.id }) { p -> PredictionRow(p, askResult = false) }
        }
        if (done.isNotEmpty()) {
            item { SectionLabel("CONFERIDAS") }
            items(done, key = { it.id }) { p -> PredictionRow(p, askResult = false) }
        }
    }
}

@Composable
private fun SectionLabel(text: String, alert: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (alert) Alert else Accent,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PredictionRow(p: Prediction, askResult: Boolean) {
    val repo = LocalContext.current.repository
    val deadline = runCatching { LocalDate.parse(p.deadline).format(dateFmt) }.getOrDefault(p.deadline)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            (when (p.hit) { true -> "✅ "; false -> "❌ "; null -> "" }) + p.text,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = if (p.hit == null) FontStyle.Normal else FontStyle.Italic,
        )
        Text(
            "até $deadline · certeza ${p.confidence}% · feita em ${dayOf(p.created)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (askResult) {
                OutlinedButton(onClick = { repo.resolvePrediction(p.id, true) }, modifier = Modifier.padding(end = 8.dp)) { Text("Acertei") }
                OutlinedButton(onClick = { repo.resolvePrediction(p.id, false) }, modifier = Modifier.padding(end = 8.dp)) { Text("Errei") }
            } else if (p.hit != null) {
                TextButton(onClick = { repo.resolvePrediction(p.id, null) }) { Text("Reabrir") }
            }
            TextButton(onClick = { repo.deletePrediction(p.id) }) { Text("Apagar") }
        }
    }
    HorizontalDivider()
}

private val DEADLINES = listOf("1 semana" to 7L, "1 mês" to 30L, "3 meses" to 91L, "6 meses" to 182L, "1 ano" to 365L)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PredictionDialog(onDismiss: () -> Unit, onSave: (String, LocalDate, Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var days by remember { mutableLongStateOf(30L) }
    var confidence by remember { mutableFloatStateOf(70f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova previsão") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("O que vai acontecer?") },
                    placeholder = { Text("Cessar-fogo entre Rússia e Ucrânia") },
                    minLines = 2,
                )
                Text("Até quando", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DEADLINES.forEach { (label, d) ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(label) })
                    }
                }
                Text(
                    "Certeza: ${confidence.toInt()}% · data ${LocalDate.now().plusDays(days).format(dateFmt)}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Slider(value = confidence, onValueChange = { confidence = it }, valueRange = 50f..100f, steps = 9)
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text, LocalDate.now().plusDays(days), confidence.toInt()) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
