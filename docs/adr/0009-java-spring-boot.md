# 0009 · Migración a Java 21 (Spring Boot) y web sin framework

- **Estado:** aceptada (reemplaza 0001, 0002, 0003, 0006 y 0007, que describían el stack anterior en
  Python/TypeScript; se retiraron del repositorio y siguen disponibles en el historial de git)
- **Fecha:** 2026-10-16

## Contexto

El proyecto estaba escrito en Python (FastAPI) y TypeScript (Next.js + Expo). El equipo pidió que
todo el proyecto esté hecho en Java, que las funciones (modelos 3D, cámara, IA, biblioteca)
funcionen de verdad y que la aplicación cargue más rápido.

## Decisión

- **CIVIA-BACKEND:** Java 21 + Spring Boot 3.5 (Web, Security con OAuth2 Resource Server, Data JPA).
  H2 en archivo para desarrollo y PostgreSQL en producción con el mismo código.
- **CIVIA-IA:** librería Java (`com.civia:civia-ai`) con el SDK oficial de Anthropic para Java,
  PDFBox y un motor local (BM25 + reglas). Se incluye en el backend como dependencia.
- **CIVIA-FRONTEND:** el navegador solo ejecuta HTML/CSS/JavaScript, así que la interfaz es web
  estándar (módulos ES sin framework + Three.js) y el servidor del repositorio es Java
  (`server/CiviaWeb.java`, solo JDK).
- La app nativa Expo se retira: la PWA instalable cubre escritorio y móvil, incluida la cámara.

## Consecuencias

- Un solo lenguaje de servidor y un solo comando para arrancar (`run.sh` / `run.ps1`).
- Sin dependencias de Node en producción ni paso de build de la interfaz.
- Se pierde la Row-Level Security de PostgreSQL (0004); el aislamiento lo hace la capa de servicio
  (`ProjectAccess`, 404 para recursos ajenos) y está cubierto por pruebas de integración.
