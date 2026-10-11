package com.mehedi.waylo.service;

import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.Role;
import com.mehedi.waylo.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class ModerationService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PostRepository posts;
    private final PostCommentRepository comments;
    private final AuditService audit;
    public ModerationService(JdbcTemplate jdbc, UserRepository users, PostRepository posts,
            PostCommentRepository comments, AuditService audit) {
        this.jdbc=jdbc; this.users=users; this.posts=posts; this.comments=comments; this.audit=audit;
    }
    private User actor(String email, boolean admin) {
        if (email == null) throw new AccessDeniedException("Sign in required.");
        User user=users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable."));
        if (admin && user.getRole()!=Role.ADMIN) throw new AccessDeniedException("Admin access required.");
        return user;
    }
    private String reason(String text) {
        if (text==null || text.strip().length()<10 || text.length()>500)
            throw new IllegalArgumentException("Give a reason between 10 and 500 characters.");
        return text.strip();
    }
    @Transactional
    public void report(String email, String type, Long id, String text) {
        User user=actor(email,false); String reason=reason(text);
        if ("POST".equals(type)) {
            posts.findById(id).filter(p -> !p.isDeleted() && !p.isHidden())
                    .orElseThrow(() -> new IllegalArgumentException("Story unavailable."));
        } else if ("COMMENT".equals(type)) {
            comments.findById(id).filter(c -> !c.isDeleted() && !c.isHidden() && !c.getPost().isHidden() && !c.getPost().isDeleted())
                    .orElseThrow(() -> new IllegalArgumentException("Comment unavailable."));
        } else throw new IllegalArgumentException("Unsupported report type.");
        jdbc.update("INSERT INTO content_reports(reporter_id,target_type,target_id,reason) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                user.getId(),type,id,reason);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> reports(String email) {
        actor(email,true);
        return jdbc.queryForList("""
            SELECT r.*, u.full_name AS reporter_name,
              CASE WHEN r.target_type='POST' THEN p.caption ELSE c.body END AS content_text
            FROM content_reports r JOIN users u ON u.id=r.reporter_id
            LEFT JOIN posts p ON r.target_type='POST' AND p.id=r.target_id
            LEFT JOIN post_comments c ON r.target_type='COMMENT' AND c.id=r.target_id
            ORDER BY (r.status='OPEN') DESC,r.id DESC LIMIT 200
            """);
    }
    @Transactional
    public void review(String email, Long reportId, boolean hide, String text) {
        User user=actor(email,true); String reason=reason(text);
        var rows=jdbc.queryForList("SELECT * FROM content_reports WHERE id=? FOR UPDATE",reportId);
        if(rows.isEmpty()) throw new IllegalArgumentException("Report not found.");
        var report=rows.getFirst();
        if(!"OPEN".equals(report.get("status"))) throw new IllegalArgumentException("Report already reviewed.");
        Long target=((Number)report.get("target_id")).longValue();
        if(hide) {
            if("POST".equals(report.get("target_type"))) jdbc.update("UPDATE posts SET hidden=true WHERE id=?",target);
            else jdbc.update("UPDATE post_comments SET hidden=true WHERE id=?",target);
        }
        jdbc.update("UPDATE content_reports SET status=?,reviewed_by=?,review_reason=?,reviewed_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                hide?"HIDDEN":"DISMISSED",user.getId(),reason,reportId);
        audit.record(user.getId(),hide?"CONTENT_HIDDEN":"REPORT_DISMISSED",report.get("target_type").toString(),target,reason);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> audit(String email) {
        actor(email,true);
        return jdbc.queryForList("SELECT a.*,u.full_name FROM audit_events a JOIN users u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT 200");
    }
    @Transactional(readOnly=true)
    public Map<String,Object> overview(String email) {
        actor(email,true);
        return Map.of("members",jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE enabled=true",Long.class),
                "destinations",jdbc.queryForObject("SELECT COUNT(*) FROM destinations",Long.class),
                "trips",jdbc.queryForObject("SELECT COUNT(*) FROM trips WHERE status='PUBLISHED'",Long.class),
                "reports",jdbc.queryForObject("SELECT COUNT(*) FROM content_reports WHERE status='OPEN'",Long.class));
    }
}
