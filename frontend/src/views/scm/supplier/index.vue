<template>
  <div class="page-scm-supplier">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="供应商编码"><el-input v-model="query.supplierCode" placeholder="供应商编码" clearable style="width:180px" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="query.supplierName" placeholder="名称" clearable style="width:180px" /></el-form-item>
      <el-form-item label="联系人"><el-input v-model="query.contact" placeholder="联系人" clearable style="width:180px" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:160px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'scm:supplier:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增供应商管理</el-button>
      <el-button><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border>
    <el-table-column prop="supplierCode" label="编码" width="140">
    </el-table-column>
    <el-table-column prop="supplierName" label="供应商名称" width="200" show-overflow-tooltip>
    </el-table-column>
    <el-table-column prop="shortName" label="简称" width="100">
    </el-table-column>
    <el-table-column prop="level" label="级别" width="90">
    </el-table-column>
    <el-table-column prop="contact" label="联系人" width="100">
    </el-table-column>
    <el-table-column prop="phone" label="电话" width="130">
    </el-table-column>
    <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip>
    </el-table-column>
    <el-table-column prop="address" label="地址" width="240" show-overflow-tooltip>
    </el-table-column>
    <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap_supplier[row.status]?.t || ''">
            {{ statusMap_supplier[row.status]?.l || String(row.status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
    </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
      <el-button v-hasPermi="'scm:supplier:list'" size="small" link @click="onView(row)">详情</el-button>
          <el-button v-hasPermi="'scm:supplier:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除当前记录？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'scm:supplier:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <template v-for="a in rowActionList" :key="a.key">
            <el-button v-if="!a.permission || hasPermission(a.permission)"
              v-hasPermi="a.permission ? [a.permission] : undefined"
              size="small" link :type="(a as any).type || 'default'"
              @click="onActionClick(a, row)">
              {{ a.label }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper"
                     v-model:current-page="query.pageNum"
                     v-model:page-size="query.pageSize"
                     :total="total"
                     :page-sizes="[20, 50, 100]"
                     @size-change="loadList(1)"
                     @current-change="loadList()" />
    </div>

    <!-- 新增/编辑/详情 弹窗 -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
      <el-form-item label="编码" prop="supplierCode">
        <el-input v-model="form.supplierCode" placeholder="保存时自动生成" disabled />
      </el-form-item>
      <el-form-item label="名称" required prop="supplierName">
        <el-input v-model="form.supplierName" placeholder="请输入名称" />
      </el-form-item>
      <el-form-item label="简称" prop="shortName">
        <el-input v-model="form.shortName" placeholder="请输入简称" />
      </el-form-item>
      <el-form-item label="级别" prop="level">
        <el-select v-model="form.level" placeholder="请选择" style="width:100%">
          <el-option v-for="o in fOpt_level" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="联系人" prop="contact">
        <el-input v-model="form.contact" placeholder="请输入联系人" />
      </el-form-item>
      <el-form-item label="电话" prop="phone">
        <el-input v-model="form.phone" placeholder="请输入电话" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="地址" prop="address">
        <el-input v-model="form.address" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="状态(1启用/0停用)" prop="status">
        <el-input-number v-model="form.status" controls-position="right" style="width:100%" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshLeft, Plus, Download } from '@element-plus/icons-vue'
import {
  scmSupplierApi, type ScmSupplierListQ
} from '@/api'
import type { ScmSupplier } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'ScmSupplierPage' })

const userStore = useUserStore()
function hasPermission(p: string) {
  return hasPerm(p, userStore.userInfo?.permissions)
}
function hasPermOrThrow(p: string) {
  if (!hasPermission(p)) throw new Error('无此操作权限: ' + p)
  return true
}
function canShowPerm(p: string) { return hasPermission(p) }

const statusOpts: { v: number; l: string; t?: string }[] = [{"v":0,"l":"停用","t":"info"},{"v":1,"l":"启用","t":"success"}]
const statusMap_supplier: Record<number, {l:string;t?:string}> = {}
statusOpts.forEach(o => statusMap_supplier[o.v] = { l: o.l, t: o.t })

// 枚举选项: 搜索条 select & 表单 select

const fOpt_level = [{"v":"A","l":"A"},{"v":"B","l":"B"},{"v":"C","l":"C"},{"v":"NORMAL","l":"NORMAL"}]

const query = reactive<ScmSupplierListQ>({
  pageNum: 1, pageSize: 20,
  supplierCode: '',
  supplierName: '',
  contact: '',
  status: undefined as number | undefined
})
const list = ref<ScmSupplier[]>([])
const total = ref(0)
const loading = ref(false)

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await scmSupplierApi.list({
      pageNum: query.pageNum, pageSize: query.pageSize,
      supplierCode: query.supplierCode,
      supplierName: query.supplierName,
      contact: query.contact,
      status: query.status
    })) as any
    list.value = (resp && resp.rows) || []
    total.value = (resp && Number(resp.total)) || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    list.value = []
    total.value = 0
  } finally { loading.value = false }
}
function resetQuery() {
  (query as any).supplierCode = ''
  (query as any).supplierName = ''
  (query as any).contact = ''
  (query as any).status = undefined
  loadList(1)
}

