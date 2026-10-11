package com.mehedi.waylo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actor, String action, String type, Long id, String reason) {
        jdbc.update("INSERT INTO audit_events(actor_id,action,target_type,target_id,reason) VALUES (?,?,?,?,?)",
                actor, action, type, id, reason == null ? "" : reason);
    }
}
