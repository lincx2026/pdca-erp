// 后端返回的数据模型 (类型提示用, 字段按需 subset)
export interface SysDept {
  deptId: number
  parentId: number
  ancestors?: string
  deptName: string
  orderNum?: number
  leader?: string
  phone?: string
  email?: string
  status?: number
  createTime?: string
  updateTime?: string
  children?: SysDept[]
  parent_name?: string
}
export interface SysUser {
  userId: number
  deptId?: number
  userName: string
  nickName: string
  userType?: string
  email?: string
  phonenumber?: string
  sex?: string
  avatar?: string
  status?: number
  loginIp?: string
  loginDate?: string
  createTime?: string
  remark?: string
  dept?: SysDept
  roleIds?: number[]
  postIds?: number[]
  password?: string
  delFlag?: string
}
export interface SysRole {
  roleId: number
  roleName: string
  roleKey: string
  roleSort?: number
  dataScope?: number
  menuCheckStrictly?: number
  deptCheckStrictly?: number
  status?: number
  remark?: string
  createTime?: string
  menuIds?: number[]
  deptIds?: number[]
  permissions?: string[]
}
export interface SysMenu {
  menuId: number
  menuName: string
  parentId: number
  orderNum?: number
  path?: string
  component?: string
  isFrame?: number
  isCache?: number
  menuType?: 'M' | 'C' | 'F'
  visible?: number
  status?: number
  perms?: string
  icon?: string
  remark?: string
  createTime?: string
  children?: SysMenu[]
}
export interface SysOperLog {
  operId: number
  title?: string
  businessType?: number
  method?: string
  requestMethod?: string
  operatorType?: number
  operName?: string
  deptName?: string
  operUrl?: string
  operIp?: string
  operLocation?: string
  operParam?: string
  jsonResult?: string
  status?: number
  errorMsg?: string
  operTime?: string
  costTime?: number
}

// ============== PDM 产品数据管理 ==============
export interface PdmMaterialCategory {
  categoryId: number
  parentId: number
  ancestors?: string
  categoryCode: string   // 分类编码前缀, 如 RES, IC
  categoryName: string
  orderNum?: number
  status?: number        // 1正常 0停用
  remark?: string
  createBy?: string
  createTime?: string
  updateTime?: string
  children?: PdmMaterialCategory[]
  materialCount?: number // 非数据库字段: 该分类下物料数量
}

/**
 * 物料状态机:
 * 0=草稿, 1=审核中, 2=已启用, 3=已失效, 4=已驳回
 */
export type MaterialStatus = 0 | 1 | 2 | 3 | 4

export interface PdmMaterial {
  materialId: number
  materialCode: string      // 编码规则 {categoryCode}-{YY}-{4位流水}
  materialName: string
  specification?: string    // 规格型号
  categoryId: number
  unit?: string             // 计量单位, 默认 PCS
  materialType?: string     // RAW/PCB/RESISTOR/CAPACITOR/IC/CONNECTOR/...
  brand?: string
  mpn?: string              // 原厂料号
  rohsCompliant?: number    // 1合规 0不合规
  storageCondition?: string // 常温/防潮/冷藏/MSD1~3
  validDays?: number        // 有效期天数
  abcClass?: 'A' | 'B' | 'C'
  abcManualFlag?: number
  safetyStockMin?: number | string
  safetyStockMax?: number | string
  leadTimeDays?: number
  minOrderQty?: number | string
  minPackageQty?: number | string
  lossRate?: number | string
  serialTracked?: number    // 1序列号管理 0否
  defaultSupplier1?: number
  defaultSupplier2?: number
  defaultSupplier3?: number
  status?: MaterialStatus
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateTime?: string
  // 非数据库字段
  categoryName?: string
  categoryCode?: string
}

/* ============ W3.3 供应商阶梯价 + 替代料 ============ */
export interface PdmMaterialSupplier {
  id?: number
  materialId: number
  supplierId: number
  supplierName: string
  supplierPn?: string
  mpn?: string
  brand?: string
  priority: number           // 1~9
  moq: number | string
  mpq: number | string
  leadTimeDays: number
  tier1Qty: number | string
  tier1Price: number | string
  tier2Qty?: number | string
  tier2Price?: number | string
  tier3Qty?: number | string
  tier3Price?: number | string
  currency?: string          // CNY/USD/EUR/HKD/JPY
  taxRate?: number | string  // 0~1, 默认 0.13
  effectiveDate?: string
  expiryDate?: string
  isDefault?: 0 | 1
  status?: 0 | 1 | 2
  remark?: string
}
export interface PdmMaterialAlternate {
  id?: number
  groupId?: string
  mainMaterialId: number
  altMaterialId: number
  altType?: 1 | 2 | 3    // 1=完全互替 2=单向 3=条件
  priority?: number
  subRate?: number | string
  status?: 0 | 1 | 2 | 3 | 4
  effectiveDate?: string
  expiryDate?: string
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  remark?: string
  // 列表显示非DB
  mainMaterialCode?: string
  mainMaterialName?: string
  altMaterialCode?: string
  altMaterialName?: string
}

