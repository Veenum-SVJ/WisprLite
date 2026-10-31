param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.:-]+$')][string]$Serial,
    [string]$AdbPath
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$AdbPath) { $AdbPath = Join-Path $root '.tools/android/sdk/platform-tools/adb.exe' }
$adb = (Resolve-Path -LiteralPath $AdbPath).Path
$logs = Join-Path $root ('.tools/device-tests/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force $logs | Out-Null
$component = 'com.veenum.wisprlite/com.edib.openwhispr.WhisperAccessibilityService'
function Invoke-Adb {
    param([string[]]$CommandArgs)
    $result = & $adb -s $Serial @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($CommandArgs[0])" }
    $result
}
function Set-OwnService {
    param([bool]$Enabled)
    $current = (Invoke-Adb @('shell','settings','get','secure','enabled_accessibility_services') | Out-String).Trim()
    if ($current -eq 'null') { $current = '' }
    if ($current -and $current -notmatch '^[A-Za-z0-9_./:]+$') { throw 'Unexpected accessibility component list' }
    $entries = @($current.Split(':') | Where-Object { $_ -and $_ -ne $component })
    if ($Enabled) { $entries += $component }
    if ($entries.Count) {
        Invoke-Adb @('shell','settings','put','secure','enabled_accessibility_services',($entries -join ':')) | Out-Null
        Invoke-Adb @('shell','settings','put','secure','accessibility_enabled','1') | Out-Null
    } else {
        Invoke-Adb @('shell','settings','delete','secure','enabled_accessibility_services') | Out-Null
        Invoke-Adb @('shell','settings','put','secure','accessibility_enabled','0') | Out-Null
    }
}
function Run-Suite {
    param([string]$Name, [string]$Classes, [string]$Foreground)
    $output = Join-Path $logs "$Name.txt"
    $errors = Join-Path $logs "$Name.stderr.txt"
    $arguments = @('-s',$Serial,'shell','am','instrument','-w','-e','class',$Classes,
        'com.veenum.wisprlite.test/androidx.test.runner.AndroidJUnitRunner')
    $process = Start-Process -FilePath $adb -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput $output -RedirectStandardError $errors
    $null = $process.Handle # Retain the handle so Windows PowerShell can read ExitCode after exit.
    try {
        if ($Foreground -and ($Foreground -ne 'activity' -or $needsForegroundLaunch)) {
            $start = Get-Date
            while (!$process.HasExited -and ((Get-Date) - $start).TotalSeconds -lt 10) {
                if ((Get-Content -LiteralPath $output -Raw -ErrorAction SilentlyContinue) -match 'com.edib.openwhispr') { break }
                Start-Sleep -Milliseconds 200
            }
            if ($Foreground -in @('service', 'service-web', 'service-activity')) {
                Set-OwnService $false
                Set-OwnService $true
            }
            if ($Foreground -in @('service', 'service-web')) {
                $webComposer = if ($Foreground -eq 'service-web') { 'true' } else { 'false' }
                Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','com.veenum.wisprlite.test/com.edib.openwhispr.DictationTargetActivity','--ez','webComposer',$webComposer) | Out-Null
            } elseif ($Foreground -ne 'service-activity' -or $needsForegroundLaunch) {
                # MIUI may deny the instrumentation process's background activity launch.
                Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10008000','-n','com.veenum.wisprlite/com.edib.openwhispr.MainActivity') | Out-Null
            }
        }
        $deadline = (Get-Date).AddSeconds(120)
        while (!$process.WaitForExit(500)) {
            if ((Get-Date) -gt $deadline) { throw "$Name timed out; see $output" }
        }
        $result = Get-Content -LiteralPath $output -Raw
        Write-Output $result
        if (($null -ne $process.ExitCode -and $process.ExitCode -ne 0) -or $result -notmatch 'OK \(\d+ tests?\)' -or $result -match 'FAILURES|INSTRUMENTATION_FAILED') { throw "$Name failed; see $output" }
    } finally {
        if (!$process.HasExited) {
            $process.Kill()
            Invoke-Adb @('shell','am','force-stop','com.veenum.wisprlite') | Out-Null
        }
    }
}
$manufacturer = (Invoke-Adb @('shell','getprop','ro.product.manufacturer') | Out-String).Trim()
$needsForegroundLaunch = $manufacturer -match 'xiaomi|redmi|poco'
$initial = (Invoke-Adb @('shell','settings','get','secure','enabled_accessibility_services') | Out-String).Trim()
$wasEnabled = $component -in $initial.Split(':')
try {
    Invoke-Adb @('shell','pm','revoke','com.veenum.wisprlite','android.permission.RECORD_AUDIO') | Out-Null
    Run-Suite 'microphone-permission-denied' 'com.edib.openwhispr.PermissionDeniedIntegrationTest' 'service'
    Invoke-Adb @('shell','pm','grant','com.veenum.wisprlite','android.permission.RECORD_AUDIO') | Out-Null
    $sdk = [int](Invoke-Adb @('shell','getprop','ro.build.version.sdk') | Out-String).Trim()
    if ($sdk -ge 33) { Invoke-Adb @('shell','pm','grant','com.veenum.wisprlite','android.permission.POST_NOTIFICATIONS') | Out-Null }
    Run-Suite 'storage-and-credentials' 'com.edib.openwhispr.HistoryRepositoryTest,com.edib.openwhispr.CredentialIsolationTest,com.edib.openwhispr.RetentionDeviceTest' ''
    Run-Suite 'startup-and-recreation' 'com.edib.openwhispr.StartupNavigationTest' 'activity'
    Run-Suite 'light-and-dark-appearance' 'com.edib.openwhispr.AppearanceIntegrationTest' 'activity'
    Run-Suite 'long-history-detail' 'com.edib.openwhispr.HistoryDetailIntegrationTest' 'activity'
    Run-Suite 'backup-picker-cancel' 'com.edib.openwhispr.BackupPickerIntegrationTest#cancellingDocumentPickerDiscardsPendingPassphraseAndSnapshot' 'activity'
    Run-Suite 'backup-picker-roundtrip' 'com.edib.openwhispr.BackupPickerIntegrationTest#exportedDocumentCanBeSelectedAndRestoredThroughAndroidPicker' 'activity'
    Run-Suite 'clipboard-fallback' 'com.edib.openwhispr.ClipboardFallbackIntegrationTest' 'service-activity'
    Run-Suite 'model-availability' 'com.edib.openwhispr.ModelAvailabilityIntegrationTest' 'service'
    Run-Suite 'microphone-and-insertion' 'com.edib.openwhispr.ServiceLifecycleIntegrationTest' 'service'
    Run-Suite 'web-composer-insertion' 'com.edib.openwhispr.ServiceLifecycleIntegrationTest' 'service-web'
    Write-Output "Device checks passed. Logs: $logs"
    Write-Output 'Speech accuracy, real cloud providers, optional model fixtures and airplane-mode testing are separate checks.'
} finally {
    Invoke-Adb @('shell','pm','grant','com.veenum.wisprlite','android.permission.RECORD_AUDIO') | Out-Null
    # Android marks an accessibility service disconnected after instrumentation force-stops
    # its process. Reapply the enabled state through a real off/on transition.
    if ($wasEnabled) {
        # Explicit launch clears the package's force-stopped state before rebinding.
        Invoke-Adb @('shell','am','start','-W','-n','com.veenum.wisprlite/com.edib.openwhispr.MainActivity') | Out-Null
        Set-OwnService $false
    }
    Set-OwnService $wasEnabled
    if ($wasEnabled) {
        Invoke-Adb @('shell','am','start','-W','-n','com.veenum.wisprlite.test/com.edib.openwhispr.DictationTargetActivity') | Out-Null
    }
}
