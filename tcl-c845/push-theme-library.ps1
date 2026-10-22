# Copy the theme library in tcl-c845\theme-library\ to the launcher on the TV, where Launcher settings > Theme lists it.
# Each folder is a theme (config.json, optional wallpaper.jpg and wallpapers\); the folder name sorts the list and
# config.json's "name" is what the TV shows. Re-run after adding or editing a theme. Needs USB debugging on.
#
# Usage: tcl-c845\push-theme-library.ps1 -TvHost <tv ip>
param([Parameter(Mandatory = $true)][string]$TvHost)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$tv = Join-Path $repo 'tools\tv.py'
$b64 = Join-Path $repo 'tools\b64push.py'
$env:TV_HOST = $TvHost
$uri = 'content://com.gathin.tvhome.theme/themes'
$tmp = '/data/local/tmp/cyt-lib'

# TCL firmware (APP_AUTO_START: ignore) does not start an app's process just for its provider: bring Home up first.
& $py -I $tv 'input keyevent KEYCODE_HOME; sleep 2' | Out-Null

$lib = Join-Path $PSScriptRoot 'theme-library'
foreach ($theme in Get-ChildItem $lib -Directory) {
    $name = $theme.Name
    if ($name -notmatch '^[A-Za-z0-9_-]{1,40}$') { throw "theme folder name must be letters, digits, - or _: $name" }
    $files = @(Get-ChildItem $theme.FullName -Recurse -File | Where-Object {
        $rel = $_.FullName.Substring($theme.FullName.Length + 1) -replace '\\', '/'
        $rel -eq 'config.json' -or $rel -eq 'wallpaper.jpg' -or $rel -match '^wallpapers/([A-Za-z0-9_.-]+\.(jpg|jpeg|png|webp)|captions\.txt)$'
    })
    if (-not ($files | Where-Object Name -eq 'config.json')) { throw "no config.json in $($theme.FullName)" }
    Write-Host "theme $name ($($files.Count) files)"
    foreach ($f in $files) {
        $rel = $f.FullName.Substring($theme.FullName.Length + 1) -replace '\\', '/'
        & $py -I $b64 $f.FullName $tmp | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "upload of $rel failed" }
        # The provider writes as the launcher, so the files belong to it (a shell cp into Android/data may not).
        $out = (& $py -I $tv "content write --uri $uri/$name/$rel < $tmp; echo rc=`$?; rm -f $tmp") -join ' '
        if ($out -notmatch 'rc=0' -or $out -match 'Exception|Error') { throw "write of $name/$rel failed: $out" }
    }
}
& $py -I $tv 'ls -R /sdcard/Android/data/com.gathin.tvhome/files/themes 2>/dev/null | head -40'
Write-Host 'Done. On the TV: Settings button (top right) > Theme.'
