<template>
  <div class="fas-page">
    <!-- 总览卡片 -->
    <el-row :gutter="14">
      <el-col :span="8">
        <el-card class="stat-card" shadow="never">
          <el-statistic title="总入库数" :value="stat.totalIn">
            <template #prefix><el-icon style="color:var(--el-color-success)"><Download /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card" shadow="never">
          <el-statistic title="总出库数" :value="stat.totalOut">
            <template #prefix><el-icon style="color:var(--el-color-warning)"><Upload /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card" shadow="never">
          <el-statistic title="可用库存" :value="stat.totalAvailable">
            <template #prefix><el-icon style="color:var(--el-color-primary)"><Box /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 组装工单快捷入口 banner -->
    <el-alert v-if="inboundBanner.show" type="success" :closable="true" @close="inboundBanner.show=false"
      show-icon style="margin-top:4px">
      <template #title>
        已接收组装工单入库请求：工单
        <b>{{ inboundBanner.orderNo || '-' }}</b>
        → 批次 <b>{{ inboundBanner.batchNo || '-' }}</b>
        ，下方为对应入库记录。
        <span style="color:var(--el-text-color-secondary); margin-left:8px">
          （方案 C：完工入库单审核页将在"成品仓库第 1 期"上线后替换此处）
        </span>
      </template>
    </el-alert>

    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item v-if="q.orderId || q.orderNo" label="来源工单">
          <span>{{ q.orderNo || q.orderId }}</span>
        </el-form-item>
        <el-form-item :label="pageLabel.productField">
          <el-input v-model="q.productName" placeholder="模糊搜索" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="q.batchNo" placeholder="如 B20240901" clearable @keyup.enter="loadList" style="width:160px" />
        </el-form-item>
        <el-form-item label="仓库">
          <el-input v-model="q.warehouse" placeholder="如 FG-A" clearable @keyup.enter="loadList" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />{{ pageLabel.title }}</div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="stockId" size="default">
        <el-table-column :label="pageLabel.productField" prop="productName" min-width="200" show-overflow-tooltip />
        <el-table-column label="批次号" prop="batchNo" width="140" show-overflow-tooltip />
        <el-table-column label="仓库" prop="warehouse" width="120" show-overflow-tooltip />
        <el-table-column label="入库数" prop="qtyIn" width="100" align="right" />
        <el-table-column label="出库数" prop="qtyOut" width="100" align="right" />
        <el-table-column label="可用库存" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.qtyAvailable || 0) > 0 ? 'var(--el-color-success)' : 'var(--el-color-danger)', fontWeight: 600 }">
              {{ row.qtyAvailable || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="pageLabel.sourceField" width="150">
          <template #default="{ row }">
            <span v-if="pageLabel.isAcc && (row.purchaseNo || row.sourceType)">
              <el-tooltip :content="'业务类型: ' + (row.sourceType || '-')">
                <span>{{ row.purchaseNo || row.sourceType || '-' }}</span>
              </el-tooltip>
            </span>
            <span v-else>{{ row.orderId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="入库时间" prop="inTime" width="160" />
        <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-if="(row.qtyAvailable || 0) > 0" v-hasPermi="permOut" @click="openOut(row)">出库</el-button>
            <el-button link type="info" v-if="!pageLabel.isAcc" @click="openSnLabel(row)">SN标签</el-button>
            <el-button link type="danger" v-hasPermi="permRemove" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 出库 Dialog -->
    <el-dialog v-model="outDlg.show" title="成品出库" width="420px" destroy-on-close>
      <el-form :model="outDlg.form" :rules="outRules" ref="outFormRef" label-width="100px">
        <el-form-item label="成品">
          <span>{{ outDlg.row?.productName }}</span>
        </el-form-item>
        <el-form-item label="批次号">
          <span>{{ outDlg.row?.batchNo || '-' }}</span>
        </el-form-item>
        <el-form-item label="可用库存">
          <span style="color:var(--el-color-success);font-weight:600">{{ outDlg.row?.qtyAvailable || 0 }}</span>
        </el-form-item>
        <el-form-item label="出库数量" prop="qty">
          <el-input-number v-model="outDlg.form.qty" :min="1" :max="outDlg.row?.qtyAvailable || 1" style="width:100%" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outDlg.show = false">取消</el-button>
        <el-button type="primary" @click="submitOut">确定出库</el-button>
      </template>
    </el-dialog>

    <!-- SN 标签 Dialog -->
    <el-dialog v-model="snVisible" title="成品SN标签打印" width="640px" destroy-on-close>
      <SnLabel :row="snRow" :visible="snVisible" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Refresh, Download, Upload, Box } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { pmFasApi } from '@/api/index'
import type { FasProductStock } from '@/types/models'
import SnLabel from '@/views/print/SnLabel.vue'

const route = useRoute()

// ====== 根据路由 meta.scope 区分成品仓(FG) vs 配件仓(ACC) ======
const SCOPE: 'fg' | 'acc' = ((route.meta as any)?.scope === 'acc') ? 'acc' : 'fg'
const pageLabel = computed(() => SCOPE === 'acc'
  ? { isAcc: true, title: '配件库存台账', productField: '配件名称', sourceField: '来源采购/业务' }
  : { isAcc: false, title: '成品库存台账', productField: '成品名称', sourceField: '来源工单' }
)
const permOut    = computed(() => SCOPE === 'acc' ? 'wh:acc:out'    : 'wh:finished:out')
const permRemove = computed(() => SCOPE === 'acc' ? 'wh:acc:remove' : 'wh:finished:remove')
const bannerText  = computed(() => SCOPE === 'acc' ? '配件' : '成品')

// ========== 搜索/列表 ==========
const q = reactive({
  pageNum: 1, pageSize: 20,
  productName: '', batchNo: '', warehouse: '',
  orderId: '' as string, orderNo: '' as string
})
const list = reactive({ loading: false, data: { rows: [] as FasProductStock[], total: 0 } })
const stat = reactive({ totalIn: 0, totalOut: 0, totalAvailable: 0 })
// 快捷入口预填提示 (来自组装工单跳转 —— 仅成品仓生效)
const inboundBanner = reactive<{ show: boolean; orderNo: string; batchNo: string }>({ show: false, orderNo: '', batchNo: '' })

const loadList = async () => {
  list.loading = true
  try {
    const params: any = { ...q }
    const api = SCOPE === 'acc' ? pmFasApi.accStockList : pmFasApi.stockList
    const r = await api(params)
    list.data = { rows: r?.rows || [], total: r?.total || 0 }
    let tIn = 0, tOut = 0, tAvail = 0
    ;(r?.rows || []).forEach((s: FasProductStock) => {
      tIn += s.qtyIn || 0
      tOut += s.qtyOut || 0
      tAvail += s.qtyAvailable || 0
    })
    stat.totalIn = tIn
    stat.totalOut = tOut
    stat.totalAvailable = tAvail
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败')
  } finally { list.loading = false }
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20
  q.productName = ''; q.batchNo = ''; q.warehouse = ''
  q.orderId = ''; q.orderNo = ''
  inboundBanner.show = false
  loadList()
}

// ========== 出库 ==========
const outDlg = reactive<{ show: boolean; row: FasProductStock | null; form: { qty: number } }>({
  show: false, row: null, form: { qty: 1 }
})
const outFormRef = ref<FormInstance>()
const outRules: FormRules = {
  qty: [{ required: true, message: '请填写出库数量', trigger: 'blur' }],
}
const openOut = (row: FasProductStock) => {
  outDlg.row = row
  outDlg.form = { qty: 1 }
  outDlg.show = true
}
const submitOut = async () => {
  const ok = await outFormRef.value?.validate().catch(() => false)
  if (!ok || !outDlg.row) return
  if (outDlg.form.qty > (outDlg.row.qtyAvailable || 0)) {
    ElMessage.warning('出库数量不能超过可用库存')
    return
  }
  try {
    const api = SCOPE === 'acc' ? pmFasApi.accStockOut : pmFasApi.stockOut
    await api(outDlg.row.stockId, outDlg.form.qty)
    ElMessage.success('出库成功')
    outDlg.show = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '出库失败')
  }
}

// ========== 删除 ==========
const handleRemove = async (row: FasProductStock) => {
  try {
    const scopeLabel = pageLabel.value.title
    await ElMessageBox.confirm(`确认删除${scopeLabel}记录【${row.productName || '-'} ${row.batchNo || ''}】？`, '删除确认', { type: 'warning' })
    const api = SCOPE === 'acc' ? pmFasApi.accStockRemove : pmFasApi.stockRemove
    await api(row.stockId)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== SN标签 ==========
const snVisible = ref(false)
const snRow = ref<any>(null)
const openSnLabel = (row: FasProductStock) => {
  snRow.value = { ...row }
  snVisible.value = true
}

onMounted(() => {
  // 来自组装工单跳转的预填
  const q_orderNo = route.query.orderNo as string | undefined
  const q_orderId = route.query.orderId as string | undefined
  const q_batchNo = route.query.batchNo as string | undefined
  if (q_orderNo || q_batchNo) {
    if (q_orderId) q.orderId = q_orderId
    if (q_orderNo) q.orderNo = q_orderNo
    if (q_batchNo) q.batchNo = q_batchNo
    inboundBanner.show = true
    inboundBanner.orderNo = q_orderNo || ''
    inboundBanner.batchNo = q_batchNo || ''
  }
  loadList()
})
</script>

<style scoped>
.fas-page { display: flex; flex-direction: column; gap: 14px; }
.stat-card :deep(.el-card__body) { padding: 18px 20px; }
.search-card :deep(.el-form-item) { margin-bottom: 0; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.pager { margin-top: 14px; justify-content: flex-end; display: flex; }
</style>
