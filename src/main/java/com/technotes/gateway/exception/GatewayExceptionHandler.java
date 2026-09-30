package com.technotes.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;

@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {
    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted() || !isConnectionFailure(exception)) {
            return Mono.error(exception);
        }
        ApiError error = new ApiError(Instant.now(), HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_UNAVAILABLE", "Requested service is temporarily unavailable.",
                exchange.getRequest().getPath().value(), exchange.getRequest().getId(), List.of());
        final byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException serializationException) {
            return Mono.error(serializationException);
        }
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private boolean isConnectionFailure(Throwable exception) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof ConnectException || current instanceof UnknownHostException
                    || current instanceof ReadTimeoutException || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
