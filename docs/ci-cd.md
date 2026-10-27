# GitHub Actions → 阿里云 ECS

工作流为 `.github/workflows/ci-cd.yml`。面向一台 Linux x86_64 ECS，使用现有 Docker Compose 编排，不要求在 ECS 安装 Java、Maven 或 Node.js。

## 流程

```mermaid
flowchart LR
  A[PR / main / 手动触发] --> B[Java 17 测试与打包]
  B --> C[Vue 生产构建与部署脚本测试]
  C --> D{main 检查通过}
  D --> E[发布 GHCR 后端及前端镜像]
  E --> F{DEPLOY_ENABLED=true}
  F --> G[SSH 连接 ECS]
  G --> H[固定提交及镜像摘要发布]
  H --> I[数据库 / Redis / 应用健康检查]
  I --> J[成功记录当前镜像]
  I --> K[失败恢复镜像与源码配置]
```

- PR 只测试和构建，不访问部署凭据、不发布镜像。
- CI 使用独立 PostgreSQL/Redis，执行 5 项 OSS 单元测试、6 项 PostgreSQL 集成测试及部署脚本测试，不连接生产数据库或 OSS。
- 前端运行 `npm run build`。原有严格类型检查错误尚未修复，因此 `type-check` 暂未设为 CI 门禁。
- `main` 检查通过后生成两个 `linux/amd64` 镜像，标签为提交 SHA；Actions 按 `@sha256:...` 摘要拉取，再与专用 Redis 镜像一起经 SSH 压缩流传到 ECS。ECS 启动前核对加载后的镜像 ID，使用包含提交 SHA 的项目专用本地标签。
- 镜像示例：`ghcr.io/lincx2026/pdca-erp-backend`、`ghcr.io/lincx2026/pdca-erp-frontend`。
- Actions 将完整 Git bundle 经 SSH 传到临时目录，并核对 main 提交，ECS 无需直接访问 github.com；临时源码包在结束时清理。
- 自动部署仅作用于 `main`；手动运行默认只检查和发布镜像，勾选 deploy 才执行 CD。仓库未设置 DEPLOY_ENABLED 时跳过 CD。
- 部署按 production 并发组串行执行，运行中的部署不会被新提交取消；ECS 上另用 flock 加锁。落后于当前 main 的任务不会覆盖服务器。

## ECS 首次准备

### 共享 ECS（已有 PostgreSQL、Nginx 和其他项目）

生产环境 Secret `ERP_ENV` 保存完整运行 `.env`，仅 production 部署和只读预检使用，不进入镜像或 Git。首次发布会在目标目录不存在时克隆仓库，并以 0600 权限创建 `.env`；已有服务器 `.env` 不会被覆盖。后续运行配置须在服务器同步更新，更新 Secret 仅影响首次初始化和预检。

运行配置设置 `DEPLOY_MODE=shared`，填写既有 PostgreSQL 的 DB_HOST、DB_PORT、POSTGRES_DB、POSTGRES_USER、POSTGRES_PASSWORD、DB_SSLMODE，并设置域名和 CORS。另设：

```dotenv
ERP_DOMAIN=erp.thinkfiter.com
CORS_ALLOWED_ORIGINS=https://erp.thinkfiter.com
ERP_HTTP_PORT=18080
BACKEND_PORT=18081
ERP_REDIS_PORT=16379
ERP_TLS_CERT=/etc/letsencrypt/live/your-domain/fullchain.pem
ERP_TLS_KEY=/etc/letsencrypt/live/your-domain/privkey.pem
```

证书路径指向服务器上覆盖正式域名的有效证书。共享模式要求 Linux、Compose >= 2.24.4、Python >= 3.6、psql、pg_dump、Nginx、OpenSSL、curl。后端和前端使用主机网络并仅监听回环地址，Redis 仅发布回环 16379；不启动 Compose PostgreSQL、不占用已有 Redis 6379。选定端口须未被占用。

