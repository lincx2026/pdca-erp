<template>
  <div class="page">
    <!-- 搜索条 -->
    <el-card shadow="never" class="mb-16">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题"><el-input v-model="query.title" clearable placeholder="标题关键字" style="width:220px" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.noticeType" clearable placeholder="全部" style="width:140px">
            <el-option v-for="t in TYPE_OPT" :key="t[0]" :label="t[1]" :value="t[0]" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.noticeLevel" clearable placeholder="全部" style="width:120px">
            <el-option v-for="t in LV_OPT" :key="t[0]" :label="t[1]" :value="t[0]" />
          </el-select>
        </el-form-item>
        <el-form-item label="发送状态">
          <el-select v-model="query.sendStatus" clearable placeholder="全部" style="width:120px">
            <el-option v-for="t in SEND_OPT" :key="t[0]" :label="t[1]" :value="t[0]" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page=1;reload()">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" :icon="Plus" v-hasPermi="'system:notice:add'" @click="openEdit(null)">新增通知</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column label="置顶" width="60" align="center">
          <template #default="{row}">
            <el-icon v-if="row.isTop===1" color="#f59e0b"><Top /></el-icon>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="ID" prop="noticeId" width="70" align="right" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{row}">
            <el-tag :type="typeTag(row.noticeType)" size="small">{{ typeLabel(row.noticeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="lvTag(row.noticeLevel)" size="small" effect="plain">{{ lvLabel(row.noticeLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="240" show-overflow-tooltip>
          <template #default="{row}">
            <span>{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="接收对象" width="110" align="center">
          <template #default="{row}">{{ recvLabel(row.receiverType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="sendTag(row.sendStatus)" size="small">{{ sendLabel(row.sendStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已读/未读" width="110" align="center">
          <template #default="{row}">
            <span style="color:#10b981">{{ row.readCount ?? 0 }}</span>
            <span style="color:#9ca3af"> / </span>
            <span style="color:#ef4444">{{ row.unreadCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发布人/时间" min-width="160">
          <template #default="{row}">
            <div>{{ row.sendByName || '-' }}</div>
            <div class="sub">{{ row.sendTime || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="160" />
        <el-table-column label="操作" width="340" fixed="right" align="center">
          <template #default="{row}">
            <el-button link type="primary" v-hasPermi="'system:notice:query'" @click="viewNotice(row)">查看</el-button>
            <el-button link type="primary" v-hasPermi="'system:notice:edit'"
              :disabled="row.sendStatus === 1"
              @click="openEdit(row)">{{ row.sendStatus === 1 ? '已发布不可编辑' : '编辑' }}</el-button>
            <el-button link type="success" v-hasPermi="'system:notice:send'"
              v-if="row.sendStatus !== 1"
              @click="doPublish(row, true)">发布</el-button>
            <el-button link type="warning" v-hasPermi="'system:notice:send'"
              v-else @click="doPublish(row, false)">撤回</el-button>
            <el-button link type="info" v-hasPermi="'system:notice:read'" @click="viewReads(row)">已读记录</el-button>
            <el-popconfirm title="确定删除?" @confirm="removeRow(row)" :disabled="row.sendStatus === 1">
              <template #reference>
                <el-button link type="danger" v-hasPermi="'system:notice:remove'"
                  :disabled="row.sendStatus === 1">删除</el-button>
              </template>
              <template #disabled>
                <el-button link type="danger" disabled>已发布不可删</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt-16 center"
        background v-model:current-page="page" v-model:page-size="pageSize"
        :page-sizes="[10,20,50,100]" layout="total,sizes,prev,pager,next,jumper"
        :total="total" @size-change="page=1;reload()" @current-change="reload()" />
    </el-card>

    <!-- 新增/编辑 对话框 -->
    <el-dialog v-model="editDialogVisible" :title="form.noticeId ? '编辑通知' : '新增通知'" width="780px" destroy-on-close>
      <el-form :model="form" label-width="100px" size="default">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="通知类型" required>
              <el-select v-model="form.noticeType" style="width:100%">
                <el-option v-for="t in TYPE_OPT" :key="t[0]" :label="t[1]" :value="t[0]" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通知级别" required>
              <el-select v-model="form.noticeLevel" style="width:100%">
                <el-option v-for="t in LV_OPT" :key="t[0]" :label="t[1]" :value="t[0]" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="18">
            <el-form-item label="标题" required>
              <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="通知标题" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="是否置顶">
              <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit
            placeholder="简短摘要,用于卡片/列表展示" />
        </el-form-item>
        <el-form-item label="正文内容">
          <el-input v-model="form.content" type="textarea" :rows="8" placeholder="支持HTML或纯文本内容" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="接收对象" required>
              <el-select v-model="form.receiverType" style="width:100%" @change="onRecvTypeChange">
                <el-option label="全体人员" :value="1" />
                <el-option label="指定角色" :value="2" />
                <el-option label="指定用户" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发送状态">
              <el-select v-model="form.sendStatus" style="width:100%">
                <el-option label="存为草稿" :value="0" />
                <el-option label="立即发布" :value="1" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16" v-if="form.receiverType === 2">
          <el-col :span="24">
            <el-form-item label="接收角色">
              <el-select v-model="roleIds" multiple filterable collapse-tags collapse-tags-tooltip
                style="width:100%" placeholder="请选择接收角色(多选)">
                <el-option v-for="r in roleList" :key="r.roleId" :label="r.roleName" :value="r.roleId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16" v-if="form.receiverType === 3">
          <el-col :span="24">
            <el-form-item label="接收用户">
              <el-select v-model="userIds" multiple filterable collapse-tags collapse-tags-tooltip
                style="width:100%" placeholder="请选择接收用户(多选)">
                <el-option v-for="u in userList" :key="u.userId" :label="u.nickName + ' (' + u.userName + ')'" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="生效开始">
              <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="不填表示立即可见" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="生效结束">
              <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="不填表示永久" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="跳转路由">
              <el-input v-model="form.actionRoute" placeholder="/xxx/xxx,点击通知后跳转" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog v-model="viewDialogVisible" title="通知详情" width="720px" destroy-on-close>
      <template v-if="current">
        <div class="notice-header">
          <div class="notice-title">
            <el-tag v-if="current.isTop===1" type="warning" effect="dark" style="margin-right:8px">置顶</el-tag>
            <el-tag :type="lvTag(current.noticeLevel)" size="small" style="margin-right:8px">{{ lvLabel(current.noticeLevel) }}</el-tag>
            <span>{{ current.title }}</span>
          </div>
          <div class="notice-meta">
            <el-tag :type="typeTag(current.noticeType)" size="small">{{ typeLabel(current.noticeType) }}</el-tag>
            <span class="sp">|</span>
            <span>{{ recvLabel(current.receiverType) }}</span>
            <span class="sp">|</span>
            <span>{{ current.sendByName || '-' }} 发布于 {{ current.sendTime || current.createTime }}</span>
          </div>
        </div>
        <el-divider style="margin:10px 0 16px 0" />
        <div v-if="current.summary" class="notice-summary"><b>摘要：</b>{{ current.summary }}</div>
        <div v-if="current.content" class="notice-content" v-html="current.content"></div>
        <div v-if="!current.content && !current.summary" class="empty">（无正文内容）</div>
        <el-divider style="margin:20px 0 10px 0" />
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="已读人数">{{ current.readCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="未读人数">{{ current.unreadCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="生效时间">{{ current.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="失效时间">{{ current.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态" :span="2">
            <el-tag :type="sendTag(current.sendStatus)" size="small">{{ sendLabel(current.sendStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="跳转路由" :span="2">{{ current.actionRoute || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>

    <!-- 已读记录对话框 -->
    <el-dialog v-model="readDialogVisible" title="已读人员列表" width="560px" destroy-on-close>
      <el-table :data="readList" v-loading="readLoading" border stripe size="small" max-height="420">
        <el-table-column label="序号" type="index" width="60" />
        <el-table-column label="用户ID" prop="userId" width="80" align="right" />
        <el-table-column label="用户姓名" prop="userName" />
        <el-table-column label="已读时间" prop="readTime" width="170" />
      </el-table>
      <div v-if="readList.length === 0 && !readLoading" class="empty mt-16">暂无已读记录</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Top } from '@element-plus/icons-vue'
import { noticeApi, roleApi, userApi } from '@/api'
import type { SysNotice, SysNoticeRead, SysRole, SysUser } from '@/types/models'

const loading = ref(false)
const rows = ref<SysNotice[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const query = reactive<{ noticeType?: number; noticeLevel?: number; sendStatus?: number; title?: string }>({})

const TYPE_OPT: [number, string][] = [[1, '系统公告'], [2, '业务通知'], [3, '预警提醒'], [4, '审批待办']]
const LV_OPT: [number, string][] = [[1, '普通'], [2, '重要'], [3, '紧急']]
const SEND_OPT: [number, string][] = [[0, '草稿'], [1, '已发布'], [2, '已撤回']]
const RECV_OPT: [number, string][] = [[1, '全体'], [2, '指定角色'], [3, '指定用户']]

const typeLabel = (n?: number) => TYPE_OPT.find(t => t[0] === n)?.[1] || '-'
const lvLabel = (n?: number) => LV_OPT.find(t => t[0] === n)?.[1] || '-'
const sendLabel = (n?: number) => SEND_OPT.find(t => t[0] === n)?.[1] || '-'
const recvLabel = (n?: number) => RECV_OPT.find(t => t[0] === n)?.[1] || '-'

function typeTag(n?: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (n === 1) return 'primary'
  if (n === 2) return 'success'
  if (n === 3) return 'danger'
  if (n === 4) return 'warning'
  return 'info'
}
function lvTag(n?: number): '' | 'success' | 'warning' | 'danger' {
  if (n === 2) return 'warning'
  if (n === 3) return 'danger'
  return ''
}
function sendTag(n?: number): 'info' | 'success' | 'danger' {
  if (n === 1) return 'success'
  if (n === 2) return 'danger'
  return 'info'
}

async function reload() {
  loading.value = true
  try {
    const res: any = await noticeApi.list({
      pageNum: page.value, pageSize: pageSize.value,
      noticeType: query.noticeType, noticeLevel: query.noticeLevel,
      sendStatus: query.sendStatus, title: query.title
    })
    rows.value = res.rows || []
    total.value = Number(res.total || 0)
  } finally { loading.value = false }
}
function resetQuery() {
  query.noticeType = undefined; query.noticeLevel = undefined
  query.sendStatus = undefined; query.title = ''
  page.value = 1; reload()
}
async function removeRow(row: SysNotice) {
  await noticeApi.remove(row.noticeId!)
  ElMessage.success('删除成功'); reload()
}
async function doPublish(row: SysNotice, pub: boolean) {
  const tip = pub ? `确定发布通知《${row.title}》?` : `确定撤回该通知? 撤回后用户端不可见。`
  await ElMessageBox.confirm(tip, '提示', { type: 'warning' })
  await noticeApi.publish(row.noticeId!, pub)
  ElMessage.success(pub ? '已发布' : '已撤回'); reload()
}

// ====== 查看详情 ======
const viewDialogVisible = ref(false)
const current = ref<SysNotice | null>(null)
async function viewNotice(row: SysNotice) {
  const res: any = await noticeApi.get(row.noticeId!)
  current.value = Object.assign({}, row, res)
  viewDialogVisible.value = true
}

// ====== 已读记录 ======
const readDialogVisible = ref(false)
const readLoading = ref(false)
const readList = ref<SysNoticeRead[]>([])
async function viewReads(row: SysNotice) {
  readLoading.value = true
  try {
    readList.value = (await noticeApi.readList(row.noticeId!)) as any || []
    readDialogVisible.value = true
  } finally { readLoading.value = false }
}

// ====== 新增/编辑 ======
const editDialogVisible = ref(false)
const form = reactive<Partial<SysNotice>>({})
const roleIds = ref<number[]>([])
const userIds = ref<number[]>([])
const roleList = ref<SysRole[]>([])
const userList = ref<SysUser[]>([])

async function loadOptions() {
  if (roleList.value.length === 0) roleList.value = (await roleApi.options()) as any || []
  if (userList.value.length === 0) userList.value = (await userApi.options()) as any || []
}
function onRecvTypeChange() {
  roleIds.value = []; userIds.value = []
}
function openEdit(row: SysNotice | null) {
  loadOptions()
  if (row) {
    Object.assign(form, row)
    roleIds.value = (row.receiverRoles || '').split(',').filter(Boolean).map(Number)
    userIds.value = (row.receiverUsers || '').split(',').filter(Boolean).map(Number)
  } else {
    Object.keys(form).forEach(k => delete (form as any)[k])
    form.noticeType = 1; form.noticeLevel = 1
    form.receiverType = 1; form.sendStatus = 0; form.isTop = 0
    roleIds.value = []; userIds.value = []
  }
  editDialogVisible.value = true
}
async function submitEdit() {
  if (!form.title) return ElMessage.warning('请填写标题')
  if (form.receiverType === 2 && roleIds.value.length === 0) return ElMessage.warning('请至少选择一个接收角色')
  if (form.receiverType === 3 && userIds.value.length === 0) return ElMessage.warning('请至少选择一个接收用户')
  form.receiverRoles = form.receiverType === 2 ? roleIds.value.join(',') : ''
  form.receiverUsers = form.receiverType === 3 ? userIds.value.join(',') : ''
  if (form.noticeId) {
    await noticeApi.edit(form)
    ElMessage.success('修改成功')
  } else {
    await noticeApi.add(form)
    ElMessage.success('新增成功')
  }
  editDialogVisible.value = false; reload()
}

onMounted(reload)
</script>
<style lang="scss" scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
.center { display: flex; justify-content: center; }
.sub { color:#9ca3af; font-size:12px; }
.empty { color:#9ca3af; text-align:center; padding:12px; }
.notice-header {
  .notice-title { font-size:18px; font-weight:600; color:#111827; line-height:1.5; display:flex; align-items:center; }
  .notice-meta { margin-top:6px; color:#6b7280; font-size:13px; display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
  .sp { color:#d1d5db; }
}
.notice-summary { background:#fffbeb; padding:10px 12px; border-radius:6px; color:#92400e; font-size:13px; margin-bottom:12px; }
.notice-content { font-size:14px; line-height:1.75; color:#1f2937; }
:deep(.notice-content) p { margin: 0 0 10px; }
:deep(.notice-content) ul, :deep(.notice-content) ol { padding-left: 24px; margin-bottom:10px; }
:deep(.notice-content) b { color:#111827; }
</style>
