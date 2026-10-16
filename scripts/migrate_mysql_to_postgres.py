"""Copy a read-only MySQL snapshot to a NEW PostgreSQL database.

Default: preflight only. --execute requires an entirely empty target database.
Stop business writes before the final copy. Never change the source database.
"""
from pathlib import Path
import argparse
import json
import os
import subprocess

def load_env(path):
    values = {}
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return {**values, **os.environ}

def main():
    import pymysql
    import psycopg
    from psycopg import sql
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default=str(root/".env"))
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--preserve-source-data", action="store_true",
                        help="Preserve authoritative production menus/data; skip historical V4-V7 data repairs")
    parser.add_argument("--jar", default=str(root/"backend/pcba-erp-admin/target/pcba-erp.jar"))
    args = parser.parse_args()
    env = load_env(args.env_file)
    required = ["MYSQL_HOST", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD", "POSTGRES_PASSWORD"]
    if any(not env.get(key) for key in required):
        raise SystemExit("Configure MYSQL_HOST/MYSQL_DATABASE/MYSQL_USER/MYSQL_PASSWORD and PostgreSQL settings in the env file.")
    if args.execute and not Path(args.jar).is_file():
        raise SystemExit("Build the backend jar first; it contains the Flyway baseline entry point.")
    source = pymysql.connect(host=env["MYSQL_HOST"], port=int(env.get("MYSQL_PORT", "3306")),
        user=env["MYSQL_USER"], password=env["MYSQL_PASSWORD"], database=env["MYSQL_DATABASE"],
        charset="utf8mb4", cursorclass=pymysql.cursors.SSDictCursor,
        connect_timeout=10, read_timeout=60, write_timeout=60)
    target = psycopg.connect(host=env.get("DB_HOST", "127.0.0.1"), port=int(env.get("DB_PORT", "5432")),
        dbname=env.get("POSTGRES_DB", "pcba_erp"), user=env.get("POSTGRES_USER", "pcba"),
        password=env["POSTGRES_PASSWORD"], sslmode=env.get("DB_SSLMODE", "disable"), connect_timeout=10)
    counts = {}
    try:
        with source.cursor() as cursor:
            cursor.execute("SET time_zone = %s",(env.get("MYSQL_SESSION_TIMEZONE", "+08:00"),))
            cursor.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            cursor.execute("SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")
            metadata = list(cursor.fetchall())
            source_tables = sorted(row["TABLE_NAME"] for row in metadata if row["TABLE_NAME"] != "sys_db_patch")
            if any(row["ENGINE"] != "InnoDB" for row in metadata):
                raise RuntimeError("All source tables must use InnoDB for a consistent snapshot")
            for table in source_tables:
                cursor.execute("SELECT COUNT(*) AS count FROM `"+table.replace("`","``")+"`")
                counts[table] = cursor.fetchall()[0]["count"]
        existing = target.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'").fetchall()
        if existing:
            raise RuntimeError("Target is not empty. Create a separate new PostgreSQL database; no existing tables are overwritten.")
        if not args.execute:
            print(f"Preflight: {len(counts)} source tables, {sum(counts.values())} rows, empty target. No changes made.")
            return
        baseline = root/"backend/pcba-erp-admin/src/main/resources/db/migration/V1__postgresql_schema.sql"
        target.execute(baseline.read_text(encoding="utf-8"))
        target.execute((baseline.parent/"V3__preserve_legacy_mysql_fields.sql").read_text(encoding="utf-8"))
        target_tables = {row[0] for row in target.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")}
        unexpected = set(source_tables)-target_tables
        if unexpected: raise RuntimeError("Source schema has unsupported tables: "+", ".join(sorted(unexpected)))
        for table in source_tables:
            target_columns = {row[0] for row in target.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=%s",(table,))}
            with source.cursor() as cursor:
                cursor.execute("SELECT * FROM `"+table.replace("`","``")+"`")
                columns = [col[0] for col in cursor.description]
                unknown = set(columns)-target_columns
                if unknown: raise RuntimeError(f"Schema drift in {table}: {sorted(unknown)}")
                insert = sql.SQL("INSERT INTO {} ({}) VALUES ({})").format(sql.Identifier(table),
                    sql.SQL(",").join(map(sql.Identifier, columns)),sql.SQL(",").join(sql.Placeholder()*len(columns)))
                with target.cursor() as destination:
                    while True:
                        batch = cursor.fetchmany(1000)
                        if not batch: break
                        if table == "wms_inventory":
                            for row in batch: row["batch_no"] = row["batch_no"] or ""
                        destination.executemany(insert, [tuple(row[c] for c in columns) for row in batch])
            actual = target.execute(sql.SQL("SELECT COUNT(*) FROM {}").format(sql.Identifier(table))).fetchone()[0]
            if actual != counts[table]: raise RuntimeError(f"Row count mismatch: {table}")
            print(f"Verified {table}: {actual} rows", flush=True)
        identities = target.execute("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='public' AND is_identity='YES'").fetchall()
        for table, column in identities:
            target.execute(sql.SQL("SELECT setval(pg_get_serial_sequence(%s,%s), COALESCE(MAX({}),1), MAX({}) IS NOT NULL) FROM {}").format(
                sql.Identifier(column),sql.Identifier(column),sql.Identifier(table)),(table,column))
        target.execute("CREATE TABLE _pcba_mysql_import (completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, row_counts JSONB NOT NULL)")
        target.execute("INSERT INTO _pcba_mysql_import(row_counts) VALUES (%s::jsonb)",(json.dumps(counts),))
        target.commit()
        # Let Flyway create its own history rather than fabricating history rows.
        command = ["java", "-Dloader.main=com.pcba.erp.DatabaseMigration", "-cp", args.jar,
                   "org.springframework.boot.loader.launch.PropertiesLauncher",
                   "baseline-source" if args.preserve_source_data else "baseline-import"]
        subprocess.run(command, env=env, check=True)
        print("Import verified and baselined. Configure OSS and validate the application before switching traffic.")
    except Exception:
        target.rollback()
        raise
    finally:
        source.rollback()
        source.close()
        target.close()

if __name__ == "__main__":
    main()
