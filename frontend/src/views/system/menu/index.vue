<template>
  <div class="page">
    <el-card shadow="never" class="mb-16">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="菜单名">
          <el-input v-model="query.menuName" clearable placeholder="菜单名模糊" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.menuType" clearable placeholder="全部" style="width:120px">
            <el-option label="目录" value="M" />
            <el-option label="菜单" value="C" />
            <el-option label="按钮" value="F" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="reload">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" :icon="Plus" v-hasPermi="'system:menu:add'" @click="openDialog('add')">新增菜单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="treeData" v-loading="loading" border stripe row-key="menuId" size="small"
                :tree-props="{children:'children'}" default-expand-all>
        <el-table-column label="名称" prop="menuName" min-width="220">
          <template #default="{row}">
            <el-icon v-if="row.icon && row.menuType !== 'F'" class="mr-4" style="vertical-align: -2px;">
              <component :is="iconFor(row.icon)" />
            </el-icon>
            <span>{{ row.menuName }}</span>
            <el-tag v-if="row.menuType==='M'" type="success" size="small" effect="plain" style="margin-left:6px">目录</el-tag>
            <el-tag v-else-if="row.menuType==='C'" type="primary" size="small" effect="plain" style="margin-left:6px">菜单</el-tag>
            <el-tag v-else type="info" size="small" effect="plain" style="margin-left:6px">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图标" width="90" align="center">
          <template #default="{row}">
            <el-icon v-if="row.icon && row.menuType !== 'F'"><component :is="iconFor(row.icon)" /></el-icon>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="路由" prop="path" min-width="180" />
        <el-table-column label="组件" prop="component" min-width="200" show-overflow-tooltip>
          <template #default="{row}"><code v-if="row.component" class="cmp">{{ row.component }}</code><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="权限标识" min-width="200" show-overflow-tooltip>
          <template #default="{row}"><code v-if="row.perms" class="perms">{{ row.perms }}</code><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="排序" prop="orderNum" width="70" align="center" />
        <el-table-column label="外链/缓存" width="130" align="center">
          <template #default="{row}">
            <el-tag size="small" type="warning" v-if="row.isFrame===1" class="mr-4">外链</el-tag>
            <el-tag size="small" type="success" v-if="row.isCache===1">缓存</el-tag>
            <span v-if="row.isFrame!==1 && row.isCache!==1">-</span>
          </template>
        </el-table-column>
        <el-table-column label="可见" width="70" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="row.visible===0?'success':'info'">{{ row.visible===0?'显示':'隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="row.status===0?'success':'danger'">{{ row.status===0?'正常':'停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right" align="center">
          <template #default="{row}">
            <el-button link type="primary" v-if="row.menuType !== 'F'" v-hasPermi="'system:menu:add'" @click="openDialog('addSub', row)">新增子</el-button>
            <el-button link type="primary" v-hasPermi="'system:menu:edit'" @click="openDialog('edit', row)">修改</el-button>
            <el-popconfirm title="确定删除该菜单？" @confirm="removeRow(row)">
              <template #reference>
                <el-button link type="danger" v-hasPermi="'system:menu:remove'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :md="12">
            <el-form-item label="上级菜单">
              <el-tree-select v-model="form.parentId" :data="parentMenuOptions" check-strictly
                             :props="{ label:'menuName', value:'menuId', children:'children' }"
                             style="width:100%" placeholder="顶级（无父菜单）" clearable />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="菜单类型">
              <el-radio-group v-model="form.menuType">
                <el-radio value="M">目录</el-radio>
                <el-radio value="C">菜单</el-radio>
                <el-radio value="F">按钮</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="菜单名" prop="menuName">
              <el-input v-model="form.menuName" maxlength="50" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :md="6" v-show="form.menuType !== 'F'">
            <el-form-item label="排序">
              <el-input-number v-model="form.orderNum" :min="0" :max="999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :md="6" v-show="form.menuType !== 'F'">
            <el-form-item label="图标">
              <el-input v-model="form.icon" placeholder="例: Menu / Goods" />
            </el-form-item>
          </el-col>
          <el-col :md="12" v-show="form.menuType !== 'M'">
            <el-form-item label="权限标识">
              <el-input v-model="form.perms" placeholder="例: system:user:add" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :md="12" v-show="form.menuType !== 'F'">
            <el-form-item label="路由地址" prop="path">
              <el-input v-model="form.path" placeholder="例: system/user" />
            </el-form-item>
          </el-col>
          <el-col :md="12" v-show="form.menuType === 'C'">
            <el-form-item label="组件路径" prop="component">
              <el-input v-model="form.component" placeholder="system/user/index" />
            </el-form-item>
          </el-col>
          <el-col :md="6" v-show="form.menuType !== 'F'">
            <el-form-item label="是否外链">
              <el-radio-group v-model="form.isFrame">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :md="6" v-show="form.menuType !== 'F'">
            <el-form-item label="是否缓存">
              <el-radio-group v-model="form.isCache">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :md="6" v-show="form.menuType !== 'F'">
            <el-form-item label="显示">
              <el-radio-group v-model="form.visible">
                <el-radio :value="0">显示</el-radio>
                <el-radio :value="1">隐藏</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :md="6">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="0">正常</el-radio>
                <el-radio :value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :md="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus, Menu } from '@element-plus/icons-vue'
import * as Icons from '@element-plus/icons-vue'
import { menuApi } from '@/api'
import type { SysMenu } from '@/types/models'
import type { Component } from 'vue'

const IconMap = Icons as Record<string, Component>
function iconFor(name: string | undefined | null): Component {
  if (!name) return Menu
  return IconMap[name] || Menu
}

const loading = ref(false)
const flatData = ref<SysMenu[]>([])
const treeData = ref<SysMenu[]>([])
const parentMenuOptions = ref<SysMenu[]>([])
const query = reactive<{ menuName?: string; status?: number | undefined; menuType?: 'M' | 'C' | 'F' | undefined }>({ menuName: '', status: undefined, menuType: undefined })

async function reload() {
  loading.value = true
  try {
    const flat: any[] = await menuApi.list(query) as any
    // 类型过滤
    const filtered = query.menuType ? flat.filter(x => x.menuType === query.menuType) : flat
    flatData.value = filtered
    treeData.value = buildTree(filtered)
  } finally { loading.value = false }
}
function resetQuery() { query.menuName = ''; query.status = undefined; query.menuType = undefined; reload() }

function buildTree(list: SysMenu[]): SysMenu[] {
  const m = new Map<number, any>()
  list.forEach(x => m.set(x.menuId!, { ...x, children: undefined }))
  const roots: SysMenu[] = []
  for (const x of list) {
    const node: any = m.get(x.menuId!)!
    const pid = x.parentId || 0
    if (pid === 0) roots.push(node)
    else {
      const parent: any = m.get(pid)
      if (parent) { parent.children ||= []; parent.children.push(node) }
      else roots.push(node)
    }
  }
  function sortRec(arr?: SysMenu[]) {
    if (!arr) return
    arr.sort((a, b) => (a.orderNum || 0) - (b.orderNum || 0))
    ;(arr as any[]).forEach(n => sortRec(n.children))
  }
  sortRec(roots)
  return roots
}

const dialog = reactive<{ visible: boolean; mode: 'add' | 'edit' | 'addSub' }>({ visible: false, mode: 'add' })
const dialogTitle = computed(() => {
  if (dialog.mode === 'edit') return '修改菜单'
  if (dialog.mode === 'addSub') return '新增子菜单'
  return '新增菜单'
})
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const defaultForm = () => ({
  menuId: undefined as number | undefined,
  parentId: 0 as number,
  menuName: '',
  orderNum: 0,
  menuType: 'M' as 'M' | 'C' | 'F',
  visible: 0 as number,
  status: 0 as number,
  isFrame: 0 as number,
  isCache: 0 as number,
  path: '',
  component: '',
  perms: '',
  icon: '',
  remark: ''
})
const form = reactive<any>(defaultForm())
const rules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名', trigger: 'blur' }, { max: 50, message: '最长50字' }],
  path: [
    { required: true, message: '请输入路由地址', trigger: 'blur' },
    { validator: (_r, val, cb) => {
      if (form.menuType === 'F') cb()
      else if (!val) cb(new Error('请输入路由地址'))
      else cb()
    }, trigger: 'blur' }
  ],
  component: [
    { validator: (_r, val, cb) => {
      if (form.menuType === 'C' && !val) cb(new Error('菜单类型请填写组件路径'))
      else cb()
    }, trigger: 'blur' }
  ]
}

async function openDialog(mode: 'add' | 'edit' | 'addSub', row?: SysMenu) {
  Object.assign(form, defaultForm())
  dialog.mode = mode
  const excludeId = mode === 'edit' ? row?.menuId : undefined
  parentMenuOptions.value = (await menuApi.excludeTree(excludeId as any)) as any
  if (mode === 'edit' && row) {
    Object.assign(form, JSON.parse(JSON.stringify(row)))
    // 空值规范化
    form.parentId ||= 0
    form.orderNum ||= 0
    form.visible ??= 0
    form.status ??= 0
    form.isFrame ??= 0
    form.isCache ??= 0
  }
  if (mode === 'addSub' && row) form.parentId = row.menuId
  if (mode === 'add') form.parentId = 0
  dialog.visible = true
}

function buildPayload() {
  const payload: any = { ...form }
  // 按钮无路由/组件：清空避免误导
  if (form.menuType === 'F') {
    payload.path = null
    payload.component = null
    payload.icon = null
    payload.isFrame = 0
    payload.isCache = 0
  } else {
    if (!payload.component) payload.component = null
    if (!payload.icon) payload.icon = null
  }
  if (!payload.perms) payload.perms = null
  if (form.parentId === undefined || form.parentId === '' || form.parentId === null) payload.parentId = 0
  return payload
}

async function onSubmit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  submitLoading.value = true
  try {
    const payload = buildPayload()
    if (dialog.mode === 'edit') {
      await menuApi.edit(payload); ElMessage.success('修改成功')
    } else {
      await menuApi.add(payload); ElMessage.success('新增成功')
    }
    dialog.visible = false; reload()
  } finally { submitLoading.value = false }
}
async function removeRow(row: SysMenu) {
  try {
    await menuApi.remove(row.menuId!)
    ElMessage.success('删除成功')
    reload()
  } catch {}
}
onMounted(reload)
</script>

<style scoped>
.page { padding: 16px; }
.mb-16 { margin-bottom: 16px; }
.mr-4 { margin-right: 4px; }
code.cmp, code.perms {
  padding: 1px 6px; background: #f4f4f5; border-radius: 4px;
  font-family: Consolas, Menlo, monospace;
  font-size: 12px; color: #606266;
}
code.perms { color: #b35900; background: #fdf6ec; }
</style>
