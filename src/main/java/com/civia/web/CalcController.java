package com.civia.web;

import com.civia.ai.CiviaAi;
import com.civia.ai.calc.CalcModels.CalcException;
import com.civia.ai.calc.CalcModels.Definition;
import com.civia.ai.calc.CalcModels.Result;
import com.civia.ai.report.CalcReports;
import com.civia.service.AiQuota;
import com.civia.service.ProjectAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/** Calculadoras de ingeniería civil y su memoria de cálculo en PDF. */
@RestController
@RequestMapping("/api/v1/calc")
@Tag(name = "Calculadoras")
public class CalcController {

    public record CalcRequest(Map<String, Double> inputs, UUID projectId) {}

    private final CiviaAi ai;
    private final ProjectAccess access;
    private final AiQuota quota;
    private final String catalogTag;

    public CalcController(CiviaAi ai, ProjectAccess access, AiQuota quota) {
        this.ai = ai;
        this.access = access;
        this.quota = quota;
        this.catalogTag = etag(ai.calculators().catalog().toString());
    }

    @Operation(summary = "Calculadoras disponibles y sus campos")
    @GetMapping
    public ResponseEntity<List<Definition>> catalog(WebRequest request) {
        // Solo cambia con una versión nueva: el navegador la guarda y revalida con ETag (304).
        if (request.checkNotModified(catalogTag)) return null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .eTag(catalogTag)
                .body(ai.calculators().catalog());
    }

    @Operation(summary = "Ejecutar una calculadora")
    @PostMapping("/{calc}")
    public Result run(@PathVariable String calc, @RequestBody(required = false) CalcRequest req) {
        access.me();
        return compute(calc, req);
    }

    @Operation(summary = "Memoria de cálculo en PDF")
    @PostMapping(value = "/{calc}/report", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> report(@PathVariable String calc, @RequestBody(required = false) CalcRequest req) {
        var me = access.me();
        quota.acquire(me.getId()); // generar PDF cuesta más que calcular: comparte la cuota
        Result result = compute(calc, req);
        Definition definition = ai.calculators().catalog().stream().filter(d -> d.id().equals(calc)).findFirst().orElseThrow();
        String project = req != null && req.projectId() != null ? access.view(req.projectId()).getName() : null;
        byte[] pdf = CalcReports.render(definition, result, project, me.getFullName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("memoria-" + calc + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }

    private Result compute(String calc, CalcRequest req) {
        try {
            return ai.calculators().run(calc, req == null ? Map.of() : req.inputs());
        } catch (CalcException e) {
            HttpStatus status = e.getMessage().startsWith("No existe") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, e.getMessage());
        }
    }

    static String etag(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(hash, 0, 16) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
