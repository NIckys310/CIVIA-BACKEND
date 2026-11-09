package com.civia.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Cómputo de las estructuras del catálogo ampliado del modelador (cimentaciones, cerchas, muros
 * cortina, cubiertas especiales, escaleras, etc.). Debe coincidir con {@code structureQuantity} del
 * archivo {@code structures.js} del frontend.
 */
final class ModelStructures {

    enum Bucket { VOLUME, WALL, ROOF, FINISH, GLASS, DOOR, WINDOW }

    /** Renglón donde se suma, cantidad y espesor (solo para muros de concreto). */
    record Measure(Bucket bucket, double quantity, double thickness) {
        static Measure of(Bucket bucket, double quantity) {
            return new Measure(bucket, quantity, 0);
        }

        String unit() {
            return bucket == Bucket.VOLUME ? "m³" : "m²";
        }
    }

    static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("strip_footing", "Zapatas corridas"), Map.entry("grade_beam", "Vigas de cimentación"),
            Map.entry("raft", "Losas de cimentación"), Map.entry("pile", "Pilotes"), Map.entry("pile_cap", "Dados y encepados"),
            Map.entry("retaining_wall", "Muros de contención"), Map.entry("ribbed_slab", "Losas nervadas"), Map.entry("truss", "Cerchas"),
            Map.entry("arch", "Arcos"), Map.entry("portal", "Pórticos"), Map.entry("brace", "Arriostramientos"),
            Map.entry("cantilever", "Voladizos"), Map.entry("corbel", "Ménsulas"), Map.entry("partition", "Tabiques"),
            Map.entry("curtain_wall", "Muros cortina"), Map.entry("parapet", "Antepechos y parapetos"), Map.entry("lattice", "Celosías"),
            Map.entry("brise", "Parasoles"), Map.entry("chimney", "Chimeneas"), Map.entry("duct", "Ductos técnicos"),
            Map.entry("ceiling", "Cielos rasos"), Map.entry("floor_finish", "Pisos y acabados"), Map.entry("cladding", "Enchapes de fachada"),
            Map.entry("shed_roof", "Cubiertas a un agua"), Map.entry("hip_roof", "Cubiertas a cuatro aguas"), Map.entry("flat_roof", "Cubiertas planas"),
            Map.entry("mansard", "Mansardas"), Map.entry("sawtooth", "Cubiertas en diente de sierra"), Map.entry("dome", "Cúpulas"),
            Map.entry("vault", "Bóvedas"), Map.entry("pergola", "Pérgolas"), Map.entry("canopy", "Marquesinas"), Map.entry("dormer", "Buhardillas"),
            Map.entry("double_door", "Puertas dobles"), Map.entry("sliding_door", "Puertas corredizas"), Map.entry("garage_door", "Puertas de garaje"),
            Map.entry("sliding_window", "Ventanas corredizas"), Map.entry("skylight", "Tragaluces"), Map.entry("l_stair", "Escaleras en L"),
            Map.entry("u_stair", "Escaleras en U"), Map.entry("spiral_stair", "Escaleras de caracol"), Map.entry("ramp", "Rampas"),
            Map.entry("landing", "Descansos"), Map.entry("railing", "Barandas"), Map.entry("balcony", "Balcones"), Map.entry("elevator", "Ascensores"));

    private ModelStructures() {}

    /** Cantidad del elemento, o {@code null} si el tipo no pertenece al catálogo ampliado. */
    static Measure measure(String type, String material, JsonNode p) {
        return switch (type) {
            case "strip_footing" -> volume(n(p, "width", 0.8) * n(p, "length", 6) * n(p, "thickness", 0.3));
            case "raft" -> volume(n(p, "width", 10) * n(p, "length", 12) * n(p, "thickness", 0.4));
            case "pile_cap" -> volume(n(p, "width", 1.5) * n(p, "length", 1.5) * n(p, "thickness", 0.8));
            case "landing" -> volume(n(p, "width", 1.2) * n(p, "length", 1.2) * n(p, "thickness", 0.15));
            case "grade_beam" -> volume(n(p, "b", 0.3) * n(p, "h", 0.5) * n(p, "length", 5));
            case "pile" -> volume(Math.PI * sq(n(p, "diameter", 0.5) / 2) * n(p, "length", 12));
            case "retaining_wall" -> volume(n(p, "length", 6) * (n(p, "height", 3) * n(p, "stem", 0.25) + n(p, "base", 2) * n(p, "baseT", 0.3)));
            case "ribbed_slab" -> {
                double width = n(p, "width", 6), length = n(p, "length", 8);
                yield volume(width * length * n(p, "topping", 0.05)
                        + Math.max(1, Math.floor(width / n(p, "spacing", 0.6))) * n(p, "ribB", 0.1) * n(p, "ribH", 0.25) * length);
            }
            case "truss" -> volume(trussLength(n(p, "span", 10), n(p, "height", 2), n(p, "panels", 8)) * sq(n(p, "size", 0.1)));
            case "arch" -> {
                double[] arc = arc(n(p, "span", 4), n(p, "rise", 2));
                double b = n(p, "b", 0.3), h = n(p, "h", 0.4);
                yield volume((arc[0] + h / 2) * arc[1] * b * h);
            }
            case "portal" -> {
                double b = n(p, "b", 0.4), h = n(p, "h", 0.5);
                yield volume(2 * b * b * (n(p, "height", 4) - h) + b * h * (n(p, "span", 6) + b));
            }
            case "brace" -> {
                double w = n(p, "width", 5), h = n(p, "height", 3);
                double length = switch (p.path("kind").asText("x")) {
                    case "diag" -> Math.hypot(w, h);
                    case "v" -> 2 * Math.hypot(w / 2, h);
                    default -> 2 * Math.hypot(w, h);
                };
                yield volume(length * sq(n(p, "size", 0.15)));
            }
            case "cantilever" -> volume(n(p, "width", 3) * n(p, "length", 2) * (n(p, "root", 0.25) + n(p, "tip", 0.12)) / 2);
            case "corbel" -> volume(n(p, "width", 0.4) * n(p, "depth", 0.4) * (n(p, "height", 0.5) + n(p, "tip", 0.2)) / 2);
            case "partition" -> wall(n(p, "length", 3) * n(p, "height", 2.4), n(p, "thickness", 0.1));
            case "parapet" -> wall(n(p, "length", 6) * n(p, "height", 1), n(p, "thickness", 0.15));
            case "chimney" -> wall(2 * (n(p, "width", 0.6) + n(p, "depth", 0.6)) * n(p, "height", 4), n(p, "wall", 0.12));
            case "duct" -> wall(2 * (n(p, "width", 1.2) + n(p, "depth", 1.2)) * n(p, "height", 3), n(p, "wall", 0.1));
            case "curtain_wall" -> Measure.of(Bucket.GLASS, n(p, "length", 6) * n(p, "height", 3.5));
            case "lattice" -> {
                double slat = n(p, "slat", 0.05), length = n(p, "length", 3);
                yield volume(Math.max(1, Math.floor(length / (slat + n(p, "gap", 0.1)))) * slat * n(p, "thickness", 0.1) * n(p, "height", 2.4));
            }
            case "brise" -> volume(Math.round(n(p, "count", 8)) * n(p, "length", 4) * n(p, "depth", 0.25) * 0.03);
            case "ceiling" -> Measure.of(Bucket.FINISH, n(p, "width", 5) * n(p, "length", 5));
            case "floor_finish" -> Measure.of(Bucket.FINISH, n(p, "width", 5) * n(p, "length", 5));
            case "cladding" -> Measure.of(Bucket.FINISH, n(p, "length", 4) * n(p, "height", 2.6));
            case "shed_roof" -> roof(n(p, "width", 6) * n(p, "length", 8) / Math.cos(Math.toRadians(n(p, "pitch", 10))));
            case "hip_roof" -> roof(n(p, "width", 8) * n(p, "length", 12) / Math.cos(Math.toRadians(n(p, "pitch", 25))));
            case "flat_roof" -> roof(n(p, "width", 8) * n(p, "length", 10));
            case "mansard" -> {
                double w = n(p, "width", 10), l = n(p, "length", 12);
                double i = Math.min(n(p, "inset", 1.5), Math.min(w, l) * 0.45);
                yield roof((2 * (w + l) - 4 * i) * Math.hypot(i, n(p, "lowerH", 2))
                        + (w - 2 * i) * (l - 2 * i) / Math.cos(Math.toRadians(n(p, "upperPitch", 15))));
            }
            case "sawtooth" -> {
                long teeth = Math.round(n(p, "teeth", 3));
                yield roof(teeth * Math.hypot(n(p, "width", 12) / teeth, n(p, "height", 1.5)) * n(p, "length", 10));
            }
            case "dome" -> {
                double a = n(p, "diameter", 8) / 2, r = Math.min(n(p, "rise", 4), a);
                yield roof(Math.PI * (a * a + r * r));
            }
            case "vault" -> {
                double[] arc = arc(n(p, "span", 6), n(p, "rise", 3));
                yield roof(arc[0] * arc[1] * n(p, "length", 10));
            }
            case "pergola" -> {
                double s = n(p, "post", 0.15), length = n(p, "length", 4);
                double rafters = Math.max(2, Math.floor(length / n(p, "spacing", 0.5)) + 1);
                yield volume(4 * s * s * n(p, "height", 2.6) + 2 * s * s * (length + s) + rafters * 0.15 * 0.05 * (n(p, "width", 3) + 0.4));
            }
            case "canopy" -> roof(n(p, "width", 3) * n(p, "depth", 1.5));
            case "dormer" -> roof(n(p, "width", 1.6) * n(p, "depth", 2) / Math.cos(Math.toRadians(n(p, "pitch", 30))));
            case "double_door" -> Measure.of(Bucket.DOOR, n(p, "width", 1.6) * n(p, "height", 2.1));
            case "sliding_door" -> Measure.of(Bucket.DOOR, n(p, "width", 2.4) * n(p, "height", 2.2));
            case "garage_door" -> Measure.of(Bucket.DOOR, n(p, "width", 3) * n(p, "height", 2.2));
            case "sliding_window" -> Measure.of(Bucket.WINDOW, n(p, "width", 1.8) * n(p, "height", 1.2));
            case "skylight" -> Measure.of(Bucket.GLASS, n(p, "width", 1.2) * n(p, "length", 1.2));
            case "l_stair" -> {
                double w = n(p, "width", 1.1);
                yield volume(flights(p, w) + w * w * 0.15);
            }
            case "u_stair" -> {
                double w = n(p, "width", 1.1);
                yield volume(flights(p, w) + (2 * w + 0.1) * w * 0.15);
            }
            case "spiral_stair" -> {
                double count = Math.round(n(p, "steps", 16)), r = n(p, "diameter", 2) / 2;
                yield volume(count * (Math.PI * r * r * (n(p, "turn", 360) / count / 360)) * 0.04);
            }
            case "ramp" -> volume(n(p, "width", 1.5) * n(p, "thickness", 0.15) * Math.hypot(n(p, "length", 6), n(p, "rise", 0.5)));
            case "railing" -> {
                double length = n(p, "length", 4), height = n(p, "height", 1);
                if ("vidrio".equals(material)) yield Measure.of(Bucket.GLASS, length * height);
                double area = 0.05 * 0.05;
                yield volume(2 * area * length + (Math.ceil(length / n(p, "spacing", 1.2)) + 1) * area * height);
            }
            case "balcony" -> volume(n(p, "width", 3) * n(p, "depth", 1.2) * n(p, "thickness", 0.12));
            case "elevator" -> volume(2 * (n(p, "width", 2) + n(p, "depth", 2)) * n(p, "height", 6) * n(p, "wall", 0.2));
            default -> null;
        };
    }

    private static Measure volume(double q) {
        return Measure.of(Bucket.VOLUME, q);
    }

    private static Measure roof(double q) {
        return Measure.of(Bucket.ROOF, q);
    }

    private static Measure wall(double area, double thickness) {
        return new Measure(Bucket.WALL, area, thickness);
    }

    private static double flights(JsonNode p, double width) {
        double riser = n(p, "riser", 0.175), tread = n(p, "tread", 0.28);
        return flight(Math.round(n(p, "stepsA", 8)), riser, tread, width) + flight(Math.round(n(p, "stepsB", 8)), riser, tread, width);
    }

    private static double flight(double steps, double riser, double tread, double width) {
        return (Math.hypot(steps * riser, (steps - 1) * tread) * 0.15 + steps * riser * tread / 2) * width;
    }

    /** Radio y ángulo central de un arco de luz {@code span} y flecha {@code rise} (máximo, semicircunferencia). */
    private static double[] arc(double span, double rise) {
        double r = Math.min(rise, span / 2);
        double rho = (span * span / 4 + r * r) / (2 * r);
        double a0 = Math.atan2(rho - r, span / 2);
        return new double[] {rho, Math.PI - 2 * a0};
    }

    /** Longitud total de barras de una cercha triangular tipo Howe con {@code panels} paneles. */
    private static double trussLength(double span, double height, double panelsRaw) {
        int n = (int) (Math.round(panelsRaw / 2) * 2);
        double dx = span / n;
        double total = span;
        for (int i = 0; i < n; i++) total += Math.hypot(dx, node(height, i + 1, n) - node(height, i, n));
        for (int i = 1; i < n; i++) total += node(height, i, n);
        for (int i = 1; i < n / 2; i++) total += Math.hypot(dx, node(height, i + 1, n));
        for (int i = n / 2 + 1; i < n; i++) total += Math.hypot(dx, node(height, i - 1, n));
        return total;
    }

    private static double node(double height, int i, int n) {
        return height * (1 - Math.abs((double) (2 * i) / n - 1));
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double n(JsonNode p, String field, double fallback) {
        return ModelQuantities.num(p, field, fallback);
    }
}
