# MCPack Builder — Project Instruction

Android-приложение (Kotlin/Jetpack Compose) для каталога аддонов MCPEDL/MCPEHUB, локального объединения Behavior/Resource Packs в `.mcpack`/`.mcaddon`, AI-агента и библиотеки сборок. Репо: `omEZZka/mcpack-builder`. Рабочая папка: `/root/mcpack-builder-src`.

## Стек
- Kotlin, Jetpack Compose, Material 3, MVVM, Coroutines/Flow
- Jsoup (HTML), kotlinx.serialization.json, Coil (изображения)
- Min SDK 26, Target/Compile 35, Java 17, Kotlin compiler ext 1.5.14
- Сборка: Gradle, фиксированная подпись `mcpack.keystore` (alias `mcpack`), пароль `MCPACK_PASSWORD` (дефолт `mcpack123`)

## Архитектура
- `App.kt` / `AppContainer` — DI-контейнер (parser, fetcher, selection, store, theme, app)
- `MainActivity.kt` → `AppRoot.kt` — навигация по 5 вкладкам: Каталог, Сборка, AI Чат, Библиотека, Настройки
- MVVM: каждый экран имеет `*ViewModel` + `*Screen`

## Ключевые модули
- `domain/model/Models.kt` — `ModEntry`, `ModCategory` (MODS/TEXTURES/SHADERS/MAPS), `CatalogSort`, `CatalogSource` (MCPEDL/MCPEHUB), `CatalogTag`, `SavedBuild`, `MergeResult`
- `data/parser/McpedlParser.kt` — парсинг HTML/API MCPEDL (`.fancybox.post`, `.fancybox__title-top > a`), `parseApiList`, `parseDetail`
- `data/parser/McpedlRepository.kt` — `catalog(...)`, `enrich(...)`, `directDownload(...)`, `parseMcpehubListing(...)`
- `data/web/WebFetcher.kt` — HTTP-загрузка документов
- `data/download/FileDownloader.kt`, `MapImporter.kt` — скачивание файлов/карт
- `data/store/*` — `SelectionStore` (выбранные моды), `LibraryStore`, `DownloadRepository`, `DevicePacks`
- `domain/merger/AddonMerger.kt` — локальный компилятор: распаковка, классификация BP/RP, копирование, слияние реестров (JSON smart-merge, `.lang`), Script API, генерация манифестов, упаковка в `.mcpack`/`.mcaddon`
- `domain/merger/JsonUtils.kt` — JSON-утилиты
- `data/agent/AiAgentClient.kt` — OpenAI-совместимый клиент (`{base}/chat/completions`, Bearer), `testConnection()`, `chat()`, `saveConfig/loadConfig` (SharedPreferences `ai_agent`)
- `ui/agent/ChatScreen.kt` + `ChatViewModel.kt` — чат (история 12 сообщений)
- `ui/theme/Theme.kt` (`ThemeScreen`) — настройки: тема, язык, AI-агент (пресеты провайдеров, тумблеры, тест соединения)
- `ui/catalog/*`, `ui/builder/*`, `ui/library/*` — экраны
- `util/BuildExporter.kt` — открытие/шаринг сборки в Minecraft

## AI-агент
- Конфиг: `enabled`, `baseUrl`, `apiKey`, `model`, `timeout`, `autoDownload`, `autoFix`, `recommend`, `provider`
- Пресеты: OpenAI, OpenRouter, Groq, DeepSeek, Ollama (localhost), Custom
- Протокол: `POST {baseUrl}/chat/completions`, `Authorization: Bearer {key}`, body `{model, messages, temperature}`; ответ `choices[0].message.content`
- Тест соединения различает 401/404/429/сетевые ошибки

## Каталог (MCPEDL)
- Без тега: API `https://mcpedl.com/api/list?type_id=...&sort=...&page=...`
- С тегом: `https://mcpedl.com/tag/{slug}/`
- MCPEHUB: тег-фильтры отключены (fallback на страницы категорий)
- Сортировка: `CatalogSort.api` (created_at / popularity_week / popularity_month)
- Пагинация: `page`, `pageCount`, `total`

## Сборка и деплой
- Workflow `.github/workflows/build.yml`: push в `main` → `assembleRelease`, артефакт `MCPackBuilder-release/app-release.apk`
- Секреты: `MCPACK_KEYSTORE_BASE64`, `MCPACK_PASSWORD`
- `gh` CLI: `/tmp/gh_2.65.0_linux_arm64/bin/gh`, репо `omEZZka/mcpack-builder`
- Скачивание APK: `gh run download <id> -n MCPackBuilder-release -D /tmp/apk`
- Текущая версия: `versionCode = 4`, `versionName = "1.6.0"` (app/build.gradle.kts)
- Последний успешный билд: run `45769915354`, APK `/sdcard/Download/MCPackBuilder-1.6.0.apk`

## Известные проблемы / статус
- Каталог-фильтрация (категории, сортировка, теги, источник) переписана, но на устройстве не верифицирована — пользователь сообщает странное поведение
- AI-чат новой реализации не протестирован на устройстве
- Версия в `app/build.gradle.kts` ранее отставала от имён APK — исправлено (1.6.0)
- Дубликат `ChatMessage` удалён из `ChatScreen.kt` (оставлен в `ChatViewModel.kt`)

## Конвенции
- Комментарии не добавлять без явной просьбы
- Не коммитить без явной просьбы пользователя
- Перед коммитом — `git diff --check`
- После правок — запускать lint/typecheck (команды уточнять у пользователя)
- Язык интерфейса — русский
- Пакет: `com.mcpackbuilder`
