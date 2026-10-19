package com.civia.web;

import com.civia.domain.BudgetChapter;
import com.civia.domain.BudgetItem;
import com.civia.domain.BudgetSettings;
import com.civia.domain.Project;
import com.civia.repo.BudgetChapterRepository;
import com.civia.repo.BudgetItemRepository;
import com.civia.repo.BudgetSettingsRepository;
import com.civia.service.AuditService;
import com.civia.service.ProjectAccess;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Presupuesto de obra por capítulos con AIU (administración, imprevistos, utilidad). */
@RestController
@RequestMapping("/api/v1/projects/{id}/budget")
@Tag(name = "Presupuesto")
public class BudgetController {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProjectAccess access;
    private final BudgetChapterRepository chapters;
    private final BudgetItemRepository items;
    private final BudgetSettingsRepository settings;
    private final AuditService audit;

    public BudgetController(ProjectAccess access, BudgetChapterRepository chapters, BudgetItemRepository items,
                            BudgetSettingsRepository settings, AuditService audit) {
        this.access = access;
        this.chapters = chapters;
        this.items = items;
        this.settings = settings;
        this.audit = audit;
    }

    @GetMapping
    @Transactional
    public BudgetDto get(@PathVariable UUID id) {
        return budget(access.view(id));
    }

    private BudgetSettings settingsFor(Project p) {
        return settings.findByProjectId(p.getId()).orElseGet(() -> {
            BudgetSettings s = new BudgetSettings();
            s.setProject(p);
            return settings.save(s);
        });
    }

