package com.civia.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cómputo de cantidades del modelo 3D paramétrico: volumen de concreto, kilos de acero, metros
 * cuadrados de muro, vidrio y cubierta, y número de puertas y ventanas.
 *
 * <p>Esquema de cada elemento: {@code {"type": "column", "material": "concreto", "params": {...}}}
 * con dimensiones en metros. Las cantidades son brutas (sin descontar vanos ni traslapos).
 */
public final class ModelQuantities {

    public record Line(String type, String label, String material, long count, double quantity, String unit) {}

    public record Totals(double concreteM3, double steelKg, double masonryM2, double glassM2, double woodM3,
                         double roofM2, long doors, long windows, long elements) {}

    public record Takeoff(List<Line> lines, Totals totals) {}

    private static final double STEEL_DENSITY = 7850; // kg/m³

    private static final Map<String, String> LABELS = Map.of(
            "column", "Columnas", "beam", "Vigas", "wall", "Muros", "slab", "Losas", "footing", "Zapatas",
            "stair", "Escaleras", "door", "Puertas", "window", "Ventanas", "roof", "Cubiertas");

    private ModelQuantities() {}

    public static Takeoff of(JsonNode model) {
        Map<String, double[]> grouped = new LinkedHashMap<>(); // clave → {count, quantity}
        Map<String, String[]> meta = new LinkedHashMap<>();    // clave → {type, material, unit}
        double concrete = 0, steel = 0, masonry = 0, glass = 0, wood = 0, roof = 0;
        long doors = 0, windows = 0, elements = 0;

        JsonNode list = model == null ? null : model.path("elements");
        if (list != null && list.isArray()) {
            for (JsonNode e : list) {
                String type = e.path("type").asText("");
                String material = e.path("material").asText("concreto");
                JsonNode p = e.path("params");
                double qty;
                String unit;
                switch (type) {
                    case "column" -> {
                        double area = "circular".equals(p.path("shape").asText())
                                ? Math.PI * Math.pow(num(p, "b", 0.4) / 2, 2) : num(p, "b", 0.4) * num(p, "h", 0.4);
                        qty = area * num(p, "height", 3); unit = "m³";
                    }
                    case "beam" -> { qty = num(p, "b", 0.3) * num(p, "h", 0.5) * num(p, "length", 5); unit = "m³"; }
                    case "slab" -> { qty = num(p, "width", 5) * num(p, "length", 5) * num(p, "thickness", 0.15); unit = "m³"; }
                    case "footing" -> { qty = num(p, "width", 1.5) * num(p, "length", 1.5) * num(p, "thickness", 0.4); unit = "m³"; }
                    case "stair" -> {
                        double steps = num(p, "steps", 16), riser = num(p, "riser", 0.175), tread = num(p, "tread", 0.28), width = num(p, "width", 1.1);
                        double incline = Math.hypot(steps * riser, (steps - 1) * tread);
                        qty = (incline * 0.15 + steps * riser * tread / 2) * width; unit = "m³";
                    }
                    case "wall" -> { qty = num(p, "length", 5) * num(p, "height", 2.6); unit = "m²"; }
                    case "roof" -> {
                        double pitch = Math.toRadians(num(p, "pitch", 15));
                        qty = num(p, "width", 8) * num(p, "length", 10) / Math.cos(pitch); unit = "m²";
                    }
                    case "door", "window" -> { qty = num(p, "width", 0.9) * num(p, "height", 2.1); unit = "m²"; }
                    default -> { continue; }
                }
                elements++;
                switch (type) {
                    case "wall" -> {
                        if ("concreto".equals(material)) concrete += qty * num(p, "thickness", 0.15);
                        else masonry += qty;
                    }
                    case "roof" -> roof += qty;
                    case "door" -> doors++;
                    case "window" -> { windows++; glass += qty; }
                    default -> {
                        if ("acero".equals(material)) steel += qty * STEEL_DENSITY;
                        else if ("madera".equals(material)) wood += qty;
                        else concrete += qty;
                    }
                }
                String key = type + "|" + material;
                double[] g = grouped.computeIfAbsent(key, k -> new double[2]);
                g[0]++;
                g[1] += qty;
                meta.putIfAbsent(key, new String[] {type, material, unit});
            }
        }
        List<Line> lines = new ArrayList<>();
        grouped.forEach((key, g) -> {
            String[] m = meta.get(key);
            lines.add(new Line(m[0], LABELS.getOrDefault(m[0], m[0]), m[1], (long) g[0], round(g[1]), m[2]));
        });
        return new Takeoff(lines, new Totals(round(concrete), round(steel), round(masonry), round(glass), round(wood),
                round(roof), doors, windows, elements));
    }

    private static double num(JsonNode p, String field, double fallback) {
        JsonNode v = p.path(field);
        double d = v.isNumber() ? v.asDouble() : fallback;
        // Dimensiones absurdas (negativas o kilométricas) no deben inflar el presupuesto.
        return Double.isFinite(d) && d > 0 && d < 1000 ? d : fallback;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
