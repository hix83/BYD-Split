param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'adb',
    [string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'tmp/di2-sdk-runtime')
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$packagePath = & $Adb -s $Serial shell pm path ru.logunov.bydsplit
if ($LASTEXITCODE -ne 0) { throw 'Cannot locate installed launcher.' }
$apk = ($packagePath | Where-Object { $_ -match '^package:.*base\.apk$' } | Select-Object -First 1) -replace '^package:', ''
if (!$apk) { $apk = ($packagePath | Select-Object -First 1) -replace '^package:', '' }
$apk = $apk.Trim()
if ($apk -notmatch '^/[A-Za-z0-9_./=+~-]+\.apk$') { throw 'Unexpected package path.' }
$output = & $Adb -s $Serial shell "CLASSPATH=$apk app_process /system/bin ru.logunov.bydsplit.Di2SdkProbe"
if ($LASTEXITCODE -ne 0) { throw 'SDK probe failed. Install the current debug build first.' }
$output | Set-Content (Join-Path $OutputDirectory 'sdk.json') -Encoding UTF8
& $Adb -s $Serial shell service list | Set-Content (Join-Path $OutputDirectory 'services.txt') -Encoding UTF8
& $Adb -s $Serial shell "content query --uri content://carsettings/global --where `"name='sys_day_or_night'`"" |
    Set-Content (Join-Path $OutputDirectory 'day-night.txt') -Encoding UTF8
Write-Output "SDK metadata saved to $OutputDirectory. No vehicle methods were invoked."
