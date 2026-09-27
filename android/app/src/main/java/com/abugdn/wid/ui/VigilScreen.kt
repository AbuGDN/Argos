package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.VIGIL_KINDS
import com.abugdn.wid.data.VigilEvent
import com.abugdn.wid.repository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val vigilDay = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale("pt", "BR")).withZone(ZoneId.systemDefault())
private val vigilTime = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** Registro de vigília: todos os alertas que o Argos viu, com data e hora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VigilScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onRegion: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val log by repo.vigil.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = log.filter { kind == null || it.kind == kind }
    val byDay = shown.groupBy { vigilDay.format(Instant.ofEpochMilli(it.time)) }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Registro de vigília", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "Cada alerta fica guardado aqui com data e hora: urgentes, altas incomuns, números divergentes e tensão crítica. " +
                        "O registro começa quando o app é atualizado e guarda os últimos 500.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 8.dp),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("Todos (${log.size})") }) }
                    items(VIGIL_KINDS.entries.toList()) { (k, label) ->
                        val n = log.count { it.kind == k }
                        FilterChip(selected = kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text("$label ($n)") })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        "Nenhum alerta registrado ainda. Eles aparecem aqui conforme o feed atualiza.",
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            byDay.forEach { (day, events) ->
                item(key = "day-$day") {
                    Text(
                        day.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                    )
                }
                items(events, key = { it.key }) { e ->
                    VigilRow(e, onOpen, onRegion)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun VigilRow(e: VigilEvent, onOpen: (String) -> Unit, onRegion: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val target: (() -> Unit)? = when {
        e.clusterId != null && repo.cluster(e.clusterId) != null -> { { onOpen(e.clusterId) } }
        e.region != null -> { { onRegion(e.region) } }
        else -> null
    }
    val alert = e.kind == "urgent" || e.kind == "figures" || e.kind == "tension" || e.kind == "truce" || e.kind == "internet" || e.kind == "airspace"
    Column(
        Modifier.fillMaxWidth()
            .let { if (target != null) it.clickable(onClick = target) else it }
            .padding(16.dp, 10.dp),
    ) {
        Text(
            "${vigilTime.format(Instant.ofEpochMilli(e.time))} · ${VIGIL_KINDS[e.kind] ?: e.kind}",
            style = MaterialTheme.typography.labelSmall,
            color = if (alert) Alert else Accent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            repo.translator.display(e.title, e.lang),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (e.detail.isNotBlank()) {
            Text(e.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
