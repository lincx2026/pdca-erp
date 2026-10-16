<template>
  <div class="fas-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="成品">
          <el-select v-model="q.productId" filterable placeholder="全部" clearable style="width:220px" @change="loadList">
            <el-option v-for="m in productOptions" :key="m.materialId" :label="m.materialName" :value="m.materialId" />
          </el-select>
        </el-form-item>
        <el-form-item label="子件名称">
          <el-input v-model="q.componentName" placeholder="模糊搜索" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item label="子件类型">
          <el-select v-model="q.componentType" placeholder="全部" clearable style="width:140px" @change="loadList">
            <el-option v-for="t in COMPONENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
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
          <div class="title"><span class="dot" />组装BOM管理</div>
          <div>
            <el-button :icon="View" @click="openByProduct">按成品查看</el-button>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:fas:bom:add'" @click="openAdd">新增BOM</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="bomId" size="default">
        <el-table-column label="成品名称" prop="productName" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ productMap.get(row.productId)?.materialName || row.componentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="子件名称" prop="componentName" min-width="200" show-overflow-tooltip />
        <el-table-column label="子件类型" width="120" align="center">
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
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'pm:fas:bom:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="'pm:fas:bom:remove'" @click="handleRemove(row)">删除</el-button>
            <el-button link type="success" @click="handlePrint(row)">打印BOM</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 新增/编辑 Dialog -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增组装BOM' : '修改组装BOM'" width="640px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="成品" prop="productId">
              <el-select v-model="editDlg.form.productId" filterable placeholder="选择成品" style="width:100%" @change="onProductChange">
                <el-option v-for="m in productOptions" :key="m.materialId" :label="m.materialName" :value="m.materialId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="子件" prop="componentName">
              <el-select v-model="editDlg.form.componentName" filterable placeholder="选择子件" style="width:100%" @change="onComponentChange">
                <el-option v-for="m in componentOptions" :key="m.materialId" :label="m.materialName" :value="m.materialName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="子件类型" prop="componentType">
              <el-select v-model="editDlg.form.componentType" placeholder="请选择" style="width:100%">
                <el-option v-for="t in COMPONENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单台用量" prop="qtyPer">
              <el-input-number v-model="editDlg.form.qtyPer" :min="0" :precision="4" :max="9999" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位"><el-input v-model="editDlg.form.unit" maxlength="20" placeholder="如 PCS" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投料顺序">
              <el-input-number v-model="editDlg.form.feedSequence" :min="0" :max="9999" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投料校验">
              <el-switch v-model="editDlg.form.needFeedCheck" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
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

    <!-- 按成品查看 Dialog -->
    <el-dialog v-model="byProductDlg.show" title="按成品查看BOM" width="780px" destroy-on-close>
      <el-form :inline="true" style="margin-bottom:8px">
        <el-form-item label="成品">
          <el-select v-model="byProductDlg.productId" filterable placeholder="选择成品" style="width:300px" @change="loadByProduct">
            <el-option v-for="m in productOptions" :key="m.materialId" :label="m.materialName" :value="m.materialId" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="byProductDlg.rows" v-loading="byProductDlg.loading" stripe border size="default" max-height="420">
        <el-table-column label="子件名称" prop="componentName" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
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
        <el-table-column label="顺序" prop="feedSequence" width="80" align="center" />
      </el-table>
      <el-empty v-if="!byProductDlg.loading && byProductDlg.rows.length === 0" description="请选择成品查看BOM" />
    </el-dialog>

    <!-- 打印预览 -->
    <el-dialog v-model="printVisible" title="打印预览 - 组装BOM清单" width="800px" :close-on-click-modal="false" destroy-on-close>
      <BomPrint :productId="printProductId" :productName="printProductName" :visible="printVisible" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Plus, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { pmFasApi, materialApi } from '@/api/index'
import type { FasBom } from '@/types/models'
import type { PdmMaterial } from '@/types/models'
import BomPrint from '@/views/print/BomPrint.vue'

// ========== 打印预览 ==========
const printVisible = ref(false)
const printProductId = ref<number | null>(null)
const printProductName = ref<string>('')
const handlePrint = (row: FasBom) => {
  printProductId.value = row.productId
  printProductName.value = productMap.value.get(row.productId)?.materialName || row.componentName || ''
  printVisible.value = true
}

