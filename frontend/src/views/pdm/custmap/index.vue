<template>
  <div class="custmap-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="客户名称">
          <el-input v-model="q.customerName" placeholder="客户名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="客户料号">
          <el-input v-model="q.customerMaterialNo" placeholder="客户料号" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="工厂物料编码">
          <el-input v-model="q.materialCode" placeholder="如 PCB-0001" clearable @keyup.enter="loadList" />
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
          <div class="title"><span class="dot" />客户料号映射</div>
          <div>
            <el-button type="primary" :icon="Search" @click="openReverseLookup">反查</el-button>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pdm:custmap:add'" @click="openAdd">新增映射</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="mapId" size="default">
        <el-table-column label="客户" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.customerCode || '' }}</span>
            <span>{{ row.customerName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="客户料号" prop="customerMaterialNo" width="160" fixed />
        <el-table-column label="客户物料名称" prop="customerMaterialName" min-width="180" show-overflow-tooltip />
        <el-table-column label="工厂物料" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.materialCode }}</span>
            <span class="muted">{{ row.materialName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规格型号" prop="specification" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" v-hasPermi="'pdm:custmap:edit'" @click="openEdit(row)">修改</el-button>
            <el-button link type="warning" v-if="row.status===1" v-hasPermi="'pdm:custmap:edit'" @click="handleDisable(row)">停用</el-button>
            <el-button link type="success" v-else v-hasPermi="'pdm:custmap:edit'" @click="handleEnable(row)">启用</el-button>
            <el-button link type="danger" v-hasPermi="'pdm:custmap:remove'" @click="handleRemove(row)">删除</el-button>
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
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增客户料号映射' : '修改客户料号映射'" width="720px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="editDlg.form.customerId" filterable placeholder="选择客户" style="width:100%"
                         @change="onCustomerChange">
                <el-option v-for="c in customerOpts" :key="c.customerId"
                           :label="`${c.customerCode} - ${c.customerName}`"
                           :value="c.customerId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工厂物料" prop="materialId">
              <el-select v-model="editDlg.form.materialId" filterable remote :remote-method="searchMaterial
                " placeholder="搜索物料编码/名称" style="width:100%" @change="onMaterialChange">
                <el-option v-for="m in materialOpts" :key="m.materialId"
                           :label="`${m.materialCode} - ${m.materialName}`"
                           :value="m.materialId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户料号" prop="customerMaterialNo">
              <el-input v-model="editDlg.form.customerMaterialNo" placeholder="客户侧料号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户物料名称">
              <el-input v-model="editDlg.form.customerMaterialName" placeholder="客户侧物料名称（选填）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="editDlg.form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editDlg.form.remark" type="textarea" :rows="2" placeholder="备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDlg.show" title="客户料号映射详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="客户">{{ detailDlg.data?.customerName }}</el-descriptions-item>
        <el-descriptions-item label="客户编码">{{ detailDlg.data?.customerCode }}</el-descriptions-item>
        <el-descriptions-item label="客户料号">{{ detailDlg.data?.customerMaterialNo }}</el-descriptions-item>
        <el-descriptions-item label="客户物料名称">{{ detailDlg.data?.customerMaterialName }}</el-descriptions-item>
        <el-descriptions-item label="工厂物料编码">{{ detailDlg.data?.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="工厂物料名称">{{ detailDlg.data?.materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格型号" :span="2">{{ detailDlg.data?.specification }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailDlg.data?.status === 1" type="success" size="small">启用</el-tag>
          <el-tag v-else type="info" size="small">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailDlg.data?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailDlg.data?.remark }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 反查对话框 -->
    <el-dialog v-model="reverseDlg.show" title="按客户料号反查工厂物料" width="640px">
      <el-form :inline="true" @submit.prevent="doReverseLookup">
        <el-form-item label="客户料号">
          <el-input v-model="reverseDlg.keyword" placeholder="输入客户料号" clearable @keyup.enter="doReverseLookup" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="doReverseLookup">查询</el-button>
        </el-form-item>
      </el-form>
      <el-divider v-if="reverseDlg.result" />
      <el-descriptions v-if="reverseDlg.result" :column="2" border>
        <el-descriptions-item label="客户">{{ reverseDlg.result.customerName }}</el-descriptions-item>
        <el-descriptions-item label="客户料号">{{ reverseDlg.result.customerMaterialNo }}</el-descriptions-item>
        <el-descriptions-item label="工厂物料编码">{{ reverseDlg.result.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="工厂物料名称">{{ reverseDlg.result.materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格型号" :span="2">{{ reverseDlg.result.specification }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="reverseDlg.result.status === 1" type="success" size="small">启用</el-tag>
          <el-tag v-else type="info" size="small">停用</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="reverseDlg.searched" description="未找到匹配的工厂物料" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { custMapApi } from '@/api'
import type { PdmCustomerMaterialMap } from '@/types/models'

// ============ 列表 ============
const q = reactive({
  pageNum: 1, pageSize: 20,
  customerName: '', customerMaterialNo: '', materialCode: '',
  status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as PdmCustomerMaterialMap[], total: 0 } })

async function loadList() {
  list.loading = true
  try {
    const res = await custMapApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally {
    list.loading = false
  }
}
function resetQ() {
  q.customerName = ''; q.customerMaterialNo = ''; q.materialCode = ''; q.status = undefined
  q.pageNum = 1
  loadList()
}

// ============ 下拉数据 ============
const customerOpts = ref<any[]>([])
const materialOpts = ref<any[]>([])

async function loadCustomerOpts() {
  try { customerOpts.value = await custMapApi.customerOptions() || [] }
  catch { customerOpts.value = [] }
}
async function loadMaterialOpts(keyword?: string) {
  try { materialOpts.value = await custMapApi.materialOptions(keyword) || [] }
  catch { materialOpts.value = [] }
}
function searchMaterial(query: string) { loadMaterialOpts(query) }

// ============ 新增/编辑 ============
const editFormRef = ref<FormInstance>()
const editDlg = reactive({
  show: false, isNew: true, saving: false,
  form: {} as Partial<PdmCustomerMaterialMap>
})
const editRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  materialId: [{ required: true, message: '请选择工厂物料', trigger: 'change' }],
  customerMaterialNo: [{ required: true, message: '客户料号不能为空', trigger: 'blur' }]
}

function openAdd() {
  editDlg.isNew = true
  editDlg.form = { status: 1, customerId: undefined, materialId: undefined }
  editDlg.show = true
  loadCustomerOpts()
  loadMaterialOpts()
}
async function openEdit(row: PdmCustomerMaterialMap) {
  editDlg.isNew = false
  editDlg.show = true
  loadCustomerOpts()
  loadMaterialOpts()
  try {
    const res = await custMapApi.get(row.mapId!)
    editDlg.form = { ...res }
  } catch {
    editDlg.form = { ...row }
  }
}
function onCustomerChange(val: number) {
  const c = customerOpts.value.find(c => c.customerId === val)
  if (c) editDlg.form.customerName = c.customerName
}
function onMaterialChange(val: number) {
  const m = materialOpts.value.find(m => m.materialId === val)
  if (m) {
    editDlg.form.materialCode = m.materialCode
    editDlg.form.materialName = m.materialName
    editDlg.form.specification = m.specification
  }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) {
      await custMapApi.add(editDlg.form)
      ElMessage.success('新增成功')
    } else {
      await custMapApi.edit(editDlg.form)
      ElMessage.success('修改成功')
    }
    editDlg.show = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    editDlg.saving = false
  }
}

// ============ 详情 ============
const detailDlg = reactive({ show: false, data: null as PdmCustomerMaterialMap | null })
async function openDetail(row: PdmCustomerMaterialMap) {
  try {
    detailDlg.data = await custMapApi.get(row.mapId!)
    detailDlg.show = true
  } catch (e: any) {
    ElMessage.error(e.message || '加载详情失败')
  }
}

// ============ 启用/停用/删除 ============
async function handleEnable(row: PdmCustomerMaterialMap) {
  await custMapApi.enable(row.mapId!)
  ElMessage.success('已启用')
  loadList()
}
async function handleDisable(row: PdmCustomerMaterialMap) {
  await custMapApi.disable(row.mapId!)
  ElMessage.success('已停用')
  loadList()
}
async function handleRemove(row: PdmCustomerMaterialMap) {
  await ElMessageBox.confirm('确认删除该客户料号映射？', '提示', { type: 'warning' })
  await custMapApi.remove(row.mapId!)
  ElMessage.success('删除成功')
  loadList()
}

// ============ 反查 ============
const reverseDlg = reactive({
  show: false, keyword: '', searched: false,
  result: null as PdmCustomerMaterialMap | null
})
function openReverseLookup() {
  reverseDlg.show = true
  reverseDlg.keyword = ''
  reverseDlg.searched = false
  reverseDlg.result = null
}
async function doReverseLookup() {
  if (!reverseDlg.keyword.trim()) return
  try {
    reverseDlg.result = await custMapApi.reverseLookup(reverseDlg.keyword.trim())
    reverseDlg.searched = true
  } catch {
    reverseDlg.result = null
    reverseDlg.searched = true
  }
}

// ============ 初始化 ============
onMounted(() => { loadList() })
</script>

<style scoped>
.custmap-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
.mono { font-family: 'Courier New', monospace; font-size: 13px; margin-right: 6px; color: #303133; }
.muted { color: #909399; font-size: 13px; }
</style>
