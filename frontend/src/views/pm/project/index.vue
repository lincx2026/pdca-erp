<template>
  <div class="pm-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="项目编号">
          <el-input v-model="q.projectCode" placeholder="项目编号" clearable @keyup.enter="loadList" style="width:160px" />
        </el-form-item>
        <el-form-item label="项目名称">
          <el-input v-model="q.projectName" placeholder="项目名称" clearable @keyup.enter="loadList" style="width:160px" />
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="q.customerName" placeholder="客户名称" clearable @keyup.enter="loadList" style="width:140px" />
        </el-form-item>
        <el-form-item label="项目经理">
          <el-input v-model="q.manager" placeholder="项目经理" clearable @keyup.enter="loadList" style="width:120px" />
        </el-form-item>
        <el-form-item label="项目分类">
          <el-tree-select
            v-model="q.categoryId"
            :data="categoryTree"
            :props="{ label: 'categoryName', value: 'categoryId', children: 'children' }"
            placeholder="全部" clearable check-strictly
            style="width:160px"
            @change="loadList"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:110px" @change="loadList">
            <el-option label="待启动" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已完成" :value="2" />
            <el-option label="已取消" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="q.priority" placeholder="全部" clearable style="width:90px" @change="loadList">
            <el-option label="高" value="H" />
            <el-option label="中" value="M" />
            <el-option label="低" value="L" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
          <el-button type="success" :icon="Plus" v-hasPermi="'pm:project:save'" @click="openAdd">新增项目</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表/看板切换 -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />项目管理</div>
          <div>
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button value="list">列表</el-radio-button>
              <el-radio-button value="kanban">看板</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>

      <!-- 列表视图 -->
      <el-table v-if="viewMode === 'list'" :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="projectId">
        <el-table-column label="编号" prop="projectCode" width="160" />
        <el-table-column label="项目名称" prop="projectName" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户" prop="customerName" width="140" show-overflow-tooltip />
        <el-table-column label="产品" prop="productName" width="140" show-overflow-tooltip />
        <el-table-column label="分类" width="110" align="center">
          <template #default="{ row }">{{ categoryNameMap[row.categoryId] || '-' }}</template>
        </el-table-column>
        <el-table-column label="项目经理" prop="manager" width="100" align="center" />
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.priority === 'H'" type="danger" size="small">高</el-tag>
            <el-tag v-else-if="row.priority === 'L'" type="info" size="small">低</el-tag>
            <el-tag v-else type="warning" size="small">中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">待启动</el-tag>
            <el-tag v-else-if="row.status === 1" type="primary" size="small">进行中</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">已完成</el-tag>
            <el-tag v-else type="danger" size="small">已取消</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始日期" prop="startDate" width="110" />
        <el-table-column label="计划完成" prop="endDate" width="110" />
        <el-table-column label="预算" prop="budget" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.budget) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'pm:project:list'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" v-hasPermi="'pm:project:list'" @click="openAttach(row)">附件</el-button>
            <el-button v-if="row.status === 0" link type="primary" v-hasPermi="'pm:project:save'" @click="changeStatus(row, 1)">启动</el-button>
            <el-button v-if="row.status === 1" link type="success" v-hasPermi="'pm:project:save'" @click="changeStatus(row, 2)">完成</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" link type="warning" v-hasPermi="'pm:project:save'" @click="changeStatus(row, 3)">取消</el-button>
            <el-button link type="danger" v-hasPermi="'pm:project:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 看板视图 -->
      <div v-else class="kanban-board" v-loading="list.loading">
        <div v-for="col in kanbanColumns" :key="col.status" class="kanban-col">
          <div class="kanban-col-header">
            <el-tag :type="col.tagType" size="small">{{ col.label }}</el-tag>
            <span class="kanban-count">{{ kanbanData[col.status]?.length || 0 }}</span>
          </div>
          <div class="kanban-cards">
            <div v-for="row in kanbanData[col.status] || []" :key="row.projectId" class="kanban-card">
              <div class="kanban-card-title">
                <span class="k-code">{{ row.projectCode }}</span>
                <el-tag v-if="row.priority === 'H'" type="danger" size="small" effect="plain">高</el-tag>
                <el-tag v-else-if="row.priority === 'L'" type="info" size="small" effect="plain">低</el-tag>
              </div>
              <div class="kanban-card-name">{{ row.projectName }}</div>
              <div class="kanban-card-meta">
                <span>客户: {{ row.customerName || '-' }}</span>
                <span>经理: {{ row.manager || '-' }}</span>
              </div>
              <div class="kanban-card-meta" v-if="row.endDate">
                <span>截止: {{ row.endDate }}</span>
                <span>预算: {{ formatMoney(row.budget) }}</span>
              </div>
              <div class="kanban-card-actions">
                <el-button v-if="row.status === 0" link type="primary" size="small" v-hasPermi="'pm:project:save'" @click="changeStatus(row, 1)">启动</el-button>
                <el-button v-if="row.status === 1" link type="success" size="small" v-hasPermi="'pm:project:save'" @click="changeStatus(row, 2)">完成</el-button>
                <el-button v-if="row.status === 0 || row.status === 1" link type="warning" size="small" v-hasPermi="'pm:project:save'" @click="changeStatus(row, 3)">取消</el-button>
                <el-button link type="primary" size="small" v-hasPermi="'pm:project:list'" @click="openEdit(row)">编辑</el-button>
                <el-button link type="success" size="small" v-hasPermi="'pm:project:list'" @click="openAttach(row)">附件</el-button>
              </div>
            </div>
            <el-empty v-if="!kanbanData[col.status]?.length" :image-size="60" description="暂无项目" />
          </div>
        </div>
      </div>

      <el-pagination v-if="viewMode === 'list'" class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增项目' : '编辑项目'" width="720px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目编号">
              <el-input v-model="editDlg.form.projectCode" placeholder="保存时自动生成" :disabled="!editDlg.isNew" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目名称" prop="projectName">
              <el-input v-model="editDlg.form.projectName" placeholder="请输入项目名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称">
              <el-input v-model="editDlg.form.customerName" placeholder="客户名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品名称">
              <el-input v-model="editDlg.form.productName" placeholder="产品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目经理">
              <el-input v-model="editDlg.form.manager" placeholder="项目经理" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目分类">
              <el-tree-select
                v-model="editDlg.form.categoryId"
                :data="categoryTree"
                :props="{ label: 'categoryName', value: 'categoryId', children: 'children' }"
                placeholder="请选择项目分类" clearable check-strictly
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-select v-model="editDlg.form.priority" style="width:100%">
                <el-option label="高" value="H" />
                <el-option label="中" value="M" />
                <el-option label="低" value="L" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" v-if="!editDlg.isNew">
              <el-select v-model="editDlg.form.status" style="width:100%">
                <el-option label="待启动" :value="0" />
                <el-option label="进行中" :value="1" />
                <el-option label="已完成" :value="2" />
                <el-option label="已取消" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="editDlg.form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划完成日期">
              <el-date-picker v-model="editDlg.form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目预算">
              <el-input-number v-model="editDlg.form.budget" :min="0" :precision="2" :step="1000" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="项目描述">
              <el-input v-model="editDlg.form.description" type="textarea" :rows="3" placeholder="项目描述" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editDlg.form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 附件管理弹窗 -->
    <el-dialog v-model="attachDlg.show" :title="'附件管理 - ' + (attachDlg.projectName || '')" width="720px" destroy-on-close>
      <div class="attach-upload">
        <el-upload
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="handleUpload"
          drag
          multiple>
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持任意格式文件，单个文件不超过 50MB</div>
          </template>
        </el-upload>
      </div>
      <el-table :data="attachDlg.list" v-loading="attachDlg.loading" border stripe>
        <el-table-column label="文件名" prop="fileName" min-width="180" show-overflow-tooltip />
        <el-table-column label="大小" width="100" align="right">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传人" prop="createBy" width="90" align="center" />
        <el-table-column label="上传时间" prop="createTime" width="160" align="center" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'pm:project:list'" @click="downloadAttach(row)">下载</el-button>
            <el-button link type="danger" v-hasPermi="'pm:project:remove'" @click="removeAttach(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus, UploadFilled } from '@element-plus/icons-vue'
