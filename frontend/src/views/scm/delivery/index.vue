<template>
  <div class="page-scm-delivery">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="到货单号"><el-input v-model="query.deliveryNo" placeholder="到货单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="关联订单号"><el-input v-model="query.poNo" placeholder="关联订单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="供应商"><el-input v-model="query.supplierName" placeholder="供应商" clearable style="width:180px" /></el-form-item>
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
      <el-button type="primary" v-hasPermi="'scm:delivery:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增采购到货</el-button>
      <el-button><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="deliveryNo" label="到货单号" width="160" />
      <el-table-column prop="poNo" label="订单号" width="160" />
      <el-table-column prop="bizCategory" label="采购大类" width="110">
        <template #default="{ row }">{{ bizLabel((row as any).bizCategory) }}</template>
      </el-table-column>
      <el-table-column prop="supplierName" label="供应商" width="180" show-overflow-tooltip />
      <el-table-column prop="deliveryDate" label="到货日期" width="110" />
      <el-table-column prop="waybillNo" label="运单号" width="140" />
      <el-table-column prop="carrierName" label="承运商" width="140" />
      <el-table-column prop="totalQty" label="总数量" width="100"><template #default="{ row }">{{ row.totalQty == null ? '-' : Number(row.totalQty).toLocaleString() }}</template></el-table-column>
      <el-table-column prop="totalAmount" label="总金额" width="120"><template #default="{ row }">{{ row.totalAmount == null ? '-' : '¥' + Number(row.totalAmount).toFixed(2) }}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l || String(row.status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="560" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onViewLines(row)">明细</el-button>
          <el-button v-hasPermi="'scm:delivery:list'" size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'scm:delivery:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-else size="small" link type="primary" disabled :title="cannotEditReason(row)">编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除当前记录？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'scm:delivery:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <el-popconfirm v-else title="确认删除当前记录？">
            <template #reference><el-button size="small" link type="danger" disabled :title="cannotRemoveReason(row)">删除</el-button></template>
          </el-popconfirm>
          <template v-for="a in stateActionsFor(row)" :key="a.key">
            <el-tooltip v-if="a.disabled" :content="a.disabledReason" placement="top">
              <el-button size="small" link :type="a.type || 'default'" disabled>{{ a.label }}</el-button>
            </el-tooltip>
            <el-button v-else-if="!a.permission || hasPermission(a.permission)"
              v-hasPermi="a.permission ? [a.permission] : undefined"
              size="small" link :type="a.type || 'default'"
              @click="onActionClick(a, row)">
              {{ a.label }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :total="total" :page-sizes="[20, 50, 100]"
                     @size-change="loadList(1)" @current-change="loadList()" />
    </div>

    <!-- 新增/编辑/详情 弹窗 -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
        <el-form-item label="到货单号" required prop="deliveryNo"><el-input v-model="form.deliveryNo" /></el-form-item>
        <el-form-item label="采购订单" prop="poId">
          <el-select v-model="form.poId" filterable clearable placeholder="选择采购订单" style="width:100%">
            <el-option v-for="p in purchaseOrders" :key="p.poId" :label="`${p.poNo || ''} ${p.supplierName || ''}`" :value="p.poId">
              <span style="float:left">{{ p.poNo }} {{ p.supplierName }}</span>
              <span style="float:right;color:#999;font-size:12px">#{{ p.poId }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="订单号" prop="poNo"><el-input v-model="form.poNo" /></el-form-item>
        <el-form-item label="采购大类" prop="bizCategory">
          <el-select v-model="form.bizCategory" placeholder="默认从关联采购订单继承" clearable style="width:100%">
            <el-option v-for="o in BIZ_CATEGORIES" :key="o.v" :label="o.l" :value="o.v" />
          </el-select>
          <div style="color:var(--el-text-color-secondary); font-size:12px; margin-top:2px;">未填写时回源自关联采购订单的biz_category；再未填则默认走电子仓</div>
        </el-form-item>
        <el-form-item label="供应商" required prop="supplierId">
          <el-select v-model="form.supplierId" filterable clearable placeholder="选择供应商" style="width:100%">
            <el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
              <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ s.contact }} {{ s.phone }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="供应商名称" prop="supplierName"><el-input v-model="form.supplierName" /></el-form-item>
        <el-form-item label="运单号" prop="waybillNo"><el-input v-model="form.waybillNo" /></el-form-item>
        <el-form-item label="承运商" prop="carrierName"><el-input v-model="form.carrierName" /></el-form-item>
        <el-form-item label="车牌号" prop="vehicleNo"><el-input v-model="form.vehicleNo" /></el-form-item>
        <el-form-item label="司机" prop="driverName"><el-input v-model="form.driverName" /></el-form-item>
        <el-form-item label="司机电话" prop="driverPhone"><el-input v-model="form.driverPhone" /></el-form-item>
        <el-form-item label="到货日期" prop="deliveryDate"><el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="仓库" required prop="warehouseId">
          <el-select v-model="form.warehouseId" filterable clearable placeholder="选择仓库" style="width:100%">
            <el-option v-for="w in warehouses" :key="w.warehouseId" :label="`${w.warehouseCode || ''} ${w.warehouseName || ''}`" :value="w.warehouseId">
              <span style="float:left">{{ w.warehouseCode }} {{ w.warehouseName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ w.warehouseType || '' }} {{ w.address || '' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="总数量" prop="totalQty"><el-input-number v-model="form.totalQty" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="总金额" prop="totalAmount"><el-input-number v-model="form.totalAmount" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="IQC结果" prop="iqcResult"><el-input-number v-model="form.iqcResult" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="IQC报告" prop="iqcReport"><el-input v-model="form.iqcReport" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 到货明细弹窗 -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="1040px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="到货单号">{{ linesOwner.deliveryNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ linesOwner.poNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="采购大类">{{ bizLabel((linesOwner as any).bizCategory) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ linesOwner.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到货日期">{{ linesOwner.deliveryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总数量">{{ linesOwner.totalQty == null ? '-' : Number(linesOwner.totalQty).toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="typeof linesOwner.status === 'number'" :type="statusMap[linesOwner.status]?.t || ''">
              {{ statusMap[linesOwner.status]?.l || String(linesOwner.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider content-position="left">
        <span style="display:flex; align-items:center; gap:10px;">
          <span>到货明细行（共 {{ lines.length }} 行）</span>
          <el-button type="primary" size="small" @click="openLineDialog()">新增行</el-button>
        </span>
      </el-divider>
      <el-table v-loading="linesLoading" :data="lines" stripe border size="small" max-height="520">
        <el-table-column prop="lineNo" label="行号" width="60" align="center" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格" width="140" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="deliveryQty" label="送货数量" width="90" align="right">
          <template #default="{ row }">{{ row.deliveryQty == null ? '-' : Number(row.deliveryQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="acceptedQty" label="合格数量" width="90" align="right">
          <template #default="{ row }">{{ row.acceptedQty == null ? 0 : Number(row.acceptedQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="rejectedQty" label="拒收数量" width="90" align="right">
          <template #default="{ row }">{{ row.rejectedQty == null ? 0 : Number(row.rejectedQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="concessionQty" label="特采数量" width="90" align="right">
          <template #default="{ row }">{{ row.concessionQty == null ? 0 : Number(row.concessionQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次号" width="130" />
        <el-table-column prop="producedDate" label="生产日" width="100" />
        <el-table-column prop="expireDate" label="效期" width="100" />
        <el-table-column prop="iqcDefectDesc" label="IQC说明" min-width="150" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openLineDialog(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="onRemoveLine(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="lines-footer"><span class="lines-total">共 {{ lines.length }} 行</span></div>
      <template #footer><el-button @click="linesDialog = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="lineDialog" :title="lineDialogTitle" width="600px" destroy-on-close top="10vh">
      <el-form ref="lineFormRef" :model="lineForm" :rules="lineRules" label-width="90px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="lineForm.materialId" placeholder="输入编码/名称搜索" filterable style="width:100%" @change="onLineMaterialChange">
            <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
              <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="物料编码"><el-input v-model="lineForm.materialCode" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料名称"><el-input v-model="lineForm.materialName" maxlength="200" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="lineForm.specification" maxlength="200" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="lineForm.unit" maxlength="20" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="6"><el-form-item label="送货数量" prop="deliveryQty"><el-input-number v-model="lineForm.deliveryQty" :min="0" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="合格数量"><el-input-number v-model="lineForm.acceptedQty" :min="0" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="拒收数量"><el-input-number v-model="lineForm.rejectedQty" :min="0" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="特采数量"><el-input-number v-model="lineForm.concessionQty" :min="0" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="批次号"><el-input v-model="lineForm.batchNo" maxlength="50" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="生产日期"><el-date-picker v-model="lineForm.producedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="有效期"><el-date-picker v-model="lineForm.expireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="IQC说明"><el-input v-model="lineForm.iqcDefectDesc" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="lineForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineDialog = false">取消</el-button>
        <el-button type="primary" :loading="lineSubmitLoading" @click="onLineSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus, Download } from '@element-plus/icons-vue'
import { scmDeliveryApi, scmSupplierApi, warehouseApi, scmPoApi, materialApi, type ScmDelListQ } from '@/api'
import type { ScmDeliveryNote, ScmDeliveryNoteLine } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'ScmDeliveryPage' })

const router = useRouter()
const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function hasPermOrThrow(p: string) { if (!hasPermission(p)) throw new Error('无此操作权限: ' + p); return true }

// 0 待收货 1 收货中 2 已收货 3 IQC检验中 4 入库完成 5 部分退货 9 取消
const STATUS_LABEL: Record<number, { l:string; t?: string }> = {
  0: { l:'待收货', t:'info' },
  1: { l:'收货中', t:'warning' },
  2: { l:'已收货', t:'primary' },
  3: { l:'IQC检验中', t:'warning' },
  4: { l:'入库完成', t:'success' },
  5: { l:'部分退货', t:'' },
  9: { l:'取消', t:'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v,m])=>({ v: Number(v), l: m.l, t: m.t }))
const statusMap = STATUS_LABEL

const BIZ_CATEGORIES = [
  { v: 'ELECTRONIC', l: '电子物料采购' },
  { v: 'ACCESSORY',  l: '配件采购' },
  { v: 'SPARE',      l: '备品备件' }
] as const
function bizLabel(v?: string) {
  if (!v) return '-'
  return BIZ_CATEGORIES.find((b) => b.v === v)?.l ?? v
}

const ACTION_DEF: Record<string, { label: string; type?: any; permission: string; allowFrom: number[]; disabledReason: (s:number)=>string }> = {
  receive: { label:'收货登记', type:'primary', permission:'scm:delivery:receive', allowFrom:[0,1], disabledReason:(s)=>`仅「待收货/收货中」可登记（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  iqc:     { label:'IQC检验', type:'default', permission:'scm:delivery:iqc',     allowFrom:[2],   disabledReason:(s)=>`仅「已收货」可检验（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  stockIn: { label:'生成入库单', type:'primary', permission:'scm:delivery:stockin', allowFrom:[2,3], disabledReason:(s)=>`仅「已收货/IQC检验中」可入库（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  cancel:  { label:'取消', type:'danger', permission:'scm:delivery:cancel', allowFrom:[0,1], disabledReason:(s)=>`仅「待收货/收货中」可取消（当前:${STATUS_LABEL[s]?.l ?? s}）` }
}
const EDITABLE_STATUS = new Set<number>([0])
function canEdit(row: ScmDeliveryNote)  { return EDITABLE_STATUS.has(Number(row.status)) }
function canRemove(row: ScmDeliveryNote){ return EDITABLE_STATUS.has(Number(row.status)) }
function cannotEditReason(row: ScmDeliveryNote)   { return `仅「待收货」可编辑（当前:${STATUS_LABEL[row.status as number]?.l ?? row.status}）` }
function cannotRemoveReason(row: ScmDeliveryNote) { return `仅「待收货」可删除（当前:${STATUS_LABEL[row.status as number]?.l ?? row.status}）` }

function stateActionsFor(row: ScmDeliveryNote): any[] {
  const s = Number(row.status)
  return (['receive','iqc','stockIn','cancel'] as const).map((key) => {
    const d = ACTION_DEF[key]
    const enabled = d.allowFrom.includes(s)
    return {
      key, label: d.label, type: d.type, permission: d.permission,
      disabled: !enabled,
      disabledReason: enabled ? '' : d.disabledReason(s),
      onClick: (async (r: any) => {
        try {
          await hasPermOrThrow(d.permission)
          const resp = await (scmDeliveryApi as any)[key](r.deliveryId) as any
          if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          ElMessage.success(d.label + '成功'); await loadList()
          // ---- 生成入库单 → 按 biz_category 跳转对应仓库审核页 ----
          if (key === 'stockIn') {
            const biz: string = (r.bizCategory || 'ELECTRONIC').toUpperCase()
            if (biz === 'ACCESSORY') {
              ElMessageBox.alert(
                '配件采购入库草稿已生成，请进入【仓库管理→配件仓库→配件库存台账】查看记录；记录审核通过后库存正式增加。',
                '已生成配件入库草稿', { type: 'info' }
              ).finally(() => router.push('/fas/acc-stock'))
            } else if (biz === 'SPARE') {
              ElMessage.info('备品备件入库业务模块暂未接入')
            } else {
              ElMessage.success('电子仓采购入库草稿已生成，即将跳转【电子料入库】审核...')
              setTimeout(() => router.push('/wms/stock-in'), 400)
            }
          }
        } catch (e: any) { ElMessage.error(e?.message || d.label + '失败') }
      }),
      confirm: (key === 'stockIn' || key === 'cancel') ? `确认${d.label}当前记录？`
               : (key === 'receive' ? `确认收货登记？操作后将更新状态并进入后续IQC/入库流程。` : '')
    }
  })
}

const query = reactive<ScmDelListQ>({ pageNum:1, pageSize:20, deliveryNo:'', poNo:'', supplierName:'', status: undefined as number|undefined })
const list = ref<ScmDeliveryNote[]>([])
const total = ref(0)
const loading = ref(false)

// 下拉选项数据
const suppliers = ref<any[]>([])
const warehouses = ref<any[]>([])
const purchaseOrders = ref<any[]>([])
const materialOptions = ref<any[]>([])
async function loadOptions() {
  try {
    const [s, w, p, m] = await Promise.all([
      scmSupplierApi.options(),
      warehouseApi.options(),
      scmPoApi.options(),
      materialApi.list({ pageNum: 1, pageSize: 1000 }) as any
    ])
    suppliers.value = (s as any)?.data || s || []
    warehouses.value = (w as any)?.data || w || []
    purchaseOrders.value = (p as any)?.data || p || []
    materialOptions.value = (m as any)?.rows || []
  } catch (e: any) {
    try {
      const [s2, w2, p2] = await Promise.all([
        scmSupplierApi.list({ pageNum: 1, pageSize: 1000 } as any),
        warehouseApi.list({ pageNum: 1, pageSize: 1000 } as any),
        scmPoApi.list({ pageNum: 1, pageSize: 1000 } as any)
      ])
      suppliers.value = (s2 as any)?.rows || []
      warehouses.value = (w2 as any)?.rows || []
      purchaseOrders.value = (p2 as any)?.rows || []
    } catch {
      // ignore
    }
  }
}
function onLineMaterialChange(val: any) {
  const m = materialOptions.value.find(x => x.materialId === val)
  if (m) {
    lineForm.materialCode = m.materialCode
    lineForm.materialName = m.materialName
    lineForm.specification = m.specification
    if (m.unit) lineForm.unit = m.unit
  }
}

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await scmDeliveryApi.list({ pageNum: query.pageNum, pageSize: query.pageSize, deliveryNo: query.deliveryNo, poNo: query.poNo, supplierName: query.supplierName, status: query.status })) as any
    list.value = resp?.rows || []
    total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败'); list.value = []; total.value = 0 }
  finally { loading.value = false }
}
function resetQuery() { (query as any).deliveryNo = ''; (query as any).poNo = ''; (query as any).supplierName = ''; (query as any).status = undefined; loadList(1) }

function onActionClick(a: any, row: any) {
  if (a.confirm) { ElMessageBox.confirm(a.confirm, '操作确认', { type:'warning' }).then(()=>a.onClick?.(row)).catch(()=>{}) }
  else { a.onClick?.(row) }
}

const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => (form.deliveryId ? (readonly.value ? '采购到货详情' : '编辑采购到货') : '新增采购到货'))
const form = reactive<Partial<ScmDeliveryNote>>({
  deliveryNo:'', poId:undefined, poNo:'', bizCategory:'', supplierId:undefined, supplierName:'', waybillNo:'', carrierName:'',
  vehicleNo:'', driverName:'', driverPhone:'', deliveryDate:'', warehouseId:undefined,
  totalQty:undefined, totalAmount:undefined, iqcResult:undefined, iqcReport:'', remark:''
})
function formDefaults() {
  form.deliveryNo=''; form.poId=undefined; form.poNo=''; form.bizCategory=''; form.supplierId=undefined; form.supplierName=''; form.waybillNo=''; form.carrierName=''
  form.vehicleNo=''; form.driverName=''; form.driverPhone=''; form.deliveryDate=''; form.warehouseId=undefined
  form.totalQty=undefined; form.totalAmount=undefined; form.iqcResult=undefined; form.iqcReport=''; form.remark=''
}
function openDialog(row?: any) {
  formDefaults()
  if (row?.deliveryId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: any) {
  formDefaults()
  try {
    const detail = (await scmDeliveryApi.get(row.deliveryId)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.deliveryId) { await scmDeliveryApi.edit(form as any); ElMessage.success('修改成功') }
    else { await scmDeliveryApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: any) {
  try {
    await hasPermOrThrow('scm:delivery:remove')
    await scmDeliveryApi.remove(row.deliveryId)
    ElMessage.success('删除成功'); await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// 明细弹窗
const linesDialog = ref(false)
const linesOwner = ref<ScmDeliveryNote | null>(null)
const lines = ref<ScmDeliveryNoteLine[]>([])
const linesLoading = ref(false)
const linesTitle = computed(() => `采购到货明细${linesOwner.value?.deliveryNo ? ' - ' + linesOwner.value.deliveryNo : ''}`)
async function onViewLines(row: ScmDeliveryNote) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    const resp = (await scmDeliveryApi.lines(row.deliveryId!)) as any
    const rows = resp?.rows || []
    lines.value = (rows as ScmDeliveryNoteLine[]).slice().sort((a,b)=>(a.lineNo||0)-(b.lineNo||0))
  } catch (e: any) { ElMessage.error(e?.message || '加载明细失败'); lines.value = [] }
  finally { linesLoading.value = false }
}

// ---------- 行编辑弹窗 ----------
const lineDialog = ref(false)
const lineSubmitLoading = ref(false)
const lineFormRef = ref<FormInstance | null>(null)
const lineEditingId = ref<number | null>(null)
const lineForm = reactive<Partial<ScmDeliveryNoteLine>>({
  deliveryId: undefined, materialId: 1, materialCode: '', materialName: '', specification: '', unit: 'PCS',
  deliveryQty: 0, acceptedQty: 0, rejectedQty: 0, concessionQty: 0,
  batchNo: '', producedDate: '', expireDate: '', iqcDefectDesc: '', remark: ''
})
const lineRules = {
  materialId:   [{ required: true, message: '请选择物料', trigger: 'change' }],
  deliveryQty:  [{ required: true, validator: (_r: any, v: any, cb: any) => (v != null && Number(v) > 0) ? cb() : cb(new Error('送货数量需大于0')), trigger: 'change' }],
} as any
const lineDialogTitle = computed(() => lineEditingId.value ? '编辑到货行' : '新增到货行')
function resetLineForm() {
  Object.assign(lineForm, {
    deliveryId: linesOwner.value?.deliveryId ?? undefined, materialId: 1, materialCode: '', materialName: '',
    specification: '', unit: 'PCS', deliveryQty: 0, acceptedQty: 0, rejectedQty: 0, concessionQty: 0,
    batchNo: '', producedDate: linesOwner.value?.deliveryDate ?? '', expireDate: '',
    iqcDefectDesc: '', remark: ''
  })
  lineEditingId.value = null
}
function openLineDialog(row?: ScmDeliveryNoteLine) {
  if (!linesOwner.value?.deliveryId) { ElMessage.warning('缺少主单ID'); return }
  if (row) { Object.assign(lineForm, { ...row }); lineEditingId.value = (row.lineId as number) ?? null }
  else resetLineForm()
  lineDialog.value = true
}
async function onLineSubmit() {
  lineSubmitLoading.value = true
  try {
    await lineFormRef.value?.validate()
    const payload = { ...lineForm, deliveryId: linesOwner.value!.deliveryId! }
    if (lineEditingId.value) {
      await scmDeliveryApi.editLine({ ...payload, lineId: lineEditingId.value } as any)
      ElMessage.success('修改行成功')
    } else {
      await scmDeliveryApi.addLine(payload as any)
      ElMessage.success('新增行成功')
    }
    lineDialog.value = false
    await onViewLines(linesOwner.value!)
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { lineSubmitLoading.value = false }
}
async function onRemoveLine(row: ScmDeliveryNoteLine) {
  try { await ElMessageBox.confirm(`确定删除行号 ${row.lineNo} ?`, '提示', { type: 'warning' }) } catch { return }
  try { await scmDeliveryApi.removeLine(row.lineId as number); ElMessage.success('删除成功'); await onViewLines(linesOwner.value!) }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(() => { loadOptions(); loadList() })
</script>

<style scoped>
.page-scm-delivery { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.lines-header { margin-bottom: 4px; }
.lines-footer { margin-top: 8px; display: flex; justify-content: flex-end; }
.lines-total { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
