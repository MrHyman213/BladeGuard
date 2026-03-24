package com.knifecerts;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Интеграционный тест для проверки корректности схемы БД после применения Liquibase миграций.
 * 
 * Feature: blade-guardian-full-implementation
 * Требование: 20.4
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class NewSchemaIntegrationTest {
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Проверяет наличие всех необходимых таблиц после применения changeSet'а 001-initial-schema.
     */
    @Test
    void shouldHaveAllRequiredTables() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            Set<String> tables = new HashSet<>();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
            
            // Проверяем наличие всех таблиц из схемы
            assertThat(tables).contains(
                "brands",
                "knife_models",
                "knives",
                "alternatives",
                "submissions_buffer",
                "user_main_menu"
            );
        }
    }
    
    /**
     * Проверяет наличие уникальных индексов на brands.name и knife_models.name.
     */
    @Test
    void shouldHaveUniqueIndexesOnNames() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Проверяем уникальный индекс на brands.name
            boolean brandsNameUnique = false;
            try (ResultSet rs = metaData.getIndexInfo(null, null, "brands", true, false)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    if ("name".equalsIgnoreCase(columnName)) {
                        brandsNameUnique = true;
                        break;
                    }
                }
            }
            assertThat(brandsNameUnique)
                .as("brands.name должен иметь уникальный индекс")
                .isTrue();
            
            // Проверяем уникальный индекс на knife_models.name
            boolean modelsNameUnique = false;
            try (ResultSet rs = metaData.getIndexInfo(null, null, "knife_models", true, false)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    if ("name".equalsIgnoreCase(columnName)) {
                        modelsNameUnique = true;
                        break;
                    }
                }
            }
            assertThat(modelsNameUnique)
                .as("knife_models.name должен иметь уникальный индекс")
                .isTrue();
        }
    }
    
    /**
     * Проверяет наличие триггера trg_cleanup_orphan_models.
     */
    @Test
    void shouldHaveAutoDeleteTrigger() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // Проверяем наличие функции cleanup_orphan_knife_models
            String checkFunctionSql = 
                "SELECT COUNT(*) FROM pg_proc WHERE proname = 'cleanup_orphan_knife_models'";
            
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(checkFunctionSql)) {
                rs.next();
                int functionCount = rs.getInt(1);
                assertThat(functionCount)
                    .as("Функция cleanup_orphan_knife_models должна существовать")
                    .isGreaterThan(0);
            }
            
            // Проверяем наличие триггера trg_cleanup_orphan_models
            String checkTriggerSql = 
                "SELECT COUNT(*) FROM pg_trigger WHERE tgname = 'trg_cleanup_orphan_models'";
            
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(checkTriggerSql)) {
                rs.next();
                int triggerCount = rs.getInt(1);
                assertThat(triggerCount)
                    .as("Триггер trg_cleanup_orphan_models должен существовать")
                    .isGreaterThan(0);
            }
        }
    }
    
    /**
     * Проверяет структуру таблицы submissions_buffer.
     */
    @Test
    void shouldHaveSubmissionsBufferWithAlternativesColumn() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            Set<String> columns = new HashSet<>();
            try (ResultSet rs = metaData.getColumns(null, null, "submissions_buffer", null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
            
            // Проверяем наличие всех необходимых колонок
            assertThat(columns).contains(
                "id",
                "user_id",
                "username",
                "model_name",
                "brand_name",
                "idx",
                "photo_path",
                "created_at",
                "alternatives"  // TEXT колонка для хранения альтернатив
            );
        }
    }
    
    /**
     * Проверяет структуру таблицы user_main_menu.
     */
    @Test
    void shouldHaveUserMainMenuTable() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            Set<String> columns = new HashSet<>();
            try (ResultSet rs = metaData.getColumns(null, null, "user_main_menu", null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
            
            // Проверяем наличие колонок
            assertThat(columns).contains(
                "chat_id",
                "message_id"
            );
            
            // Проверяем что chat_id является первичным ключом
            Set<String> primaryKeys = new HashSet<>();
            try (ResultSet rs = metaData.getPrimaryKeys(null, null, "user_main_menu")) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
            assertThat(primaryKeys).contains("chat_id");
        }
    }
    
    /**
     * Проверяет наличие внешних ключей.
     */
    @Test
    void shouldHaveForeignKeys() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Проверяем FK для knives → knife_models
            boolean hasModelFK = false;
            try (ResultSet rs = metaData.getImportedKeys(null, null, "knives")) {
                while (rs.next()) {
                    String fkColumn = rs.getString("FKCOLUMN_NAME");
                    String pkTable = rs.getString("PKTABLE_NAME");
                    if ("model_id".equalsIgnoreCase(fkColumn) && "knife_models".equalsIgnoreCase(pkTable)) {
                        hasModelFK = true;
                        break;
                    }
                }
            }
            assertThat(hasModelFK)
                .as("knives.model_id должен иметь FK на knife_models.id")
                .isTrue();
            
            // Проверяем FK для knives → brands
            boolean hasBrandFK = false;
            try (ResultSet rs = metaData.getImportedKeys(null, null, "knives")) {
                while (rs.next()) {
                    String fkColumn = rs.getString("FKCOLUMN_NAME");
                    String pkTable = rs.getString("PKTABLE_NAME");
                    if ("brand_id".equalsIgnoreCase(fkColumn) && "brands".equalsIgnoreCase(pkTable)) {
                        hasBrandFK = true;
                        break;
                    }
                }
            }
            assertThat(hasBrandFK)
                .as("knives.brand_id должен иметь FK на brands.id")
                .isTrue();
        }
    }
}
