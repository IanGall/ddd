package cn.iantech.gateway.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 缓存经过上限校验的原始请求体，确保验签后控制器仍可读取完全相同的字节。
 */
public final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes) throws IOException {
        super(request);
        if (request.getContentLengthLong() > maxBodyBytes) {
            throw new ChannelPayloadTooLargeException();
        }
        byte[] bytes = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (bytes.length > maxBodyBytes) {
            throw new ChannelPayloadTooLargeException();
        }
        this.body = bytes;
    }

    public byte[] body() {
        return body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 当前网关使用同步 Servlet 请求处理，不启用异步读取。
            }

            @Override
            public int read() {
                return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    public static final class ChannelPayloadTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
