# MCPack Builder

Android-приложение на Kotlin/Jetpack Compose для каталога MCPEDL, загрузки аддонов и локального объединения Behavior/Resource Packs в `.mcpack` и `.mcaddon`.

## Сборка

Проект использует фиксированную подпись `mcpack.keystore` (alias `mcpack`). Релиз собирается только этим ключом:

```bash
./gradlew assembleRelease
```

Перед сборкой поместите исходный приватный ключ в корень проекта и задайте `MCPACK_PASSWORD`. Для Actions нужны секреты `MCPACK_PASSWORD` и `MCPACK_KEYSTORE_BASE64`. Ключ не входит в Git, его сертификат проверяется Gradle. Храните резервную копию отдельно.

APK: `app/build/outputs/apk/release/app-release.apk`.

Каталог MCPEDL получает данные через официальный публичный API, а HTML-фолбэк разбирается Jsoup. Архивы объединяются полностью локально, без собственного сервера.
