package com.abugdn.wid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.AlertRule
import com.abugdn.wid.data.RULE_KINDS
import com.abugdn.wid.data.RuleCondition
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.describe
import com.abugdn.wid.data.holds
import com.abugdn.wid.repository

/** Regras de alerta da pessoa: "SE ... E ... → avisar". Avaliadas a cada sincronização (30 min). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.repository
    val rules by repo.rules.collectAsStateWithLifecycle()
    val radar by repo.radar.collectAsStateWithLifecycle()
    val feed by repo.feed.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Regras de alerta", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Text(
                    "Monte avisos do seu jeito: “se Ormuz entrar em alerta e o petróleo passar de 90, me avise”. " +
                        "Todas as condições precisam valer ao mesmo tempo. O Argos confere a cada 30 min e avisa quando a regra " +
                        "passa a valer (não repete enquanto continuar valendo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                FilledTonalButton(onClick = { creating = true }) { Text("＋ Nova regra") }
            }
            if (rules.isEmpty()) {
                item { Text("Nenhuma regra ainda.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp)) }
            }
            items(rules, key = { it.id }) { r ->
                val now = r.holds(feed, radar) { repo.translator.cached(it) }
                ArgosCard(
                    "🔔 ${r.name.uppercase()}",
                    alert = now,
                    titleExtra = { if (now) Text("  · valendo agora", style = MaterialTheme.typography.labelSmall, color = Alert) },
                ) {
                    Text("SE " + r.describe(radar), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = { repo.setRules(rules.filter { it.id != r.id }) }) { Text("Apagar") }
                }
            }
        }
    }
    if (creating) {
        RuleEditor(
            markets = radar?.markets?.items.orEmpty().map { it.id to it.name },
            onDismiss = { creating = false },
            onSave = { rule -> repo.setRules(rules + rule); creating = false },
        )
    }
}

@Composable
private fun RuleEditor(markets: List<Pair<String, String>>, onDismiss: () -> Unit, onSave: (AlertRule) -> Unit) {
    var name by remember { mutableStateOf("") }
    val conditions = remember { mutableStateListOf<RuleCondition>() }
    var kind by remember { mutableStateOf("tensao") }
    var region by remember { mutableStateOf("israel") }
    var market by remember { mutableStateOf(markets.firstOrNull()?.first ?: "brent") }
    var op by remember { mutableStateOf("acima") }
    var number by remember { mutableStateOf("60") }
    var word by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova regra") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome da regra") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (conditions.isNotEmpty()) {
                    Text("Condições (todas precisam valer):", style = MaterialTheme.typography.labelMedium, color = Accent, modifier = Modifier.padding(top = 10.dp))
                    conditions.forEachIndexed { i, c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((if (i > 0) "E " else "SE ") + c.describe(null), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { conditions.removeAt(i) }) { IconText("✕") }
                        }
                    }
                }
                if (conditions.size < 3) {
                    Text("Adicionar condição:", style = MaterialTheme.typography.labelMedium, color = Accent, modifier = Modifier.padding(top = 10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(RULE_KINDS.entries.toList()) { (k, label) -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) }) }
                    }
                    if (kind == "tensao" || kind == "radar") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            items(TAG_LABELS.entries.toList()) { (t, label) -> FilterChip(selected = region == t, onClick = { region = t }, label = { Text(label) }) }
                        }
                    }
                    if (kind == "mercado") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            items(markets) { (id, label) -> FilterChip(selected = market == id, onClick = { market = id }, label = { Text(label) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            listOf("acima", "abaixo").forEach { o -> FilterChip(selected = op == o, onClick = { op = o }, label = { Text(o) }) }
                        }
                    }
                    if (kind == "tensao" || kind == "mercado") {
                        OutlinedTextField(
                            number, { number = it },
                            label = { Text(if (kind == "tensao") "Tensão mínima (0 a 100)" else "Valor") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                    if (kind == "palavra") {
                        OutlinedTextField(word, { word = it }, label = { Text("Palavra ou nome") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    TextButton(onClick = {
                        val v = number.replace(',', '.').toDoubleOrNull() ?: 0.0
                        val c = when (kind) {
                            "tensao" -> RuleCondition("tensao", region = region, value = v)
                            "radar" -> RuleCondition("radar", region = region)
                            "mercado" -> RuleCondition("mercado", market = market, op = op, value = v)
                            else -> RuleCondition("palavra", word = word.trim()).takeIf { word.isNotBlank() }
                        }
                        if (c != null) conditions.add(c)
                    }) { Text("＋ Adicionar esta condição") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = conditions.isNotEmpty(),
                onClick = {
                    onSave(AlertRule("r" + System.currentTimeMillis(), name.ifBlank { "Minha regra" }.trim(), conditions.toList()))
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
