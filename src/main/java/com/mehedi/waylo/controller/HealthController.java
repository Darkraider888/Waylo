package com.mehedi.waylo.controller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @GetMapping("/healthz")
    public ResponseEntity<String> health() {
        try { jdbc.queryForObject("SELECT 1",Integer.class);return ResponseEntity.ok("OK"); }
        catch(Exception ex) { return ResponseEntity.status(503).body("UNAVAILABLE"); }
    }
}
