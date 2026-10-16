<template>
  <div class="route-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="路线编码">
          <el-input v-model="q.routeCode" placeholder="PR-xxx" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="路线名称">
          <el-input v-model="q.routeName" placeholder="路线名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="产品编码">
          <el-input v-model="q.productCode" placeholder="FG-xxx" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:110px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工具栏 + 列表 -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title">
            <span class="dot" />
            工艺路线管理
          </div>
          <div>
            <el-button type="primary" :icon="Plus" @click="openAdd">新增工艺</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="routeId" size="default">
        <el-table-column label="路线编码" prop="routeCode" width="180" fixed />
        <el-table-column label="路线名称" prop="routeName" min-width="220" show-overflow-tooltip />
        <el-table-column label="产品" width="200">
          <template #default="{ row }">
            <span class="mono">{{ row.productCode }}</span>
            <span class="muted"> {{ row.productName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="warning" size="small" effect="light">★默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工序" width="70" align="center">
          <template #default="{ row }">
            <el-badge :value="stepCount[row.routeId] || '?'" class="step-badge" />
          </template>
        </el-table-column>
        <el-table-column label="总工时(min)" width="110" align="right">
          <template #default="{ row }">
            <span class="mono">{{ fmt(row.totalCycleMin, 2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总良率" width="100" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.overallYield ? (row.overallYield * 100).toFixed(2) + '%' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">修改</el-button>
            <el-button link type="primary" @click="handleCopy(row)">复制</el-button>
            <el-button link type="warning" v-if="row.status===1" @click="handleDisable(row)">停用</el-button>
            <el-button link type="success" v-else @click="handleEnable(row)">启用</el-button>
            <el-button link type="primary" v-if="row.status===1 && row.isDefault!==1" @click="handleSetDefault(row)">设默认</el-button>
            <el-button link type="danger" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="q.pageNum"
        v-model:page-size="q.pageSize"
        :total="list.data.total || 0"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- 新增/编辑 对话框 -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增工艺路线' : '修改工艺路线'" width="880px" top="5vh" destroy-on-close>
      <el-form :model="editDlg.route" :rules="editRules" ref="editFormRef" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="路线编码" prop="routeCode">
              <el-input v-model="editDlg.route.routeCode" placeholder="PR-YYYYMMDD-NNNN" :disabled="!editDlg.isNew" />
              <el-button v-if="editDlg.isNew" link type="primary" @click="genCode">生成</el-button>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路线名称" prop="routeName">
              <el-input v-model="editDlg.route.routeName" placeholder="如: 主控板 Rev.A 标准工艺路线" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品" prop="productId">
              <el-select v-model="editDlg.route.productId" filterable placeholder="选择产品 (成品/半成品)" style="width:100%"
                         @change="onProductChange">
                <el-option v-for="m in productOptions" :key="m.materialId"
                           :label="`${m.materialCode} - ${m.materialName}`"
                           :value="m.materialId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认工艺">
              <el-switch v-model="editDlg.route.isDefault" :active-value="1" :inactive-value="0" />
              <span class="muted" style="margin-left:8px">同产品仅1条默认</span>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editDlg.route.remark" type="textarea" :rows="1" placeholder="路线说明、车间分配等" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 步骤列表 -->
        <el-divider content-position="left">工艺步骤（按 stepNo 升序）</el-divider>
        <div class="steps-toolbar">
          <el-button size="small" :icon="Plus" type="primary" @click="addStep">添加工序</el-button>
          <el-button size="small" :icon="Delete" type="danger" plain :disabled="!editDlg.route.steps?.length"
                     @click="clearSteps">清空</el-button>
          <el-button size="small" :icon="ArrowUp" :disabled="!canMoveUp" @click="moveStepUp">上移</el-button>
          <el-button size="small" :icon="ArrowDown" :disabled="!canMoveDown" @click="moveStepDown">下移</el-button>
          <span class="muted" v-if="editDlg.route.steps?.length" style="margin-left:auto">
            共 {{ editDlg.route.steps.length }} 步 · 总工时 {{ editTotalMin }} min · 总良率 {{ editYieldPct }}%
          </span>
        </div>

        <el-table :data="editDlg.route.steps || []" border size="small" @selection-change="selSteps">
          <el-table-column type="selection" width="38" />
          <el-table-column label="序号" width="75">
            <template #default="{ row }">
              <el-input-number v-model="row.stepNo" size="small" :min="1" :step="10" controls-position="right"
                               style="width:70px" />
            </template>
          </el-table-column>
          <el-table-column label="工序编码" width="140">
            <template #default="{ row }">
              <el-select v-model="row.stepCode" filterable size="small" style="width:130px">
                <el-option v-for="c in STEP_CODES" :key="c" :label="c" :value="c" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="工序名称" width="140">
            <template #default="{ row }">
              <el-input v-model="row.stepName" size="small" placeholder="如: 锡膏印刷" />
            </template>
          </el-table-column>
          <el-table-column label="工作中心" width="130">
            <template #default="{ row }">
              <el-input v-model="row.workCenter" size="small" placeholder="SMT1线" />
            </template>
          </el-table-column>
          <el-table-column label="工时(min)" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.standardCycleMin" size="small" :min="0" :precision="2" style="width:100px" />
            </template>
          </el-table-column>
          <el-table-column label="人数" width="80" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.standardLaborQty" size="small" :min="0" :max="99" controls-position="right"
                               style="width:60px" />
            </template>
          </el-table-column>
          <el-table-column label="良率" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row._yield" size="small" :min="0" :max="100" :precision="2"
                               controls-position="right" style="width:90px"
                               @change="v => row.targetYieldRate = v / 100" />
            </template>
          </el-table-column>
          <el-table-column label="关键/必检" width="140" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isKeyProcess" type="warning" size="small" effect="plain">关键</el-tag>
              <el-tag v-if="row.isRequiredInspection" type="danger" size="small" effect="plain">必检</el-tag>
              <el-switch v-model="row.isKeyProcess" :active-value="1" :inactive-value="0" size="small" style="margin-right:4px" />
              <el-switch v-model="row.isRequiredInspection" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeStep($index)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" @click="saveRoute">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.show" title="工艺路线详情" size="720px" destroy-on-close>
      <template v-if="detail.route">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="路线编码">{{ detail.route.routeCode }}</el-descriptions-item>
          <el-descriptions-item label="路线名称">{{ detail.route.routeName }}</el-descriptions-item>
          <el-descriptions-item label="产品">
            {{ detail.route.productCode }} / {{ detail.route.productName }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detail.route.status === 1" type="success">启用</el-tag>
            <el-tag v-else type="info">停用</el-tag>
            <el-tag v-if="detail.route.isDefault === 1" type="warning" style="margin-left:6px">★默认</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总工时">{{ detail.route.totalCycleMin }} min</el-descriptions-item>
          <el-descriptions-item label="总良率">{{ (detail.route.overallYield * 100).toFixed(4) }}%</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.route.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">工艺步骤</el-divider>
        <el-timeline>
          <el-timeline-item v-for="(s, idx) in detail.route.steps" :key="s.stepId || idx"
                            :timestamp="`Step ${s.stepNo}`"
                            :color="s.isKeyProcess ? 'red' : 'blue'"
                            placement="top">
            <div class="step-timeline">
              <strong>{{ s.stepCode }} - {{ s.stepName }}</strong>
              <span class="muted" style="margin-left:12px">@ {{ s.workCenter }}</span>
              <el-tag v-if="s.isKeyProcess" type="warning" size="small" effect="light" style="margin-left:8px">关键</el-tag>
              <el-tag v-if="s.isRequiredInspection" type="danger" size="small" effect="light" style="margin-left:4px">必检</el-tag>
              <div class="step-meta">
                工时 <b>{{ s.standardCycleMin }} min</b>
                · 人数 <b>{{ s.standardLaborQty }}</b>
                · 良率 <b>{{ (s.targetYieldRate * 100).toFixed(2) }}%</b>
              </div>
              <div v-if="s.remark" class="muted" style="font-size:12px">{{ s.remark }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { processRouteApi, materialApi } from '@/api'
import type { PdmProcessRoute, PdmProcessStep, PdmMaterial } from '@/types/models'

const STEP_CODES = ['SMT-PRINT', 'SMT-PLACE', 'SMT-REFLOW', 'AOI', 'XRAY', 'ICT', 'FCT', 'FCT-WIFI',
  'DIP-WAVE', 'HAND-WELD', 'ASSEMBLE', 'AGING', 'PACKAGE', 'CALIBRATE', 'TEST']

// ========== 列表 ==========
const q = reactive({ pageNum: 1, pageSize: 20, routeCode: '', routeName: '', productCode: '', productId: null as number | null, status: null as number | null })
const list = reactive<{ loading: boolean; data: { rows: PdmProcessRoute[]; total: number } }>({ loading: false, data: { rows: [], total: 0 } })
const stepCount: Record<number, number> = ref({}).value
const productOptions = ref<PdmMaterial[]>([])

async function loadList() {
  list.loading = true
  try {
    const res: any = await processRouteApi.list(q)
    list.data = res || { rows: [], total: 0 }
    // 统计步骤数: 用详情接口批量取 step 数
    for (const r of list.data.rows) {
      if (stepCount[r.routeId!] === undefined) {
        const d: any = await processRouteApi.get(r.routeId!)
        stepCount[r.routeId!] = (d.steps || []).length
      }
    }
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') }
  finally { list.loading = false }
}
function resetQ() { Object.assign(q, { routeCode: '', routeName: '', productCode: '', productId: null, status: null, pageNum: 1 }); loadList() }

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; saving: boolean; route: PdmProcessRoute }>({
  show: false, isNew: true, saving: false,
  route: { routeCode: '', routeName: '', productId: 0, isDefault: 0, status: 1, steps: [] }
})
const editFormRef = ref()
const editRules = {
  routeCode: [{ required: true, message: '请输入路线编码', trigger: 'blur' }],
  routeName: [{ required: true, message: '请输入路线名称', trigger: 'blur' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }]
}
const selSteps = ref<PdmProcessStep[]>([])

function openAdd() {
  editDlg.isNew = true
  editDlg.route = { routeCode: '', routeName: '', productId: 0, isDefault: 0, status: 1, steps: [] }
  editDlg.show = true
}
async function openEdit(row: PdmProcessRoute) {
  editDlg.isNew = false
  const r: any = await processRouteApi.get(row.routeId!)
  // 把良率从小数变百分比展示
  ;(r.steps || []).forEach((s: any) => { s._yield = (s.targetYieldRate ?? 0.99) * 100 })
  editDlg.route = r
  editDlg.show = true
}
async function openDetail(row: PdmProcessRoute) {
  detail.route = await processRouteApi.get(row.routeId!)
  detail.show = true
}
async function genCode() {
  editDlg.route.routeCode = await processRouteApi.nextCode()
}
function onProductChange(pid: number) {
  const p = productOptions.value.find(m => m.materialId === pid)
  if (p) { editDlg.route.productCode = p.materialCode; editDlg.route.productName = p.materialName }
}

function addStep() {
  const steps = editDlg.route.steps || []
  const last = steps[steps.length - 1]
  const newStepNo = last ? (last.stepNo || 0) + 10 : 10
  steps.push({ stepNo: newStepNo, stepCode: 'SMT-PLACE', stepName: '', standardCycleMin: 0, standardLaborQty: 1, targetYieldRate: 0.99, isKeyProcess: 0, isRequiredInspection: 0, _yield: 99 } as any)
  editDlg.route.steps = steps
}
function removeStep(i: number) { editDlg.route.steps?.splice(i, 1) }
function clearSteps() { editDlg.route.steps = [] }
const canMoveUp = computed(() => selSteps.value.length === 1 && selSteps.value[0].stepNo > 10)
const canMoveDown = computed(() => selSteps.value.length === 1)
function moveStepUp() {
  const s = selSteps.value[0]; if (!s) return
  s.stepNo = Math.max(10, s.stepNo - 10)
  sortSteps()
}
function moveStepDown() {
  const s = selSteps.value[0]; if (!s) return
  s.stepNo = (s.stepNo || 0) + 10
  sortSteps()
}
function sortSteps() { editDlg.route.steps?.sort((a, b) => (a.stepNo || 0) - (b.stepNo || 0)) }

// 实时汇总
const editTotalMin = computed(() => {
  const steps = editDlg.route.steps || []
  return steps.reduce((a, b) => a + (+b.standardCycleMin || 0), 0).toFixed(2)
})
const editYieldPct = computed(() => {
  const steps = editDlg.route.steps || []
  if (!steps.length) return '100.0000'
  const y = steps.reduce((a, b) => a * (+b.targetYieldRate ?? 1), 1)
  return (y * 100).toFixed(4)
})

async function saveRoute() {
  try {
    await editFormRef.value?.validate()
  } catch { return }
  // 清理 steps 中的临时属性, 并排序
  const steps = (editDlg.route.steps || []).sort((a, b) => (a.stepNo || 0) - (b.stepNo || 0))
    .map(({ _yield, stepId, ...rest }: any) => ({ ...rest }))
  editDlg.saving = true
  try {
    if (editDlg.isNew) {
      const id: number = await processRouteApi.add({ ...editDlg.route, steps } as any)
      ElMessage.success('新增成功')
      stepCount[id] = steps.length
    } else {
      await processRouteApi.edit({ ...editDlg.route, steps } as any)
      await processRouteApi.replaceSteps(editDlg.route.routeId!, steps)
      stepCount[editDlg.route.routeId!] = steps.length
      ElMessage.success('保存成功')
    }
    editDlg.show = false
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
  finally { editDlg.saving = false }
}

// ========== 操作 ==========
async function handleRemove(row: PdmProcessRoute) {
  await ElMessageBox.confirm(`确认删除 ${row.routeCode} ${row.routeName}?`, '删除确认', { type: 'warning' })
  try {
    await processRouteApi.remove(row.routeId!)
    ElMessage.success('已删除')
    delete stepCount[row.routeId!]
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}
async function handleDisable(row: PdmProcessRoute) {
  await processRouteApi.disable(row.routeId!)
  ElMessage.success('已停用'); loadList()
}
async function handleEnable(row: PdmProcessRoute) {
  await processRouteApi.enable(row.routeId!)
  ElMessage.success('已启用'); loadList()
}
async function handleSetDefault(row: PdmProcessRoute) {
  await processRouteApi.setDefault(row.routeId!)
  ElMessage.success('已设为默认'); loadList()
}
async function handleCopy(row: PdmProcessRoute) {
  const newName = row.routeName + ' 副本'
  try {
    const { value } = await ElMessageBox.prompt('输入新路线名称', '复制工艺路线', { inputValue: newName })
    const id: number = await processRouteApi.copy(row.routeId!, value)
    stepCount[id] = stepCount[row.routeId!]
    ElMessage.success('复制成功'); loadList()
  } catch {}
}

// ========== 详情抽屉 ==========
const detail = reactive<{ show: boolean; route: PdmProcessRoute | null }>({ show: false, route: null })

// ========== 工具 ==========
function fmt(n: any, d = 2) { return n == null ? '-' : Number(n).toFixed(d) }

onMounted(async () => {
  // 加载产品选项 (所有物料都可以做产品)
  try {
    const res: any = await materialApi.list({ pageNum: 1, pageSize: 200 })
    productOptions.value = res?.rows || []
  } catch {}
  await loadList()
})
</script>

<style scoped>
.route-page { padding: 16px; }
.search-card { margin-bottom: 12px; }
.list-card :deep(.list-header) { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; font-size: 15px; display: flex; align-items: center; gap: 8px; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: var(--el-color-primary); }
.mono { font-family: Consolas, Menlo, monospace; color: #303133; }
.muted { color: #909399; font-size: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
.steps-toolbar { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.step-meta { font-size: 12px; color: #606266; margin-top: 4px; }
.step-timeline { line-height: 1.6; }
.step-badge { :deep(.el-badge__content) { top: 8px; } }
</style>
