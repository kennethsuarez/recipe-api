package com.kenenthsuarez.recipe_api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.util.concurrent.Semaphore;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLimitsFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final Semaphore permits;

    public RequestLimitsFilter(@Value("${recipe.max-request-bytes:524288}") int maxBytes,
                               @Value("${recipe.max-concurrent-requests:32}") int concurrency) {
        if (maxBytes < 1 || concurrency < 1) throw new IllegalArgumentException("Request limits must be positive");
        this.maxBytes = maxBytes;
        this.permits = new Semaphore(concurrency);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/recipes");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            response.setHeader("Retry-After", "1");
            reject(response, 503, "Service Unavailable", "Too many concurrent requests");
            return;
        }
        try {
            if (request.getContentLengthLong() > maxBytes) {
                reject(response, 413, "Payload Too Large", "Request body exceeds the configured limit");
                return;
            }
            // Read a bounded amount before dispatch, including for chunked bodies with no Content-Length.
            byte[] bytes = request.getInputStream().readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                reject(response, 413, "Payload Too Large", "Request body exceeds the configured limit");
                return;
            }
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override
                public ServletInputStream getInputStream() {
                    ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                    return new ServletInputStream() {
                        public int read() { return input.read(); }
                        public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                        public boolean isFinished() { return input.available() == 0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) {
                            throw new UnsupportedOperationException("Asynchronous request bodies are not supported");
                        }
                    };
                }
            }, response);
        } finally {
            permits.release();
        }
    }

    private void reject(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":" + status + ",\"title\":\"" + title + "\",\"detail\":\"" + detail + "\"}");
    }
}
