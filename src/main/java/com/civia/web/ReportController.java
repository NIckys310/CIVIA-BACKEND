package com.civia.web;

import com.civia.ai.Recommendation;
import com.civia.ai.report.PdfReport;
import com.civia.service.AiQuota;
import com.civia.service.AiService;
import com.civia.service.ModelQuantities;
import com.civia.service.ProjectAccess;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Informe del proyecto en PDF: ficha, avance por fases, tareas, presupuesto, archivos, cantidades
 * del modelo 3D y lo que CIVIA recomienda revisar.
 */
@RestController
@RequestMapping("/api/v1/projects/{id}")
@Tag(name = "Informes")
public class ReportController {

    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", CO);
    private static final Map<String, String> STATUS = Map.of("PLANNING", "Planeación", "DESIGN", "Diseño", "PERMITS", "Licencias",
            "CONSTRUCTION", "Construcción", "COMPLETED", "Terminado", "ON_HOLD", "En pausa");
    private static final Map<String, String> TASK_STATUS = Map.of("TODO", "Pendiente", "IN_PROGRESS", "En curso", "REVIEW", "Revisión", "DONE", "Hecha");
    private static final Map<String, String> PRIORITY = Map.of("URGENT", "Urgente", "HIGH", "Alta", "MEDIUM", "Media", "LOW", "Baja");
    private static final Map<String, String> MATERIAL = Map.of("concreto", "Concreto", "acero", "Acero", "madera", "Madera", "vidrio", "Vidrio",
            "ladrillo", "Ladrillo", "bloque", "Bloque de concreto", "pintura", "Pañete y pintura", "teja", "Teja de barro");
    private static final Map<String, String> KIND = Map.of("PLAN", "Plano", "MODEL_3D", "Modelo 3D", "PHOTO", "Foto", "DOCUMENT", "Documento");

    private final ProjectAccess access;
    private final ProjectController projects;
    private final WorkController work;
    private final BudgetController budget;
    private final DocumentController documents;
    private final ModelController model;
    private final AiService ai;
    private final AiQuota quota;

    public ReportController(ProjectAccess access, ProjectController projects, WorkController work, BudgetController budget,
                            DocumentController documents, ModelController model, AiService ai, AiQuota quota) {
        this.access = access;
        this.projects = projects;
        this.work = work;
        this.budget = budget;
        this.documents = documents;
        this.model = model;
        this.ai = ai;
        this.quota = quota;
    }

