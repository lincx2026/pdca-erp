<template>
  <div class="company-page">
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />工厂产线</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'company:factory:add'" @click="openAdd()">新增工厂</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" row-key="factoryId" border stripe
        :tree-props="{ children: 'children' }" default-expand-all size="default">
        <el-table-column label="编码" prop="factoryCode" width="140" />
        <el-table-column label="名称" prop="factoryName" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="nodeTypeTag(row.nodeType)" size="small" effect="plain">{{ nodeTypeLabel(row.nodeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地址" prop="address" min-width="180" show-overflow-tooltip />
        <el-table-column label="负责人" prop="managerName" width="100" />
        <el-table-column label="联系电话" prop="managerPhone" width="130" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status===0" type="success" size="small">正常</el-tag>
            <el-tag v-else type="danger" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排序号" prop="orderNum" width="80" align="center" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'company:factory:add'" @click="openAdd(row)">新增下级</el-button>
            <el-button link type="primary" v-hasPermi="'company:factory:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="'company:factory:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 Dialog -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增工厂/产线' : '修改工厂/产线'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="editDlg.form" :rules="rules" label-width="100px">
        <el-form-item label="父节点">
          <el-tree-select
            v-model="editDlg.form.parentId"
            :data="parentOptions"
            :props="{ label: 'factoryName', value: 'factoryId', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            placeholder="顶级(0) 表示根工厂"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="编码" prop="factoryCode">
          <el-input v-model="editDlg.form.factoryCode" maxlength="50" placeholder="如 F01 / L01" />
        </el-form-item>
        <el-form-item label="名称" prop="factoryName">
          <el-input v-model="editDlg.form.factoryName" maxlength="100" />
        </el-form-item>
        <el-form-item label="类型" prop="nodeType">
          <el-select v-model="editDlg.form.nodeType" placeholder="请选择" style="width:100%">
            <el-option v-for="t in NODE_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="editDlg.form.address" maxlength="255" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="editDlg.form.managerName" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="editDlg.form.managerPhone" maxlength="20" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-switch v-model="editDlg.form.status" :active-value="0" :inactive-value="1" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="editDlg.form.orderNum" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editDlg.form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.submitting" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { companyApi } from '@/api/index'
import type { CompanyFactory } from '@/types/models'

// ========== 字典 ==========
const NODE_TYPE_OPTIONS = [
  { label: '工厂', value: 'FACTORY' },
  { label: '车间/分类', value: 'CATEGORY' },
  { label: '产线', value: 'LINE' },
]
const nodeTypeLabel = (v?: string) => NODE_TYPE_OPTIONS.find(x => x.value === v)?.label || (v || '-')
const nodeTypeTag = (v?: string) => {
  const map: Record<string, any> = { FACTORY: 'info', CATEGORY: 'warning', LINE: 'primary' }
  return map[v || ''] || ''
}

// ========== 列表 ==========
const loading = ref(false)
const tableData = ref<CompanyFactory[]>([])
const parentOptions = ref<CompanyFactory[]>([])

const loadList = async () => {
  loading.value = true
  try {
    const r = await companyApi.factoryList()
    tableData.value = r || []
    parentOptions.value = buildParentOptions(r || [])
  } finally { loading.value = false }
}
// 构造父节点选择树: 顶级追加一个虚拟根 0
const buildParentOptions = (list: CompanyFactory[]): CompanyFactory[] => {
  const root: CompanyFactory = { factoryId: 0, parentId: -1, factoryCode: 'ROOT', factoryName: '顶级(根)', nodeType: 'FACTORY', status: 0, orderNum: 0, children: list }
  return [root]
}

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; submitting: boolean; form: Partial<CompanyFactory> }>({
  show: false, isNew: true, submitting: false, form: {}
})
const formRef = ref<FormInstance>()
const rules: FormRules = {
  factoryCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  factoryName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  nodeType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}
const openAdd = (parent?: CompanyFactory) => {
  editDlg.isNew = true
  editDlg.form = { parentId: parent?.factoryId ?? 0, nodeType: 'FACTORY', status: 0, orderNum: 0 }
  editDlg.show = true
}
const openEdit = (row: CompanyFactory) => {
  editDlg.isNew = false
  editDlg.form = JSON.parse(JSON.stringify(row))
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  editDlg.submitting = true
  try {
    if (editDlg.isNew) {
      await companyApi.factoryAdd(editDlg.form as any)
      ElMessage.success('新增成功')
    } else {
      await companyApi.factoryEdit(editDlg.form as any)
      ElMessage.success('修改成功')
    }
    editDlg.show = false
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { editDlg.submitting = false }
}
const handleRemove = async (row: CompanyFactory) => {
  try {
    await ElMessageBox.confirm(`确认删除【${row.factoryName}】？子节点也会一并删除。`, '删除确认', { type: 'warning' })
    await companyApi.factoryRemove(row.factoryId!)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

onMounted(() => { loadList() })
</script>

<style scoped>
.company-page { display: flex; flex-direction: column; gap: 14px; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
</style>
