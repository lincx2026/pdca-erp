<template>
  <div class="pdm-category-page">
    <!-- 搜索栏 -->
    <el-card class="search-bar" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent>
        <el-form-item label="分类名称">
          <el-input v-model="q.categoryName" placeholder="请输入名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTree" :icon="Search">搜索</el-button>
          <el-button @click="resetQ" :icon="RefreshRight">重置</el-button>
        </el-form-item>
        <el-form-item style="margin-left: auto">
          <el-button type="success" @click="openAdd(null)" :icon="Plus" v-if="canAdd">新增顶级分类</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 分类树表格 -->
    <el-card class="mt-16" shadow="never">
      <el-table
        ref="tblRef"
        v-loading="loading"
        :data="tree"
        row-key="categoryId"
        border
        stripe
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="categoryCode" label="分类编码" width="140">
          <template #default="{ row }">
            <el-tag type="info" effect="plain" size="small">{{ row.categoryCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类名称" min-width="220" />
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column prop="materialCount" label="物料数" width="90" align="center">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="gotoMaterial(row)">
              {{ row.materialCount ?? 0 }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else type="danger" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="320" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openAdd(row)" v-if="canAdd">新增子项</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)" v-if="canEdit">修改</el-button>
            <el-popconfirm title="确认删除该分类? 有子分类或物料则禁止" @confirm="remove(row)">
              <template #reference>
                <el-button link type="danger" size="small" v-if="canRemove">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dlg.visible" :title="dlg.isEdit ? '修改分类' : (dlg.parentId ? '新增子分类' : '新增顶级分类')" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级分类" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'categoryName', value: 'categoryId', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            placeholder="空 = 顶级分类"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="form.categoryCode" placeholder="如 RES / CAP / IC, 作为物料编码前缀" maxlength="30" show-word-limit>
            <template #append>
              <el-button size="small" link type="primary" @click="showPreview">预览格式</el-button>
            </template>
          </el-input>
          <div v-if="previewText" class="preview-hint">编码范围: <b>{{ previewText }}</b></div>
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="同级名称不可重复" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="显示顺序" prop="orderNum">
          <el-input-number v-model="form.orderNum" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, nextTick, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type TreeInstance } from 'element-plus'
import { Search, RefreshRight, Plus } from '@element-plus/icons-vue'
import { categoryApi, codeGenApi, type CategoryListQ } from '@/api'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'
import type { PdmMaterialCategory } from '@/types/models'

const router = useRouter()
const route = useRoute()
// 根据路由 meta.scope 决定当前是电子域(elec) 还是 成品域(fg) → 权限前缀不同
const SCOPE: 'elec' | 'fg' = (route.meta as any)?.scope === 'fg' ? 'fg' : 'elec'
const PERM_PREFIX = SCOPE === 'fg' ? 'pdm:fgcategory:' : 'pdm:category:'
const userPerms = computed(() => useUserStore().userInfo?.permissions || [])
const canAdd    = computed(() => hasPerm(PERM_PREFIX + 'add',    userPerms.value))
const canEdit   = computed(() => hasPerm(PERM_PREFIX + 'edit',   userPerms.value))
const canRemove = computed(() => hasPerm(PERM_PREFIX + 'remove', userPerms.value))

const tblRef = ref<TreeInstance>()
const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const tree = ref<PdmMaterialCategory[]>([])
const parentOptions = ref<PdmMaterialCategory[]>([])
const previewText = ref('')

const q = reactive<CategoryListQ>({
  categoryName: '',
  status: undefined,
  withMaterialCount: true
})

async function loadTree() {
  loading.value = true
  try {
    tree.value = await categoryApi.list(q) as any
  } finally { loading.value = false }
}
function resetQ() {
  q.categoryName = ''
  q.status = undefined
  loadTree()
}
function gotoMaterial(row: PdmMaterialCategory) {
  router.push({ path: '/pdm/material', query: { categoryId: String(row.categoryId) } })
}

// ============= 对话框 =============
const dlg = reactive<{ visible: boolean; isEdit: boolean; parentId: number | null }>({
  visible: false, isEdit: false, parentId: null
})
type F = Partial<PdmMaterialCategory>
const form = reactive<F>({
  parentId: 0,
  categoryCode: '',
  categoryName: '',
  orderNum: 0,
  status: 1,
  remark: ''
})
const rules: FormRules = {
  categoryCode: [{ required: true, message: '请输入分类编码前缀', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

function initForm(row: PdmMaterialCategory | null) {
  Object.keys(form).forEach(k => delete (form as any)[k])
  Object.assign(form, { parentId: 0, categoryCode: '', categoryName: '', orderNum: 0, status: 1, remark: '' })
  previewText.value = ''
}

async function openAdd(parent: PdmMaterialCategory | null) {
  initForm(null)
  dlg.isEdit = false
  dlg.parentId = parent ? parent.categoryId : null
  form.parentId = parent ? parent.categoryId : 0
  parentOptions.value = await categoryApi.excludeTree(null) as any
  dlg.visible = true
}

async function openEdit(row: PdmMaterialCategory) {
  initForm(null)
  dlg.isEdit = true
  dlg.parentId = row.parentId
  const detail = await categoryApi.get(row.categoryId) as any
  Object.assign(form, detail)
  // 排除自身及子级, 防止选自己做父级
  parentOptions.value = await categoryApi.excludeTree(row.categoryId) as any
  dlg.visible = true
}

async function showPreview() {
  if (!form.categoryCode) { ElMessage.warning('请先填分类编码'); return }
  // 必须先存过分类才能预览 (后端需查 DB 取 categoryId)
  if (dlg.isEdit && form.categoryId) {
    previewText.value = (await codeGenApi.preview(form.categoryId) as any) || ''
  } else {
    previewText.value = `保存后可预览完整号段格式 (例: ${form.categoryCode.toUpperCase()}-${String(new Date().getFullYear() % 100).padStart(2, '0')}-0001)`
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dlg.isEdit) {
      await categoryApi.edit(form as any)
      ElMessage.success('修改成功')
    } else {
      await categoryApi.add(form as any)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    loadTree()
  } finally { submitting.value = false }
}

async function remove(row: PdmMaterialCategory) {
  try {
    await categoryApi.remove(row.categoryId)
    ElMessage.success('删除成功')
    loadTree()
  } catch {}
}

onMounted(loadTree)
</script>

<style scoped lang="scss">
.pdm-category-page {
  .search-bar { margin-bottom: 16px }
  .mt-16 { margin-top: 0 }
  .preview-hint { color: #909399; font-size: 12px; margin-top: 4px }
}
</style>
