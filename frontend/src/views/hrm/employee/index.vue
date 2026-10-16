<template>
  <div class="pm-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工号">
          <el-input v-model="q.employeeNo" placeholder="工号" clearable @keyup.enter="loadList" style="width:130px" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="q.name" placeholder="姓名" clearable @keyup.enter="loadList" style="width:120px" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="q.phone" placeholder="手机号" clearable @keyup.enter="loadList" style="width:130px" />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="q.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', value: 'deptId', children: 'children' }"
            placeholder="全部" clearable check-strictly
            style="width:150px"
            @change="loadList"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:100px" @change="loadList">
            <el-option label="在职" :value="1" />
            <el-option label="试用期" :value="2" />
            <el-option label="离职" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
          <el-button type="success" :icon="Plus" v-hasPermi="'hrm:employee:save'" @click="openAdd">新增员工</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="employeeId">
        <el-table-column label="工号" prop="employeeNo" width="120" />
        <el-table-column label="姓名" prop="name" width="100" />
        <el-table-column label="性别" width="70" align="center">
          <template #default="{ row }">{{ row.gender === 1 ? '女' : '男' }}</template>
        </el-table-column>
        <el-table-column label="部门" prop="deptName" width="140" show-overflow-tooltip />
        <el-table-column label="岗位" prop="position" width="120" show-overflow-tooltip />
        <el-table-column label="手机" prop="phone" width="130" />
        <el-table-column label="入职日期" prop="entryDate" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">在职</el-tag>
            <el-tag v-else-if="row.status === 2" type="warning" size="small">试用期</el-tag>
            <el-tag v-else type="info" size="small">离职</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'hrm:employee:list'" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" v-hasPermi="'hrm:employee:save'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="'hrm:employee:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增员工' : '编辑员工'" width="720px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" prop="employeeNo">
              <el-input v-model="editDlg.form.employeeNo" placeholder="工号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="editDlg.form.name" placeholder="姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-radio-group v-model="editDlg.form.gender">
                <el-radio :value="0">男</el-radio>
                <el-radio :value="1">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门">
              <el-tree-select
                v-model="editDlg.form.deptId"
                :data="deptTree"
                :props="{ label: 'deptName', value: 'deptId', children: 'children' }"
                placeholder="请选择部门" clearable check-strictly
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-input v-model="editDlg.form.position" placeholder="岗位" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="editDlg.form.phone" placeholder="手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="editDlg.form.email" placeholder="邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身份证号">
              <el-input v-model="editDlg.form.idCard" placeholder="身份证号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期">
              <el-date-picker v-model="editDlg.form.entryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="离职日期">
              <el-date-picker v-model="editDlg.form.leaveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="editDlg.form.status" style="width:100%">
                <el-option label="在职" :value="1" />
                <el-option label="试用期" :value="2" />
                <el-option label="离职" :value="3" />
              </el-select>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDlg.show" :title="'员工档案 - ' + (detailDlg.data?.name || '')" width="760px" destroy-on-close>
      <el-descriptions :column="3" border v-loading="detailDlg.loading">
        <el-descriptions-item label="工号">{{ detailDlg.data?.employeeNo }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detailDlg.data?.name }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ detailDlg.data?.gender === 1 ? '女' : '男' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ detailDlg.data?.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ detailDlg.data?.position || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailDlg.data?.status === 1" type="success" size="small">在职</el-tag>
          <el-tag v-else-if="detailDlg.data?.status === 2" type="warning" size="small">试用期</el-tag>
          <el-tag v-else type="info" size="small">离职</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="手机">{{ detailDlg.data?.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱" :span="2">{{ detailDlg.data?.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detailDlg.data?.idCard || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入职日期">{{ detailDlg.data?.entryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="离职日期">{{ detailDlg.data?.leaveDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detailDlg.data?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 附件管理 -->
      <el-divider content-position="left">合同与证件</el-divider>
      <div class="attach-upload">
        <el-upload :show-file-list="false" :before-upload="beforeUpload" :http-request="handleUpload" drag multiple>
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持劳动合同、身份证、学历证书、资格证书等，单个文件不超过 50MB</div>
          </template>
        </el-upload>
      </div>
      <el-table :data="detailDlg.attachments" v-loading="detailDlg.attachLoading" border stripe size="small" style="margin-top:12px">
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">{{ attachTypeLabel(row.attachType) }}</template>
        </el-table-column>
        <el-table-column label="文件名" prop="fileName" min-width="180" show-overflow-tooltip />
        <el-table-column label="大小" width="100" align="right">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传时间" prop="createTime" width="160" align="center" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'hrm:employee:list'" @click="downloadAttach(row)">下载</el-button>
            <el-button link type="danger" v-hasPermi="'hrm:employee:remove'" @click="removeAttach(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus, UploadFilled } from '@element-plus/icons-vue'
import { hrmEmployeeApi, hrmEmployeeAttachmentApi } from '@/api'
import type { HrmEmployee, HrmEmployeeAttachment } from '@/types/models'

const q = reactive({
  pageNum: 1, pageSize: 20,
  employeeNo: '', name: '', phone: '',
  deptId: undefined as number | undefined,
  status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as HrmEmployee[], total: 0 } })
const deptTree = ref<any[]>([])

async function loadDeptTree() {
  deptTree.value = (await hrmEmployeeApi.deptTree()) || []
}

async function loadList() {
  list.loading = true
  try {
    const res = await hrmEmployeeApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) { ElMessage.error(e.message || '查询失败') }
  finally { list.loading = false }
}

function resetQ() {
  Object.assign(q, { employeeNo: '', name: '', phone: '', deptId: undefined, status: undefined, pageNum: 1 })
  loadList()
}

const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<HrmEmployee> })
const editRules = {
  employeeNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

function emptyForm(): Partial<HrmEmployee> {
  return { employeeNo: '', name: '', gender: 0, phone: '', email: '', idCard: '', deptId: undefined, position: '', entryDate: '', leaveDate: '', status: 1, remark: '' }
}

function openAdd() {
  editDlg.isNew = true
  editDlg.form = emptyForm()
  editDlg.show = true
}

function openEdit(row: HrmEmployee) {
  editDlg.isNew = false
  editDlg.form = { ...row }
  editDlg.show = true
}

async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await hrmEmployeeApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await hrmEmployeeApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false
    loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

async function handleRemove(row: HrmEmployee) {
  try { await ElMessageBox.confirm(`确定删除员工「${row.name}」吗？`, '提示', { type: 'warning' }) }
  catch { return }
  try { await hrmEmployeeApi.remove(row.employeeId!); ElMessage.success('删除成功'); loadList() }
  catch (e: any) { ElMessage.error(e.message || '删除失败') }
}

/* ============ 详情 + 附件 ============ */
const detailDlg = reactive({
  show: false, loading: false, attachLoading: false,
  data: null as HrmEmployee | null,
  attachments: [] as HrmEmployeeAttachment[]
})

async function openDetail(row: HrmEmployee) {
  detailDlg.show = true
  detailDlg.loading = true
  try {
    detailDlg.data = await hrmEmployeeApi.get(row.employeeId!)
    await loadAttachments(row.employeeId!)
  } catch (e: any) { ElMessage.error(e.message || '加载失败') }
  finally { detailDlg.loading = false }
}

async function loadAttachments(employeeId: number) {
  detailDlg.attachLoading = true
  try {
    detailDlg.attachments = (await hrmEmployeeAttachmentApi.list(employeeId)) || []
  } catch (e: any) { ElMessage.error(e.message || '加载附件失败') }
  finally { detailDlg.attachLoading = false }
}

function beforeUpload(file: File) {
  if (file.size > 50 * 1024 * 1024) { ElMessage.error('文件不能超过 50MB'); return false }
  return true
}

async function handleUpload(options: any) {
  try {
    await hrmEmployeeAttachmentApi.upload(detailDlg.data!.employeeId!, 'other', options.file)
    ElMessage.success('上传成功')
    loadAttachments(detailDlg.data!.employeeId!)
  } catch (e: any) { ElMessage.error(e.message || '上传失败') }
}

function downloadAttach(row: HrmEmployeeAttachment) {
  hrmEmployeeAttachmentApi.download(row.attachmentId!).then((blob: any) => {
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName
    a.click()
    window.URL.revokeObjectURL(url)
  }).catch((e: any) => ElMessage.error(e.message || '下载失败'))
}

async function removeAttach(row: HrmEmployeeAttachment) {
  try { await ElMessageBox.confirm(`确定删除附件「${row.fileName}」吗？`, '提示', { type: 'warning' }) }
  catch { return }
  try {
    await hrmEmployeeAttachmentApi.remove(row.attachmentId!)
    ElMessage.success('删除成功')
    loadAttachments(detailDlg.data!.employeeId!)
  } catch (e: any) { ElMessage.error(e.message || '删除失败') }
}

function attachTypeLabel(t?: string) {
  const map: Record<string, string> = { contract: '劳动合同', id_card: '身份证', education: '学历证书', cert: '资格证书', other: '其他' }
  return t ? (map[t] || t) : '其他'
}

function formatSize(bytes?: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

onMounted(() => { loadDeptTree(); loadList() })
</script>
