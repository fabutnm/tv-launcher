# Undo everything this session changed on the TCL P725: re-enable every package in debloat.txt
# (and the stock launcher if it was disabled here), put Projectivy back as Home and remove com.gathin.tvhome.
# Packages that were already disabled before this session (tvlauncher, tcl.gallery, Freeview) are left alone.
#
# Usage: tcl-p725\restore-p725.ps1 -TvHost <tv ip> [-KeepLauncher]
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
# Display density was 480 before this session (physical 320); uncomment to bring the larger UI back.
# & $py -I $tv 'wm density 480'
if (-not $KeepLauncher) {
    & $py -I $tv 'pm uninstall com.gathin.tvhome'   # our own app, not a system package
    & $py -I $tv 'pm uninstall io.github.fabutnm.homepanel'  # the "Дом" app and its config (token)
}
& $py -I $tv 'input keyevent KEYCODE_HOME'
& $py -I $tv 'dumpsys activity activities | grep mFocusedApp'
Write-Output 'Still disabled now:'
& $py -I $tv 'pm list packages -d'
