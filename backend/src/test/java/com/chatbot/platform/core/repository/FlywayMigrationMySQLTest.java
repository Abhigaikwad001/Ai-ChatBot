package com.chatbot.platform.core.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationMySQLTest {

    @Test
    @DisplayName("Flyway V1 migration must execute cleanly against MySQL-compatible engine without errors")
    void testFlywayMigration_executesCleanlyOnMySQLCompatibleEngine() throws Exception {
        DataSource dataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:flyway_test;DB_CLOSE_DELAY=-1;MODE=MySQL")
                .driverClassName("org.h2.Driver")
                .username("sa")
                .password("")
                .build();

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        int migrationsApplied = flyway.migrate().migrationsExecuted;
        assertThat(migrationsApplied).isGreaterThanOrEqualTo(1);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Verify all tables exist
            Set<String> tables = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }

            assertThat(tables).contains(
                    "users",
                    "conversations",
                    "messages",
                    "message_attachments",
                    "ai_audit_logs",
                    "flyway_schema_history"
            );

            // Verify inserting a test user with UUID string and verifying VARCHAR(36) column
            stmt.executeUpdate(
                    "INSERT INTO users (id, email, password_hash, full_name, role, status, created_at, updated_at) " +
                    "VALUES ('9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d', 'test@hospital.org', 'hash', 'Dr. Smith', " +
                    "'ROLE_USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
            );

            try (ResultSet rs = stmt.executeQuery("SELECT email FROM users WHERE id = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("email")).isEqualTo("test@hospital.org");
            }
        }
    }
}
