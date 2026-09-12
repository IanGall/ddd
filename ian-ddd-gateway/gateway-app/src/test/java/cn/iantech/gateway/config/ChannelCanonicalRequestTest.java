package cn.iantech.gateway.config;

import cn.iantech.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelCanonicalRequestTest {

    private static final String CHANNEL_CODE = "ch_abcdefghijklmnopqrstuv";
    private static final String SIGNATURE = "1".repeat(64);

    @Test
    void shouldBuildEightLineCanonicalRequestAndSortQuery() throws Exception {
        byte[] body = "{\"name\":\"渠道\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = signedRequest("POST", "/api/external/orders", body);
        request.setQueryString("b=%E4%B8%AD%E6%96%87&a=&b=%2B");
        request.setContentType("application/json; charset=UTF-8");

        ChannelCanonicalRequest.Material material = ChannelCanonicalRequest.create(request, body);

        assertEquals(CHANNEL_CODE, material.channelCode());
        assertEquals(8, material.canonicalRequest().split("\n", -1).length);
        assertEquals(String.join("\n", "POST", "/api/external/orders",
                "a=&b=%2B&b=%E4%B8%AD%E6%96%87", "application/json", CHANNEL_CODE, "1",
                "1787107200", sha256(body)), material.canonicalRequest());
    }

    @Test
    void shouldUseEmptyContentTypeAndEmptyBodyHashWhenBodyIsEmpty() throws Exception {
        byte[] body = new byte[0];
        MockHttpServletRequest request = signedRequest("GET", "/api/external/orders", body);
        request.setContentType("application/json; charset=UTF-8");

        ChannelCanonicalRequest.Material material = ChannelCanonicalRequest.create(request, body);

        assertEquals(String.join("\n", "GET", "/api/external/orders", "", "", CHANNEL_CODE,
                "1", "1787107200", sha256(body)), material.canonicalRequest());
    }

    @Test
    void shouldRejectBodyHashMismatchAndDuplicateHeader() throws Exception {
        MockHttpServletRequest mismatch = signedRequest("POST", "/api/external/orders", "{}".getBytes(StandardCharsets.UTF_8));
        mismatch.setContentType("application/json");
        mismatch.removeHeader(ChannelCanonicalRequest.CONTENT_SHA256_HEADER);
        mismatch.addHeader(ChannelCanonicalRequest.CONTENT_SHA256_HEADER, "0".repeat(64));
        assertEquals("AUTH_REQUIRED", assertThrows(AppException.class,
                () -> ChannelCanonicalRequest.create(mismatch, mismatch.getContentAsByteArray())).getCode());

        MockHttpServletRequest duplicate = signedRequest("GET", "/api/external/orders", new byte[0]);
        duplicate.addHeader(ChannelCanonicalRequest.CHANNEL_CODE_HEADER, CHANNEL_CODE);
        assertEquals("AUTH_REQUIRED", assertThrows(AppException.class,
                () -> ChannelCanonicalRequest.create(duplicate, new byte[0])).getCode());
    }

    @Test
    void shouldRejectIllegalPathQueryAndContentType() throws Exception {
        MockHttpServletRequest dotPath = signedRequest("GET", "/api/external/%2e%2e/admin", new byte[0]);
        assertEquals("INVALID_ARGUMENT", assertThrows(AppException.class,
                () -> ChannelCanonicalRequest.create(dotPath, new byte[0])).getCode());

        MockHttpServletRequest badQuery = signedRequest("GET", "/api/external/orders", new byte[0]);
        badQuery.setQueryString("name=%GG");
        assertEquals("INVALID_ARGUMENT", assertThrows(AppException.class,
                () -> ChannelCanonicalRequest.create(badQuery, new byte[0])).getCode());

        byte[] body = "value".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest text = signedRequest("POST", "/api/external/orders", body);
        text.setContentType("text/plain");
        assertEquals("INVALID_ARGUMENT", assertThrows(AppException.class,
                () -> ChannelCanonicalRequest.create(text, body)).getCode());

        MockHttpServletRequest multipart = signedRequest("POST", "/api/external/orders", new byte[0]);
        multipart.setContentType("multipart/form-data; boundary=test");
        assertEquals("INVALID_ARGUMENT", assertThrows(AppException.class,
                () -> ChannelCanonicalRequest.create(multipart, new byte[0])).getCode());
    }

    private MockHttpServletRequest signedRequest(String method, String path, byte[] body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContent(body);
        request.addHeader(ChannelCanonicalRequest.CHANNEL_CODE_HEADER, CHANNEL_CODE);
        request.addHeader(ChannelCanonicalRequest.SECRET_VERSION_HEADER, "1");
        request.addHeader(ChannelCanonicalRequest.TIMESTAMP_HEADER, "1787107200");
        request.addHeader(ChannelCanonicalRequest.CONTENT_SHA256_HEADER, sha256(body));
        request.addHeader(ChannelCanonicalRequest.SIGNATURE_HEADER, SIGNATURE);
        return request;
    }

    private String sha256(byte[] body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }
}
