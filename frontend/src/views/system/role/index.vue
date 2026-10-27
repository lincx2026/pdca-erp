<template>
  <div class="page">
    <el-card shadow="never" class="mb-16">
      <el-form :inline="true" :model="query">
        <el-form-item label="角色名"><el-input v-model="query.roleName" clearable placeholder="角色名模糊" /></el-form-item>
        <el-form-item label="标识"><el-input v-model="query.roleKey" clearable placeholder="role key 模糊" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:120px">
            <el-option label="正常" :value="1" /><el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" range-separator="~" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page=1;reload()">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" :icon="Plus" v-hasPermi="'system:role:add'" @click="openDialog('add')">新增角色</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column label="ID" prop="roleId" width="70" align="center" />
        <el-table-column label="角色名" prop="roleName" min-width="130" />
        <el-table-column label="标识" prop="roleKey" min-width="130">
          <template #default="{row}"><el-tag size="small" type="info">{{ row.roleKey }}</el-tag></template>
        </el-table-column>
        <el-table-column label="排序" prop="roleSort" width="80" align="center" />
        <el-table-column label="数据范围" width="140" align="center">
          <template #default="{row}">{{ dsLabel(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{row}">
            <el-tag v-if="row.status===1" type="success" size="small">正常</el-tag>
            <el-tag v-else type="danger" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="320" fixed="right" align="center">
          <template #default="{row}">
            <el-button link type="primary" v-hasPermi="'system:role:edit'" @click="openDialog('edit', row)">修改</el-button>
            <el-button link type="primary" v-hasPermi="'system:role:edit'" @click="openAuth(row)">分配菜单</el-button>
            <el-button link type="primary" v-hasPermi="'system:role:list'" @click="openDialog('edit', row)">数据范围</el-button>
            <el-popconfirm
              v-if="row.roleId !== 1"
              title="确定删除角色?"
              @confirm="removeRow(row)">
              <template #reference>
                <el-button link type="danger" v-hasPermi="'system:role:remove'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt-16 center"
        background v-model:current-page="page" v-model:page-size="pageSize"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper" :total="total"
        @size-change="reload()" @current-change="reload()" />
    </el-card>

    <!-- 新增/编辑 Tab -->
    <el-dialog v-model="dialog.visible" :title="dialog.mode==='add'?'新增角色':'修改角色'" width="780px" destroy-on-close>
      <el-tabs v-model="tabName">
        <el-tab-pane label="基础信息" name="basic">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
            <el-row :gutter="16">
              <el-col :md="12"><el-form-item label="角色名" prop="roleName"><el-input v-model="form.roleName" maxlength="30" style="width:100%" /></el-form-item></el-col>
              <el-col :md="12"><el-form-item label="标识" prop="roleKey"><el-input v-model="form.roleKey" maxlength="100" style="width:100%" /></el-form-item></el-col>
              <el-col :md="12"><el-form-item label="排序" prop="roleSort"><el-input-number v-model="form.roleSort" :min="0" :max="999" controls-position="right" /></el-form-item></el-col>
              <el-col :md="12"><el-form-item label="状态">
                <el-radio-group v-model="form.status"><el-radio :value="1">正常</el-radio><el-radio :value="0" :disabled="form.roleId === 1">停用</el-radio></el-radio-group>
              </el-form-item></el-col>
              <el-col :md="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
            </el-row>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="菜单权限" name="menu">
          <el-alert type="info" :closable="false" show-icon class="mb-8"
            title="勾选后,属于该角色的用户将拥有对应菜单和按钮权限,超级管理员菜单权限全部放行。" />
          <el-tree
            ref="menuTreeRef"
            :data="fullMenuTree"
            :props="{ label:'menuName', children:'children' }"
            show-checkbox node-key="menuId"
            default-expand-all
            :default-checked-keys="form.menuIds || []"
          />
        </el-tab-pane>
        <el-tab-pane label="数据范围" name="ds">
          <el-radio-group v-model="form.dataScope" class="block mb-16">
            <el-radio :value="1">全部数据权限</el-radio>
            <el-radio :value="2">本部门数据权限</el-radio>
            <el-radio :value="3">本部门及以下数据权限</el-radio>
            <el-radio :value="4">仅本人数据权限</el-radio>
            <el-radio :value="5">自定义数据权限(按部门)</el-radio>
          </el-radio-group>
          <div v-if="form.dataScope === 5">
            <el-alert type="warning" :closable="false" show-icon title="请勾选该角色能看到哪些部门下的数据:" class="mb-8" />
            <el-tree
              :data="deptTree"
              :props="{ label:'deptName', children:'children' }"
              show-checkbox node-key="deptId"
              default-expand-all
              v-model:checked-keys="form.deptIds"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 轻量分配菜单弹窗(独立 Tab 用) -->
    <el-dialog v-model="authDialog.visible" :title="'分配菜单 · ' + authDialog.roleName" width="640px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon class="mb-8" title="勾选菜单及按钮权限:" />
      <el-tree
        ref="authMenuTreeRef"
        :data="authDialog.menus"
        :props="{label:'menuName',children:'children'}"
        show-checkbox node-key="menuId"
        default-expand-all
        :default-checked-keys="authDialog.checkedMenuIds"
      />
      <template #footer>
        <el-button @click="authDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onAuthMenuSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { menuApi, deptApi, roleApi } from '@/api'
import type { SysRole, SysMenu, SysDept } from '@/types/models'

