<template>
  <div class="company-page">
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />编码规则</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'company:coding:add'" @click="openAdd">新增规则</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border row-key="ruleId" size="default">
        <el-table-column label="模块" prop="moduleName" width="140" fixed />
        <el-table-column label="描述" prop="moduleDesc" min-width="180" show-overflow-tooltip />
        <el-table-column label="前缀" prop="prefix" width="90" align="center" />
        <el-table-column label="日期格式" prop="dateFormat" width="110" align="center" />
        <el-table-column label="流水号位数" prop="seqLength" width="100" align="center" />
        <el-table-column label="当前流水号" prop="currentSeq" width="110" align="center" />
        <el-table-column label="分隔符" prop="separator" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status===0" type="success" size="small">启用</el-tag>
            <el-tag v-else type="danger" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="preview(row)">生成预览</el-button>
            <el-button link type="primary" v-hasPermi="'company:coding:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="'company:coding:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 Dialog -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增编码规则' : '修改编码规则'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="editDlg.form" :rules="rules" label-width="110px">
        <el-form-item label="模块名" prop="moduleName">
          <el-input v-model="editDlg.form.moduleName" maxlength="50" placeholder="如 WO / PO / SO" />
        </el-form-item>
        <el-form-item label="模块描述">
          <el-input v-model="editDlg.form.moduleDesc" maxlength="100" placeholder="如 生产工单" />
        </el-form-item>
        <el-form-item label="前缀" prop="prefix">
          <el-input v-model="editDlg.form.prefix" maxlength="10" placeholder="如 WO" />
        </el-form-item>
        <el-form-item label="日期格式">
          <el-select v-model="editDlg.form.dateFormat" placeholder="请选择" clearable style="width:100%">
            <el-option label="无(不拼接日期)" value="" />
            <el-option label="YYYYMM" value="YYYYMM" />
            <el-option label="YYYYMMDD" value="YYYYMMDD" />
          </el-select>
        </el-form-item>
        <el-form-item label="流水号位数" prop="seqLength">
          <el-input-number v-model="editDlg.form.seqLength" :min="1" :max="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="当前流水号">
          <el-input-number v-model="editDlg.form.currentSeq" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="分隔符">
          <el-input v-model="editDlg.form.separator" maxlength="5" placeholder="如 - 或留空" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editDlg.form.status" :active-value="0" :inactive-value="1" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editDlg.form.description" type="textarea" :rows="2" maxlength="500" />
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
import type { CompanyCodingRule } from '@/types/models'

// ========== 列表 ==========
const loading = ref(false)
const tableData = ref<CompanyCodingRule[]>([])

const loadList = async () => {
  loading.value = true
  try {
    const r = await companyApi.codingList()
    tableData.value = r || []
  } finally { loading.value = false }
}

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; submitting: boolean; form: Partial<CompanyCodingRule> }>({
  show: false, isNew: true, submitting: false, form: {}
})
const formRef = ref<FormInstance>()
const rules: FormRules = {
  moduleName: [{ required: true, message: '请输入模块名', trigger: 'blur' }],
  prefix:     [{ required: true, message: '请输入前缀', trigger: 'blur' }],
  seqLength:  [{ required: true, message: '请填写流水号位数', trigger: 'blur' }]
}
const openAdd = () => {
  editDlg.isNew = true
  editDlg.form = { dateFormat: 'YYYYMMDD', seqLength: 4, currentSeq: 0, separator: '-', status: 0 }
  editDlg.show = true
}
const openEdit = (row: CompanyCodingRule) => {
  editDlg.isNew = false
  editDlg.form = JSON.parse(JSON.stringify(row))
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  editDlg.submitting = true
  try {
    await companyApi.codingSave(editDlg.form as any)
    ElMessage.success(editDlg.isNew ? '新增成功' : '修改成功')
    editDlg.show = false
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { editDlg.submitting = false }
}
const handleRemove = async (row: CompanyCodingRule) => {
  try {
    await ElMessageBox.confirm(`确认删除编码规则【${row.moduleName}】？`, '删除确认', { type: 'warning' })
    await companyApi.codingRemove(row.ruleId!)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== 生成预览 ==========
const preview = async (row: CompanyCodingRule) => {
  try {
    const code = await companyApi.codingGenerate(row.moduleName)
    ElMessageBox.alert(`生成编码: <b style="color:var(--el-color-primary)">${code || '(空)'}</b>`, '生成预览', {
      dangerouslyUseHTMLString: true, type: 'success'
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '生成失败')
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
