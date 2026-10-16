package com.pcba.erp;

import org.flywaydb.core.Flyway;
import java.sql.DriverManager;

/** Offline entry point: runs Flyway without starting HTTP, schedulers or bootstrap users. */
public final class DatabaseMigration {
    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "migrate" : args[0];
        String url = "jdbc:postgresql://" + value("DB_HOST", "127.0.0.1") + ":"
                + value("DB_PORT", "5432") + "/" + value("POSTGRES_DB", "pcba_erp")
                + "?sslmode=" + value("DB_SSLMODE", "disable");
        String user = value("POSTGRES_USER", "pcba");
        String password = System.getenv("POSTGRES_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Missing POSTGRES_PASSWORD");
        var config = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").cleanDisabled(true).validateOnMigrate(true);
        switch (command) {
            case "migrate" -> config.load().migrate();
            case "validate" -> config.load().validate();
            case "baseline-import", "baseline-source" -> {
                try (var connection = DriverManager.getConnection(url, user, password);
                     var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT COUNT(*) FROM _pcba_mysql_import")) {
                    if (!result.next() || result.getLong(1) != 1) {
                        throw new IllegalStateException("A completed, verified MySQL import is required before baselining");
                    }
                }
                // V4-V7 only repair legacy data/menu seeds. A complete production
                // snapshot already supplies authoritative data and must retain it.
                String version = command.equals("baseline-source") ? "7" : "3";
                config.baselineVersion(version).baselineDescription("Verified MySQL import").load().baseline();
            }
            default -> throw new IllegalArgumentException("Use migrate, validate, baseline-import or baseline-source");
        }
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
