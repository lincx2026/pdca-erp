/** 后端 R<T> 响应结构 */
export interface R<T> {
  code: number
  msg: string
  data?: T
}

/** 分页列表响应 */
export interface Page<T> {
  total: number
  rows: T[]
  code?: number
  msg?: string
}

/** 后端 TableDataInfo (SysUser/Material 用) */
export interface TableDataInfo {
  total: number
  rows: any[]
  code: number
  msg: string
}
