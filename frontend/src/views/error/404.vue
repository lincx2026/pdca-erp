<template>
  <div class="error">
    <el-result status="404" title="404" sub-title="抱歉, 您访问的页面不存在">
      <template #extra>
        <el-button type="primary" @click="$router.replace('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>
<style scoped>.error{display:flex;align-items:center;justify-content:center;height:100%;}</style>
