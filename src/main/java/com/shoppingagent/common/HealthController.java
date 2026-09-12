package com.shoppingagent.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${shopping.search.mode:mock}")
    private String searchMode;

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "shopping-agent-backend");
        body.put("version", "0.1.0");
        body.put("searchMode", searchMode);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
