# tv-launcher — сборка fabutnm на основе CleanYourTV

Это копия [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (лицензия MIT, вся история коммитов
оригинала сохранена) с доработками, сделанными и проверенными на телевизоре TCL P725 (Android TV 11). Оригинал
подключается как `upstream`, его обновления можно подтягивать:

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git
git fetch upstream
git merge upstream/main
```

## Что изменено относительно оригинала

- **Выбор темы на ТВ, с пульта.** В «Настройках главного экрана» появилась строка «Тема». Темы лежат на ТВ в
  библиотеке (`themes/<имя>/` в папке лаунчера), заливаются один раз скриптом и переключаются без компьютера.
  Код: `ThemeLibrary.java`, правки в `LauncherSettingsActivity.java`, `ThemeFiles.java`, `Config.java`,
  `activity_launcher_settings.xml`, строки `theme_*`.
- **`config.xml` настроен под TCL P725:** док из 6 приложений, 6 колонок, Live TV `com.tcl.tv`, кнопка настроек
  открывает настройки лаунчера, меню на плитках (удержание OK), круглые кнопки Wi-Fi и настроек.
- **Своя нумерация версий** (1.0, основана на cleanyourtv 2.6) и подпись релизным ключом из
  `~/.gradle/gradle.properties` (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
  `RELEASE_KEY_PASSWORD`). Без них сборка подписывается отладочным ключом.
- **Папки [`tcl-p725/`](tcl-p725/README.md) и [`tcl-c845/`](tcl-c845/README.md)** (TCL C845, Google TV G05):
  списки отключённых пакетов, скрипты установки, восстановления и библиотеки тем для каждого телевизора.

## Готовый APK

В разделе Releases: `tv-launcher-<версия>.apk`. Установка: `adb install -r tv-launcher-1.0.apk`, затем
`adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity` — подробности в `tcl-p725/README.md`.

Приложение «Дом» для Home Assistant — отдельный репозиторий `tv-smart-home`.
