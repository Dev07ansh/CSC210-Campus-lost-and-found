package com.csc210.backend.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SQLite cannot alter a CHECK constraint in place. This helper rebuilds only a
 * table whose enum CHECK is missing one of the values supported by the
 * application. It keeps the current columns, data, primary/unique keys,
 * foreign keys, explicit indexes, triggers, and AUTOINCREMENT sequence.
 */
final class SqliteEnumConstraintMigrator {
    private SqliteEnumConstraintMigrator() {
    }

    /**
     * Rebuilds {@code table} when its enum CHECK prevents one of the supplied
     * values from being stored. Values in {@code replacements} are converted
     * while rows are copied, so an old table's CHECK never blocks the status
     * migration before the rebuild is complete.
     *
     * @return {@code true} when a rebuild was performed
     */
    static boolean rebuildIfEnumCheckIsOutdated(Connection connection, String table,
                                                 String enumColumn, List<String> allowedValues,
                                                 Map<String, String> replacements) throws SQLException {
        String tableSql = tableSql(connection, table);
        if (!requiresRebuild(tableSql, enumColumn, allowedValues)) {
            return false;
        }

        TableLayout layout = readLayout(connection, table, tableSql);
        if (layout.columns().stream().noneMatch(column -> column.name().equalsIgnoreCase(enumColumn))) {
            return false;
        }

        String stagingTable = "__" + table + "_enum_constraint_migration";
        if (tableSql(connection, stagingTable) != null) {
            throw new SQLException("A previous " + table + " schema migration did not finish. "
                    + "Remove or restore table " + stagingTable + " before restarting.");
        }

        Long previousSequence = sequenceValue(connection, table);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableSql(stagingTable, layout, enumColumn, allowedValues));
            statement.executeUpdate(copyTableSql(table, stagingTable, layout.columns(), enumColumn, replacements));

            // Drop before restoring named indexes: SQLite index names are global,
            // and the old table still owns those names until it is removed.
            statement.executeUpdate("DROP TABLE " + identifier(table));
            statement.executeUpdate("ALTER TABLE " + identifier(stagingTable) + " RENAME TO " + identifier(table));

