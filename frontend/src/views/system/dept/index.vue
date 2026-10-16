<template>
  <div class="page">
    <el-card shadow="never" class="mb-16">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="部门名">
          <el-input v-model="query.deptName" placeholder="请输入部门名" clearable style="width:220px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="reload">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" :icon="Plus" v-hasPermi="'system:dept:add'" @click="handleAdd()">新增</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" row-key="deptId" border stripe :tree-props="{ children: 'children' }" default-expand-all size="small">
        <el-table-column prop="deptName" label="部门名" min-width="180" />
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status===0" type="success">正常</el-tag>
            <el-tag v-else type="danger">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="leader" label="负责人" width="120" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'system:dept:add'" @click="handleAdd(row)">新增下级</el-button>
            <el-button link type="primary" v-hasPermi="'system:dept:edit'" @click="handleEdit(row)">修改</el-button>
            <el-popconfirm v-if="row.deptId !== 100" title="确定删除该部门?" @confirm="handleRemove(row)">
              <template #reference>
                <el-button link type="danger" v-hasPermi="'system:dept:remove'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '修改部门' : '新增部门'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'deptName', value: 'deptId', children: 'children', disabled: false }"
            check-strictly
            :render-after-expand="false"
            placeholder="请选择上级部门(无则=集团根)"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="部门名" prop="deptName">
          <el-input v-model="form.deptName" maxlength="30" show-word-limit style="width:100%" />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="form.orderNum" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.leader" maxlength="20" style="width:100%" /></el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" maxlength="11" placeholder="手机或座机" style="width:100%" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" type="email" maxlength="50" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { deptApi } from '@/api'
import type { SysDept } from '@/types/models'

const loading = ref(false)
const tableData = ref<SysDept[]>([])
const parentOptions = ref<SysDept[]>([])
const query = reactive<{ deptName?: string; status?: number }>({})

async function reload() {
  loading.value = true
  try {
    tableData.value = await deptApi.list(query) as any
    parentOptions.value = await deptApi.excludeTree() as any
  } finally { loading.value = false }
}
function resetQuery() { query.deptName = ''; query.status = undefined; reload() }

const dialog = reactive<{ visible: boolean; isEdit: boolean }>({ visible: false, isEdit: false })
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const defaultForm = () => ({ deptId: undefined as number | undefined, parentId: 100 as any, deptName: '', orderNum: 0, leader: '', phone: '', email: '', status: 0 })
const form = reactive<SysDept & any>(defaultForm())
const rules: FormRules = {
  parentId: [{ required: true, message: '请选择上级部门', trigger: 'change' }],
  deptName: [{ required: true, message: '请输入部门名', trigger: 'blur' }, { min: 2, max: 30, message: '2~30 字符', trigger: 'blur' }],
  orderNum: [{ required: true, message: '请输入排序', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

/** row=undefined=顶级新增; row 已存在=在其下新增子 */
function handleAdd(row?: SysDept) {
  Object.assign(form, defaultForm())
  dialog.isEdit = false
  form.parentId = row ? row.deptId : 100
  // 编辑时排除自己及子级
  parentOptions.value = []
  deptApi.excludeTree(dialog.isEdit ? form.deptId : undefined).then(r => { parentOptions.value = r as any })
  dialog.visible = true
}
async function handleEdit(row: SysDept) {
  Object.assign(form, defaultForm(), JSON.parse(JSON.stringify(row)))
  dialog.isEdit = true
  parentOptions.value = await deptApi.excludeTree(row.deptId) as any
  dialog.visible = true
}
async function handleRemove(row: SysDept) {
  try {
    await deptApi.remove(row.deptId!)
    ElMessage.success('删除成功')
    reload()
  } catch {}
}
async function onSubmit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  submitLoading.value = true
  try {
    if (dialog.isEdit) { await deptApi.edit(form as any); ElMessage.success('修改成功') }
    else { await deptApi.add(form as any); ElMessage.success('新增成功') }
    dialog.visible = false
    reload()
  } finally { submitLoading.value = false }
}
onMounted(reload)
</script>
<style lang="scss" scoped>.mb-16{margin-bottom:16px;}</style>
