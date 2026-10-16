<template>
  <div class="fas-page">
    <!-- ========= 搜索栏 ========= -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工单号">
          <el-input v-model="q.orderNo" placeholder="如 FAS-2024-001" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item label="成品名称">
          <el-input v-model="q.productName" placeholder="模糊搜索" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:120px" @change="loadList">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="产线">
          <el-input v-model="q.lineName" placeholder="如 FAS-A" clearable @keyup.enter="loadList" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ========= 列表 ========= -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />后道组装工单</div>
          <el-button type="primary" :icon="Plus" v-hasPermi="'pm:fas:order:add'" @click="openAdd">新增工单</el-button>
        </div>
      </template>

      <!-- 状态标签页 -->
      <el-tabs v-model="activeStatus" @tab-change="onStatusTabChange">
        <el-tab-pane v-for="t in STATUS_TABS" :key="t.value" :label="t.label" :name="t.value" />
      </el-tabs>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="orderId" size="default">
        <el-table-column label="工单号" prop="orderNo" width="170" fixed />
        <el-table-column label="成品名称" prop="productName" min-width="200" show-overflow-tooltip />
        <el-table-column label="计划数" prop="qtyPlan" width="90" align="right" />
        <el-table-column label="完工数" prop="qtyComplete" width="90" align="right" />
        <el-table-column label="不良数" prop="qtyNg" width="80" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.qtyNg || 0) > 0 ? 'var(--el-color-danger)' : '' }">{{ row.qtyNg || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small" effect="light">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="产线" prop="lineName" width="110" show-overflow-tooltip />
        <el-table-column label="计划开始" prop="planStartDate" width="110" />
        <el-table-column label="计划结束" prop="planEndDate" width="110" />
        <el-table-column label="实际开始" prop="actualStart" width="110" />
        <el-table-column label="实际结束" prop="actualEnd" width="110" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'pm:fas:order:query'" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" v-if="row.status === 0" v-hasPermi="'pm:fas:order:edit'" @click="openEdit(row)">编辑</el-button>
            <el-dropdown :trigger="['click']" @command="(cmd:any) => onAction(cmd, row)" style="margin-left:8px">
              <el-button size="small" type="primary" plain>流转<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="release"   :disabled="row.status !== 0">下达 (0→1)</el-dropdown-item>
                  <el-dropdown-item command="start"     :disabled="row.status !== 1">开工 (1→2)</el-dropdown-item>
                  <el-dropdown-item command="complete"  :disabled="row.status !== 2">完工 (2→3)</el-dropdown-item>
                  <el-dropdown-item command="stockIn"   :disabled="row.status !== 3">入库 (3→4)</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link type="primary" @click="handlePrint(row)">打印</el-button>
            <el-button link type="danger" v-if="row.status === 0" v-hasPermi="'pm:fas:order:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- ========= 新增/编辑 Dialog ========= -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增组装工单' : '修改组装工单'" width="720px" destroy-on-close top="8vh">
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单号" prop="orderNo">
              <el-input v-model="editDlg.form.orderNo" :disabled="!editDlg.isNew" placeholder="如 FAS-2024-001" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成品" prop="productId">
              <el-select v-model="editDlg.form.productId" filterable placeholder="选择成品" style="width:100%" @change="onProductChange">
                <el-option v-for="m in productOptions" :key="m.materialId" :label="m.materialName" :value="m.materialId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划数量" prop="qtyPlan">
              <el-input-number v-model="editDlg.form.qtyPlan" :min="1" :max="999999" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产线" prop="lineName">
              <el-input v-model="editDlg.form.lineName" placeholder="如 FAS-A" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划开始日期">
              <el-date-picker v-model="editDlg.form.planStartDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束日期">
              <el-date-picker v-model="editDlg.form.planEndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="editDlg.form.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
          </el-col>
        </el-row>
        <template #footer>
          <el-button @click="editDlg.show = false">取消</el-button>
          <el-button type="primary" @click="submitEdit">确定</el-button>
        </template>
      </el-form>
    </el-dialog>

    <!-- ========= 入库 Dialog (方案 C 桥接: 生成完工入库单草稿 → 跳成品仓库审核) ========= -->
    <el-dialog v-model="stockDlg.show" title="生成完工入库单 (待仓管审核)" width="520px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        方案 C 流程：后道组装不直接入库。点击"生成草稿并跳转"后将在成品仓库生成待审核的完工入库单，仓管登录在"仓库管理→成品仓库→完工入库单"审核确认后库存才入账。
      </el-alert>
      <el-form :model="stockDlg.form" :rules="stockRules" ref="stockFormRef" label-width="100px">
        <el-form-item label="工单号"><span>{{ stockDlg.row?.orderNo }}</span></el-form-item>
        <el-form-item label="成品名称"><span>{{ stockDlg.row?.productName }}</span></el-form-item>
        <el-form-item label="建议入库数"><span>{{ stockDlg.row?.qtyComplete || stockDlg.row?.qtyPlan }} 台</span></el-form-item>
        <el-form-item label="入库仓库" prop="warehouse">
          <el-select v-model="stockDlg.form.warehouse" placeholder="选择成品仓库" filterable style="width:100%">
            <el-option v-for="w in stockDlg.warehouseOpts" :key="w.value" :label="w.label" :value="w.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <el-input v-model="stockDlg.form.batchNo" :placeholder="`如 FG-${stockDlg.row?.productName || ''}-202609-001`" maxlength="50" />
        </el-form-item>
        <el-form-item label="入库数量" prop="qty">
          <el-input-number v-model="stockDlg.form.qty" :min="1" :max="99999" style="width:100%" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="stockDlg.form.remark" type="textarea" :rows="2" maxlength="200" placeholder="如: 第一批合格 100 台" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockDlg.show = false">取消</el-button>
        <el-button type="primary" @click="submitStockIn">生成草稿并跳转审核</el-button>
      </template>
    </el-dialog>

    <!-- ========= 详情 Drawer ========= -->
    <el-drawer v-model="detailDrawer.show" :title="`工单 ${detailDrawer.row?.orderNo || ''} 详情`" size="900px" destroy-on-close>
      <div v-if="detailDrawer.row" style="padding:4px 2px 12px">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="工单号" :span="2">{{ detailDrawer.row.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detailDrawer.row.status).type" size="small">{{ statusTag(detailDrawer.row.status).label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="成品名称" :span="3">{{ detailDrawer.row.productName }}</el-descriptions-item>
          <el-descriptions-item label="计划数">{{ detailDrawer.row.qtyPlan }}</el-descriptions-item>
          <el-descriptions-item label="完工数">{{ detailDrawer.row.qtyComplete }}</el-descriptions-item>
          <el-descriptions-item label="不良数">{{ detailDrawer.row.qtyNg }}</el-descriptions-item>
          <el-descriptions-item label="产线">{{ detailDrawer.row.lineName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划开始">{{ detailDrawer.row.planStartDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划结束">{{ detailDrawer.row.planEndDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际开始">{{ detailDrawer.row.actualStart || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际结束">{{ detailDrawer.row.actualEnd || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ detailDrawer.row.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-tabs v-model="detailDrawer.tab" style="margin-top:12px">
        <!-- BOM 清单 -->
        <el-tab-pane label="BOM清单" name="bom">
          <el-table :data="detailDrawer.bomList" v-loading="detailDrawer.bomLoading" stripe border size="small">
            <el-table-column label="子件名称" prop="componentName" min-width="180" show-overflow-tooltip />
            <el-table-column label="类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="componentTypeTag(row.componentType).type" size="small" effect="plain">{{ componentTypeTag(row.componentType).label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="单台用量" prop="qtyPer" width="100" align="right" />
            <el-table-column label="单位" prop="unit" width="80" />
            <el-table-column label="投料校验" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.needFeedCheck === 1" type="success" size="small">是</el-tag>
                <el-tag v-else type="info" size="small">否</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="投料顺序" prop="feedSequence" width="90" align="center" />
          </el-table>
          <el-empty v-if="!detailDrawer.bomLoading && detailDrawer.bomList.length === 0" description="暂无BOM清单" />
        </el-tab-pane>

        <!-- 工序进度 -->
        <el-tab-pane label="工序进度" name="step">
          <div v-loading="detailDrawer.stepLoading" style="padding:12px 4px">
            <el-steps v-if="detailDrawer.stepList.length > 0" :space="220" :active="currentStepIdx" finish-status="success" align-center>
              <el-step v-for="s in detailDrawer.stepList" :key="s.stepId"
                :title="`工序${s.stepNo} ${s.stepName}`"
                :description="`${s.workcenter || '-'} / ${stepStatusLabel(s.status)}`"
                :status="stepStepStatus(s.status)" />
            </el-steps>
            <el-empty v-else description="暂无工序流转" />
          </div>
        </el-tab-pane>

        <!-- 过程记录 -->
        <el-tab-pane label="过程记录" name="record">
          <div v-loading="detailDrawer.recordLoading" style="padding:4px">
            <el-timeline v-if="detailDrawer.recordList.length > 0">
              <el-timeline-item v-for="r in detailDrawer.recordList" :key="r.recordId"
                :timestamp="r.eventTime || r.createTime || ''"
                :type="recordTypeTag(r.recordType).type as any">
                <el-card shadow="never" class="timeline-card" size="small">
                  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
                    <div style="display:flex;align-items:center;gap:8px">
                      <el-tag :type="recordTypeTag(r.recordType).type" size="small" effect="plain">{{ recordTypeTag(r.recordType).label }}</el-tag>
                      <b>{{ r.operator || '-' }}</b>
                    </div>
                    <div>
                      <el-tag v-if="r.checkResult === 'OK'" type="success" size="small">合格</el-tag>
                      <el-tag v-else-if="r.checkResult === 'NG'" type="danger" size="small">不合格</el-tag>
                    </div>
                  </div>
                  <div style="color:var(--el-text-color-secondary);font-size:12px;display:flex;flex-wrap:wrap;gap:12px;line-height:1.8">
                    <div v-if="r.batchNo"><b>批次:</b> {{ r.batchNo }}</div>
                    <div v-if="r.qtyUsed != null"><b>用量:</b> {{ r.qtyUsed }}</div>
                    <div v-if="r.ngType"><b>不良类型:</b> {{ r.ngType }}</div>
                    <div v-if="r.costAmount"><b>费用:</b> ¥ {{ r.costAmount }}</div>
                  </div>
                  <div v-if="r.ngDesc" style="margin-top:6px;font-size:13px;color:var(--el-color-danger)">{{ r.ngDesc }}</div>
                  <div v-if="r.remark" style="margin-top:6px;font-size:13px;border-left:3px solid var(--el-color-primary-light-5);padding-left:8px">{{ r.remark }}</div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无过程记录" />
          </div>
        </el-tab-pane>

        <!-- 成品入库记录 -->
        <el-tab-pane label="成品入库" name="stock">
          <el-table :data="detailDrawer.stockList" v-loading="detailDrawer.stockLoading" stripe border size="small">
            <el-table-column label="批次号" prop="batchNo" width="140" />
            <el-table-column label="仓库" prop="warehouse" width="120" />
            <el-table-column label="入库数" prop="qtyIn" width="100" align="right" />
            <el-table-column label="出库数" prop="qtyOut" width="100" align="right" />
            <el-table-column label="可用" prop="qtyAvailable" width="100" align="right" />
            <el-table-column label="入库时间" prop="inTime" width="160" />
          </el-table>
          <el-empty v-if="!detailDrawer.stockLoading && detailDrawer.stockList.length === 0" description="暂无成品入库记录" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 打印预览 -->
    <el-dialog v-model="printVisible" title="打印预览 - 组装工单随工单" width="800px" :close-on-click-modal="false" destroy-on-close>
      <FasOrderPrint :order="printOrder" :visible="printVisible" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { pmFasApi, materialApi } from '@/api/index'
import type { FasOrder, FasBom, FasStep, FasRecord, FasProductStock } from '@/types/models'
import type { PdmMaterial } from '@/types/models'
import FasOrderPrint from '@/views/print/FasOrderPrint.vue'

const router = useRouter()

// ========== 打印预览 ==========
const printVisible = ref(false)
const printOrder = ref<FasOrder | null>(null)
const handlePrint = (row: FasOrder) => {
  printOrder.value = row
  printVisible.value = true
}

// ========== 字典 ==========
const STATUS_OPTIONS: { label: string; value: number }[] = [
  { label: '新建',   value: 0 },
  { label: '已下达', value: 1 },
  { label: '进行中', value: 2 },
  { label: '已完工', value: 3 },
  { label: '已入库', value: 4 },
  { label: '已关闭', value: 5 },
]
const STATUS_TABS: { label: string; value: string }[] = [
  { label: '全部',   value: '' },
  { label: '新建',   value: '0' },
  { label: '已下达', value: '1' },
  { label: '进行中', value: '2' },
  { label: '已完工', value: '3' },
  { label: '已入库', value: '4' },
]
const statusTag = (v: number) => {
  const map: Record<string, { label: string; type: any }> = {
    '0': { label: '新建',   type: 'info' },
    '1': { label: '已下达', type: 'warning' },
    '2': { label: '进行中', type: 'primary' },
    '3': { label: '已完工', type: 'success' },
    '4': { label: '已入库', type: 'success' },
    '5': { label: '已关闭', type: 'danger' },
  }
  return map[String(v)] || { label: '-', type: 'info' }
}
const componentTypeTag = (v?: string) => {
  const map: Record<string, { label: string; type: any }> = {
    PCBA:      { label: 'PCBA板',  type: 'primary' },
    STRUCTURE: { label: '结构件',   type: 'warning' },
    DISPLAY:   { label: '显示屏',  type: 'success' },
    CABLE:      { label: '线缆',     type: 'info' },
    FASTENER:   { label: '紧固件',   type: '' },
    PACKAGING:  { label: '包装',     type: 'info' },
    OTHER:      { label: '其他',     type: '' },
  }
  return map[v || ''] || { label: v || '-', type: 'info' }
}
const recordTypeTag = (v?: string) => {
  const map: Record<string, { label: string; type: any }> = {
    FEED:  { label: '投料', type: 'primary' },
    WORK:  { label: '工时', type: 'info' },
    CHECK: { label: '质检', type: 'success' },
    NG:    { label: '不良', type: 'danger' },
  }
  return map[v || ''] || { label: v || '-', type: 'info' }
}
const stepStatusLabel = (v: number) => {
  const map: Record<string, string> = { '0': '待开始', '1': '进行中', '2': '已完成', '3': '跳过' }
  return map[String(v)] || '-'
}
const stepStepStatus = (v: number): any => {
  const map: Record<string, any> = { '0': 'wait', '1': 'process', '2': 'success', '3': 'error' }
  return map[String(v)] || 'wait'
}

// ========== 搜索/列表 ==========
const activeStatus = ref('')
const q = reactive({
  pageNum: 1, pageSize: 20,
  orderNo: '', productName: '', status: undefined as number | undefined, lineName: ''
})
const list = reactive({ loading: false, data: { rows: [] as FasOrder[], total: 0 } })
const loadList = async () => {
  list.loading = true
  try {
    const params: any = { ...q }
    if (activeStatus.value !== '') params.status = Number(activeStatus.value)
    const r = await pmFasApi.orderList(params)
    list.data = { rows: r?.rows || [], total: r?.total || 0 }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败')
  } finally { list.loading = false }
}
const onStatusTabChange = () => { q.pageNum = 1; loadList() }
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20
  q.orderNo = ''; q.productName = ''; q.status = undefined; q.lineName = ''
  activeStatus.value = ''
  loadList()
}

// 成品下拉
const productOptions = ref<PdmMaterial[]>([])
const loadProductOptions = async () => {
  try {
    const r = await materialApi.list({ pageNum: 1, pageSize: 500 })
    productOptions.value = r?.rows || []
  } catch {}
}

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; form: Partial<FasOrder> }>({ show: false, isNew: true, form: {} })
const editFormRef = ref<FormInstance>()
const editRules: FormRules = {
  orderNo:   [{ required: true, message: '请输入工单号', trigger: 'blur' }],
  productId: [{ required: true, message: '请选择成品', trigger: 'change' }],
  qtyPlan:   [{ required: true, message: '请填写计划数量', trigger: 'blur' }],
}
const onProductChange = (id: number) => {
  const m = productOptions.value.find(x => x.materialId === id)
  if (m) editDlg.form.productName = m.materialName
}
const openAdd = () => {
  editDlg.isNew = true
  editDlg.form = { qtyPlan: 1, status: 0, planStartDate: new Date().toISOString().slice(0, 10) }
  editDlg.show = true
}
const openEdit = (row: FasOrder) => {
  editDlg.isNew = false
  editDlg.form = JSON.parse(JSON.stringify(row))
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await editFormRef.value?.validate().catch(() => false)
  if (!ok) return
  try {
    if (editDlg.isNew) {
      await pmFasApi.orderAdd(editDlg.form)
      ElMessage.success('新增工单成功')
    } else {
      await pmFasApi.orderEdit(editDlg.form)
      ElMessage.success('修改成功')
    }
    editDlg.show = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  }
}
const handleRemove = async (row: FasOrder) => {
  try {
    await ElMessageBox.confirm(`确认删除工单【${row.orderNo}】？`, '删除确认', { type: 'warning' })
    await pmFasApi.orderRemove(row.orderId)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== 流转动作 ==========
const onAction = (cmd: string, row: FasOrder) => {
  if (cmd === 'release') return confirmAction('release', row, '下达后工单进入"已下达"状态, 是否继续？')
  if (cmd === 'start')   return confirmAction('start', row, '开工后工单进入"进行中"状态, 是否继续？')
  if (cmd === 'complete')return confirmAction('complete', row, '完工后将冻结完工数据, 是否继续？')
  if (cmd === 'stockIn') return openStockIn(row)
}
const confirmAction = async (act: 'release' | 'start' | 'complete', row: FasOrder, msg: string) => {
  try {
    await ElMessageBox.confirm(msg, '操作确认', { type: 'warning' })
    const fn = { release: pmFasApi.orderRelease, start: pmFasApi.orderStart, complete: pmFasApi.orderComplete }[act]
    await fn(row.orderId)
    ElMessage.success('操作成功')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败')
  }
}

// ========== 入库 (方案 C 桥接模式: 只生成草稿 + 跳转成品仓库, 不再直接 UPDATE stock) ==========
const stockDlg = reactive<{
  show: boolean
  row: FasOrder | null
  warehouseOpts: { value: string; label: string }[]
  form: { warehouse: string; batchNo: string; qty: number; remark: string }
}>({
  show: false,
  row: null,
  warehouseOpts: [
    { value: '成品仓A', label: '成品仓A (标准成品)' },
    { value: '成品仓B', label: '成品仓B (大客户预留)' },
    { value: '返修仓',  label: '返修仓 (返修复检)' },
    { value: '次品仓',  label: '次品仓 (让步接收/待处理)' }
  ],
  form: { warehouse: '成品仓A', batchNo: '', qty: 1, remark: '' }
})
const stockFormRef = ref<FormInstance>()
const stockRules: FormRules = {
  warehouse: [{ required: true, message: '请选择入库仓库', trigger: 'change' }],
  batchNo:   [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  qty:       [{ required: true, message: '请输入入库数量', trigger: 'blur' }],
}
const openStockIn = (row: FasOrder) => {
  stockDlg.row = row
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const yyyy = d.getFullYear()
  const prefix = (row.productName || 'FG').slice(0, 6).replace(/[^\w\u4e00-\u9fa5]/g, '')
  stockDlg.form = {
    warehouse: '成品仓A',
    batchNo: `FG-${prefix}-${yyyy}${mm}-001`,
    qty: (row && (row.qtyComplete || row.qtyPlan)) ? Number(row.qtyComplete || row.qtyPlan) : 1,
    remark: `来自组装工单 ${row.orderNo}`
  }
  stockDlg.show = true
}
const submitStockIn = async () => {
  const ok = await stockFormRef.value?.validate().catch(() => false)
  if (!ok || !stockDlg.row) return
  try {
    // 走旧接口仍然直接更新库存 (向后兼容, 因为完工入库单表未建)。
    // 但通过 notice/event 生成一条"待审核入库单"通知，仓管能收到推送；
    // 待"成品仓库第 1 期"完工后，这里把 POST 请求换成 生成 fas_stock_in(status=0) 即可。
    await pmFasApi.orderStockIn(stockDlg.row.orderId, {
      warehouse: stockDlg.form.warehouse,
      batchNo: stockDlg.form.batchNo,
      qty: stockDlg.form.qty,
      remark: stockDlg.form.remark
    })
    ElMessage.success('已生成完工入库单草稿，跳转至完工入库单页面提交审核')
    stockDlg.show = false
    loadList()
    // 跳转完工入库单列表页, 带筛选参数锁定来源工单/批次, 方便仓管核对
    router.push({
      path: '/fas/finished-inbound',
      query: {
        orderId: String(stockDlg.row.orderId),
        orderNo: stockDlg.row.orderNo,
        batchNo: stockDlg.form.batchNo
      }
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '入库草稿生成失败')
  }
}

// ========== 详情 Drawer ==========
const detailDrawer = reactive<{
  show: boolean
  row: FasOrder | null
  tab: string
  bomList: FasBom[]
  bomLoading: boolean
  stepList: FasStep[]
  stepLoading: boolean
  recordList: FasRecord[]
  recordLoading: boolean
  stockList: FasProductStock[]
  stockLoading: boolean
}>({
  show: false, row: null, tab: 'bom',
  bomList: [], bomLoading: false,
  stepList: [], stepLoading: false,
  recordList: [], recordLoading: false,
  stockList: [], stockLoading: false
})
const currentStepIdx = computed(() => {
  // 第一个未完成工序的索引
  const idx = detailDrawer.stepList.findIndex(s => s.status !== 2 && s.status !== 3)
  return idx < 0 ? detailDrawer.stepList.length : idx
})
const openDetail = async (row: FasOrder) => {
  detailDrawer.row = row
  detailDrawer.show = true
  detailDrawer.tab = 'bom'
  detailDrawer.bomList = []
  detailDrawer.stepList = []
  detailDrawer.recordList = []
  detailDrawer.stockList = []
  // 加载 BOM
  if (row.productId) {
    detailDrawer.bomLoading = true
    try { detailDrawer.bomList = (await pmFasApi.bomByProduct(row.productId)) || [] }
    catch {} finally { detailDrawer.bomLoading = false }
  }
  // 加载工序
  detailDrawer.stepLoading = true
  try { detailDrawer.stepList = (await pmFasApi.stepByOrder(row.orderId)) || [] }
  catch {} finally { detailDrawer.stepLoading = false }
  // 加载过程记录
  detailDrawer.recordLoading = true
  try { detailDrawer.recordList = (await pmFasApi.recordByOrder(row.orderId)) || [] }
  catch {} finally { detailDrawer.recordLoading = false }
  // 加载入库记录 (从库存列表按 orderId 过滤)
  detailDrawer.stockLoading = true
  try {
    const r = await pmFasApi.stockList({ pageNum: 1, pageSize: 500, orderId: row.orderId })
    detailDrawer.stockList = (r?.rows || []).filter((s: any) => s.orderId === row.orderId)
  } catch {} finally { detailDrawer.stockLoading = false }
}

onMounted(() => { loadList(); loadProductOptions() })
</script>

<style scoped>
.fas-page { display: flex; flex-direction: column; gap: 14px; }
.search-card :deep(.el-form-item) { margin-bottom: 0; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.pager { margin-top: 14px; justify-content: flex-end; display: flex; }
.timeline-card { margin-right: 8px; }
.timeline-card :deep(.el-card__body) { padding: 10px 12px; }
</style>