// ========== 字典 ==========
const COMPONENT_TYPES: { label: string; value: string }[] = [
  { label: 'PCBA板',    value: 'PCBA' },
  { label: '结构件',    value: 'STRUCTURE' },
  { label: '显示屏',   value: 'DISPLAY' },
  { label: '线缆',      value: 'CABLE' },
  { label: '紧固件',    value: 'FASTENER' },
  { label: '包装',      value: 'PACKAGING' },
  { label: '其他',      value: 'OTHER' },
]
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

// ========== 搜索/列表 ==========
const q = reactive({
  pageNum: 1, pageSize: 20,
  productId: undefined as number | undefined,
  componentName: '', componentType: '' as string | undefined
})
const list = reactive({ loading: false, data: { rows: [] as FasBom[], total: 0 } })
const productOptions = ref<PdmMaterial[]>([])
const componentOptions = ref<PdmMaterial[]>([])
const productMap = computed(() => {
  const m = new Map<number, PdmMaterial>()
  productOptions.value.forEach(e => { if (e.materialId) m.set(e.materialId, e) })
  return m
})
const loadList = async () => {
  list.loading = true
  try {
    const r = await pmFasApi.bomList({ ...q })
    list.data = { rows: r?.rows || [], total: r?.total || 0 }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败')
  } finally { list.loading = false }
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20
  q.productId = undefined; q.componentName = ''; q.componentType = undefined
  loadList()
}
const loadOptions = async () => {
  try {
    const r = await materialApi.list({ pageNum: 1, pageSize: 500 })
    productOptions.value = r?.rows || []
    componentOptions.value = r?.rows || []
  } catch {}
}

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; form: Partial<FasBom> }>({ show: false, isNew: true, form: {} })
const editFormRef = ref<FormInstance>()
const editRules: FormRules = {
  productId:      [{ required: true, message: '请选择成品', trigger: 'change' }],
  componentName:  [{ required: true, message: '请选择子件', trigger: 'change' }],
  componentType:   [{ required: true, message: '请选择子件类型', trigger: 'change' }],
  qtyPer:         [{ required: true, message: '请填写单台用量', trigger: 'blur' }],
}
const onProductChange = (id: number) => {
  const m = productOptions.value.find(x => x.materialId === id)
  if (m && !editDlg.form.unit) editDlg.form.unit = m.unit
}
const onComponentChange = (name: string) => {
  const m = componentOptions.value.find(x => x.materialName === name)
  if (m && !editDlg.form.unit) editDlg.form.unit = m.unit
}
const openAdd = () => {
  editDlg.isNew = true
  editDlg.form = { qtyPer: 1, needFeedCheck: 0, feedSequence: 0 }
  editDlg.show = true
}
const openEdit = (row: FasBom) => {
  editDlg.isNew = false
  editDlg.form = JSON.parse(JSON.stringify(row))
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await editFormRef.value?.validate().catch(() => false)
  if (!ok) return
  try {
    if (editDlg.isNew) {
      await pmFasApi.bomAdd(editDlg.form)
      ElMessage.success('新增BOM成功')
    } else {
      await pmFasApi.bomEdit(editDlg.form)
      ElMessage.success('修改成功')
    }
    editDlg.show = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  }
}
const handleRemove = async (row: FasBom) => {
  try {
    await ElMessageBox.confirm(`确认删除BOM【${row.componentName || '-'}】？`, '删除确认', { type: 'warning' })
    await pmFasApi.bomRemove(row.bomId)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== 按成品查看 ==========
const byProductDlg = reactive<{ show: boolean; productId?: number; rows: FasBom[]; loading: boolean }>({
  show: false, productId: undefined, rows: [], loading: false
})
const openByProduct = () => {
  byProductDlg.show = true
  byProductDlg.productId = undefined
  byProductDlg.rows = []
}
const loadByProduct = async () => {
  if (!byProductDlg.productId) { byProductDlg.rows = []; return }
  byProductDlg.loading = true
  try {
    byProductDlg.rows = (await pmFasApi.bomByProduct(byProductDlg.productId)) || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败')
  } finally { byProductDlg.loading = false }
}

onMounted(() => { loadList(); loadOptions() })
</script>

<style scoped>
.fas-page { display: flex; flex-direction: column; gap: 14px; }
.search-card :deep(.el-form-item) { margin-bottom: 0; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.pager { margin-top: 14px; justify-content: flex-end; display: flex; }
</style>