import { projectApi, projectAttachmentApi, projectCategoryApi } from '@/api'
import type { PmProject, PmProjectAttachment, PmProjectCategory } from '@/types/models'

const viewMode = ref<'list' | 'kanban'>('list')

const q = reactive({
  pageNum: 1, pageSize: 20,
  projectCode: '', projectName: '', customerName: '',
  manager: '', categoryId: undefined as number | undefined,
  status: undefined as number | undefined, priority: ''
})
const list = reactive({ loading: false, data: { rows: [] as PmProject[], total: 0 } })

// 项目分类
const categoryTree = ref<PmProjectCategory[]>([])
const categoryNameMap = computed(() => {
  const map: Record<number, string> = {}
  const walk = (nodes: PmProjectCategory[]) => {
    for (const n of nodes) {
      map[n.categoryId!] = n.categoryName
      if (n.children) walk(n.children)
    }
  }
  walk(categoryTree.value)
  return map
})
async function loadCategoryTree() {
  categoryTree.value = (await projectCategoryApi.tree()) || []
}

const kanbanColumns = [
  { status: 0, label: '待启动', tagType: 'info' },
  { status: 1, label: '进行中', tagType: 'primary' },
  { status: 2, label: '已完成', tagType: 'success' },
  { status: 3, label: '已取消', tagType: 'danger' }
]
const kanbanData = computed(() => {
  const map: Record<number, PmProject[]> = { 0: [], 1: [], 2: [], 3: [] }
  for (const r of list.data.rows) {
    const s = r.status ?? 0
    if (!map[s]) map[s] = []
    map[s].push(r)
  }
  return map
})

