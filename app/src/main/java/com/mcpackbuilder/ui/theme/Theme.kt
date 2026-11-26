package com.mcpackbuilder.ui.theme

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

data class ThemePalette(val nameRu: String, val nameEn: String, val hue: Float, val dark: Boolean)

private fun lightPalettes(): List<ThemePalette> = listOf(
    ThemePalette("Изумруд", "Emerald", 145f, false), ThemePalette("Небо", "Sky", 205f, false),
    ThemePalette("Лаванда", "Lavender", 265f, false), ThemePalette("Персик", "Peach", 25f, false),
    ThemePalette("Роза", "Rose", 345f, false), ThemePalette("Лайм", "Lime", 95f, false),
    ThemePalette("Бирюза", "Teal", 175f, false), ThemePalette("Янтарь", "Amber", 40f, false),
    ThemePalette("Индиго", "Indigo", 235f, false), ThemePalette("Коралл", "Coral", 10f, false),
    ThemePalette("Мята", "Mint", 160f, false), ThemePalette("Сапфир", "Sapphire", 220f, false),
    ThemePalette("Фуксия", "Fuchsia", 315f, false), ThemePalette("Олива", "Olive", 75f, false),
    ThemePalette("Песок", "Sand", 55f, false), ThemePalette("Слива", "Plum", 290f, false),
    ThemePalette("Морской", "Ocean", 195f, false), ThemePalette("Вишня", "Cherry", 355f, false),
    ThemePalette("Лён", "Linen", 35f, false), ThemePalette("Лёд", "Ice", 190f, false),
    ThemePalette("Сирень", "Lilac", 280f, false), ThemePalette("Клюква", "Cranberry", 335f, false),
    ThemePalette("Хаки", "Khaki", 65f, false), ThemePalette("Сталь", "Steel", 215f, false),
    ThemePalette("Бумага", "Paper", 30f, false),
)

private fun darkPalettes(): List<ThemePalette> = listOf(
    ThemePalette("Изумрудная ночь", "Emerald Night", 145f, true), ThemePalette("Глубокий океан", "Deep Ocean", 210f, true),
    ThemePalette("Аметист", "Amethyst", 270f, true), ThemePalette("Уголь", "Ember Coal", 20f, true),
    ThemePalette("Бургундия", "Burgundy", 350f, true), ThemePalette("Мох", "Moss", 110f, true),
    ThemePalette("Тёмная бирюза", "Dark Teal", 175f, true), ThemePalette("Медь", "Copper", 35f, true),
    ThemePalette("Ночное небо", "Night Sky", 235f, true), ThemePalette("Гранат", "Garnet", 5f, true),
    ThemePalette("Тайга", "Taiga", 155f, true), ThemePalette("Север", "North", 225f, true),
    ThemePalette("Орхидея", "Orchid", 305f, true), ThemePalette("Оливковая тень", "Olive Shadow", 80f, true),
    ThemePalette("Какао", "Cocoa", 30f, true), ThemePalette("Фиолетовая тень", "Violet Shade", 285f, true),
    ThemePalette("Шторм", "Storm", 200f, true), ThemePalette("Тёмная вишня", "Dark Cherry", 340f, true),
    ThemePalette("Графит", "Graphite", 220f, true), ThemePalette("Арктика", "Arctic", 190f, true),
    ThemePalette("Сумерки", "Twilight", 255f, true), ThemePalette("Марсала", "Marsala", 355f, true),
    ThemePalette("Лес", "Forest", 130f, true), ThemePalette("Сланец", "Slate", 215f, true),
    ThemePalette("Обсидиан", "Obsidian", 260f, true),
)

val AllPalettes: List<ThemePalette> = lightPalettes() + darkPalettes()

data class ThemeSettingsData(val paletteIndex: Int = 0, val language: String = "ru")

class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("mcpack_theme", Context.MODE_PRIVATE)
    private val _state = mutableStateOf(load())
    val state: State<ThemeSettingsData> = _state

    private fun load(): ThemeSettingsData = ThemeSettingsData(
        paletteIndex = prefs.getInt("palette", 0).coerceIn(0, AllPalettes.lastIndex),
        language = prefs.getString("lang", "ru") ?: "ru"
    )

    fun setPalette(i: Int) = update(_state.value.copy(paletteIndex = i.coerceIn(0, AllPalettes.lastIndex)))
    fun setLanguage(code: String) = update(_state.value.copy(language = code))

    private fun update(s: ThemeSettingsData) {
        _state.value = s
        prefs.edit().putInt("palette", s.paletteIndex).putString("lang", s.language).apply()
    }
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r1 + m, g1 + m, b1 + m)
}

