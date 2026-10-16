<template>
  <div class="company-page">
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />业务参数</div>
          <div class="header-right">
            <el-select v-model="filterCategory" placeholder="按分类筛选" clearable style="width:180px" @change="applyFilter">
              <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
            </el-select>
            <el-button type="primary" :icon="Plus" v-hasPermi="'company:param:add'" @click="openAdd">新增参数</el-button>
          </div>
        </div>
      </template>

      <el-table :data="filteredData" v-loading="loading" stripe border row-key="paramId" size="default">
        <el-table-column label="参数键" prop="paramKey" width="180" fixed />
        <el-table-column label="显示名" prop="paramName" width="160" />
        <el-table-column label="参数值" min-width="220">
          <template #default="{ row }">
            <div class="inline-edit">
              <el-input v-model="row.paramValue" size="small" :placeholder="(row.paramType || 'STRING') === 'BOOLEAN' ? 'true/false' : ''" />
              <el-button link type="primary" :icon="Check" @click="saveInline(row)" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="paramTypeTag(row.paramType)" size="small" effect="plain">{{ row.paramType || 'STRING' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分类" prop="category" width="120" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status===0" type="success" size="small">启用</el-tag>
            <el-tag v-else type="danger" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'company:param:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="'company:param:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 Dialog -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增参数' : '修改参数'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="editDlg.form" :rules="rules" label-width="100px">
        <el-form-item label="参数键" prop="paramKey">
          <el-input v-model="editDlg.form.paramKey" maxlength="100" placeholder="如 sys.title" :disabled="!editDlg.isNew" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="editDlg.form.paramName" maxlength="100" placeholder="中文显示名" />
        </el-form-item>
        <el-form-item label="参数值">
          <el-input v-model="editDlg.form.paramValue" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editDlg.form.paramType" placeholder="请选择" style="width:100%">
            <el-option label="字符串 STRING" value="STRING" />
            <el-option label="数字 NUMBER" value="NUMBER" />
            <el-option label="布尔 BOOLEAN" value="BOOLEAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="editDlg.form.category" maxlength="50" placeholder="如 system / finance" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editDlg.form.status" :active-value="0" :inactive-value="1" active-text="启用" inactive-text="停用" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Check } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { companyApi } from '@/api/index'
import type { CompanyParam } from '@/types/models'

// ========== 列表 ==========
const loading = ref(false)
const tableData = ref<CompanyParam[]>([])
const filterCategory = ref<string>('')

const categoryOptions = computed(() => {
  const set = new Set<string>()
  tableData.value.forEach(e => { if (e.category) set.add(e.category) })
  return Array.from(set)
})
const filteredData = computed(() => {
  if (!filterCategory.value) return tableData.value
  return tableData.value.filter(e => e.category === filterCategory.value)
})
const applyFilter = () => {}

const loadList = async () => {
  loading.value = true
  try {
    const r = await companyApi.paramList()
    tableData.value = r || []
  } finally { loading.value = false }
}

const paramTypeTag = (v?: string) => {
  const map: Record<string, any> = { STRING: 'info', NUMBER: 'warning', BOOLEAN: 'primary' }
  return map[v || ''] || 'info'
}

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; submitting: boolean; form: Partial<CompanyParam> }>({
  show: false, isNew: true, submitting: false, form: {}
})
const formRef = ref<FormInstance>()
const rules: FormRules = {
  paramKey: [{ required: true, message: '请输入参数键', trigger: 'blur' }]
}
const openAdd = () => {
  editDlg.isNew = true
  editDlg.form = { paramType: 'STRING', status: 0 }
  editDlg.show = true
}
const openEdit = (row: CompanyParam) => {
  editDlg.isNew = false
  editDlg.form = JSON.parse(JSON.stringify(row))
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  editDlg.submitting = true
  try {
    await companyApi.paramSave(editDlg.form as any)
    ElMessage.success(editDlg.isNew ? '新增成功' : '修改成功')
    editDlg.show = false
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { editDlg.submitting = false }
}
const handleRemove = async (row: CompanyParam) => {
  try {
    await ElMessageBox.confirm(`确认删除参数【${row.paramKey}】？`, '删除确认', { type: 'warning' })
    await companyApi.paramRemove(row.paramId!)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== 行内编辑保存 ==========
const saveInline = async (row: CompanyParam) => {
  try {
    await companyApi.paramSave({ paramId: row.paramId, paramValue: row.paramValue })
    ElMessage.success(`已更新 ${row.paramKey}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  }
}

onMounted(() => { loadList() })
</script>

<style scoped>
.company-page { display: flex; flex-direction: column; gap: 14px; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.list-card .header-right { display: flex; align-items: center; gap: 12px; }
.inline-edit { display: flex; align-items: center; gap: 4px; }
</style>
