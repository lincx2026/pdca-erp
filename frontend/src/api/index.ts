import request from '@/utils/request'
import type { Page, SysUser, SysRole, SysMenu, SysDept, SysOperLog, PdmMaterialCategory, PdmMaterial, PdmMaterialSupplier, PdmMaterialAlternate, PdmProcessRoute, PdmProcessStep, TableDataInfo,
  PmmWorkOrder, PmmWorkOrderBom, PmmProcessTask, PmmWorkReport, PmmQualityDefect, PmmSchedulePlan, PmmKanbanCard, PmmDefectCode, PmProject, PmProjectAttachment, PmProjectCategory, HrmEmployee, HrmEmployeeAttachment,
  ScmPurchaseRequisition, ScmPurchaseOrder, ScmDeliveryNote, ScmPurchaseReturn, ScmSupplierPrice, ScmSupplier, ScmPurchaseRequisitionLine, ScmPurchaseOrderLine,
  ScmApPayable, ScmApPayableLine, ScmApPaymentPlan, ScmApPayment, FinVoucherInterface,
  SalCustomer, SalSalesOrder, SalSalesOrderLine, SalSalesReturn, SalSalesReturnLine, SalKitting, SalKittingLine,
  SalArReceivable, SalArReceivableLine, SalArReceiptPlan, SalArReceipt,
  AsmKitting, AsmKittingLine, AsmKittingPick, AsmSn, AsmSnNode, AsmFeederLoad,
  PmmEquipment, PmmEquipmentLog,
  SalInvoice, SalInvoiceLine, InvoiceableReceivable,
  FasOrder, FasBom, FasStep, FasRecord, FasProductStock,
  CompanyProfile, CompanyFactory, CompanyCodingRule, CompanyParam,
  SysNotice, SysNoticeRead, NoticeCountResp, NoticeListQ, NoticeUserListQ } from '@/types/models'

// ============ 认证接口 ============
export interface LoginData { username: string; password: string; captcha?: string; uuid?: string }
export interface LoginResp { token: string }
export interface UserInfoResp {
  user: SysUser
  roles: string[]
  permissions: string[]
  menus: SysMenu[]
  dataScope: number
}

export const authApi = {
  login:    (d: LoginData)     => request.post<LoginResp>('/auth/login', d),
  getInfo:  ()                 => request.get<UserInfoResp>('/auth/get-info'),
  logout:   ()                 => request.post<void>('/auth/logout'),
  captcha:  ()                 => request.get<{uuid:string; code:string; img:string}>('/auth/captcha'),
  updateProfile: (d: { nickName?: string; email?: string; phonenumber?: string }) => request.put<void>('/auth/profile', d),
  updatePwd:     (d: { oldPassword: string; newPassword: string }) => request.put<void>('/auth/pwd', d)
}

// ============ 通用 list 分页参数 ============
export interface PageQ { pageNum: number; pageSize: number }

// ============ 部门 ============
export const deptApi = {
  list:        (p: {deptName?: string; status?: number})     => request.get<SysDept[]>('/system/dept/list', p),
  get:         (id: number)                                   => request.get<SysDept>('/system/dept/' + id),
  excludeTree: (excludeId?: number)                           => request.get<SysDept[]>('/system/dept/exclude-tree', { excludeId }),
  add:         (d: Partial<SysDept>)                          => request.post<number>('/system/dept', d),
  edit:        (d: Partial<SysDept>)                          => request.put<number>('/system/dept', d),
  remove:      (id: number)                                   => request.delete<number>('/system/dept/' + id)
}

// ============ 用户 ============
export interface UserListQ extends PageQ { deptId?: number; userName?: string; status?: number; phonenumber?: string; beginTime?: string; endTime?: string }
export const userApi = {
  list:         (p: UserListQ)      => request.get<Page<SysUser>>('/system/user/list', p),
  get:          (id: number)        => request.get<{data: SysUser; roles: SysRole[]; roleIds: number[]}>('/system/user/' + id),
  add:          (d: Partial<SysUser>) => request.post<number>('/system/user', d),
  edit:         (d: Partial<SysUser>) => request.put<number>('/system/user', d),
  remove:       (ids: number[])     => request.delete<number>('/system/user/' + ids.join(',')),
  resetPwd:     (p: {userId:number; password:string}) => request.put<number>('/system/user/reset-pwd', p),
  changeStatus: (p: {userId:number; status:number})   => request.put<number>('/system/user/change-status', p),
  options:      ()                  => request.get<any[]>('/system/user/optionselect')
}

// ============ 角色 ============
export interface RoleListQ extends PageQ { roleName?: string; roleKey?: string; status?: number; beginTime?: string; endTime?: string }
export const roleApi = {
  list:        (p: RoleListQ)     => request.get<Page<SysRole>>('/system/role/list', p),
  options:     ()                 => request.get<SysRole[]>('/system/role/optionselect'),
  get:         (id: number)       => request.get<SysRole & {menus?: SysMenu[]; depts?: SysDept[]; menuIds?: number[]; deptIds?: number[]}>('/system/role/' + id),
  add:         (d: Partial<SysRole>) => request.post<number>('/system/role', d),
  edit:        (d: Partial<SysRole>) => request.put<number>('/system/role', d),
  remove:      (ids: number[])    => request.delete<number>('/system/role/' + ids.join(',')),
  authMenu:    (p: {roleId: number; menuIds: number[]}) => request.put<number>('/system/role/auth-menu', p),
  deptTree:    (roleId: number)   => request.get<SysDept[]>('/system/role/dept-tree/' + roleId)
}

// ============ 菜单 ============
export const menuApi = {
  list:          (p: {menuName?:string; status?: number}) => request.get<SysMenu[]>('/system/menu/list', p),
  treeSelect:    ()                      => request.get<SysMenu[]>('/system/menu/tree-select'),
  excludeTree:   (excludeId?: number)    => request.get<SysMenu[]>('/system/menu/exclude-tree', { excludeId }),
  roleMenuTree:  (roleId: number)        => request.get<{menus: SysMenu[]; checkedKeys: number[]}>('/system/menu/role-menu-tree/' + roleId),
  get:           (id: number)            => request.get<SysMenu>('/system/menu/' + id),
  add:           (d: Partial<SysMenu>)   => request.post<number>('/system/menu', d),
  edit:          (d: Partial<SysMenu>)   => request.put<number>('/system/menu', d),
  remove:        (id: number)            => request.delete<number>('/system/menu/' + id)
}

// ============ 操作日志 ============
export interface OperlogQ extends PageQ { title?: string; businessType?: number; status?: number; operName?: string; beginTime?: string; endTime?: string }
export const operlogApi = {
  list:   (p: OperlogQ)     => request.get<Page<SysOperLog>>('/monitor/operlog/list', p),
  get:    (id: number)      => request.get<SysOperLog>('/monitor/operlog/' + id),
  remove: (ids: number[])   => request.delete<number>('/monitor/operlog/' + ids.join(',')),
  clean:  ()                => request.delete<void>('/monitor/operlog/clean')
}

// 冒烟测试
export const systemApi = {
  hello: () => request.get<any>('/system/hello'),
  ping:  () => request.get<string>('/system/ping')
}

// ============ 物料分类 ============
export interface CategoryListQ { categoryName?: string; status?: number; withMaterialCount?: boolean }
export const categoryApi = {
  list:        (p: CategoryListQ)    => request.get<PdmMaterialCategory[]>('/pdm/category/list', p),
  flat:        (p: CategoryListQ)    => request.get<PdmMaterialCategory[]>('/pdm/category/flat', p),
  get:         (id: number)          => request.get<PdmMaterialCategory>('/pdm/category/' + id),
  excludeTree: (excludeId?: number)  => request.get<PdmMaterialCategory[]>('/pdm/category/exclude-tree', { excludeId }),
  add:         (d: Partial<PdmMaterialCategory>) => request.post<number>('/pdm/category', d),
  edit:        (d: Partial<PdmMaterialCategory>) => request.put<number>('/pdm/category', d),
  remove:      (id: number)          => request.delete<number>('/pdm/category/' + id)
}

// ============ 物料编码生成器 ============
export const codeGenApi = {
  generate: (categoryId: number, count = 1) => request.get<string[]>('/pdm/code/generate', { categoryId, count }),
  preview:  (categoryId: number)             => request.get<string>('/pdm/code/preview', { categoryId })
}

// ============ W3.3 供应商阶梯价 ============
export const supplierApi = {
  list:       (materialId: number, supplierId?: number, status?: number) =>
    request.get<PdmMaterialSupplier[]>('/pdm/material-supplier/list', { materialId, supplierId, status }),
  add:        (d: Partial<PdmMaterialSupplier>) => request.post<number>('/pdm/material-supplier', d),
  edit:       (d: Partial<PdmMaterialSupplier>) => request.put<number>('/pdm/material-supplier', d),
  remove:     (id: number) => request.delete<number>('/pdm/material-supplier/' + id),
  replaceAll: (materialId: number, list: PdmMaterialSupplier[]) =>
    request.put<number>('/pdm/material-supplier/replace-all/' + materialId, list)
}

// ============ W3.3 替代料 ============
export const alternateApi = {
  list: (p: { mainMaterialId?: number; altMaterialId?: number; groupId?: string; status?: number }) =>
    request.get<PdmMaterialAlternate[]>('/pdm/material-alternate/list', p),
  nextGroupId: () => request.get<string>('/pdm/material-alternate/next-group-id'),
  add:         (d: Partial<PdmMaterialAlternate>) => request.post<number>('/pdm/material-alternate', d),
  edit:        (d: Partial<PdmMaterialAlternate>) => request.put<number>('/pdm/material-alternate', d),
  remove:      (id: number) => request.delete<number>('/pdm/material-alternate/' + id),
  replaceAll:  (mainMaterialId: number, list: PdmMaterialAlternate[]) =>
    request.put<number>('/pdm/material-alternate/replace-all/' + mainMaterialId, list),
  submitAudit: (id: number) => request.put<number>('/pdm/material-alternate/submit-audit/' + id),
  auditPass:   (id: number) => request.put<number>('/pdm/material-alternate/audit-pass/' + id),
  auditReject: (id: number, rejectReason?: string) =>
    request.put<number>('/pdm/material-alternate/audit-reject/' + id, undefined, { params: { rejectReason } }),
  disable:     (id: number) => request.put<number>('/pdm/material-alternate/disable/' + id)
}

