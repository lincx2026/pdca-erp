<template>
  <div class="page-scm-requisition">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="标题"><el-input v-model="query.title" placeholder="标题" clearable style="width:180px" @keyup.enter="loadList(1)" /></el-form-item>
      <el-form-item label="请购单号"><el-input v-model="query.requisitionNo" placeholder="请购单号" clearable style="width:180px" @keyup.enter="loadList(1)" /></el-form-item>
      <el-form-item label="申请人"><el-input v-model="query.applicantName" placeholder="申请人" clearable style="width:140px" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="请购日期">
        <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="起始" end-placeholder="结束" style="width:240px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'scm:requisition:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增请购</el-button>
      <el-button v-hasPermi="'scm:requisition:edit'" type="success" :disabled="!curRow || !canEdit(curRow)" @click="openDialog(curRow)"><el-icon><Edit /></el-icon> 修改</el-button>
      <el-button v-hasPermi="'scm:requisition:remove'" type="danger" :disabled="!curRow || !canRemove(curRow)" @click="onRemove(curRow)"><el-icon><Delete /></el-icon> 删除</el-button>
      <el-divider direction="vertical" />
      <el-button v-hasPermi="'scm:requisition:submit'" :disabled="!curRow || curRow.status != 0 && curRow.status != 4" @click="doSubmit(curRow)"><el-icon><Promotion /></el-icon> 提交审核</el-button>
      <el-button v-hasPermi="'scm:requisition:audit'" :disabled="!curRow || curRow.status != 1" type="success" @click="doAudit(curRow)"><el-icon><CircleCheck /></el-icon> 审核通过</el-button>
      <el-button v-hasPermi="'scm:requisition:audit'" :disabled="!curRow || curRow.status != 1" type="danger" @click="doReject(curRow)"><el-icon><CircleClose /></el-icon> 审核驳回</el-button>
      <el-divider direction="vertical" />
      <el-button v-hasPermi="'scm:requisition:generateShortfall'" type="warning" @click="openGenerateShortfall"><el-icon><Warning /></el-icon> 生成安全库存短缺单</el-button>
      <el-button v-hasPermi="'scm:requisition:convert'" type="primary" :disabled="!canBatchConvert()" @click="openConvertPoDlg">
        <el-icon><ShoppingCart /></el-icon> 批量转PO
        <span v-if="selection.length" class="badge">{{ convertibleCount }}</span>
      </el-button>
      <el-button><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 主表 -->
    <el-table v-loading="loading" :data="list" stripe border
              highlight-current-row
              @selection-change="onSelectionChange"
              @current-change="(r:any) => curRow = r || null">
      <el-table-column type="selection" width="44" :selectable="(row) => Number(row.status) === 2" />
      <el-table-column prop="requisitionId" label="ID" width="70" align="right" />
      <el-table-column prop="requisitionNo" label="请购单号" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l || String(row.status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="200" show-overflow-tooltip />
      <el-table-column prop="requisitionDate" label="请购日期" width="110" />
      <el-table-column prop="requiredDate" label="需求日期" width="110" />
      <el-table-column prop="applicant" label="申请人" width="100" />
      <el-table-column prop="dept" label="部门" width="120" />
      <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
        <template #default="{ row }">{{ row.totalAmount == null ? '-' : '¥' + Number(row.totalAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="lineCount" label="行数" width="80" align="right" />
      <el-table-column prop="createBy" label="创建人" width="100" />
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onViewLines(row)">明细</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'scm:requisition:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-else size="small" link type="primary" disabled>编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'scm:requisition:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <el-popconfirm v-else title="确认删除？">
            <template #reference><el-button size="small" link type="danger" disabled>删除</el-button></template>
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

    <!-- ================ 新增/编辑/详情 ================ -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="700px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="110px" :disabled="readonly" :rules="formRules">
        <el-form-item label="请购单号" prop="requisitionNo"><el-input v-model="form.requisitionNo" placeholder="留空则系统自动生成" /></el-form-item>
        <el-form-item label="标题" required prop="title"><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="请购日期" prop="requisitionDate"><el-date-picker v-model="form.requisitionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="需求日期" prop="requiredDate"><el-date-picker v-model="form.requiredDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="申请人" prop="applicant"><el-input v-model="form.applicant" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="部门" prop="dept"><el-input v-model="form.dept" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="币种"><el-input v-model="form.currency" placeholder="CNY" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="总金额"><el-input-number v-model="form.totalAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="原因/说明" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ================ 行明细 subDialog ================ -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="1240px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="请购单号">{{ linesOwner.requisitionNo || '-' }} #{{ linesOwner.requisitionId }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ linesOwner.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="typeof linesOwner.status === 'number'" :type="statusMap[linesOwner.status]?.t || ''">
              {{ statusMap[linesOwner.status]?.l || String(linesOwner.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="请购/需求日期">{{ linesOwner.requisitionDate || '-' }} / {{ linesOwner.requiredDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人/部门">{{ linesOwner.applicant || '-' }} / {{ linesOwner.dept || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总金额">{{ linesOwner.totalAmount == null ? '-' : '¥' + Number(linesOwner.totalAmount).toFixed(2) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider content-position="left">
        <span style="display:flex;align-items:center;gap:10px;">
          <span>请购明细行（共 {{ lines.length }} 行）</span>
          <el-button type="primary" size="small" @click="openLineDialog()">新增行</el-button>
        </span>
      </el-divider>
      <el-table v-loading="linesLoading" :data="lines" stripe border size="small" max-height="520">
        <el-table-column prop="line" label="行" width="56" align="center" />
        <el-table-column prop="materialId" label="物料ID" width="80" align="right" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" width="140" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="64" align="center" />
        <el-table-column prop="qty" label="需求数量" width="100" align="right">
          <template #default="{ row }">{{ Number(row.qty||0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="typeof row.status === 'number'" :type="lineStatusMap[row.status]?.t || ''">
              {{ lineStatusMap[row.status]?.l || String(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="supplierId" label="建议供应商" width="120" align="right" />
        <el-table-column prop="convertedPoId" label="已转PO号" width="140" />
        <el-table-column prop="totalConverted" label="累计转单数量" width="120" align="right">
          <template #default="{ row }">{{ Number(row.totalConverted||0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="requiredDate" label="行需求日期" width="110" />
        <el-table-column prop="remark" label="行备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openLineDialog(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="onRemoveLine(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="lines-footer"><span class="lines-total">共 {{ lines.length }} 行</span></div>
      <template #footer>
        <el-button @click="linesDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 行编辑弹窗 -->
    <el-dialog v-model="lineDialog" :title="lineDialogTitle" width="620px" destroy-on-close top="10vh">
      <el-form ref="lineFormRef" :model="lineForm" :rules="lineRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="lineForm.materialId" placeholder="输入编码/名称搜索" filterable style="width:100%" @change="onLineMaterialChange">
            <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
              <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="物料编码"><el-input v-model="lineForm.materialCode" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料名称"><el-input v-model="lineForm.materialName" maxlength="200" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="lineForm.spec" maxlength="200" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="lineForm.unit" maxlength="20" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="需求数量" required prop="qty"><el-input-number v-model="lineForm.qty" :min="0" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="建议供应商"><el-select v-model="lineForm.supplierId" filterable clearable placeholder="选择供应商" style="width:100%">
            <el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
              <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ s.contact }} {{ s.phone }}</span>
            </el-option>
          </el-select></el-form-item></el-col>
        </el-row>
        <el-form-item label="行需求日期"><el-date-picker v-model="lineForm.requiredDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="行备注"><el-input v-model="lineForm.remark" type="textarea" :rows="2" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineDialog = false">取消</el-button>
        <el-button type="primary" :loading="lineSubmitLoading" @click="onLineSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ================ 生成安全库存短缺单 ================ -->
    <el-dialog v-model="shortfallDlg" title="生成安全库存短缺单" width="520px" destroy-on-close top="14vh">
      <el-form :model="shortfallForm" label-width="160px">
        <el-form-item label="需求日期偏移(天)">
          <el-input-number v-model="shortfallForm.requiredDateOffsetDays" :min="1" :max="180" controls-position="right" style="width:200px" />
          <div style="color:var(--el-text-color-secondary); font-size:12px;">生成的请购行，其需求日期 = 今天 + N天</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shortfallDlg = false">取消</el-button>
        <el-button type="primary" :loading="shortfallSubmitting" @click="submitShortfall">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- ================ 批量转PO Dialog ================ -->
    <el-dialog v-model="convertDlg" title="批量转采购订单 (PO)" width="860px" top="8vh" destroy-on-close>
      <div class="convert-wrap">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            将把勾选的 <b class="c-blue">{{ convertibleCount }}</b> 条「已审核(status=2)」请购行（已去重）合并转为 1-N 个采购订单（按供应商分组）。
            请先设置公共头参数；若某行缺少必填字段（如 supplierId / buyerId / requiredDateOffsetDays 等），后端会在 warnings 中返回 missingLineIds。
          </template>
        </el-alert>
        <el-form :model="hd" label-width="150px" class="convert-form" style="margin-top:14px;">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="付款条件" required>
                <el-select v-model="hd.paymentTerm" placeholder="请选择" style="width:100%">
                  <el-option label="月结30天" value="月结30天" />
                  <el-option label="月结45天" value="月结45天" />
                  <el-option label="月结60天" value="月结60天" />
                  <el-option label="月结90天" value="月结90天" />
                  <el-option label="货到付款" value="货到付款" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="币种">
                <el-input v-model="hd.currency" placeholder="CNY" style="width:100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="税率(%)">
                <el-input-number v-model="hd.taxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="交期偏移(天)" required>
                <el-input-number v-model="hd.deliveryDateOffsetDays" :min="1" :max="365" controls-position="right" style="width:100%" />
                <div style="color:var(--el-text-color-secondary); font-size:12px;">PO交付日期 = 请购行 requiredDate + 偏移天数（默认20）</div>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="采购大类">
                <el-select v-model="hd.bizCategory" placeholder="请选择" style="width:100%">
                  <el-option v-for="b in BIZ_CATEGORIES" :key="b.v" :label="b.l" :value="b.v" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="采购员">
                <el-select v-model="hd.buyerId" placeholder="选择采购员" filterable clearable style="width:100%">
                  <el-option v-for="u in userOptions" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="convertDlg = false">取消</el-button>
        <el-button type="primary" :loading="convertSubmitting" :disabled="!canSubmitConvert" @click="submitConvert">
          <el-icon><Check /></el-icon> 确认转PO（{{ convertibleCount }} 行）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, getCurrentInstance } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Search, RefreshLeft, Plus, Download, Edit, Delete, Promotion,
  CircleCheck, CircleClose, Warning, ShoppingCart, Check
} from '@element-plus/icons-vue'
import { scmReqApi, type ScmReqListQ, scmSupplierApi, materialApi, userApi } from '@/api'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'ScmRequisitionPage' })
const { proxy } = getCurrentInstance() as any
const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function hasPermOrThrow(p: string) { if (!hasPermission(p)) throw new Error('无此操作权限: ' + p); return true }

function todayStr() {
  const d = new Date(); const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0')
  return `${d.getFullYear()}-${m}-${day}`
}
function plusDays(ds: string, days: number) {
  if (!ds) return ''
  const d = new Date(ds); d.setDate(d.getDate() + days)
  const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0')
  return `${d.getFullYear()}-${m}-${day}`
}

// 状态机：0草稿 1提交 2已审核 3已转PO 4驳回 9取消
const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l:'草稿', t:'info' },
  1: { l:'已提交', t:'warning' },
  2: { l:'已审核', t:'primary' },
  3: { l:'已转PO', t:'success' },
  4: { l:'已驳回', t:'danger' },
  9: { l:'取消', t:'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v,m])=>({ v: Number(v), l: m.l, t: m.t }))
const statusMap = STATUS_LABEL

const lineStatusMap: Record<number, { l: string; t?: string }> = {
  0: { l:'草稿', t:'info' },
  1: { l:'已提交', t:'warning' },
  2: { l:'已审核', t:'primary' },
  3: { l:'部分转', t:'warning' },
  4: { l:'已转完', t:'success' },
  9: { l:'取消', t:'info' }
}

const BIZ_CATEGORIES = [
  { v: 'ELECTRONIC', l: '电子物料采购' },
  { v: 'ACCESSORY',  l: '配件采购' },
  { v: 'SPARE',      l: '备品备件' }
] as const

const EDITABLE_STATUS = new Set<number>([0, 4])
function canEdit(row: any){ return row && EDITABLE_STATUS.has(Number(row.status)) }
function canRemove(row: any){ return row && EDITABLE_STATUS.has(Number(row.status)) }

// 查询
const query = reactive<any>({
  pageNum: 1, pageSize: 20,
  requisitionNo: '', title: '', applicantName: '',
  status: undefined as number|undefined,
  dateRange: [] as string[]
})
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const selection = ref<any[]>([])
const curRow = ref<any>(null)

// 下拉选项数据
const suppliers = ref<any[]>([])
const materialOptions = ref<any[]>([])
const userOptions = ref<any[]>([])
async function loadOptions() {
  try {
    const [s, m, u] = await Promise.all([
      (async () => { try { const r = (await scmSupplierApi.options()) as any; return r?.data || r || [] } catch { return [] } })(),
      (async () => { try { const r = (await materialApi.list({ pageNum: 1, pageSize: 1000 })) as any; return r?.rows || [] } catch { return [] } })(),
      (async () => { try { const r = (await userApi.options()) as any; return r?.data || r || [] } catch { return [] } })()
    ])
    suppliers.value = Array.isArray(s) ? s : []
    materialOptions.value = Array.isArray(m) ? m : []
    userOptions.value = Array.isArray(u) ? u : []
  } catch { /* ignore */ }
}
function onLineMaterialChange(val: any) {
  const m = materialOptions.value.find(x => x.materialId === val)
  if (m) {
    lineForm.materialCode = m.materialCode
    lineForm.materialName = m.materialName
    lineForm.spec = m.specification
    if (m.unit) lineForm.unit = m.unit
  }
}

function onSelectionChange(rows: any[]) { selection.value = rows }

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const body: any = {
      pageNum: query.pageNum, pageSize: query.pageSize,
      requisitionNo: query.requisitionNo || undefined,
      title: query.title || undefined,
      applicantName: query.applicantName || undefined,
      status: query.status
    }
    if (Array.isArray(query.dateRange) && query.dateRange.length === 2) {
      body.startDate = query.dateRange[0]
      body.endDate = query.dateRange[1]
    }
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/requisition/list', 'POST', body)
    else resp = await scmReqApi.list(body)
    list.value = resp?.rows || []
    total.value = Number(resp?.total) || 0
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '加载失败')
    list.value = []; total.value = 0
  } finally { loading.value = false }
}
function resetQuery() {
  query.requisitionNo = ''; query.title = ''; query.applicantName = ''
  query.status = undefined; query.dateRange = []
  loadList(1)
}

// ========== 主单CRUD ==========
const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => form.requisitionId ? (readonly.value ? '请购详情' : '修改请购') : '新增请购')
const form = reactive<any>({
  requisitionId: undefined, requisitionNo: '', title: '', requisitionDate: todayStr(),
  requiredDate: plusDays(todayStr(), 14), applicant: '', dept: '',
  currency: 'CNY', totalAmount: undefined as number|undefined,
  reason: '', remark: ''
})
const formRules: FormRules = {
  title: [{ required: true, message: '标题必填', trigger: 'blur' }]
}
function formDefaults() {
  Object.assign(form, {
    requisitionId: undefined, requisitionNo: '', title: '',
    requisitionDate: todayStr(), requiredDate: plusDays(todayStr(), 14),
    applicant: '', dept: '', currency: 'CNY', totalAmount: undefined,
    reason: '', remark: ''
  })
}
function openDialog(row?: any) {
  formDefaults()
  if (row?.requisitionId) { Object.assign(form, row); readonly.value = false }
  else readonly.value = false
  dialog.value = true
}
async function onView(row: any) {
  formDefaults()
  try {
    let detail: any
    if (proxy?.$request) detail = await proxy.$request('/scm/requisition/get', 'POST', { requisitionId: row.requisitionId })
    else detail = await scmReqApi.get(row.requisitionId)
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    const payload = { ...form }
    let resp: any
    if (proxy?.$request) {
      resp = await proxy.$request(form.requisitionId ? '/scm/requisition/update' : '/scm/requisition/add', 'POST', payload)
    } else {
      resp = form.requisitionId ? await scmReqApi.edit(payload) : await scmReqApi.add(payload)
    }
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '保存失败')
    proxy?.$modal?.msgSuccess?.(form.requisitionId ? '修改成功' : '新增成功') || ElMessage.success(form.requisitionId ? '修改成功' : '新增成功')
    dialog.value = false; await loadList()
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '保存失败')
  } finally { submitLoading.value = false }
}
async function onRemove(row: any) {
  if (!row || !row.requisitionId) { ElMessage.warning('请先选中行'); return }
  if (!canRemove(row)) { ElMessage.warning('仅草稿/驳回可删除'); return }
  try {
    await hasPermOrThrow('scm:requisition:remove')
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/requisition/remove', 'POST', { requisitionId: row.requisitionId })
    else resp = await scmReqApi.remove(row.requisitionId)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '删除失败')
    proxy?.$modal?.msgSuccess?.('删除成功') || ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '删除失败') }
}

