param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [ValidateSet('read','climate','power','temperature','fan','wind','recirculation','compressor')]
    [string]$Command = 'read',
    [Nullable[int]]$Value = $null,
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
if ($Command -in @('read','climate')) {
    if ($null -ne $Value) { throw 'read/climate do not accept Value.' }
} else {
    if ($null -eq $Value) { throw 'A climate command requires -Value.' }
    $minimum = 0; $maximum = 1
    switch ($Command) {
        'temperature' { $minimum = 18; $maximum = 32 }
        'fan' { $maximum = 7 }
        'wind' { $minimum = 1; $maximum = 7 }
    }
    if ($Value -lt $minimum -or $Value -gt $maximum) { throw "Value must be $minimum..$maximum." }
}
$packagePath = & $Adb -s $Serial shell pm path ru.logunov.bydsplit
if ($LASTEXITCODE -ne 0) { throw 'Cannot locate installed launcher.' }
$apk = (($packagePath | Select-Object -First 1) -replace '^package:', '').Trim()
if ($apk -notmatch '^/[A-Za-z0-9_./=+~-]+\.apk$') { throw 'Unexpected package path.' }
$arguments = $Command
if ($null -ne $Value) { $arguments += " $Value" }
$output = & $Adb -s $Serial shell "CLASSPATH=$apk timeout 8 app_process /system/bin ru.logunov.bydsplit.Di2VehicleBridge $arguments"
$line = $output | Where-Object { $_ -like 'BYD_DI2_V1 *' } | Select-Object -Last 1
if (!$line) { throw 'Di2 helper did not reply: timeout or start failure.' }
$reply = $line.Substring('BYD_DI2_V1 '.Length) | ConvertFrom-Json
$reply | ConvertTo-Json -Depth 8
if (!$reply.ok) { exit 2 }
