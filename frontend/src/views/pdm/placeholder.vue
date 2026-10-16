<template>
  <div class="page">
    <el-result icon="info" title="🚧 页面开发中" :sub-title="'此模块(' + ($route.meta.title || '') + ')是阶段一任务, 将在后续迭代中按任务清单上线。'">
      <template #extra>
        <el-button type="primary" @click="$router.back()">返回上一页</el-button>
      </template>
    </el-result>
    <el-card shadow="never" class="tip-card">
      <h4>该模块对应的阶段一任务：</h4>
      <ul>
        <li>完成后端 Entity / Mapper / Service / Controller 代码</li>
        <li>完成前端列表页（筛选条件 + 表格 + 分页 + 导出）</li>
        <li>完成表单页（Tab 拆分、必填校验、附件上传）</li>
        <li>完成接口/功能测试 + 权限按钮控制</li>
        <li>完成 Excel 批量导入工具</li>
      </ul>
      <p>您可以参照 <code>阶段一-详细任务清单与交付物.md</code> 按周逐步推进。</p>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.page { max-width: 900px; margin: 0 auto; }
.tip-card { margin-top: 12px; line-height: 2; color: #374151; }
code { background: #eef2ff; padding: 2px 6px; border-radius: 4px; color: #1e3a8a; }
</style>
