package com.AIstudy.delichat.chat.repository;

import com.AIstudy.delichat.chat.dto.ChatMessageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepository {

    private static final int MAX_HISTORY_TURNS = 5;
    private final JdbcTemplate jdbcTemplate;

    public List<ChatMessageResult> findRecentMessage(Long sessionId){
        String sql = """
                SELECT role,content
                FROM chat_message
                WHERE session_id=?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<ChatMessageResult> recent = jdbcTemplate.query(sql,
                (rs,rowNum)-> new ChatMessageResult(
                        rs.getString("role"),rs.getString("content")),
                    sessionId,MAX_HISTORY_TURNS*2
                );

        Collections.reverse(recent);
        return recent;
    }

    public void save(Long sessionId, String role, String content){
        jdbcTemplate.update(
                "INSERT INTO chat_message (session_id,role,content) VALUES (?,?,?)",
                sessionId,role,content
        );
    }
}
