package dev.normalbias.api;

import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;

final class SchemaMigrations {
    private SchemaMigrations() {}

    static void migrate(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            Set<String> tables = new HashSet<>();
            try (var result = connection.getMetaData().getTables(null, "PUBLIC", null, new String[]{"TABLE", "VIEW"})) {
                while (result.next()) tables.add(result.getString("TABLE_NAME"));
            }
            if (!tables.isEmpty() && !tables.contains("flyway_schema_history")) {
                if (!flyway.getConfiguration().getBaselineVersion().toString().equals("1")) {
                    throw new IllegalStateException("Legacy adoption requires baseline version 1");
                }
                validateLegacy(connection, tables);
                flyway.baseline(); // Version 1 is the verified, unchanged PoC catalogue.
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot inspect catalogue schema", e);
        }
        flyway.migrate(); // Includes checksum validation; never automatically repair history.
    }

    private static void validateLegacy(Connection connection, Set<String> tables) throws SQLException {
        if (!tables.equals(Set.of("TRACKS"))) throw new IllegalStateException("Unrecognised legacy schema; refusing to baseline");
        List<String> actual = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'TRACKS'
                ORDER BY ORDINAL_POSITION
                """)) {
            while (rows.next()) actual.add(rows.getString(1) + ":" + rows.getString(2) + ":" + rows.getString(3) + ":" + rows.getString(4));
        }
        List<String> expected = List.of("ID:CHARACTER VARYING:64:NO", "FILENAME:CHARACTER VARYING:1024:NO",
                "TITLE:CHARACTER VARYING:1024:NO", "ARTIST:CHARACTER VARYING:1024:YES", "ALBUM:CHARACTER VARYING:1024:YES",
                "DURATION_MS:BIGINT:null:NO", "SIZE_BYTES:BIGINT:null:NO", "CREATED_AT:TIMESTAMP WITH TIME ZONE:null:NO");
        Set<String> key = new HashSet<>();
        try (var rows = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", "TRACKS")) {
            while (rows.next()) key.add(rows.getString("COLUMN_NAME"));
        }
        if (!actual.equals(expected) || !key.equals(Set.of("ID"))) {
            throw new IllegalStateException("Legacy tracks schema differs from V1; refusing to baseline");
        }
        // Do not silently adopt custom constraints, defaults or generated columns.
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = 'PUBLIC' AND CONSTRAINT_TYPE <> 'PRIMARY KEY'
                """)) {
            rows.next();
            if (rows.getInt(1) != 0) throw new IllegalStateException("Unexpected legacy constraints; refusing to baseline");
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC'
                AND (COLUMN_DEFAULT IS NOT NULL OR IS_IDENTITY = 'YES' OR IS_GENERATED <> 'NEVER')
                """)) {
            rows.next();
            if (rows.getInt(1) != 0) throw new IllegalStateException("Unexpected legacy generated/default columns; refusing to baseline");
        }
    }
}