// ========== 状态机按钮 ==========
async function doSubmit(row: any) {
  if (!row?.requisitionId) return
  try {
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/requisition/submit', 'POST', { requisitionId: row.requisitionId })
    else resp = await (scmReqApi as any).submit(row.requisitionId)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    proxy?.$modal?.msgSuccess?.('提交成功') || ElMessage.success('提交成功'); await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '提交失败') }
}
async function doAudit(row: any) {
  if (!row?.requisitionId) return
  try {
    await hasPermOrThrow('scm:requisition:audit')
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/requisition/audit-pass', 'POST', { requisitionId: row.requisitionId })
    else resp = await (scmReqApi as any).audit(row.requisitionId)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    proxy?.$modal?.msgSuccess?.('审核通过') || ElMessage.success('审核通过'); await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '审核失败') }
}
async function doReject(row: any) {
  if (!row?.requisitionId) return
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '审核驳回', { confirmButtonText: '确认驳回', cancelButtonText: '取消', inputPlaceholder: '驳回原因' })
    await hasPermOrThrow('scm:requisition:audit')
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/requisition/audit-reject', 'POST', { requisitionId: row.requisitionId, reason: value || '前端驳回' })
    else resp = await (scmReqApi as any).auditFail(row.requisitionId, value || '前端驳回')
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    proxy?.$modal?.msgSuccess?.('已驳回') || ElMessage.success('已驳回'); await loadList()
  } catch (e: any) {
    if ((e as any) === 'cancel') return
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '驳回失败')
  }
}

