import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'
import { hasPerm } from '@/utils/permission'

/**
 * 按钮级权限指令 v-hasPermi="'system:user:add'" 或 v-hasPermi="['system:user:add','system:user:edit']"
 * - 用户权限含 *:*:* 视为超管全部放行 (在 hasPerm 中处理)
 * - 没有权限 → 直接 remove 节点
 */
const hasPermi: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const perms = binding.value
    if (!perms) return
    const store = useUserStore()
    if (!store.userInfo) {
      el.parentNode?.removeChild(el); return
    }
    const all = store.userInfo.permissions || []
    if (!hasPerm(perms, all)) el.parentNode?.removeChild(el)
  }
}
export default hasPermi
