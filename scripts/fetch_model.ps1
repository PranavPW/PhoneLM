# fetch_model.ps1 - PhoneLM bundled-model fetcher (DECISIONS.md D4)
# Downloads Qwen2.5-0.5B-Instruct Q4_K_M GGUF into app/src/main/assets/models/
# The file is gitignored and NEVER committed. assembleDebug works without it.
#
# Usage:
#   .\scripts\fetch_model.ps1                     # download from Hugging Face
#   .\scripts\fetch_model.ps1 -SourcePath <path>  # copy from a local GGUF file
param(
    [string]$SourcePath = ""
)

$ErrorActionPreference = "Stop"

$destDir = Join-Path $PSScriptRoot "..\app\src\main\assets\models"
$modelName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
$url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/$modelName"
$destFile = Join-Path $destDir $modelName

if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
}

if ($SourcePath -ne "") {
    if (-not (Test-Path $SourcePath)) { throw "SourcePath not found: $SourcePath" }
    Copy-Item $SourcePath $destFile -Force
    Write-Output "Copied local model -> $destFile"
} else {
    if (Test-Path $destFile) {
        $size = (Get-Item $destFile).Length
        if ($size -gt 400MB) {
            Write-Output "Model already present ($([math]::Round($size/1MB,1)) MB): $destFile"
            exit 0
        }
    }
    Write-Output "Downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $destFile -UseBasicParsing
}

$finalSize = (Get-Item $destFile).Length
if ($finalSize -lt 100MB) { throw "Downloaded file suspiciously small: $finalSize bytes" }
Write-Output ("OK: {0} ({1:N1} MB)" -f $destFile, ($finalSize/1MB))