// ========== 明细 subDialog ==========
const linesDialog = ref(false)
const linesOwner = ref<any>(null)
const lines = ref<any[]>([])
const linesLoading = ref(false)
const linesTitle = computed(() => `请购明细${linesOwner.value?.requisitionNo ? ' - ' + linesOwner.value.requisitionNo : ''}`)
async function onViewLines(row: any) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/requisition/lines', 'POST', { requisitionId: row.requisitionId })
    else resp = await scmReqApi.lines(row.requisitionId)
    const rows = resp?.rows || []
    lines.value = rows.map((r: any, i: number) => ({
      line: r.lineNo ?? (i+1),
      ...r
    }))
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '加载明细失败') }
  finally { linesLoading.value = false }
}

// 行编辑
const lineDialog = ref(false)
const lineSubmitLoading = ref(false)
const lineFormRef = ref<FormInstance>()
const lineEditingId = ref<number | null>(null)
const lineForm = reactive<any>({
  requisitionId: undefined, lineId: undefined, lineNo: undefined,
  materialId: 1, materialCode: '', materialName: '', spec: '', unit: 'PCS',
  qty: 0, supplierId: undefined, convertedPoId: undefined, totalConverted: 0,
  requiredDate: '', remark: ''
})
const lineRules: FormRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  qty: [{ required: true, validator: (_r: any, v: any, cb: any) => (v != null && Number(v) > 0) ? cb() : cb(new Error('数量需>0')), trigger: 'change' }]
}
const lineDialogTitle = computed(() => lineEditingId.value ? '编辑请购行' : '新增请购行')
function resetLineForm() {
  Object.assign(lineForm, {
    requisitionId: linesOwner.value?.requisitionId, lineId: undefined, lineNo: undefined,
    materialId: 1, materialCode: '', materialName: '', spec: '', unit: 'PCS',
    qty: 0, supplierId: undefined, convertedPoId: undefined, totalConverted: 0,
    requiredDate: linesOwner.value?.requiredDate || plusDays(todayStr(), 14),
    remark: ''
  })
  lineEditingId.value = null
}
function openLineDialog(row?: any) {
  if (!linesOwner.value?.requisitionId) { ElMessage.warning('缺少主单ID'); return }
  resetLineForm()
  if (row) { Object.assign(lineForm, row); lineEditingId.value = row.lineId ?? null }
  lineDialog.value = true
}
async function onLineSubmit() {
  lineSubmitLoading.value = true
  try {
    await lineFormRef.value?.validate()
    const payload = { ...lineForm, requisitionId: linesOwner.value!.requisitionId }
    let resp: any
    if (proxy?.$request) {
      resp = await proxy.$request(lineEditingId.value ? '/scm/requisition/update-line' : '/scm/requisition/add-line', 'POST', payload)
    } else {
      resp = lineEditingId.value
        ? await scmReqApi.editLine({ ...payload, lineId: lineEditingId.value })
        : await scmReqApi.addLine(payload)
    }
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '保存失败')
    proxy?.$modal?.msgSuccess?.(lineEditingId.value ? '修改行成功' : '新增行成功') || ElMessage.success(lineEditingId.value ? '修改行成功' : '新增行成功')
    lineDialog.value = false
    await onViewLines(linesOwner.value!)
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '保存失败')
  } finally { lineSubmitLoading.value = false }
}
async function onRemoveLine(row: any) {
  try { await ElMessageBox.confirm(`确定删除行 ${row.line || row.lineNo || ''} ?`, '提示', { type: 'warning' }) } catch { return }
  try {
    let resp: any
    if (proxy?.$request) resp = await proxy.$request('/scm/requisition/remove-line', 'POST', { lineId: row.lineId })
    else resp = await scmReqApi.removeLine(row.lineId)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '删除失败')
    proxy?.$modal?.msgSuccess?.('删除成功') || ElMessage.success('删除成功'); await onViewLines(linesOwner.value!)
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '删除失败') }
}

