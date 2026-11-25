package io.github.sjh9714.subtitles;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets in only the requests that carry the owner's token, and turns away bodies too large to be captions.
 * Every translation costs money, so a server that other machines can reach must not start without a token.
 */
@Component
class ApiGuard extends OncePerRequestFilter {

    /** The captions of a six-hour video are a few megabytes. */
    static final long MAX_BODY_BYTES = 20L * 1024 * 1024;

    private final byte[] token;

    ApiGuard(@Value("${subtitles.api-token}") String token, @Value("${server.address}") String address)
            throws UnknownHostException {
        if (token.isBlank() && !InetAddress.getByName(address).isLoopbackAddress()) {
            throw new IllegalStateException("API_TOKEN must be set when the server listens on " + address);
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (token.length > 0 && !MessageDigest.isEqual(token, sentToken(request))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } else if (length > MAX_BODY_BYTES) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        } else if (length < 0 && "POST".equals(request.getMethod())) {
            // A body sent in pieces has no stated size to check.
            response.setStatus(HttpServletResponse.SC_LENGTH_REQUIRED);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static byte[] sentToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8)
                : new byte[0];
    }
}
