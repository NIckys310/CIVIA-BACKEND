# Imagen de CIVIA AI (API + interfaz) en un solo contenedor.
# Compila la capa de IA (CIVIA-IA) y toma la interfaz (CIVIA-FRONTEND) desde GitHub.
#   docker build -t civia .                                   (usa main)
#   docker build -t civia --build-arg CIVIA_REF=develop .     (otra rama)
#   docker run -p 8080:8080 -e ANTHROPIC_API_KEY=... civia

FROM eclipse-temurin:21-jdk AS build
# Rama de CIVIA-IA y CIVIA-FRONTEND a compilar. `main` siempre existe; las ramas de trabajo
# se borran al fusionar su PR, así que no deben usarse como valor por defecto.
ARG CIVIA_REF=main
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /src
RUN git clone --depth 1 --branch "$CIVIA_REF" https://github.com/NIckys310/CIVIA-IA.git CIVIA-IA \
 && git clone --depth 1 --branch "$CIVIA_REF" https://github.com/NIckys310/CIVIA-FRONTEND.git CIVIA-FRONTEND
RUN cd CIVIA-IA && ./mvnw -B -q install -DskipTests
COPY . CIVIA-BACKEND
RUN cd CIVIA-BACKEND && ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 civia && mkdir -p /app/data && chown civia /app/data
WORKDIR /app
# El jar se extrae (lib/ aparte) para poder usar CDS: arranca más rápido que el jar anidado.
COPY --from=build /src/CIVIA-BACKEND/target/civia-backend-1.0.0.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app/app && rm /tmp/app.jar
COPY --from=build /src/CIVIA-FRONTEND/web web

# Arranque en frío: el plan gratuito de Render da ~0,1 CPU y Spring Boot tardaba más de 3 min
# en despertar, más que los 120 s que Vercel espera antes de responder 502.
# CDS (Class Data Sharing): un arranque de entrenamiento con H2 en memoria guarda las clases ya
# cargadas en app.jsa y los arranques reales las leen de ahí. Si el entrenamiento fallara, la
# imagen se construye igual y la JVM arranca sin el archivo (-Xshare:auto, el valor por defecto).
RUN mkdir -p /tmp/cds \
 && (cd /tmp/cds && CIVIA_WEB_DIR=/app/web CIVIA_STORAGE_DIR=/tmp/cds/uploads CIVIA_KEYS_DIR=/tmp/cds/keys \
      java -XX:+UseSerialGC -XX:ArchiveClassesAtExit=/app/app.jsa -Dspring.context.exit=onRefresh \
           -Dspring.datasource.url=jdbc:h2:mem:cds -Dlogging.level.root=warn -jar /app/app/app.jar \
      || echo "CDS: entrenamiento omitido; la API arrancará sin el archivo.") \
 && rm -rf /tmp/cds

USER civia
# -XX:TieredStopAtLevel=1: solo el compilador rápido (C1). Con tan poca CPU, el compilador C2
# competía con el arranque; para esta API el rendimiento de C1 sobra.
ENV CIVIA_WEB_DIR=/app/web \
    CIVIA_STORAGE_DIR=/app/data/uploads \
    CIVIA_KEYS_DIR=/app/data/keys \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:SharedArchiveFile=/app/app.jsa"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app/app.jar"]
