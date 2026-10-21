param(
    [string]$Serial = "",
    [switch]$SkipBuild,
    [switch]$NoPcPair
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$pkg = "com.sitikplay.foxhole"
$tmp = Join-Path $env:TEMP "foxhole-e2e"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Find-First([object[]]$Candidates) {
    foreach ($c in $Candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) { return (Resolve-Path -LiteralPath $c).Path }
    }
    return $null
}

function Assert($Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$sdkRoots = @()
if ($env:ANDROID_SDK_ROOT) { $sdkRoots += $env:ANDROID_SDK_ROOT }
if ($env:ANDROID_HOME) { $sdkRoots += $env:ANDROID_HOME }
$sdkRoots += (Join-Path $env:LOCALAPPDATA "Android\Sdk")
$adb = Find-First ($sdkRoots | ForEach-Object { Join-Path $_ "platform-tools\adb.exe" })
if (-not $adb) {
    $c = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($c) { $adb = $c.Source }
}
Assert ($adb) "adb не найден: установи Android SDK platform-tools или добавь adb в PATH"
$sdk = Split-Path (Split-Path $adb -Parent) -Parent

$javac = Find-First @(
    "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe",
    "C:\Program Files (x86)\Android\Android Studio\jbr\bin\javac.exe",
    (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr\bin\javac.exe"),
    (Join-Path $env:ProgramFiles "Android\Android Studio\jbr\bin\javac.exe")
)
if (-not $javac -and $env:JAVA_HOME) { $javac = Find-First @((Join-Path $env:JAVA_HOME "bin\javac.exe")) }
if (-not $javac) {
    $c = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($c) { $javac = $c.Source }
}
Assert ($javac) "javac не найден: нужен JDK (Android Studio jbr или JAVA_HOME)"
$jdkBin = Split-Path $javac -Parent

Write-Host "adb: $adb"
Write-Host "JDK: $jdkBin"

function Invoke-Adb {
    & $adb -s $Serial @args
}

function Sh([string]$Command) {
    (& $adb -s $Serial shell $Command 2>&1 | Out-String).Trim()
}

function Invoke-Acc {
    param([string]$Action, [hashtable]$Extra = @{})
    $a = @("shell", "uiautomator", "runtest", "acc.jar", "-c", "fox.Acc#testAcc", "-e", "action", $Action)
    foreach ($key in $Extra.Keys) {
        $a += @("-e", $key, [string]$Extra[$key])
    }
    return (& $adb -s $Serial @a 2>&1 | Out-String)
}

function Ensure-Acc {
    $src = Join-Path $root "tools\acc\src"
    $acc = Join-Path $tmp "acc.jar"
    $classes = Join-Path $tmp "classes"
    $dex = Join-Path $tmp "dex"
    $sources = @(
        (Join-Path $src "fox\Acc.java"),
        (Join-Path $src "android\test\RepetitiveTest.java")
    )

    $fresh = Test-Path $acc
    if ($fresh) {
        $accTime = (Get-Item $acc).LastWriteTimeUtc
        foreach ($s in $sources) {
            if ((Get-Item $s).LastWriteTimeUtc -gt $accTime) { $fresh = $false; break }
        }
    }

    if (-not $fresh) {
        Remove-Item -Recurse -Force $classes, $dex -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $classes, $dex | Out-Null

        $androidJar = Get-ChildItem (Join-Path $sdk "platforms\*\android.jar") -ErrorAction SilentlyContinue |
            Sort-Object { try { [int]($_.Directory.Name -replace '\D', '') } catch { 0 } } -Descending |
            Select-Object -First 1
        Assert ($null -ne $androidJar) "android.jar not found in $sdk\platforms"
        $uia = Join-Path $androidJar.Directory.FullName "uiautomator.jar"
        Assert (Test-Path $uia) "uiautomator.jar not found"
        $junit = Get-ChildItem (Join-Path $env:USERPROFILE ".gradle\caches") -Recurse -Filter "junit-4.13.2.jar" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        Assert ($null -ne $junit) "junit-4.13.2.jar not found in Gradle cache"
        $bt = Get-ChildItem (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
            Sort-Object { try { [version]$_.Name } catch { [version]"0.0" } } -Descending |
            Select-Object -First 1
        Assert ($bt) "build-tools not found in $sdk"
        $d8 = Join-Path $bt.FullName "lib\d8.jar"
        Assert (Test-Path $d8) "d8.jar not found in $($bt.FullName)"

        & (Join-Path $jdkBin "javac.exe") -source 8 -target 8 -encoding UTF-8 `
            -cp "$($androidJar.FullName);$uia;$($junit.FullName)" -d $classes @sources
        Assert ($LASTEXITCODE -eq 0) "javac failed"

        & (Join-Path $jdkBin "java.exe") -cp $d8 com.android.tools.r8.D8 --lib $androidJar.FullName `
            --output $dex (Join-Path $classes "fox\Acc.class") (Join-Path $classes "android\test\RepetitiveTest.class")
        Assert ($LASTEXITCODE -eq 0) "d8 failed"

        Push-Location $dex
        try {
            & (Join-Path $jdkBin "jar.exe") cf $acc classes.dex
        } finally {
            Pop-Location
        }
    }

    Invoke-Adb push $acc /data/local/tmp/acc.jar | Out-Null
}

function Wait-Text([string]$Text, [int]$TimeoutSec = 6) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if ((Invoke-Acc "hastext" @{ text = $Text }) -match "found=[1-9]") { return $true }
        Start-Sleep -Milliseconds 150
    }
    return $false
}

function Show-WirelessPage {
    Sh "am start -n com.android.settings/.Settings --es ':settings:show_fragment' 'com.android.settings.development.WirelessDebuggingFragment'" | Out-Null
    Wait-Text "Беспроводн" 8 | Out-Null
}

function Ensure-Unlocked {
    Sh "svc power stayon true" | Out-Null
    Sh "wm dismiss-keyguard" | Out-Null
    Assert ((Sh "dumpsys power | grep mWakefulness") -match "mWakefulness=Awake") "Телефон спит: разбуди и разблокируй его, затем повтори команду"
    $ui = Join-Path $tmp "unlock.xml"
    Sh "uiautomator dump /sdcard/ui.xml" | Out-Null
    Invoke-Adb pull /sdcard/ui.xml $ui | Out-Null
    if (Test-Path $ui) {
        [xml]$locked = Get-Content -Raw -Encoding UTF8 $ui
        $pin = $locked.SelectSingleNode("//node[@resource-id='com.android.systemui:id/pinEntry']")
        $keyguard = $locked.SelectSingleNode("//node[contains(@resource-id,'keyguard')]")
        Assert (($null -eq $pin) -and ($null -eq $keyguard)) "Телефон заблокирован: разблокируй его и оставь экран включённым"
    }
    Sh "cmd statusbar collapse" | Out-Null
}

function Ensure-NotificationPermission {
    $dump = Sh "dumpsys package $pkg"
    if ($dump -match "POST_NOTIFICATIONS: granted=true") { return }
    Sh "am start -S -n $pkg/.MainActivity" | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Acc "clicktext" @{ text = "отладки" } | Out-Null
    Start-Sleep -Seconds 1
    Invoke-Acc "clickid" @{ id = "com.android.permissioncontroller:id/permission_allow_button" } | Out-Null
    Start-Sleep -Seconds 1
    $dump = Sh "dumpsys package $pkg"
    Assert ($dump -match "POST_NOTIFICATIONS: granted=true") "Не удалось выдать разрешение на уведомления"
}

function Ensure-WirelessDebugging {
    Show-WirelessPage
    if ((Sh "settings get global adb_wifi_enabled") -ne "1") {
        $r = Invoke-Acc "clicktext" @{ text = "Беспроводная" }
        $deadline = (Get-Date).AddSeconds(6)
        while ((Get-Date) -lt $deadline -and (Sh "settings get global adb_wifi_enabled") -ne "1") {
            Start-Sleep -Milliseconds 200
        }
        Assert ((Sh "settings get global adb_wifi_enabled") -eq "1") "Не удалось включить беспроводную отладку. $r"
    }
}

function Get-Pairing {
    $r = Invoke-Acc "pairinfo"
    $m = [regex]::Match($r, "CODE=(\d{6}) IP=([0-9.]+):(\d+)")
    if ($m.Success) {
        return [pscustomobject]@{ Ip = $m.Groups[2].Value; Port = [int]$m.Groups[3].Value; Code = $m.Groups[1].Value }
    }
    return $null
}

function Open-PairingDialog {
    Invoke-Acc "back" | Out-Null
    Start-Sleep -Milliseconds 250
    for ($i = 0; $i -lt 6; $i++) {
        Show-WirelessPage
        $r = Invoke-Acc "clicktext" @{ text = "6-значного" }
        if ($r -notmatch "found=[1-9]") {
            Invoke-Acc "clicktext" @{ text = "ОК" } | Out-Null
            Start-Sleep -Milliseconds 400
            continue
        }
        $deadline = (Get-Date).AddSeconds(4)
        while ((Get-Date) -lt $deadline) {
            $pair = Get-Pairing
            if ($null -ne $pair) { return $pair }
            Start-Sleep -Milliseconds 150
        }
    }
    throw "Не удалось открыть окно с кодом спаривания"
}

function Start-PairService($Pair) {
    $input = "$($Pair.Port) $($Pair.Code)"
    $cmd = "am start-foreground-service -n $pkg/.PairService -a $pkg.action.PAIR --es pair_input '$input'"
    $out = Sh $cmd
    Assert ($out -match "Starting service") "Не удалось запустить PairService: $out"
}

function Wait-Paired {
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $n = Sh "dumpsys notification --noredact"
        if ($n -match "android\.title=String \(Спарено\)") { return }
        if ($n -match "android\.title=String \(Спаривание не удалось\)") {
            $m = [regex]::Match($n, "android\.text=String \((.*?)\)")
            throw "Спаривание не удалось: $($m.Groups[1].Value)"
        }
        Start-Sleep -Milliseconds 400
    }
    throw "Спаривание не завершилось за 90 секунд"
}

