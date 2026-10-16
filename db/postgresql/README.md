# PostgreSQL 迁移

正式迁移在 `backend/pcba-erp-admin/src/main/resources/db/migration/`，随 JAR 发布。

- V1：96 张业务/系统表的结构基线，不装载演示业务数据。
- V2：部门、角色、菜单、授权、物料分类，不包含用户密码。
- V3：保留现有 MySQL 数据库中额外的供应商、产品及 BOM 版本字段。
- 新变更新增 V4 等版本；不修改已发布文件。
- Flyway 校验摘要并锁定迁移；禁用 clean 和自动 baseline。
- 旧 db/init、db/patch 已归档到本机 `backup/legacy-cleanup-20261015/db/`，部署不会执行这些 MySQL 历史材料。

库存唯一键为仓库+物料+批次，空批次统一为空字符串。
增量通过 ON CONFLICT DO UPDATE，扣减带可用量条件；数据库约束禁止负库存及台账不一致。
现有 MySQL 数据迁移只接受新空目标库，源只读，校验行数并恢复 identity 序列后使用 Flyway 建立版本 3 导入基线。
完整步骤见根目录 README。
