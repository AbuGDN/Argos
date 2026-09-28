package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abugdn.wid.data.SectionStatus

/**
 * Cartão padrão do Argos. Todo cartão tem o mesmo cabeçalho:
 * título (ouro; vermelho em alerta) · ⓘ à direita com a explicação e a fonte ·
 * linha "fonte · atualizado há X" embaixo do título.
 *
 * [collapsedSummary] não nulo deixa o cartão recolhível: fechado, mostra só o título e o resumo.
 */
@Composable
fun ArgosCard(
    title: String,
    modifier: Modifier = Modifier,
    alert: Boolean = false,
    titleColor: Color? = null,
    source: String? = null,
    updated: String? = null,
    status: SectionStatus? = null,
    info: String? = null,
    collapsedSummary: String? = null,
    startExpanded: Boolean = true,
    titleExtra: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val collapsible = collapsedSummary != null
    var expanded by rememberSaveable(title, startExpanded) { mutableStateOf(startExpanded || !collapsible) }
    var showInfo by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp).breathingBorder(alert)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().let { if (collapsible) it.clickable { expanded = !expanded } else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = titleColor ?: if (alert) Alert else Accent,
                        fontWeight = FontWeight.Bold,
                    )
                    titleExtra?.invoke()
                }
                if (info != null) {
                    Text(
                        "ⓘ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showInfo = true }.padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
                if (collapsible) {
                    Text(
                        if (expanded) "▾" else "▸",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else if (info == null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                }
            }
            Column(Modifier.padding(end = 8.dp)) {
                val failed = status != null && !status.ok
                val meta = buildList {
                    if (!source.isNullOrBlank()) add(source)
                    if (!updated.isNullOrBlank()) add("atualizado ${relativeTime(updated)}")
                    if (failed) add("⚠ a última coleta falhou; mostrando o dado anterior")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) Alert else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded) {
                    content()
                } else if (!collapsedSummary.isNullOrBlank()) {
                    Text(
                        collapsedSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
    if (showInfo && info != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title.trimStart { !it.isLetterOrDigit() }.lowercase().replaceFirstChar { it.uppercase() }) },
            text = { Text(info, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Entendi") } },
        )
    }
}
