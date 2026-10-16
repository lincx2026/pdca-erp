import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 全局 UI 状态 (侧边栏折叠/403 提示闪一下, 后续扩展多主题等) */
export const useLayoutStore = defineStore('layout', () => {
  const flashForbidden = ref<string>('')
  return { flashForbidden }
})
