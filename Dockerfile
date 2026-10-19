# Imagen de CIVIA AI (API + interfaz) en un solo contenedor.
# Compila la capa de IA (CIVIA-IA) y toma la interfaz (CIVIA-FRONTEND) desde GitHub.
#   docker build -t civia .                                   (usa main)
#   docker build -t civia --build-arg CIVIA_REF=develop .     (otra rama)
#   docker run -p 8080:8080 -e ANTHROPIC_API_KEY=... civia

FROM eclipse-temurin:24-jdk AS build
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

FROM eclipse-temurin:24-jre
RUN useradd --system --uid 10001 civia && mkdir -p /app/data && chown civia /app/data
WORKDIR /app
COPY --from=build /src/CIVIA-BACKEND/target/civia-backend-1.0.0.jar app.jar
COPY --from=build /src/CIVIA-FRONTEND/web web
USER civia
ENV CIVIA_WEB_DIR=/app/web \
    CIVIA_STORAGE_DIR=/app/data/uploads \
    CIVIA_KEYS_DIR=/app/data/keys \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