若没有匹配的证书，可显式设置 `ERP_TLS_AUTO=true`，并将两个证书路径设为 `/etc/letsencrypt/live/<ERP_DOMAIN>/fullchain.pem` 和 `privkey.pem`。发布脚本仅创建 ERP 域名的 HTTP ACME 路由，通过 Certbot webroot 申请独立证书；保留验证路由供续期，续期后平滑加载 Nginx。申请失败恢复原 ERP 虚拟主机。该模式需要域名 DNS 正确指向 ECS、80 端口公网可达及已安装 Certbot。

若本机 PostgreSQL 对 ERP 使用 Ident，root 部署可在确认目标库、登录账号和密码已存在后，按原密码存储格式增加仅该库、该账号、127.0.0.1 的密码认证规则。修改前保存 pg_hba.conf 权限 0600 的备份，仅 reload；连接或结构校验失败立即恢复原规则。不修改账号密码、其他库规则或重启数据库。

部署先只读验证数据库连接和结构，拒绝自动迁移缺少 ERP/Flyway 标记的非空数据库；每次应用切换前通过 pg_dump 备份目标库到 `.deploy-backups/database-before-*.dump`。空库允许 Flyway 初始化，但不会自动迁入本机历史业务数据。已有非 Flyway ERP 数据库应先完成可审查的离线迁移，不能自动 baseline。

仅管理 `/etc/nginx/conf.d/pcba-erp.conf`，拒绝覆盖无管理标记的文件或抢占已有同域名虚拟主机。引用已有证书，执行 `nginx -t` 后平滑 reload，并验证带有效 TLS 的 HTTPS readiness；失败恢复 ERP 虚拟主机和可用旧镜像。其他站点配置、数据库实例和证书文件不作修改。

首次发布前运行 Actions 的 **ECS read-only preflight** 查看连接、端口、域名和证书检查；预检不会创建业务表或修改服务。正式域名 DNS 应指向该 ECS。

### 独立 ECS（默认 standalone 模式）

安装 Git、Bash、Docker Engine、Compose v2、flock（util-linux）。部署账号需要目标目录写权限和 Docker 权限。
源码和镜像均由 Actions 经 SSH 传输，ECS 无需直接访问 GitHub、GHCR 或 Docker Hub。首次数据迁入需要访问 OSS；自动证书申请需要访问 ACME 服务。
安全组开放域名服务的 80/443，以及供 Actions 连接的 SSH 端口；数据库和 Redis 仅绑定回环地址。

以部署账号执行：

```bash
git clone https://github.com/lincx2026/pdca-erp.git /opt/pcba-erp
cd /opt/pcba-erp
python3 scripts/init-env.py
mkdir -p docker/nginx/ssl
```

在 ECS 本机编辑 `.env`，填写生产域名、OSS、CORS、数据库和 Redis 配置，放置域名证书：

- `docker/nginx/ssl/fullchain.pem`
- `docker/nginx/ssl/private.key`

首次构建镜像在 Actions 完成；服务器不需要执行 `deploy.sh all`。已有 MySQL 数据必须先按 README 迁入新 PostgreSQL 库，再启用自动部署。
如果已有生产 PostgreSQL，核对 Compose 命名卷 POSTGRES_VOLUME_NAME，避免误连空库。运行数据与 `.env`、证书不会通过 Actions 上传或覆盖。
服务器已跟踪的源码文件应保持干净，定制域名等参数写在 `.env`；脏工作区会阻止自动切换版本。
不要在自动部署期间同时手工运行 deploy.sh。每次数据库变更前备份，Flyway 迁移须兼容前一应用版本。

## GitHub 配置

在仓库 Settings → Environments 创建 `production`，部署分支限制为 main。在该环境添加 Secrets：

| Secret | 内容 |
| --- | --- |
| ECS_HOST | ECS 公网 IP 或可解析主机名，不带协议和端口 |
| ECS_USER | 专用 SSH 部署账号 |
| ECS_SSH_KEY | 该账号的专用 SSH 私钥，使用无口令自动化密钥 |
| ECS_KNOWN_HOSTS | 经可信渠道核对指纹后的 SSH known_hosts 完整记录；非 22 端口记录使用 `[host]:port` |
| ERP_ENV | 完整生产 `.env`；共享模式首次初始化需要，已有服务器文件保留 |