function Show-App {
    Sh "am start -n $pkg/.MainActivity" | Out-Null
    Start-Sleep -Seconds 1
}

function Get-ServerToken {
    $prefs = Sh "run-as $pkg cat shared_prefs/foxhole.xml"
    $m = [regex]::Match($prefs, '<string name="token">([^<]+)</string>')
    if ($m.Success) { return $m.Groups[1].Value.Trim() }
    return ""
}

function Send-ServerCommand([string]$Token, [string]$Command) {
    $localPort = 37183
    Invoke-Adb forward "tcp:$localPort" "tcp:27183" | Out-Null
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect("127.0.0.1", $localPort)
        $stream = $client.GetStream()
        $writer = New-Object System.IO.StreamWriter($stream)
        $writer.NewLine = "`n"
        $writer.AutoFlush = $true
        $writer.WriteLine($Token)
        $writer.WriteLine($Command)
        $reader = New-Object System.IO.StreamReader($stream)
        $line = $reader.ReadLine()
        $writer.Dispose()
        $reader.Dispose()
        return $line
    } finally {
        $client.Close()
        Invoke-Adb forward --remove "tcp:$localPort" | Out-Null
    }
}

function Wait-Server([int]$Seconds = 25) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    $net = ""
    while ((Get-Date) -lt $deadline) {
        $net = Sh "netstat -an | grep 27183"
        if ($net -match "LISTEN") { return $true }
        Start-Sleep -Milliseconds 400
    }
    return $false
}

