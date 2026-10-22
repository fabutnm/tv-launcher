# TCL C845 (Google TV, G05): чистка и лаунчер

Проверено на TCL C845 («Smart TV Pro», устройство G05, продукт G05_4K_GB, плата C05, MediaTek), Google TV на
Android 11 (SDK 30), отпечаток `TCL/G05_4K_GB/G05:11/AR2101/AR28`, 2,7 ГБ ОЗУ, Wi-Fi. Близкие отчёты:
`examples/tcl-g05-google-tv` и `docs/devices/tcl-55c728-g05-4k-gb.md`.

Подготовка ПК и включение отладки — как в [`tcl-p725/README.md`](../tcl-p725/README.md).

## Что внутри

| Файл | Что делает |
|---|---|
| `debloat.txt` | 22 пакета: телеметрия Google и TCL Guard, промо и рекомендации TCL, лишние службы и предустановки, стоковый YouTube и Prime Video. Голос, Chromecast, AirPlay, Miracast, T-Cast и обновления оставлены. |
| `push-theme-library.ps1` | Заливает темы из `theme-library\` (док этого ТВ: YouTube, VK Видео, Wink, Кинопоиск, Okko, Lampa). |
| `restore.ps1` | Включает пакеты обратно, возвращает Projectivy, удаляет лаунчер и «Дом». |

## Порядок

```powershell
tools\disable.ps1 -TvHost <tv ip> tcl-c845\debloat.txt
adb install -r tv-launcher-1.0.apk          # из Releases
adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
tcl-c845\push-theme-library.ps1 -TvHost <tv ip>
tools\theme.ps1 -TvHost <tv ip> tcl-c845\theme-library\1-black   # док этого ТВ сразу, без выбора на ТВ
```

## Особенности этого ТВ

- Стоковый домашний экран Google TV (`launcherx`) и мастер настройки (`setupwraith`) здесь были отключены ещё до
  установки, и после перезагрузки остались отключены: Home остаётся за лаунчером. Сохранённая сеть Wi-Fi
  принадлежит системе (`cname=android.uid.system`), так что setupwraith её не удалил бы.
  Вернуть стоковый Google TV: `adb shell pm enable com.google.android.apps.tv.launcherx` и
  `adb shell pm enable com.google.android.tungsten.setupwraith`.
- `com.tcl.notereminder` прошивка включает обратно при каждой загрузке, поэтому в списке он закомментирован.
- `com.tcl.suspension` не отключать: на этой платформе без него не работает кнопка Input на пульте.
- Если стоял Projectivy с «перехватом Home» через спец. возможности, его службу нужно выключить
  (`settings put secure enabled_accessibility_services ''`), иначе Home открывает Projectivy.
