<template>
  <div class="page-sal-order">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="订单号"><el-input v-model="query.soNo" placeholder="订单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="客户">
        <el-select v-model="query.customerId" placeholder="输入编码/名称搜索" clearable filterable style="width:240px">
          <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName} ${c.shortName || ''}`" :value="c.customerId" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.soType" placeholder="全部" clearable style="width:140px">
          <el-option v-for="o in sOpt_soType" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="交付日期"><el-date-picker v-model="query.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="YYYY-MM-DD" style="width:160px" /></el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'sal:order:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增销售订单</el-button>
      <el-button v-hasPermi="'sal:order:export'" :loading="exporting" @click="doExport"><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="soNo" label="订单号" width="160" />
      <el-table-column prop="soType" label="类型" width="90" />
      <el-table-column prop="customerName" label="客户" width="180" show-overflow-tooltip />
      <el-table-column prop="salesRepName" label="销售员" width="100" />
      <el-table-column prop="soDate" label="下单日期" width="110" />
      <el-table-column prop="deliveryDate" label="交付日期" width="110" />
      <el-table-column prop="totalAmount" label="订单金额" width="120"><template #default="{ row }">{{ row.totalAmount == null ? '-' : '¥' + Number(row.totalAmount).toFixed(2) }}</template></el-table-column>
      <el-table-column prop="currency" label="币种" width="70" />
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
          <el-button v-hasPermi="'sal:order:list'" size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'sal:order:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-else size="small" link type="primary" disabled :title="cannotEditReason(row)">编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除当前记录？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'sal:order:remove'" size="small" link type="danger">删除</el-button></template>
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
          <!-- P0: 订单行→齐套 入口 -->
          <el-button v-hasPermi="['sal:kitting:fromOrder']" size="small" link type="warning"
             :disabled="typeof row.status !== 'number' || [0, 9, 7].includes(row.status)"
             :title="([0, 9, 7].includes(row.status) ? '订单草稿/取消/驳回不可执行齐套' : '进入订单明细后选择成品行')"
             @click="onViewLines(row)">
            执行齐套
          </el-button>
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
        <el-form-item label="订单号" prop="soNo">
          <el-input v-model="form.soNo" placeholder="保存时后端自动生成" />
        </el-form-item>
        <el-form-item label="类型" prop="soType">
          <el-select v-model="form.soType" placeholder="请选择" style="width:100%">
            <el-option v-for="o in fOpt_soType" :key="o.v" :label="o.l" :value="o.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户" required prop="customerId">
          <el-select v-model="form.customerId" placeholder="输入编码/名称/简称搜索" filterable style="width:100%" @change="onCustomerChange">
            <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName} ${c.shortName || ''} ${c.contact || ''} ${c.phone || ''}`" :value="c.customerId">
              <span style="float:left">{{ c.customerCode }} {{ c.customerName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ c.contact }} {{ c.phone }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName"><el-input v-model="form.customerName" disabled /></el-form-item>
        <el-form-item v-if="creditInfo" label="信用占用">
          <div class="w-full">
            <el-alert
              v-if="creditInfo.hasOverdue"
              type="error" :closable="false" show-icon class="mb-1"
              :title="`存在逾期未收 ${fmtMoney(creditInfo.overdue)} CNY，审核将被拦截`" />
            <el-alert
              v-else-if="creditInfo.controlled && Number(creditInfo.available) < 0"
              type="error" :closable="false" show-icon class="mb-1"
              :title="`信用额度不足：额度 ${fmtMoney(creditInfo.creditLimit)} / 已用 ${fmtMoney(creditInfo.used)} / 可用 ${fmtMoney(creditInfo.available)} CNY`" />
            <el-alert
              v-else-if="!creditInfo.controlled"
              type="info" :closable="false" show-icon class="mb-1"
              title="该客户未启用信用额度控制（额度=0）" />
            <el-descriptions :column="4" border size="small">
              <el-descriptions-item label="信用额度">{{ fmtMoney(creditInfo.creditLimit) }}</el-descriptions-item>
              <el-descriptions-item label="已用(未收)">{{ fmtMoney(creditInfo.used) }}</el-descriptions-item>
              <el-descriptions-item label="可用额度">
                <span :class="Number(creditInfo.available) < 0 ? 'text-red-600 font-semibold' : ''">{{ fmtMoney(creditInfo.available) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="逾期未收">
                <span :class="Number(creditInfo.overdue) > 0 ? 'text-red-600 font-semibold' : ''">{{ fmtMoney(creditInfo.overdue) }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-form-item>
        <el-form-item label="联系人" prop="contactName"><el-input v-model="form.contactName" placeholder="请输入联系人" /></el-form-item>
        <el-form-item label="联系电话" prop="contactPhone"><el-input v-model="form.contactPhone" placeholder="请输入联系电话" /></el-form-item>
        <el-form-item label="销售员" prop="salesRepId">
          <el-select v-model="form.salesRepId" placeholder="选择销售员" clearable filterable style="width:100%">
            <el-option v-for="u in users" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="下单日期" prop="soDate"><el-date-picker v-model="form.soDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择下单日期" style="width:100%" /></el-form-item>
        <el-form-item label="交付日期" prop="deliveryDate"><el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择交付日期" style="width:100%" /></el-form-item>
        <el-form-item label="币种" prop="currency"><el-input v-model="form.currency" placeholder="CNY" /></el-form-item>
        <el-form-item label="税率(%)" prop="taxRate"><el-input-number v-model="form.taxRate" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="付款条件" prop="paymentTerm"><el-input v-model="form.paymentTerm" placeholder="请输入付款条件" /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 订单明细弹窗 -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="1000px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="订单号">{{ linesOwner.soNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ linesOwner.soType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ linesOwner.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="交付日期">{{ linesOwner.deliveryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">{{ linesOwner.totalAmount == null ? '-' : '¥' + Number(linesOwner.totalAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="typeof linesOwner.status === 'number'" :type="statusMap[linesOwner.status]?.t || ''">
              {{ statusMap[linesOwner.status]?.l || String(linesOwner.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider content-position="left">
        <span style="display:flex; align-items:center; gap:10px;">
          <span>订单明细行（共 {{ lines.length }} 行）</span>
          <el-button type="primary" size="small" @click="openLineDialog()">新增行</el-button>
        </span>
      </el-divider>
      <el-table v-loading="linesLoading" :data="lines" stripe border size="small" max-height="520">
        <el-table-column prop="lineNo" label="行号" width="70" align="center" />
        <el-table-column prop="materialCode" label="物料编码" width="150" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" width="160" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="orderQty" label="订单数量" width="100" align="right">
          <template #default="{ row }">{{ row.orderQty == null ? '-' : Number(row.orderQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="shippedQty" label="已发数量" width="100" align="right">
          <template #default="{ row }">{{ row.shippedQty == null ? 0 : Number(row.shippedQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="returnedQty" label="已退数量" width="100" align="right">
          <template #default="{ row }">{{ row.returnedQty == null ? 0 : Number(row.returnedQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="110" align="right">
          <template #default="{ row }">{{ row.unitPrice == null ? '-' : '¥' + Number(row.unitPrice).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="lineAmount" label="行金额" width="120" align="right">
          <template #default="{ row }">{{ row.lineAmount == null ? '-' : '¥' + Number(row.lineAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="requiredDate" label="交付日期" width="110" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openLineDialog(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="onRemoveLine(row)">删除</el-button>
            <el-button v-hasPermi="['sal:kitting:fromOrder']"
               size="small" link type="warning"
               :disabled="!linesOwner?.soId || typeof row.lineId !== 'number'"
               :loading="(kittingLine as any)[row.lineId!] === 'loading'"
               @click="doCreateKittingFromLine(row)">
              执行齐套
            </el-button>
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
          <el-col :span="12"><el-form-item label="物料编码" prop="materialCode"><el-input v-model="lineForm.materialCode" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料名称" prop="materialName"><el-input v-model="lineForm.materialName" maxlength="200" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="规格型号"><el-input v-model="lineForm.specification" maxlength="200" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="lineForm.unit" maxlength="20" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="订单数量" prop="orderQty"><el-input-number v-model="lineForm.orderQty" :min="0" :precision="4" controls-position="right" style="width:100%" @change="onQtyChange" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单价"><el-input-number v-model="lineForm.unitPrice" :min="0" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="折扣率"><el-input-number v-model="lineForm.discountRate" :min="0" :max="0.9999" :step="0.01" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="税率">
              <el-input-number v-model="lineForm.taxRate" :min="0" :max="1" :step="0.01" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行金额">
              <el-input-number v-model="lineForm.lineAmount" :min="0" :precision="2" controls-position="right" style="width:100%" />
              <div style="color:var(--el-text-color-secondary); font-size:12px; margin-top:2px;">默认 = 数量 × 单价 × (1 - 折扣率)</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="税额">
              <el-input-number v-model="lineForm.taxAmount" :min="0" :precision="2" controls-position="right" style="width:100%" />
              <div style="color:var(--el-text-color-secondary); font-size:12px; margin-top:2px;">默认 = 行金额 × 税率</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交付日期">
              <el-date-picker v-model="lineForm.requiredDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="lineForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
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
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus, Download } from '@element-plus/icons-vue'
import { salOrderApi, salCustomerApi, salPriceApi, salKittingApi, userApi, materialApi, type SalOrderListQ } from '@/api'
import type { SalSalesOrder, SalSalesOrderLine, SalCustomer } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'SalOrderPage' })

const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function hasPermOrThrow(p: string) { if (!hasPermission(p)) throw new Error('无此操作权限: ' + p); return true }

// 状态机（单一判定源）
// 0 草稿 1 提交 2 已审核 3 部分发货 4 全部发货 5 已完成 6 已驳回 9 取消
const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '草稿', t: 'info' },
  1: { l: '已提交', t: 'warning' },
  2: { l: '已审核', t: 'primary' },
  3: { l: '部分发货', t: 'warning' },
  4: { l: '全部发货', t: '' },
  5: { l: '已完成', t: 'success' },
  6: { l: '已驳回', t: 'danger' },
  9: { l: '取消', t: 'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v, m]) => ({ v: Number(v), l: m.l, t: m.t }))
const statusMap = STATUS_LABEL

const ACTION_DEF: Record<string, { label: string; type?: any; permission: string; allowFrom: number[]; disabledReason: (s: number) => string }> = {
  submit:   { label: '提交',   type: 'default', permission: 'sal:order:submit',  allowFrom: [0, 6], disabledReason: (s) => `仅「草稿/驳回」可提交（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  audit:    { label: '审核',   type: 'primary', permission: 'sal:order:audit',   allowFrom: [1],    disabledReason: (s) => `仅「已提交」可审核（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  stockOut: { label: '生成出库单', type: 'success', permission: 'sal:order:stockout', allowFrom: [2], disabledReason: (s) => `仅「已审核」可生成出库单（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  complete: { label: '完成',   type: 'default', permission: 'sal:order:complete', allowFrom: [4],   disabledReason: (s) => `仅「全部发货」可完成（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  reject:   { label: '驳回',   type: 'default', permission: 'sal:order:reject',  allowFrom: [1],    disabledReason: (s) => `仅「已提交」可驳回（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  cancel:   { label: '取消',   type: 'danger',  permission: 'sal:order:cancel',  allowFrom: [0, 1, 2, 6], disabledReason: (s) => `仅草稿/提交/审核/驳回可取消（当前:${STATUS_LABEL[s]?.l ?? s}）` }
}
const EDITABLE_STATUS = new Set<number>([0, 6])
function canEdit(row: SalSalesOrder)  { return EDITABLE_STATUS.has(Number(row.status)) }
function canRemove(row: SalSalesOrder){ return EDITABLE_STATUS.has(Number(row.status)) }
function cannotEditReason(row: SalSalesOrder)   { return `仅「草稿/驳回」可编辑（当前:${STATUS_LABEL[row.status as number]?.l ?? row.status}）` }
function cannotRemoveReason(row: SalSalesOrder) { return `仅「草稿/驳回」可删除（当前:${STATUS_LABEL[row.status as number]?.l ?? row.status}）` }

function stateActionsFor(row: SalSalesOrder): any[] {
  const s = Number(row.status)
  return (['submit', 'audit', 'stockOut', 'complete', 'reject', 'cancel'] as const).map((key) => {
    const d = ACTION_DEF[key]
    const enabled = d.allowFrom.includes(s)
    return {
      key, label: d.label, type: d.type, permission: d.permission,
      disabled: !enabled,
      disabledReason: enabled ? '' : d.disabledReason(s),
      onClick: (async (r: any) => {
        try {
          await hasPermOrThrow(d.permission)
          if (key === 'reject') {
            const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回-' + r.soNo, {
              confirmButtonText: '确定', cancelButtonText: '取消', inputPlaceholder: '选填', inputType: 'text'
            }).catch(() => ({ value: undefined }))
            const resp = (await salOrderApi.reject(r.soId, value)) as any
            if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          } else {
            const resp = await (salOrderApi as any)[key](r.soId) as any
            if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          }
          ElMessage.success(d.label + '成功'); await loadList()
        } catch (e: any) {
          if (e === undefined || e === null) return
          ElMessage.error(e?.message || d.label + '失败')
        }
      }),
      confirm: (key === 'submit' || key === 'cancel' || key === 'stockOut') ? `确认${d.label}当前记录？` : ''
    }
  })
}

const sOpt_soType = [{ v: 'STOCK', l: '备货' }, { v: 'JIT', l: 'JIT' }, { v: 'SAMPLE', l: '样品' }, { v: 'URGENT', l: '加急' }]
const fOpt_soType = sOpt_soType

const query = reactive<SalOrderListQ>({
  pageNum: 1, pageSize: 20, soNo: '', soType: '', customerId: undefined, customerName: '',
  status: undefined as number | undefined, deliveryDate: ''
})
const list = ref<SalSalesOrder[]>([])
const total = ref(0)
const loading = ref(false)
const customers = ref<SalCustomer[]>([])
const users = ref<any[]>([])
const customerFilterQuery = ref('')
const filteredCustomers = computed(() => {
  const q = customerFilterQuery.value.trim().toLowerCase()
  if (!q) return customers.value
  return customers.value.filter(c =>
    (c.customerCode || '').toLowerCase().includes(q) ||
    (c.customerName || '').toLowerCase().includes(q) ||
    (c.shortName || '').toLowerCase().includes(q) ||
    (c.contact || '').toLowerCase().includes(q) ||
    (c.phone || '').toLowerCase().includes(q)
  )
})
function customerFilter(val: string) { customerFilterQuery.value = val }
const creditInfo = ref<any>(null)
const creditLoading = ref(false)

const materialOptions = ref<any[]>([])
async function loadCustomers() {
  try {
    const [resp, u] = await Promise.all([
      salCustomerApi.options() as any,
      userApi.options() as any
    ])
    customers.value = resp?.data || resp || []
    users.value = u?.data || u || []
  } catch (e: any) { ElMessage.error(e?.message || '加载选项失败') }
}
async function loadMaterials() {
  try {
    const m = await materialApi.list({ pageNum: 1, pageSize: 1000 }) as any
    materialOptions.value = m?.rows || []
  } catch { /* ignore */ }
}

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await salOrderApi.list({
      pageNum: query.pageNum, pageSize: query.pageSize, soNo: query.soNo, soType: query.soType,
      customerId: query.customerId, status: query.status, deliveryDate: query.deliveryDate
    })) as any
    list.value = resp?.rows || []
    total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败'); list.value = []; total.value = 0 }
  finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, { soNo: '', soType: '', customerId: undefined, customerName: '', status: undefined, deliveryDate: '', pageNum: 1 })
  loadList(1)
}

function onActionClick(a: any, row: any) {
  if (a.confirm) { ElMessageBox.confirm(a.confirm, '操作确认', { type: 'warning' }).then(() => a.onClick?.(row)).catch(() => {}) }
  else { a.onClick?.(row) }
}

const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => (form.soId ? (readonly.value ? '销售订单详情' : '编辑销售订单') : '新增销售订单'))
const form = reactive<Partial<SalSalesOrder>>({
  soNo: '', soType: '', customerId: undefined as any, customerCode: '', customerName: '',
  contactName: '', contactPhone: '', salesRepId: undefined,
  soDate: '', deliveryDate: '', currency: 'CNY', taxRate: 0.13,
  paymentTerm: '', remark: ''
})
function formDefaults() {
  Object.assign(form, {
    soId: undefined, soNo: '', soType: '', customerId: undefined as any, customerCode: '', customerName: '',
    contactName: '', contactPhone: '', salesRepId: undefined,
    soDate: '', deliveryDate: '', currency: 'CNY', taxRate: 0.13,
    paymentTerm: '', remark: ''
  })
}
function onCustomerChange(val: any) {
  const c = customers.value.find(x => x.customerId === val)
  if (c) {
    form.customerCode = c.customerCode
    form.customerName = c.customerName
    form.paymentTerm = c.paymentTerms || form.paymentTerm
    form.contactName = c.contact || form.contactName || ''
    form.contactPhone = c.phone || form.contactPhone || ''
    form.salesRepId = c.salesRepId || form.salesRepId
    form.deliveryAddress = c.address || form.deliveryAddress || ''
  }
  loadCreditInfo(val)
}
async function loadCreditInfo(customerId: any) {
  creditInfo.value = null
  if (!customerId) return
  creditLoading.value = true
  try {
    const resp = (await salCustomerApi.getCredit(customerId)) as any
    creditInfo.value = resp?.data || resp || null
  } catch { creditInfo.value = null } finally { creditLoading.value = false }
}
const fmtMoney = (n: any) => Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function openDialog(row?: any) {
  formDefaults()
  if (row?.soId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
  loadCreditInfo(form.customerId)
}
async function onView(row: any) {
  formDefaults()
  try {
    const detail = (await salOrderApi.get(row.soId)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
    loadCreditInfo(form.customerId)
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.soId) { await salOrderApi.edit(form as any); ElMessage.success('修改成功') }
    else { await salOrderApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: any) {
  try {
    await hasPermOrThrow('sal:order:remove')
    await salOrderApi.remove(row.soId)
    ElMessage.success('删除成功'); await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

/* ================ 导出 ================ */
const exporting = ref(false)
function saveBlob(blob: Blob, resp: any, fallback: string) {
  const disp = resp?.headers?.['content-disposition'] || resp?.headers?.['Content-Disposition']
  let name = fallback
  if (disp) {
    const m = /filename\*=utf-8''([^;]+)/i.exec(disp) || /filename="?([^";]+)"?/i.exec(disp)
    if (m?.[1]) try { name = decodeURIComponent(m[1]) } catch { /* ignore */ }
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = name
  document.body.appendChild(a); a.click(); document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
async function doExport() {
  exporting.value = true
  try {
    const resp = await salOrderApi.export({ ...query, pageNum: 1, pageSize: 10000 })
    saveBlob(resp.data, resp, `销售订单_${Date.now()}.xlsx`)
    ElMessage.success('导出成功')
  } catch (e: any) { ElMessage.error(e?.message || '导出失败') }
  finally { exporting.value = false }
}

// 明细弹窗
const linesDialog = ref(false)
const linesOwner = ref<SalSalesOrder | null>(null)
const lines = ref<SalSalesOrderLine[]>([])
const linesLoading = ref(false)
/* P0: 记录每行的齐套创建状态(防止重复提交) */
const kittingLine = reactive<Record<number, 'loading' | 'done' | undefined>>({})
/** P0: 订单行→一键齐套 调用后端 fromOrderLine; 完成后跳转到销售齐套页并打开详情 */
async function doCreateKittingFromLine(row: SalSalesOrderLine) {
  if (!row.lineId || !linesOwner.value?.soId) { ElMessage.warning('订单主单/行信息不完整'); return }
  try {
    await ElMessageBox.confirm(
      `确认对订单行【${row.materialCode} ${row.materialName} 数量=${row.orderQty}】执行销售齐套？\n系统会自动匹配【客户+成品】的销售BOM(bomType=3 已启用)并立即执行齐套检查。`,
      '创建销售齐套', { type: 'warning' })
  } catch { return }
  try {
    kittingLine[row.lineId] = 'loading'
    const resp: any = await salKittingApi.fromOrderLine({ soLineId: row.lineId })
    const kit: any = resp?.data ?? resp
    if (!kit?.kittingId) throw new Error(resp?.msg || '创建齐套单失败')
    kittingLine[row.lineId] = 'done'
    // 计算齐套率（避免模板字符串深层嵌套）
    let ratePct = 0
    if (Number(kit.totalLines ?? 0) > 0) {
      const avail = Number(kit.totalAvailableQty ?? 0)
      const req = Math.max(Number(kit.totalRequiredQty ?? 0), 1)
      ratePct = Math.round((avail / req) * 100)
    }
    const statusNames = ['待检查', '部分齐套', '齐套完成', '已生成出库', '已生成请购', '已取消']
    const statusText = (kit.status != null && kit.status >= 0 && kit.status < statusNames.length)
      ? statusNames[kit.status] : String(kit.status)
    const result = await ElMessageBox.confirm(
      `齐套创建成功！单号 ${kit.kittingNo}。\n` +
      `齐套率: ${ratePct}%；` +
      `状态: ${statusText}\n` +
      `缺料总数: ${kit.totalShortageQty ?? 0}\n\n是否跳转到销售齐套页面查看详情？`,
      '齐套成功', { confirmButtonText: '跳转齐套', cancelButtonText: '留在订单', type: 'success' } as any)
    if (result !== undefined) {
      // 通过路由跳转并把 kittingId 带上，让销售齐套页默认打开该单详情
      const win = window.open(`${window.location.pathname}#/sal/kitting?kittingId=${kit.kittingId}`, '_blank')
      if (!win) location.href = `${window.location.pathname}#/sal/kitting?kittingId=${kit.kittingId}`
    }
  } catch (e: any) {
    if (e !== undefined && e !== null && typeof e !== 'string' && !(e instanceof Error)) { /* 用户取消 */ }
    else ElMessage.error(e?.message || '创建齐套失败')
  } finally {
    kittingLine[row.lineId] = undefined
  }
}

const linesTitle = computed(() => `销售订单明细${linesOwner.value?.soNo ? ' - ' + linesOwner.value.soNo : ''}`)
async function onViewLines(row: SalSalesOrder) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    const resp = (await salOrderApi.lines(row.soId!)) as any
    const rows = resp?.rows || []
    lines.value = (rows as SalSalesOrderLine[]).slice().sort((a, b) => (a.lineNo || 0) - (b.lineNo || 0))
  } catch (e: any) { ElMessage.error(e?.message || '加载明细失败'); lines.value = [] }
  finally { linesLoading.value = false }
}

// ---------- 行编辑弹窗 ----------
const lineDialog = ref(false)
const lineSubmitLoading = ref(false)
const lineFormRef = ref<FormInstance | null>(null)
const lineEditingId = ref<number | null>(null)
const lineForm = reactive<Partial<SalSalesOrderLine>>({
  soId: undefined, materialId: 1, materialCode: '', materialName: '', specification: '', unit: 'PCS',
  orderQty: 0, shippedQty: 0, returnedQty: 0, unitPrice: 0, discountRate: 0, taxRate: 0, lineAmount: 0,
  taxAmount: 0, requiredDate: '', remark: ''
})
const lineRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  orderQty:   [{ required: true, validator: (_r: any, v: any, cb: any) => (v != null && Number(v) > 0) ? cb() : cb(new Error('订单数量需大于0')), trigger: 'change' }],
} as any
const lineDialogTitle = computed(() => lineEditingId.value ? '编辑订单行' : '新增订单行')
function resetLineForm() {
  Object.assign(lineForm, {
    soId: linesOwner.value?.soId ?? undefined, materialId: 1, materialCode: '', materialName: '',
    specification: '', unit: 'PCS', orderQty: 0, shippedQty: 0, returnedQty: 0, unitPrice: 0,
    discountRate: 0, taxRate: 0, lineAmount: 0, taxAmount: 0,
    requiredDate: linesOwner.value?.deliveryDate ?? '', remark: ''
  })
  lineEditingId.value = null
}
function openLineDialog(row?: SalSalesOrderLine) {
  if (!linesOwner.value?.soId) { ElMessage.warning('缺少主单ID'); return }
  if (row) { Object.assign(lineForm, { ...row }); lineEditingId.value = (row.lineId as number) ?? null }
  else resetLineForm()
  lineDialog.value = true
}
function recalcLineAmount() {
  const q = Number(lineForm.orderQty ?? 0)
  const p = Number(lineForm.unitPrice ?? 0)
  const d = Number(lineForm.discountRate ?? 0)
  const tr = Number(lineForm.taxRate ?? 0)
  const line = q * p * (1 - d)
  lineForm.lineAmount = Number(line.toFixed(2))
  lineForm.taxAmount = Number((line * tr).toFixed(2))
}
// SO下单带价: 数量变化重算 + 按客户+物料+数量匹配生效客户价(阶梯/is_default优先)
function onQtyChange() {
  recalcLineAmount()
  onLineMatchPrice()
}
function onLineMaterialChange(val: any) {
  const m = materialOptions.value.find(x => x.materialId === val)
  if (m) {
    lineForm.materialCode = m.materialCode
    lineForm.materialName = m.materialName
    lineForm.specification = m.specification
    if (m.unit) lineForm.unit = m.unit
  }
  onLineMatchPrice()
}
async function onLineMatchPrice() {
  const cid = linesOwner.value?.customerId
  const mid = Number(lineForm.materialId)
  const qty = Number(lineForm.orderQty ?? 0)
  if (!cid || !mid) return
  try {
    const resp = await salPriceApi.match(cid, mid, qty) as any
    const p = resp?.data
    if (p && p.priceId) {
      // 报价含税单价 → 订单行未税单价
      lineForm.unitPrice = Number(p.untaxedUnitPrice ?? p.unitPrice)
      lineForm.taxRate = Number(p.taxRate)
      if (p.materialCode) lineForm.materialCode = p.materialCode
      if (p.materialName) lineForm.materialName = p.materialName
      if (p.specification) lineForm.specification = p.specification
      if (p.unit) lineForm.unit = p.unit
      recalcLineAmount()
      ElMessage.success(`已带出客户专属价 ¥${Number(lineForm.unitPrice).toFixed(4)}（含税¥${Number(p.unitPrice).toFixed(4)}，${p.validFrom}~${p.validTo}）`)
    } else {
      ElMessage.info('未匹配到生效客户价，请手动填写单价')
    }
  } catch (e) { /* 静默：不阻断明细录入 */ }
}
async function onLineSubmit() {
  lineSubmitLoading.value = true
  try {
    await lineFormRef.value?.validate()
    recalcLineAmount()
    const payload = { ...lineForm, soId: linesOwner.value!.soId! }
    if (lineEditingId.value) {
      await salOrderApi.editLine({ ...payload, lineId: lineEditingId.value } as any)
      ElMessage.success('修改行成功')
    } else {
      await salOrderApi.addLine(payload as any)
      ElMessage.success('新增行成功')
    }
    lineDialog.value = false
    await onViewLines(linesOwner.value!)
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { lineSubmitLoading.value = false }
}
async function onRemoveLine(row: SalSalesOrderLine) {
  try { await ElMessageBox.confirm(`确定删除行号 ${row.lineNo} ?`, '提示', { type: 'warning' }) } catch { return }
  try { await salOrderApi.removeLine(row.lineId as number); ElMessage.success('删除成功'); await onViewLines(linesOwner.value!) }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(async () => {
  await loadCustomers()
  loadMaterials()
  await loadList()
})
</script>

<style scoped>
.page-sal-order { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.lines-header { margin-bottom: 4px; }
.lines-footer { margin-top: 8px; display: flex; justify-content: flex-end; }
.lines-total { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
