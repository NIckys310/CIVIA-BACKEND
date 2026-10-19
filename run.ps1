# Arranca CIVIA AI en Windows (PowerShell). Requiere Java 21 y los repos clonados lado a lado:
#   CIVIA\CIVIA-IA   CIVIA\CIVIA-BACKEND   CIVIA\CIVIA-FRONTEND
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ia = Join-Path $here "..\CIVIA-IA"
if (-not (Test-Path (Join-Path $env:USERPROFILE ".m2\repository\com\civia\civia-ai\1.0.0"))) {
    Write-Host "Instalando la capa de IA (CIVIA-IA)..."
    Push-Location $ia; .\mvnw.cmd -B -q install -DskipTests; Pop-Location
}
Push-Location $here
if (-not (Test-Path "target\civia-backend-1.0.0.jar")) { .\mvnw.cmd -B -q package -DskipTests }
Write-Host "CIVIA AI en http://localhost:8080"
java -jar target\civia-backend-1.0.0.jar
Pop-Location
