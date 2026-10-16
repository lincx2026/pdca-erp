<template>
  <div class="user-page">
    <el-row :gutter="16">
      <!-- 左侧部门树 -->
      <el-col :xs="24" :md="6">
        <el-card shadow="never">
          <template #header>
            <div class="flex justify-between">
              <el-input v-model="deptSearch" placeholder="搜索部门" size="small" clearable :prefix-icon="Search" />
            </div>
          </template>
          <el-tree
            ref="deptTreeRef"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            :filter-node-method="filterDept"
            node-key="deptId"
            highlight-current
            @node-click="onDeptClick"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="18">
        <el-card shadow="never" class="mb-16">
          <el-form :inline="true" :model="query" @submit.prevent>
            <el-form-item label="账号">
              <el-input v-model="query.userName" clearable placeholder="账号/昵称模糊" />
            </el-form-item>
            <el-form-item label="手机">
              <el-input v-model="query.phonenumber" clearable placeholder="手机号" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部" style="width:120px">
                <el-option label="正常" :value="0" />
                <el-option label="停用" :value="1" />
              </el-select>
            </el-form-item>
            <el-form-item label="创建时间">
              <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                start-placeholder="开始" end-placeholder="结束" range-separator="~" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="page=1;reload()">搜索</el-button>
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
              <el-button type="success" :icon="Plus" v-hasPermi="'system:user:add'" @click="openForm('add')">新增用户</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <el-table :data="rows" v-loading="loading" border stripe size="small">
            <el-table-column type="selection" width="48" />
            <el-table-column label="ID" prop="userId" width="70" align="center" />
            <el-table-column label="头像" width="60" align="center">
              <template #default="{row}">
                <el-avatar :size="28" :icon="UserFilled" v-if="!row.avatar"/>
                <el-avatar :size="28" :src="row.avatar" v-else />
              </template>
            </el-table-column>
            <el-table-column label="账号" prop="userName" min-width="110" />
            <el-table-column label="昵称" prop="nickName" min-width="110" />
            <el-table-column label="部门" min-width="120">
              <template #default="{row}">{{ row.dept?.deptName || '-' }}</template>
            </el-table-column>
            <el-table-column label="手机" prop="phonenumber" width="130" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}">
                <el-switch
                  v-if="row.userId !== 1"
                  :model-value="row.status===0"
                  inline-prompt
                  active-text="启用" inactive-text="停用"
                  @change="(v)=>changeStatus(row, v?0:1)" />
                <el-tag v-else type="success" size="small">超级管理员</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" prop="createTime" width="170" />
            <el-table-column label="操作" width="260" fixed="right" align="center">
              <template #default="{row}">
                <el-button link type="primary" v-hasPermi="'system:user:edit'" @click="openForm('edit', row)">修改</el-button>
                <el-button link type="primary" v-hasPermi="'system:user:resetPwd'" @click="openResetPwd(row)">重置密码</el-button>
                <el-popconfirm
                  v-if="row.userId !== 1"
                  title="确定删除此用户?"
                  @confirm="removeRow(row)">
                  <template #reference>
                    <el-button link type="danger" v-hasPermi="'system:user:remove'">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="mt-16 center"
            background
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="reload()"
            @current-change="reload()"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑表单 -->
    <el-dialog v-model="dialog.visible" :title="dialog.mode==='add'?'新增用户':'修改用户'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :md="12" v-if="dialog.mode==='add'">
            <el-form-item label="账号" prop="userName"><el-input v-model="form.userName" maxlength="30" /></el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="昵称" prop="nickName"><el-input v-model="form.nickName" maxlength="30" /></el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="部门" prop="deptId">
              <el-tree-select v-model="form.deptId" :data="deptOptions" check-strictly :props="{label:'deptName',value:'deptId',children:'children'}" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="手机号" prop="phonenumber"><el-input v-model="form.phonenumber" maxlength="11" /></el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" type="email" /></el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="性别">
              <el-radio-group v-model="form.sex">
                <el-radio value="0">男</el-radio><el-radio value="1">女</el-radio><el-radio value="2">未知</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status"><el-radio :value="0">正常</el-radio><el-radio :value="1">停用</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :md="24" v-if="dialog.mode==='add'">
            <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" maxlength="20" show-password placeholder="留空默认 123456" /></el-form-item>
          </el-col>
          <el-col :md="24">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple collapse-tags collapse-tags-tooltip placeholder="请选择角色" style="width:100%">
                <el-option v-for="r in roleOptions" :key="r.roleId" :label="r.roleName" :value="r.roleId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmitUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdDialog.visible" title="重置密码" width="420px">
      <el-form ref="pwdFormRef" :model="pwdDialog" :rules="pwdRules" label-width="90px">
        <el-form-item label="用户账号">{{ pwdDialog.userName }}</el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdDialog.password" type="password" show-password maxlength="20" placeholder="5~20 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="onResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus, User as UserFilled } from '@element-plus/icons-vue'
