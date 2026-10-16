# PCBA ERP

Vue 3 + Spring Boot 3 / Java 17 模块化 ERP，使用 PostgreSQL 16、Redis、阿里云 OSS。
后端为 `pcba-erp.jar`；Nginx 提供 HTTPS、前端静态资源和 `/api` 反向代理。

## 环境配置

执行 `python scripts/init-env.py`，仅创建不存在的根目录 `.env`，随机生成数据库、Redis、JWT、初始管理员密码，不打印密钥。
`.env` 被 Git 和 Docker 构建排除；可提交的模板为 `.env.example`。使用不加引号的 KEY=value 格式。
Spring 从项目根目录或 backend 工作目录自动读取 `.env`，系统环境变量优先。

填写 OSS_ENDPOINT（HTTPS）、OSS_BUCKET、OSS_ACCESS_KEY_ID、OSS_ACCESS_KEY_SECRET；STS 需同时填写 OSS_SECURITY_TOKEN。
预先创建私有 bucket，应用 RAM 账号只授予所需对象的 GetObject/PutObject/DeleteObject 权限。
应用不会创建 bucket 或修改 ACL；保留 objectKey，迁移后数据库附件引用无需变化。
OSS 未配置时其他业务可启动，附件操作报配置错误。STS 过期后须更新令牌并重启。
生产使用独立密钥；曾用于实际环境的历史明文密钥需要另行轮换。

首次登录账号 admin，密码从本机 `.env` 的 BOOTSTRAP_ADMIN_PASSWORD 读取。
只在用户表为空时创建管理员，已有账号不会重置。

## 本地开发

安装 Java 17、Maven、Node.js 20、Docker Compose v2。

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d postgres redis
cd backend
mvn clean package -pl pcba-erp-admin -am
java -jar pcba-erp-admin/target/pcba-erp.jar
```

另开终端进入 frontend 执行 `npm ci`、`npm run dev`。前端端口 5180，后端 8080。
首次后端启动执行 Flyway 建库。接口文档默认关闭，开发可设 API_DOCS_ENABLED=true；文档仍要求登录。

## 生产部署

服务器需要 Docker Compose v2、Bash。构建在 Maven/Node 容器中完成。
创建生产 `.env`，设置 OSS 和 ERP_DOMAIN，将自己的域名证书放入
`docker/nginx/ssl/fullchain.pem`、`private.key`，再执行：

```bash
bash deploy.sh all
bash deploy.sh status
```

首次必须 all；后续可用 be、fe。脚本生成独立版本镜像，先构建，再启动并等待 PostgreSQL/Redis、后端、Nginx 就绪。
失败恢复本轮切换前的镜像；`bash deploy.sh rollback` 手工恢复镜像。
回滚不撤销 SQL，也不恢复挂载的 Nginx 配置和证书。数据库变更应兼容前一版本应用，分阶段删除旧字段。
旧 `deploy.sh sql` 不再执行散落补丁；新增 Flyway 迁移后发布后端。

Nginx HTTP `/healthz` 返回 200，其他路径跳转 HTTPS；后端 `/actuator/health/readiness` 检查数据库和 Redis。
外部验收地址为 HTTPS `/api/actuator/health/readiness`。只有 80/443 对外，其余端口仅绑定回环。
PostgreSQL 数据放在 Docker 命名卷 `pcba-erp-postgres-data`（可通过 POSTGRES_VOLUME_NAME 配置），兼容 Windows 文件权限限制。
原 `docker/mysql/data`、`docker/minio/data` 不会被修改或删除。不要执行带 `-v` 的 compose down 删除数据库卷。
每天做 pg_dump 异机备份，按恢复目标配置 WAL 归档并演练恢复；OSS 使用版本控制/备份策略。

## 数据及附件迁移

先备份并在测试环境演练，正式导入期间停止业务写入。禁止向现有库重跑旧 init/patch。

```powershell
python -m venv .migration-work/venv
.migration-work/venv/Scripts/python -m pip install -r scripts/migration-requirements.txt
```

在迁移环境文件中增加 MYSQL_HOST、MYSQL_PORT、MYSQL_DATABASE、MYSQL_USER、MYSQL_PASSWORD，
并配置目标 DB_HOST、DB_PORT、POSTGRES_DB、POSTGRES_USER、POSTGRES_PASSWORD。
目标必须是没有任何表的新 PostgreSQL 数据库，不能先启动应用初始化。
工具需要 Java 17 和已构建的 JAR；以下 python 应使用上面虚拟环境中的解释器：

```powershell
python scripts/migrate_mysql_to_postgres.py --env-file .env
python scripts/migrate_mysql_to_postgres.py --env-file .env --execute
python scripts/verify_mysql_postgres.py --env-file .env --report backup/migration-verification.json
```

默认只预检，--execute 才导入。源库只读一致性快照；结构和数据在目标单一事务中建立，逐表核对行数并恢复序列，
最后使用 Flyway 建立版本 3 导入基线。保留原账号、密码哈希及授权。
独立校验工具逐表比较行数以及全部源字段的规范化行摘要；正式切换前应停止写入后再次核对。
未知字段、空批次合并冲突或不合法库存会导致导入回滚，不会静默丢弃或修正数据。
导入基线来源于历史 SQL；线上额外手工改过的结构会报告 schema drift，需补充迁移后重新演练。
切换前核对 PostgreSQL 与 MySQL collation 差异影响的用户名、编码唯一性及搜索行为。

如仅最后 Flyway 步骤失败，已导入数据保留，可在相同系统环境变量下恢复：

```bash
java -Dloader.main=com.pcba.erp.DatabaseMigration -cp backend/pcba-erp-admin/target/pcba-erp.jar org.springframework.boot.loader.launch.PropertiesLauncher baseline-import
```

Java 工具从系统环境变量读取连接，不自动解析 `.env`；Python 导入工具会自动传入。
其他导入错误请换新空库重试，不覆盖已有数据。

附件迁移另外配置 MINIO_ENDPOINT、MINIO_ACCESS_KEY、MINIO_SECRET_KEY、MINIO_BUCKET 和 OSS 凭据：

```powershell
python scripts/migrate_objects_to_oss.py --env-file .env
python scripts/migrate_objects_to_oss.py --env-file .env --execute
```

默认仅列举。执行保留 objectKey，逐个校验 SHA-256；已存在对象也必须通过校验，源对象不删除。
复制和校验产生 OSS 请求及流量。数据库与附件验收通过后再切换业务入口。

## 验证

后端：`mvn test -pl pcba-erp-admin -am`。前端：`npm run build`，独立类型检查 `npm run type-check`。
OSS 单元测试不连接云端。PostgreSQL 集成测试使用专用的本地测试端口，禁止改为生产地址：

```powershell
docker run -d --name pcba-postgres-test-20261015 -e POSTGRES_PASSWORD=postgres-test-only -e POSTGRES_DB=pcba_erp -p 127.0.0.1:55432:5432 postgres:16-alpine
docker run -d --name pcba-redis-test-20261015 -p 127.0.0.1:56379:6379 redis:7-alpine redis-server --requirepass redis-test-only
$env:PCBA_INTEGRATION_TESTS='true'
cd backend
mvn test -pl pcba-erp-admin -am
```

测试覆盖建库、Mapper 查询计划、并发首次入库、并发扣减及同单并发确认。
前端原有严格类型检查错误需单独修复，正式构建仍使用 Vite。

本次本地数据迁移、备份恢复和验收记录见 [迁移记录](docs/migration-20261015.md)。
