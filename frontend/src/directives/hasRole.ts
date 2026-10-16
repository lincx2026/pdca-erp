import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'
import { hasRole as hasRoleMatch } from '@/utils/permission'

/** v-hasRole="'admin'" 或 v-hasRole="['pdm:admin','common']"（使用 role_key，兼容中文名） */
const hasRole: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const roles = binding.value
    if (!roles) return
    const s = useUserStore()
    if (!s.userInfo) { el.parentNode?.removeChild(el); return }
    const my = s.userInfo.roles || []
    if (!hasRoleMatch(roles, my)) el.parentNode?.removeChild(el)
  }
}
export default hasRole
