<template>
  <div class="pm-page">
    <!-- Tab 切换: 看板视图 / 列表视图 -->
    <el-card class="tab-card" shadow="never">
      <div class="tab-bar">
        <el-radio-group v-model="viewMode" size="default">
          <el-radio-button value="board">
            <el-icon><Grid /></el-icon>&nbsp;看板视图
          </el-radio-button>
          <el-radio-button value="list">
            <el-icon><List /></el-icon>&nbsp;列表视图
          </el-radio-button>
        </el-radio-group>
        <div class="tab-actions">
          <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
          <el-button type="primary" :icon="Plus" v-hasPermi="'pm:kanban:list'" @click="openAdd">新建卡片</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表视图（带搜索） -->
    <el-card v-if="viewMode === 'list'" class="search-card list-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList" style="margin-bottom:12px">
        <el-form-item label="工单">
          <el-select v-model="q.workOrderId" filterable placeholder="全部" clearable style="width:220px" @change="loadList">
            <el-option v-for="w in workOrderOptions" :key="w.workOrderId" :label="`${w.orderNo || ''} ${w.materialName || ''}`" :value="w.workOrderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="产线">
          <el-input-number v-model="q.lineNo" :min="0" controls-position="right" @change="loadList" style="width:120px" />
        </el-form-item>
        <el-form-item label="工作中心">
          <el-input v-model="q.workCenter" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="q.title" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="标记">
          <el-select v-model="q.isFlagged" placeholder="全部" clearable style="width:110px" @change="loadList">
            <el-option label="已标记" :value="1" />
            <el-option label="未标记" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="cardId" size="default">
        <el-table-column label="ID" prop="cardId" width="70" align="center" />
        <el-table-column label="标题" prop="title" min-width="180" show-overflow-tooltip />
        <el-table-column label="产线" prop="lineNo" width="70" align="center" />
        <el-table-column label="列" prop="columnNo" width="70" align="center" />
        <el-table-column label="工单ID" prop="workOrderId" width="90" align="center" />
        <el-table-column label="任务ID" prop="taskId" width="80" align="center" />
        <el-table-column label="工作中心" prop="workCenter" width="110" />
        <el-table-column label="计划数量" prop="planQty" width="90" align="right" />
        <el-table-column label="合格" prop="passQty" width="80" align="right">
          <template #default="{ row }"><span class="ok">{{ row.passQty || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="不良" prop="failQty" width="80" align="right">
          <template #default="{ row }"><span v-if="row.failQty" class="bad">{{ row.failQty }}</span><span v-else>0</span></template>
        </el-table-column>
        <el-table-column label="进度(%)" width="110">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.progressPct || 0)" :stroke-width="12" />
          </template>
        </el-table-column>
        <el-table-column label="预计完成" prop="eta" width="110" />
        <el-table-column label="到期日" prop="dueDate" width="110" />
        <el-table-column label="标记" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isFlagged === 1" type="danger" size="small">★</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'pm:kanban:list'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" v-hasPermi="'pm:kanban:list'" @click="openMove(row)">移动</el-button>
            <el-button link type="danger" v-hasPermi="'pm:kanban:list'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 看板视图 -->
    <div v-else class="kanban-board" v-loading="board.loading">
      <div v-for="col in columns" :key="col.no" class="kanban-col">
        <div class="kanban-col-header" :style="{ background: col.bg }">
          <span class="col-title">{{ col.title }}</span>
          <el-tag size="small" type="info">{{ cardsByCol[col.no]?.length || 0 }}</el-tag>
        </div>
        <div class="kanban-col-body">
          <div v-for="card in cardsByCol[col.no] || []" :key="card.cardId"
               class="kanban-card" :style="cardStyle(card)"
               @click="openEdit(card)">
            <div class="card-header">
              <span class="card-title">{{ card.title }}</span>
              <el-tag v-if="card.isFlagged===1" type="danger" size="small" effect="dark">★</el-tag>
            </div>
            <div class="card-meta">
              <span>WO#{{ card.workOrderId }}</span>
              <span v-if="card.workCenter">{{ card.workCenter }}</span>
            </div>
            <el-progress class="card-progress" :percentage="Math.round(card.progressPct || 0)" :stroke-width="8"
              :color="progressColor(card.progressPct)" />
            <div class="card-qty">
              <span class="ok">✓ {{ card.passQty || 0 }}</span>
              <span class="bad" v-if="card.failQty">✗ {{ card.failQty }}</span>
              <span class="muted">/ {{ card.planQty || 0 }}</span>
            </div>
            <div class="card-footer">
              <span v-if="card.eta" class="muted">ETA: {{ card.eta }}</span>
              <span v-if="card.tags" class="card-tags">{{ card.tags }}</span>
            </div>
          </div>
          <div v-if="!cardsByCol[col.no]?.length" class="col-empty">— 暂无卡片 —</div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑 对话框 -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新建看板卡片' : '编辑看板卡片'" width="640px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="标题" prop="title">
              <el-input v-model="editDlg.form.title" placeholder="卡片标题，用于看板显示" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工单" prop="workOrderId">
              <el-select v-model="editDlg.form.workOrderId" filterable placeholder="选择工单" style="width:100%">
                <el-option v-for="w in workOrderOptions" :key="w.workOrderId" :label="`${w.orderNo || ''} ${w.materialName || ''}`" :value="w.workOrderId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务">
              <el-select v-model="editDlg.form.taskId" filterable placeholder="选择任务" clearable style="width:100%">
                <el-option v-for="t in taskOptions" :key="t.taskId" :label="`#${t.taskId} ${t.processName || ''}`" :value="t.taskId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="产线">
              <el-input-number v-model="editDlg.form.lineNo" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="列">
              <el-input-number v-model="editDlg.form.columnNo" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工作中心">
              <el-input v-model="editDlg.form.workCenter" placeholder="如 SMT-L1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划数量">
              <el-input-number v-model="editDlg.form.planQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="合格数">
              <el-input-number v-model="editDlg.form.passQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="不良数">
              <el-input-number v-model="editDlg.form.failQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进度(%)">
              <el-input-number v-model="editDlg.form.progressPct" :min="0" :max="100" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标记">
              <el-radio-group v-model="editDlg.form.isFlagged">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预计完成">
              <el-date-picker v-model="editDlg.form.eta" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日">
              <el-date-picker v-model="editDlg.form.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签">
              <el-input v-model="editDlg.form.tags" placeholder="多个用逗号分隔" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="背景色">
              <el-color-picker v-model="editDlg.form.backgroundColor" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editDlg.form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 移动对话框 -->
    <el-dialog v-model="moveDlg.show" title="移动卡片" width="360px" destroy-on-close>
      <el-form :model="moveDlg.form" label-width="80px">
        <el-form-item label="产线">
          <el-input-number v-model="moveDlg.form.lineNo" :min="1" />
        </el-form-item>
        <el-form-item label="列">
          <el-input-number v-model="moveDlg.form.columnNo" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="moveDlg.saving" @click="submitMove">确定移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus, Grid, List } from '@element-plus/icons-vue'
