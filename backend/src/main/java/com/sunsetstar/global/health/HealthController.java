package com.sunsetstar.global.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버가 켜져 있는지 확인하는 API.
 * 프론트엔드에서 서버 연결이 잘 되는지 확인할 때 씁니다.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
