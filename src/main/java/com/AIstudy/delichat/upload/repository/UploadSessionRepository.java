package com.AIstudy.delichat.upload.repository;

import com.AIstudy.delichat.upload.dto.UploadSessionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UploadSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long save(String objectName, String originalFilename, String contentType) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO upload_session (object_name, original_filename, content_type) VALUES (?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, objectName);
            ps.setString(2, originalFilename);
            ps.setString(3, contentType);
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    public Optional<UploadSessionResult> findById(Long sessionId) {
        String sql = """
                SELECT id, object_name, original_filename, content_type, status
                FROM upload_session
                WHERE id = ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new UploadSessionResult(
                rs.getLong("id"),
                rs.getString("object_name"),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getString("status")
        ), sessionId).stream().findFirst();
    }

    public void updateStatus(Long sessionId, String status) {
        jdbcTemplate.update(
                "UPDATE upload_session SET status = ?, completed_at = now() WHERE id = ?",
                status, sessionId
        );
    }
}