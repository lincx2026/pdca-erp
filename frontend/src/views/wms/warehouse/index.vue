<template>
  <div class="warehouse-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="仓库编码">
          <el-input v-model="q.warehouseCode" placeholder="仓库编码" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="仓库名称">
          <el-input v-model="q.warehouseName" placeholder="仓库名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="仓库类型">
          <el-select v-model="q.warehouseType" placeholder="全部" clearable style="width:140px">
            <el-option v-for="t in warehouseTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
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
          <div class="title"><span class="dot" />仓库档案</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'wms:warehouse:add'" @click="openAdd">新增仓库</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="warehouseId" size="default">
        <el-table-column label="仓库编码" prop="warehouseCode" width="140" fixed />
        <el-table-column label="仓库名称" prop="warehouseName" min-width="180" show-overflow-tooltip />
        <el-table-column label="仓库类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.warehouseType)" size="small">{{ typeLabel(row.warehouseType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地址" prop="address" min-width="200" show-overflow-tooltip />
        <el-table-column label="管理员" prop="manager" width="120" />
        <el-table-column label="电话" prop="phone" width="140" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" v-hasPermi="'wms:warehouse:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" v-if="row.status===1" v-hasPermi="'wms:warehouse:edit'" @click="handleDisable(row)">停用</el-button>
            <el-button link type="success" v-else v-hasPermi="'wms:warehouse:edit'" @click="handleEnable(row)">启用</el-button>
            <el-button link type="danger" v-hasPermi="'wms:warehouse:remove'" @click="handleRemove(row)">删除</el-button>
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
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增仓库' : '修改仓库'" width="720px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="仓库编码" prop="warehouseCode">
              <el-input v-model="editDlg.form.warehouseCode" placeholder="如 WH-RAW-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库名称" prop="warehouseName">
              <el-input v-model="editDlg.form.warehouseName" placeholder="仓库名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库类型" prop="warehouseType">
              <el-select v-model="editDlg.form.warehouseType" placeholder="选择仓库类型" style="width:100%">
                <el-option v-for="t in warehouseTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="管理员">
              <el-input v-model="editDlg.form.manager" placeholder="管理员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话">
              <el-input v-model="editDlg.form.phone" placeholder="联系电话" />
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
            <el-form-item label="地址">
              <el-input v-model="editDlg.form.address" placeholder="仓库地址" />
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
    <el-dialog v-model="detailDlg.show" title="仓库详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="仓库编码">{{ detailDlg.data?.warehouseCode }}</el-descriptions-item>
        <el-descriptions-item label="仓库名称">{{ detailDlg.data?.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="仓库类型">
          <el-tag :type="typeTagType(detailDlg.data?.warehouseType)" size="small">
            {{ typeLabel(detailDlg.data?.warehouseType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailDlg.data?.status === 1" type="success" size="small">启用</el-tag>
          <el-tag v-else type="info" size="small">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="管理员">{{ detailDlg.data?.manager }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ detailDlg.data?.phone }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ detailDlg.data?.address }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailDlg.data?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailDlg.data?.updateTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailDlg.data?.remark }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { warehouseApi } from '@/api'
import type { WmsWarehouse } from '@/types/models'

// ============ 类型映射 ============
/** 方案 C: 电子仓库 = WMS 专用, 不再管成品。
 *  成品整机的仓库档案在"成品仓库"子域独立维护 (后续 fas_warehouse 表)。
 *  电子仓库下的 3 种子仓 (和 DB warehouse_type 对齐) */
const warehouseTypes = [
  { value: 'ELEC', label: '电子仓'   }, // 电子料/IC/阻容/PCBA 半成品等
  { value: 'AUX',  label: '辅料仓'   }, // 线材/螺丝/外壳/结构件
  { value: 'BOX',  label: '包材仓'   }, // 彩盒/说明书/泡沫/外箱
  { value: 'SPARE',label: '备品仓'   }  // 备件/工装耗材
]
function typeLabel(type?: string) {
  return warehouseTypes.find(t => t.value === type)?.label || type || '-'
}
function typeTagType(type?: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    ELEC:  'warning', // 琥珀色=芯片/元件
    AUX:   'info',
    BOX:   '',
    SPARE: 'success'
  }
  return type ? (map[type] || '') : ''
}

// ============ 列表 ============
const q = reactive({
  pageNum: 1, pageSize: 20,
  warehouseCode: '', warehouseName: '', warehouseType: '',
  status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as WmsWarehouse[], total: 0 } })

async function loadList() {
  list.loading = true
  try {
    const res = await warehouseApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally {
    list.loading = false
  }
}
function resetQ() {
  q.warehouseCode = ''; q.warehouseName = ''; q.warehouseType = ''; q.status = undefined
  q.pageNum = 1
  loadList()
}

// ============ 新增/编辑 ============
const editFormRef = ref<FormInstance>()
const editDlg = reactive({
  show: false, isNew: true, saving: false,
  form: {} as Partial<WmsWarehouse>
})
const editRules = {
  warehouseCode: [{ required: true, message: '仓库编码不能为空', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '仓库名称不能为空', trigger: 'blur' }],
  warehouseType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }]
}

function openAdd() {
  editDlg.isNew = true
  editDlg.form = { status: 1 }
  editDlg.show = true
}
async function openEdit(row: WmsWarehouse) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await warehouseApi.get(row.warehouseId!)
    editDlg.form = { ...res }
  } catch {
    editDlg.form = { ...row }
  }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) {
      await warehouseApi.add(editDlg.form)
      ElMessage.success('新增成功')
    } else {
      await warehouseApi.edit(editDlg.form)
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
const detailDlg = reactive({ show: false, data: null as WmsWarehouse | null })
async function openDetail(row: WmsWarehouse) {
  try {
    detailDlg.data = await warehouseApi.get(row.warehouseId!)
    detailDlg.show = true
  } catch (e: any) {
    ElMessage.error(e.message || '加载详情失败')
  }
}

// ============ 启用/停用/删除 ============
async function handleEnable(row: WmsWarehouse) {
  await warehouseApi.enable(row.warehouseId!)
  ElMessage.success('已启用')
  loadList()
}
async function handleDisable(row: WmsWarehouse) {
  await warehouseApi.disable(row.warehouseId!)
  ElMessage.success('已停用')
  loadList()
}
async function handleRemove(row: WmsWarehouse) {
  await ElMessageBox.confirm('确认删除该仓库？', '提示', { type: 'warning' })
  await warehouseApi.remove(row.warehouseId!)
  ElMessage.success('删除成功')
  loadList()
}

// ============ 初始化 ============
onMounted(() => { loadList() })
</script>

<style scoped>
.warehouse-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
.mono { font-family: 'Courier New', monospace; font-size: 13px; margin-right: 6px; color: #303133; }
.muted { color: #909399; font-size: 13px; }
</style>
