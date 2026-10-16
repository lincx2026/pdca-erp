<template>
  <div class="page-scm-return">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="退货单号"><el-input v-model="query.returnNo" placeholder="退货单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="供应商"><el-input v-model="query.supplierName" placeholder="供应商" clearable style="width:180px" /></el-form-item>
      <el-form-item label="退货日期">
        <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="起始" end-placeholder="结束" style="width:240px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:160px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'scm:return:fromStockIn'" @click="openFromStockInDlg">
        <el-icon><DocumentAdd /></el-icon> 从入库单行生成退货草稿
      </el-button>
      <el-button v-hasPermi="'scm:return:edit'" type="success"
                 :disabled="!curRow || !canEdit(curRow)" @click="openDialog(curRow)"><el-icon><Edit /></el-icon> 修改</el-button>
      <el-button v-hasPermi="'scm:return:remove'" type="danger"
                 :disabled="!curRow || !canRemove(curRow)" @click="onRemove(curRow)"><el-icon><Delete /></el-icon> 删除</el-button>
      <el-divider direction="vertical" />
      <el-button v-hasPermi="'scm:return:submit'" type="warning"
                 :disabled="!curRow || Number(curRow.status) !== 0" @click="doSubmit(curRow)"><el-icon><Promotion /></el-icon> 提交</el-button>
      <el-button v-hasPermi="'scm:return:audit'" type="success"
                 :disabled="!curRow || Number(curRow.status) !== 1" @click="doAudit(curRow)"><el-icon><CircleCheck /></el-icon> 审核通过</el-button>
      <el-button v-hasPermi="'scm:return:stockout'" type="primary"
                 :disabled="!curRow || Number(curRow.status) !== 2" @click="doStockOut(curRow)"><el-icon><Box /></el-icon> 生成出库单(旧版)</el-button>
      <el-button><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 主表 -->
    <el-table v-loading="loading" :data="list" stripe border
              highlight-current-row
              @current-change="(r:any) => curRow = r || null">
      <el-table-column prop="returnId" label="ID" width="70" align="right" />
      <el-table-column prop="returnNo" label="退货单号" width="170" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l || String(row.status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="supplier" label="供应商" width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.supplier || row.supplierName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="warehouse" label="仓库" width="140" />
      <el-table-column prop="totalQty" label="退货数量" width="110" align="right">
        <template #default="{ row }">{{ Number(row.totalQty||0).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="totalAmount" label="退货金额" width="130" align="right">
        <template #default="{ row }">{{ row.totalAmount == null ? '-' : '¥' + Number(row.totalAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="returnReason" label="退货原因" width="200" show-overflow-tooltip />
      <el-table-column label="是否补请购" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.hasReplenishment === 1 || row.hasReplenishment === true" type="warning" effect="dark">是</el-tag>
          <el-tag v-else-if="row.hasReplenishment === 0 || row.hasReplenishment === false" type="info">否</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="returnDate" label="退货日期" width="110" />
      <el-table-column prop="applyBy" label="申请人" width="100" />
      <el-table-column prop="applyTime" label="申请时间" width="160" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onViewLines(row)">明细</el-button>
          <el-button v-if="Number(row.status) === 1" v-hasPermi="'scm:return:audit'" size="small" link type="success" @click="doAudit(row)">审核</el-button>
          <el-popconfirm v-if="Number(row.status) === 0" title="确认作废？" @confirm="doCancel(row)">
            <template #reference><el-button size="small" link type="danger">作废</el-button></template>
          </el-popconfirm>
          <el-button size="small" link @click="onView(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper"
                     v-model:current-page="query.pageNum"
                     v-model:page-size="query.pageSize"
                     :total="total"
                     :page-sizes="[20, 50, 100]"
                     @size-change="loadList(1)"
                     @current-change="loadList()" />
    </div>

    <!-- ============== 从入库单行生成退货草稿 ============== -->
    <el-dialog v-model="fromStockInDlg" title="从入库单行生成退货草稿" width="860px" top="8vh" destroy-on-close>
      <el-form :model="fs" label-width="120px">
        <el-form-item label="入库单行" required>
          <el-select v-model="fs.stockInLineIds" multiple filterable collapse-tags collapse-tags-tooltip
                     placeholder="搜索入库单号/物料编码后勾选（多选）"
                     style="width:100%" :loading="stockInLineLoading" @visible-change="onStockInVisible">
            <el-option v-for="l in stockInLineOptions" :key="l.stockInLineId"
                       :label="`[${l.stockInNo}] ${l.materialCode || ('#'+l.materialId)} · ${l.materialName || ''} · 数量=${Number(l.availableQty ?? l.qty ?? 0)} · 库位=${l.warehouseName || l.locationCode || ''}`"
                       :value="l.stockInLineId">
              <div style="display:flex;justify-content:space-between;gap:10px;">
                <span>
                  <el-tag size="small" type="primary" effect="plain">{{ l.stockInNo }}</el-tag>
                  <b class="ml-8">{{ l.materialCode || ('#'+l.materialId) }}</b>
                  <span class="ml-8">{{ l.materialName }}</span>
                </span>
                <span>可退数量: <b class="c-blue">{{ Number(l.availableQty ?? l.qty ?? 0).toLocaleString() }}</b></span>
              </div>
            </el-option>
          </el-select>
          <div style="color:var(--el-text-color-secondary); font-size:12px; margin-top:4px;">
            可多选；需同时满足：数量 > 已退数量；同一草稿的物料需指向同一供应商（后端会校验）。
          </div>
        </el-form-item>
        <el-form-item label="退货原因" required>
          <el-input v-model="fs.returnReason" type="textarea" :rows="2" maxlength="200" placeholder="例如：来料外观不良/功能失效/型号错发..." />
        </el-form-item>
        <el-form-item label="是否补请购">
          <el-switch v-model="fs.hasReplenishment" :active-value="1" :inactive-value="0" />
          <span style="color:var(--el-text-color-secondary); font-size:12px; margin-left:8px;">
            开启后，系统将按退货物料与数量自动生成一条"补请购草稿"，供采购后续下单。
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fromStockInDlg = false">取消</el-button>
        <el-button type="primary" :loading="fsSubmitting" :disabled="!canSubmitFromStockIn" @click="submitFromStockIn">
          确认生成（{{ fs.stockInLineIds.length }} 行）
        </el-button>
      </template>
    </el-dialog>

    <!-- ============== 新增/编辑/详情 ============== -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="110px" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="退货单号" prop="returnNo"><el-input v-model="form.returnNo" placeholder="留空由系统生成" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width:100%">
                <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" filterable clearable placeholder="选择供应商" style="width:100%">
                <el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
                  <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ s.contact }} {{ s.phone }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="供应商" prop="supplier"><el-input v-model="form.supplier" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" filterable clearable placeholder="选择仓库" style="width:100%">
                <el-option v-for="w in warehouses" :key="w.warehouseId" :label="`${w.warehouseCode || ''} ${w.warehouseName || ''}`" :value="w.warehouseId">
                  <span style="float:left">{{ w.warehouseCode }} {{ w.warehouseName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ w.warehouseType || '' }} {{ w.address || '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="仓库" prop="warehouse"><el-input v-model="form.warehouse" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="退货日期" prop="returnDate"><el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="采购订单" prop="poId">
              <el-select v-model="form.poId" filterable clearable placeholder="选择采购订单" style="width:100%">
                <el-option v-for="p in purchaseOrders" :key="p.poId" :label="`${p.poNo || ''} ${p.supplierName || ''}`" :value="p.poId">
                  <span style="float:left">{{ p.poNo }} {{ p.supplierName }}</span>
                  <span style="float:right;color:#999;font-size:12px">#{{ p.poId }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="总数量"><el-input-number v-model="form.totalQty" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="总金额"><el-input-number v-model="form.totalAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="退货原因" prop="returnReason"><el-input v-model="form.returnReason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="是否补请购">
          <el-switch v-model="form.hasReplenishment" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="驳回原因" prop="rejectReason"><el-input v-model="form.rejectReason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============== 行明细 ============== -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="1200px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="退货单号">{{ linesOwner.returnNo || '-' }} #{{ linesOwner.returnId }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ linesOwner.supplier || linesOwner.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="typeof linesOwner.status === 'number'" :type="statusMap[linesOwner.status]?.t || ''">
              {{ statusMap[linesOwner.status]?.l || String(linesOwner.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="退货日期">{{ linesOwner.returnDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退货金额">{{ linesOwner.totalAmount == null ? '-' : '¥' + Number(linesOwner.totalAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="退货原因">{{ linesOwner.returnReason || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider content-position="left">退货明细行 ({{ lines.length }})</el-divider>
      <el-table v-loading="linesLoading" :data="lines" stripe border size="small" max-height="520">
        <el-table-column prop="lineNo" label="行号" width="60" align="center" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格" width="150" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="returnQty" label="退货数量" width="110" align="right">
          <template #default="{ row }">{{ Number(row.returnQty||0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="110" align="right">
          <template #default="{ row }">{{ row.unitPrice == null ? '-' : '¥' + Number(row.unitPrice).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="returnAmount" label="退货金额" width="120" align="right">
          <template #default="{ row }">{{ row.returnAmount == null ? '-' : '¥' + Number(row.returnAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次号" width="130" />
        <el-table-column prop="defectDesc" label="不良说明" min-width="170" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>
      <div class="lines-footer"><span class="lines-total">共 {{ lines.length }} 行</span></div>
      <template #footer><el-button @click="linesDialog = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, getCurrentInstance } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  Search, RefreshLeft, Download, Edit, Delete, Promotion,
  CircleCheck, Box, DocumentAdd
} from '@element-plus/icons-vue'
import { scmReturnApi, scmSupplierApi, warehouseApi, scmPoApi } from '@/api'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'ScmReturnPage' })
const { proxy } = getCurrentInstance() as any
const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function hasPermOrThrow(p: string) { if (!hasPermission(p)) throw new Error('无此操作权限: ' + p); return true }
function todayStr() {
  const d = new Date(); const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0')
  return `${d.getFullYear()}-${m}-${day}`
}

// 状态机
const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l:'草稿', t:'info' },
  1: { l:'已提交', t:'warning' },
  2: { l:'审核通过', t:'primary' },
  3: { l:'出库中', t:'warning' },
  4: { l:'已完成', t:'success' },
  5: { l:'供方签收', t:'' },
  9: { l:'作废/取消', t:'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v,m])=>({ v: Number(v), l: m.l, t: m.t }))
const statusMap = STATUS_LABEL

const EDITABLE_STATUS = new Set<number>([0])
function canEdit(row: any){ return row && EDITABLE_STATUS.has(Number(row.status)) }
function canRemove(row: any){ return row && EDITABLE_STATUS.has(Number(row.status)) }

const query = reactive<any>({
  pageNum: 1, pageSize: 20,
  returnNo: '', supplierName: '', dateRange: [] as string[],
  status: undefined as number|undefined
})
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const curRow = ref<any>(null)

// 下拉选项数据
const suppliers = ref<any[]>([])
const warehouses = ref<any[]>([])
const purchaseOrders = ref<any[]>([])
async function loadOptions() {
  try {
    const [s, w, p] = await Promise.all([
      scmSupplierApi.options(),
      warehouseApi.options(),
      scmPoApi.options()
    ])
    suppliers.value = (s as any)?.data || s || []
    warehouses.value = (w as any)?.data || w || []
    purchaseOrders.value = (p as any)?.data || p || []
  } catch (e: any) {
    // 降级：尝试 list 接口
    try {
      const [s2, w2, p2] = await Promise.all([
        scmSupplierApi.list({ pageNum: 1, pageSize: 1000 } as any),
        warehouseApi.list({ pageNum: 1, pageSize: 1000 } as any),
        scmPoApi.list({ pageNum: 1, pageSize: 1000 } as any)
      ])
      suppliers.value = (s2 as any)?.rows || []
      warehouses.value = (w2 as any)?.rows || []
      purchaseOrders.value = (p2 as any)?.rows || []
    } catch {
      // ignore
    }
  }
}

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const body: any = {
      pageNum: query.pageNum, pageSize: query.pageSize,
      returnNo: query.returnNo || undefined,
      supplierName: query.supplierName || undefined,
      status: query.status
    }
    if (Array.isArray(query.dateRange) && query.dateRange.length === 2) {
      body.startDate = query.dateRange[0]; body.endDate = query.dateRange[1]
    }
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/return/list', 'POST', body)
    else resp = await scmReturnApi.list(body)
    list.value = resp?.rows || []
    total.value = Number(resp?.total) || 0
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '加载失败')
    list.value = []; total.value = 0
  } finally { loading.value = false }
}
function resetQuery() {
  query.returnNo = ''; query.supplierName = ''; query.dateRange = []; query.status = undefined
  loadList(1)
}

// ========== 从入库单行生成 ==========
const fromStockInDlg = ref(false)
const fsSubmitting = ref(false)
const stockInLineLoading = ref(false)
const stockInLineOptions = ref<any[]>([])
const fs = reactive<{
  stockInLineIds: any[], returnReason: string, hasReplenishment: 0|1
}>({
  stockInLineIds: [], returnReason: '', hasReplenishment: 0
})
const canSubmitFromStockIn = computed(() => {
  return fs.stockInLineIds.length > 0 && !!fs.returnReason.trim()
})
async function onStockInVisible(visible: boolean) {
  if (!visible) return
  if (stockInLineOptions.value.length) return
  stockInLineLoading.value = true
  try {
    const resp = await proxy?.$request?.('/scm/stock-in/line-options', 'POST', { pageNum: 1, pageSize: 200 })
    stockInLineOptions.value = (resp?.rows) || (resp?.data?.rows) || []
  } catch (e: any) {
    // 回退：调用 /wms/stock-in/lines 或通用接口
    try {
      const resp2 = await proxy?.$request?.('/scm/stock-in/lines', 'POST', { pageNum: 1, pageSize: 200 })
      stockInLineOptions.value = (resp2?.rows) || (resp2?.data?.rows) || []
    } catch {
      proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '拉取入库单行失败')
    }
  } finally { stockInLineLoading.value = false }
}
function openFromStockInDlg() {
  fs.stockInLineIds = []
  fs.returnReason = ''
  fs.hasReplenishment = 0
  fromStockInDlg.value = true
}
async function submitFromStockIn() {
  if (!canSubmitFromStockIn.value) { ElMessage.warning('请选择入库单行并填写退货原因'); return }
  try {
    await hasPermOrThrow('scm:return:fromStockIn')
    fsSubmitting.value = true
    const resp = await proxy?.$request?.('/scm/return/from-stock-in-lines', 'POST', {
      stockInLineIds: fs.stockInLineIds.map(x => Number(x)),
      returnReason: fs.returnReason,
      hasReplenishment: Number(fs.hasReplenishment) === 1 ? 1 : 0
    })
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '生成失败')
    const data = resp?.data || resp
    const id = data?.returnId ?? (typeof resp === 'number' ? resp : undefined)
    proxy?.$modal?.msgSuccess?.(`生成退货草稿成功${id != null ? '，returnId=' + id : ''}`) ||
      ElMessage.success(`生成退货草稿成功${id != null ? '，returnId=' + id : ''}`)
    fromStockInDlg.value = false; await loadList(1)
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '生成失败')
  } finally { fsSubmitting.value = false }
}