私钥对应的公钥需要放入 ECS 部署账号的 `~/.ssh/authorized_keys`。
不关闭主机指纹校验，不将私钥、`.env` 或 OSS 凭据提交仓库。可以在可信管理机采集公钥后，与 ECS 控制台/管理员提供的指纹核对，再保存到 Secret。

在 Settings → Secrets and variables → Actions → Variables 添加**仓库级**变量：

| Variable | 默认值 / 用途 |
| --- | --- |
| DEPLOY_ENABLED | 填 `true` 开启 CD；未设置时仅 CI 和镜像发布 |
| DEPLOY_PATH | `/opt/pcba-erp`，绝对路径，仅使用字母、数字、下划线、连字符及 `/` |
| ECS_PORT | `22` |
| ERP_DOMAIN | 只读预检使用的正式域名；实际服务域名来自 ERP_ENV / 服务器 .env |

GHCR 发布使用工作流自动生成的 GITHUB_TOKEN，无需另填长期仓库令牌。工作流给发布 job packages:write，部署 job packages:read；镜像 source 标签关联本仓库。
首次发布后检查 GHCR 包的 Actions access 是否允许本仓库读取；不要公开业务镜像来绕过权限问题。
Actions 拉取 GHCR 使用该次任务的临时令牌和临时 Docker 配置目录，任务结束后清理；服务器不保存仓库登录令牌。成功发布的 GHCR 源摘要记录在 `.deploy-backups/source-images.env`，实际运行镜像记录在 `current.env`。

## 发布、回滚与排查

推送 main 自动执行 CI/CD；也可在 Actions → CI and ECS deployment → Run workflow 手动运行。
配置完 Secrets 和变量后手动运行一次并勾选 deploy；查看三个 job：ci、publish、deploy。

在 ECS 查看状态或回滚镜像：

```bash
cd /opt/pcba-erp
bash deploy.sh status
bash deploy.sh rollback
```

当前镜像记录在 `.deploy-backups/current.env`，上一完整镜像快照在 `.deploy-backups/previous.yml`，最近成功的 CD 提交在 `.deploy-backups/revision`。
直接执行 Compose 命令时需要同时传入 `.env` 与 `.deploy-backups/current.env`，否则会使用 Compose 默认 local 镜像。
自动发布失败会恢复之前的 Git 提交和配置，并使用现有镜像回滚机制恢复应用；首次发布没有旧版本时只能报告失败，需修复后重跑。
手工 `deploy.sh rollback` 仅恢复应用镜像，不回滚 Git 配置或数据库；配置回退需结合相应 Git 版本处理。数据库恢复应单独演练。
不会执行历史 MySQL SQL、删除数据库卷或清理已有业务数据。

## 首次迁入已验证的历史数据

`scripts/stage_ecs_import.py` 将本机校验通过的 PostgreSQL dump 使用随机密码和 OpenSSL 加密，上传私有 OSS 临时对象，并通过 gh stdin 设置 production Secret `ERP_DATA_IMPORT`。临时下载地址有效期 4 小时，明文备份和解密密码不会进入 Git、镜像或 Actions 构建产物。工具依赖迁移 requirements 中的 oss2、OpenSSL 和 gh。

服务器在备份目标空库后，验证下载文件的 SHA-256，以 `pg_restore --single-transaction --no-owner --no-privileges` 恢复，再逐表核对历史记录数；拒绝覆盖非空库。成功记录 `.deploy-backups/initial-data-import.sha256`，同一备份重复发布不会再次恢复。应用运行后仍需保留本机原始备份。

首次导入并完成发布后，用 `stage_ecs_import.py --state <本机暂存记录> --delete` 删除临时 OSS 对象与 ERP_DATA_IMPORT Secret。该工具的暂存记录只含对象键和校验和，应保存在已忽略的 backup 目录。