/* ============ W3.4 BOM ============ */
export interface PdmBom {
  bomId: number
  bomCode: string
  bomName: string
  productMaterialId: number
  productMaterialCode?: string
  productMaterialName?: string
  specification?: string
  version: string
  isDefault?: 0 | 1
  quantity?: number | string
  unit?: string
  bomType?: 1 | 2 | 3 | 4 | 5  // 1制造 2工程 3销售 4计划 5替代
  status?: 0 | 1 | 2 | 3 | 4  // 0草稿 1审批中 2启用 3停用 4归档
  effectiveDate?: string
  expiryDate?: string
  ecoNo?: string
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  remark?: string
  deptId?: number
  createBy?: string
  createTime?: string
  updateTime?: string
  // 非DB
  lines?: PdmBomLine[]
  categoryId?: number
}
export interface PdmBomLine {
  lineId?: number
  bomId?: number
  lineNo?: number
  parentMaterialId: number
  componentMaterialId: number
  componentMaterialCode?: string
  componentMaterialName?: string
  componentSpec?: string
  quantity: number | string
  unit?: string
  lossRate?: number | string
  grossQty?: number | string
  altGroupId?: string
  isPhantom?: 0 | 1
  isOptional?: 0 | 1
  supplyType?: 1 | 2 | 3 | 4 | 5   // 1采购 2自制 3委外 4虚拟 5领用
  referenceDesignator?: string
  mfrPartNo?: string
  status?: 0 | 1
  effectiveDate?: string
  expiryDate?: string
  remark?: string
  createTime?: string
  // 非DB
  parentMaterialCode?: string
  parentMaterialName?: string
  children?: PdmBomLine[]
  level?: number
  pathQty?: number | string
}


// ============ 工艺路线 ============
export interface PdmProcessStep {
  stepId?: number
  routeId?: number
  stepNo: number
  stepCode: string
  stepName: string
  workCenter?: string
  standardCycleMin?: number
  standardLaborQty?: number
  targetYieldRate?: number
  isRequiredInspection?: number
  isKeyProcess?: number
  sopFileUrl?: string
  remark?: string
}

export interface PdmProcessRoute {
  routeId?: number
  routeCode: string
  routeName: string
  productId: number
  productCode?: string
  productName?: string
  bomVersionId?: number
  bomVersionCode?: string
  isDefault?: number
  status?: number
  remark?: string
  totalCycleMin?: number
  totalLaborQty?: number
  overallYield?: number
  steps?: PdmProcessStep[]
}

/** 客户料号映射 */
export interface PdmCustomerMaterialMap {
  mapId?: number
  customerId: number
  customerName?: string
  materialId: number
  customerMaterialNo: string
  customerMaterialName?: string
  status?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  // 联表回填
  materialCode?: string
  materialName?: string
  specification?: string
  customerCode?: string
}

// ============ WMS 仓储管理 ============

