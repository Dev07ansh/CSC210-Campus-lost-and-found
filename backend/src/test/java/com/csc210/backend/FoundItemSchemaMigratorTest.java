package com.csc210.backend;

import com.csc210.backend.config.FoundItemSchemaMigrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundItemSchemaMigratorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void addsDeskStorageWithoutInventingALocationForLegacyReports() throws Exception {
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temporaryDirectory.resolve("legacy-found-items.db"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE found_item (id INTEGER PRIMARY KEY, item_name VARCHAR(255))");
            statement.executeUpdate("INSERT INTO found_item (id, item_name) VALUES (1, 'legacy keys')");
        }

        new FoundItemSchemaMigrator(dataSource).migrate();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("PRAGMA table_info(found_item)")) {
                boolean deskColumnPresent = false;
                while (rows.next()) {
                    if ("collection_desk".equalsIgnoreCase(rows.getString("name"))) deskColumnPresent = true;
                }
                assertTrue(deskColumnPresent);
            }
            try (ResultSet legacy = statement.executeQuery("SELECT collection_desk FROM found_item WHERE id = 1")) {
                assertTrue(legacy.next());
                assertNull(legacy.getString("collection_desk"));
            }
            statement.executeUpdate("INSERT INTO found_item (id, item_name, collection_desk) VALUES (2, 'wallet', 'Gate 12')");
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO found_item (id, item_name, collection_desk) VALUES (3, 'wallet', 'Library')"));
            // Normal updates to legacy records remain possible until staff assigns a desk.
            statement.executeUpdate("UPDATE found_item SET item_name = 'legacy key ring' WHERE id = 1");
        }
    }
}
