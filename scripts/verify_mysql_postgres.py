"""Read-only, per-table row-count and content verification after MySQL import."""
from pathlib import Path
from decimal import Decimal
from datetime import date, datetime
import argparse
import hashlib
import json
from migrate_mysql_to_postgres import load_env

def canonical(value, data_type):
    if value is None: return ["sql-null"]
    if data_type in ("smallint","integer","bigint","numeric","real","double precision"):
        number=Decimal(str(value))
        text=format(number,"f")
        if "." in text: text=text.rstrip("0").rstrip(".")
        return ["number","0" if number.is_zero() else text]
    if data_type in ("json","jsonb"):
        return ["json",json.loads(value) if isinstance(value,str) else value]
    if isinstance(value,(bytes,bytearray,memoryview)): return ["bytes",bytes(value).hex()]
    if isinstance(value,(date,datetime)): return ["date",value.isoformat()]
    return ["text",str(value)]

def digest(rows,columns,types,table):
    hashes=[]; count=0
    # ponytail: O(rows) hash storage per table; use external sorting for million-row tables.
    for row in rows:
        values=[row[c] for c in columns] if isinstance(row,dict) else list(row)
        if table=="wms_inventory" and "batch_no" in columns:
            index=columns.index("batch_no"); values[index]=values[index] or ""
        normalized=[canonical(v,types[c]) for c,v in zip(columns,values)]
        raw=json.dumps(normalized,ensure_ascii=False,sort_keys=True,separators=(",",":")).encode()
        hashes.append(hashlib.sha256(raw).digest()); count+=1
    result=hashlib.sha256()
    for row_hash in sorted(hashes): result.update(row_hash)
    return count,result.hexdigest()

def main():
    import pymysql,psycopg
    from psycopg import sql
    root=Path(__file__).resolve().parents[1]
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file",default=str(root/".env"))
    parser.add_argument("--report",required=True)
    parser.add_argument("--fingerprints",help="Write PostgreSQL table fingerprints for verified ECS replacement")
    args=parser.parse_args(); env=load_env(args.env_file)
    source=pymysql.connect(host=env["MYSQL_HOST"],port=int(env.get("MYSQL_PORT","3306")),
        user=env["MYSQL_USER"],password=env["MYSQL_PASSWORD"],database=env["MYSQL_DATABASE"],
        charset="utf8mb4",cursorclass=pymysql.cursors.SSDictCursor,connect_timeout=10,read_timeout=60)
    target=psycopg.connect(host=env.get("DB_HOST","127.0.0.1"),port=int(env.get("DB_PORT","5432")),
        user=env.get("POSTGRES_USER","pcba"),password=env["POSTGRES_PASSWORD"],
        dbname=env.get("POSTGRES_DB","pcba_erp"),connect_timeout=10,sslmode=env.get("DB_SSLMODE","disable"))
    report={}
    try:
        target.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
        with source.cursor() as cursor:
            cursor.execute("SET time_zone=%s",(env.get("MYSQL_SESSION_TIMEZONE","+08:00"),))
            cursor.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            cursor.execute("SHOW TABLES"); tables=[next(iter(row.values())) for row in cursor.fetchall()]
        for table in sorted(tables):
            if table=="sys_db_patch": continue
            types=dict(target.execute("SELECT column_name,data_type FROM information_schema.columns WHERE table_schema='public' AND table_name=%s",(table,)).fetchall())
            with source.cursor() as cursor:
                cursor.execute("SELECT * FROM `"+table.replace("`","``")+"`")
                columns=[c[0] for c in cursor.description]
                expected=digest(cursor,columns,types,table)
            selected=[sql.SQL("{}::text").format(sql.Identifier(c)) if types[c] in ("json","jsonb") else sql.Identifier(c) for c in columns]
            with target.cursor(name="verify_"+table) as cursor:
                cursor.execute(sql.SQL("SELECT {} FROM {}").format(sql.SQL(",").join(selected),sql.Identifier(table)))
                actual=digest(cursor,columns,types,table)
            report[table]={"rows":expected[0],"sha256":expected[1],"matches":expected==actual}
        Path(args.report).parent.mkdir(parents=True,exist_ok=True)
        Path(args.report).write_text(json.dumps(report,indent=2),encoding="utf-8")
        mismatches=[t for t,r in report.items() if not r["matches"]]
        if mismatches: raise RuntimeError("Content mismatch: "+", ".join(mismatches))
        if args.fingerprints:
            from ecs_reimport import fingerprint_sql
            target.execute("SET TIME ZONE 'Asia/Shanghai'")
            tables=target.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name").fetchall()
            fingerprints={table:target.execute(fingerprint_sql(table)).fetchone()[0] for table, in tables}
            Path(args.fingerprints).write_text(json.dumps(fingerprints,indent=2),encoding='utf-8')
        print(f"Verified all values: {len(report)} tables, {sum(r['rows'] for r in report.values())} rows.")
    finally:
        target.rollback();target.close();source.rollback();source.close()

if __name__=="__main__": main()