import { deptApi, userApi, roleApi } from '@/api'
import type { SysDept, SysUser, SysRole } from '@/types/models'

const loading = ref(false)
const rows = ref<SysUser[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const dateRange = ref<string[]>([])
const query = reactive<{ deptId?: number; userName?: string; status?: number; phonenumber?: string }>({})
const deptSearch = ref('')
const deptTreeRef = ref()
const deptTree = ref<SysDept[]>([])
const deptOptions = ref<SysDept[]>([])
const roleOptions = ref<SysRole[]>([])

function filterDept(value: string, data: any) {
  if (!value) return true
  return (data.deptName || '').includes(value)
}
watch(deptSearch, (v) => deptTreeRef.value?.filter(v))

function onDeptClick(node: SysDept) { query.deptId = node.deptId; page.value = 1; reload() }

async function reload() {
  loading.value = true
  try {
    const [beginTime, endTime] = (dateRange.value && dateRange.value.length === 2) ? dateRange.value : [undefined as any, undefined as any]
    const res = await userApi.list({
      pageNum: page.value, pageSize: pageSize.value,
      deptId: query.deptId, userName: query.userName,
      status: query.status, phonenumber: query.phonenumber, beginTime, endTime
    }) as any
    rows.value = res.rows || []
    total.value = Number(res.total || 0)
  } finally { loading.value = false }
}

async function resetQuery() {
  query.deptId = undefined; query.userName = ''; query.phonenumber = ''; query.status = undefined
  dateRange.value = []; page.value = 1
  deptTreeRef.value?.setCurrentKey(null)
  reload()
}

function changeStatus(row: SysUser, st: number) {
  userApi.changeStatus({ userId: row.userId!, status: st }).then(() => {
    ElMessage.success('状态已更新'); row.status = st
  })
}

async function openResetPwd(row: SysUser) {
  pwdDialog.visible = true
  pwdDialog.userId = row.userId!
  pwdDialog.userName = row.userName
  pwdDialog.password = ''
}
const pwdDialog = reactive<{ visible: boolean; userId: number; userName: string; password: string }>({ visible: false, userId: 0, userName: '', password: '' })
const pwdLoading = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdRules: FormRules = {
  password: [{ required: true, message: '请输入新密码' }, { min: 5, max: 20, message: '5~20 位' }]
}
async function onResetPwd() {
  const ok = await pwdFormRef.value?.validate().catch(() => false)
  if (!ok) return
  pwdLoading.value = true
  try {
    await userApi.resetPwd({ userId: pwdDialog.userId, password: pwdDialog.password })
    ElMessage.success('密码重置成功')
    pwdDialog.visible = false
  } finally { pwdLoading.value = false }
}

// ============ 新增/编辑 ============
const dialog = reactive<{ visible: boolean; mode: 'add' | 'edit' }>({ visible: false, mode: 'add' })
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({
  userId: undefined, deptId: 100, userName: '', nickName: '', password: '',
  phonenumber: '', email: '', sex: '2', status: 0, roleIds: [], remark: ''
})
const rules: FormRules = {
  userName: [{ required: true, message: '请输入账号', trigger: 'blur' }, { min: 2, max: 30 }],
  nickName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  email: [{ type: 'email', message: '邮箱格式不正确' }]
}
function resetForm() {
  Object.assign(form, { userId: undefined, deptId: 100, userName: '', nickName: '', password: '', phonenumber: '', email: '', sex: '2', status: 0, roleIds: [], remark: '' })
}
async function openForm(mode: 'add' | 'edit', row?: SysUser) {
  resetForm()
  dialog.mode = mode
  deptOptions.value = (await deptApi.excludeTree() as any) || []
  roleOptions.value = (await roleApi.options() as any) || []
  if (mode === 'edit' && row) {
    const res: any = await userApi.get(row.userId!)
    const data: SysUser = res.data || {}
    Object.assign(form, JSON.parse(JSON.stringify(data)))
    form.roleIds = res.roleIds || []
  }
  dialog.visible = true
}
async function onSubmitUser() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  submitLoading.value = true
  try {
    if (dialog.mode === 'add') { await userApi.add(form); ElMessage.success('新增成功') }
    else { await userApi.edit(form); ElMessage.success('修改成功') }
    dialog.visible = false
    reload()
  } finally { submitLoading.value = false }
}
async function removeRow(row: SysUser) {
  await userApi.remove([row.userId!])
  ElMessage.success('删除成功')
  reload()
}
onMounted(async () => {
  deptTree.value = (await deptApi.list({}) as any) || []
  deptOptions.value = deptTree.value
  roleOptions.value = (await roleApi.options() as any) || []
  reload()
})
</script>
<style lang="scss" scoped>
.user-page { min-height: 100%; }
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
.center { display: flex; justify-content: center; }
</style>