    private BudgetDto budget(Project p) {
        List<BudgetItem> all = items.findByChapterProjectIdOrderByCodeAsc(p.getId());
        List<ChapterDto> out = new ArrayList<>();
        BigDecimal direct = BigDecimal.ZERO;
        for (BudgetChapter c : chapters.findByProjectIdOrderBySortOrderAsc(p.getId())) {
            List<ItemDto> its = all.stream().filter(i -> i.getChapter().getId().equals(c.getId())).map(BudgetController::item).toList();
            BigDecimal subtotal = its.stream().map(ItemDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            direct = direct.add(subtotal);
            out.add(new ChapterDto(c.getId(), c.getCode(), c.getName(), c.getSortOrder(), subtotal, its));
        }
        BudgetSettings s = settingsFor(p);
        BigDecimal admin = pct(direct, s.getAdministrationPct());
        BigDecimal contingency = pct(direct, s.getContingencyPct());
        BigDecimal profit = pct(direct, s.getProfitPct());
        BigDecimal tax = pct(profit, s.getTaxOnProfitPct());
        BigDecimal total = direct.add(admin).add(contingency).add(profit).add(tax);
        Double perM2 = p.getBuiltArea() != null && p.getBuiltArea() > 0
                ? total.divide(BigDecimal.valueOf(p.getBuiltArea()), 2, RoundingMode.HALF_UP).doubleValue() : null;
        return new BudgetDto(out, direct, admin, contingency, profit, tax, total, p.getCurrency(),
                new BudgetSettingsDto(s.getAdministrationPct(), s.getContingencyPct(), s.getProfitPct(), s.getTaxOnProfitPct()), perM2);
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal pct) {
        return pct == null ? BigDecimal.ZERO : base.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static ItemDto item(BudgetItem i) {
        BigDecimal total = i.getQuantity().multiply(i.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
        return new ItemDto(i.getId(), i.getChapter().getId(), i.getCode(), i.getDescription(), i.getUnit(), i.getQuantity(), i.getUnitPrice(), total);
    }

    @PutMapping("/settings")
    @Transactional
    public BudgetDto updateSettings(@PathVariable UUID id, @RequestBody BudgetSettingsDto req) {
        Project p = access.edit(id);
        BudgetSettings s = settingsFor(p);
        if (req.administrationPct() != null) s.setAdministrationPct(req.administrationPct());
        if (req.contingencyPct() != null) s.setContingencyPct(req.contingencyPct());
        if (req.profitPct() != null) s.setProfitPct(req.profitPct());
        if (req.taxOnProfitPct() != null) s.setTaxOnProfitPct(req.taxOnProfitPct());
        return budget(p);
    }

    @PostMapping("/chapters")
    @Transactional
    public BudgetDto addChapter(@PathVariable UUID id, @RequestBody ChapterRequest req) {
        Project p = access.edit(id);
        if (req.name() == null || req.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El capítulo necesita un nombre.");
        int order = req.sortOrder() != null ? req.sortOrder() : chapters.findByProjectIdOrderBySortOrderAsc(id).size() + 1;
        BudgetChapter c = new BudgetChapter();
        c.setProject(p);
        c.setName(req.name().strip());
        c.setCode(req.code() == null || req.code().isBlank() ? String.format("%02d", order) : req.code());
        c.setSortOrder(order);
        chapters.save(c);
        return budget(p);
    }

    @DeleteMapping("/chapters/{chapterId}")
    @Transactional
    public BudgetDto deleteChapter(@PathVariable UUID id, @PathVariable UUID chapterId) {
        Project p = access.edit(id);
        chapters.findById(chapterId).filter(c -> c.getProject().getId().equals(id)).ifPresent(c -> {
            items.deleteByChapterId(c.getId());
            chapters.delete(c);
        });
        return budget(p);
    }

    @PostMapping("/items")
    @Transactional
    public BudgetDto addItem(@PathVariable UUID id, @RequestBody ItemRequest req) {
        Project p = access.edit(id);
        BudgetChapter c = chapters.findById(req.chapterId()).filter(x -> x.getProject().getId().equals(id))
                .orElseThrow(() -> ProjectAccess.notFound("Capítulo"));
        BudgetItem i = new BudgetItem();
        i.setChapter(c);
        applyItem(i, req);
        if (i.getDescription() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ítem necesita una descripción.");
        items.save(i);
        audit.activity(p, access.me(), "budget.item", "budget", i.getId(), "Agregó al presupuesto «" + i.getDescription() + "»");
        return budget(p);
    }

    @PatchMapping("/items/{itemId}")
    @Transactional
    public BudgetDto updateItem(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody ItemRequest req) {
        Project p = access.edit(id);
        BudgetItem i = items.findById(itemId).filter(x -> x.getChapter().getProject().getId().equals(id))
                .orElseThrow(() -> ProjectAccess.notFound("Ítem"));
        applyItem(i, req);
        return budget(p);
    }

    @DeleteMapping("/items/{itemId}")
    @Transactional
    public BudgetDto deleteItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        Project p = access.edit(id);
        items.findById(itemId).filter(x -> x.getChapter().getProject().getId().equals(id)).ifPresent(items::delete);
        return budget(p);
    }

    /** Plantilla de capítulos típicos de una edificación. */
    @PostMapping("/template")
    @Transactional
    public BudgetDto template(@PathVariable UUID id) {
        Project p = access.edit(id);
        if (!chapters.findByProjectIdOrderBySortOrderAsc(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El presupuesto ya tiene capítulos.");
        }
        String[] names = {"Preliminares", "Cimentación", "Estructura", "Mampostería", "Cubierta", "Instalaciones hidrosanitarias",
                "Instalaciones eléctricas", "Pañetes y estucos", "Pisos y enchapes", "Carpintería", "Aparatos sanitarios",
                "Pintura", "Obras exteriores", "Aseo y entrega"};
        for (int k = 0; k < names.length; k++) {
            BudgetChapter c = new BudgetChapter();
            c.setProject(p);
            c.setName(names[k]);
            c.setCode(String.format("%02d", k + 1));
            c.setSortOrder(k + 1);
            chapters.save(c);
        }
        return budget(p);
    }

    private static void applyItem(BudgetItem i, ItemRequest r) {
        if (r.code() != null) i.setCode(r.code());
        if (r.description() != null && !r.description().isBlank()) i.setDescription(r.description().strip());
        if (r.unit() != null) i.setUnit(r.unit());
        if (r.quantity() != null) {
            if (r.quantity().signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad no puede ser negativa.");
            i.setQuantity(r.quantity());
        }
        if (r.unitPrice() != null) {
            if (r.unitPrice().signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio no puede ser negativo.");
            i.setUnitPrice(r.unitPrice());
        }
    }

    @GetMapping("/export.csv")
    @Transactional
    public org.springframework.http.ResponseEntity<String> export(@PathVariable UUID id) {
        BudgetDto b = budget(access.view(id));
        StringBuilder csv = new StringBuilder("﻿Código;Descripción;Unidad;Cantidad;Valor unitario;Total\n");
        for (ChapterDto c : b.chapters()) {
            csv.append(c.code()).append(";").append(esc(c.name())).append(";;;;").append(c.subtotal()).append('\n');
            for (ItemDto i : c.items()) {
                csv.append(i.code() == null ? "" : i.code()).append(';').append(esc(i.description())).append(';').append(i.unit())
                        .append(';').append(i.quantity()).append(';').append(i.unitPrice()).append(';').append(i.total()).append('\n');
            }
        }
        csv.append(";Costo directo;;;;").append(b.directCost()).append('\n')
                .append(";Administración;;;;").append(b.administration()).append('\n')
                .append(";Imprevistos;;;;").append(b.contingency()).append('\n')
                .append(";Utilidad;;;;").append(b.profit()).append('\n')
                .append(";IVA sobre utilidad;;;;").append(b.taxOnProfit()).append('\n')
                .append(";TOTAL;;;;").append(b.total()).append('\n');
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"presupuesto.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    /** Evita inyección de fórmulas al abrir el CSV en Excel. */
    private static String esc(String s) {
        String v = s == null ? "" : s.replace(";", ",").replace("\n", " ");
        return v.matches("^[=+\\-@].*") ? "'" + v : v;
    }
}
