/**
 * 权限匹配工具：供路由 beforeEach / v-hasPermi / v-hasRole 共享使用
 * - 支持超管 *:*:* 一键放行
 * - 支持 Pinia Proxy(Array) → 先 toRaw / 展开为普通数组
 * - 支持通配符: 形如 pdm:* 可匹配任意具体项 (RuoYi 风格)
 */

const WILDCARD_SUPER_A = '*'
const WILDCARD_SUPER_B = '*:*:*'

function toPlainStrings(value: unknown): string[] {
  if (value == null) return []
  if (Array.isArray(value)) return value.map(String).filter(Boolean)
  // Pinia Proxy / Set 兼容
  try {
    const arr = Array.from(value as any)
    return arr.map(String).filter(Boolean)
  } catch {
    return []
  }
}

/**
 * 单个权限字符串是否命中用户所有权限
 */
export function matchPerm(need: string, allPerms: string[]): boolean {
  if (!need) return true
  if (allPerms.includes(WILDCARD_SUPER_A)) return true
  if (allPerms.includes(WILDCARD_SUPER_B)) return true
  if (allPerms.includes(need)) return true
  // 模块级通配符: user 有 "system:*" 即可匹配 "system:user:list"
  const parts = need.split(':')
  for (let i = parts.length; i > 0; i--) {
    const prefix = parts.slice(0, i).join(':') + ':*'
    if (allPerms.includes(prefix)) return true
  }
  return false
}

/**
 * 路由/按钮级权限检查：需要任意一个权限命中即可放行
 */
export function hasPerm(need: string | string[] | undefined | null, rawAll: unknown): boolean {
  if (need == null) return true
  const req = Array.isArray(need) ? need : [need]
  if (req.length === 0) return true
  const all = toPlainStrings(rawAll)
  return req.some(p => matchPerm(p, all))
}

/**
 * 角色权限检查：含任一角色即匹配
 */
export function hasRole(need: string | string[] | undefined | null, rawAll: unknown): boolean {
  if (need == null) return true
  const req = Array.isArray(need) ? need : [need]
  if (req.length === 0) return true
  const all = toPlainStrings(rawAll)
  if (all.includes('admin')) return true
  return req.some(r => all.includes(r))
}
