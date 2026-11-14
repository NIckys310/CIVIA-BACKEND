package com.civia.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Aplica el {@link SealedChannel} a la API.
 *
 * <ul>
 *   <li>{@code POST /api/v1/seal} con {@code {"publicKey": "..."}} abre un canal y responde su id.</li>
 *   <li>Con la cabecera {@code X-Civia-Seal: <id>}, el cuerpo JSON llega como {@code {"iv","data"}} y se
 *       descifra antes de llegar a los controladores; las respuestas JSON salen cifradas igual, con la
 *       cabecera {@code X-Civia-Sealed: 1}.</li>
 *   <li>Canal desconocido o caducado: 428, para que el cliente repita el saludo. Cuerpo manipulado: 400.</li>
 * </ul>
 *
 * Las subidas de archivos (multipart), las descargas binarias y el streaming (SSE) no se cifran aquí:
 * van solo por TLS. Las peticiones sin la cabecera siguen funcionando como antes.
 */
public class SealFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Civia-Seal";
    public static final String SEALED = "X-Civia-Sealed";
    static final String HANDSHAKE = "/api/v1/seal";

    private final SealedChannel channel;
    private final ObjectMapper json = new ObjectMapper();

    public SealFilter(SealedChannel channel) {
        this.channel = channel;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (HANDSHAKE.equals(path)) {
            handshake(req, res);
            return;
        }
        String id = req.getHeader(HEADER);
        if (id == null) {
            chain.doFilter(req, res);
            return;
        }
        Optional<byte[]> key = channel.key(id);
        if (key.isEmpty()) {
            error(res, 428, "El canal seguro caducó. Vuelve a intentarlo.");
            return;
        }
        String route = req.getMethod() + " " + path;

        HttpServletRequest inner = req;
        String type = req.getContentType();
        boolean multipart = type != null && type.toLowerCase().startsWith("multipart/");
        if (!multipart) {
            byte[] body = req.getInputStream().readAllBytes();
            if (body.length > 0) {
                try {
                    JsonNode env = json.readTree(body);
                    byte[] plain = SealedChannel.unseal(key.get(), env.path("iv").asText(), env.path("data").asText(), "req|" + route);
                    inner = new PlainRequest(req, plain);
                } catch (GeneralSecurityException | IOException e) {
                    error(res, 400, "La solicitud cifrada no es válida.");
                    return;
                }
            }
        }

        // El streaming (SSE) se entrega en vivo: no se puede guardar en búfer para cifrarlo entero.
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            chain.doFilter(inner, res);
            return;
        }

        ContentCachingResponseWrapper out = new ContentCachingResponseWrapper(res);
        try {
            chain.doFilter(inner, out);
        } finally {
            if (!req.isAsyncStarted()) finish(out, key.get(), route);
        }
    }

    private void finish(ContentCachingResponseWrapper out, byte[] key, String route) throws IOException {
        String ct = out.getContentType();
        byte[] body = out.getContentAsByteArray();
        if (ct == null || !ct.toLowerCase().contains("json") || body.length == 0) {
            out.copyBodyToResponse();
            return;
        }
        try {
            byte[] sealed = SealedChannel.seal(key, body, "res|" + route, channel.random());
            out.resetBuffer();
            HttpServletResponse raw = (HttpServletResponse) out.getResponse();
            raw.setHeader(SEALED, "1");
            raw.setHeader("Cache-Control", "no-store");
            raw.setContentType("application/json");
            raw.setContentLength(sealed.length);
            raw.getOutputStream().write(sealed);
            raw.flushBuffer();
        } catch (GeneralSecurityException e) {
            out.resetBuffer();
            error((HttpServletResponse) out.getResponse(), 500, "No se pudo cifrar la respuesta.");
        }
    }

    private void handshake(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!"POST".equals(req.getMethod())) {
            error(res, 405, "Método no permitido.");
            return;
        }
        try {
            byte[] body = req.getInputStream().readNBytes(4096);
            String pub = json.readTree(body).path("publicKey").asText("");
            SealedChannel.Handshake h = channel.open(pub);
            res.setStatus(200);
            res.setHeader("Cache-Control", "no-store");
            res.setContentType("application/json");
            res.getWriter().write(json.writeValueAsString(java.util.Map.of("id", h.id(), "publicKey", h.publicKey(), "alg", "ECDH-P256+HKDF-SHA256+A256GCM")));
        } catch (GeneralSecurityException | IOException e) {
            error(res, 400, "Clave pública no válida.");
        }
    }

    private static void error(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setHeader("Cache-Control", "no-store");
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }

    /** Petición con el cuerpo ya descifrado (JSON en claro solo dentro del servidor). */
    private static final class PlainRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        PlainRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getHeader(String name) {
            if ("Content-Type".equalsIgnoreCase(name)) return "application/json";
            if ("Content-Length".equalsIgnoreCase(name)) return String.valueOf(body.length);
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Content-Type".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) {
                return Collections.enumeration(java.util.List.of(getHeader(name)));
            }
            return super.getHeaders(name);
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
