package com.kenenthsuarez.recipe_api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class RequestLimitsFilterTest {
    @Test
    void saturationReturns503AndReleasesPermit() throws Exception {
        var filter = new RequestLimitsFilter(10, 1);
        var first = request();
        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(first, new MockHttpServletResponse(), (req, res) ->
                filter.doFilter(request(), secondResponse, (r, s) -> { throw new AssertionError("Must reject"); }));
        assertThat(secondResponse.getStatus()).isEqualTo(503);
        var after = new MockHttpServletResponse();
        filter.doFilter(request(), after, (req, res) -> res.getWriter().write("ok"));
        assertThat(after.getContentAsString()).isEqualTo("ok");
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/recipes");
        request.setServletPath("/api/recipes");
        return request;
    }

    @Test
    void unknownContentLengthStillEnforcesBodyLimit() throws Exception {
        var filter = new RequestLimitsFilter(10, 1);
        var request = new MockHttpServletRequest("POST", "/api/recipes") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setServletPath("/api/recipes");
        request.setContent(new byte[11]);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Must reject"); });
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