function Test-Server {
    Sh "am start -n $pkg/.MainActivity --ez auto_start true" | Out-Null
    Assert (Wait-Server) "Сервер Foxhole не слушает 27183"
    $token = Get-ServerToken
    if ($token) {
        $line = Send-ServerCommand $token "ping"
        Assert ($line -match "OK pong") "Сервер не ответил на ping: $line"
    }
}

# Запуск сервера командой с ПК: приложение создаёт start.sh, компьютер запускает его по ADB.
function Test-PcScript {
    $path = "/storage/emulated/0/Android/data/$pkg/files/start.sh"

    Show-App
    for ($i = 0; $i -lt 3; $i++) {
        Invoke-Acc "clicktext2" @{ text = "Запуск с ПК" } | Out-Null
        Start-Sleep -Milliseconds 500
        if ((Invoke-Acc "hastext" @{ text = "Создать файл запуска" }) -match "found=[1-9]") { break }
    }
    Invoke-Acc "clicktext2" @{ text = "Создать файл запуска" } | Out-Null
    Start-Sleep -Seconds 2

    Assert ((Sh "test -f '$path' && echo ok") -match "ok") "start.sh не создан приложением"
    Assert ((Sh "cat '$path'") -match "app_process") "start.sh без app_process"

    $out = (& $adb -s $Serial shell "sh '$path'" 2>&1 | Out-String)
    Assert (Wait-Server) "Сервер не поднялся командой с ПК: $out"
    $token = Get-ServerToken
    Assert ($token) "Токен сервера не найден"
    $line = Send-ServerCommand $token "ping"
    Assert ($line -match "OK pong") "Сервер не ответил на ping: $line"

    Send-ServerCommand $token "stop" | Out-Null
    Start-Sleep -Seconds 1
    Assert ((Sh "netstat -an | grep 27183") -notmatch "LISTEN") "stop не остановил сервер"
    Write-Host "Запуск с ПК: сервер поднят и остановлен командой sh start.sh"
}

