# 0007 — Expo (React Native) para Android e iOS + PWA

- Estado: aceptado
- Fecha: 2026-10-08

## Decisión
- App nativa con **Expo SDK 57 + Expo Router**, builds con **EAS**: APK (perfil `preview`),
  AAB (`production`) e IPA/TestFlight.
- La web Next.js es además **PWA instalable** (manifest + service worker que nunca cachea la API).
- Seguridad móvil (MASVS): `expo-secure-store` con `WHEN_UNLOCKED_THIS_DEVICE_ONLY`,
  `allowBackup=false`, biometría (`expo-local-authentication`) y bloqueo tras 5 min de
  inactividad.

## Consecuencias
- Medición AR/LiDAR (ARKit/ARCore) requerirá módulos nativos propios con development build
  (Fase 4); Expo lo soporta mediante config plugins.
