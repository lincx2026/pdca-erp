<template>
  <div class="pm-page">
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <el-button type="success" :icon="Plus" v-hasPermi="'pm:category:save'" @click="openAdd(0)">新增分类</el-button>
        </div>
      </template>

      <el-table :data="tree" row-key="categoryId" :tree-props="{ children: 'children' }" border stripe default-expand-all>
        <el-table-column prop="categoryName" label="分类名称" min-width="220" />
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" v-hasPermi="'pm:category:save'" @click="openAdd(row.categoryId)">新增子项</el-button>
            <el-button link type="primary" size="small" v-hasPermi="'pm:category:save'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" v-hasPermi="'pm:category:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增分类' : '编辑分类'" width="520px" destroy-on-close>
      <el-form :model="editDlg.form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="上级分类">
          <el-tree-select
            v-model="editDlg.form.parentId"
            :data="parentOptions"
            :props="{ label: 'categoryName', value: 'categoryId', children: 'children' }"
            check-strictly
            clearable
            placeholder="顶级分类"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="editDlg.form.categoryName" placeholder="请输入分类名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editDlg.form.orderNum" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editDlg.form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editDlg.form.remark" type="textarea" :rows="3" placeholder="备注" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { projectCategoryApi, type PmProjectCategory } from '@/api'

const tree = ref<PmProjectCategory[]>([])
const parentOptions = ref<PmProjectCategory[]>([])

const formRef = ref()
const editDlg = reactive({
  show: false,
  isNew: true,
  saving: false,
  form: {} as Partial<PmProjectCategory>
})

const rules = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

async function loadTree() {
  const data = await projectCategoryApi.tree()
  tree.value = data || []
  // parentOptions 用于上级分类选择，不含当前编辑节点及其子节点（编辑时处理）
  parentOptions.value = data || []
}

function openAdd(parentId: number) {
  editDlg.isNew = true
  editDlg.form = { parentId, orderNum: 0, status: 0, remark: '' }
  editDlg.show = true
}

async function openEdit(row: PmProjectCategory) {
  editDlg.isNew = false
  const detail = await projectCategoryApi.get(row.categoryId!)
  editDlg.form = { ...detail }
  editDlg.show = true
}

async function submitEdit() {
  await formRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) {
      await projectCategoryApi.add(editDlg.form)
      ElMessage.success('新增成功')
    } else {
      await projectCategoryApi.edit(editDlg.form)
      ElMessage.success('修改成功')
    }
    editDlg.show = false
    loadTree()
  } finally {
    editDlg.saving = false
  }
}

async function handleRemove(row: PmProjectCategory) {
  await ElMessageBox.confirm(`确定删除分类「${row.categoryName}」吗？`, '提示', { type: 'warning' })
  await projectCategoryApi.remove(row.categoryId!)
  ElMessage.success('删除成功')
  loadTree()
}

onMounted(loadTree)
</script>
