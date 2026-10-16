<template>
  <div class="page-scm-price">
    <!-- 搜索: 以物料为主视角 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="物料编码"><el-input v-model="query.materialCode" placeholder="物料编码" clearable style="width:180px" @keyup.enter="loadList(1)" /></el-form-item>
      <el-form-item label="物料名称"><el-input v-model="query.materialName" placeholder="物料名称" clearable style="width:200px" @keyup.enter="loadList(1)" /></el-form-item>
      <el-form-item label="供应商名称"><el-input v-model="query.supplierName" placeholder="供应商名称" clearable style="width:200px" @keyup.enter="loadList(1)" /></el-form-item>
      <el-form-item label="币种"><el-input v-model="query.currency" placeholder="币种" clearable style="width:120px" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
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
      <el-button type="primary" v-hasPermi="'scm:price:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增供应商价格</el-button>
      <el-button v-hasPermi="'scm:price:convert'" type="success" :disabled="selection.length === 0" @click="openConvertDialog">
        <el-icon><Promotion /></el-icon> 批量转采购订单
        <span v-if="selection.length" class="badge">{{ selection.length }}</span>
      </el-button>
      <el-button><el-icon><Download /></el-icon> 导出</el-button>
      <span class="group-tip">
        同物料多供应商报价自动合并 ·
        <span class="legend-min">最低价</span>
        <el-tag size="small" type="warning" effect="dark">默认</el-tag>该物料默认供应商
        <el-tag size="small" v-if="selection.length" type="danger" class="ml-8">需勾选"已审"供应商报价才能转PO</el-tag>
      </span>
    </div>

    <!-- 表格: 按物料分组, 物料列前置 -->
    <el-table v-loading="loading" :data="list" stripe border
              @selection-change="onSelectionChange"
              :span-method="objectSpanMethod"
              :cell-class-name="cellClassName"
              :row-class-name="rowClassName">
      <el-table-column type="selection" width="44" :selectable="(row) => canSelect(row)" />
      <el-table-column label="物料信息" width="260">
        <template #default="{ row }">
          <div class="mat-cell">
            <div class="mat-code">{{ row.materialCode || ('#' + row.materialId) }}</div>
            <div class="mat-name">{{ row.materialName || '-' }}</div>
            <div class="mat-spec" v-if="row.specification">{{ row.specification }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="供应商" width="180">
        <template #default="{ row }">
          <div>{{ row.supplierName || ('#' + row.supplierId) }}</div>
          <div class="sub-code" v-if="row.supplierCode">{{ row.supplierCode }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="currency" label="币种" width="70" />
      <el-table-column prop="unitPrice" label="单价" width="130">
        <template #default="{ row }">
          <span class="price-val">{{ row.unitPrice == null ? '-' : '¥' + Number(row.unitPrice).toFixed(4) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="taxRate" label="税率(%)" width="90"><template #default="{ row }">{{ row.taxRate == null ? '-' : Number(row.taxRate).toLocaleString() }}</template></el-table-column>
      <el-table-column prop="minQty" label="最小订购量" width="110"><template #default="{ row }">{{ row.minQty == null ? '-' : Number(row.minQty).toLocaleString() }}</template></el-table-column>
      <el-table-column prop="validFrom" label="生效日期" width="110" />
      <el-table-column prop="validTo" label="失效日期" width="110" />
      <el-table-column prop="quoteSource" label="报价来源" width="100" />
      <el-table-column prop="isDefault" label="默认" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault === 1" type="warning" effect="dark">默认</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap_price[row.status]?.t || ''">
            {{ statusMap_price[row.status]?.l || String(row.status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="'scm:price:list'" size="small" link @click="onView(row)">详情</el-button>
          <el-button v-hasPermi="'scm:price:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除当前记录？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'scm:price:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <template v-for="a in buildRowActionList(row)" :key="a.key">
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
      <el-form-item label="物料" required prop="materialId">
        <el-select v-model="form.materialId" filterable remote reserve-keyword
                   :remote-method="searchMaterial" :loading="materialLoading"
                   placeholder="输入物料编码/名称搜索" style="width:100%" @change="onMaterialChange">
          <el-option v-for="m in materialOptions" :key="m.materialId"
                     :label="`${m.materialCode} · ${m.materialName}`" :value="m.materialId" />
        </el-select>
      </el-form-item>
      <el-form-item label="供应商" required prop="supplierId">
        <el-select v-model="form.supplierId" filterable remote reserve-keyword
                   :remote-method="searchSupplier" :loading="supplierLoading"
                   placeholder="输入供应商名称搜索" style="width:100%">
          <el-option v-for="s in supplierOptions" :key="s.supplierId"
                     :label="s.supplierName" :value="s.supplierId" />
        </el-select>
      </el-form-item>
      <el-form-item label="币种" prop="currency">
        <el-input v-model="form.currency" placeholder="请输入币种" />
      </el-form-item>
      <el-form-item label="单价" required prop="unitPrice">
        <el-input-number v-model="form.unitPrice" controls-position="right" style="width:100%" />
      </el-form-item>
      <el-form-item label="税率" prop="taxRate">
        <el-input-number v-model="form.taxRate" controls-position="right" style="width:100%" />
      </el-form-item>
      <el-form-item label="最小订购量" prop="minQty">
        <el-input-number v-model="form.minQty" controls-position="right" style="width:100%" />
      </el-form-item>
      <el-form-item label="生效日期" required prop="validFrom">
        <el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" placeholder="请选择生效日期" style="width:100%" />
      </el-form-item>
      <el-form-item label="失效日期" required prop="validTo">
        <el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" placeholder="请选择失效日期" style="width:100%" />
      </el-form-item>
      <el-form-item label="报价来源" prop="quoteSource">
        <el-input v-model="form.quoteSource" placeholder="请输入报价来源" />
      </el-form-item>
      <el-form-item label="默认供应商" prop="isDefault">
        <el-radio-group v-model="form.isDefault">
          <el-radio :value="1">是</el-radio>
          <el-radio :value="0">否</el-radio>
        </el-radio-group>
        <span class="default-tip">设为"是"时, 同物料其他默认将自动取消</span>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="form.status" placeholder="请选择状态" style="width:100%">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
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

    <!-- ================= P0-C: 批量转采购订单 弹窗 ================= -->
    <el-dialog v-model="convertDlg" title="批量转采购订单（P0 供应商报价→采购订单）" width="1180px" top="4vh" destroy-on-close>
      <div v-if="convertDlg" class="convert-wrap">
        <!-- 订单头部信息 -->
        <el-form :model="hd" :inline="true" label-width="90px" class="header-form">
          <el-form-item label="供应商"><el-tag v-if="hd.supplierId" type="primary" effect="dark">{{ hd.supplierName }} (#{{ hd.supplierId }})</el-tag><span v-else style="color:#F56C6C">— 请统一勾选同一供应商 —</span></el-form-item>
          <el-form-item label="订单日期" required>
            <el-date-picker v-model="hd.orderDate" type="date" value-format="YYYY-MM-DD" placeholder="下单日期" style="width:160px" />
          </el-form-item>
          <el-form-item label="交付日期" required>
            <el-date-picker v-model="hd.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="供应商交付日期" style="width:160px" />
          </el-form-item>
          <el-form-item label="付款条件">
            <el-select v-model="hd.paymentTerm" clearable placeholder="请选择" style="width:160px">
              <el-option label="款到发货" value="款到发货" /><el-option label="货到付款" value="货到付款" />
              <el-option label="月结30天" value="月结30天" /><el-option label="月结60天" value="月结60天" />
              <el-option label="月结90天" value="月结90天" />
            </el-select>
          </el-form-item>
          <el-form-item label="币种">
            <el-input v-model="hd.currency" maxlength="6" style="width:100px" />
          </el-form-item>
          <el-form-item label="税率(%)">
            <el-input-number v-model="hd.taxRate" :min="0" :max="100" :precision="2" style="width:130px" />
          </el-form-item>
          <el-form-item label="备注" style="flex:1;min-width:320px">
            <el-input v-model="hd.remark" placeholder="订单备注 (同步写入采购订单remark)" maxlength="200" />
          </el-form-item>
        </el-form>

        <div class="hint-row">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              共选中 <b class="c-blue">{{ convertRows.length }}</b> 条 <el-tag size="small" type="success" effect="dark">已审</el-tag> 供应商报价。
              请填写每行实际采购数量 (<b>必须满足 minQty 最小订购量</b>)、交付日期、税率、行备注；系统后端会严格校验 minQty + 物料未被其他 PO 占用 + 效期等。
              汇总金额：<b class="c-orange">¥ {{ totalLineAmount.toFixed(2) }}</b>（未税）  税额：<b class="c-orange">¥ {{ totalTaxAmount.toFixed(2) }}</b>  合计：<b class="c-red">¥ {{ (totalLineAmount + totalTaxAmount).toFixed(2) }}</b>
            </template>
          </el-alert>
        </div>

        <div class="line-table">
          <el-table :data="convertRows" stripe border size="default" :row-class-name="({row}:any)=> row.invalid ? 'row-invalid':''">
            <el-table-column label="#" width="50" align="center" type="index" />
            <el-table-column label="物料编码" width="150" prop="materialCode" />
            <el-table-column label="物料名称" width="180" prop="materialName" show-overflow-tooltip />
            <el-table-column label="规格" width="140" prop="specification" show-overflow-tooltip />
            <el-table-column label="单位" width="70" prop="unit" />
            <el-table-column label="原单价" width="110">
              <template #default="{row}:any">¥{{ Number(row.unitPrice||0).toFixed(4) }}</template>
            </el-table-column>
            <el-table-column label="最小订购" width="100" align="right">
              <template #default="{row}:any"><span :class="row.invalid ? 'c-red':''">{{ Number(row.minQty||0).toLocaleString() }}</span></template>
            </el-table-column>
            <el-table-column label="效期" width="200">
              <template #default="{row}:any">{{ row.validFrom || '-' }} ~ {{ row.validTo || '-' }}</template>
            </el-table-column>
            <el-table-column label="采购数量 *" width="150">
              <template #default="{row}:any">
                <el-input-number v-model="row.qty" :min="Number(row.minQty||1)" :precision="row.unit==='PCS'||!row.unit?0:4"
                  :step="Math.max(1,Number(row.minQty||1))" style="width:100%" size="default" @change="refreshTotal()" />
              </template>
            </el-table-column>
            <el-table-column label="成交单价" width="140">
              <template #default="{row}:any">
                <el-input-number v-model="row.unitPrice" :min="0" :precision="6" :step="0.01" style="width:100%" size="default" @change="refreshTotal()" />
              </template>
            </el-table-column>
            <el-table-column label="税率(%)" width="110">
              <template #default="{row}:any">
                <el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" style="width:100%" size="default" @change="refreshTotal()" />
              </template>
            </el-table-column>
            <el-table-column label="行交付日期" width="160">
              <template #default="{row}:any">
                <el-date-picker v-model="row.deliveryDate" type="date" value-format="YYYY-MM-DD" :placeholder="hd.deliveryDate||'同订单'" style="width:100%" size="default" />
              </template>
            </el-table-column>
            <el-table-column label="行金额(未税)" width="130" align="right">
              <template #default="{row}:any"><b class="c-blue">¥{{ lineAmount(row).toFixed(2) }}</b></template>
            </el-table-column>
            <el-table-column label="行备注" min-width="160">
              <template #default="{row}:any">
                <el-input v-model="row.remark" size="default" maxlength="100" placeholder="行备注(可选)" />
              </template>
            </el-table-column>
            <el-table-column label="移除" width="70" align="center" fixed="right">
              <template #default="{$index}">
                <el-button link type="danger" size="small" @click="removeConvertRow($index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="convertDlg=false">取消</el-button>
        <el-button type="primary" :disabled="!canSubmitConvert" :loading="submitConverting" @click="submitConvert">
          <el-icon><Check /></el-icon> 生成采购订单 ({{ convertRows.length }}项)
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshLeft, Plus, Download, Promotion, Check } from '@element-plus/icons-vue'
import {
  scmPriceApi, scmSupplierApi, materialApi, type ScmPriceListQ, scmPoApi
} from '@/api'
import type { ScmSupplierPrice, PdmMaterial, ScmSupplier } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'ScmPricePage' })
const router = useRouter()

const userStore = useUserStore()
function hasPermission(p: string) {
  return hasPerm(p, userStore.userInfo?.permissions)
}
function hasPermOrThrow(p: string) {
  if (!hasPermission(p)) throw new Error('无此操作权限: ' + p)
  return true
}
function canShowPerm(p: string) { return hasPermission(p) }

// 供应商报价状态机：0草稿→1待审→2已审→5已转单  或  4审核拒绝 / 3停用
const statusOpts: { v: number; l: string; t?: string }[] = [
  { v: 0, l: '草稿', t: 'info' },
  { v: 1, l: '待审核', t: 'warning' },
  { v: 2, l: '已审核', t: 'success' },
  { v: 3, l: '停用', t: 'info' },
  { v: 4, l: '审核拒绝', t: 'danger' },
  { v: 5, l: '已转PO', t: 'primary' },
]
const statusMap_price: Record<number, {l:string;t?:string}> = {}
statusOpts.forEach(o => statusMap_price[o.v] = { l: o.l, t: o.t })
function canSelect(row: any) {
  // 只能勾选 已审核 且 尚未转PO 的报价
  return Number(row.status) === 2 && !row.poId
}

// ================ 多选行 + 批量转PO ================
const selection = ref<any[]>([])
function onSelectionChange(rows: any[]) {
  // 只保留符合 canSelect 的项 (用户若反选前端也保留)
  selection.value = rows.filter((r: any) => canSelect(r))
}
const convertDlg = ref(false)
const submitConverting = ref(false)

interface ConvertLine {
  priceId: number; supplierId: number; supplierCode?: string; supplierName?: string
  materialId: number; materialCode?: string; materialName?: string; specification?: string; unit?: string
  unitPrice: number; taxRate: number; minQty: number
  validFrom?: string; validTo?: string
  qty: number; deliveryDate?: string; remark?: string
  invalid: boolean
}
const convertRows = ref<ConvertLine[]>([])
const hd = reactive<{
  supplierId?: number; supplierName?: string; supplierCode?: string
  orderDate: string; deliveryDate: string
  paymentTerm: string; currency: string; taxRate: number; remark: string
}>({
  supplierId: undefined, supplierName: undefined, supplierCode: undefined,
  orderDate: '', deliveryDate: '', paymentTerm: '',
  currency: 'CNY', taxRate: 13, remark: ''
})

function todayStr() {
  const d = new Date(); const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0')
  return `${d.getFullYear()}-${m}-${day}`
}
function plusDays(ds: string, days: number) {
  if (!ds) return ''
  const d = new Date(ds); d.setDate(d.getDate() + days)
  const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0')
  return `${d.getFullYear()}-${m}-${day}`
}
watch(convertRows, refreshTotal, { deep: true })
function refreshTotal() {}  // placeholder replaced later by computed (no need action, only trigger at changes)
function removeConvertRow(i: number) { convertRows.value.splice(i, 1) }
function lineAmount(row: ConvertLine | any) {
  const q = Number(row.qty) || 0
  const p = Number(row.unitPrice) || 0
  return q * p
}
const totalLineAmount = computed(() => {
  return convertRows.value.reduce((s, r) => s + lineAmount(r), 0)
})
const totalTaxAmount = computed(() => {
  return convertRows.value.reduce((s, r) => {
    const tr = Number((r as any).taxRate); if (Number.isNaN(tr) || tr <= 0) return s
    return s + lineAmount(r) * (tr / 100)
  }, 0)
})
const canSubmitConvert = computed(() => {
  if (!hd.supplierId) return false
  if (!hd.orderDate || !hd.deliveryDate) return false
  if (!convertRows.value.length) return false
  for (const r of convertRows.value) {
    if (!r.priceId) return false
    const q = Number(r.qty); const mq = Number(r.minQty || 1)
    if (!Number.isFinite(q) || q <= 0 || q < mq) return false
    if (!Number.isFinite(Number(r.unitPrice)) || Number(r.unitPrice) < 0) return false
  }
  return true
})
function openConvertDialog() {
  const rows = selection.value.slice().filter(canSelect)
  if (!rows.length) { ElMessage.warning('请先勾选"已审核"且未转PO的供应商报价（支持跨页多选的话仅对当前页）'); return }
  // 必须同一供应商
  const set = new Set(rows.map(r => Number(r.supplierId)))
  if (set.size !== 1) {
    ElMessage.warning(`批量转PO要求勾选同一供应商的报价，当前选中 ${set.size} 家`)
    return
  }
  const first = rows[0]
  hd.supplierId   = Number(first.supplierId)
  hd.supplierName = first.supplierName
  hd.supplierCode = first.supplierCode
  hd.orderDate    = todayStr()
  hd.deliveryDate = plusDays(todayStr(), 20)
  hd.paymentTerm  = '月结60天'
  hd.currency     = first.currency || 'CNY'
  hd.taxRate      = Number(first.taxRate) || 13
  hd.remark       = ''
  convertRows.value = rows.map((r: any): ConvertLine => {
    return {
      priceId: Number(r.priceId), supplierId: Number(r.supplierId),
      supplierCode: r.supplierCode, supplierName: r.supplierName,
      materialId: Number(r.materialId), materialCode: r.materialCode,
      materialName: r.materialName, specification: r.specification, unit: r.unit,
      unitPrice: Number(r.unitPrice) || 0, taxRate: Number(r.taxRate) || (Number(hd.taxRate) || 13),
      minQty: Number(r.minQty) || 1,
      validFrom: r.validFrom, validTo: r.validTo,
      qty: Number(r.minQty) || 1, deliveryDate: plusDays(todayStr(), 15), remark: '', invalid: false
    }
  })
  convertDlg.value = true
}
async function submitConvert() {
  try {
    await hasPermOrThrow('scm:price:convert')
    if (!canSubmitConvert.value) { ElMessage.warning('请检查红色必填项（供应商/日期/数量不满足最小订购/单价）'); return }
    submitConverting.value = true
    const items = convertRows.value.map(r => ({
      priceId: r.priceId, qty: Number(r.qty),
      deliveryDate: r.deliveryDate || hd.deliveryDate,
      unitPrice: Number(r.unitPrice), taxRate: Number(r.taxRate), remark: r.remark
    }))
    const extra = {
      orderDate: hd.orderDate, deliveryDate: hd.deliveryDate,
      paymentTerm: hd.paymentTerm, taxRate: Number(hd.taxRate),
      currency: hd.currency, remark: hd.remark
    }
    const poId = await scmPriceApi.convertOrder({ supplierId: Number(hd.supplierId!), items, extra }) as any
    const id = (typeof poId === 'number' || typeof poId === 'string') ? poId : (poId?.data ?? poId)
    ElMessage.success(`生成采购订单成功 → PO ID=${id}，页面即将跳转到采购订单`)
    convertDlg.value = false
    await loadList(1)
    // 跳转采购订单页
    setTimeout(() => {
      const route = router.resolve({ path: '/scm/purchase' })
      if (route.name) router.push({ path: '/scm/purchase', query: { highlight: String(id) } })
    }, 600)
  } catch (e: any) {
    ElMessage.error(e?.message || '转采购订单失败')
  } finally { submitConverting.value = false }
}

const query = reactive<ScmPriceListQ>({
  pageNum: 1, pageSize: 20,
  supplierId: undefined as number | undefined,
  materialId: undefined as number | undefined,
  materialCode: '',
  materialName: '',
  supplierName: '',
  currency: '',
  status: undefined as number | undefined
})
const list = ref<ScmSupplierPrice[]>([])
const total = ref(0)
const loading = ref(false)

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await scmPriceApi.list({
      pageNum: query.pageNum, pageSize: query.pageSize,
      supplierId: query.supplierId,
      materialId: query.materialId,
      materialCode: query.materialCode,
      materialName: query.materialName,
      supplierName: query.supplierName,
      currency: query.currency,
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
  ;(query as any).supplierId = undefined
  ;(query as any).materialId = undefined
  ;(query as any).materialCode = ''
  ;(query as any).materialName = ''
  ;(query as any).supplierName = ''
  ;(query as any).currency = ''
  ;(query as any).status = undefined
  loadList(1)
}

// ============ 按物料分组 (span-method 合并物料列) ============
// 后端已按 material_id ASC 排序, 同物料报价相邻
function objectSpanMethod({ row, rowIndex, columnIndex }: any) {
  // 物料信息列(第0列) 合并同物料的连续行
  if (columnIndex === 0) {
    const prev = rowIndex > 0 ? list.value[rowIndex - 1] : null
    if (!prev || prev.materialId !== row.materialId) {
      // 起始行, 向后统计同物料行数
      let span = 1
      for (let i = rowIndex + 1; i < list.value.length; i++) {
        if (list.value[i].materialId === row.materialId) span++
        else break
      }
      return { rowspan: span, colspan: 1 }
    }
    return { rowspan: 0, colspan: 0 }
  }
}

// ============ 比价: 同物料最低价高亮 ============
const minPriceMap = computed(() => {
  const map = new Map<number, number>()
  for (const r of list.value) {
    if (r.unitPrice == null) continue
    const cur = map.get(r.materialId)
    if (cur == null || Number(r.unitPrice) < cur) map.set(r.materialId, Number(r.unitPrice))
  }
  return map
})
function cellClassName({ row, column }: any) {
  if (column.property === 'unitPrice') {
    const min = minPriceMap.value.get(row.materialId)
    if (min != null && Number(row.unitPrice) === min) return 'min-price-cell'
  }
  return ''
}
function rowClassName({ row }: any) {
  return row.isDefault === 1 ? 'default-price-row' : ''
}

// 动态行操作: 草稿→提审→已审核  (状态机推进 / 停用 / 转单跳转)
function buildRowActionList(row: any) {
  const st = Number(row.status)
  const arr: any[] = []
  if (st === 0) {
    arr.push({ label: '提审', type: 'primary', key: 'submit', permission: 'scm:price:edit',
      onClick: async (r: any) => {
        try { await hasPermOrThrow('scm:price:edit'); await scmPriceApi.submit(r.priceId); ElMessage.success('已提交审核,状态=待审核'); await loadList() }
        catch (e: any) { ElMessage.error(e?.message || '提审失败') }
      }})
  }
  if (st === 1) {
    arr.push({ label: '通过审核', type: 'success', key: 'audit', permission: 'scm:price:audit',
      onClick: async (r: any) => {
        try { await hasPermOrThrow('scm:price:audit'); await scmPriceApi.audit(r.priceId); ElMessage.success('审核通过,状态=已审,可批量转PO'); await loadList() }
        catch (e: any) { ElMessage.error(e?.message || '审核失败') }
      }})
    arr.push({ label: '拒绝', type: 'danger', key: 'reject', permission: 'scm:price:audit',
      confirm: '确认审核拒绝此报价条？',
      onClick: async (r: any) => {
        try { await hasPermOrThrow('scm:price:audit'); await scmPriceApi.auditFail(r.priceId, '前端拒绝'); ElMessage.success('已拒绝'); await loadList() }
        catch (e: any) { ElMessage.error(e?.message || '拒绝失败') }
      }})
  }
  if (st === 2) {
    arr.push({ label: '停用报价', type: 'info', key: 'disable', permission: 'scm:price:edit',
      confirm: '确认停用此审核通过的报价条？',
      onClick: async (r: any) => {
        try { await hasPermOrThrow('scm:price:edit'); await scmPriceApi.disable(r.priceId); ElMessage.success('已停用'); await loadList() }
        catch (e: any) { ElMessage.error(e?.message || '停用失败') }
      }})
    arr.push({ label: '单独转PO', type: 'warning', key: 'single-convert', permission: 'scm:price:convert',
      onClick: (r: any) => { selection.value = [r]; openConvertDialog() } })
  }
  if (st === 3) {
    arr.push({ label: '重新启用', type: 'primary', key: 'enable', permission: 'scm:price:edit',
      onClick: async (r: any) => {
        try { await hasPermOrThrow('scm:price:edit'); await scmPriceApi.enable(r.priceId); ElMessage.success('已启用(返回草稿)'); await loadList() }
        catch (e: any) { ElMessage.error(e?.message || '启用失败') }
      }})
  }
  if (st === 4) {
    arr.push({ label: '修正后重提', type: 'primary', key: 'submit-again', permission: 'scm:price:edit',
      onClick: async (r: any) => {
        try { await hasPermOrThrow('scm:price:edit'); await scmPriceApi.submit(r.priceId); ElMessage.success('已重新提交审核'); await loadList() }
        catch (e: any) { ElMessage.error(e?.message || '提交失败') }
      }})
  }
  if (st === 5) {
    arr.push({ label: '查看对应PO', type: 'success', key: 'goto-po',
      onClick: (r: any) => {
        if (r.poId) router.push({ path: '/scm/purchase', query: { highlight: String(r.poId) } })
        else ElMessage.info('poId未回填')
      }})
  }
  arr.push({ label: '设为默认', type: 'primary', key: 'setDefault', permission: 'scm:price:default',
    onClick: async (r: any) => {
      try { await hasPermOrThrow('scm:price:default'); await scmPriceApi.setDefault(r.priceId); ElMessage.success('设为默认成功'); await loadList() }
      catch (e: any) { ElMessage.error(e?.message || '设为默认失败') }
    }})
  return arr
}

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
const dialogTitle = computed(() => (form.priceId ? (readonly.value ? '供应商价格详情' : '编辑供应商价格') : '新增供应商价格'))

const form = reactive<Partial<ScmSupplierPrice>>({
  supplierId: undefined as number | undefined,
  materialId: undefined as number | undefined,
  currency: '',
  unitPrice: undefined as number | undefined,
  taxRate: undefined as number | undefined,
  minQty: undefined as number | undefined,
  validFrom: '',
  validTo: '',
  quoteSource: '',
  isDefault: 0,
  status: undefined as number | undefined,
  remark: ''
})

function formDefaults() {
  form.supplierId = undefined
  form.materialId = undefined
  form.currency = ''
  form.unitPrice = undefined
  form.taxRate = undefined
  form.minQty = undefined
  form.validFrom = ''
  form.validTo = ''
  form.quoteSource = ''
  form.isDefault = 0
  form.status = undefined
  form.remark = ''
}

// ============ 物料/供应商远程搜索选择器 ============
const materialOptions = ref<PdmMaterial[]>([])
const materialLoading = ref(false)
const supplierOptions = ref<ScmSupplier[]>([])
const supplierLoading = ref(false)

// 物料搜索: 关键字同时匹配编码和名称 (后端 materialCode/materialName 各自 LIKE, 这里两次查询合并)
async function searchMaterial(kw: string) {
  if (!kw) { materialOptions.value = []; return }
  materialLoading.value = true
  try {
    const byName = await materialApi.list({ pageNum: 1, pageSize: 20, materialName: kw }) as any
    const byCode = await materialApi.list({ pageNum: 1, pageSize: 20, materialCode: kw }) as any
    const rows: PdmMaterial[] = [...((byName?.rows) || []), ...((byCode?.rows) || [])]
    // 去重 (按 materialId)
    const map = new Map<number, PdmMaterial>()
    for (const m of rows) if (!map.has(m.materialId)) map.set(m.materialId, m)
    materialOptions.value = Array.from(map.values()).slice(0, 30)
  } catch { materialOptions.value = [] }
  finally { materialLoading.value = false }
}
async function searchSupplier(kw: string) {
  if (!kw) { supplierOptions.value = []; return }
  supplierLoading.value = true
  try {
    const resp = await scmSupplierApi.list({ pageNum: 1, pageSize: 20, supplierName: kw }) as any
    supplierOptions.value = resp?.rows || []
  } catch { supplierOptions.value = [] }
  finally { supplierLoading.value = false }
}
// 编辑/详情时, 根据 ID 回填对应选项到下拉列表 (保证 label 能显示)
async function ensureMaterialOption(id?: number) {
  if (!id) return
  if (materialOptions.value.find(m => m.materialId === id)) return
  try {
    const resp = await materialApi.get(id) as any
    const m = resp?.data || resp
    if (m && m.materialId) materialOptions.value = [m, ...materialOptions.value]
  } catch { /* 忽略, 不影响编辑 */ }
}
async function ensureSupplierOption(id?: number) {
  if (!id) return
  if (supplierOptions.value.find(s => s.supplierId === id)) return
  try {
    const resp = await scmSupplierApi.get(id) as any
    const s = resp?.data || resp
    if (s && s.supplierId) supplierOptions.value = [s, ...supplierOptions.value]
  } catch { /* 忽略, 不影响编辑 */ }
}
// 选物料时无需额外联动 (价格仍手动录入)
function onMaterialChange(_v: number) { /* 预留: 后续可带出该物料历史报价 */ }

function openDialog(row?: any) {
  formDefaults()
  materialOptions.value = []
  supplierOptions.value = []
  if (row && row.priceId) {
    Object.assign(form, row)
    readonly.value = false
    // 回填已选物料/供应商到下拉列表
    ensureMaterialOption(row.materialId)
    ensureSupplierOption(row.supplierId)
  } else {
    readonly.value = false
  }
  dialog.value = true
}
async function onView(row: any) {
  formDefaults()
  materialOptions.value = []
  supplierOptions.value = []
  try {
    const detail = (await scmPriceApi.get(row.priceId)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true
    ensureMaterialOption(form.materialId)
    ensureSupplierOption(form.supplierId)
    dialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '获取详情失败')
  }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.priceId) {
      await scmPriceApi.edit(form as any)
      ElMessage.success('修改成功')
    } else {
      await scmPriceApi.add(form as any)
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
    await hasPermOrThrow('scm:price:remove')
    await scmPriceApi.remove(row.priceId)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(() => loadList())
</script>

<style scoped>
.page-scm-price { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; display: flex; align-items: center; gap: 12px; }
.table-toolbar .group-tip { font-size: 12px; color: #909399; display: inline-flex; align-items: center; gap: 6px; }
.table-toolbar .legend-min { color: #e6a23c; font-weight: 600; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }

/* 物料信息单元格 */
.mat-cell { line-height: 1.5; }
.mat-code { font-weight: 600; color: #303133; font-size: 13px; }
.mat-name { color: #606266; font-size: 12px; }
.mat-spec { color: #909399; font-size: 11px; margin-top: 2px; }
.sub-code { color: #909399; font-size: 11px; }

/* 最低价高亮 */
:deep(.min-price-cell) { background-color: #fdf6ec !important; }
:deep(.min-price-cell .price-val) { color: #e6a23c; font-weight: 700; }

/* 默认供应商行 */
:deep(.default-price-row) { background-color: #fef0f0 !important; }

/* 弹窗: 默认供应商提示 */
.default-tip { margin-left: 12px; color: #909399; font-size: 12px; }

/* -------- 批量转PO 弹窗 -------- */
.convert-wrap { padding: 0 8px; }
.header-form { background: #f5f7fa; padding: 12px 14px; border-radius: 6px; }
.hint-row { margin: 14px 0 10px; }
.hint-row b.c-blue { color:#409EFF }
.hint-row b.c-orange { color:#E6A23C }
.hint-row b.c-red { color:#F56C6C }
.badge { display:inline-block; min-width:18px; padding:0 6px; margin-left:6px; border-radius:9px; background:#67C23A; color:#fff; font-size:12px; text-align:center; line-height:18px; }
.ml-8 { margin-left: 8px; }
.line-table :deep(.row-invalid) { background:#fef0f0; }
.line-table .c-red { color:#F56C6C; font-weight:600 }
.c-blue { color:#409EFF; }
.c-orange { color:#E6A23C; }
.c-red { color:#F56C6C; }
</style>
