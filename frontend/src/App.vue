<template>
  <router-view />
</template>

<script setup lang="ts">
// 根组件
</script>

<style lang="scss">
/* 全局 Element Plus 品牌色微调 */
.el-button--primary {
  --el-button-bg-color: #{$primary-color};
  --el-button-border-color: #{$primary-color};
  --el-button-hover-bg-color: #{$primary-light};
  --el-button-hover-border-color: #{$primary-light};
}
.el-menu--dark {
  --el-menu-bg-color: #{$bg-aside};
  --el-menu-dark-bg-color: #{$bg-aside};
}
</style>
