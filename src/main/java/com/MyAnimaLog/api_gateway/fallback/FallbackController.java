package com.MyAnimaLog.api_gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/user")
    public ResponseEntity<Map<String, Object>> userFallback() {
        return buildFallback("User service");
    }

    @RequestMapping("/pet")
    public ResponseEntity<Map<String, Object>> petFallback() {
        return buildFallback("Pet service");
    }

    @RequestMapping("/veterinary")
    public ResponseEntity<Map<String, Object>> veterinaryFallback() {
        return buildFallback("Veterinary service");
    }

    @RequestMapping("/medical")
    public ResponseEntity<Map<String, Object>> medicalFallback() {
        return buildFallback("Medical pet service");
    }

    @RequestMapping("/calendar")
    public ResponseEntity<Map<String, Object>> calendarFallback() {
        return buildFallback("Calendar service");
    }

    @RequestMapping("/notification")
    public ResponseEntity<Map<String, Object>> notificationFallback() {
        return buildFallback("Notification service");
    }

    private ResponseEntity<Map<String, Object>> buildFallback(String serviceName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        body.put("message", serviceName + " is currently unavailable. Please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}