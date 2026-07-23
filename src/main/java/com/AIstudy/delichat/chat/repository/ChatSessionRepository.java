package com.AIstudy.delichat.chat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;

@Repository
@RequiredArgsConstructor
public class ChatSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long createSession(Long memberId){
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection ->{
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO chat_session (member_id) VALUES (?)",
                    new String[]{"id"}
            );
            if(memberId != null){
                ps.setLong(1, memberId);
            }else{
                ps.setNull(1, Types.BIGINT);
            }
            return ps;
        },keyHolder);

        return keyHolder.getKey().longValue();
    }

    public Long findMemberId(Long sessionId){
        String sql = "SELECT member_id FROM chat_session WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, sessionId);
    }
}
