import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import type { R } from '@/types/api'

/**
 * 通用 axios 封装:
 * - BaseURL: import.meta.env.VITE_API_BASE → 走 Vite dev proxy: /api
 * - Request Interceptor: 加 Authorization: Bearer <token>
 * - Response Interceptor: 统一按 R<T> 格式解包(code=200 才 resolve data, 否则 401 登出/ 403 提示/ 5xx 报错)
 */

// ============ 类型 (避免循环依赖, 放到 types/api.d.ts) ============
const BASE_URL = import.meta.env.VITE_API_BASE as string || '/api'
const TIMEOUT = 15000

const service: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// 避免重复弹 "登录已过期"
let isReloginShown = false

// ============ 请求拦截 ============
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    // 只要 pinia 中有 token，就把 Authorization: Bearer <token> 带上。
    // 注意：早期代码曾经加过 "在 /login 页面就不带 token" 的保护，
    // 但它会导致"登录成功后在跳页之前的同步 fetchUserInfo 请求"因 onLoginPage=true 被误判，
    // 从而不带 header 导致 401、登录流程失败；登录/验证码白名单路径不受 header 影响。
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ============ 响应拦截 ============
service.interceptors.response.use(
  async (resp) => {
    // Blob/二进制响应: 保留完整 response 对象 (含 headers), 供调用方 saveBlob 读取 Content-Disposition 文件名
    if (resp.config?.responseType === 'blob' || resp.data instanceof Blob) {
      return resp as any
    }
    const res = resp.data as R<any>
    // 非标准响应(例如下载 Excel Blob), 直接透传
    if (res == null || typeof res !== 'object' || typeof res.code !== 'number') return resp.data as any

    // code=200 时: 区分 R<T>(含 data 字段) 与 TableDataInfo(含 total/rows 平结构)
    if (res.code === 200) {
      if ('data' in res) return (res as any).data as any
      return res as any
    }

    // 业务错误
    const msg = res.msg || '请求失败'

    // 401 未登录/过期
    if (res.code === 401) {
      const onLoginPage = router.currentRoute.value.path === '/login'
      // 登录页不弹过期提示（直接交给登录失败提示），避免覆盖登录表单
      if (onLoginPage) {
        return Promise.reject(new Error(msg))
      }
      if (!isReloginShown) {
        isReloginShown = true
        try {
          await ElMessageBox.confirm('登录状态已过期, 您可以继续留在此页面或重新登录', '系统提示', {
            confirmButtonText: '重新登录',
            cancelButtonText: '取消',
            type: 'warning'
          })
          const userStore = useUserStore()
          userStore.reset()
          isReloginShown = false
          router.replace('/login?redirect=' + encodeURIComponent(router.currentRoute.value.fullPath))
        } catch { isReloginShown = false }
      }
      return Promise.reject(new Error(msg))
    }

    // 403 权限不足
    if (res.code === 403) {
      ElMessage.error('❌ 权限不足: ' + msg)
      return Promise.reject(new Error(msg))
    }

    // 404/500 等
    if (res.code >= 400) {
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }

    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error: AxiosError<R<any>>) => {
    // 网络层错误: 401/403/断网
    let msg = error.message || '请求失败'
    const status = error.response?.status
    const respBody = error.response?.data as R<any> | undefined
    if (respBody?.msg) msg = respBody.msg

    if (status === 401 || (respBody && respBody.code === 401)) {
      const onLoginPage = router.currentRoute.value.path === '/login'
      if (!onLoginPage && !isReloginShown) {
        isReloginShown = true
        ;(async () => {
          try {
            await ElMessageBox.confirm('登录状态已过期,请重新登录', '系统提示', { confirmButtonText: '重新登录', cancelButtonText: '取消', type: 'warning' })
            useUserStore().reset()
            isReloginShown = false
            router.replace('/login')
          } catch { isReloginShown = false }
        })()
      }
    } else if (status === 403) {
      ElMessage.error('❌ 权限不足')
    } else if (!window.navigator.onLine) {
      ElMessage.error('网络连接异常,请检查')
    } else if (status && status >= 500) {
      ElMessage.error('服务器异常 (' + status + '): ' + msg)
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(new Error(msg))
  }
)

// 封装常用方法, 返回 Promise<T> (已经把 R<T>.data 解包出来)
const request = {
  get:    <T = any>(url: string, params?: any, config?: AxiosRequestConfig) => service.get<any, T>(url, { params, ...config }),
  post:   <T = any>(url: string, data?: any, config?: AxiosRequestConfig) => service.post<any, T>(url, data, config),
  put:    <T = any>(url: string, data?: any, config?: AxiosRequestConfig) => service.put<any, T>(url, data, config),
  delete: <T = any>(url: string, params?: any, config?: AxiosRequestConfig) => service.delete<any, T>(url, { params, ...config }),
  /** 原始 axios 实例 (下载文件/自定义) */
  instance: service
}
export default request
export { service as axiosRaw }