// ========== 主单CRUD ==========
const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => form.returnId ? (readonly.value ? '采购退货详情' : '修改采购退货') : '修改采购退货')
const form = reactive<any>({
  returnId: undefined, returnNo: '', status: 0,
  supplierId: undefined, supplier: '', warehouseId: undefined, warehouse: '',
  returnDate: todayStr(), poId: undefined,
  totalQty: 0, totalAmount: 0,
  returnReason: '', hasReplenishment: 0,
  rejectReason: '', remark: ''
})
function formDefaults() {
  Object.assign(form, {
    returnId: undefined, returnNo: '', status: 0,
    supplierId: undefined, supplier: '', warehouseId: undefined, warehouse: '',
    returnDate: todayStr(), poId: undefined,
    totalQty: 0, totalAmount: 0,
    returnReason: '', hasReplenishment: 0,
    rejectReason: '', remark: ''
  })
}
function openDialog(row?: any) {
  formDefaults()
  if (row?.returnId) { Object.assign(form, row); readonly.value = false }
  else readonly.value = false
  dialog.value = true
}
async function onView(row: any) {
  formDefaults()
  try {
    let detail: any
    if (proxy?.$request) detail = await proxy.$request('/scm/return/get', 'POST', { returnId: row.returnId })
    else detail = await scmReturnApi.get(row.returnId)
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    const payload = { ...form }
    let resp: any
    if (proxy?.$request) {
      resp = await proxy.$request(form.returnId ? '/scm/return/update' : '/scm/return/add', 'POST', payload)
    } else {
      resp = form.returnId ? await scmReturnApi.edit(payload) : await scmReturnApi.add(payload)
    }
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '保存失败')
    proxy?.$modal?.msgSuccess?.('保存成功') || ElMessage.success('保存成功')
    dialog.value = false; await loadList()
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '保存失败')
  } finally { submitLoading.value = false }
}
async function onRemove(row: any) {
  if (!row?.returnId) { ElMessage.warning('请先选中行'); return }
  if (!canRemove(row)) { ElMessage.warning('仅草稿可删除'); return }
  try {
    await hasPermOrThrow('scm:return:remove')
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/return/remove', 'POST', { returnId: row.returnId })
    else resp = await scmReturnApi.remove(row.returnId)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '删除失败')
    proxy?.$modal?.msgSuccess?.('删除成功') || ElMessage.success('删除成功'); await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '删除失败') }
}