function formatMoney(v: any) {
  if (v == null || v === '') return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadList() {
  list.loading = true
  try {
    const res = await projectApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) { ElMessage.error(e.message || '查询失败') }
  finally { list.loading = false }
}

function resetQ() {
  Object.assign(q, { projectCode: '', projectName: '', customerName: '', manager: '', categoryId: undefined, status: undefined, priority: '', pageNum: 1 })
  loadList()
}

/* ============ 新增/编辑 ============ */
const editFormRef = ref<FormInstance>()
const editDlg = reactive({
  show: false, isNew: true, saving: false,
  form: {} as Partial<PmProject>
})
const editRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

function emptyForm(): Partial<PmProject> {
  return { projectCode: '', projectName: '', customerName: '', productName: '', manager: '', categoryId: undefined, priority: 'M', status: 0, startDate: '', endDate: '', budget: 0, description: '', remark: '' }
}

function openAdd() {
  editDlg.isNew = true
  editDlg.form = emptyForm()
  editDlg.show = true
}

function openEdit(row: PmProject) {
  editDlg.isNew = false
  editDlg.form = { ...row }
  editDlg.show = true
}

async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) {
      await projectApi.add(editDlg.form)
      ElMessage.success('新增成功')
    } else {
      await projectApi.edit(editDlg.form)
      ElMessage.success('修改成功')
    }
    editDlg.show = false
    loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

/* ============ 删除 ============ */
async function handleRemove(row: PmProject) {
  try {
    await ElMessageBox.confirm(`确定删除项目「${row.projectName}」吗？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await projectApi.remove(row.projectId!)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) { ElMessage.error(e.message || '删除失败') }
}

/* ============ 状态流转 ============ */
async function changeStatus(row: PmProject, target: number) {
  const label = { 1: '启动', 2: '完成', 3: '取消' }[target]
  try {
    await ElMessageBox.confirm(`确定将项目「${row.projectName}」${label}吗？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await projectApi.changeStatus(row.projectId!, target)
    ElMessage.success(`${label}成功`)
    loadList()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
}

/* ============ 附件管理 ============ */
const attachDlg = reactive({
  show: false, projectId: 0, projectName: '',
  list: [] as PmProjectAttachment[], loading: false
})

function openAttach(row: PmProject) {
  attachDlg.projectId = row.projectId!
  attachDlg.projectName = row.projectName
  attachDlg.show = true
  loadAttachments()
}

async function loadAttachments() {
  attachDlg.loading = true
  try {
    attachDlg.list = await projectAttachmentApi.list(attachDlg.projectId) || []
  } catch (e: any) { ElMessage.error(e.message || '加载附件失败') }
  finally { attachDlg.loading = false }
}

function beforeUpload(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 50MB')
    return false
  }
  return true
}

async function handleUpload(options: any) {
  try {
    const att = await projectAttachmentApi.upload(attachDlg.projectId, options.file)
    if (att) {
      ElMessage.success('上传成功')
      loadAttachments()
    }
  } catch (e: any) { ElMessage.error(e.message || '上传失败') }
}

async function downloadAttach(row: PmProjectAttachment) {
  try {
    const blob: any = await projectAttachmentApi.download(row.attachmentId!)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e: any) { ElMessage.error(e.message || '下载失败') }
}

async function removeAttach(row: PmProjectAttachment) {
  try {
    await ElMessageBox.confirm(`确定删除附件「${row.fileName}」吗？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await projectAttachmentApi.remove(row.attachmentId!)
    ElMessage.success('删除成功')
    loadAttachments()
  } catch (e: any) { ElMessage.error(e.message || '删除失败') }
}

function formatSize(bytes: any) {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

onMounted(() => { loadCategoryTree(); loadList() })
</script>

<style scoped>
.pm-page { padding: 12px; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.title { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; }
.pager { margin-top: 12px; justify-content: flex-end; display: flex; }

/* 看板样式 */
.kanban-board { display: flex; gap: 12px; min-height: 400px; overflow-x: auto; }
.kanban-col { flex: 1; min-width: 260px; background: #f5f7fa; border-radius: 6px; padding: 10px; display: flex; flex-direction: column; }
.kanban-col-header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #ebeef5; }
.kanban-count { font-size: 12px; color: #909399; background: #fff; padding: 1px 8px; border-radius: 10px; }
.kanban-cards { flex: 1; overflow-y: auto; }
.kanban-card { background: #fff; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
.kanban-card-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.k-code { font-size: 12px; color: #909399; font-family: monospace; }
.kanban-card-name { font-weight: 600; font-size: 14px; margin-bottom: 6px; color: #303133; }
.kanban-card-meta { font-size: 12px; color: #606266; display: flex; justify-content: space-between; margin-bottom: 4px; }
.kanban-card-actions { display: flex; gap: 4px; margin-top: 8px; padding-top: 8px; border-top: 1px dashed #ebeef5; }

/* 附件上传 */
.attach-upload { margin-bottom: 16px; }
.attach-upload :deep(.el-upload-dragger) { padding: 16px; }
</style>
