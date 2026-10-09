# 0006 — Sistema de diseño compartido web/móvil

- Estado: aceptado
- Fecha: 2026-10-08

## Decisión
- `packages/ui` es la única fuente de verdad de color, tipografía, espaciado, radios,
  movimiento y semáforo de estados. La web consume `tokens.css` (generado y verificado por
  test); el móvil importa los tokens TypeScript directamente.
- Iconos propios de ingeniería definidos como datos de trazado SVG: un solo set para
  `<svg>` (web) y `react-native-svg` (móvil).
- Tests automáticos de contraste **WCAG 2.2 AA** para todas las combinaciones de texto y
  estados en modo claro y oscuro: un color ilegible rompe el CI.
- Dirección visual: papel de plano (cuadrícula), ejes A-B-C/1-2-3, líneas de cota, azul
  blueprint para marca, naranja seguridad solo para la acción principal (con texto oscuro,
  como la señalización de obra), estados que nunca dependen solo del color.

## Consecuencias
- Storybook se incorporará cuando el catálogo de componentes crezca (Fase 1).
- En móvil se usan fuentes del sistema (Menlo/monospace para datos técnicos) en Fase 0.
