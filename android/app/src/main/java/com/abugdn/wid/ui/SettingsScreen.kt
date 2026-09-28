package com.abugdn.wid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.Settings
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.ThemeMode
import com.abugdn.wid.repository
import com.abugdn.wid.sync.DigestWorker
import com.abugdn.wid.widget.CompactWidgetReceiver
import com.abugdn.wid.widget.RegionWidgetReceiver
import com.abugdn.wid.widget.TopWidgetReceiver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = context.repository.settings
    val s by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var widgetMsg by remember { mutableStateOf<String?>(null) }

    fun update(reschedule: Boolean = false, t: (Settings) -> Settings) {
        store.update(t)
        if (reschedule) DigestWorker.schedule(context)
    }

    fun pin(request: suspend GlanceAppWidgetManager.() -> Boolean) = scope.launch {
        val ok = runCatching { GlanceAppWidgetManager(context).request() }.getOrDefault(false)
        widgetMsg = if (ok) null else "Seu launcher não aceita adicionar pelo app; use a lista de widgets da tela inicial."
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Section("Notificações")
            Toggle("Urgentes", "Muitos veículos cobrindo a mesma história em pouco tempo", s.notifyUrgent) {
                update { st -> st.copy(notifyUrgent = it) }
            }
            Toggle("Radar", "Apagão de internet ou espaço aéreo fechado numa região", s.notifyRadar) {
                update { st -> st.copy(notifyRadar = it) }
            }
            Toggle("Principal do dia", "Quando a principal muda (no máximo a cada 4 h)", s.notifyTop) {
                update { st -> st.copy(notifyTop = it) }
            }
            Toggle("Resumo diário", "As 3 principais das últimas 24 h, às ${s.digestHour}h", s.dailyDigest) {
                update(reschedule = true) { st -> st.copy(dailyDigest = it) }
            }
            if (s.dailyDigest) {
                Chips(listOf(6, 7, 8, 9, 12, 18, 21), selected = { it == s.digestHour }, label = { "${it}h" }) {
                    update(reschedule = true) { st -> st.copy(digestHour = it) }
                }
            }
            Toggle("Boletim semanal", "Domingo, junto do resumo diário: as 5 principais dos últimos 7 dias e o boletim em imagem para compartilhar", s.weeklyDigest) {
                update(reschedule = true) { st -> st.copy(weeklyDigest = it) }
            }
            Toggle(
                "Não perturbe à noite",
                "Sem notificações das ${s.quietStart}h às ${s.quietEnd}h (o que surgir entra no resumo)",
                s.quietHours,
            ) { update { st -> st.copy(quietHours = it) } }

            Section("Regiões das notificações")
            Text(
                if (s.regions.isEmpty()) "Todas as regiões. Toque para limitar." else "Só as regiões marcadas geram notificação.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                modifier = Modifier.padding(16.dp, 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TAG_LABELS.forEach { (tag, label) ->
                    FilterChip(
                        selected = tag in s.regions,
                        onClick = {
                            update { st -> st.copy(regions = if (tag in st.regions) st.regions - tag else st.regions + tag) }
                        },
                        label = { Text(label) },
                    )
                }
            }

            Section("Palavras vigiadas")
            Text(
                "Notifica sempre que uma notícia citar um destes termos (em qualquer região, respeitando o não perturbe).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            var newWord by remember { mutableStateOf("") }
            fun addWord() {
                val w = newWord.trim()
                if (w.isNotEmpty()) update { st -> st.copy(watchWords = st.watchWords + w) }
                newWord = ""
            }
            Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    placeholder = { Text("ex.: Hezbollah, Rafah, Houthi") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addWord() }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { addWord() }) { Text("Adicionar") }
            }
            if (s.watchWords.isNotEmpty()) {
                FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.watchWords.sorted().forEach { word ->
                        InputChip(
                            selected = true,
                            onClick = { update { st -> st.copy(watchWords = st.watchWords - word) } },
                            label = { Text(word) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remover $word") },
                        )
                    }
                }
            }

            Section("Veículos")
            Text(
                "Toque para alternar: normal → ★ preferido (dá o título e sobe no ranking) → oculto (some do app).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val sources = remember { context.repository.knownSources() }
            FlowRow(modifier = Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { name ->
                    val preferred = name in s.preferredSources
                    val hidden = name in s.hiddenSources
                    FilterChip(
                        selected = preferred || hidden,
                        onClick = {
                            update { st ->
                                when {
                                    name in st.preferredSources -> st.copy(
                                        preferredSources = st.preferredSources - name,
                                        hiddenSources = st.hiddenSources + name,
                                    )
                                    name in st.hiddenSources -> st.copy(hiddenSources = st.hiddenSources - name)
                                    else -> st.copy(preferredSources = st.preferredSources + name)
                                }
                            }
                        },
                        label = {
                            Text(
                                when {
                                    preferred -> "★ $name"
                                    hidden -> "✕ $name"
                                    else -> name
                                },
                                textDecoration = if (hidden) TextDecoration.LineThrough else null,
                            )
                        },
                    )
                }
            }
            if (sources.isEmpty()) {
                Text("Carregue as notícias primeiro.", modifier = Modifier.padding(horizontal = 16.dp))
            }

            Section("Economia de dados")
            Toggle(
                "Economia de dados",
                "Sem imagens; textos completos antecipados e o modelo de tradução só no Wi-Fi",
                s.dataSaver,
            ) { update { st -> st.copy(dataSaver = it) } }

            Section("Tela Hoje")
            Toggle("Faixa de manchetes", "Manchetes urgentes rolando no topo da tela Hoje", s.showTicker) {
                update { st -> st.copy(showTicker = it) }
            }
            Section("Imagens sensíveis")
            Toggle(
                "Borrar imagens sensíveis",
                "Fotos de notícias com mortos ou feridos aparecem borradas até você tocar",
                s.blurSensitive,
            ) { update { st -> st.copy(blurSensitive = it) } }

            Section("Aparência")
            Chips(ThemeMode.entries, selected = { it == s.theme }, label = {
                when (it) {
                    ThemeMode.SYSTEM -> "Igual ao sistema"
                    ThemeMode.LIGHT -> "Claro"
                    ThemeMode.DARK -> "Escuro (padrão)"
                    ThemeMode.AMOLED -> "Preto (AMOLED)"
                }
            }) { update { st -> st.copy(theme = it) } }
            Text("Tamanho do texto", modifier = Modifier.padding(16.dp, 8.dp))
            Chips(listOf(0.9f, 1f, 1.15f, 1.3f), selected = { it == s.textScale }, label = {
                when (it) {
                    0.9f -> "Pequeno"
                    1f -> "Normal"
                    1.15f -> "Grande"
                    else -> "Enorme"
                }
            }) { update { st -> st.copy(textScale = it) } }

            Section("Versão do app")
            val updater = context.repository.updater
            var checkMsg by remember { mutableStateOf<String?>(null) }
            Text("Instalada: ${updater.installedName}", modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        checkMsg = "Procurando…"
                        checkMsg = updater.check(force = true).fold(
                            { if (it == null) "Você já está na versão mais recente." else null },
                            { "Sem conexão com o GitHub." },
                        )
                    }
                },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) { Text("Procurar atualização") }
            checkMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
            UpdateBanner(Modifier.padding(16.dp, 8.dp))

            Section("Widgets")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pin { requestPinGlanceAppWidget(TopWidgetReceiver::class.java) } }) { Text("Principal do dia") }
                OutlinedButton(onClick = { pin { requestPinGlanceAppWidget(CompactWidgetReceiver::class.java) } }) { Text("Compacto 4×1") }
                OutlinedButton(onClick = { pin { requestPinGlanceAppWidget(RegionWidgetReceiver::class.java) } }) { Text("Por região") }
            }
            Text(
                "O widget por região começa em Israel. Para trocar, segure o widget na tela inicial e escolha \"Configurar\" (ou adicione pela lista de widgets, que já pergunta a região).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 4.dp),
            )
            widgetMsg?.let { Text(it, color = Accent, modifier = Modifier.padding(16.dp, 8.dp)) }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Accent,
        modifier = Modifier.padding(16.dp, 12.dp),
    )
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: (T) -> Boolean, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(modifier = Modifier.padding(16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = selected(option), onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}
