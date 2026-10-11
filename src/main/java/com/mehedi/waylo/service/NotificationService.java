package com.mehedi.waylo.service;
import com.mehedi.waylo.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class NotificationService {
    private final JdbcTemplate jdbc;private final UserRepository users;
    public NotificationService(JdbcTemplate jdbc,UserRepository users){this.jdbc=jdbc;this.users=users;}
    private Long actor(String email){return users.findByEmail(email).filter(com.mehedi.waylo.entity.User::isEnabled)
            .orElseThrow(()->new AccessDeniedException("Account unavailable.")).getId();}
    @Transactional public void send(Long userId,String message,String link){
        if(!link.startsWith("/")||link.startsWith("//")||message.length()>500)throw new IllegalArgumentException("Invalid notification.");
        jdbc.update("INSERT INTO user_notifications(user_id,message,link) VALUES(?,?,?)",userId,message,link);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> list(String email){
        return jdbc.queryForList("SELECT id,message,link,created_at,read_at FROM user_notifications WHERE user_id=? ORDER BY id DESC LIMIT 100",actor(email));
    }
    @Transactional public void read(String email,Long id){jdbc.update("UPDATE user_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(6)) WHERE id=? AND user_id=?",id,actor(email));}
}
