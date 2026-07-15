package com.AIstudy.delichat.chat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;

@Repository
@RequiredArgsConstructor
public class ChatSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long createSession(){
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection ->{
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO chat_session DEFAULT VALUES ",
                    new String[]{"id"}
            );
            return ps;
        },keyHolder);

        return keyHolder.getKey().longValue();
    }
}
