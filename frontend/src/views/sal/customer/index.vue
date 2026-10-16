<template>
  <div class="page-sal-customer">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="客户编码"><el-input v-model="query.customerCode" placeholder="客户编码" clearable style="width:180px" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="query.customerName" placeholder="名称" clearable style="width:180px" /></el-form-item>
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
      <el-button type="primary" v-hasPermi="'sal:customer:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增客户</el-button>
      <el-button><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="customerCode" label="客户编码" min-width="120" />
      <el-table-column prop="customerName" label="客户名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="shortName" label="简称" min-width="80" />
      <el-table-column prop="industry" label="行业" min-width="100" />
      <el-table-column prop="level" label="级别" min-width="70" />
      <el-table-column prop="contact" label="联系人" min-width="80" />
      <el-table-column prop="phone" label="电话" min-width="110" />
      <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip />
      <el-table-column prop="creditDays" label="信用期(天)" min-width="90" align="right" />
      <el-table-column prop="creditLimit" label="信用额度" min-width="100" align="right">
        <template #default="{ row }">{{ row.creditLimit == null ? '-' : fmt(row.creditLimit) }}</template>
      </el-table-column>
      <el-table-column prop="creditBalance" label="可用额度" min-width="100" align="right">
        <template #default="{ row }">
          <span v-if="row.creditLimit == null || Number(row.creditLimit) === 0" class="text-gray-400">不控</span>
          <span v-else :class="Number(row.creditBalance) < 0 ? 'text-red-600 font-semibold' : ''">
            {{ fmt(row.creditBalance) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="70">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l || String(row.status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="'sal:customer:list'" size="small" link @click="onView(row)">详情</el-button>
          <el-button v-hasPermi="'sal:customer:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除当前记录？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'sal:customer:remove'" size="small" link type="danger">删除</el-button></template>
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
    <el-dialog v-model="dialog" :title="dialogTitle" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="客户编码" prop="customerCode">
              <el-input v-model="form.customerCode" placeholder="保存时自动生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称" required prop="customerName">
              <el-input v-model="form.customerName" placeholder="请输入客户名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="简称" prop="shortName"><el-input v-model="form.shortName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="行业" prop="industry"><el-input v-model="form.industry" placeholder="如 电子/汽车/通信" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="级别" prop="level">
              <el-select v-model="form.level" placeholder="请选择" style="width:100%">
                <el-option v-for="o in fOpt_level" :key="o.v" :label="o.l" :value="o.v" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="状态(1启用/0停用)" prop="status">
            <el-input-number v-model="form.status" controls-position="right" style="width:100%" />
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="联系人" prop="contact"><el-input v-model="form.contact" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话" prop="phone"><el-input v-model="form.phone" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税号" prop="taxNo"><el-input v-model="form.taxNo" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" type="textarea" :rows="2" />
        </el-form-item>
        <el-divider content-position="left">放账信息</el-divider>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="信用期(天)" prop="creditDays"><el-input-number v-model="form.creditDays" :min="0" :precision="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="信用额度" prop="creditLimit"><el-input-number v-model="form.creditLimit" :min="0" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="付款条件" prop="paymentTerms"><el-input v-model="form.paymentTerms" placeholder="如 30天月结" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">付款方信息</el-divider>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="付款单位" prop="payerName"><el-input v-model="form.payerName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="开户行" prop="payerBank"><el-input v-model="form.payerBank" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="账号" prop="payerAccount"><el-input v-model="form.payerAccount" /></el-form-item></el-col>
        </el-row>
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
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus, Download } from '@element-plus/icons-vue'
import { salCustomerApi, type SalCustomerListQ } from '@/api'
import type { SalCustomer } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'SalCustomerPage' })

const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function hasPermOrThrow(p: string) { if (!hasPermission(p)) throw new Error('无此操作权限: ' + p); return true }

function fmt(v: any) { return v == null || v === '' ? '-' : '¥' + Number(v).toFixed(2) }

