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
        <el-statistic title="已完成(入库)" :value="stat.done">
          <template #prefix><el-icon style="color:var(--el-color-success)"><CircleCheckFilled /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="累计入库数" :value="stat.qty">
          <template #prefix><el-icon style="color:var(--el-color-primary)"><Box /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
    </el-row>

    <!-- 组装工单跳转过来的提示条 -->
    <el-alert v-if="banner.show" type="success" :closable="true" @close="banner.show=false" show-icon style="margin-top:4px">
      <template #title>
        已为工单 <b>{{ banner.orderNo || '-' }}</b> / 批次 <b>{{ banner.batchNo || '-' }}</b> 生成完工入库单草稿，
        请确认无误后 <b>提交审核</b>，由仓管人员审核通过后自动入库。
      </template>
    </el-alert>

    <!-- 搜索 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item v-if="banner.orderNo" label="来源工单"><span>{{ banner.orderNo }}</span></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.docStatus" placeholder="全部" clearable style="width:140px" @change="loadList">
            <el-option :value="0" label="草稿" />
            <el-option :value="1" label="待审核" />
            <el-option :value="3" label="已完成" />
            <el-option :value="4" label="已驳回" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库单号">
          <el-input v-model="q.docNo" placeholder="WFI 前缀" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="成品名称"><el-input v-model="q.productName" clearable style="width:180px" /></el-form-item>
        <el-form-item label="批次号"><el-input v-model="q.batchNo" clearable style="width:140px" /></el-form-item>
        <el-form-item label="仓库"><el-input v-model="q.warehouse" clearable style="width:120px" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <template #header><div class="list-header"><div class="title"><span class="dot" />完工入库单</div></div></template>
      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="stockId" size="default">
        <el-table-column label="入库单号" prop="docNo" width="170" fixed="left">
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
        <el-table-column label="入库仓库" prop="warehouse" width="120" />
        <el-table-column label="待入库数" align="right" width="110">
          <template #default="{ row }">{{ row.flowQty || 0 }}</template>
        </el-table-column>
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="审核人" prop="auditBy" width="100" />
        <el-table-column label="审核时间" prop="auditTime" width="160" />
        <el-table-column label="创建时间" prop="createTime" width="160" />
        <el-table-column label="驳回原因" prop="rejectReason" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-if="row.docStatus===0" v-hasPermi="'wh:finished:inbound:edit'" @click="handleSubmit(row)">提交审核</el-button>
            <el-button link type="success" v-if="row.docStatus===1" v-hasPermi="'wh:finished:inbound:audit'" @click="handleAudit(row)">审核通过</el-button>
            <el-button link type="warning" v-if="row.docStatus===1" v-hasPermi="'wh:finished:inbound:audit'" @click="openReject(row)">驳回</el-button>
            <el-button link type="info" @click="view(row)">详情</el-button>
            <el-button link type="danger" v-if="row.docStatus===0 || row.docStatus===4" v-hasPermi="'wh:finished:inbound:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 详情 Drawer -->
    <el-drawer v-model="detailShow" title="完工入库单详情" size="520px" destroy-on-close>
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="入库单号">{{ detail.docNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusTag(detail.docStatus).type">{{ statusTag(detail.docStatus).text }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="成品名称">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入库仓库">{{ detail.warehouse }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ Math.abs(detail.flowQty || 0) }} PCS</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ sourceTypeLabel(detail.sourceType) }}</el-descriptions-item>
        <el-descriptions-item label="关联工单">{{ detail.orderId ? ('#' + detail.orderId) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.auditTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="驳回原因">{{ detail.rejectReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- 驳回 Dialog -->
    <el-dialog v-model="rejectDlg.show" title="驳回入库单" width="420px" destroy-on-close>
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Refresh, Clock, EditPen, CircleCheckFilled, Box } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pmFasApi } from '@/api/index'
import type { FasProductStock } from '@/types/models'

const route = useRoute()

const q = reactive({
  pageNum: 1, pageSize: 20,
  docStatus: null as number | null,
  docNo: '', productName: '', batchNo: '', warehouse: ''
})
const list = reactive({ loading: false, data: { rows: [] as FasProductStock[], total: 0 } })
const stat = reactive({ pending: 0, draft: 0, done: 0, qty: 0 })
const banner = reactive({ show: false, orderNo: '', batchNo: '' })

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
  const map: Record<string, string> = { ASSEMBLY_IN: '组装完工入库', SALES_RETURN: '销售退货入库', TRANSFER: '调拨入库', OTHER: '其他入库' }
  return map[s || ''] || (s || '-')
}

const loadList = async () => {
  list.loading = true
  try {
    const r = await pmFasApi.inboundList({ ...q })
    const rows = r?.rows || []
    list.data = { rows, total: r?.total || 0 }
    let p = 0, d = 0, done = 0, qty = 0
    rows.forEach((r: any) => {
      if (r.docStatus === 1) p++; else if (r.docStatus === 0 || r.docStatus === 4) d++; else if (r.docStatus === 3) { done++; qty += r.qtyIn || 0 }
    })
    stat.pending = p; stat.draft = d; stat.done = done; stat.qty = qty
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败')
  } finally { list.loading = false }
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20
  q.docStatus = null; q.docNo = ''; q.productName = ''; q.batchNo = ''; q.warehouse = ''
  banner.show = false
  loadList()
}

const handleSubmit = async (row: FasProductStock) => {
  await ElMessageBox.confirm(`提交单号【${row.docNo}】进入审核？`, '提示', { type: 'warning' })
  await pmFasApi.inboundSubmit(row.stockId!)
  ElMessage.success('已提交审核')
  loadList()
}
const handleAudit = async (row: FasProductStock) => {
  await ElMessageBox.confirm(`审核通过单号【${row.docNo}】，将直接入库并增加库存，是否继续？`, '审核确认', { type: 'success' })
  await pmFasApi.inboundAudit(row.stockId!)
  ElMessage.success('审核通过，已完成入库')
  loadList()
}

const rejectDlg = reactive({ show: false, form: { reason: '' } })
const openReject = (row: FasProductStock) => { rejectDlg.show = true; rejectDlg.form.reason = ''; (rejectDlg as any).row = row }
const submitReject = async () => {
  if (!rejectDlg.form.reason.trim()) return ElMessage.warning('请填写驳回原因')
  const row = (rejectDlg as any).row
  await pmFasApi.inboundReject(row.stockId, rejectDlg.form.reason.trim())
  ElMessage.success('已驳回')
  rejectDlg.show = false
  loadList()
}

const handleRemove = async (row: FasProductStock) => {
  await ElMessageBox.confirm(`确定删除单号【${row.docNo}】？`, '提示', { type: 'warning' })
  await pmFasApi.inboundRemove(row.stockId!)
  ElMessage.success('删除成功')
  loadList()
}

const detailShow = ref(false)
const detail = ref<FasProductStock | null>(null)
const view = async (row: FasProductStock) => {
  detail.value = (await pmFasApi.inboundGet(row.stockId!)) || row
  detailShow.value = true
}

onMounted(() => {
  if (route.query.orderNo || route.query.batchNo) {
    banner.show = true
    banner.orderNo = (route.query.orderNo as string) || ''
    banner.batchNo = (route.query.batchNo as string) || ''
    if (banner.batchNo) q.batchNo = banner.batchNo
  }
  loadList()
})
</script>