// ============ W3.4 BOM 管理 ============
export interface BomListQ extends PageQ {
  bomCode?: string
  bomName?: string
  productMaterialCode?: string
  productMaterialId?: number
  categoryId?: number
  bomType?: number
  status?: number
  version?: string
}
export const bomApi = {
  list: (p: BomListQ) => request.get<{ rows: PdmBom[]; total: number }>('/pdm/bom/list', p) as any,
  get:  (id: number) => request.get<PdmBom>('/pdm/bom/' + id),
  nextCode: () => request.get<string>('/pdm/bom/next-code'),
  add:  (d: Partial<PdmBom>) => request.post<number>('/pdm/bom', d),
  edit: (d: Partial<PdmBom>) => request.put<number>('/pdm/bom', d),
  remove: (id: number) => request.delete<number>('/pdm/bom/' + id),
  replaceLines: (bomId: number, lines: PdmBomLine[]) =>
    request.put<number>('/pdm/bom/replace-lines/' + bomId, lines),
  // 状态机
  submitAudit: (id: number) => request.put<number>('/pdm/bom/submit-audit/' + id),
  auditPass:   (id: number) => request.put<number>('/pdm/bom/audit-pass/' + id),
  auditReject: (id: number, rejectReason?: string) =>
    request.put<number>('/pdm/bom/audit-reject/' + id, undefined, { params: { rejectReason } }),
  disable: (id: number) => request.put<number>('/pdm/bom/disable/' + id),
  archive: (id: number) => request.put<number>('/pdm/bom/archive/' + id),
  // 展开
  linesSingle: (bomId: number) => request.get<PdmBomLine[]>('/pdm/bom/lines/single/' + bomId),
  explodeTree: (bomId: number, levelMax = 10, phantomExpand = true) =>
    request.get<PdmBomLine[]>('/pdm/bom/explode/tree/' + bomId, { levelMax, phantomExpand }),
  explodeFlat: (bomId: number, levelMax = 10, phantomExpand = true) =>
    request.get<Record<number, PdmBomLine>>('/pdm/bom/explode/flat/' + bomId, { levelMax, phantomExpand }),
  // Where-Used
  whereUsedSingle: (componentId: number, lineStatus?: number, bomStatus?: number) =>
    request.get<PdmBomLine[]>('/pdm/bom/where-used/single', { componentId, lineStatus, bomStatus }),
  whereUsedRecursive: (componentId: number, lineStatus?: number, bomStatus?: number, levelMax = 10) =>
    request.get<any[]>('/pdm/bom/where-used/recursive', { componentId, lineStatus, bomStatus, levelMax }),
  // 替换子件
  replaceComponent: (bomId: number, oldComponentId: number, newComponentId: number,
                     keepAltGroup = true, remark?: string) =>
    request.put<number>('/pdm/bom/replace-component/' + bomId,
        undefined, { params: { oldComponentId, newComponentId, keepAltGroup, remark } }),
  // 导入
  downloadBomTemplate: () => request.instance.get('/pdm/bom/import/template', { responseType: 'blob' }) as Promise<any>,
  previewBomImport: (file: File, productMaterialId: number, bomName?: string, version?: string) => {
    const fd = new FormData()
    fd.append('file', file)
    const qs = new URLSearchParams()
    qs.append('productMaterialId', String(productMaterialId))
    if (bomName) qs.append('bomName', bomName)
    if (version) qs.append('version', version)
    return request.instance.post('/pdm/bom/import/preview?' + qs.toString(), fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  confirmBomImport: (previewData: any) => request.post<number>('/pdm/bom/import/confirm', previewData)
}

// ============ 物料主档 ============
export interface MaterialListQ extends PageQ {
  categoryId?: number
  materialCode?: string
  materialName?: string
  materialType?: string
  mpn?: string
  status?: number
}
export const materialApi = {
  list:         (p: MaterialListQ)    => request.get<{ rows: PdmMaterial[]; total: number }>('/pdm/material/list', p) as any,
  get:          (id: number)          => request.get<PdmMaterial>('/pdm/material/' + id),
  add:          (d: Partial<PdmMaterial>) => request.post<number>('/pdm/material', d),
  edit:         (d: Partial<PdmMaterial>) => request.put<number>('/pdm/material', d),
  remove:       (id: number)          => request.delete<number>('/pdm/material/' + id),
  submitAudit:  (id: number)          => request.put<number>('/pdm/material/submit-audit/' + id),
  auditPass:    (id: number)          => request.put<number>('/pdm/material/audit-pass/' + id),
  auditReject:  (id: number, rejectReason?: string) => request.put<number>('/pdm/material/audit-reject/' + id, undefined, { params: { rejectReason } }),
  disable:      (id: number)          => request.put<number>('/pdm/material/disable/' + id),
  /* ============ W3.2 导入导出 ============ */
  downloadTemplate: () => request.instance.get('/pdm/material/import/template', {
    responseType: 'blob'
  }) as Promise<any>,
  export: (p: MaterialListQ) => request.instance.get('/pdm/material/export', {
    params: p,
    responseType: 'blob'
  }) as Promise<any>,
  import: (file: File, updateSupport = false, onUploadProgress?: (progress: number) => void) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('updateSupport', updateSupport ? 'true' : 'false')
    return request.instance.post<{ code: number; msg: string; data: MaterialImportResult }>('/pdm/material/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (evt: any) => {
        if (onUploadProgress && evt.total) onUploadProgress(Math.round((evt.loaded / evt.total) * 100))
      }
    })
  }
}

/** 导入结果 DTO (对应后端 MaterialImportResult) */
export interface MaterialImportResult {
  totalRows: number
  successCount: number
  failCount: number
  failRows: Array<{
    rowNum: number
    errorMsg: string
    materialCode?: string | null
    materialName?: string | null
    categoryName?: string | null
    specification?: string | null
  }>
}

// ============ 工艺路线 ============
export interface ProcessRouteListQ extends PageQ {
  routeCode?: string
  routeName?: string
  productId?: number
  productCode?: string
  status?: number
}
export const processRouteApi = {
  list:    (p: ProcessRouteListQ) => request.get<{ rows: PdmProcessRoute[]; total: number }>('/pdm/process-route/list', p) as any,
  get:     (id: number) => request.get<PdmProcessRoute>('/pdm/process-route/' + id),
  nextCode:() => request.get<string>('/pdm/process-route/next-code'),
  add:     (d: Partial<PdmProcessRoute>) => request.post<number>('/pdm/process-route', d),
  edit:    (d: Partial<PdmProcessRoute>) => request.put<number>('/pdm/process-route', d),
  remove:  (id: number) => request.delete<number>('/pdm/process-route/' + id),
  replaceSteps: (routeId: number, steps: PdmProcessStep[]) =>
    request.put<number>('/pdm/process-route/replace-steps/' + routeId, steps),
  setDefault: (id: number) => request.put<number>('/pdm/process-route/set-default/' + id),
  enable:     (id: number) => request.put<number>('/pdm/process-route/enable/' + id),
  disable:    (id: number) => request.put<number>('/pdm/process-route/disable/' + id),
  copy:       (sourceId: number, newRouteName?: string) =>
    request.post<number>('/pdm/process-route/copy/' + sourceId, undefined, { params: { newRouteName } }),
  getDefault: (productId: number) => request.get<PdmProcessRoute>('/pdm/process-route/default', { productId })
}

// ============ 客户料号映射 ============
export interface CustMapListQ extends PageQ {
  customerId?: number
  customerName?: string
  materialId?: number
  customerMaterialNo?: string
  materialCode?: string
  status?: number
}
export const custMapApi = {
  list:     (p: CustMapListQ) => request.get<{ rows: PdmCustomerMaterialMap[]; total: number }>('/pdm/custmap/list', p) as any,
  customerOptions: () => request.get<{ customerId: number; customerCode: string; customerName: string }[]>('/pdm/custmap/customer-options') as any,
  materialOptions: (keyword?: string) => request.get<{ materialId: number; materialCode: string; materialName: string; specification?: string }[]>('/pdm/custmap/material-options', keyword ? { keyword } : {}) as any,
  get:      (id: number) => request.get<PdmCustomerMaterialMap>('/pdm/custmap/' + id),
  reverseLookup: (customerMaterialNo: string) =>
    request.get<PdmCustomerMaterialMap>('/pdm/custmap/reverse-lookup', { customerMaterialNo }),
  add:      (d: Partial<PdmCustomerMaterialMap>) => request.post<number>('/pdm/custmap', d),
  edit:     (d: Partial<PdmCustomerMaterialMap>) => request.put<number>('/pdm/custmap', d),
  remove:   (id: number) => request.delete<number>('/pdm/custmap/' + id),
  enable:   (id: number) => request.put<number>('/pdm/custmap/enable/' + id),
  disable:  (id: number) => request.put<number>('/pdm/custmap/disable/' + id),
  batchImport: (list: Partial<PdmCustomerMaterialMap>[], updateSupport = false) =>
    request.post<string>('/pdm/custmap/batch-import', list, { params: { updateSupport } })
}

// ============ WMS 仓储管理 ============

// 仓库主档
export interface WarehouseListQ extends PageQ {
  warehouseCode?: string
  warehouseName?: string
  warehouseType?: string
  status?: number
}
export const warehouseApi = {
  list:    (p: WarehouseListQ) => request.get<{ rows: WmsWarehouse[]; total: number }>('/wms/warehouse/list', p) as any,
  get:     (id: number) => request.get<WmsWarehouse>('/wms/warehouse/' + id),
  options: () => request.get<WmsWarehouse[]>('/wms/warehouse/options') as any,
  add:     (d: Partial<WmsWarehouse>) => request.post<number>('/wms/warehouse', d),
  edit:    (d: Partial<WmsWarehouse>) => request.put<number>('/wms/warehouse', d),
  remove:  (id: number) => request.delete<number>('/wms/warehouse/' + id),
  enable:  (id: number) => request.put<number>('/wms/warehouse/enable/' + id),
  disable: (id: number) => request.put<number>('/wms/warehouse/disable/' + id)
}

// 库存台账
export interface InventoryListQ extends PageQ {
  warehouseId?: number
  materialCode?: string
  materialName?: string
  status?: number
}
export const inventoryApi = {
  list: (p: InventoryListQ) => request.get<{ rows: WmsInventory[]; total: number }>('/wms/inventory/list', p) as any,
  get:  (id: number) => request.get<WmsInventory>('/wms/inventory/' + id)
}

// 入库单
export interface StockInListQ extends PageQ {
  orderNo?: string
  warehouseId?: number
  inType?: string
  status?: number
}
export const stockInApi = {
  list:    (p: StockInListQ) => request.get<{ rows: WmsStockIn[]; total: number }>('/wms/stock-in/list', p) as any,
  get:     (id: number) => request.get<WmsStockIn>('/wms/stock-in/' + id),
  nextNo:  () => request.get<string>('/wms/stock-in/next-no'),
  add:     (d: Partial<WmsStockIn>) => request.post<number>('/wms/stock-in', d),
  edit:    (d: Partial<WmsStockIn>) => request.put<number>('/wms/stock-in', d),
  remove:  (id: number) => request.delete<number>('/wms/stock-in/' + id),
  submit:  (id: number) => request.put<number>('/wms/stock-in/submit/' + id),
  audit:   (id: number) => request.put<number>('/wms/stock-in/audit/' + id),
  reject:  (id: number, reason: string) => request.put<number>('/wms/stock-in/reject/' + id, {}, { params: { reason } }),
  confirm: (id: number) => request.put<number>('/wms/stock-in/confirm/' + id)
}