import { kanbanApi, workOrderApi, processTaskApi } from '@/api'
import type { PmmKanbanCard } from '@/types/models'

const viewMode = ref<'board' | 'list'>('board')

// ---------- 看板列定义 ----------
const columns = [
  { no: 1, title: '待开始', bg: '#f4f4f5' },
  { no: 2, title: '准备中', bg: '#ecf5ff' },
  { no: 3, title: '生产中', bg: '#fdf6ec' },
  { no: 4, title: '质检', bg: '#fef0f0' },
  { no: 5, title: '已完成', bg: '#f0f9eb' }
]

// ---------- 列表视图搜索 ----------
const q = reactive({
  pageNum: 1, pageSize: 20,
  workOrderId: undefined as number | undefined,
  lineNo: undefined as number | undefined,
  workCenter: '', title: '',
  isFlagged: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as PmmKanbanCard[], total: 0 } })
const workOrderOptions = ref<any[]>([])
const taskOptions = ref<any[]>([])

async function loadList() {
  list.loading = true
  try {
    const res = await kanbanApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) { ElMessage.error(e.message || '查询失败') }
  finally { list.loading = false }
}
async function loadOptions() {
  try {
    const r = await workOrderApi.options() as any
    workOrderOptions.value = r?.data || r || []
  } catch { workOrderOptions.value = [] }
  try {
    const r = await processTaskApi.list({ pageNum: 1, pageSize: 1000 }) as any
    taskOptions.value = (r as any)?.rows || []
  } catch { taskOptions.value = [] }
}
function resetQ() {
  Object.assign(q, { workOrderId: undefined, lineNo: undefined, workCenter: '', title: '', isFlagged: undefined, pageNum: 1 })
  loadList()
}

// ---------- 看板视图数据 ----------
const board = reactive({ loading: false, cards: [] as PmmKanbanCard[] })
const cardsByCol = computed(() => {
  const map: Record<number, PmmKanbanCard[]> = {}
  for (const c of board.cards) {
    const k = c.columnNo || 1
    if (!map[k]) map[k] = []
    map[k].push(c)
  }
  return map
})