    @Operation(summary = "Informe del proyecto en PDF")
    @GetMapping(value = "/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> report(@PathVariable UUID id) {
        access.view(id);
        quota.acquire(access.me().getId());
        ProjectDto p = projects.get(id);
        PdfReport pdf = new PdfReport("Informe de proyecto", p.name() + (p.code() == null ? "" : " · " + p.code()));

        pdf.metrics(List.of(
                new String[] {"Estado", STATUS.getOrDefault(p.status(), p.status()), p.phase() == null ? "" : p.phase()},
                new String[] {"Área construida", p.builtArea() == null ? "—" : number(p.builtArea(), 0), "m²"},
                new String[] {"Tareas abiertas", String.valueOf(p.stats().openTasks()), "de las tareas del proyecto"},
                new String[] {"Archivos", String.valueOf(p.stats().plans() + p.stats().models() + p.stats().photos() + p.stats().documents()),
                        p.stats().plans() + " planos · " + p.stats().models() + " modelos"}));

        pdf.heading("Ficha del proyecto");
        Map<String, String> sheet = new LinkedHashMap<>();
        put(sheet, "Tipo", p.type());
        put(sheet, "Ubicación", join(p.location(), p.address()));
        put(sheet, "Cliente", p.clientName());
        put(sheet, "Responsable", p.ownerName());
        put(sheet, "Área del lote", p.lotArea() == null ? null : number(p.lotArea(), 0) + " m²");
        put(sheet, "Pisos", p.floors() == null ? null : String.valueOf(p.floors()));
        put(sheet, "Presupuesto estimado", p.budgetEstimate() == null ? null : money(p.budgetEstimate(), p.currency()));
        put(sheet, "Inicio", p.startDate() == null ? null : p.startDate().format(DATE));
        put(sheet, "Entrega prevista", p.dueDate() == null ? null : p.dueDate().format(DATE));
        put(sheet, "Integrantes", String.valueOf(p.stats().members()));
        pdf.keyValues(sheet);
        if (p.description() != null && !p.description().isBlank()) pdf.paragraph(p.description());

        List<PhaseDto> phases = projects.listPhases(id);
        if (!phases.isEmpty()) {
            pdf.heading("Fases");
            List<String[]> rows = new ArrayList<>();
            for (PhaseDto f : phases) {
                rows.add(new String[] {f.name(), phaseStatus(f.status()),
                        f.startDate() == null ? "—" : f.startDate().format(DATE), f.endDate() == null ? "—" : f.endDate().format(DATE)});
            }
            pdf.table(List.of("Fase", "Estado", "Inicio", "Fin"), rows, new float[] {0.4f, 0.2f, 0.2f, 0.2f});
        }

        List<Recommendation> recs = ai.recommendations(id);
        if (!recs.isEmpty()) {
            pdf.heading("Lo que CIVIA recomienda revisar");
            pdf.bullets(recs.stream().limit(10).map(r -> "[" + r.priority() + "] " + r.title() + ": " + r.detail()).toList());
        }

        List<TaskDto> tasks = work.list(id);
        if (!tasks.isEmpty()) {
            pdf.heading("Tareas");
            List<String[]> rows = new ArrayList<>();
            for (TaskDto t : tasks.stream().limit(60).toList()) {
                rows.add(new String[] {t.title(), TASK_STATUS.getOrDefault(t.status(), t.status()), PRIORITY.getOrDefault(t.priority(), t.priority()),
                        t.assigneeName() == null ? "—" : t.assigneeName(), t.dueDate() == null ? "—" : t.dueDate().format(DATE)});
            }
            pdf.table(List.of("Tarea", "Estado", "Prioridad", "Responsable", "Fecha"), rows, new float[] {0.36f, 0.15f, 0.13f, 0.2f, 0.16f});
            if (tasks.size() > 60) pdf.paragraph("… y " + (tasks.size() - 60) + " tareas más.");
        }

        BudgetDto b = budget.get(id);
        if (!b.chapters().isEmpty()) {
            pdf.heading("Presupuesto");
            List<String[]> rows = new ArrayList<>();
            for (ChapterDto c : b.chapters()) rows.add(new String[] {nz(c.code()), c.name(), money(c.subtotal(), b.currency())});
            rows.add(new String[] {"", "Costo directo", money(b.directCost(), b.currency())});
            rows.add(new String[] {"", "Administración", money(b.administration(), b.currency())});
            rows.add(new String[] {"", "Imprevistos", money(b.contingency(), b.currency())});
            rows.add(new String[] {"", "Utilidad", money(b.profit(), b.currency())});
            rows.add(new String[] {"", "IVA sobre la utilidad", money(b.taxOnProfit(), b.currency())});
            rows.add(new String[] {"", "TOTAL", money(b.total(), b.currency())});
            pdf.table(List.of("Código", "Capítulo", "Valor"), rows, new float[] {0.15f, 0.55f, 0.3f});
            if (b.costPerM2() != null) pdf.paragraph("Costo por metro cuadrado: " + money(BigDecimal.valueOf(b.costPerM2()), b.currency()) + " / m².");
        }

        ModelQuantities.Takeoff takeoff = model.quantities(id);
        if (takeoff.totals().elements() > 0) {
            pdf.heading("Cantidades del modelo 3D");
            var t = takeoff.totals();
            pdf.metrics(List.of(
                    new String[] {"Concreto", number(t.concreteM3(), 2), "m³"},
                    new String[] {"Acero estructural", number(t.steelKg(), 0), "kg"},
                    new String[] {"Mampostería", number(t.masonryM2(), 1), "m²"},
                    new String[] {"Puertas · ventanas", t.doors() + " · " + t.windows(), number(t.glassM2(), 1) + " m² de vidrio"}));
            List<String[]> rows = new ArrayList<>();
            for (ModelQuantities.Line l : takeoff.lines()) {
                rows.add(new String[] {l.label(), MATERIAL.getOrDefault(l.material(), l.material()), String.valueOf(l.count()), number(l.quantity(), 2) + " " + l.unit()});
            }
            pdf.table(List.of("Elemento", "Material", "Cantidad", "Medida"), rows, new float[] {0.3f, 0.25f, 0.15f, 0.3f});
        }

        List<DocumentDto> docs = documents.list(id, null);
        if (!docs.isEmpty()) {
            pdf.heading("Archivos del proyecto");
            List<String[]> rows = new ArrayList<>();
            for (DocumentDto d : docs.stream().limit(40).toList()) {
                rows.add(new String[] {d.name(), KIND.getOrDefault(d.kind(), d.kind()), "v" + d.version(),
                        d.lastAnalysis() == null || d.lastAnalysis().completeness() == null ? "—" : d.lastAnalysis().completeness() + " %"});
            }
            pdf.table(List.of("Archivo", "Tipo", "Versión", "Revisión IA"), rows, new float[] {0.5f, 0.2f, 0.12f, 0.18f});
        }

        pdf.callout("Alcance del informe", List.of(
                "Generado automáticamente con los datos del proyecto en CIVIA AI el " + LocalDate.now().format(DATE) + ".",
                "Las recomendaciones y cantidades son orientativas y deben revisarse por el profesional responsable."));

        String file = "informe-" + (p.code() != null ? p.code() : p.name()).replaceAll("[^\\p{L}\\p{N}]+", "-").toLowerCase(CO) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file, StandardCharsets.UTF_8).build().toString())
                .body(pdf.toBytes());
    }

    private static String phaseStatus(String s) {
        return switch (s == null ? "" : s) {
            case "DONE", "COMPLETED" -> "Terminada";
            case "IN_PROGRESS", "ACTIVE" -> "En curso";
            default -> "Pendiente";
        };
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " · " + b;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String number(double v, int decimals) {
        NumberFormat f = NumberFormat.getNumberInstance(CO);
        f.setMaximumFractionDigits(decimals);
        f.setMinimumFractionDigits(decimals);
        return f.format(v);
    }

    private static String money(BigDecimal v, String currency) {
        if (v == null) return "—";
        String c = currency == null ? "COP" : currency;
        return "$ " + number(v.doubleValue(), "COP".equals(c) ? 0 : 2) + " " + c;
    }
}