const loading = ref(false)
const rows = ref<SysRole[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const dateRange = ref<string[]>([])
const query = reactive<{ roleName?: string; roleKey?: string; status?: number }>({})

const DS_LABEL: Record<number, string> = { 1: '全部', 2: '本部门', 3: '本部门及以下', 4: '本人', 5: '自定义(部门)' }
function dsLabel(s?: number) { return s == null ? '-' : (DS_LABEL[s] || '未知') }

async function reload() {
  loading.value = true
  try {
    const [beginTime, endTime] = dateRange.value.length === 2 ? dateRange.value : [undefined as any, undefined as any]
    const res = await roleApi.list({
      pageNum: page.value, pageSize: pageSize.value,
      roleName: query.roleName, roleKey: query.roleKey, status: query.status, beginTime, endTime
    }) as any
    rows.value = res.rows || []; total.value = Number(res.total || 0)
  } finally { loading.value = false }
}
function resetQuery() { query.roleName = ''; query.roleKey = ''; query.status = undefined; dateRange.value = []; page.value = 1; reload() }

// 数据
const menuTree = ref<SysMenu[]>([])          // 仅 M/C（用于 sidebar 等）
const fullMenuTree = ref<SysMenu[]>([])      // 含 M/C/F（用于角色分配）
const deptTree = ref<SysDept[]>([])

// 主 Dialog
const tabName = ref('basic')
const dialog = reactive<{ visible: boolean; mode: 'add' | 'edit' }>({ visible: false, mode: 'add' })
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const menuTreeRef = ref<any>()
const form = reactive<any>({
  roleId: undefined, roleName: '', roleKey: '', roleSort: 0, status: 1, dataScope: 3,
  remark: '', menuIds: [], deptIds: [], menuCheckStrictly: 0, deptCheckStrictly: 0
})
const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名', trigger: 'blur' }, { min: 2, max: 30 }],
  roleKey: [{ required: true, message: '请输入角色权限标识', trigger: 'blur' }, { min: 2, max: 100 }],
  roleSort: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}
function resetForm() { Object.assign(form, { roleId: undefined, roleName: '', roleKey: '', roleSort: 0, status: 1, dataScope: 3, remark: '', menuIds: [], deptIds: [], menuCheckStrictly: 0, deptCheckStrictly: 0 }) }
async function openDialog(mode: 'add' | 'edit', row?: SysRole) {
  resetForm()
  dialog.mode = mode
  tabName.value = 'basic'
  if (mode === 'edit' && row) {
    // roleApi.get 返回 R<T> 已解包 → { data: SysRole, menus, depts, menuIds, deptIds }
    const r: any = await roleApi.get(row.roleId!)
    Object.assign(form, r?.data || r || {}, {
      menuIds: (r?.menuIds ?? r?.data?.menuIds ?? []) as number[],
      deptIds: (r?.deptIds ?? r?.data?.deptIds ?? []) as number[]
    })
  }
  dialog.visible = true
}
async function onSubmit() {
  tabName.value = 'basic'
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  submitLoading.value = true
  try {
    // 收集菜单勾选
    form.menuIds = menuTreeRef.value?.getCheckedKeys(true)
      .concat(menuTreeRef.value?.getHalfCheckedKeys ? menuTreeRef.value.getHalfCheckedKeys() : [])
    if (dialog.mode === 'add') { await roleApi.add(form); ElMessage.success('新增成功') }
    else { await roleApi.edit(form); ElMessage.success('修改成功') }
    dialog.visible = false; reload()
  } finally { submitLoading.value = false }
}
async function removeRow(row: SysRole) {
  await roleApi.remove([row.roleId!])
  ElMessage.success('删除成功')
  reload()
}

// 分配菜单 Dialog
const authMenuTreeRef = ref<any>()
const authDialog = reactive<{
  visible: boolean; roleId?: number; roleName?: string;
  checkedMenuIds: number[]; menus: SysMenu[];
}>({ visible: false, checkedMenuIds: [], menus: [] })
async function openAuth(row: SysRole) {
  const r: any = await menuApi.roleMenuTree(row.roleId!)
  authDialog.roleId = row.roleId
  authDialog.roleName = row.roleName
  authDialog.menus = (r.menus || []) as SysMenu[]
  authDialog.checkedMenuIds = (r.checkedKeys || []) as number[]
  authDialog.visible = true
}
async function onAuthMenuSubmit() {
  if (!authDialog.roleId) return
  const keys = authMenuTreeRef.value.getCheckedKeys(true).concat(authMenuTreeRef.value.getHalfCheckedKeys ? authMenuTreeRef.value.getHalfCheckedKeys() : [])
  submitLoading.value = true
  try {
    await roleApi.authMenu({ roleId: authDialog.roleId, menuIds: keys })
    ElMessage.success('菜单权限保存成功')
    authDialog.visible = false
  } finally { submitLoading.value = false }
}

onMounted(async () => {
  menuTree.value = (await menuApi.treeSelect() as any) || []
  // fullMenuTree: 含 M/C/F 的完整菜单树，用于角色分配
  const allMenusResp: any = await menuApi.roleMenuTree(1)
  fullMenuTree.value = (allMenusResp?.menus || []) as SysMenu[]
  deptTree.value = (await deptApi.list({}) as any) || []
  reload()
})
</script>
<style lang="scss" scoped>
.mb-8{margin-bottom:8px;}.mb-16{margin-bottom:16px;}.mt-16{margin-top:16px;}.center{display:flex;justify-content:center;}.block{display:block;margin-bottom:12px;}
</style>