const rowActionList = [
          { label: '启用',  key: 'enable',  permission: 'scm:supplier:edit', onClick: async (row: any) => {
            try {
              await hasPermOrThrow('scm:supplier:edit')
              const resp = await scmSupplierApi.enable(row.supplierId) as any
              ElMessage.success('启用成功')
              await loadList()
            } catch (e: any) { ElMessage.error(e?.message || '启用失败') }
          } },
          { label: '停用',  key: 'disable',  permission: 'scm:supplier:edit', onClick: async (row: any) => {
            try {
              await hasPermOrThrow('scm:supplier:edit')
              const resp = await scmSupplierApi.disable(row.supplierId) as any
              ElMessage.success('停用成功')
              await loadList()
            } catch (e: any) { ElMessage.error(e?.message || '停用失败') }
          } },
          { label: '评级', type: 'primary', key: 'rate',  permission: 'scm:supplier:rate', onClick: async (row: any) => {
            try {
              await hasPermOrThrow('scm:supplier:rate')
              const resp = await scmSupplierApi.rate(row.supplierId) as any
              ElMessage.success('评级成功')
              await loadList()
            } catch (e: any) { ElMessage.error(e?.message || '评级失败') }
          } }
] as any[]

function onActionClick(a: any, row: any) {
  if (a.confirm) {
    ElMessageBox.confirm(a.confirm, '操作确认', { type: 'warning' })
      .then(() => a.onClick && a.onClick(row))
      .catch(() => {})
  } else {
    a.onClick && a.onClick(row)
  }
}

// 新增/编辑/详情
const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => (form.supplierId ? (readonly.value ? '供应商管理详情' : '编辑供应商管理') : '新增供应商管理'))

const form = reactive<Partial<ScmSupplier>>({
  supplierCode: '',
  supplierName: '',
  shortName: '',
  level: '',
  contact: '',
  phone: '',
  email: '',
  address: '',
  status: undefined as number | undefined,
  remark: ''
})

function formDefaults() {
  form.supplierCode = ''
  form.supplierName = ''
  form.shortName = ''
  form.level = ''
  form.contact = ''
  form.phone = ''
  form.email = ''
  form.address = ''
  form.status = undefined
  form.remark = ''
}

async function openDialog(row?: any) {
  formDefaults()
  if (row && row.supplierId) {
    Object.assign(form, row)
    readonly.value = false
  } else {
    readonly.value = false
    // 自动生成供应商编码
    try {
      const res = (await scmSupplierApi.nextCode()) as any
      form.supplierCode = res?.data || res || ''
    } catch { /* 后端save时会兜底生成 */ }
  }
  dialog.value = true
}
async function onView(row: any) {
  formDefaults()
  try {
    const detail = (await scmSupplierApi.get(row.supplierId)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true
    dialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '获取详情失败')
  }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.supplierId) {
      await scmSupplierApi.edit(form as any)
      ElMessage.success('修改成功')
    } else {
      await scmSupplierApi.add(form as any)
      ElMessage.success('新增成功')
    }
    dialog.value = false
    await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: any) {
  try {
    await hasPermOrThrow('scm:supplier:remove')
    await scmSupplierApi.remove(row.supplierId)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(() => loadList())
</script>

<style scoped>
.page-scm-supplier { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
