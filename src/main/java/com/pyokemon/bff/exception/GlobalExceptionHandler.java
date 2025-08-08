package com.pyokemon.bff.exception;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(-2)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        
        // 기본 상태 코드 및 메시지 설정
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "서버 내부 오류가 발생했습니다.";
        
        // 예외 유형에 따라 상태 코드와 메시지 설정
        if (ex instanceof ResponseStatusException) {
            status = ((ResponseStatusException) ex).getStatusCode();
            message = ex.getMessage();
        } else if (ex.getMessage() != null && ex.getMessage().contains("권한이 없습니다")) {
            status = HttpStatus.FORBIDDEN;
            message = "해당 리소스에 접근할 권한이 없습니다.";
        } else if (ex.getCause() != null && ex.getCause().getClass().getSimpleName().contains("TimeoutException")) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = "외부 서비스 응답 시간이 초과되었습니다.";
        }
        
        // 응답 설정
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        // 에러 응답 본문 생성
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now().toString());
        errorBody.put("status", status.value());
        errorBody.put("error", status.getReasonPhrase());
        errorBody.put("message", message);
        errorBody.put("path", exchange.getRequest().getPath().value());
        
        // 개발 환경에서만 스택 트레이스 포함
        if (isDevEnvironment()) {
            errorBody.put("exception", ex.getClass().getName());
            errorBody.put("trace", ex.getMessage());
        }
        
        String errorJson;
        try {
            errorJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorBody);
        } catch (Exception e) {
            errorJson = "{\"message\":\"" + message + "\"}";
        }
        
        DataBuffer buffer = bufferFactory.wrap(errorJson.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    
    private boolean isDevEnvironment() {
        String profile = System.getProperty("spring.profiles.active");
        return profile != null && (profile.contains("dev") || profile.contains("local"));
    }
}