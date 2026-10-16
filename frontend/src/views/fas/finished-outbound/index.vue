<template>
  <div class="fas-page">
    <!-- 状态概览 -->
    <el-row :gutter="14">
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="待审核" :value="stat.pending">
          <template #prefix><el-icon style="color:var(--el-color-warning)"><Clock /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="草稿/驳回" :value="stat.draft">
          <template #prefix><el-icon style="color:var(--el-color-info)"><EditPen /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="已完成(出库)" :value="stat.done">
          <template #prefix><el-icon style="color:var(--el-color-success)"><CircleCheckFilled /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="累计出库数" :value="stat.qty">
          <template #prefix><el-icon style="color:var(--el-color-danger)"><Promotion /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
    </el-row>

    <!-- 搜索 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="状态">
          <el-select v-model="q.docStatus" placeholder="全部" clearable style="width:140px" @change="loadList">
            <el-option :value="0" label="草稿" />
            <el-option :value="1" label="待审核" />
            <el-option :value="3" label="已完成" />
            <el-option :value="4" label="已驳回" />
          </el-select>
        </el-form-item>
        <el-form-item label="出库单号">
          <el-input v-model="q.docNo" placeholder="WFO 前缀" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="成品名称"><el-input v-model="q.productName" clearable style="width:180px" /></el-form-item>
        <el-form-item label="批次号"><el-input v-model="q.batchNo" clearable style="width:140px" /></el-form-item>
        <el-form-item label="仓库"><el-input v-model="q.warehouse" clearable style="width:120px" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Plus" v-hasPermi="'wh:finished:outbound:add'" @click="openCreate">新建出库单</el-button>
          <el-button :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <template #header><div class="list-header"><div class="title"><span class="dot" />成品出库单</div></div></template>
      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="stockId" size="default">
        <el-table-column label="出库单号" prop="docNo" width="170" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" @click="view(row)">{{ row.docNo || '-' }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.docStatus).type">{{ statusTag(row.docStatus).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成品名称" prop="productName" min-width="200" show-overflow-tooltip />
        <el-table-column label="批次号" prop="batchNo" width="140" />
        <el-table-column label="出库仓库" prop="warehouse" width="120" />
        <el-table-column label="出库数量" align="right" width="110">
          <template #default="{ row }"><span style="color:var(--el-color-danger)">{{ Math.abs(row.flowQty || 0) }}</span></template>
        </el-table-column>
        <el-table-column label="业务类型" width="130">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="审核人" prop="auditBy" width="100" />
        <el-table-column label="审核时间" prop="auditTime" width="160" />
        <el-table-column label="驳回原因" prop="rejectReason" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-if="row.docStatus===0" v-hasPermi="'wh:finished:outbound:edit'" @click="handleSubmit(row)">提交审核</el-button>
            <el-button link type="success" v-if="row.docStatus===1" v-hasPermi="'wh:finished:outbound:audit'" @click="handleAudit(row)">审核通过</el-button>
            <el-button link type="warning" v-if="row.docStatus===1" v-hasPermi="'wh:finished:outbound:audit'" @click="openReject(row)">驳回</el-button>
            <el-button link type="info" @click="view(row)">详情</el-button>
            <el-button link type="danger" v-if="row.docStatus===0 || row.docStatus===4" v-hasPermi="'wh:finished:outbound:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 新建 Dialog -->
    <el-dialog v-model="createDlg.show" title="新建成品出库单" width="540px" destroy-on-close>
      <el-form :model="createDlg.form" label-width="100px" :rules="createRules" ref="createFormRef">
        <el-form-item label="成品名称" prop="productName">
          <el-input v-model="createDlg.form.productName" placeholder="必填" />
        </el-form-item>
        <el-form-item label="产品" prop="productId">
          <el-select v-model="createDlg.form.productId" placeholder="输入编码/名称搜索" filterable style="width:100%" @change="onProductChange">
            <el-option v-for="m in productOptions" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
              <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="批次号"><el-input v-model="createDlg.form.batchNo" placeholder="可选" /></el-form-item>
        <el-form-item label="出库仓库" prop="warehouse"><el-input v-model="createDlg.form.warehouse" placeholder="如 FG-A" /></el-form-item>
        <el-form-item label="出库数量" prop="flowQty"><el-input-number v-model="createDlg.form.flowQty" :min="1" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="createDlg.form.sourceType" style="width:100%">
            <el-option value="SALES_OUT" label="销售出库" />
            <el-option value="SAMPLE" label="样品出库" />
            <el-option value="TRANSFER" label="调拨出库" />
            <el-option value="OTHER" label="其他出库" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="createDlg.form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg.show=false">取消</el-button>
        <el-button type="primary" @click="submitCreate">生成草稿</el-button>
      </template>
    </el-dialog>

    <!-- 详情 Drawer -->
    <el-drawer v-model="detailShow" title="成品出库单详情" size="520px" destroy-on-close>
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="出库单号">{{ detail.docNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusTag(detail.docStatus).type">{{ statusTag(detail.docStatus).text }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="成品名称">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库仓库">{{ detail.warehouse }}</el-descriptions-item>
        <el-descriptions-item label="出库数量">{{ Math.abs(detail.flowQty || 0) }} PCS</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ sourceTypeLabel(detail.sourceType) }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.auditTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="驳回原因">{{ detail.rejectReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- 驳回 Dialog -->
    <el-dialog v-model="rejectDlg.show" title="驳回出库单" width="420px" destroy-on-close>
      <el-form :model="rejectDlg.form" label-width="80px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectDlg.form.reason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDlg.show=false">取消</el-button>
        <el-button type="primary" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, type FormInstance, type FormRules } from 'vue'
import { Search, Refresh, Plus, Clock, EditPen, CircleCheckFilled, Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pmFasApi, materialApi } from '@/api/index'
import type { FasProductStock } from '@/types/models'

const q = reactive({
  pageNum: 1, pageSize: 20,
  docStatus: null as number | null,
  docNo: '', productName: '', batchNo: '', warehouse: ''
})
const list = reactive({ loading: false, data: { rows: [] as FasProductStock[], total: 0 } })
const stat = reactive({ pending: 0, draft: 0, done: 0, qty: 0 })

const statusTag = (s: number | null) => {
  const map: Record<number, { text: string; type: any }> = {
    0: { text: '草稿', type: 'info' },
    1: { text: '待审核', type: 'warning' },
    2: { text: '已审核', type: 'primary' },
    3: { text: '已完成', type: 'success' },
    4: { text: '已驳回', type: 'danger' }
  }
  return map[s ?? -1] || { text: '-', type: 'info' }
}
const sourceTypeLabel = (s: string | null) => {
  const map: Record<string, string> = { SALES_OUT: '销售出库', SALES_RETURN: '销售退货', SAMPLE: '样品出库', TRANSFER: '调拨出库', OTHER: '其他出库' }
  return map[s || ''] || (s || '-')
}

const loadList = async () => {
  list.loading = true
  try {
    const r = await pmFasApi.outboundList({ ...q })
    const rows = r?.rows || []
    list.data = { rows, total: r?.total || 0 }
    let p = 0, d = 0, done = 0, qty = 0
    rows.forEach((r: any) => {
      if (r.docStatus === 1) p++; else if (r.docStatus === 0 || r.docStatus === 4) d++; else if (r.docStatus === 3) { done++; qty += r.qtyOut || 0 }
    })
    stat.pending = p; stat.draft = d; stat.done = done; stat.qty = qty
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败') }
  finally { list.loading = false }
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20; q.docStatus = null; q.docNo = ''; q.productName = ''; q.batchNo = ''; q.warehouse = ''
  loadList()
}

/* 新建 */
const createFormRef = ref<FormInstance>()
const createDlg = reactive({
  show: false,
  form: { productName: '', productId: 1, batchNo: '', warehouse: '', flowQty: 1, sourceType: 'SALES_OUT', remark: '' } as Partial<FasProductStock>
})
const createRules: FormRules = {
  productName: [{ required: true, message: '请填写成品名称', trigger: 'blur' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  warehouse: [{ required: true, message: '请填写出库仓库', trigger: 'blur' }],
  flowQty: [{ required: true, message: '请填写出库数量', trigger: 'blur' }]
}
const openCreate = () => {
  createDlg.form = { productName: '', productId: 1, batchNo: '', warehouse: '', flowQty: 1, sourceType: 'SALES_OUT', remark: '' }
  createDlg.show = true
}
const submitCreate = async () => {
  const ok = await createFormRef.value?.validate().catch(() => false)
  if (!ok) return
  await pmFasApi.outboundCreate(createDlg.form)
  ElMessage.success('已生成出库单草稿')
  createDlg.show = false
  loadList()
}

/* 状态流转 */
const handleSubmit = async (row: FasProductStock) => {
  await ElMessageBox.confirm(`提交单号【${row.docNo}】进入审核？`, '提示', { type: 'warning' })
  await pmFasApi.outboundSubmit(row.stockId!)
  ElMessage.success('已提交审核')
  loadList()
}
const handleAudit = async (row: FasProductStock) => {
  await ElMessageBox.confirm(`审核通过单号【${row.docNo}】，将直接扣减库存，是否继续？`, '审核确认', { type: 'warning' })
  await pmFasApi.outboundAudit(row.stockId!)
  ElMessage.success('审核通过，已完成出库')
  loadList()
}
const rejectDlg = reactive({ show: false, form: { reason: '' } })
const openReject = (row: FasProductStock) => { rejectDlg.show = true; rejectDlg.form.reason = ''; (rejectDlg as any).row = row }
const submitReject = async () => {
  if (!rejectDlg.form.reason.trim()) return ElMessage.warning('请填写驳回原因')
  const row = (rejectDlg as any).row
  await pmFasApi.outboundReject(row.stockId, rejectDlg.form.reason.trim())
  ElMessage.success('已驳回'); rejectDlg.show = false; loadList()
}
const handleRemove = async (row: FasProductStock) => {
  await ElMessageBox.confirm(`确定删除单号【${row.docNo}】？`, '提示', { type: 'warning' })
  await pmFasApi.outboundRemove(row.stockId!)
  ElMessage.success('删除成功'); loadList()
}

const detailShow = ref(false)
const detail = ref<FasProductStock | null>(null)
const view = async (row: FasProductStock) => {
  detail.value = (await pmFasApi.outboundGet(row.stockId!)) || row
  detailShow.value = true
}

// ========== 选项加载 ==========
const productOptions = ref<any[]>([])
async function loadOptions() {
  try {
    const m = await materialApi.list({ pageNum: 1, pageSize: 1000 }) as any
    productOptions.value = m?.rows || []
  } catch { /* ignore */ }
}
function onProductChange(val: any) {
  const m = productOptions.value.find(x => x.materialId === val)
  if (m) createDlg.form.productName = m.materialName
}

onMounted(() => { loadList(); loadOptions() })
</script>