// ========== F2. 生成安全库存短缺单 ==========
const shortfallDlg = ref(false)
const shortfallSubmitting = ref(false)
const shortfallForm = reactive({ requiredDateOffsetDays: 14 })
function openGenerateShortfall() { shortfallForm.requiredDateOffsetDays = 14; shortfallDlg.value = true }
async function submitShortfall() {
  try {
    await hasPermOrThrow('scm:requisition:generateShortfall')
    shortfallSubmitting.value = true
    const resp = await proxy?.$request?.('/scm/requisition/generate-shortfall', 'POST', {
      requiredDateOffsetDays: Number(shortfallForm.requiredDateOffsetDays) || 14
    })
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '生成失败')
    const data = resp?.data || resp
    const count = data?.createdCount ?? data?.count ?? data?.requisitionId ?? ''
    proxy?.$modal?.msgSuccess?.(`生成成功${count ? `，数量=${count}` : ''}`) || ElMessage.success(`生成成功${count ? `，数量=${count}` : ''}`)
    shortfallDlg.value = false; await loadList(1)
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '生成失败')
  } finally { shortfallSubmitting.value = false }
}

// ========== F3. 批量转PO ==========
const convertDlg = ref(false)
const convertSubmitting = ref(false)
const convertibleCount = computed(() => selection.value.filter((l: any) => Number(l.status) === 2).length)
function canBatchConvert(): boolean { return convertibleCount.value > 0 }

