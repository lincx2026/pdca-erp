# PostgreSQL / OSS 改造与本地迁移记录

日期：2026-10-15。已改造代码并迁移本机现有数据；尚未发布到公网生产环境。

## 技术架构与部署

Vue 3 / Vite 前端由 Nginx 提供静态资源和 HTTPS；`/api` 转发至 Java 17 / Spring Boot 3 模块化单体。
后端通过 MyBatis-Plus / JDBC 访问 PostgreSQL 16，通过 Redis 保存缓存与会话，通过阿里云 OSS SDK 操作附件。
Docker Compose 编排 PostgreSQL、Redis、后端和 Nginx；OSS 使用云端私有 bucket。

`.env` 保存本机配置，不提交版本控制或打入镜像；`.env.example` 提供字段模板，初始化脚本生成随机密码和 JWT 密钥。
PostgreSQL 使用 `pcba-erp-postgres-data` 命名卷。生产配置域名、HTTPS 证书及 OSS 凭据后执行 `bash deploy.sh all`。
后续按需发布 `be` 或 `fe`；就绪检查失败时恢复前一应用镜像。数据库版本迁移不随镜像回滚。
完整命令及恢复步骤见根目录 README。

## 现有数据保留与迁移结果

- 原 `docker/mysql/data` 共 249 个文件已完整备份；迁移后文件 SHA-256 清单保持一致。
- 现有 MySQL 的 **62 张表、1,488 行**已导入本机 PostgreSQL。原账号、密码哈希、角色与授权均保留。
- 全部源字段通过逐表行数与规范化行内容 SHA-256 对比；补充保留实际源库中的 6 个额外字段。
- 目标结构共 96 张业务/系统表；源库没有的表为空，导入没有覆盖原菜单、权限或填充演示业务数据。
- Flyway 导入基线为版本 3；后续变更从 V4 开始。原 MySQL 数据目录未修改或删除。
- PostgreSQL `pg_dump -Fc` 备份已在隔离数据库恢复，并再次通过 62 张表、1,488 行的完整内容校验。
- 本机 MinIO 未发现业务对象，数据库 `pdm_file_ref` 为空。本次无历史附件需要复制；另提供保留 objectKey 的迁移工具。

备份与校验文件位于本机 `backup/`（不提交版本控制）：

| 文件 | 内容 |
| --- | --- |
| `mysql-before-postgresql-20261015/data/` | 原 MySQL 数据文件副本 |
| `mysql-before-postgresql-20261015/sha256-manifest.json` | 原文件完整性清单 |
| `postgresql-migrated-20261015.dump` | PostgreSQL 自定义格式备份 |
| `postgresql-migrated-20261015.sha256` | PostgreSQL 备份摘要 |
| `postgresql-migration-verification-20261015.json` | 导入后逐表内容校验 |
| `postgresql-restore-verification-20261015.json` | 备份恢复后的逐表内容校验 |

恢复到新空库时使用 `pg_restore --no-owner --no-privileges --exit-on-error -d <新库> <备份文件>`。
不要覆盖当前业务库或删除命名卷；应用回滚命令不承担数据库恢复。
已有用户继续使用原密码；`.env` 中的初始管理员密码只用于空用户库。

## 已处理的 P0 问题

1. 部署不再扫描并执行历史 SQL；Flyway 管理版本、摘要校验及迁移锁，禁止自动基线和 clean。
2. 库存使用原子 UPSERT、带可用量条件的扣减、单据行锁和数据库约束；财务库存按精确批次加锁，阻止超扣及重复确认。
3. 构建失败阻止发布；后端就绪检查包含 PostgreSQL 与 Redis，Nginx 健康检查绕过代理变量，发布失败恢复应用镜像。
4. 移除默认账号密码和硬编码密钥；强制有效 JWT 密钥，收紧匿名端点与 CORS，关闭默认 API 文档和 Druid 控制台。

## 验收与边界

- 后端打包成功；9 项自动化测试通过，包括 OSS SDK 模拟测试及 PostgreSQL 并发库存/重复单据确认测试。
- 189 条 Mapper SELECT 查询计划在 PostgreSQL 上通过。
- 登录、用户信息和就绪探针通过；137 个 GET 接口通过，另 14 个接口需要显式业务参数，未执行完整业务写入流程回归。
- 4 项部署脚本模拟测试通过，包含不完整快照时禁止恢复过期版本；Compose 配置、Bash 语法、真实 Nginx 配置及健康地址检查通过。
- 前端 Vite 生产构建通过。项目原有严格 TypeScript 类型检查仍有错误，需另行修复。
- OSS 已使用本机 `.env` 完成真实上传、下载 SHA-256 校验、临时签名链接下载、删除及对象不存在检查；测试对象全部清理，凭据未写入日志或文档。
- 联调修复了空 STS 令牌导致临时签名链接返回 `403 SignatureDoesNotMatch` 的问题：空白令牌统一传入 null；新增无令牌及有效 STS 参数的签名回归测试。
- 本次 5 项 OSS 自动化测试通过。SDK 3.18.1 依据令牌是否为 null 判定 STS，参见 [DefaultCredentials 源码](https://github.com/aliyun/aliyun-oss-java-sdk/blob/3.18.1/src/main/java/com/aliyun/oss/common/auth/DefaultCredentials.java#L59)。
- 生产切换仍需配置生产域名/证书/OSS，停止业务写入后重新执行迁移与独立内容校验，并验收核心写入业务。

## 本机历史目录整理

- `docker/mysql/`、`docker/minio/`、空的 `docker/postgres/`、`db/init/`、`db/patch/` 及旧构建包、调试工具已移至 `backup/legacy-cleanup-20261015/`，保持相对目录结构。
- 原 MySQL 数据现位于 `backup/legacy-cleanup-20261015/docker/mysql/data/`；原始独立备份和 PostgreSQL 备份继续保留。
- `.migration-work/`、前端 `node_modules/`、`dist/`、旧打包归档及 Python 缓存已移至 `backup/rebuildable-cache-20261015/`。
- 直接删除被自动审批拦截，因此此次采用可恢复归档，仅整理工作目录，没有释放对应磁盘空间。
- 当前 `.env`、PostgreSQL 命名卷、Redis 数据和 HTTPS 证书保留；两份新归档均由 Git 忽略。
