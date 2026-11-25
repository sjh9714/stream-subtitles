package io.github.sjh9714.subtitles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiGuardTest {

    /** The status the guard answers with, or 0 when it passes the request on. */
    private static int status(ApiGuard guard, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        guard.doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 0;
    }

    private static MockHttpServletRequest get(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/videos/aaaaaaaaaaa/subtitles");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    void onlyARequestWithTheTokenPasses() throws Exception {
        ApiGuard guard = new ApiGuard("secret", "0.0.0.0");

        assertThat(status(guard, get("Bearer secret"))).isZero();
        assertThat(status(guard, get(null))).isEqualTo(401);
        assertThat(status(guard, get("Bearer wrong"))).isEqualTo(401);
        assertThat(status(guard, get("secret"))).isEqualTo(401);
    }

    @Test
    void serverOnThisMachineAloneMayRunWithoutAToken() throws Exception {
        assertThat(status(new ApiGuard("", "127.0.0.1"), get(null))).isZero();
        assertThatThrownBy(() -> new ApiGuard("", "0.0.0.0")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bodyThatIsTooLargeOrHasNoStatedSizeIsTurnedAway() throws Exception {
        ApiGuard guard = new ApiGuard("", "127.0.0.1");
        MockHttpServletRequest unsized = new MockHttpServletRequest("POST", "/api/videos/aaaaaaaaaaa/translation");
        MockHttpServletRequest large = new MockHttpServletRequest("POST", "/api/videos/aaaaaaaaaaa/translation") {
            @Override
            public long getContentLengthLong() {
                return ApiGuard.MAX_BODY_BYTES + 1;
            }
        };

        assertThat(status(guard, unsized)).isEqualTo(411);
        assertThat(status(guard, large)).isEqualTo(413);

        MockHttpServletRequest small = new MockHttpServletRequest("POST", "/api/videos/aaaaaaaaaaa/translation");
        small.setContent("{}".getBytes());
        assertThat(status(guard, small)).isZero();
    }
}
