package com.restaurant.ireservation.util;

import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.LocalDateTime;
import java.util.Collection;

@ControllerAdvice
public class ResFormatResponse implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body,
                                            MethodParameter returnType,
                                            MediaType selectedContentType,
                                            Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                            ServerHttpRequest request,
                                            ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
            || path.startsWith("/swagger-resources")
        ) {
            return body;
        }
        HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
        int status = servletResponse.getStatus();

        if (body instanceof String || body instanceof Resource || body instanceof ResourceRegion || body instanceof byte[]) {
            return body;
        }

        if (body instanceof Collection) {
            Collection<?> collection = (Collection<?>) body;
            if (!collection.isEmpty() && collection.iterator().next() instanceof ResourceRegion) {
                return body;
            }
        }

        if (status >= 400) {
            return body;
        } else {
            RestResponse<Object> res = new RestResponse<>();
            res.setTimestamp(LocalDateTime.now());
            res.setStatusCode(status);
            res.setData(body);
            ApiMessage message = returnType.getMethodAnnotation(ApiMessage.class);
            res.setMessage(message);
            return res;
        }

    }
}