/** 仓库主档 */
export interface WmsWarehouse {
  warehouseId?: number
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  address?: string
  manager?: string
  phone?: string
  status?: number
  remark?: string
  deptId?: number
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 库存台账 */
export interface WmsInventory {
  inventoryId?: number
  warehouseId: number
  materialId: number
  batchNo?: string
  quantity?: number
  lockedQty?: number
  availableQty?: number
  unit?: string
  status?: number
  warehouseName?: string
  warehouseCode?: string
  materialCode?: string
  materialName?: string
  specification?: string
  updateTime?: string
}

/** 入库单 */
export interface WmsStockIn {
  stockInId?: number
  orderNo: string
  warehouseId: number
  inType: string
  supplierId?: number
  supplierName?: string
  totalQty?: number
  totalAmount?: number
  status?: number
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  remark?: string
  warehouseName?: string
  lines?: WmsStockInLine[]
}

/** 入库明细 */
export interface WmsStockInLine {
  lineId?: number
  stockInId?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  batchNo?: string
  quantity: number
  unit?: string
  unitPrice?: number
  amount?: number
  remark?: string
}

/** 出库单 */
export interface WmsStockOut {
  stockOutId?: number
  orderNo: string
  warehouseId: number
  outType: string
  customerId?: number
  customerName?: string
  totalQty?: number
  status?: number
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  remark?: string
  warehouseName?: string
  lines?: WmsStockOutLine[]
}

/** 出库明细 */
export interface WmsStockOutLine {
  lineId?: number
  stockOutId?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  batchNo?: string
  quantity: number
  unit?: string
  remark?: string
}

// ========================================================
// 阶段三: 生产管理 (PMM)
// ========================================================

/** 生产工单 */
export interface PmmWorkOrder {
  workOrderId?: number
  orderNo: string
  sourceType?: 'MTO' | 'MTS' | 'REWORK' | 'SAMPLE' | string
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  bomId?: number
  routeId?: number
  orderQty: number
  finishedQty?: number
  scrappedQty?: number
  unit?: string
  planStartDate?: string
  planEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  priority?: number
  workCenter?: string
  deptId?: number
  salesmanId?: number
  customerId?: number
  customerName?: string
  saleOrderNo?: string
  /** 0=草稿 10=已下发 20=生产中 30=暂停 40=完工 50=关闭 90=取消 */
  status?: number
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 工单BOM行 */
export interface PmmWorkOrderBom {
  bomLineId?: number
  workOrderId: number
  lineNo?: number
  parentMaterialId?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  qtyPerUnit?: number
  requiredQty?: number
  issuedQty?: number
  returnedQty?: number
  scrapRate?: number
  warehouseId?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 工序任务 */
export interface PmmProcessTask {
  taskId?: number
  workOrderId: number
  stepId?: number
  processCode?: string
  processName: string
  sequence?: number
  workCenter?: string
  assignedUserId?: number
  assignedUser?: string
  planQty?: number
  inputQty?: number
  passQty?: number
  failQty?: number
  cycleTimeSec?: number
  planStartTime?: string
  planEndTime?: string
  actualStartTime?: string
  actualEndTime?: string
  /** 0=待派发 10=已派发 20=加工中 30=暂停 40=已完成 50=已流转 90=取消 */
  status?: number
  isKeyProcess?: number
  isQcRequired?: number
  qcUserId?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 报工记录 */
export interface PmmWorkReport {
  reportId?: number
  reportNo: string
  workOrderId: number
  taskId: number
  processName?: string
  workCenter?: string
  operatorId?: number
  operatorName?: string
  shift?: 'DAY' | 'NIGHT' | 'OT' | string
  reportType?: 'START' | 'END' | 'PAUSE' | 'PROGRESS' | string
  inputQty?: number
  passQty?: number
  failQty?: number
  usedMinutes?: number
  machineCode?: string
  moldCode?: string
  isQcPass?: number
  qcBy?: string
  qcTime?: string
  qcRemark?: string
  defectCodes?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 质量不良 */
export interface PmmQualityDefect {
  defectId?: number
  workOrderId?: number
  taskId?: number
  reportId?: number
  materialId?: number
  materialCode?: string
  processName?: string
  defectCode: string
  defectName?: string
  defectLevel: 'CRITICAL' | 'MAJOR' | 'MINOR' | 'SUGGESTION' | string
  defectQty: number
  sampleQty?: number
  checkType: 'IQC' | 'IPQC' | 'FQC' | 'OQC' | 'SPC' | string
  inspectorId?: number
  inspectorName?: string
  checkTime?: string
  rootCause?: string
  correctiveAction?: string
  preventiveAction?: string
  disposition?: 'PENDING' | 'REWORK' | 'SCRAP' | 'CONCESSION' | 'RETURN' | string
  handledBy?: string
  handleTime?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 排产计划 */
export interface PmmSchedulePlan {
  planId?: number
  scheduleDate: string
  shift?: 'DAY' | 'NIGHT' | 'OT' | string
  workOrderId: number
  taskId?: number
  workCenter?: string
  machineCode?: string
  operatorId?: number
  operatorName?: string
  planQty?: number
  startTime: string
  endTime: string
  isFixed?: number
  sequence?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 生产看板卡片 */
export interface PmmKanbanCard {
  cardId?: number
  workOrderId: number
  taskId?: number
  title: string
  lineNo?: number
  columnNo?: number
  workCenter?: string
  planQty?: number
  passQty?: number
  failQty?: number
  progressPct?: number
  eta?: string
  tags?: string
  dueDate?: string
  backgroundColor?: string
  isFlagged?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 项目管理 */
export interface PmProject {
  projectId?: number
  projectCode?: string
  projectName: string
  customerName?: string
  productName?: string
  manager?: string
  priority?: 'H' | 'M' | 'L' | string
  status?: number
  startDate?: string
  endDate?: string
  actualEndDate?: string
  budget?: number
  description?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 项目附件 */
export interface PmProjectAttachment {
  attachmentId?: number
  projectId?: number
  fileName?: string
  objectKey?: string
  fileSize?: number
  contentType?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 项目分类 */
export interface PmProjectCategory {
  categoryId?: number
  categoryName: string
  parentId?: number
  orderNum?: number
  status?: number
  remark?: string
  children?: PmProjectCategory[]
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 员工档案 */
export interface HrmEmployee {
  employeeId?: number
  employeeNo: string
  name: string
  gender?: number
  phone?: string
  email?: string
  idCard?: string
  deptId?: number
  deptName?: string
  position?: string
  entryDate?: string
  leaveDate?: string
  status?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 员工附件 */
export interface HrmEmployeeAttachment {
  attachmentId?: number
  employeeId?: number
  attachType?: string
  fileName: string
  objectKey?: string
  fileSize?: number
  contentType?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 不良代码字典 */
export interface PmmDefectCode {
  codeId?: number
  defectCode: string
  defectName: string
  defectCategory?: 'SMT' | 'TH' | 'ASSY' | 'TEST' | 'PKG' | string
  defaultLevel?: 'CRITICAL' | 'MAJOR' | 'MINOR' | 'SUGGESTION' | string
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 设备档案 (PMM 生产管理-设备管理) */
export interface PmmEquipment {
  equipmentId?: number
  equipmentNo: string
  equipmentName: string
  /** PRINTER/SPI/PLACEMENT/REFLOW/AOI/WAVE/ICT/FCT/XRAY/OTHER */
  equipmentType: string
  brand?: string
  model?: string
  serialNo?: string
  assetNo?: string
  workshop?: string
  workCenter?: string
  location?: string
  supplierId?: number
  supplierName?: string
  purchaseDate?: string
  warrantyEndDate?: string
  installDate?: string
  /** 0闲置 / 1在用 / 2保养中 / 3维修中 / 4报废 */
  status: number
  /** 1关键 / 2一般 */
  equipLevel: number
  capacityPerHour: number
  capacityUnit?: string
  maintainCycleDays: number
  lastMaintainDate?: string
  nextMaintainDate?: string
  calibrationCycleDays: number
  lastCalDate?: string
  nextCalDate?: string
  operatorIds?: string
  operatorNames?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 设备事件日志 (状态/保养/维修/校准/事故/其他) */
export interface PmmEquipmentLog {
  logId?: number
  equipmentId: number
  equipmentNo: string
  /** STATUS_CHANGE/MAINTAIN/REPAIR/CALIBRATION/ACCIDENT/OTHER */
  eventType: string
  eventTitle: string
  eventTime: string
  /** PASS/FAIL/PENDING */
  eventResult?: string
  oldStatus?: number
  newStatus?: number
  operatorId?: number
  operatorName?: string
  costAmount: number
  attachments?: string
  remark?: string
  createBy?: string
  createTime?: string
}

/** 工装治具档案主表 (PMM 生产管理-工装治具) */
export interface PmmJig {
  jigId?: number
  jigNo: string
  jigName: string
  /** STEEL_STENCIL(钢网)/FCT_JIG(功能测试治具)/ICT_JIG(在线测试治具)/SMT_FIXTURE(SMT贴片治具)/WAVE_JIG(波峰焊治具)/ASSEMBLY_FIXTURE(组装工装)/PRESS_FIT(压合治具)/CUTTER(分板治具)/TORQUE_WRENCH(扭力扳手)/OTHER(其他) */
  jigType: string
  /** 产品机种编码(pdm_product.product_code, 支持模糊/下拉) */
  productCode?: string
  productName?: string
  /** 供应商/外协厂商 */
  supplierId?: number
  supplierName?: string
  brand?: string
  model?: string
  /** 板材/材质(AL5052/电木/SUS304/FR4/Durostone 等) */
  material?: string
  /** 网板厚度/工艺参数(例: 100μm 电铸 / 阶梯0.12) */
  techSpec?: string
  /** 入库/到货 日期 */
  receiveDate?: string
  /** 寿命阈值(使用次数上限, 0=不限) */
  lifeCount: number
  /** 累计使用次数(归还 used_count_add 累加 / 修磨后归零) */
  usedCount: number
  /** 当前总套数(相同 jigno 有多个备份时填写 > 1) */
  qtyCurrent: number
  /** 可用套数(闲置) */
  qtyAvailable: number
  /** 借出中套数(当前占用) */
  qtyBorrowed: number
  /** 0闲置/1在用/2借出/3校准中/4修磨中/5维修中/6报废 */
  status: number
  /** 单套购置成本(元) */
  costAmount?: number
  /** 校准/校验周期(天), 0=无需校准 */
  calCycleDays: number
  lastCalDate?: string
  nextCalDate?: string
  lastMaintainDate?: string
  /** 存放仓库 */
  warehouse?: string
  /** 存放货架/位置 */
  location?: string
  /** 保管人ID(默认库管员) */
  keeperId?: number
  keeperName?: string
  /** 当前领用人ID/姓名 */
  borrowerId?: number
  borrowerName?: string
  /** 图片/附件(JSON/URL 逗号分隔) */
  attachments?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 工装治具动作日志 */
export interface PmmJigLog {
  logId?: number
  jigId: number
  jigNo: string
  /** INBOUND(入库)/BORROW(领用)/RETURN(归还)/CALIBRATE(校准/张力测试)/RESHARPEN(修磨/更换钢片)/REPAIR(维修)/SCRAP(报废)/INSPECT(抽检)/OTHER(其他) */
  eventType: string
  eventTitle: string
  eventTime: string
  /** PASS/FAIL/PENDING */
  eventResult?: string
  /** 借用信息 */
  borrowerId?: number
  borrowerName?: string
  planReturnDate?: string
  realReturnDate?: string
  /** 钢网张力(N/cm)/探针压力(g)/扭力值(N·m) 按类型选择 */
  measureItem?: string
  measureUnit?: string
  measureValue?: number
  measureMin?: number
  measureMax?: number
  /** 归还时累计使用次数(usedCount += usedCountAdd), 用于寿命追踪 */
  usedCountAdd?: number
  /** 本次花费 */
  costAmount?: number
  operatorId?: number
  operatorName?: string
  attachments?: string
  remark?: string
  createBy?: string
  createTime?: string
}

// ========================================================
// 阶段四: 采购管理 (SCM)
// ========================================================

/** 采购申请 */
export interface ScmPurchaseRequisition {
  requisitionId?: number
  requisitionNo: string
  title: string
  applicantId?: number
  applicantName?: string
  deptId?: number
  deptName?: string
  requisitionDate?: string
  requiredDate?: string
  totalAmount?: number
  currency?: string
  taxRate?: number
  reason?: string
  attachFile?: string
  warehouseId?: number
  /** 0=草稿 1=提交 2=已审核 3=已转采购单 4=已驳回 9=取消 */
  status?: number
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 采购申请行 */
export interface ScmPurchaseRequisitionLine {
  lineId?: number
  requisitionId: number
  lineNo: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  qty: number
  estimatePrice?: number
  estimateAmount?: number
  requiredDate?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 采购订单 */
export interface ScmPurchaseOrder {
  poId?: number
  poNo: string
  poType?: string
  requisitionId?: number
  supplierId: number
  supplierCode?: string
  supplierName?: string
  contactName?: string
  contactPhone?: string
  buyerId?: number
  buyerName?: string
  deptId?: number
  poDate?: string
  deliveryDate?: string
  warehouseId?: number
  currency?: string
  exchangeRate?: number
  taxRate?: number
  undiscAmount?: number
  discountRate?: number
  taxAmount?: number
  totalAmount?: number
  paidAmount?: number
  paymentTerm?: string
  deliveryTerm?: string
  transportType?: string
  incAttach?: string
  /** 0=草稿 1=提交 2=已审核 3=已下单 4=部分到货 5=全部到货 6=已完成 7=已驳回 9=取消 */
  status?: number
  signStatus?: number
  signTime?: string
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  closeReason?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 采购订单行 */
export interface ScmPurchaseOrderLine {
  lineId?: number
  poId: number
  lineNo: number
  requisitionLineId?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  orderQty: number
  receivedQty?: number
  returnedQty?: number
  unitPrice?: number
  taxRate?: number
  taxAmount?: number
  lineAmount?: number
  discountRate?: number
  requiredDate?: string
  warehouseId?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 采购到货单 */
export interface ScmDeliveryNote {
  deliveryId?: number
  deliveryNo: string
  poId?: number
  poNo?: string
  supplierId: number
  supplierName?: string
  waybillNo?: string
  carrierName?: string
  vehicleNo?: string
  driverName?: string
  driverPhone?: string
  deliveryDate?: string
  warehouseId: number
  totalQty?: number
  totalAmount?: number
  attachFile?: string
  /** 0=待收货 1=收货中 2=已收货 3=IQC检验中 4=入库完成 5=部分退货 9=取消 */
  status?: number
  receiverId?: number
  receiveTime?: string
  iqcBy?: string
  iqcResult?: number
  iqcReport?: string
  stockInId?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 采购退货 */
export interface ScmPurchaseReturn {
  returnId?: number
  returnNo: string
  poId?: number
  deliveryId?: number
  supplierId: number
  supplierName?: string
  returnDate?: string
  warehouseId: number
  returnReason?: string
  totalQty?: number
  totalAmount?: number
  hasRefund?: number
  refundAmount?: number
  stockOutId?: number
  /** 0=草稿 1=提交 2=审核通过 3=出库中 4=已完成 5=供方已签收 9=取消 */
  status?: number
  auditBy?: string
  auditTime?: string
  signBy?: string
  signTime?: string
  rejectReason?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 采购到货单行项 */
export interface ScmDeliveryNoteLine {
  lineId?: number
  deliveryId: number
  lineNo: number
  poLineId?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  deliveryQty?: number
  acceptedQty?: number
  rejectedQty?: number
  concessionQty?: number
  batchNo?: string
  producedDate?: string
  expireDate?: string
  iqcDefectDesc?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 采购退货单行项 */
export interface ScmPurchaseReturnLine {
  lineId?: number
  returnId: number
  lineNo: number
  deliveryLineId?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  returnQty?: number
  unitPrice?: number
  returnAmount?: number
  batchNo?: string
  defectDesc?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 供应商价格 */
export interface ScmSupplierPrice {
  priceId?: number
  supplierId: number
  materialId: number
  currency?: string
  unitPrice: number
  taxRate?: number
  minQty?: number
  validFrom?: string
  validTo?: string
  quoteSource?: string
  isDefault?: number
  status?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  /** 非数据库字段 (联表回填) */
  materialCode?: string
  materialName?: string
  specification?: string
  supplierCode?: string
  supplierName?: string
}

/** 供应商 */
export interface ScmSupplier {
  supplierId?: number
  supplierCode: string
  supplierName: string
  shortName?: string
  level?: string
  contact?: string
  phone?: string
  email?: string
  address?: string
  /** 1=启用 0=停用 */
  status?: number
  remark?: string
  /* ====== 放账/财务字段 ====== */
  creditDays?: number
  creditLimit?: number
  paymentTerms?: string
  taxNo?: string
  payeeName?: string
  payeeBank?: string
  payeeAccount?: string
  payeeSwift?: string
  /* =========================== */
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/* ========= 应付对账 (AP) 模块 ========= */

/** 应付单 */
export interface ScmApPayable {
  payableId?: number
  payableNo?: string
  bizType?: string
  supplierId: number
  supplierCode?: string
  supplierName?: string
  currency?: string
  exchangeRate?: number
  undiscAmount?: number
  discountAmount?: number
  roundingAmount?: number
  adjustmentAmount?: number
  taxAmount?: number
  totalAmount?: number
  cnyTotalAmount?: number
  paidAmount?: number
  unpaidAmount?: number
  bizDate: string
  creditDays?: number
  dueDate: string
  isOverdue?: number
  overdueDays?: number
  /** 0待对账 1对账中 2对账完成 3部分付 4付款完成 9作废 */
  status?: number
  reconcileBy?: string
  reconcileTime?: string
  signBy?: string
  signTime?: string
  voucherId?: number
  attachment?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 应付行 */
export interface ScmApPayableLine {
  lineId?: number
  payableId: number
  lineNo?: number
  lineSrc?: string
  stockInId?: number; stockInLineId?: number
  stockOutId?: number; stockOutLineId?: number
  deliveryId?: number; deliveryLineId?: number
  returnId?: number; returnLineId?: number
  poId?: number; poLineId?: number
  materialId?: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  qty?: number
  unitPrice?: number
  taxRate?: number
  undiscAmount?: number
  taxAmount?: number
  lineAmount?: number
  batchNo?: string
  producedDate?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 付款计划 */
export interface ScmApPaymentPlan {
  planId?: number
  planNo?: string
  payableId: number
  payableNo?: string
  supplierId: number
  supplierName?: string
  phaseNo?: number
  phaseName?: string
  ratio?: number
  planAmount?: number
  planDate: string
  actualAmount?: number
  unpaidAmount?: number
  /** 0待付 1部分付 2已付清 3付款中 9作废 */
  status?: number
  payBankName?: string
  payBankAccount?: string
  payeeName?: string
  payeeBank?: string
  payeeAccount?: string
  payeeSwift?: string
  flowNo?: string
  auditBy?: string
  auditTime?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 付款单 */
export interface ScmApPayment {
  paymentId?: number
  paymentNo?: string
  supplierId: number
  supplierCode?: string
  supplierName?: string
  payeeName?: string
  payeeBank?: string
  payeeAccount?: string
  payeeSwift?: string
  payBankName?: string
  payBankAccount?: string
  payChannel?: string
  payDate: string
  payCurrency?: string
  payExchangeRate?: number
  totalAmount?: number
  cnyTotalAmount?: number
  feeBearer?: string
  feeAmount?: number
  waterNo?: string
  invoiceNo?: string
  invoiceAmount?: number
  voucherId?: number
  planId?: number
  planPhaseNo?: number
  payableId?: number
  /** 0草稿 1已审批 2付款成功 3付款失败 9作废 */
  status?: number
  auditBy?: string
  auditTime?: string
  payBy?: string
  payTime?: string
  failReason?: string
  attachment?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 财务凭证接口 */
export interface FinVoucherInterface {
  voucherId?: number
  voucherNo?: string
  voucherType?: string
  bizSource?: string
  bizId?: number
  bizNo?: string
  voucherDate: string
  companyCode?: string
  currency?: string
  exchangeRate?: number
  totalDebit?: number
  totalCredit?: number
  totalDebitCny?: number
  totalCreditCny?: number
  voucherLines?: string
  /** 0待推 1推中 2成功 3失败 4已确认 9作废 */
  pushStatus?: number
  pushTime?: string
  pushMsg?: string
  externalVoucherNo?: string
  attachment?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

// ========================================================
// 阶段五: 销售管理 (SAL)
// ========================================================

/** 客户 */
export interface SalCustomer {
  customerId?: number
  customerCode: string
  customerName: string
  shortName?: string
  industry?: string
  level?: string
  contact?: string
  phone?: string
  email?: string
  address?: string
  /** 1=启用 0=停用 */
  status?: number
  remark?: string
  /* ====== 放账/财务字段 ====== */
  creditDays?: number
  creditLimit?: number
  paymentTerms?: string
  taxNo?: string
  payerName?: string
  payerBank?: string
  payerAccount?: string
  /* =========================== */
  salesRepId?: number
  salesRepName?: string
  creditBalance?: number
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 销售订单 */
export interface SalSalesOrder {
  soId?: number
  soNo: string
  soType?: string
  customerId: number
  customerCode?: string
  customerName?: string
  contactName?: string
  contactPhone?: string
  salesRepId?: number
  salesRepName?: string
  deptId?: number
  soDate?: string
  deliveryDate?: string
  warehouseId?: number
  currency?: string
  exchangeRate?: number
  taxRate?: number
  undiscAmount?: number
  discountRate?: number
  taxAmount?: number
  totalAmount?: number
  receivedAmount?: number
  paymentTerm?: string
  deliveryTerm?: string
  transportType?: string
  incAttach?: string
  /** 0=草稿 1=提交 2=已审核 3=部分发货 4=全部发货 5=已完成 6=已驳回 9=取消 */
  status?: number
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  closeReason?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 销售订单行 */
export interface SalSalesOrderLine {
  lineId?: number
  soId: number
  lineNo?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  orderQty: number
  shippedQty?: number
  returnedQty?: number
  unitPrice?: number
  taxRate?: number
  taxAmount?: number
  lineAmount?: number
  discountRate?: number
  requiredDate?: string
  warehouseId?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 销售退货 */
export interface SalSalesReturn {
  returnId?: number
  returnNo: string
  soId?: number
  soNo?: string
  customerId: number
  customerCode?: string
  customerName?: string
  returnDate?: string
  warehouseId?: number
  reason?: string
  currency?: string
  exchangeRate?: number
  totalAmount?: number
  refundAmount?: number
  stockOutId?: number
  /** 0=草稿 1=提交 2=审核通过 3=入库中 4=已完成 5=客户已签收 9=取消 */
  status?: number
  auditBy?: string
  auditTime?: string
  rejectReason?: string
  /** 质检状态 null未启用/0待检/1合格/2部分不良/3全不良 */
  qcStatus?: number | null
  qcBy?: string
  qcTime?: string
  /** 整体处置 ACCEPT入库/RETURN_SUPPLIER退供方/CONCESSION折价/SCRAP报废 */
  disposition?: string
  qcRemark?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 销售退货行 */
export interface SalSalesReturnLine {
  lineId?: number
  returnId: number
  lineNo?: number
  soLineId?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  returnQty: number
  unitPrice?: number
  returnAmount?: number
  reason?: string
  /** 本次合格数(质检录入, 未填默认=returnQty) */
  qcQty?: number
  /** 行处置 ACCEPT/RETURN_SUPPLIER/CONCESSION/SCRAP */
  qcDisposition?: string
  qcRemark?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 应收单 */
export interface SalArReceivable {
  receivableId?: number
  receivableNo?: string
  bizType?: string
  customerId: number
  customerCode?: string
  customerName?: string
  currency?: string
  exchangeRate?: number
  undiscAmount?: number
  discountAmount?: number
  roundingAmount?: number
  adjustmentAmount?: number
  taxAmount?: number
  totalAmount?: number
  cnyTotalAmount?: number
  receivedAmount?: number
  invoicedAmount?: number
  unreceivedAmount?: number
  bizDate: string
  creditDays?: number
  dueDate: string
  isOverdue?: number
  overdueDays?: number
  /** 0待对账 1对账中 2对账完成 3部分收 4收款完成 9作废 */
  status?: number
  reconcileBy?: string
  reconcileTime?: string
  signBy?: string
  signTime?: string
  voucherId?: number
  attachment?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 开票单(蓝字) 0草稿 1已开 2作废 */
export interface SalInvoice {
  invoiceId?: number
  invoiceNo?: string
  invoiceType?: string
  customerId: number
  customerCode?: string
  customerName?: string
  invoiceDate: string
  undiscAmount?: number
  taxAmount?: number
  totalAmount?: number
  currency?: string
  exchangeRate?: number
  cnyTotalAmount?: number
  buyerName?: string
  buyerTaxNo?: string
  buyerAddrPhone?: string
  buyerBankAccount?: string
  redFlag?: number
  sourceInvoiceId?: number
  status?: number
  issueBy?: string
  issueTime?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 开票单明细(关联应收单,部分金额) */
export interface SalInvoiceLine {
  lineId?: number
  invoiceId?: number
  receivableId: number
  receivableNo?: string
  amount?: number
  createBy?: string
  createTime?: string
}

/** 可开票应收单(查询返回) */
export interface InvoiceableReceivable {
  receivableId: number
  receivableNo?: string
  bizDate?: string
  bizType?: string
  totalAmount?: number
  invoicedAmount?: number
  balance?: number
  currency?: string
}

/** 应收行 */
export interface SalArReceivableLine {
  lineId?: number
  receivableId: number
  lineNo?: number
  lineSrc?: string
  stockOutId?: number; stockOutLineId?: number
  soId?: number; soLineId?: number
  returnId?: number; returnLineId?: number
  materialId?: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  qty?: number
  unitPrice?: number
  taxRate?: number
  undiscAmount?: number
  taxAmount?: number
  lineAmount?: number
  batchNo?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 收款计划 */
export interface SalArReceiptPlan {
  planId?: number
  planNo?: string
  receivableId: number
  customerId: number
  customerName?: string
  phaseNo?: number
  planDate: string
  ratio?: number
  planAmount?: number
  actualAmount?: number
  unpaidAmount?: number
  /** 0待收 1部分收 2已收清 3收款中 9作废 */
  status?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 收款单 */
export interface SalArReceipt {
  receiptId?: number
  receiptNo?: string
  customerId: number
  customerCode?: string
  customerName?: string
  payerName?: string
  payerBank?: string
  payerAccount?: string
  payerSwift?: string
  recvBankName?: string
  recvBankAccount?: string
  recvChannel?: string
  receiptDate: string
  receiptCurrency?: string
  exchangeRate?: number
  totalAmount?: number
  cnyTotalAmount?: number
  feeBearer?: string
  feeAmount?: number
  waterNo?: string
  invoiceNo?: string
  invoiceAmount?: number
  voucherId?: number
  planId?: number
  planPhaseNo?: number
  receivableId?: number
  /** 0草稿 1已审批 2收款成功 3收款失败 9作废 */
  status?: number
  auditBy?: string
  auditTime?: string
  receiptBy?: string
  receiptTime?: string
  failReason?: string
  attachment?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

// ============ 销售预测/滚动计划 ============
/** 状态机: 0草稿 → 1生效 → 2已归档(不删,保留历史) */
export interface SalForecast {
  forecastId?: number
  versionNo: string
  versionType: string              // W=周 / M=月
  customerId?: number
  customerName?: string
  materialId?: number
  materialCode?: string
  materialName?: string
  unit?: string
  periodStart: string
  periodEnd: string
  forecastQty: number | string
  forecastAmt: number | string
  actualQty?: number | string
  actualAmt?: number | string
  hitRate?: number | string
  method?: string                  // MA=移动平均 / MANUAL=手工 / SEASON=季节
  status?: number                  // 0草稿 1生效 2已归档
  auditBy?: string
  auditTime?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}
export interface SalForecastVersion {
  versionNo: string
  versionType: string
  periodStart?: string
  periodEnd?: string
  status?: number
}

// ============== ASM 装配管理 ==============
export interface AsmKitting {
  kittingId: number
  kittingNo: string
  workOrderId: number
  workOrderNo?: string
  materialId?: number
  materialCode?: string
  materialName?: string
  bomId?: number
  warehouseId?: number
  kittingType?: string   // FULL/PARTIAL/REWORK
  totalLines?: number
  totalRequiredQty?: number
  totalIssuedQty?: number
  planStartTime?: string
  actualStartTime?: string
  actualEndTime?: string
  operatorId?: number
  operatorName?: string
  status: number         // 0待配料 1配料中 2已配料待发 3已发料 4部分发料 5已退料 6已取消
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

export interface AsmKittingLine {
  kittingLineId: number
  kittingId: number
  lineNo?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  bomLineId?: number
  reqQty?: number
  reservedQty?: number
  pickedQty?: number
  issuedQty?: number
  returnedQty?: number
  scrapQty?: number
  warehouseId?: number
  locationCode?: string
  batchNo?: string
  lockStatus?: number
  lineStatus?: number    // 0待拣 1拣货中 2已拣 3已发 4已退 5缺料
  remark?: string
  createTime?: string
}

// ============== SAL 销售齐套 ==============
export interface SalKitting {
  kittingId: number
  kittingNo: string
  soId: number
  soNo?: string
  soLineId?: number
  customerId?: number
  customerName?: string
  bomId: number
  kitModel?: string
  productMaterialId?: number
  productMaterialCode?: string
  productMaterialName?: string
  kitQty: number
  warehouseId?: number
  totalLines?: number
  totalRequiredQty?: number
  totalAvailableQty?: number
  totalShortageQty?: number
  stockOutId?: number
  requisitionId?: number
  checkTime?: string
  completeTime?: string
  operatorId?: number
  operatorName?: string
  status: number  // 0待检查 1部分齐套 2齐套完成 3已生成出库 4已生成请购 5已取消
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  lines?: SalKittingLine[]
}

export interface SalKittingLine {
  kittingLineId: number
  kittingId: number
  lineNo?: number
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  bomLineId?: number
  reqQty: number
  availableQty: number
  shortageQty: number
  warehouseId?: number
  batchNo?: string
  isShortage: number   // 0齐套 1缺料
  lineStatus: number  // 0待检 1齐套 2缺料 3已出库
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

export interface AsmKittingPick {
  pickId: number
  kittingId: number
  kittingLineId: number
  pickNo?: string
  warehouseId?: number
  locationCode?: string
  materialId: number
  materialCode?: string
  planPickQty?: number
  actualPickQty?: number
  batchNo?: string
  pickStatus?: number    // 0待拣 1拣货中 2已拣 3异常
  pickerId?: number
  pickerName?: string
  pickTime?: string
  remark?: string
}

export interface AsmSn {
  snId: number
  snCode: string
  snType?: string        // RAW原材料批次/WIP半成品SN/FG成品SN/REEL料盘号
  snLevel?: number       // 1原材料 2半成品 3成品
  materialId?: number
  materialCode?: string
  materialName?: string
  specification?: string
  batchNo?: string
  qty?: number
  unit?: string
  supplierId?: number
  supplierName?: string
  certificateNo?: string
  productionDate?: string
  expiryDate?: string
  workOrderId?: number
  workOrderNo?: string
  currentProcess?: string
  currentStatus?: number // 0待生产 1生产中 2在制 3已完工 4不良 5报废 6已出货
  firstSeenTime?: string
  lastSeenTime?: string
  remark?: string
  createTime?: string
}

export interface AsmSnNode {
  nodeId: number
  snId: number
  snCode?: string
  seqNo?: number
  nodeType?: string      // INBOUND/KITTING/LOAD/SMT/REFLOW/AOI/ICT/FUNCTION/REWORK/PACKAGE/OUTBOUND
  processCode?: string
  processName?: string
  equipmentId?: number
  equipmentCode?: string
  equipmentName?: string
  programName?: string
  feederNo?: string
  operatorId?: number
  operatorName?: string
  nodeResult?: string    // OK/NG/REWORK/PASS/FAIL
  defectCode?: string
  defectDesc?: string
  nodeTime?: string
  remark?: string
}

export interface AsmFeederLoad {
  loadId: number
  equipmentId?: number
  equipmentCode: string
  feederNo: string
  slotNo?: number
  materialId: number
  materialCode?: string
  materialName?: string
  reelSn?: string
  reelQty?: number
  batchNo?: string
  programName?: string
  workOrderId?: number
  workOrderNo?: string
  loadTime?: string
  unloadTime?: string
  operatorId?: number
  operatorName?: string
  status?: number        // 0在料 1已换料 2已下料 3报废
  remark?: string
}

// ==================== 后道组装 FasOrder ====================
export interface FasOrder {
  orderId: number
  orderNo: string
  productId: number
  productName: string
  qtyPlan: number
  qtyComplete: number
  qtyNg: number
  status: number  // 0新建 1已下达 2进行中 3已完工 4已入库 5已关闭
  lineName?: string
  planStartDate?: string
  planEndDate?: string
  actualStart?: string
  actualEnd?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  remark?: string
}

export interface FasBom {
  bomId: number
  productId: number
  componentName?: string
  componentType?: string  // PCBA/STRUCTURE/DISPLAY/CABLE/FASTENER/PACKAGING/OTHER
  qtyPer: number
  unit?: string
  needFeedCheck: number  // 1是 0否
  feedSequence: number
  createBy?: string
  createTime?: string
  remark?: string
}

export interface FasStep {
  stepId: number
  orderId: number
  stepNo: number
  stepName: string
  workcenter?: string
  stdTimeMin?: number
  status: number  // 0待开始 1进行中 2已完成 3跳过
  operator?: string
  startTime?: string
  endTime?: string
  qtyPass: number
  qtyNg: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface FasRecord {
  recordId: number
  orderId: number
  stepId?: number
  recordType: string  // FEED/WORK/CHECK/NG
  operator?: string
  eventTime?: string
  batchNo?: string
  componentId?: number
  qtyUsed?: number
  checkResult?: string  // OK/NG
  ngType?: string
  ngDesc?: string
  costAmount?: number
  remark?: string
  createBy?: string
  createTime?: string
}

export interface FasProductStock {
  stockId: number
  productId: number
  productName?: string
  batchNo?: string
  warehouse?: string
  qtyIn: number
  qtyOut: number
  qtyAvailable: number
  orderId?: number
  inTime?: string
  createTime?: string
  remark?: string
}

// ==================== 公司设置 ====================
export interface CompanyProfile {
  profileId: number
  companyName: string
  shortName?: string
  logoUrl?: string
  legalPerson?: string
  contactPhone?: string
  contactEmail?: string
  address?: string
  postalCode?: string
  taxNo?: string
  bankName?: string
  bankAccount?: string
  invoiceTitle?: string
  taxRate?: number
  currency?: string
  decimalPlaces?: number
  updateBy?: string
  updateTime?: string
  remark?: string
}

export interface CompanyFactory {
  factoryId: number
  parentId: number
  factoryCode: string
  factoryName: string
  nodeType: string  // FACTORY/CATEGORY/LINE
  address?: string
  managerName?: string
  managerPhone?: string
  status: number  // 0正常 1停用
  orderNum: number
  remark?: string
  createBy?: string
  createTime?: string
  children?: CompanyFactory[]
}

export interface CompanyCodingRule {
  ruleId: number
  moduleName: string
  moduleDesc?: string
  prefix: string
  dateFormat?: string
  seqLength: number
  currentSeq: number
  separator?: string
  status: number
  description?: string
  createBy?: string
  createTime?: string
}

export interface CompanyParam {
  paramId: number
  paramKey: string
  paramValue?: string
  paramName?: string
  paramType?: string  // STRING/NUMBER/BOOLEAN
  category?: string
  status: number
  remark?: string
  createBy?: string
  createTime?: string
}

// ==================== 通知公告 ====================
/** 通知公告主表 */
export interface SysNotice {
  noticeId?: number
  /** 1=系统公告 2=业务通知 3=预警提醒 4=审批待办 */
  noticeType?: number
  /** 1=普通 2=重要 3=紧急 */
  noticeLevel?: number
  title: string
  content?: string
  summary?: string
  /** 1=全体 2=指定角色 3=指定用户 */
  receiverType?: number
  receiverRoles?: string
  receiverUsers?: string
  relatedType?: string
  relatedId?: string
  actionRoute?: string
  isTop?: number
  /** 0=草稿 1=已发布 2=已撤回 */
  sendStatus?: number
  sendBy?: number
  sendByName?: string
  sendTime?: string
  startTime?: string
  endTime?: string
  createBy?: number
  createTime?: string
  updateBy?: number
  updateTime?: string
  remark?: string
  /** 用户端:是否已读 0/1 */
  readFlag?: number
  /** 管理端:已读人数 */
  readCount?: number
  /** 管理端:未读人数 */
  unreadCount?: number
}

/** 通知已读记录 */
export interface SysNoticeRead {
  readId?: number
  noticeId?: number
  userId?: number
  userName?: string
  isRead?: number
  readTime?: string
}

/** 用户未读统计 */
export interface NoticeCountResp {
  total: number
  type1: number
  type2: number
  type3: number
  type4: number
  [key: string]: any
}

/** 管理端列表查询参数 */
export interface NoticeListQ {
  pageNum: number
  pageSize: number
  noticeType?: number
  noticeLevel?: number
  sendStatus?: number
  title?: string
}

/** 用户端列表查询参数 */
export interface NoticeUserListQ {
  pageNum: number
  pageSize: number
  noticeType?: number
  noticeLevel?: number
  isRead?: number
  keyword?: string
}