// 出库单
export interface StockOutListQ extends PageQ {
  orderNo?: string
  warehouseId?: number
  outType?: string
  status?: number
}
export const stockOutApi = {
  list:    (p: StockOutListQ) => request.get<{ rows: WmsStockOut[]; total: number }>('/wms/stock-out/list', p) as any,
  get:     (id: number) => request.get<WmsStockOut>('/wms/stock-out/' + id),
  nextNo:  () => request.get<string>('/wms/stock-out/next-no'),
  add:     (d: Partial<WmsStockOut>) => request.post<number>('/wms/stock-out', d),
  edit:    (d: Partial<WmsStockOut>) => request.put<number>('/wms/stock-out', d),
  remove:  (id: number) => request.delete<number>('/wms/stock-out/' + id),
  submit:  (id: number) => request.put<number>('/wms/stock-out/submit/' + id),
  audit:   (id: number) => request.put<number>('/wms/stock-out/audit/' + id),
  reject:  (id: number, reason: string) => request.put<number>('/wms/stock-out/reject/' + id, {}, { params: { reason } }),
  confirm: (id: number) => request.put<number>('/wms/stock-out/confirm/' + id)
}

// ============ PMM 生产管理 ============

/** 生产工单 */
export interface WorkOrderListQ extends PageQ {
  orderNo?: string
  materialCode?: string
  materialName?: string
  sourceType?: string
  status?: number
  workCenter?: string
  saleOrderNo?: string
}
export const workOrderApi = {
  list:    (p: WorkOrderListQ) => request.get<{ rows: PmmWorkOrder[]; total: number }>('/pm/work-order/list', p) as any,
  get:     (id: number) => request.get<PmmWorkOrder>('/pm/work-order/' + id),
  options: () => request.get<PmmWorkOrder[]>('/pm/work-order/options') as any,
  add:     (d: Partial<PmmWorkOrder>) => request.post<number>('/pm/work-order', d),
  edit:    (d: Partial<PmmWorkOrder>) => request.put<number>('/pm/work-order', d),
  remove:  (id: number) => request.delete<number>('/pm/work-order/' + id),
  release: (id: number) => request.put<number>('/pm/work-order/release/' + id),
  finish:  (id: number) => request.put<number>('/pm/work-order/finish/' + id),
  close:   (id: number) => request.put<number>('/pm/work-order/close/' + id),
  cancel:  (id: number) => request.put<number>('/pm/work-order/cancel/' + id)
}

/** 工单BOM */
export interface WorkBomListQ extends PageQ {
  workOrderId?: number
  materialCode?: string
  materialName?: string
}
export const workBomApi = {
  list:          (p: WorkBomListQ) => request.get<{ rows: PmmWorkOrderBom[]; total: number }>('/pm/work-bom/list', p) as any,
  get:           (id: number) => request.get<PmmWorkOrderBom>('/pm/work-bom/' + id),
  add:           (d: Partial<PmmWorkOrderBom>) => request.post<number>('/pm/work-bom', d),
  edit:          (d: Partial<PmmWorkOrderBom>) => request.put<number>('/pm/work-bom', d),
  remove:        (id: number) => request.delete<number>('/pm/work-bom/' + id),
  listByWorkOrder: (workOrderId: number) => request.get<PmmWorkOrderBom[]>('/pm/work-bom/listByWorkOrder', { workOrderId }) as any
}

/** 工序任务 */
export interface ProcessTaskListQ extends PageQ {
  workOrderId?: number
  processName?: string
  workCenter?: string
  status?: number
  assignedUserId?: number
}
export const processTaskApi = {
  list:          (p: ProcessTaskListQ) => request.get<{ rows: PmmProcessTask[]; total: number }>('/pm/process-task/list', p) as any,
  get:           (id: number) => request.get<PmmProcessTask>('/pm/process-task/' + id),
  add:           (d: Partial<PmmProcessTask>) => request.post<number>('/pm/process-task', d),
  edit:          (d: Partial<PmmProcessTask>) => request.put<number>('/pm/process-task', d),
  remove:        (id: number) => request.delete<number>('/pm/process-task/' + id),
  start:         (id: number) => request.put<number>('/pm/process-task/start/' + id),
  finish:        (id: number) => request.put<number>('/pm/process-task/finish/' + id),
  transfer:      (id: number) => request.put<number>('/pm/process-task/transfer/' + id),
  listByWorkOrder: (workOrderId: number) => request.get<PmmProcessTask[]>('/pm/process-task/listByWorkOrder', { workOrderId }) as any
}

/** 生产报工 */
export interface WorkReportListQ extends PageQ {
  reportNo?: string
  workOrderId?: number
  taskId?: number
  operatorId?: number
  reportType?: string
  shift?: string
}
export const workReportApi = {
  list:          (p: WorkReportListQ) => request.get<{ rows: PmmWorkReport[]; total: number }>('/pm/report/list', p) as any,
  get:           (id: number) => request.get<PmmWorkReport>('/pm/report/' + id),
  add:           (d: Partial<PmmWorkReport>) => request.post<number>('/pm/report', d),
  revoke:        (id: number) => request.delete<number>('/pm/report/' + id),
  qc:           (d: Partial<PmmWorkReport>) => request.put<number>('/pm/report/qc', d),
  listByWorkOrder: (workOrderId: number) => request.get<PmmWorkReport[]>('/pm/report/listByWorkOrder', { workOrderId }) as any
}

/** 质量不良 */
export interface QualityListQ extends PageQ {
  workOrderId?: number
  defectCode?: string
  defectLevel?: string
  checkType?: string
  disposition?: string
  processName?: string
  inspectorId?: number
}
export const qualityApi = {
  list:    (p: QualityListQ) => request.get<{ rows: PmmQualityDefect[]; total: number }>('/pm/quality/list', p) as any,
  get:     (id: number) => request.get<PmmQualityDefect>('/pm/quality/' + id),
  add:     (d: Partial<PmmQualityDefect>) => request.post<number>('/pm/quality', d),
  edit:    (d: Partial<PmmQualityDefect>) => request.put<number>('/pm/quality', d),
  export:  (p: QualityListQ) => request.instance.get('/pm/quality/export', { params: p, responseType: 'blob' }) as any,
  defectCodeOptions: () => request.get<PmmDefectCode[]>('/pm/defect-code/options') as any
}

/** 排产计划 */
export interface ScheduleListQ extends PageQ {
  scheduleDate?: string
  shift?: string
  workOrderId?: number
  workCenter?: string
  machineCode?: string
}
export const scheduleApi = {
  list:       (p: ScheduleListQ) => request.get<{ rows: PmmSchedulePlan[]; total: number }>('/pm/schedule/list', p) as any,
  get:        (id: number) => request.get<PmmSchedulePlan>('/pm/schedule/' + id),
  save:       (d: Partial<PmmSchedulePlan>) => request.post<number>('/pm/schedule/save', d),
  remove:     (id: number) => request.delete<number>('/pm/schedule/' + id),
  listByDate: (date: string) => request.get<PmmSchedulePlan[]>('/pm/schedule/listByDate', { date }) as any
}

/** 项目管理 */
export interface ProjectListQ extends PageQ {
  projectCode?: string
  projectName?: string
  customerName?: string
  manager?: string
  status?: number
  priority?: string
}
export const projectApi = {
  list:       (p: ProjectListQ) => request.get<{ rows: PmProject[]; total: number }>('/pm/project/list', p) as any,
  get:        (id: number) => request.get<PmProject>('/pm/project/' + id),
  nextCode:   () => request.get<string>('/pm/project/nextCode'),
  add:        (d: Partial<PmProject>) => request.post<number>('/pm/project', d),
  edit:       (d: Partial<PmProject>) => request.put<number>('/pm/project', d),
  remove:     (id: number) => request.delete<number>('/pm/project/' + id),
  changeStatus: (id: number, status: number) => request.put<number>('/pm/project/status/' + id, null, { params: { status } })
}

