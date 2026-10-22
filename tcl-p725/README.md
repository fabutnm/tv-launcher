# TCL P725: чистка и лаунчер

Проверено на TCL P725 («Smart TV Pro», BeyondTV4, плата RTD2851M), Android TV 11 (SDK 30), прошивка
V8-R51MT02-LF1V344, 2 ГБ ОЗУ. Подойдёт и для других TCL Android TV 11 той же серии (см. `examples/tcl-55c725`).

Все команды — из корня репозитория в PowerShell, с включённой на ТВ «Отладкой по USB» (Настройки → Настройки
устройства → Об устройстве → 7 раз «Сборка», затем «Для разработчиков»). Один раз подготовить Python:

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
```

Windows не загружает часть файлов Python из очень длинных путей — держите репозиторий в короткой папке
(например `C:\src\tv-launcher`).

## Что внутри

| Файл | Что делает |
|---|---|
| `debloat.txt` | 31 системный пакет TCL и Google, который отключается (не удаляется): телеметрия, промо, лишние службы, голос, Miracast, стоковый YouTube, обновления. Перед применением закомментируйте то, чем пользуетесь. |
| `build-and-install-p725.ps1` | Собирает лаунчер и ставит его домашним экраном. |
| `push-theme-library-p725.ps1` | Заливает темы из `theme-library\` — потом они выбираются на ТВ: Настройки главного экрана → Тема. |
| `restore-p725.ps1` | Возвращает всё как было: включает пакеты из списка, удаляет лаунчер и «Дом». |
| `theme-library\` | Темы: Чёрная, Аврора, Стекло, Пейзажи (14 картинок, смена раз в час), Небо. Док в каждой — свой, правьте `dock_apps`. |

## Порядок

```powershell
tools\disable.ps1 -TvHost <tv ip> tcl-p725\debloat.txt
tcl-p725\build-and-install-p725.ps1 -TvHost <tv ip>
tcl-p725\push-theme-library-p725.ps1 -TvHost <tv ip>
```

Для сборки нужны JDK 17 (`winget install Microsoft.OpenJDK.17`) и Android SDK (platform 34, build-tools 34.0.0).
Готовый APK можно взять в Releases и поставить так:

```powershell
adb connect <tv ip>:5555
adb install -r tv-launcher-1.0.apk
adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
```

## Особенности этого ТВ

- Если стоял сторонний лаунчер с «перехватом кнопки Home» через службу специальных возможностей (Projectivy),
  `set-home-activity` отвечает `Success`, но Home открывает старый лаунчер. Отключите его службу:
  `adb shell settings put secure enabled_accessibility_services ''` — `build-and-install-p725.ps1` делает это сам.
- На TCL не стартуют в фоне приложения, которые ни разу не открывались (`APP_AUTO_START: ignore`).
- Снимки экрана Live TV и HDMI приходят пустыми — это защита ТВ.
- После работы выключите на ТВ «Отладку по USB» и отзовите авторизации: порт 5555 открыт без шифрования.