private fun schemeFor(p: ThemePalette): ColorScheme {
    val h = p.hue
    return if (p.dark) darkColorScheme(
        primary = hsl(h, 0.55f, 0.72f), onPrimary = hsl(h, 0.45f, 0.12f),
        primaryContainer = hsl(h, 0.35f, 0.24f), onPrimaryContainer = hsl(h, 0.45f, 0.90f),
        secondary = hsl((h + 30f) % 360f, 0.30f, 0.72f), secondaryContainer = hsl((h + 20f) % 360f, 0.22f, 0.24f), onSecondaryContainer = hsl(h, 0.20f, 0.90f),
        tertiary = hsl((h + 60f) % 360f, 0.35f, 0.72f),
        background = hsl(h, 0.14f, 0.08f), onBackground = hsl(h, 0.10f, 0.92f),
        surface = hsl(h, 0.14f, 0.11f), onSurface = hsl(h, 0.10f, 0.92f),
        surfaceVariant = hsl(h, 0.16f, 0.20f), onSurfaceVariant = hsl(h, 0.10f, 0.78f),
        outline = hsl(h, 0.10f, 0.45f), error = Color(0xFFFFB4AB),
    ) else lightColorScheme(
        primary = hsl(h, 0.52f, 0.34f), onPrimary = Color.White,
        primaryContainer = hsl(h, 0.45f, 0.88f), onPrimaryContainer = hsl(h, 0.55f, 0.14f),
        secondary = hsl((h + 25f) % 360f, 0.30f, 0.38f), secondaryContainer = hsl((h + 20f) % 360f, 0.28f, 0.90f), onSecondaryContainer = hsl(h, 0.30f, 0.16f),
        tertiary = hsl((h + 55f) % 360f, 0.35f, 0.40f),
        background = hsl(h, 0.20f, 0.97f), onBackground = hsl(h, 0.20f, 0.12f),
        surface = hsl(h, 0.22f, 0.985f), onSurface = hsl(h, 0.20f, 0.12f),
        surfaceVariant = hsl(h, 0.18f, 0.92f), onSurfaceVariant = hsl(h, 0.15f, 0.30f),
        outline = hsl(h, 0.12f, 0.55f), error = Color(0xFFBA1A1A),
    )
}

@Composable
fun MCPackTheme(themeSettings: ThemeSettings? = null, content: @Composable () -> Unit) {
    val settings by (themeSettings?.state ?: remember { mutableStateOf(ThemeSettingsData()) })
    val palette = AllPalettes.getOrElse(settings.paletteIndex) { AllPalettes.first() }
    val strings = stringsFor(AppLanguage.fromCode(settings.language))
    CompositionLocalProvider(LocalStrings provides strings) {
        MaterialTheme(colorScheme = schemeFor(palette), typography = AppTypography, shapes = AppShapes, content = content)
    }
}

@Composable
fun ThemeScreen(theme: ThemeSettings, context: android.content.Context) {
    SettingsScreen(theme, context)
}

@Composable
fun SettingsScreen(theme: ThemeSettings, context: android.content.Context) {
    val s = LocalStrings.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(s.settingsGeneral, s.settingsAppearance, s.aiAgent, s.settingsAbout)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.navSettings, style = MaterialTheme.typography.headlineMedium)
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
                }
            }
        }
        when (tab) {
            0 -> GeneralSettingsTab(theme)
            1 -> AppearanceSettingsTab(theme)
            2 -> AiAgentSettingsTab(context)
            3 -> AboutSettingsTab()
        }
    }
}

