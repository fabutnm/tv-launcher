# Undo the setup of the TCL C845 (G05 Google TV): re-enable every package in debloat.txt, put Projectivy back as
# Home and remove com.gathin.tvhome and the Дом app. Packages that were already disabled before (launcherx,
# setupwraith, dreamx, logkit, region apps) are left alone; bringing back the stock Google TV home is in README.md.
#
# Usage: tcl-c845\restore.ps1 -TvHost <tv ip> [-KeepLauncher]
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$KeepLauncher
)
$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent $PSScriptRoot
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$tv = Join-Path $repo 'tools\tv.py'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

$list = Join-Path $PSScriptRoot 'debloat.txt'
$pkgs = Get-Content $list | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ } |
    ForEach-Object { ($_ -split '\s+')[0] }

# One pm command per shell call: chained commands can stop silently after the first.
foreach ($p in $pkgs) {
    $out = & $py -I $tv "pm enable --user 0 $p"
    Write-Output "$p : $out"
}

$projectivy = 'com.spocky.projengmenu/.ui.home.MainActivity'
& $py -I $tv "cmd package set-home-activity $projectivy"
# Projectivy's own Home-button override, switched off when this launcher took over Home.
& $py -I $tv 'settings put secure enabled_accessibility_services com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService'
& $py -I $tv 'settings put secure accessibility_enabled 1'
if (-not $KeepLauncher) {
    & $py -I $tv 'pm uninstall com.gathin.tvhome'   # our own app, not a system package
    & $py -I $tv 'pm uninstall io.github.fabutnm.homepanel'  # the "Дом" app and its config (token)
}
& $py -I $tv 'input keyevent KEYCODE_HOME'
& $py -I $tv 'dumpsys activity activities | grep mFocusedApp'
Write-Output 'Still disabled now:'
& $py -I $tv 'pm list packages -d'
