# Generar APK / AAB / IPA (EAS Build)

Requisitos: cuenta de Expo (gratuita) y, para iOS, cuenta de Apple Developer.

```bash
npm i -g eas-cli
cd apps/mobile
eas login
eas init            # vincula el proyecto y escribe el projectId en app.json
```

## Android

```bash
npm run build:android:preview     # APK instalable directamente (distribución interna)
eas build -p android --profile production   # AAB para Google Play
```

El enlace de descarga del APK aparece al terminar el build. Las claves de firma las genera
y custodia EAS (o se suben las propias con `eas credentials`); **nunca** se guardan en el repo.

## iOS

```bash
npm run build:ios:preview         # IPA para dispositivos registrados (ad hoc)
eas build -p ios --profile production && eas submit -p ios   # TestFlight / App Store
```

## Variables por entorno

`EXPO_PUBLIC_API_URL` se define por perfil en `eas.json`. Los valores de staging y
producción de este archivo son marcadores (`*.civia.example`): reemplázalos por los
dominios reales antes del primer build distribuible.

## Desarrollo en un dispositivo o emulador

```bash
npm run dev -w @civia/mobile          # escanea el QR con Expo Go o un development build
```

- Emulador Android: la API local se alcanza en `http://10.0.2.2:<puerto>`.
- Teléfono físico en la misma red: usa la IP LAN del PC y arranca la API escuchando en
  esa interfaz.
