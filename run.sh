#!/usr/bin/env bash
# Arranca CIVIA AI (Linux/macOS/Git Bash). Requiere Java 21 y los repos clonados lado a lado.
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -d "$HOME/.m2/repository/com/civia/civia-ai/1.0.0" ]; then
  echo "Instalando la capa de IA (CIVIA-IA)..."
  (cd ../CIVIA-IA && ./mvnw -B -q install -DskipTests)
fi
[ -f target/civia-backend-1.0.0.jar ] || ./mvnw -B -q package -DskipTests
echo "CIVIA AI en http://localhost:8080"
exec java -jar target/civia-backend-1.0.0.jar