## 已有新站数据的完整替换

仅在明确需要以旧生产库替换新站数据时使用 `ECS verified data replacement` 手动工作流；它不访问或修改旧服务器。

1. 从旧库的一致性只读快照导入独立 PostgreSQL 库，使用 `migrate_mysql_to_postgres.py --execute --preserve-source-data`。此模式按版本 7 建立 Flyway 基线，保留源库菜单及业务数据，不执行 V4–V7 的历史数据修复；后续新迁移仍正常执行。
2. 用 `verify_mysql_postgres.py --report <校验报告> --fingerprints <内容指纹 JSON>` 核对全部字段，导出 PostgreSQL 自定义格式 dump 和 SHA-256。用 `stage_ecs_import.py --secret-name ERP_REIMPORT --fingerprints <内容指纹 JSON>` 加密暂存。
3. 手动执行工作流 `backup`、`stage`，查看备份和全部内容校验结果。`stage` 在独立临时数据库恢复，线上数据保持原样。
4. 执行 `activate`：短暂停止新站 ERP 后端，再次备份数据库及专属 Redis；旧库改名保留，已校验临时库接替原库名，清空专属 Redis 缓存并启动原后端。健康检查失败自动恢复旧库和缓存，不删除任何数据库。
5. 核对菜单、角色和业务数据后，用暂存记录执行 `stage_ecs_import.py --state <记录> --delete`，删除临时 OSS 对象及 ERP_REIMPORT Secret。

所有数据库备份、替换状态及回滚库名保存在服务器 `.deploy-backups/`；旧库以 `pcba_erp_before_` 开头。此操作只支持本机共享模式的专用 `pcba_erp`，不会操作同机其它数据库或 Redis。旧服务器后续写入不会自动同步到新站。

## 角色、部门状态约定

用户、角色、部门统一使用 `1=正常、0=停用`；菜单保留自身的 `0=正常、1=停用` 约定。
V8 仅对 Flyway 版本 7、描述为 `Verified MySQL import` 的旧站导入库反转角色和部门的历史状态，保留原来的启停含义；新安装的参考数据不反转。发布前自动备份数据库，角色绑定、菜单授权和部门关系保持不变。
该转换由 Flyway 仅执行一次；若回退到 V8 以前的应用，需要同时恢复发布前的数据库备份，单独回退镜像会造成状态含义再次不一致。
角色状态变更提交后只失效该角色用户的登录会话，用户重新登录后获取当前权限，避免停用角色的缓存权限继续生效。按状态筛选部门时，父节点未匹配的部门仍作为结果根节点显示。

## admin 账号恢复

`ECS verified data replacement` 的 `inspect-admin` 只输出账号状态、更新时间和密码格式，不输出密码或散列。`reset-admin` 从临时 production Secret `ERP_ADMIN_PASSWORD_HASH` 读取 BCrypt 散列，拒绝明文，先保存权限为 600 的账号备份，再更新唯一的 `user_id=1 / user_name=admin`、启用账号并删除该账号的 Redis 会话；不重置角色或业务数据。散列经 SSH 标准输入传输，SQL 经 psql 标准输入执行。实际登录验证通过后删除临时 Secret。

若 CI 失败，在 Test reports 下载 JUnit XML；前端构建错误在 Frontend production build。
SSH 失败核对账号、公钥、主机指纹和安全组；拉取镜像失败核对 GHCR 权限和 ECS 网络出口。
健康检查失败在 ECS 使用 Compose logs 查看原因，日志输出不要包含 `.env` 或完整 Compose 展开配置。

实现依据：[GitHub 镜像发布](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)、[GHCR 权限](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)、[部署环境](https://docs.github.com/en/actions/concepts/workflows-and-actions/deployment-environments)。

共享部署依据：[Compose 合并与覆盖](https://docs.docker.com/reference/compose-file/merge/)、[PostgreSQL 密码认证](https://www.postgresql.org/docs/17/auth-password.html)、[Certbot webroot](https://eff-certbot.readthedocs.io/en/stable/using.html)。
