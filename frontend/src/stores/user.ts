import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type UserInfoResp, type LoginData } from '@/api'
import type { SysMenu, SysUser } from '@/types/models'

export { type LoginForm, type UserInfo, type MenuItem }

type LoginForm = LoginData
interface UserInfo {
  userId: number
  userName: string
  nickName: string
  avatar?: string
  email?: string
  phonenumber?: string
  deptId?: number
  deptName?: string
  roles: string[]
  permissions: string[]
  menus: SysMenu[]
}
export type MenuItem = SysMenu

const STORE_KEY = 'pcba-user-store'
// 仅允许持久化的白名单字段 (防御旧版本把 userInfo/menus 写进来)
const PERSIST_ALLOWED = ['token']

/**
 * 清洗本地缓存: 仅保留白名单字段，任何其他字段（如 userInfo / menus）一律丢弃。
 * 这是为了兼容早期 persist 未设置 pick: ['token'] 时写入的脏数据，
 * 否则 menus.length=2 会被当成 "已有用户信息"，跳过 fetchUserInfo，导致看不到新增菜单。
 */
function sanitizePersistedCache() {
  try {
    const raw = localStorage.getItem(STORE_KEY)
    if (!raw) return
    const parsed = JSON.parse(raw) as Record<string, any>
    if (!parsed || typeof parsed !== 'object') {
      localStorage.removeItem(STORE_KEY)
      return
    }
    const cleaned: Record<string, any> = {}
    for (const k of PERSIST_ALLOWED) if (k in parsed) cleaned[k] = parsed[k]
    localStorage.setItem(STORE_KEY, JSON.stringify(cleaned))
  } catch {
    // JSON 解析失败直接丢弃
    try { localStorage.removeItem(STORE_KEY) } catch {}
  }
}
// store 加载前先清一次（同步执行，确保 beforeRestore 之前就干净）
sanitizePersistedCache()

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)
  const menus = ref<SysMenu[]>([])

  const isLogin = computed(() => !!token.value)

  /** 真实后端登录 POST /api/auth/login */
  async function login(form: LoginData) {
    // authApi.login 由 request.ts 解包 R<T>.data → 此处是 {token:string}（而非原始 R 包装）
    const resp = await authApi.login(form) as any
    const t: string = (resp && typeof resp === 'object') ? (resp.token || '') : (typeof resp === 'string' ? resp : '')
    if (!t) throw new Error('登录响应缺少 token')
    token.value = t
    // 登录成功但先不补 userInfo，让路由守卫统一走 fetchUserInfo 流程取最新菜单
    userInfo.value = null
    menus.value = []
    return { code: 200, msg: '登录成功', data: null }
  }

  /** 调后端 GET /api/auth/get-info 取 当前用户 + 角色/权限/菜单 */
  async function fetchUserInfo(): Promise<UserInfo> {
    const data = await authApi.getInfo() as any as UserInfoResp
    const u: SysUser = data.user as any
    const rawMenus: SysMenu[] = Array.isArray((data as any).menus) ? (data as any).menus : []
    menus.value = rawMenus
    const out: UserInfo = {
      userId: u.userId,
      userName: u.userName,
      nickName: u.nickName,
      avatar: u.avatar,
      email: u.email,
      phonenumber: u.phonenumber,
      deptId: u.deptId,
      deptName: (u as any).dept?.deptName,
      roles: Array.isArray(data.roles) ? data.roles : [],
      permissions: Array.isArray(data.permissions) ? data.permissions : [],
      menus: rawMenus
    }
    userInfo.value = out
    return out
  }

  function reset() {
    token.value = ''
    userInfo.value = null
    menus.value = []
    try { localStorage.removeItem(STORE_KEY) } catch {}
  }

  async function logout() {
    try { await authApi.logout() } catch {}
    reset()
  }

  return {
    token, userInfo, menus, isLogin,
    login, fetchUserInfo, reset, logout
  }
}, {
  persist: {
    key: STORE_KEY,
    pick: ['token'],
    /** 反序列化前再清洗一道，兼容更早启动但 pick 不生效的场景 */
    beforeRestore: (ctx) => { sanitizePersistedCache() },
    /** 序列化前也再按 pick 白名单缩一次 */
    serializer: {
      serialize: (val: Record<string, any>) => {
        const cleaned: Record<string, any> = {}
        for (const k of PERSIST_ALLOWED) if (k in val) cleaned[k] = val[k]
        return JSON.stringify(cleaned)
      },
      deserialize: (str: string) => {
        try {
          const parsed = JSON.parse(str)
          if (!parsed || typeof parsed !== 'object') return {}
          const cleaned: Record<string, any> = {}
          for (const k of PERSIST_ALLOWED) if (k in parsed) cleaned[k] = parsed[k]
          return cleaned
        } catch { return {} }
      }
    }
  }
})
