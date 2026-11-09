package com.civia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita el cuerpo de las peticiones JSON de la API. Sin esto, cualquiera podría enviar cuerpos enormes a
 * los puntos públicos (inicio de sesión, registro) y agotar la memoria. Las subidas de archivos tienen su
 * propio límite y solo se procesan después de autenticar.
 */
public class BodyLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public BodyLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return !request.getRequestURI().startsWith("/api/") || (type != null && type.toLowerCase().startsWith("multipart/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getContentLengthLong() > maxBytes) {
            res.setStatus(413);
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":413,\"error\":\"Payload Too Large\",\"message\":\"La solicitud es demasiado grande.\"}");
            return;
        }
        // Las peticiones sin Content-Length (chunked) se cuentan mientras se leen.
        chain.doFilter(new LimitedRequest(req, maxBytes), res);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long total;

                private int count(int read) throws IOException {
                    if (read > 0) total += read;
                    if (total > maxBytes) throw new IOException("Cuerpo de la solicitud demasiado grande");
                    return read;
                }

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0) count(1);
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    return count(in.read(b, off, len));
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    in.setReadListener(listener);
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
