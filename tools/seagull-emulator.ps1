param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AvdName = 'BYD_Seagull_DiLink2_API28',
    [ValidateRange(5554, 5682)][int]$Port = 5560,
    [switch]$SetupOnly,
    [switch]$InstallApp
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (!$SdkRoot -and (Test-Path "$projectRoot/local.properties")) {
    $line = Get-Content "$projectRoot/local.properties" | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($line) { $SdkRoot = ($line -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\' }
}
if (!$SdkRoot) { throw 'Specify -SdkRoot or ANDROID_HOME.' }
if ($AvdName -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Invalid AVD name.' }
if ($Port % 2) { throw 'Emulator port must be even.' }
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$emulator = Join-Path $SdkRoot 'emulator/emulator.exe'
$avdmanager = Join-Path $SdkRoot 'cmdline-tools/latest/bin/avdmanager.bat'
$image = Join-Path $SdkRoot 'system-images/android-28/default/x86_64/system.img'
foreach ($path in @($adb, $emulator, $avdmanager, $image)) {
    if (!(Test-Path $path)) { throw "Missing SDK component: $path. Install emulator and system-images;android-28;default;x86_64 using SDK Manager." }
}
$avdHome = $env:ANDROID_AVD_HOME
if (!$avdHome) {
    $androidHome = $env:ANDROID_USER_HOME
    if (!$androidHome) { $androidHome = Join-Path $env:USERPROFILE '.android' }
    $avdHome = Join-Path $androidHome 'avd'
}
$config = Join-Path $avdHome "$AvdName.avd/config.ini"
if (!(Test-Path $config)) {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    if (!(Test-Path "$env:JAVA_HOME/bin/java.exe")) { throw 'Specify -JavaHome with a working JDK 17 directory.' }
    'no' | & $avdmanager create avd --name $AvdName --package 'system-images;android-28;default;x86_64'
    if ($LASTEXITCODE -ne 0) { throw 'AVD creation failed.' }
    $settings = @{
        'avd.ini.displayname' = 'BYD Seagull - Android 9 - 10.1 inch (provisional FHD)'
        'hw.lcd.width' = '1920'; 'hw.lcd.height' = '1080'; 'hw.lcd.density' = '160'
        'hw.initialOrientation' = 'landscape'; 'hw.ramSize' = '2048'; 'hw.cpu.ncore' = '4'
        'hw.gpu.enabled' = 'yes'; 'hw.gpu.mode' = 'auto'; 'hw.keyboard' = 'yes'
        'hw.mainKeys' = 'no'; 'hw.camera.back' = 'none'; 'hw.camera.front' = 'none'
        'disk.dataPartition.size' = '4G'; 'showDeviceFrame' = 'no'
        'skin.dynamic' = 'yes'; 'skin.name' = '1920x1080'
        'fastboot.forceColdBoot' = 'yes'; 'fastboot.forceFastBoot' = 'no'
    }
    $lines = @(Get-Content $config | Where-Object { ($_ -split '=', 2)[0] -notin $settings.Keys -and $_ -notmatch '^skin.path=' })
    $lines += $settings.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Key)=$($_.Value)" }
    $lines | Set-Content $config -Encoding ASCII
}
if ($SetupOnly) { Write-Output "AVD ready: $AvdName ($config)"; return }
$serial = "emulator-$Port"
$state = & $adb -s $serial get-state 2>$null
if ($state -eq 'device') {
    $runningName = & $adb -s $serial emu avd name
    if ($runningName -notcontains $AvdName) { throw "Port $Port belongs to another AVD." }
} else {
    $logDir = Join-Path $projectRoot 'tmp/seagull-emulator'
    New-Item -ItemType Directory -Force $logDir | Out-Null
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $AvdName, '-port', $Port, '-no-snapshot', '-no-boot-anim') -WindowStyle Hidden -RedirectStandardOutput "$logDir/stdout.log" -RedirectStandardError "$logDir/stderr.log" | Out-Null
}
$deadline = (Get-Date).AddMinutes(3)
do {
    $booted = & $adb -s $serial shell getprop sys.boot_completed 2>$null
    if ($booted -eq '1') { break }
    if ((Get-Date) -gt $deadline) { throw 'Emulator boot timed out. See tmp/seagull-emulator/ logs.' }
    Start-Sleep -Seconds 2
} while ($true)
& $adb -s $serial shell settings put system accelerometer_rotation 0
& $adb -s $serial shell settings put system user_rotation 0
& $adb -s $serial shell wm size
& $adb -s $serial shell wm density
if ($InstallApp) {
    $apk = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
    if (!(Test-Path $apk)) { throw 'Build assembleDebug first.' }
    & $adb -s $serial install -r -g $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
    & $adb -s $serial shell am start -n ru.logunov.bydsplit/.MainActivity --ez demo_mode true
    if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
}
Write-Output "Ready: $AvdName ($serial). Screen resolution and density are provisional; see docs/SEAGULL-EMULATOR.md."