/** 项目附件 */
export const projectAttachmentApi = {
  list:     (projectId: number) => request.get<PmProjectAttachment[]>('/pm/project/attachment/list/' + projectId),
  upload:   (projectId: number, file: File, remark?: string) => {
    const fd = new FormData()
    fd.append('projectId', String(projectId))
    fd.append('file', file)
    if (remark) fd.append('remark', remark)
    return request.post<PmProjectAttachment>('/pm/project/attachment/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  download: (attachmentId: number) => request.get('/pm/project/attachment/download/' + attachmentId, { responseType: 'blob' } as any),
  remove:   (attachmentId: number) => request.delete<number>('/pm/project/attachment/' + attachmentId)
}

/** 员工管理 */
export const hrmEmployeeApi = {
  list:     (p: any) => request.get<TableDataInfo<HrmEmployee>>('/hrm/employee/list', { params: p }),
  deptTree: () => request.get<any[]>('/hrm/employee/dept-tree'),
  get:      (id: number) => request.get<HrmEmployee>('/hrm/employee/' + id),
  add:      (d: Partial<HrmEmployee>) => request.post<number>('/hrm/employee', d),
  edit:     (d: Partial<HrmEmployee>) => request.put<number>('/hrm/employee', d),
  remove:   (id: number) => request.delete<number>('/hrm/employee/' + id)
}

/** 员工附件 */
export const hrmEmployeeAttachmentApi = {
  list:     (employeeId: number) => request.get<HrmEmployeeAttachment[]>('/hrm/employee/attachment/list/' + employeeId),
  upload:   (employeeId: number, attachType: string, file: File, remark?: string) => {
    const fd = new FormData()
    fd.append('employeeId', String(employeeId))
    fd.append('attachType', attachType || 'other')
    fd.append('file', file)
    if (remark) fd.append('remark', remark)
    return request.post<HrmEmployeeAttachment>('/hrm/employee/attachment/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  download: (attachmentId: number) => request.get('/hrm/employee/attachment/download/' + attachmentId, { responseType: 'blob' } as any),
  remove:   (attachmentId: number) => request.delete<number>('/hrm/employee/attachment/' + attachmentId)
}

/** 项目分类 */
export const projectCategoryApi = {
  tree:   () => request.get<PmProjectCategory[]>('/pm/project-category/tree'),
  list:   () => request.get<PmProjectCategory[]>('/pm/project-category/list'),
  get:    (id: number) => request.get<PmProjectCategory>('/pm/project-category/' + id),
  add:    (d: Partial<PmProjectCategory>) => request.post<number>('/pm/project-category', d),
  edit:   (d: Partial<PmProjectCategory>) => request.put<number>('/pm/project-category', d),
  remove: (id: number) => request.delete<number>('/pm/project-category/' + id)
}

/** 生产看板 */
export interface KanbanListQ extends PageQ {
  workOrderId?: number
  lineNo?: number
  workCenter?: string
  isFlagged?: number
  title?: string
}
export const kanbanApi = {
  list:   (p: KanbanListQ) => request.get<{ rows: PmmKanbanCard[]; total: number }>('/pm/kanban/list', p) as any,
  board:  () => request.get<PmmKanbanCard[]>('/pm/kanban/board') as any,
  get:    (id: number) => request.get<PmmKanbanCard>('/pm/kanban/' + id),
  add:    (d: Partial<PmmKanbanCard>) => request.post<number>('/pm/kanban', d),
  edit:   (d: Partial<PmmKanbanCard>) => request.put<number>('/pm/kanban', d),
  remove: (id: number) => request.delete<number>('/pm/kanban/' + id),
  move:   (id: number, lineNo?: number, columnNo?: number) =>
    request.put<number>('/pm/kanban/move/' + id, { lineNo, columnNo })
}

/** 不良代码字典 (内嵌质量页) */
export const defectCodeApi = {
  list:    (p: { pageNum: number; pageSize: number; defectCode?: string; defectName?: string; defectCategory?: string; status?: number }) =>
             request.get<{ rows: PmmDefectCode[]; total: number }>('/pm/defect-code/list', p) as any,
  get:     (id: number) => request.get<PmmDefectCode>('/pm/defect-code/' + id),
  options: () => request.get<PmmDefectCode[]>('/pm/defect-code/options') as any,
  add:     (d: Partial<PmmDefectCode>) => request.post<number>('/pm/defect-code', d),
  edit:    (d: Partial<PmmDefectCode>) => request.put<number>('/pm/defect-code', d),
  remove:  (id: number) => request.delete<number>('/pm/defect-code/' + id),
  enable:  (id: number) => request.put<number>('/pm/defect-code/enable/' + id),
  disable: (id: number) => request.put<number>('/pm/defect-code/disable/' + id)
}

// ============ PMM 设备管理 ============
export interface EquipmentListQ extends PageQ {
  equipmentNo?: string
  equipmentName?: string
  equipmentType?: string
  status?: number
  equipLevel?: number
  workshop?: string
  workCenter?: string
}
export interface EquipmentLogListQ extends PageQ {
  equipmentId?: number
  equipmentNo?: string
  eventType?: string
  eventResult?: string
}
export const pmEquipmentApi = {
  list:    (p: EquipmentListQ) => request.get<{ rows: PmmEquipment[]; total: number }>('/pm/equipment/list', p) as any,
  get:     (id: number)        => request.get<PmmEquipment>('/pm/equipment/' + id),
  options: ()                  => request.get<PmmEquipment[]>('/pm/equipment/options') as any,
  add:     (d: Partial<PmmEquipment>) => request.post<number>('/pm/equipment', d),
  edit:    (d: Partial<PmmEquipment>) => request.put<number>('/pm/equipment', d),
  remove:  (id: number)        => request.delete<number>('/pm/equipment/' + id),
  // —— 事件日志 ——
  logList:   (p: EquipmentLogListQ)                => request.get<{ rows: PmmEquipmentLog[]; total: number }>('/pm/equipment/log/list', p) as any,
  logGet:    (logId: number)                       => request.get<PmmEquipmentLog>('/pm/equipment/log/' + logId),
  logAdd:    (d: Partial<PmmEquipmentLog>)         => request.post<number>('/pm/equipment/log', d),
  logRemove: (logId: number)                       => request.delete<number>('/pm/equipment/log/' + logId)
}

// ============ PMM 工装治具 ============
export interface JigListQ extends PageQ {
  jigNo?: string
  jigName?: string
  jigType?: string
  productCode?: string
  productName?: string
  status?: number
  keeper?: string
  warehouse?: string
  location?: string
  borrowerName?: string
}
export interface JigLogListQ extends PageQ {
  jigId?: number
  jigNo?: string
  eventType?: string
  eventTitle?: string
  eventResult?: string
  borrowerName?: string
  startTime?: string
  endTime?: string
}
export const pmJigApi = {
  list:    (p: JigListQ) => request.get<{ rows: PmmJig[]; total: number }>('/pm/jig/list', p) as any,
  get:     (id: number)  => request.get<PmmJig>('/pm/jig/' + id),
  options: (productCode?: string) => request.get<PmmJig[]>('/pm/jig/options', { productCode }) as any,
  add:     (d: Partial<PmmJig>) => request.post<number>('/pm/jig', d),
  edit:    (d: Partial<PmmJig>) => request.put<number>('/pm/jig', d),
  remove:  (id: number)  => request.delete<number>('/pm/jig/' + id),
  // —— 6 大业务动作 ——
  borrow:     (id: number, d: Partial<PmmJigLog>) => request.post<number>(`/pm/jig/${id}/borrow`, d),
  returnBack: (id: number, d: Partial<PmmJigLog>) => request.post<number>(`/pm/jig/${id}/return`, d),
  calibrate:  (id: number, d: Partial<PmmJigLog>) => request.post<number>(`/pm/jig/${id}/calibrate`, d),
  resharpen:  (id: number, d: Partial<PmmJigLog>) => request.post<number>(`/pm/jig/${id}/resharpen`, d),
  repair:     (id: number, d: Partial<PmmJigLog>) => request.post<number>(`/pm/jig/${id}/repair`, d),
  scrap:      (id: number, d?: Partial<PmmJigLog>) => request.post<number>(`/pm/jig/${id}/scrap`, d),
  // —— 动作日志 ——
  logList:   (p: JigLogListQ)                => request.get<{ rows: PmmJigLog[]; total: number }>('/pm/jig/log/list', p) as any,
  logGet:    (logId: number)                 => request.get<PmmJigLog>('/pm/jig/log/' + logId),
  logRemove: (logId: number)                 => request.delete<number>('/pm/jig/log/' + logId)
}

// ============================================================
// 采购管理 (SCM)
// ============================================================

/** 采购申请 */
export interface ScmReqListQ extends PageQ {
  requisitionNo?: string
  title?: string
  applicantName?: string
  deptId?: number
  status?: number
  requiredDate?: string
}
export const scmReqApi = {
  list:    (p: ScmReqListQ) => request.get<{ rows: ScmPurchaseRequisition[]; total: number }>('/scm/requisition/list', p) as any,
  get:     (id: number) => request.get<ScmPurchaseRequisition>('/scm/requisition/' + id),
  options: () => request.get<ScmPurchaseRequisition[]>('/scm/requisition/options') as any,
  add:     (d: Partial<ScmPurchaseRequisition>) => request.post<number>('/scm/requisition', d),
  edit:    (d: Partial<ScmPurchaseRequisition>) => request.put<number>('/scm/requisition', d),
  remove:  (id: number) => request.delete<number>('/scm/requisition/' + id),
  submit:  (id: number) => request.put<number>('/scm/requisition/submit/' + id),
  audit:   (id: number) => request.put<number>('/scm/requisition/audit/' + id),
  reject:  (id: number) => request.put<number>('/scm/requisition/reject/' + id),
  convert: (id: number) => request.put<number>('/scm/requisition/convert/' + id),
  cancel:  (id: number) => request.put<number>('/scm/requisition/cancel/' + id),
  lines:   (requisitionId: number) => request.get<{ rows: ScmPurchaseRequisitionLine[]; total: number }>('/scm/requisition-line/list', { requisitionId, pageNum: 1, pageSize: 200 }) as any,
  addLine: (d: Partial<ScmPurchaseRequisitionLine>) => request.post<number>('/scm/requisition-line', d),
  editLine:(d: Partial<ScmPurchaseRequisitionLine>) => request.put<number>('/scm/requisition-line', d),
  removeLine:(id: number) => request.delete<number>('/scm/requisition-line/' + id)
}

/** 采购订单 */
export interface ScmPoListQ extends PageQ {
  poNo?: string
  poType?: string
  bizCategory?: string
  supplierId?: number
  supplierName?: string
  buyerId?: number
  status?: number
  deliveryDate?: string
}
export const scmPoApi = {
  list:    (p: ScmPoListQ) => request.get<{ rows: ScmPurchaseOrder[]; total: number }>('/scm/purchase/list', p) as any,
  get:     (id: number) => request.get<ScmPurchaseOrder>('/scm/purchase/' + id),
  options: () => request.get<ScmPurchaseOrder[]>('/scm/purchase/options') as any,
  add:     (d: Partial<ScmPurchaseOrder>) => request.post<number>('/scm/purchase', d),
  edit:    (d: Partial<ScmPurchaseOrder>) => request.put<number>('/scm/purchase', d),
  remove:  (id: number) => request.delete<number>('/scm/purchase/' + id),
  submit:  (id: number) => request.put<number>('/scm/purchase/submit/' + id),
  audit:   (id: number) => request.put<number>('/scm/purchase/audit/' + id),
  release: (id: number) => request.put<number>('/scm/purchase/release/' + id),
  complete:(id: number) => request.put<number>('/scm/purchase/complete/' + id),
  reject:  (id: number) => request.put<number>('/scm/purchase/reject/' + id),
  cancel:  (id: number) => request.put<number>('/scm/purchase/cancel/' + id),
  lines:   (poId: number) => request.get<{ rows: ScmPurchaseOrderLine[]; total: number }>('/scm/purchase-line/list', { poId, pageNum: 1, pageSize: 200 }) as any,
  addLine: (d: Partial<ScmPurchaseOrderLine>) => request.post<number>('/scm/purchase-line', d),
  editLine:(d: Partial<ScmPurchaseOrderLine>) => request.put<number>('/scm/purchase-line', d),
  removeLine:(id: number) => request.delete<number>('/scm/purchase-line/' + id),
  // P1: 从采购订单生成一张入库单（支持分批多次入库）
  generateStockIn: (id: number, body: {
    warehouseId: number
    stockInNo?: string
    stockInDate?: string
    remark?: string
    lines: Array<{ lineId: number; qty: number; batchNo?: string; unitPrice?: number; remark?: string }>
  }) => request.put<number>('/scm/purchase/stockIn/' + id, body)
}

/** 采购到货 */
export interface ScmDelListQ extends PageQ {
  deliveryNo?: string
  poNo?: string
  supplierId?: number
  supplierName?: string
  warehouseId?: number
  status?: number
  deliveryDate?: string
}
export const scmDeliveryApi = {
  list:     (p: ScmDelListQ) => request.get<{ rows: ScmDeliveryNote[]; total: number }>('/scm/delivery/list', p) as any,
  get:      (id: number) => request.get<ScmDeliveryNote>('/scm/delivery/' + id),
  options:  () => request.get<ScmDeliveryNote[]>('/scm/delivery/options') as any,
  add:      (d: Partial<ScmDeliveryNote>) => request.post<number>('/scm/delivery', d),
  edit:     (d: Partial<ScmDeliveryNote>) => request.put<number>('/scm/delivery', d),
  remove:   (id: number) => request.delete<number>('/scm/delivery/' + id),
  receive:  (id: number) => request.put<number>('/scm/delivery/receive/' + id),
  iqc:      (id: number) => request.put<number>('/scm/delivery/iqc/' + id),
  stockIn:  (id: number) => request.put<number>('/scm/delivery/stockIn/' + id),
  cancel:   (id: number) => request.put<number>('/scm/delivery/cancel/' + id),
  lines:    (deliveryId: number) => request.get<{ rows: ScmDeliveryNoteLine[]; total: number }>('/scm/delivery-line/list', { deliveryId, pageNum: 1, pageSize: 500 }) as any,
  addLine:  (d: Partial<ScmDeliveryNoteLine>) => request.post<number>('/scm/delivery-line', d),
  editLine: (d: Partial<ScmDeliveryNoteLine>) => request.put<number>('/scm/delivery-line', d),
  removeLine:(id: number) => request.delete<number>('/scm/delivery-line/' + id)
}

/** 采购退货 */
export interface ScmRetListQ extends PageQ {
  returnNo?: string
  poId?: number
  supplierId?: number
  supplierName?: string
  warehouseId?: number
  status?: number
  returnDate?: string
}
export const scmReturnApi = {
  list:     (p: ScmRetListQ) => request.get<{ rows: ScmPurchaseReturn[]; total: number }>('/scm/return/list', p) as any,
  get:      (id: number) => request.get<ScmPurchaseReturn>('/scm/return/' + id),
  options:  () => request.get<ScmPurchaseReturn[]>('/scm/return/options') as any,
  add:      (d: Partial<ScmPurchaseReturn>) => request.post<number>('/scm/return', d),
  edit:     (d: Partial<ScmPurchaseReturn>) => request.put<number>('/scm/return', d),
  remove:   (id: number) => request.delete<number>('/scm/return/' + id),
  submit:   (id: number) => request.put<number>('/scm/return/submit/' + id),
  audit:    (id: number) => request.put<number>('/scm/return/audit/' + id),
  stockOut: (id: number) => request.put<number>('/scm/return/stockOut/' + id),
  sign:     (id: number) => request.put<number>('/scm/return/sign/' + id),
  cancel:   (id: number) => request.put<number>('/scm/return/cancel/' + id),
  lines:    (returnId: number) => request.get<{ rows: ScmPurchaseReturnLine[]; total: number }>('/scm/return-line/list', { returnId, pageNum: 1, pageSize: 500 }) as any,
  addLine:  (d: Partial<ScmPurchaseReturnLine>) => request.post<number>('/scm/return-line', d),
  editLine: (d: Partial<ScmPurchaseReturnLine>) => request.put<number>('/scm/return-line', d),
  removeLine:(id: number) => request.delete<number>('/scm/return-line/' + id)
}

/** 供应商价格 */
export interface ScmPriceListQ extends PageQ {
  supplierId?: number
  materialId?: number
  materialCode?: string
  materialName?: string
  supplierName?: string
  currency?: string
  status?: number
}
export const scmPriceApi = {
  list:       (p: ScmPriceListQ) => request.get<{ rows: ScmSupplierPrice[]; total: number }>('/scm/price/list', p) as any,
  get:        (id: number) => request.get<ScmSupplierPrice>('/scm/price/' + id),
  options:    () => request.get<ScmSupplierPrice[]>('/scm/price/options') as any,
  add:        (d: Partial<ScmSupplierPrice>) => request.post<number>('/scm/price', d),
  edit:       (d: Partial<ScmSupplierPrice>) => request.put<number>('/scm/price', d),
  remove:     (id: number) => request.delete<number>('/scm/price/' + id),
  enable:     (id: number) => request.put<number>('/scm/price/enable/' + id, { status: 1 }),
  disable:    (id: number) => request.put<number>('/scm/price/disable/' + id, { status: 0 }),
  setDefault: (id: number) => request.put<number>('/scm/price/setDefault/' + id),
  submit:     (id: number) => request.put<number>('/scm/price/submit/' + id),
  audit:      (id: number) => request.put<number>('/scm/price/audit/' + id),
  auditFail:  (id: number, reason?: string) => request.put<number>('/scm/price/audit-fail/' + id, { reason }),
  convertOrder: (p: {
    supplierId: number;
    items: Array<{ priceId: number; qty: number; deliveryDate?: string; unitPrice?: number; taxRate?: number; remark?: string }>;
    extra?: { orderDate?: string; deliveryDate?: string; paymentTerm?: string; taxRate?: number; currency?: string; remark?: string }
  }) => request.post<number>('/scm/price/convert-order', p)
}

/** 供应商管理 */
export interface ScmSupplierListQ extends PageQ {
  supplierCode?: string
  supplierName?: string
  contact?: string
  phone?: string
  level?: string
  status?: number
}
export const scmSupplierApi = {
  list:    (p: ScmSupplierListQ) => request.get<{ rows: ScmSupplier[]; total: number }>('/scm/supplier/list', p) as any,
  get:     (id: number) => request.get<ScmSupplier>('/scm/supplier/' + id),
  options: () => request.get<ScmSupplier[]>('/scm/supplier/options') as any,
  nextCode: () => request.get<string>('/scm/supplier/next-code') as any,
  add:     (d: Partial<ScmSupplier>) => request.post<number>('/scm/supplier', d),
  edit:    (d: Partial<ScmSupplier>) => request.put<number>('/scm/supplier', d),
  remove:  (id: number) => request.delete<number>('/scm/supplier/' + id),
  enable:  (id: number) => request.put<number>('/scm/supplier/enable/' + id, { status: 1 }),
  disable: (id: number) => request.put<number>('/scm/supplier/disable/' + id, { status: 0 }),
  rate:    (id: number) => request.put<number>('/scm/supplier/rate/' + id, { status: 1 })
}

/* ========== 应付单 (AP Payable) ========== */
export interface ScmApPayableListQ extends PageQ {
  payableNo?: string
  supplierId?: number
  supplierName?: string
  status?: number
  bizDate?: string
  dueDate?: string
  isOverdue?: number
}
export const scmApPayableApi = {
  list:    (p: ScmApPayableListQ) => request.get<{ rows: ScmApPayable[]; total: number }>('/scm/ap-payable/list', p) as any,
  get:     (id: number) => request.get<ScmApPayable>('/scm/ap-payable/' + id),
  options: () => request.get<ScmApPayable[]>('/scm/ap-payable/options') as any,
  add:     (d: Partial<ScmApPayable>) => request.post<number>('/scm/ap-payable', d),
  edit:    (d: Partial<ScmApPayable>) => request.put<number>('/scm/ap-payable', d),
  remove:  (id: number) => request.delete<number>('/scm/ap-payable/' + id),
  // 行项
  lines:   (payableId: number) => request.get<{ rows: ScmApPayableLine[]; total: number }>(`/scm/ap-payable/${payableId}/lines`, { pageNum: 1, pageSize: 500 }) as any,
  addLine: (d: Partial<ScmApPayableLine>) => request.post<number>('/scm/ap-payable/line', d),
  editLine:(d: Partial<ScmApPayableLine>) => request.put<number>('/scm/ap-payable/line', d),
  removeLine:(id: number) => request.delete<number>('/scm/ap-payable/line/' + id),
  // 付款计划
  plans:         (payableId: number) => request.get<ScmApPaymentPlan[]>(`/scm/ap-payable/${payableId}/plans`) as any,
  generatePlans: (payableId: number, phases: any[]) => request.post<number>(`/scm/ap-payable/${payableId}/plans/generate`, phases),
  // 业务动作
  reconcile: (id: number, reconcileBy?: string) => request.put<number>(`/scm/ap-payable/reconcile/${id}`, undefined, { params: { reconcileBy } }),
  cancel:    (id: number) => request.put<number>(`/scm/ap-payable/cancel/${id}`),
  // 看板
  kanbanSummary: (supplierId?: number) => request.get<Record<string, any>>('/scm/ap-payable/kanban/summary', supplierId != null ? { supplierId } : {}) as any,
  topSuppliers:  (supplierId?: number, limit = 10) => request.get<any[]>('/scm/ap-payable/kanban/top-suppliers', supplierId != null ? { supplierId, limit } : { limit }) as any,
  aging:         (supplierId?: number) => request.get<any[]>('/scm/ap-payable/kanban/aging', supplierId != null ? { supplierId } : {}) as any
}

/* ========== 付款计划 ========== */
export interface ScmApPlanListQ extends PageQ {
  planNo?: string
  payableId?: number
  supplierId?: number
  supplierName?: string
  status?: number
  planDate?: string
  phaseNo?: number
}
export const scmApPlanApi = {
  list:   (p: ScmApPlanListQ) => request.get<{ rows: ScmApPaymentPlan[]; total: number }>('/scm/ap-payment-plan/list', p) as any,
  get:    (id: number) => request.get<ScmApPaymentPlan>('/scm/ap-payment-plan/' + id),
  add:    (d: Partial<ScmApPaymentPlan>) => request.post<number>('/scm/ap-payment-plan', d),
  edit:   (d: Partial<ScmApPaymentPlan>) => request.put<number>('/scm/ap-payment-plan', d),
  remove: (id: number) => request.delete<number>('/scm/ap-payment-plan/' + id)
}

/* ========== 付款单 ========== */
export interface ScmApPaymentListQ extends PageQ {
  paymentNo?: string
  supplierId?: number
  supplierName?: string
  status?: number
  payDate?: string
  planId?: number
  payableId?: number
}
export const scmApPaymentApi = {
  list:    (p: ScmApPaymentListQ) => request.get<{ rows: ScmApPayment[]; total: number }>('/scm/ap-payment/list', p) as any,
  get:     (id: number) => request.get<ScmApPayment>('/scm/ap-payment/' + id),
  options: () => request.get<ScmApPayment[]>('/scm/ap-payment/options') as any,
  add:     (d: Partial<ScmApPayment>) => request.post<number>('/scm/ap-payment', d),
  edit:    (d: Partial<ScmApPayment>) => request.put<number>('/scm/ap-payment', d),
  remove:  (id: number) => request.delete<number>('/scm/ap-payment/' + id),
  audit:   (id: number, auditBy?: string) => request.put<number>(`/scm/ap-payment/audit/${id}`, undefined, { params: { auditBy } }),
  confirm: (id: number, payBy?: string)   => request.put<number>(`/scm/ap-payment/confirm/${id}`, undefined, { params: { payBy } }),
  fail:    (id: number, reason?: string)  => request.put<number>(`/scm/ap-payment/fail/${id}`, undefined, { params: { reason } }),
  cancel:  (id: number, reason?: string)  => request.put<number>(`/scm/ap-payment/cancel/${id}`, undefined, { params: { reason } })
}

/* ========== 财务凭证接口 ========== */
export interface ScmFinVoucherListQ extends PageQ {
  voucherNo?: string
  voucherType?: string
  bizSource?: string
  bizId?: number
  pushStatus?: number
  voucherDate?: string
}
export const finVoucherApi = {
  list:        (p: ScmFinVoucherListQ) => request.get<{ rows: FinVoucherInterface[]; total: number }>('/scm/fin-voucher/list', p) as any,
  get:         (id: number) => request.get<FinVoucherInterface>('/scm/fin-voucher/' + id),
  options:     () => request.get<FinVoucherInterface[]>('/scm/fin-voucher/options') as any,
  add:         (d: Partial<FinVoucherInterface>) => request.post<number>('/scm/fin-voucher', d),
  edit:        (d: Partial<FinVoucherInterface>) => request.put<number>('/scm/fin-voucher', d),
  remove:      (id: number) => request.delete<number>('/scm/fin-voucher/' + id),
  pushPayable: (payableId: number) => request.post<number>(`/scm/fin-voucher/push/payable/${payableId}`),
  pushPayment: (paymentId: number) => request.post<number>(`/scm/fin-voucher/push/payment/${paymentId}`),
  mockPush:    (id: number) => request.put<number>(`/scm/fin-voucher/mock-push/${id}`),
  cancel:      (id: number, reason?: string) => request.put<number>(`/scm/fin-voucher/cancel/${id}`, undefined, { params: { reason } })
}

// ============================================================
// 销售管理 (SAL)
// ============================================================

/** 客户管理 */
export interface SalCustomerListQ extends PageQ {
  customerCode?: string
  customerName?: string
  industry?: string
  level?: string
  status?: number
}
export const salCustomerApi = {
  list:    (p: SalCustomerListQ) => request.get<{ rows: SalCustomer[]; total: number }>('/sal/customer/list', p) as any,
  get:     (id: number) => request.get<SalCustomer>('/sal/customer/' + id),
  options: () => request.get<SalCustomer[]>('/sal/customer/options') as any,
  nextCode: () => request.get<string>('/sal/customer/next-code') as any,
  add:     (d: Partial<SalCustomer>) => request.post<number>('/sal/customer', d),
  edit:    (d: Partial<SalCustomer>) => request.put<number>('/sal/customer', d),
  remove:  (id: number) => request.delete<number>('/sal/customer/' + id),
  enable:  (id: number) => request.put<number>('/sal/customer/enable/' + id, { status: 1 }),
  disable: (id: number) => request.put<number>('/sal/customer/disable/' + id, { status: 0 }),
  getCredit: (id: number) => request.get<any>('/sal/customer/' + id + '/credit') as any
}

// ============ 销售开票 ============
export interface SalInvoiceListQ extends PageQ { invoiceNo?: string; customerId?: number; invoiceType?: string; status?: number; dateFrom?: string; dateTo?: string }
export const salInvoiceApi = {
  list: (p: SalInvoiceListQ) => request.get<Page<SalInvoice>>('/sal/invoice/list', p),
  get: (id: number) => request.get<{ invoice: SalInvoice; lines: SalInvoiceLine[] }>('/sal/invoice/' + id),
  invoiceable: (customerId: number) => request.get<InvoiceableReceivable[]>('/sal/invoice/invoiceable', { customerId }),
  add: (invoice: Partial<SalInvoice>, lines: Partial<SalInvoiceLine>[]) => request.post<number>('/sal/invoice', { invoice, lines }),
  edit: (invoice: Partial<SalInvoice>, lines: Partial<SalInvoiceLine>[]) => request.put<number>('/sal/invoice', { invoice, lines }),
  remove: (id: number) => request.delete<number>('/sal/invoice/' + id),
  issue: (id: number) => request.put<number>('/sal/invoice/issue/' + id),
  cancel: (id: number) => request.put<number>('/sal/invoice/cancel/' + id),
  export: (qs: Partial<SalInvoiceListQ>) => request.instance.get('/sal/export/invoices', { params: qs, responseType: 'blob' }) as any
}

/** 销售报价(客户专属价格表) */
export interface SalPriceListQ extends PageQ {
  customerId?: number
  materialId?: number
  currency?: string
  status?: number
  customerName?: string
  onlyValid?: number
}
export const salPriceApi = {
  list:       (p: SalPriceListQ) => request.get<{ rows: any[]; total: number }>('/sal/price/list', p) as any,
  get:        (id: number) => request.get<any>('/sal/price/' + id),
  add:        (d: any) => request.post<number>('/sal/price', d),
  batch:      (items: any[]) => request.post<number[]>('/sal/price/batch', items) as any,
  edit:       (d: any) => request.put<number>('/sal/price', d),
  remove:     (id: number) => request.delete<number>('/sal/price/' + id),
  submit:     (id: number) => request.put<number>('/sal/price/submit/' + id),
  audit:      (id: number) => request.put<number>('/sal/price/audit/' + id),
  reject:     (id: number, reason?: string) => request.put<number>('/sal/price/reject/' + id, { reason }),
  disable:    (id: number) => request.put<number>('/sal/price/disable/' + id),
  enable:     (id: number) => request.put<number>('/sal/price/enable/' + id),
  setDefault: (id: number) => request.put<number>('/sal/price/setDefault/' + id),
  match:      (customerId: number, materialId: number, qty?: number, date?: string) =>
    request.get<any>('/sal/price/match', { customerId, materialId, qty, date }),
  options:    () => request.get<any[]>('/sal/price/options') as any,
  export:     (p: SalPriceListQ) => request.instance.get('/sal/price/export', { params: p, responseType: 'blob' }),
  // P1: 报价条目批量 → 1张销售订单
  convertOrder:(p: { customerId: number; items: Array<{priceId: number; qty: number; deliveryDate?: string}>;
                  extra?: { deliveryDate?: string; soDate?: string; warehouseId?: number; salesRepId?: number; paymentTerm?: string; remark?: string } }) =>
    request.post<number>('/sal/price/convert-order', p)
}

/** 销售订单 */
export interface SalOrderListQ extends PageQ {
  soNo?: string
  soType?: string
  customerId?: number
  customerName?: string
  salesRepId?: number
  status?: number
  deliveryDate?: string
}
export const salOrderApi = {
  list:    (p: SalOrderListQ) => request.get<{ rows: SalSalesOrder[]; total: number }>('/sal/order/list', p) as any,
  get:     (id: number) => request.get<SalSalesOrder>('/sal/order/' + id),
  options: () => request.get<SalSalesOrder[]>('/sal/order/options') as any,
  add:     (d: Partial<SalSalesOrder>) => request.post<number>('/sal/order', d),
  edit:    (d: Partial<SalSalesOrder>) => request.put<number>('/sal/order', d),
  remove:  (id: number) => request.delete<number>('/sal/order/' + id),
  submit:   (id: number) => request.put<number>('/sal/order/submit/' + id),
  audit:    (id: number) => request.put<number>('/sal/order/audit/' + id),
  complete: (id: number) => request.put<number>('/sal/order/complete/' + id),
  stockOut: (id: number) => request.put<number>('/sal/order/stockOut/' + id),
  reject:  (id: number, reason?: string) => request.put<number>('/sal/order/reject/' + id, undefined, { params: { reason } }),
  cancel:  (id: number) => request.put<number>('/sal/order/cancel/' + id),
  // 行项
  lines:   (soId: number) => request.get<{ rows: SalSalesOrderLine[]; total: number }>('/sal/order-line/list', { soId, pageNum: 1, pageSize: 500 }) as any,
  addLine: (d: Partial<SalSalesOrderLine>) => request.post<number>('/sal/order-line', d),
  editLine:(d: Partial<SalSalesOrderLine>) => request.put<number>('/sal/order-line', d),
  removeLine:(id: number) => request.delete<number>('/sal/order-line/' + id),
  export:  (p: SalOrderListQ) => request.instance.get('/sal/export/orders', { params: p, responseType: 'blob' }) as any
}

/** 销售齐套 */
export interface SalKittingListQ extends PageQ {
  kittingNo?: string
  soNo?: string
  customerId?: number
  status?: number
  bomId?: number
}
export const salKittingApi = {
  list:        (p: SalKittingListQ) => request.get<{ rows: SalKitting[]; total: number }>('/sal/kitting/list', p) as any,
  get:         (id: number) => request.get<SalKitting>('/sal/kitting/' + id),
  nextCode:    () => request.get<string>('/sal/kitting/next-code'),
  options:     () => request.get<SalKitting[]>('/sal/kitting/options') as any,
  add:         (d: Partial<SalKitting>) => request.post<number>('/sal/kitting', d),
  edit:        (d: Partial<SalKitting>) => request.put<number>('/sal/kitting', d),
  remove:      (id: number) => request.delete<number>('/sal/kitting/' + id),
  // 核心动作
  check:       (id: number) => request.put<number>('/sal/kitting/check/' + id),
  stockOut:    (id: number) => request.put<number>('/sal/kitting/stockout/' + id),
  requisition: (id: number) => request.put<number>('/sal/kitting/requisition/' + id),
  cancel:      (id: number) => request.put<number>('/sal/kitting/cancel/' + id),
  // P0: 从订单行一键创建齐套单(自动匹配BOM并执行检查), 返回齐套单详情
  fromOrderLine:(p: { soLineId: number; bomId?: number }) =>
    request.post<SalKitting>('/sal/kitting/from-order-line', p),
  // 明细
  lines:       (id: number) => request.get<SalKittingLine[]>('/sal/kitting/' + id + '/lines') as any
}

/** 销售退货 */
export interface SalReturnListQ extends PageQ {
  returnNo?: string
  soId?: number
  customerId?: number
  customerName?: string
  status?: number
  returnDate?: string
}
export const salReturnApi = {
  list:    (p: SalReturnListQ) => request.get<{ rows: SalSalesReturn[]; total: number }>('/sal/return/list', p) as any,
  get:     (id: number) => request.get<SalSalesReturn>('/sal/return/' + id),
  options: () => request.get<SalSalesReturn[]>('/sal/return/options') as any,
  add:     (d: Partial<SalSalesReturn>) => request.post<number>('/sal/return', d),
  edit:    (d: Partial<SalSalesReturn>) => request.put<number>('/sal/return', d),
  remove:  (id: number) => request.delete<number>('/sal/return/' + id),
  submit:   (id: number) => request.put<number>('/sal/return/submit/' + id),
  audit:    (id: number) => request.put<number>('/sal/return/audit/' + id),
  complete: (id: number) => request.put<number>('/sal/return/complete/' + id),
  reject:  (id: number, reason?: string) => request.put<number>('/sal/return/reject/' + id, undefined, { params: { reason } }),
  cancel:  (id: number) => request.put<number>('/sal/return/cancel/' + id),
  // 退货质检录入: 入库确认后待检 → 录入合格数/处置 → 推进3→4并重算退款
  submitQc: (id: number, data: { disposition?: string; qcRemark?: string; lines?: Partial<SalSalesReturnLine>[] }) =>
    request.put<number>('/sal/return/qc/' + id, data),
  // 行项
  lines:   (returnId: number) => request.get<{ rows: SalSalesReturnLine[]; total: number }>('/sal/return-line/list', { returnId, pageNum: 1, pageSize: 500 }) as any,
  addLine: (d: Partial<SalSalesReturnLine>) => request.post<number>('/sal/return-line', d),
  editLine:(d: Partial<SalSalesReturnLine>) => request.put<number>('/sal/return-line', d),
  removeLine:(id: number) => request.delete<number>('/sal/return-line/' + id),
  export:  (p: SalReturnListQ) => request.instance.get('/sal/export/returns', { params: p, responseType: 'blob' }) as any
}

// ============ 销售预测/滚动计划 ============
export interface SalForecastListQ extends PageQ {
  versionNo?: string
  versionType?: string
  customerId?: number
  materialId?: number
  status?: number
  customerName?: string
  materialName?: string
}
export const salForecastApi = {
  list:    (p: SalForecastListQ) => request.get<{ rows: SalForecast[]; total: number }>('/sal/forecast/list', p) as any,
  get:     (id: number) => request.get<SalForecast>('/sal/forecast/' + id),
  add:     (d: Partial<SalForecast>) => request.post<number>('/sal/forecast', d),
  edit:    (d: Partial<SalForecast>) => request.put<number>('/sal/forecast', d),
  remove:  (id: number) => request.delete<number>('/sal/forecast/' + id),
  versions:() => request.get<SalForecastVersion[]>('/sal/forecast/versions') as any,
  generate:(versionType: string, periodsAhead = 12) =>
    request.post<number>('/sal/forecast/generate', undefined, { params: { versionType, periodsAhead } }),
  rollback:(versionNo: string) => request.put<number>('/sal/forecast/rollback', undefined, { params: { versionNo } }),
  audit:   (versionNo: string) => request.put<number>('/sal/forecast/audit', undefined, { params: { versionNo } }),
  archive: (versionNo: string) => request.put<number>('/sal/forecast/archive', undefined, { params: { versionNo } }),
  adjust:  (forecastId: number, newQty: number) =>
    request.put<number>('/sal/forecast/adjust', undefined, { params: { forecastId, newQty } }),
  kanbanSummary: (customerId?: number) => request.get<any>('/sal/forecast/kanban/summary', customerId != null ? { customerId } : {}) as any,
  trend:   (customerId?: number, periods = 12) => request.get<any[]>('/sal/forecast/kanban/trend', customerId != null ? { customerId, periods } : { periods }) as any,
  topCustomers: (customerId?: number, limit = 10) => request.get<any[]>('/sal/forecast/kanban/top-customers', customerId != null ? { customerId, limit } : { limit }) as any,
  topMaterials: (customerId?: number, limit = 10) => request.get<any[]>('/sal/forecast/kanban/top-materials', customerId != null ? { customerId, limit } : { limit }) as any,
  export:  (p: SalForecastListQ) => request.instance.get('/sal/forecast/export', { params: p, responseType: 'blob' }) as any
}

/* ========== 应收单 (AR Receivable) ========== */
export interface SalArReceivableListQ extends PageQ {
  receivableNo?: string
  customerId?: number
  customerName?: string
  status?: number
  bizDate?: string
  dueDate?: string
  isOverdue?: number
}
export const salArReceivableApi = {
  list:    (p: SalArReceivableListQ) => request.get<{ rows: SalArReceivable[]; total: number }>('/sal/ar-receivable/list', p) as any,
  get:     (id: number) => request.get<SalArReceivable>('/sal/ar-receivable/' + id),
  options: () => request.get<SalArReceivable[]>('/sal/ar-receivable/options') as any,
  add:     (d: Partial<SalArReceivable>) => request.post<number>('/sal/ar-receivable', d),
  edit:    (d: Partial<SalArReceivable>) => request.put<number>('/sal/ar-receivable', d),
  remove:  (id: number) => request.delete<number>('/sal/ar-receivable/' + id),
  // 行项
  lines:   (receivableId: number) => request.get<{ rows: SalArReceivableLine[]; total: number }>(`/sal/ar-receivable/${receivableId}/lines`, { pageNum: 1, pageSize: 500 }) as any,
  addLine: (d: Partial<SalArReceivableLine>) => request.post<number>('/sal/ar-receivable/line', d),
  editLine:(d: Partial<SalArReceivableLine>) => request.put<number>('/sal/ar-receivable/line', d),
  removeLine:(id: number) => request.delete<number>('/sal/ar-receivable/line/' + id),
  // 收款计划
  plans:         (receivableId: number) => request.get<SalArReceiptPlan[]>(`/sal/ar-receivable/${receivableId}/plans`) as any,
  generatePlans: (receivableId: number, phases: any[]) => request.post<number>(`/sal/ar-receivable/${receivableId}/plans/generate`, phases),
  // 业务动作 — 对账两级流程 + 反审 + 批量
  submitReconcile: (id: number, body: { signBy?: string; attachment?: string; remark?: string }) =>
    request.put<number>(`/sal/ar-receivable/submit-reconcile/${id}`, body),
  confirmReconcile: (id: number, body: { signBy: string; signTime?: string; voucherId?: number }) =>
    request.put<number>(`/sal/ar-receivable/confirm-reconcile/${id}`, body),
  undoReconcile: (id: number) => request.put<number>(`/sal/ar-receivable/undo-reconcile/${id}`),
  batchReconcile: (body: { customerId: number; dateFrom: string; dateTo: string }) =>
    request.post<number>(`/sal/ar-receivable/batch-reconcile`, body),
  cancel:    (id: number) => request.put<number>(`/sal/ar-receivable/cancel/${id}`),
  export:    (p: SalArReceivableListQ) => request.instance.get('/sal/export/receivables', { params: p, responseType: 'blob' }) as any,
  exportStatement: (customerId: number, dateFrom: string, dateTo: string) =>
    request.instance.get('/sal/export/ar-statement', { params: { customerId, dateFrom, dateTo }, responseType: 'blob' }) as any,
  // 看板
  kanbanSummary: (customerId?: number) => request.get<Record<string, any>>('/sal/kanban/summary', customerId != null ? { customerId } : {}) as any,
  topCustomers:  (customerId?: number, limit = 10) => request.get<any[]>('/sal/kanban/top-customers', customerId != null ? { customerId, limit } : { limit }) as any,
  aging:         (customerId?: number) => request.get<any[]>('/sal/kanban/aging', customerId != null ? { customerId } : {}) as any,
  salesSummary:  (customerId?: number) => request.get<Record<string, any>>('/sal/kanban/sales-summary', customerId != null ? { customerId } : {}) as any,
  topReceived:   (customerId?: number, limit = 10) => request.get<any[]>('/sal/kanban/top-received', customerId != null ? { customerId, limit } : { limit }) as any,
  salesTrend:    (customerId?: number, months = 6) => request.get<any[]>('/sal/kanban/sales-trend', customerId != null ? { customerId, months } : { months }) as any
}

/* ========== 收款计划 ========== */
export interface SalArPlanListQ extends PageQ {
  planNo?: string
  receivableId?: number
  customerId?: number
  customerName?: string
  status?: number
  planDate?: string
  phaseNo?: number
}
export const salArPlanApi = {
  list:   (p: SalArPlanListQ) => request.get<{ rows: SalArReceiptPlan[]; total: number }>('/sal/ar-receipt-plan/list', p) as any,
  get:    (id: number) => request.get<SalArReceiptPlan>('/sal/ar-receipt-plan/' + id),
  add:    (d: Partial<SalArReceiptPlan>) => request.post<number>('/sal/ar-receipt-plan', d),
  edit:   (d: Partial<SalArReceiptPlan>) => request.put<number>('/sal/ar-receipt-plan', d),
  remove: (id: number) => request.delete<number>('/sal/ar-receipt-plan/' + id)
}

/* ========== 收款单 ========== */
export interface SalArReceiptListQ extends PageQ {
  receiptNo?: string
  customerId?: number
  customerName?: string
  status?: number
  receiptDate?: string
  planId?: number
  receivableId?: number
}
export const salArReceiptApi = {
  list:    (p: SalArReceiptListQ) => request.get<{ rows: SalArReceipt[]; total: number }>('/sal/ar-receipt/list', p) as any,
  get:     (id: number) => request.get<SalArReceipt>('/sal/ar-receipt/' + id),
  options: () => request.get<SalArReceipt[]>('/sal/ar-receipt/options') as any,
  add:     (d: Partial<SalArReceipt>) => request.post<number>('/sal/ar-receipt', d),
  edit:    (d: Partial<SalArReceipt>) => request.put<number>('/sal/ar-receipt', d),
  remove:  (id: number) => request.delete<number>('/sal/ar-receipt/' + id),
  audit:   (id: number, auditBy?: string) => request.put<number>(`/sal/ar-receipt/audit/${id}`, undefined, { params: { auditBy } }),
  confirm: (id: number, receiptBy?: string) => request.put<number>(`/sal/ar-receipt/confirm/${id}`, undefined, { params: { receiptBy } }),
  fail:    (id: number, reason?: string)  => request.put<number>(`/sal/ar-receipt/fail/${id}`, undefined, { params: { reason } }),
  cancel:  (id: number, reason?: string)  => request.put<number>(`/sal/ar-receipt/cancel/${id}`, undefined, { params: { reason } })
}

// ============ ASM 装配管理 ============
/** 配料单 */
export interface AsmKittingListQ extends PageQ {
  kittingNo?: string
  workOrderId?: number
  materialCode?: string
  kittingType?: string
  status?: number
}
export const asmKittingApi = {
  list:    (p: AsmKittingListQ) => request.get<{ rows: AsmKitting[]; total: number }>('/asm/kitting/list', p) as any,
  get:     (id: number) => request.get<AsmKitting>('/asm/kitting/' + id),
  nextCode:() => request.get<string>('/asm/kitting/next-code'),
  add:     (d: Partial<AsmKitting>) => request.post<number>('/asm/kitting', d),
  edit:    (d: Partial<AsmKitting>) => request.put<number>('/asm/kitting', d),
  remove:  (id: number) => request.delete<number>('/asm/kitting/' + id),
  options: () => request.get<AsmKitting[]>('/asm/kitting/options') as any,
  // 状态流转
  start:   (id: number) => request.put<number>('/asm/kitting/start/' + id),
  finish:  (id: number) => request.put<number>('/asm/kitting/finish/' + id),
  issue:   (id: number) => request.put<number>('/asm/kitting/issue/' + id),
  cancel:  (id: number) => request.put<number>('/asm/kitting/cancel/' + id),
  // 明细行
  lines:   (kittingId: number) => request.get<AsmKittingLine[]>('/asm/kitting/lines', { kittingId }) as any
}

/** SN码追溯 */
export interface AsmSnListQ extends PageQ {
  snCode?: string
  snType?: string
  snLevel?: number
  currentStatus?: number
  workOrderId?: number
  materialCode?: string
}
export const asmSnApi = {
  list:   (p: AsmSnListQ) => request.get<{ rows: AsmSn[]; total: number }>('/asm/sn/list', p) as any,
  get:    (id: number) => request.get<AsmSn>('/asm/sn/' + id),
  add:    (d: Partial<AsmSn>) => request.post<number>('/asm/sn', d),
  edit:   (d: Partial<AsmSn>) => request.put<number>('/asm/sn', d),
  remove: (id: number) => request.delete<number>('/asm/sn/' + id),
  updateStatus: (id: number, status: number, process?: string) =>
    request.put<number>('/asm/sn/update-status/' + id, undefined, { params: { status, process } })
}

/** Feeder上料记录 */
export interface AsmFeederListQ extends PageQ {
  equipmentCode?: string
  feederNo?: string
  materialCode?: string
  status?: number
}
export const asmFeederApi = {
  list:    (p: AsmFeederListQ) => request.get<{ rows: AsmFeederLoad[]; total: number }>('/asm/feeder-load/list', p) as any,
  get:     (id: number) => request.get<AsmFeederLoad>('/asm/feeder-load/' + id),
  add:     (d: Partial<AsmFeederLoad>) => request.post<number>('/asm/feeder-load', d),
  edit:    (d: Partial<AsmFeederLoad>) => request.put<number>('/asm/feeder-load', d),
  remove:  (id: number) => request.delete<number>('/asm/feeder-load/' + id),
  load:    (d: Partial<AsmFeederLoad>) => request.put<number>('/asm/feeder-load/load', d),
  unload:  (id: number) => request.put<number>('/asm/feeder-load/unload/' + id),
  changeStatus: (id: number, status: number) => request.put<number>('/asm/feeder-load/status/' + id, undefined, { params: { status } })
}

/** SN追溯查询 */
export const asmTraceApi = {
  upstream:  (snCode: string) => request.get<any[]>('/asm/trace/upstream/' + encodeURIComponent(snCode)) as any,
  downstream:(snCode: string) => request.get<any[]>('/asm/trace/downstream/' + encodeURIComponent(snCode)) as any,
  nodes:     (snCode: string) => request.get<AsmSnNode[]>('/asm/trace/node/' + encodeURIComponent(snCode)) as any,
  feederLog: (keyword: string) => request.get<AsmFeederLoad[]>('/asm/trace/feeder-log', { keyword }) as any
}

/** 工序节点批量录入 (MES扫码对接用) */
export const asmNodeApi = {
  inbound:  (list: any[]) => request.post<number>('/asm/node/inbound', list),
  load:     (list: any[]) => request.post<number>('/asm/node/load', list),
  smt:      (list: any[]) => request.post<number>('/asm/node/smt', list),
  aoi:      (list: any[]) => request.post<number>('/asm/node/aoi', list),
  ict:      (list: any[]) => request.post<number>('/asm/node/ict', list),
  outbound: (list: any[]) => request.post<number>('/asm/node/outbound', list)
}

// ==================== 后道组装 ====================
export const pmFasApi = {
  // 组装工单
  orderList:    (p: any) => request.get('/fas/order/list', p) as any,
  orderGet:     (id: number) => request.get<FasOrder>(`/fas/order/${id}`) as any,
  orderAdd:     (d: Partial<FasOrder>) => request.post<number>('/fas/order', d) as any,
  orderEdit:    (d: Partial<FasOrder>) => request.put('/fas/order', d) as any,
  orderRemove:  (id: number) => request.delete(`/fas/order/${id}`) as any,
  orderRelease: (id: number) => request.post(`/fas/order/${id}/release`) as any,
  orderStart:   (id: number) => request.post(`/fas/order/${id}/start`) as any,
  orderComplete:(id: number) => request.post(`/fas/order/${id}/complete`) as any,
  orderStockIn: (id: number, d: { warehouse: string; batchNo?: string; qty?: number; remark?: string }) => request.post(`/fas/order/${id}/stockIn`, d) as any,
  // 组装BOM
  bomList:      (p: any) => request.get('/fas/bom/list', p) as any,
  bomGet:       (id: number) => request.get<FasBom>(`/fas/bom/${id}`) as any,
  bomAdd:       (d: Partial<FasBom>) => request.post<number>('/fas/bom', d) as any,
  bomEdit:      (d: Partial<FasBom>) => request.put('/fas/bom', d) as any,
  bomRemove:    (id: number) => request.delete(`/fas/bom/${id}`) as any,
  bomByProduct: (productId: number) => request.get<FasBom[]>(`/fas/bom/product/${productId}`) as any,
  // 工序流转
  stepList:     (p: any) => request.get('/fas/step/list', p) as any,
  stepGet:      (id: number) => request.get<FasStep>(`/fas/step/${id}`) as any,
  stepAdd:      (d: Partial<FasStep>) => request.post<number>('/fas/step', d) as any,
  stepEdit:     (d: Partial<FasStep>) => request.put('/fas/step', d) as any,
  stepRemove:   (id: number) => request.delete(`/fas/step/${id}`) as any,
  stepByOrder:  (orderId: number) => request.get<FasStep[]>(`/fas/step/order/${orderId}`) as any,
  stepStart:    (stepId: number, operator: string) => request.post(`/fas/step/${stepId}/start`, { operator }) as any,
  stepComplete: (stepId: number, d: { qtyPass: number; qtyNg: number; operator: string }) => request.post(`/fas/step/${stepId}/complete`, d) as any,
  // 过程记录
  recordList:   (p: any) => request.get('/fas/record/list', p) as any,
  recordAdd:    (d: Partial<FasRecord>) => request.post<number>('/fas/record', d) as any,
  recordRemove: (id: number) => request.delete(`/fas/record/${id}`) as any,
  recordByOrder:(orderId: number) => request.get<FasRecord[]>(`/fas/record/order/${orderId}`) as any,
  // 成品库存
  stockList:    (p: any) => request.get('/fas/stock/list', p) as any,
  stockGet:     (id: number) => request.get<FasProductStock>(`/fas/stock/${id}`) as any,
  stockOut:     (stockId: number, qty: number) => request.post(`/fas/stock/${stockId}/out`, { qty }) as any,
  stockRemove:  (id: number) => request.delete(`/fas/stock/${id}`) as any,
  // 配件库存台账 (外购配件 stock_type=ACC)
  accStockList:   (p: any) => request.get('/fas/acc-stock/list', p) as any,
  accStockGet:    (id: number) => request.get<FasProductStock>(`/fas/acc-stock/${id}`) as any,
  accStockOut:    (stockId: number, qty: number) => request.post(`/fas/acc-stock/${stockId}/out`, { qty }) as any,
  accStockRemove: (id: number) => request.delete(`/fas/acc-stock/${id}`) as any,
  // 完工入库单
  inboundList:   (p: any) => request.get('/fas/finished-inbound/list', p) as any,
  inboundGet:    (id: number) => request.get<FasProductStock>(`/fas/finished-inbound/${id}`) as any,
  inboundSubmit: (id: number) => request.post(`/fas/finished-inbound/${id}/submit`) as any,
  inboundAudit:  (id: number) => request.post(`/fas/finished-inbound/${id}/audit-pass`) as any,
  inboundReject: (id: number, rejectReason: string) => request.post(`/fas/finished-inbound/${id}/reject`, { rejectReason }) as any,
  inboundRemove: (id: number) => request.delete(`/fas/finished-inbound/${id}`) as any,
  // 成品出库单
  outboundList:   (p: any) => request.get('/fas/finished-outbound/list', p) as any,
  outboundGet:    (id: number) => request.get<FasProductStock>(`/fas/finished-outbound/${id}`) as any,
  outboundCreate: (d: Partial<FasProductStock>) => request.post<number>('/fas/finished-outbound', d) as any,
  outboundSubmit: (id: number) => request.post(`/fas/finished-outbound/${id}/submit`) as any,
  outboundAudit:  (id: number) => request.post(`/fas/finished-outbound/${id}/audit-pass`) as any,
  outboundReject: (id: number, rejectReason: string) => request.post(`/fas/finished-outbound/${id}/reject`, { rejectReason }) as any,
  outboundRemove: (id: number) => request.delete(`/fas/finished-outbound/${id}`) as any,
  // 出入库流水
  flowList: (p: any) => request.get('/fas/finished-flow/list', p) as any,
}

// ==================== 公司设置 ====================
export const companyApi = {
  // 公司档案
  profileGet:  () => request.get<CompanyProfile>('/company/profile') as any,
  profileSave: (d: Partial<CompanyProfile>) => request.put('/company/profile', d) as any,
  // 工厂产线
  factoryList:  () => request.get<CompanyFactory[]>('/company/factory/list') as any,
  factoryGet:   (id: number) => request.get<CompanyFactory>(`/company/factory/${id}`) as any,
  factoryAdd:   (d: Partial<CompanyFactory>) => request.post<number>('/company/factory', d) as any,
  factoryEdit:  (d: Partial<CompanyFactory>) => request.put('/company/factory', d) as any,
  factoryRemove:(id: number) => request.delete(`/company/factory/${id}`) as any,
  // 编码规则
  codingList:    () => request.get<CompanyCodingRule[]>('/company/coding/list') as any,
  codingSave:    (d: Partial<CompanyCodingRule>) => request.post('/company/coding', d) as any,
  codingRemove:  (id: number) => request.delete(`/company/coding/${id}`) as any,
  codingGenerate:(moduleName: string) => request.get<string>(`/company/coding/generate/${moduleName}`) as any,
  // 业务参数
  paramList:   () => request.get<CompanyParam[]>('/company/param/list') as any,
  paramSave:   (d: Partial<CompanyParam>) => request.post('/company/param', d) as any,
  paramRemove: (id: number) => request.delete(`/company/param/${id}`) as any,
}

// ==================== 通知管理（管理端） ====================
export const noticeApi = {
  /** 管理端分页列表 */
  list:   (p: NoticeListQ) => request.get<TableDataInfo<SysNotice>>('/system/notice/list', p) as any,
  /** 管理端详情 */
  get:    (id: number) => request.get<SysNotice>(`/system/notice/${id}`) as any,
  /** 新增（默认草稿） */
  add:    (d: Partial<SysNotice>) => request.post<number>('/system/notice', d) as any,
  /** 修改（不改变发送状态） */
  edit:   (d: Partial<SysNotice>) => request.put<number>('/system/notice', d) as any,
  /** 删除 */
  remove: (id: number) => request.delete<number>(`/system/notice/${id}`) as any,
  /** 发布/撤回 */
  publish:(id: number, publish: boolean) => request.post<number>(`/system/notice/${id}/publish`, null, { params: { publish } }) as any,
  /** 已读人员列表 */
  readList:(id: number) => request.get<SysNoticeRead[]>(`/system/notice/${id}/read`) as any,
}

// ==================== 通知中心（用户端：当前登录人） ====================
export const noticeUserApi = {
  /** 我的消息分页 */
  list:     (p: NoticeUserListQ) => request.get<TableDataInfo<SysNotice>>('/notice/user/list', p) as any,
  /** 未读统计 */
  count:    () => request.get<NoticeCountResp>('/notice/user/count') as any,
  /** 标记单条已读 */
  markRead: (id: number) => request.post<number>(`/notice/user/${id}/read`) as any,
  /** 标记全部已读 */
  markAll:  () => request.post<number>('/notice/user/read-all') as any,
  /** 我的消息详情（自动标记已读） */
  get:      (id: number) => request.get<SysNotice>(`/notice/user/${id}`) as any,
}
