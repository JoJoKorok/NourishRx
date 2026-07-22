<#
.SYNOPSIS
Installs or updates the NourishRx APK on a connected Android phone.

.DESCRIPTION
This script uses adb install -r so the app is replaced in place while keeping
local app data, as long as the APK has the same applicationId and signing key.

Build the app in Android Studio first, then run this script from PowerShell.
#>
[CmdletBinding()]
param(
    [string]$ApkPath = "",
    [string]$DeviceSerial = "",
    [switch]$Build
)

$ErrorActionPreference = "Stop"

function Get-ProjectRoot {
    $scriptRoot = $PSScriptRoot
    return (Resolve-Path -LiteralPath (Join-Path $scriptRoot "..")).Path
}

function Find-Adb {
    $candidates = New-Object System.Collections.Generic.List[string]

    if ($env:ANDROID_HOME) {
        $candidates.Add((Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"))
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates.Add((Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"))
    }
    if ($env:LOCALAPPDATA) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"))
    }

    $pathAdb = Get-Command adb -ErrorAction SilentlyContinue
    if ($pathAdb) {
        $candidates.Add($pathAdb.Source)
    }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "Could not find adb. Install Android Studio SDK Platform-Tools, then try again."
}

function Invoke-CheckedCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory = ""
    )

    if ($WorkingDirectory) {
        Push-Location -LiteralPath $WorkingDirectory
    }
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
        }
    } finally {
        if ($WorkingDirectory) {
            Pop-Location
        }
    }
}

function Build-DebugApk {
    param([string]$ProjectRoot)

    $gradlew = Join-Path $ProjectRoot "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradlew)) {
        Write-Host "No Gradle wrapper found. Build the app in Android Studio, then rerun this script."
        return
    }

    Write-Host "Building debug APK..."
    Invoke-CheckedCommand -FilePath $gradlew -Arguments @(":app:assembleDebug") -WorkingDirectory $ProjectRoot
}

function Find-LatestApk {
    param(
        [string]$ProjectRoot,
        [string]$RequestedApkPath
    )

    if ($RequestedApkPath) {
        if (-not (Test-Path -LiteralPath $RequestedApkPath)) {
            throw "APK not found: $RequestedApkPath"
        }
        return (Resolve-Path -LiteralPath $RequestedApkPath).Path
    }

    $outputs = Join-Path $ProjectRoot "app\build\outputs\apk"
    if (-not (Test-Path -LiteralPath $outputs)) {
        throw "No APK output folder found. Build or Run the app once in Android Studio, then rerun this script."
    }

    $apk = Get-ChildItem -LiteralPath $outputs -Recurse -Filter "*.apk" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $apk) {
        throw "No APK found. Build or Run the app once in Android Studio, then rerun this script."
    }

    return $apk.FullName
}

function Get-ConnectedDevice {
    param(
        [string]$Adb,
        [string]$RequestedSerial
    )

    $lines = & $Adb devices
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed. Check Android Studio Platform-Tools."
    }

    $devices = @()
    foreach ($line in $lines) {
        if ($line -match "^(\S+)\s+device$") {
            $devices += $Matches[1]
        }
    }

    if ($RequestedSerial) {
        if ($devices -notcontains $RequestedSerial) {
            throw "Device '$RequestedSerial' is not connected or not authorized."
        }
        return $RequestedSerial
    }

    if ($devices.Count -eq 0) {
        throw "No authorized phone found. Enable USB debugging, plug in your Pixel, and tap Allow on the phone."
    }

    $physicalDevices = @($devices | Where-Object { $_ -notlike "emulator-*" })
    if ($physicalDevices.Count -eq 1) {
        return $physicalDevices[0]
    }

    if ($devices.Count -eq 1) {
        return $devices[0]
    }

    if ($physicalDevices.Count -gt 1) {
        throw "More than one physical device is connected. Rerun with -DeviceSerial <serial>."
    }

    throw "More than one emulator is connected. Rerun with -DeviceSerial <serial>."
}

$projectRoot = Get-ProjectRoot
$adb = Find-Adb

if ($Build) {
    Build-DebugApk -ProjectRoot $projectRoot
}

$apk = Find-LatestApk -ProjectRoot $projectRoot -RequestedApkPath $ApkPath
$device = Get-ConnectedDevice -Adb $adb -RequestedSerial $DeviceSerial

Write-Host "Installing APK:"
Write-Host "  $apk"
Write-Host "To device:"
Write-Host "  $device"

$adbArgs = @("-s", $device, "install", "-r", $apk)
Invoke-CheckedCommand -FilePath $adb -Arguments $adbArgs

Write-Host "Done. Open NourishRx on your phone."
