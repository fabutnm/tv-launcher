# Build the launcher from launcher\ (with launcher\app\src\main\res\values\config.xml) and install it on the
# TCL P725 as Home. Projectivy stays installed and is not disabled (-StockLauncher none).
#
# Usage: tcl-p725\build-and-install-p725.ps1 -TvHost <tv ip>
# Needs USB debugging on the TV (Settings > Device preferences > Developer options) and the venv in tools\venv.
# Windows refuses some files in tools\venv when the repo sits in a very long folder path: run this from a short
# path (C:\tv\cleanyourtv) or map one with `subst T: <repo folder>` and run it from T:\.
param([Parameter(Mandatory = $true)][string]$TvHost)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $jdk) { throw 'JDK 17 not found: winget install Microsoft.OpenJDK.17' }
    $env:JAVA_HOME = $jdk
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }

Push-Location   # build-and-install.ps1 moves into launcher\
try { & (Join-Path $repo 'launcher\build-and-install.ps1') -TvHost $TvHost -StockLauncher none }
finally { Pop-Location }

# Projectivy's accessibility service takes the Home button back if it is on; keep it off.
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$tv = Join-Path $repo 'tools\tv.py'
$env:TV_HOST = $TvHost
$a11y = (& $py -I $tv 'settings get secure enabled_accessibility_services') -join ''
if ($a11y -match 'projengmenu') {
    Write-Warning 'Projectivy accessibility service is on and overrides Home; switching it off.'
    & $py -I $tv "settings put secure enabled_accessibility_services ''"
    & $py -I $tv 'settings put secure accessibility_enabled 0'
}
& $py -I $tv 'input keyevent KEYCODE_HOME'
Start-Sleep 2
& $py -I $tv 'dumpsys activity activities | grep mResumedActivity | head -1'