const statusOpts: { v: number; l: string; t?: string }[] = [
  { v: 0, l: '停用', t: 'info' },
  { v: 1, l: '启用', t: 'success' }
]
const statusMap: Record<number, { l: string; t?: string }> = {}
statusOpts.forEach(o => statusMap[o.v] = { l: o.l, t: o.t })

const fOpt_level = [
  { v: 'A', l: 'A' },
  { v: 'B', l: 'B' },
  { v: 'C', l: 'C' },
  { v: 'VIP', l: 'VIP' },
  { v: 'NORMAL', l: 'NORMAL' }
]

const query = reactive<SalCustomerListQ>({
  pageNum: 1, pageSize: 20,
  customerCode: '',
  customerName: '',
  status: undefined as number | undefined
})
const list = ref<SalCustomer[]>([])
const total = ref(0)
const loading = ref(false)

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await salCustomerApi.list({
      pageNum: query.pageNum, pageSize: query.pageSize,
      customerCode: query.customerCode,
      customerName: query.customerName,
      status: query.status
    })) as any
    list.value = (resp?.rows) || []
    total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败'); list.value = []; total.value = 0 }
  finally { loading.value = false }
}
function resetQuery() {
  ;(query as any).customerCode = ''
  ;(query as any).customerName = ''
  ;(query as any).status = undefined
  loadList(1)
}

const rowActionList = [
  { label: '启用', key: 'enable', permission: 'sal:customer:edit', onClick: async (row: any) => {
    try {
      await hasPermOrThrow('sal:customer:edit')
      await salCustomerApi.enable(row.customerId) as any
      ElMessage.success('启用成功'); await loadList()
    } catch (e: any) { ElMessage.error(e?.message || '启用失败') }
  } },
  { label: '停用', key: 'disable', permission: 'sal:customer:edit', onClick: async (row: any) => {
    try {
      await hasPermOrThrow('sal:customer:edit')
      await salCustomerApi.disable(row.customerId) as any
      ElMessage.success('停用成功'); await loadList()
    } catch (e: any) { ElMessage.error(e?.message || '停用失败') }
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
const dialogTitle = computed(() => (form.customerId ? (readonly.value ? '客户详情' : '编辑客户') : '新增客户'))

const form = reactive<Partial<SalCustomer>>({
  customerCode: '', customerName: '', shortName: '', industry: '', level: '',
  contact: '', phone: '', email: '', address: '',
  status: 1, remark: '',
  creditDays: 30, creditLimit: 0, paymentTerms: '', taxNo: '',
  payerName: '', payerBank: '', payerAccount: ''
})

function formDefaults() {
  Object.assign(form, {
    customerId: undefined, customerCode: '', customerName: '', shortName: '', industry: '', level: '',
    contact: '', phone: '', email: '', address: '',
    status: 1, remark: '',
    creditDays: 30, creditLimit: 0, paymentTerms: '', taxNo: '',
    payerName: '', payerBank: '', payerAccount: ''
  })
}

async function openDialog(row?: any) {
  formDefaults()
  if (row && row.customerId) {
    Object.assign(form, row)
    readonly.value = false
  } else {
    readonly.value = false
    // 自动生成客户编码
    try {
      const res = (await salCustomerApi.nextCode()) as any
      form.customerCode = res?.data || res || ''
    } catch { /* 后端save时会兜底生成 */ }
  }
  dialog.value = true
}
async function onView(row: any) {
  formDefaults()
  try {
    const detail = (await salCustomerApi.get(row.customerId)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true
    dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.customerId) {
      await salCustomerApi.edit(form as any)
      ElMessage.success('修改成功')
    } else {
      await salCustomerApi.add(form as any)
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
    await hasPermOrThrow('sal:customer:remove')
    await salCustomerApi.remove(row.customerId)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(() => loadList())
</script>

<style scoped>
.page-sal-customer { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