            for (String indexSql : layout.indexSql()) {
                statement.executeUpdate(indexSql);
            }
            for (String triggerSql : layout.triggerSql()) {
                statement.executeUpdate(triggerSql);
            }
        }
        restoreSequence(connection, table, previousSequence);
        return true;
    }

    private static boolean requiresRebuild(String tableSql, String enumColumn, List<String> allowedValues) {
        if (tableSql == null) {
            return false;
        }
        String normalized = tableSql.toUpperCase(Locale.ROOT);
        // A regular table has no enum constraint to replace. Rebuilding it would
        // create unnecessary migration risk.
        if (!normalized.contains("CHECK") || !containsIdentifier(normalized, enumColumn)) {
            return false;
        }
        return allowedValues.stream()
                .map(value -> "'" + value.toUpperCase(Locale.ROOT) + "'")
                .anyMatch(value -> !normalized.contains(value));
    }

    private static boolean containsIdentifier(String sql, String identifier) {
        return sql.matches("(?s).*\\b" + java.util.regex.Pattern.quote(identifier.toUpperCase(Locale.ROOT)) + "\\b.*");
    }

    private static String tableSql(Connection connection, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT sql FROM sqlite_master
                WHERE type = 'table' AND name = ?
                """)) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static TableLayout readLayout(Connection connection, String table, String tableSql) throws SQLException {
        List<Column> columns = readColumns(connection, table);
        List<UniqueConstraint> uniqueConstraints = readUniqueConstraints(connection, table);
        List<ForeignKey> foreignKeys = readForeignKeys(connection, table);
        List<String> indexSql = readSchemaSql(connection, "index", table);
        List<String> triggerSql = readSchemaSql(connection, "trigger", table);
        return new TableLayout(columns, uniqueConstraints, foreignKeys, indexSql, triggerSql,
                tableSql != null && tableSql.toUpperCase(Locale.ROOT).contains("AUTOINCREMENT"));
    }

    private static List<Column> readColumns(Connection connection, String table) throws SQLException {
        List<Column> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA table_info(" + identifier(table) + ")")) {
            while (rows.next()) {
                columns.add(new Column(
                        rows.getString("name"),
                        rows.getString("type"),
                        rows.getInt("notnull") != 0,
                        rows.getString("dflt_value"),
                        rows.getInt("pk")));
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Could not read the existing schema for " + table + ".");
        }
        return columns;
    }

    private static List<UniqueConstraint> readUniqueConstraints(Connection connection, String table) throws SQLException {
        List<UniqueConstraint> constraints = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA index_list(" + identifier(table) + ")")) {
            while (rows.next()) {
                // SQLite reports table-level UNIQUE constraints with origin "u".
                // Explicit CREATE UNIQUE INDEX statements have origin "c" and are
                // restored verbatim below instead.
                if (!"u".equalsIgnoreCase(rows.getString("origin"))) {
                    continue;
                }
                String indexName = rows.getString("name");
                List<String> columns = new ArrayList<>();
                try (Statement indexStatement = connection.createStatement();
                     ResultSet indexColumns = indexStatement.executeQuery("PRAGMA index_info(" + identifier(indexName) + ")")) {
                    while (indexColumns.next()) {
                        columns.add(indexColumns.getString("name"));
                    }
                }
                if (!columns.isEmpty()) {
                    constraints.add(new UniqueConstraint(columns));
                }
            }
        }
        return constraints;
    }

    private static List<ForeignKey> readForeignKeys(Connection connection, String table) throws SQLException {
        Map<Integer, ForeignKeyBuilder> grouped = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA foreign_key_list(" + identifier(table) + ")")) {
            while (rows.next()) {
                int id = rows.getInt("id");
                ForeignKeyBuilder key = grouped.get(id);
                if (key == null) {
                    key = new ForeignKeyBuilder(rows.getString("table"), rows.getString("on_update"),
                            rows.getString("on_delete"), rows.getString("match"));
                    grouped.put(id, key);
                }
                key.columns.add(new ForeignKeyColumn(rows.getInt("seq"), rows.getString("from"), rows.getString("to")));
            }
        }
        return grouped.values().stream().map(ForeignKeyBuilder::build).toList();
    }

    private static List<String> readSchemaSql(Connection connection, String type, String table) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT sql FROM sqlite_master
                WHERE type = ? AND tbl_name = ? AND sql IS NOT NULL
                ORDER BY name
                """)) {
            query.setString(1, type);
            query.setString(2, table);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString(1));
                }
            }
        }
        return result;
    }

    private static String createTableSql(String stagingTable, TableLayout layout,
                                         String enumColumn, List<String> allowedValues) {
        List<Column> primaryKeyColumns = layout.columns().stream()
                .filter(column -> column.primaryKeyOrder() > 0)
                .sorted(Comparator.comparingInt(Column::primaryKeyOrder))
                .toList();
        boolean inlinePrimaryKey = primaryKeyColumns.size() == 1;

        List<String> definitions = new ArrayList<>();
        for (Column column : layout.columns()) {
            StringBuilder definition = new StringBuilder(identifier(column.name()));
            if (column.type() != null && !column.type().isBlank()) {
                definition.append(' ').append(column.type());
            }
            if (column.notNull()) {
                definition.append(" NOT NULL");
            }
            if (column.defaultValue() != null) {
                definition.append(" DEFAULT ").append(column.defaultValue());
            }
            if (inlinePrimaryKey && column.primaryKeyOrder() == 1) {
                definition.append(" PRIMARY KEY");
                if (layout.autoincrement()) {
                    definition.append(" AUTOINCREMENT");
                }
            }
            definitions.add(definition.toString());
        }
        if (!inlinePrimaryKey && !primaryKeyColumns.isEmpty()) {
            definitions.add("PRIMARY KEY (" + joinIdentifiers(primaryKeyColumns.stream().map(Column::name).toList()) + ")");
        }
        for (UniqueConstraint unique : distinctUniqueConstraints(layout.uniqueConstraints())) {
            definitions.add("UNIQUE (" + joinIdentifiers(unique.columns()) + ")");
        }
        for (ForeignKey key : layout.foreignKeys()) {
            definitions.add(key.sql());
        }
        definitions.add("CHECK (" + identifier(enumColumn) + " IN (" + joinLiterals(allowedValues) + "))");
        return "CREATE TABLE " + identifier(stagingTable) + " (" + String.join(", ", definitions) + ")";
    }

    private static List<UniqueConstraint> distinctUniqueConstraints(List<UniqueConstraint> constraints) {
        Set<List<String>> distinct = new LinkedHashSet<>();
        List<UniqueConstraint> result = new ArrayList<>();
        for (UniqueConstraint constraint : constraints) {
            List<String> normalized = constraint.columns().stream()
                    .map(column -> column.toLowerCase(Locale.ROOT)).toList();
            if (distinct.add(normalized)) {
                result.add(constraint);
            }
        }
        return result;
    }

    private static String copyTableSql(String table, String stagingTable, List<Column> columns,
                                       String enumColumn, Map<String, String> replacements) {
        List<String> selectExpressions = new ArrayList<>();
        for (Column column : columns) {
            if (column.name().equalsIgnoreCase(enumColumn) && !replacements.isEmpty()) {
                StringBuilder value = new StringBuilder("CASE ").append(identifier(column.name()));
                replacements.forEach((oldValue, newValue) -> value.append(" WHEN ")
                        .append(literal(oldValue)).append(" THEN ").append(literal(newValue)));
                value.append(" ELSE ").append(identifier(column.name())).append(" END AS ").append(identifier(column.name()));
                selectExpressions.add(value.toString());
            } else {
                selectExpressions.add(identifier(column.name()));
            }
        }
        String names = joinIdentifiers(columns.stream().map(Column::name).toList());
        return "INSERT INTO " + identifier(stagingTable) + " (" + names + ") SELECT "
                + String.join(", ", selectExpressions) + " FROM " + identifier(table);
    }

    private static Long sequenceValue(Connection connection, String table) throws SQLException {
        if (tableSql(connection, "sqlite_sequence") == null) {
            return null;
        }
        try (PreparedStatement query = connection.prepareStatement("SELECT seq FROM sqlite_sequence WHERE name = ?")) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getLong(1) : null;
            }
        }
    }

    private static void restoreSequence(Connection connection, String table, Long sequence) throws SQLException {
        if (sequence == null || tableSql(connection, "sqlite_sequence") == null) {
            return;
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM sqlite_sequence WHERE name = ?");
             PreparedStatement insert = connection.prepareStatement("INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)")) {
            delete.setString(1, table);
            delete.executeUpdate();
            insert.setString(1, table);
            insert.setLong(2, sequence);
            insert.executeUpdate();
        }
    }

    private static String identifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String joinIdentifiers(List<String> values) {
        return values.stream().map(SqliteEnumConstraintMigrator::identifier).reduce((left, right) -> left + ", " + right).orElseThrow();
    }

    private static String joinLiterals(List<String> values) {
        return values.stream().map(SqliteEnumConstraintMigrator::literal).reduce((left, right) -> left + ", " + right).orElseThrow();
    }

    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private record Column(String name, String type, boolean notNull, String defaultValue, int primaryKeyOrder) {
    }

    private record UniqueConstraint(List<String> columns) {
    }

    private record ForeignKey(String referencedTable, List<ForeignKeyColumn> columns,
                              String onUpdate, String onDelete, String match) {
        String sql() {
            List<ForeignKeyColumn> sorted = columns.stream().sorted(Comparator.comparingInt(ForeignKeyColumn::order)).toList();
            StringBuilder definition = new StringBuilder("FOREIGN KEY (")
                    .append(joinIdentifiers(sorted.stream().map(ForeignKeyColumn::from).toList()))
                    .append(") REFERENCES ").append(identifier(referencedTable));
            List<String> referencedColumns = sorted.stream().map(ForeignKeyColumn::to).toList();
            if (referencedColumns.stream().allMatch(column -> column != null && !column.isBlank())) {
                definition.append(" (").append(joinIdentifiers(referencedColumns)).append(')');
            }
            appendAction(definition, "ON UPDATE", onUpdate);
            appendAction(definition, "ON DELETE", onDelete);
            appendAction(definition, "MATCH", match);
            return definition.toString();
        }

        private static void appendAction(StringBuilder target, String keyword, String action) {
            if (action != null && !action.isBlank() && !"NONE".equalsIgnoreCase(action)) {
                target.append(' ').append(keyword).append(' ').append(action);
            }
        }
    }

    private record ForeignKeyColumn(int order, String from, String to) {
    }

    private static final class ForeignKeyBuilder {
        private final String referencedTable;
        private final String onUpdate;
        private final String onDelete;
        private final String match;
        private final List<ForeignKeyColumn> columns = new ArrayList<>();

        private ForeignKeyBuilder(String referencedTable, String onUpdate, String onDelete, String match) {
            this.referencedTable = referencedTable;
            this.onUpdate = onUpdate;
            this.onDelete = onDelete;
            this.match = match;
        }

        private ForeignKey build() {
            return new ForeignKey(referencedTable, List.copyOf(columns), onUpdate, onDelete, match);
        }
    }

    private record TableLayout(List<Column> columns, List<UniqueConstraint> uniqueConstraints,
                               List<ForeignKey> foreignKeys, List<String> indexSql,
                               List<String> triggerSql, boolean autoincrement) {
    }
}