// ========== 状态机按钮 ==========
async function doSubmit(row: any) {
  if (!row?.returnId) return
  try {
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/return/submit', 'POST', { returnId: row.returnId })
    else resp = await (scmReturnApi as any).submit(row.returnId)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    proxy?.$modal?.msgSuccess?.('提交成功') || ElMessage.success('提交成功'); await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '提交失败') }
}
async function doAudit(row: any) {
  if (!row?.returnId) return
  try {
    await hasPermOrThrow('scm:return:audit')
    const resp = await proxy?.$request?.(`/scm/return/${row.returnId}/audit-pass`, 'POST', {})
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '审核失败')
    proxy?.$modal?.msgSuccess?.('审核通过') || ElMessage.success('审核通过'); await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '审核失败') }
}
async function doCancel(row: any) {
  if (!row?.returnId) return
  try {
    await hasPermOrThrow('scm:return:cancel')
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/return/cancel', 'POST', { returnId: row.returnId })
    else resp = await (scmReturnApi as any).cancel(row.returnId)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '作废失败')
    proxy?.$modal?.msgSuccess?.('已作废') || ElMessage.success('已作废'); await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '作废失败') }
}
async function doStockOut(row: any) {
  if (!row?.returnId) return
  try {
    await hasPermOrThrow('scm:return:stockout')
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/return/generate-stock-out', 'POST', { returnId: row.returnId })
    else resp = await (scmReturnApi as any).stockOut(row.returnId)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '生成出库单失败')
    const id = resp?.stockOutId ?? resp?.data?.stockOutId
    proxy?.$modal?.msgSuccess?.(`已生成出库单(旧版)${id != null ? '，stockOutId=' + id : ''}`) ||
      ElMessage.success(`已生成出库单(旧版)${id != null ? '，stockOutId=' + id : ''}`)
    await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '生成出库单失败') }
}

// ========== 明细 ==========
const linesDialog = ref(false)
const linesOwner = ref<any>(null)
const lines = ref<any[]>([])
const linesLoading = ref(false)
const linesTitle = computed(() => `退货明细${linesOwner.value?.returnNo ? ' - ' + linesOwner.value.returnNo : ''}`)
async function onViewLines(row: any) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/return/lines', 'POST', { returnId: row.returnId })
    else resp = await scmReturnApi.lines(row.returnId)
    lines.value = (resp?.rows) || []
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '加载明细失败') }
  finally { linesLoading.value = false }
}

onMounted(() => { loadOptions(); loadList() })
</script>

<style scoped>
.page-scm-return { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.lines-header { margin-bottom: 4px; }
.lines-footer { margin-top: 8px; display:flex; justify-content:flex-end; }
.lines-total { color: var(--el-text-color-secondary); font-size: 12px; }
.ml-8 { margin-left: 8px; }
.c-blue { color:#409EFF; font-weight:600; }
</style>
