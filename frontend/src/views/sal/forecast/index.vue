<template>
  <div class="page-sal-forecast">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="版本">
        <el-select v-model="query.versionNo" placeholder="全部版本" clearable filterable style="width:200px" @change="loadList(1)">
          <el-option v-for="v in versions" :key="v.versionNo" :label="`${v.versionNo} (${v.versionType === 'W' ? '周' : '月'})`" :value="v.versionNo" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.versionType" placeholder="全部" clearable style="width:100px" @change="loadList(1)">
          <el-option label="周版本" value="W" />
          <el-option label="月版本" value="M" />
        </el-select>
      </el-form-item>
      <el-form-item label="客户">
        <el-select v-model="query.customerId" placeholder="全部客户" clearable filterable style="width:200px" @change="loadList(1)">
          <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:120px" @change="loadList(1)">
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
      <el-button type="primary" v-hasPermi="'sal:forecast:generate'" :loading="generating" @click="openGenerateDialog">
        <el-icon><Plus /></el-icon> 生成新版本
      </el-button>
      <el-button v-hasPermi="'sal:forecast:edit'" :loading="rollingBack" :disabled="!query.versionNo" @click="onRollback">
        <el-icon><Refresh /></el-icon> 回写实际
      </el-button>
      <el-button v-hasPermi="'sal:forecast:audit'" :disabled="!query.versionNo" @click="onAudit">审核生效</el-button>
      <el-button v-hasPermi="'sal:forecast:archive'" :disabled="!query.versionNo" @click="onArchive">归档</el-button>
      <el-button v-hasPermi="'sal:forecast:export'" :loading="exporting" @click="doExport"><el-icon><Download /></el-icon> 导出</el-button>
      <span style="color:var(--el-text-color-secondary); font-size:12px; margin-left:8px;">提示:操作按钮按当前选中版本号执行</span>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="versionNo" label="版本号" width="120" />
      <el-table-column prop="versionType" label="类型" width="70"><template #default="{ row }">{{ row.versionType === 'W' ? '周' : '月' }}</template></el-table-column>
      <el-table-column prop="customerName" label="客户" width="160" show-overflow-tooltip />
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" width="160" show-overflow-tooltip />
      <el-table-column label="周期" width="190"><template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template></el-table-column>
      <el-table-column prop="forecastQty" label="预测量" width="100" align="right"><template #default="{ row }">{{ Number(row.forecastQty || 0).toFixed(2) }}</template></el-table-column>
      <el-table-column prop="forecastAmt" label="预测金额" width="110" align="right"><template #default="{ row }">{{ row.forecastAmt == null ? '-' : '¥' + Number(row.forecastAmt).toFixed(2) }}</template></el-table-column>
      <el-table-column prop="actualQty" label="实际量" width="100" align="right"><template #default="{ row }">{{ Number(row.actualQty || 0).toFixed(2) }}</template></el-table-column>
      <el-table-column prop="hitRate" label="命中率" width="90" align="right">
        <template #default="{ row }">
          <span :style="{ color: hitColor(row.hitRate) }">{{ pct(row.hitRate) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="method" label="算法" width="80" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.t || ''">{{ statusMap[row.status]?.l || String(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" v-hasPermi="'sal:forecast:edit'" size="small" link type="primary" @click="openAdjustDialog(row)">调整量</el-button>
          <el-popconfirm v-if="row.status === 0" title="确认删除该预测行？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'sal:forecast:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <span v-if="row.status !== 0" style="color:var(--el-text-color-secondary); font-size:12px;">—</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-size="query.pageSize" :current-page="query.pageNum" :page-sizes="[10,20,50,100]" @current-change="(p)=>{query.pageNum=p;loadList()}" @size-change="(s)=>{query.pageSize=s;loadList(1)}" style="margin-top:12px;justify-content:flex-end;display:flex" />

    <!-- 生成版本对话框 -->
    <el-dialog v-model="genDialog.visible" title="生成新版本" width="480px">
      <el-form :model="genForm" label-width="100px">
        <el-form-item label="版本类型">
          <el-radio-group v-model="genForm.versionType">
            <el-radio value="W">周版本(W)</el-radio>
            <el-radio value="M">月版本(M)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="未来周期数">
          <el-input-number v-model="genForm.periodsAhead" :min="1" :max="52" :step="1" />
          <span style="color:var(--el-text-color-secondary); font-size:12px; margin-left:8px;">默认12个周期</span>
        </el-form-item>
        <el-alert type="info" :closable="false" style="margin-top:8px;">
          算法: MA-8移动平均(过去8周实际量÷8)<br/>
          仅"过去4周有订单"的客户+物料维度会生成,历史不足跳过避免噪音<br/>
          旧生效版本自动归档(保留历史)
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="genDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="onGenerate">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 调整预测量对话框 -->
    <el-dialog v-model="adjDialog.visible" title="调整预测量" width="480px">
      <el-descriptions :column="1" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="客户">{{ adjRow.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料">{{ adjRow.materialCode }} {{ adjRow.materialName }}</el-descriptions-item>
        <el-descriptions-item label="周期">{{ adjRow.periodStart }} ~ {{ adjRow.periodEnd }}</el-descriptions-item>
        <el-descriptions-item label="原预测量">{{ Number(adjRow.forecastQty || 0).toFixed(2) }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="adjForm" label-width="100px">
        <el-form-item label="新预测量">
          <el-input-number v-model="adjForm.newQty" :min="0" :precision="4" :step="1" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-alert type="info" :closable="false">金额自动重算 = 新量 × 客户专属价(无价则0)</el-alert>
      </el-form>
      <template #footer>
        <el-button @click="adjDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Plus, Refresh, Download } from '@element-plus/icons-vue'
import { salForecastApi, salCustomerApi, materialApi } from '@/api'

const statusOpts = [
  { v: 0, l: '草稿' }, { v: 1, l: '生效' }, { v: 2, l: '已归档' }
]
const statusMap: Record<number, { l: string; t: any }> = {
  0: { l: '草稿', t: 'info' }, 1: { l: '生效', t: 'success' }, 2: { l: '已归档', t: 'danger' }
}

const customers = ref<any[]>([])
const versions = ref<any[]>([])
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const generating = ref(false)
const rollingBack = ref(false)

const query = reactive({ pageNum: 1, pageSize: 20, versionNo: undefined, versionType: undefined, customerId: undefined, status: undefined, customerName: '', materialName: '' } as any)

const genDialog = reactive({ visible: false })
const genForm = reactive({ versionType: 'W', periodsAhead: 12 } as any)

const adjDialog = reactive({ visible: false })
const adjRow = ref<any>({})
const adjForm = reactive({ newQty: 0 } as any)

const fmt = (n: any) => Number(n || 0).toFixed(2)
const pct = (n: any) => {
  const v = Number(n || 0)
  return (v * 100).toFixed(1) + '%'
}
const hitColor = (n: any) => {
  const v = Number(n || 0)
  if (v >= 0.9 && v <= 1.1) return 'var(--el-color-success)'
  if (v < 0.5) return 'var(--el-color-danger)'
  if (v > 1.2) return 'var(--el-color-warning)'
  return 'var(--el-color-info)'
}

async function loadList(page = 0) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const res = await salForecastApi.list({ ...query })
    list.value = res?.rows || []
    total.value = res?.total || 0
  } catch (e: any) { ElMessage.error(e?.message || '查询失败') }
  finally { loading.value = false }
}

function resetQuery() {
  Object.assign(query, { pageNum: 1, versionNo: undefined, versionType: undefined, customerId: undefined, status: undefined, customerName: '', materialName: '' })
  loadList(1)
}

async function loadVersions() {
  try {
    const res = (await salForecastApi.versions()) as any
    versions.value = res?.data || res || []
  } catch (e) { console.error('load versions failed', e) }
}

async function loadOptions() {
  try {
    const c = (await salCustomerApi.options()) as any
    customers.value = c?.data || c || []
  } catch (e) { console.error('load options failed', e) }
}

function openGenerateDialog() {
  Object.assign(genForm, { versionType: 'W', periodsAhead: 12 })
  genDialog.visible = true
}

async function onGenerate() {
  generating.value = true
  try {
    const res = await salForecastApi.generate(genForm.versionType, genForm.periodsAhead)
    const rows = (res as any)?.data ?? res
    ElMessage.success(`生成成功,新增 ${rows} 行预测`)
    genDialog.visible = false
    await loadVersions()
    loadList(1)
  } catch (e: any) { ElMessage.error(e?.message || '生成失败') }
  finally { generating.value = false }
}

async function onRollback() {
  if (!query.versionNo) { ElMessage.warning('请先在版本下拉选择一个版本'); return }
  rollingBack.value = true
  try {
    const res = await salForecastApi.rollback(query.versionNo)
    const n = (res as any)?.data ?? res
    ElMessage.success(`已回写 ${n} 行实际量`)
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '回写失败') }
  finally { rollingBack.value = false }
}

async function onAudit() {
  if (!query.versionNo) { ElMessage.warning('请先在版本下拉选择一个版本'); return }
  await ElMessageBox.confirm(`确认审核版本 ${query.versionNo} 生效(0→1)？`, '审核', { type: 'warning' })
  try { await salForecastApi.audit(query.versionNo); ElMessage.success('已审核生效'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function onArchive() {
  if (!query.versionNo) { ElMessage.warning('请先在版本下拉选择一个版本'); return }
  await ElMessageBox.confirm(`确认归档版本 ${query.versionNo}(1→2)？归档后不可修改,仅保留历史。`, '归档', { type: 'warning' })
  try { await salForecastApi.archive(query.versionNo); ElMessage.success('已归档'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '归档失败') }
}

function openAdjustDialog(row: any) {
  adjRow.value = row
  adjForm.newQty = Number(row.forecastQty || 0)
  adjDialog.visible = true
}

async function onAdjust() {
  saving.value = true
  try {
    await salForecastApi.adjust(adjRow.value.forecastId, adjForm.newQty)
    ElMessage.success('已调整')
    adjDialog.visible = false
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '调整失败') }
  finally { saving.value = false }
}

async function onRemove(row: any) {
  try { await salForecastApi.remove(row.forecastId); ElMessage.success('删除成功'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

async function doExport() {
  exporting.value = true
  try {
    const resp: any = await salForecastApi.export({ ...query, pageNum: 1, pageSize: 10000 } as any)
    saveBlob(resp.data, resp, '销售预测_' + Date.now() + '.xlsx')
  } catch (e: any) { ElMessage.error(e?.message || '导出失败') }
  finally { exporting.value = false }
}
function saveBlob(data: any, resp: any, filename: string) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (resp.headers && resp.headers['content-disposition'] ? decodeURIComponent(resp.headers['content-disposition'].split('filename*=utf-8\'\'')[1] || filename) : filename)
  a.click()
  window.URL.revokeObjectURL(url)
}

onMounted(() => { loadOptions(); loadVersions(); loadList(1) })
</script>

<style scoped>
.page-sal-forecast { padding: 12px; }
.search-form { margin-bottom: 8px; }
.table-toolbar { margin-bottom: 8px; }
</style>