const hd = reactive<any>({
  paymentTerm: '月结30天', currency: 'CNY',
  taxRate: 13, deliveryDateOffsetDays: 20,
  bizCategory: 'ELECTRONIC', buyerId: undefined as number|undefined
})
const canSubmitConvert = computed(() => {
  if (!hd.paymentTerm) return false
  if (!hd.deliveryDateOffsetDays || Number(hd.deliveryDateOffsetDays) <= 0) return false
  return true
})
function openConvertPoDlg() {
  if (!canBatchConvert()) { ElMessage.warning('需勾选至少一条"已审核(status=2)"的请购行'); return }
  hd.paymentTerm = '月结30天'
  hd.currency = 'CNY'
  hd.taxRate = 13
  hd.deliveryDateOffsetDays = 20
  hd.bizCategory = 'ELECTRONIC'
  hd.buyerId = undefined
  convertDlg.value = true
}
async function submitConvert() {
  if (!canSubmitConvert.value) { ElMessage.warning('请完善公共头参数'); return }
  try {
    convertSubmitting.value = true
    const lineIds = selection.value
      .filter((r: any) => Number(r.status) === 2)
      .map((r: any) => r.lineId ?? r.requisitionLineId ?? r.id)
      .filter((x: any) => x != null)
    if (!lineIds.length) { ElMessage.warning('未获取到有效的请购行ID，可能需要在明细中勾选行'); return }

    const resp = await proxy?.$request?.('/scm/requisition/convert-purchase-order', 'POST', {
      lineIds,
      paymentTerm: hd.paymentTerm,
      currency: hd.currency,
      taxRate: Number(hd.taxRate) || 0,
      deliveryDateOffsetDays: Number(hd.deliveryDateOffsetDays) || 20,
      bizCategory: hd.bizCategory || undefined,
      buyerId: hd.buyerId ? Number(hd.buyerId) : undefined
    })
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '批量转PO失败')
    const data = resp?.data || resp
    const warnings: any[] = data?.warnings || []
    const missing: any[] = data?.missingLineIds || []
    const poIds: any[] = data?.poIds || (data?.poId ? [data.poId] : [])
    let msg = '批量转PO成功'
    if (poIds.length) msg += `，生成PO ${poIds.length} 个`
    if (missing.length) msg += `，缺失字段行数=${missing.length}`
    if (warnings.length) msg += `，warnings=${warnings.length}`
    proxy?.$modal?.msgSuccess?.(msg) || ElMessage.success(msg)
    if (missing.length || warnings.length) {
      setTimeout(() => {
        ElMessageBox.alert(
          'missingLineIds: ' + JSON.stringify(missing.slice(0, 30)) +
          (warnings.length ? ('<br/>warnings: ' + JSON.stringify(warnings.slice(0, 10))) : ''),
          '后端返回提示 (前30)',
          { dangerouslyUseHTMLString: true, type: 'warning' }
        ).catch(()=>{})
      }, 300)
    }
    convertDlg.value = false; await loadList(1)
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '批量转PO失败')
  } finally { convertSubmitting.value = false }
}

onMounted(() => { loadOptions(); loadList() })
</script>

<style scoped>
.page-scm-requisition { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.lines-header { margin-bottom: 4px; }
.lines-footer { margin-top: 8px; display:flex; justify-content:flex-end; }
.lines-total { color: var(--el-text-color-secondary); font-size: 12px; }
.convert-wrap { padding: 0 4px; }
.convert-form { background:#f5f7fa; padding: 12px 14px; border-radius:6px; }
.badge { display:inline-block; min-width:18px; padding:0 6px; margin-left:6px; border-radius:9px; background:#67C23A; color:#fff; font-size:12px; text-align:center; line-height:18px; }
.c-blue { color:#409EFF; font-weight:600; }
</style>