@Composable
private fun GeneralSettingsTab(theme: ThemeSettings) {
    val s = LocalStrings.current
    val settings by theme.state
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsCard(title = s.language, subtitle = s.generalSubtitle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(selected = settings.language == lang.code, onClick = { theme.setLanguage(lang.code) }, label = { Text(lang.nativeName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsTab(theme: ThemeSettings) {
    val s = LocalStrings.current
    val settings by theme.state
    val current = AllPalettes.getOrElse(settings.paletteIndex) { AllPalettes.first() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsCard(title = s.appearance, subtitle = s.appearanceSubtitle) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !current.dark, onClick = { theme.setPalette(AllPalettes.indexOfFirst { !it.dark }.coerceAtLeast(0)) }, label = { Text(s.light) })
                        FilterChip(selected = current.dark, onClick = { theme.setPalette(AllPalettes.indexOfFirst { it.dark }.coerceAtLeast(0)) }, label = { Text(s.dark) })
                    }
                }
                Text(if (current.dark) s.dark else s.light, style = MaterialTheme.typography.titleMedium)
            }
        }
        val palettes = if (current.dark) AllPalettes.filter { it.dark } else AllPalettes.filter { !it.dark }
        itemsIndexed(palettes, key = { _, p -> p.nameEn }) { _, p ->
            val idx = AllPalettes.indexOf(p)
            val selected = idx == settings.paletteIndex
            val c = schemeFor(p)
            Card(
                modifier = Modifier.fillMaxWidth().clickable { theme.setPalette(idx) },
                colors = CardDefaults.cardColors(containerColor = c.surfaceVariant.copy(alpha = 0.7f)),
                border = if (selected) BorderStroke(2.dp, c.primary) else null
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(c.primary))
                        Box(Modifier.size(22.dp).clip(CircleShape).background(c.secondary))
                        Box(Modifier.size(22.dp).clip(CircleShape).background(c.tertiary))
                        Box(Modifier.size(22.dp).clip(CircleShape).background(c.background).border(1.dp, c.outline, CircleShape))
                    }
                    Text(if (settings.language == "en") p.nameEn else p.nameRu, style = MaterialTheme.typography.labelLarge, color = c.onSurface)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiAgentSettingsTab(context: android.content.Context) {
    val s = LocalStrings.current
    val agent = remember { com.mcpackbuilder.data.agent.AiAgentClient(context) }
    var config by remember { mutableStateOf(agent.loadConfig()) }
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<com.mcpackbuilder.data.agent.ConnectionTestResult?>(null) }
    var showKey by remember { mutableStateOf(false) }

    fun save(new: com.mcpackbuilder.data.agent.AiAgentConfig) {
        config = new
        agent.saveConfig(new)
    }

    val presets = listOf(
        Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        Triple("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        Triple("Ollama (local)", "http://192.168.1.10:11434/v1", "llama3.1"),
        Triple("Custom", "", "")
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("AI Агент", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Подключите любой OpenAI-совместимый сервер. Агент помогает собирать сборки и отвечает в чате.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            SettingsCard(title = "Провайдер", subtitle = "Быстрый выбор сервера") {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (name, url, model) ->
                        FilterChip(
                            selected = config.provider == name,
                            onClick = {
                                if (name == "Custom") save(config.copy(provider = name))
                                else save(config.copy(provider = name, baseUrl = url, model = model))
                                testResult = null
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Подключение", subtitle = "Base URL, ключ и модель") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = config.enabled, onCheckedChange = { save(config.copy(enabled = it)) })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Включить AI-агента", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (config.enabled) "Агент активен" else "Агент выключен",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = config.baseUrl,
                    onValueChange = { save(config.copy(baseUrl = it)) },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://api.openai.com/v1") },
                    supportingText = { Text("Без /chat/completions — добавится автоматически") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.apiKey,
                    onValueChange = { save(config.copy(apiKey = it)) },
                    label = { Text("API ключ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Скрыть" else "Показать", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.model,
                    onValueChange = { save(config.copy(model = it)) },
                    label = { Text("Модель") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("gpt-4o-mini") }
                )
            }
        }

        item {
            SettingsCard(title = "Проверка соединения", subtitle = "Реальный запрос к серверу") {
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = agent.testConnection()
                            testing = false
                        }
                    },
                    enabled = !testing && config.baseUrl.isNotBlank() && config.model.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (testing) "Проверка…" else "Проверить соединение")
                }
                testResult?.let { r ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = if (r.ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            (if (r.ok) "✓ " else "✗ ") + r.detail,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (r.ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Возможности", subtitle = "Что делает агент") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = config.autoDownload, onCheckedChange = { save(config.copy(autoDownload = it)) })
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Авто-скачивание", style = MaterialTheme.typography.bodyMedium)
                            Text("Скачивать все моды при сборке", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = config.autoFix, onCheckedChange = { save(config.copy(autoFix = it)) })
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Исправление конфликтов", style = MaterialTheme.typography.bodyMedium)
                            Text("Разрешать коллизии файлов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = config.recommend, onCheckedChange = { save(config.copy(recommend = it)) })
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Рекомендации", style = MaterialTheme.typography.bodyMedium)
                            Text("Подбор совместимых аддонов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSettingsTab() {
    val s = LocalStrings.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsCard(title = s.appName, subtitle = s.aboutSubtitle) {
                Text(s.appDescription, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("${s.version}: ${com.mcpackbuilder.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