async function loadBoard() {
  board.loading = true
  try {
    const res = await kanbanApi.board()
    board.cards = res || []
  } catch (e: any) { ElMessage.error(e.message || '加载看板失败') }
  finally { board.loading = false }
}
function loadAll() {
  if (viewMode.value === 'board') loadBoard()
  else loadList()
}

// ---------- 样式辅助 ----------
function cardStyle(card: PmmKanbanCard) {
  return {
    borderLeft: card.isFlagged === 1 ? '4px solid #f56c6c' : '4px solid #409eff',
    background: card.backgroundColor || '#fff'
  }
}
function progressColor(pct?: number) {
  if (pct === undefined) return '#409eff'
  if (pct >= 100) return '#67c23a'
  if (pct >= 70) return '#409eff'
  if (pct >= 30) return '#e6a23c'
  return '#f56c6c'
}

// ---------- 新增/编辑 ----------
const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<PmmKanbanCard> })
const editRules = {
  title: [{ required: true, message: '标题不能为空', trigger: 'blur' }],
  workOrderId: [{ required: true, message: '工单ID不能为空', trigger: 'blur' }]
}
function openAdd() {
  editDlg.isNew = true
  editDlg.form = { lineNo: 1, columnNo: 1, progressPct: 0, planQty: 0, passQty: 0, failQty: 0, isFlagged: 0 }
  editDlg.show = true
}
async function openEdit(row: PmmKanbanCard) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await kanbanApi.get(row.cardId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await kanbanApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await kanbanApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadAll()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

// ---------- 移动 ----------
const moveDlg = reactive({ show: false, saving: false, cardId: 0, form: { lineNo: 1, columnNo: 1 } })
function openMove(row: PmmKanbanCard) {
  moveDlg.cardId = row.cardId!
  moveDlg.form = { lineNo: row.lineNo || 1, columnNo: row.columnNo || 1 }
  moveDlg.show = true
}
async function submitMove() {
  moveDlg.saving = true
  try {
    await kanbanApi.move(moveDlg.cardId, moveDlg.form.lineNo, moveDlg.form.columnNo)
    ElMessage.success('移动成功')
    moveDlg.show = false; loadAll()
  } catch (e: any) { ElMessage.error(e.message || '移动失败') }
  finally { moveDlg.saving = false }
}

// ---------- 删除 ----------
async function handleRemove(row: PmmKanbanCard) {
  await ElMessageBox.confirm('确认删除该看板卡片？', '提示', { type: 'warning' })
  await kanbanApi.remove(row.cardId!); ElMessage.success('删除成功'); loadAll()
}

onMounted(() => { loadBoard(); loadList(); loadOptions() })
</script>

<style scoped>
.pm-page { padding: 0; }
.tab-card { margin-bottom: 12px; }
.tab-bar { display: flex; justify-content: space-between; align-items: center; }
.search-card { margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.ok { color: #67c23a; font-weight: 600; }
.bad { color: #f56c6c; font-weight: 600; }
.muted { color: #909399; font-size: 12px; }

/* 看板样式 */
.kanban-board {
  display: flex; gap: 12px; padding: 4px 0;
  overflow-x: auto; min-height: 600px;
}
.kanban-col {
  flex: 1 0 220px; min-width: 220px;
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px;
  display: flex; flex-direction: column;
}
.kanban-col-header {
  padding: 10px 14px; border-radius: 6px 6px 0 0;
  display: flex; justify-content: space-between; align-items: center;
  border-bottom: 1px solid #ebeef5;
}
.col-title { font-weight: 600; font-size: 14px; color: #303133; }
.kanban-col-body {
  padding: 10px; flex: 1; overflow-y: auto; max-height: 620px;
  display: flex; flex-direction: column; gap: 10px;
}
.col-empty { text-align: center; color: #c0c4cc; padding: 30px 0; font-size: 13px; }
.kanban-card {
  border-radius: 6px; padding: 10px 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
  cursor: pointer; transition: transform .15s, box-shadow .15s;
  font-size: 13px;
}
.kanban-card:hover { transform: translateY(-1px); box-shadow: 0 3px 10px rgba(0,0,0,.1); }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px; }
.card-title { font-weight: 600; font-size: 14px; color: #303133; line-height: 1.4; }
.card-meta { display: flex; gap: 10px; color: #909399; font-size: 12px; margin-bottom: 8px; }
.card-progress { margin-bottom: 8px; }
.card-qty { display: flex; gap: 10px; align-items: center; font-size: 12px; margin-bottom: 6px; }
.card-footer { display: flex; justify-content: space-between; align-items: center; }
.card-tags { color: #909399; font-size: 12px; }
</style>
