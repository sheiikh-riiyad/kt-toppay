$ErrorActionPreference = "Stop"

Write-Host "Checking connected Android devices..." -ForegroundColor Cyan
$deviceLines = @(adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" })

if ($deviceLines.Count -eq 0) {
    Write-Error "No authorized Android device found. Connect your phone and enable USB debugging."
}

Write-Host "Building and installing TopPay..." -ForegroundColor Cyan
& .\gradlew.bat installDebug

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Launching TopPay..." -ForegroundColor Cyan
adb shell am force-stop com.toppay.org
adb shell am start -n com.toppay.org/.MainActivity

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "TopPay is running on your device." -ForegroundColor Green