function Test-PcPair($Pair) {
    $out = (& $adb pair "$($Pair.Ip):$($Pair.Port)" $Pair.Code 2>&1 | Out-String)
    if ($out -notmatch "Successfully paired|Already paired") {
        Show-WirelessPage
        $Pair = Open-PairingDialog
        $out = (& $adb pair "$($Pair.Ip):$($Pair.Port)" $Pair.Code 2>&1 | Out-String)
    }
    Assert ($out -match "Successfully paired|Already paired") "adb pair не сработал: $out"

    $mdns = (& $adb mdns services 2>&1 | Out-String)
    $m = [regex]::Match($mdns, "_adb-tls-connect\._tcp\s+(\S+?):(\d+)")
    if ($m.Success) {
        $connect = "$($m.Groups[1].Value):$($m.Groups[2].Value)"
        & $adb connect $connect 2>&1 | Out-Null
        $devices = (& $adb devices 2>&1 | Out-String)
        Assert ($devices -match [regex]::Escape($m.Groups[1].Value)) "adb connect не подключился: $devices"
    }
}

if (-not $Serial) {
    for ($i = 0; $i -lt 20 -and -not $Serial; $i++) {
        $line = (& $adb devices) | Where-Object { $_ -match "\sdevice$" } | Select-Object -First 1
        if ($line) {
            $Serial = ($line -split "\s+")[0]
        } else {
            & $adb reconnect 2>&1 | Out-Null
            Start-Sleep -Seconds 1
        }
    }
}
Assert ($Serial) "Устройство не найдено"

Ensure-Unlocked
if (-not $SkipBuild) {
    Push-Location $root
    try {
        .\gradlew.bat testDebugUnitTest assembleDebug --console=plain
        Assert ($LASTEXITCODE -eq 0) "Сборка не удалась"
        & $adb -s $Serial install -r (Join-Path $root "app\build\outputs\apk\debug\app-debug.apk") | Out-Null
    } finally {
        Pop-Location
    }
}
Ensure-Acc
Ensure-NotificationPermission

Test-PcScript

Ensure-WirelessDebugging
Ensure-Unlocked

$pair = Open-PairingDialog
Write-Host "Окно спаривания: $($pair.Ip):$($pair.Port), код $($pair.Code)"
Start-PairService $pair
Wait-Paired
Test-Server
Write-Host "Foxhole спарен, сервер слушает 27183"

if (-not $NoPcPair) {
    Ensure-Unlocked
    $pcPair = Open-PairingDialog
    Test-PcPair $pcPair
    Write-Host "ADB с ПК спарен"
}

Invoke-Acc "back" | Out-Null
Sh "cmd statusbar collapse" | Out-Null

Write-Host "E2E OK"
