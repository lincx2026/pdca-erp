<template>
  <div class="page-sal-price">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="客户">
        <el-select v-model="query.customerId" placeholder="全部客户" clearable filterable style="width:220px">
          <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
        </el-select>
      </el-form-item>
      <el-form-item label="物料">
        <el-select v-model="query.materialId" placeholder="全部物料" clearable filterable style="width:260px">
          <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
            <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
            <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="币种"><el-input v-model="query.currency" placeholder="如 CNY" clearable style="width:110px" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:130px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label=""><el-checkbox v-model="onlyValid">仅看生效中</el-checkbox></el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'sal:price:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增报价</el-button>
      <el-button type="success" v-hasPermi="'sal:price:add'" @click="openBatchDialog()"><el-icon><Plus /></el-icon> 批量新增</el-button>
      <el-button v-hasPermi="'sal:price:convertOrder'" type="warning" :disabled="selConvertable.length === 0"
         @click="openConvertDialog">
        <el-icon><Goods /></el-icon>
        转销售订单（{{ selConvertable.length }}）
        <template v-if="selProblemMsg">
          <el-tooltip :content="selProblemMsg" placement="top" :disabled="!selProblemMsg">
            <el-tag size="small" type="info" effect="plain" style="margin-left:4px">⚠ 仅部分可转</el-tag>
          </el-tooltip>
        </template>
      </el-button>
      <el-button v-hasPermi="'sal:price:export'" :loading="exporting" @click="doExport"><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border
              @selection-change="onSelectionChange" ref="priceTableRef">
      <el-table-column type="selection" width="48" align="center"
        :selectable="(r:any) => (r.status === 2 && !r.orderId)"
        reserve-selection />
      <el-table-column prop="quoteSource" label="报价单号" width="150" />
      <el-table-column prop="customerCode" label="客户编码" width="110" />
      <el-table-column prop="customerName" label="客户名称" width="160" show-overflow-tooltip />
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" width="160" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格" width="140" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="60" />
      <el-table-column prop="currency" label="币种" width="60" />
      <el-table-column prop="unitPrice" label="含税单价" width="110"><template #default="{ row }">{{ row.unitPrice == null ? '-' : Number(row.unitPrice).toFixed(4) }}</template></el-table-column>
      <el-table-column prop="taxRate" label="税率%" width="70"><template #default="{ row }">{{ row.taxRate == null ? '-' : Number(row.taxRate) }}</template></el-table-column>
      <el-table-column prop="minQty" label="起订量" width="90"><template #default="{ row }">{{ row.minQty == null ? '-' : Number(row.minQty) }}</template></el-table-column>
      <el-table-column label="有效期" width="190"><template #default="{ row }">{{ row.validFrom }} ~ {{ row.validTo }}</template></el-table-column>
      <el-table-column prop="isDefault" label="默认" width="70">
        <template #default="{ row }"><el-tag v-if="row.isDefault === 1" type="warning">默认</el-tag><span v-else>-</span></template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.t || ''">{{ statusMap[row.status]?.l || String(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderId" label="转单状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.status === 5 || row.orderId" type="success" effect="plain">已转订单</el-tag>
          <span v-else-if="row.status === 2" style="color:#c0c4cc">待转</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="'sal:price:list'" size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'sal:price:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm v-if="canEdit(row)" title="确认删除当前记录？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'sal:price:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <el-button v-if="canSubmit(row)" v-hasPermi="'sal:price:submit'" size="small" link type="primary" @click="onSubmit(row)">提交</el-button>
          <el-button v-if="row.status === 1" v-hasPermi="'sal:price:audit'" size="small" link type="success" @click="onAudit(row)">审核</el-button>
          <el-button v-if="row.status === 1" v-hasPermi="'sal:price:reject'" size="small" link type="warning" @click="onReject(row)">驳回</el-button>
          <el-button v-if="row.status === 2" v-hasPermi="'sal:price:edit'" size="small" link @click="onDisable(row)">停用</el-button>
          <el-button v-if="row.status === 2 && row.isDefault !== 1" v-hasPermi="'sal:price:default'" size="small" link type="warning" @click="onSetDefault(row)">设默认</el-button>
          <el-button v-if="row.status === 3" v-hasPermi="'sal:price:edit'" size="small" link type="success" @click="onEnable(row)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-size="query.pageSize" :current-page="query.pageNum" :page-sizes="[10,20,50,100]" @current-change="(p)=>{query.pageNum=p;loadList()}" @size-change="(s)=>{query.pageSize=s;loadList(1)}" style="margin-top:12px;justify-content:flex-end;display:flex" />

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="720px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="form.customerId" placeholder="输入编码/名称/简称搜索" filterable style="width:100%" @change="onCustomerChange">
              <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName} ${c.shortName || ''} ${c.contact || ''} ${c.phone || ''}`" :value="c.customerId">
                <span style="float:left">{{ c.customerCode }} {{ c.customerName }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ c.contact }} {{ c.phone }}</span>
              </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称"><el-input v-model="form.customerName" disabled placeholder="选择客户后自动带出" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料" prop="materialId">
              <el-select v-model="form.materialId" placeholder="输入编码/名称/规格搜索" filterable style="width:100%" @change="onMaterialChange">
                <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                  <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称"><el-input v-model="form.materialName" disabled placeholder="选择物料后自动带出" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency"><el-input v-model="form.currency" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="含税单价" prop="unitPrice"><el-input-number v-model="form.unitPrice" :min="0" :precision="4" :step="0.01" controls-position="right" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="税率%" prop="taxRate"><el-input-number v-model="form.taxRate" :min="0" :precision="2" :step="1" controls-position="right" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="起订量" prop="minQty"><el-input-number v-model="form.minQty" :min="0" :precision="2" controls-position="right" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="生效日期" prop="validFrom"><el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失效日期" prop="validTo"><el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报价单号"><el-input v-model="form.quoteSource" disabled placeholder="保存后自动生成" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="viewDialog.visible" title="报价详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="客户">{{ form.customerCode }} {{ form.customerName }}</el-descriptions-item>
        <el-descriptions-item label="物料">{{ form.materialCode }} {{ form.materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格/单位">{{ form.specification }} / {{ form.unit }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ form.currency }}</el-descriptions-item>
        <el-descriptions-item label="含税单价">{{ form.unitPrice }}</el-descriptions-item>
        <el-descriptions-item label="税率%">{{ form.taxRate }}</el-descriptions-item>
        <el-descriptions-item label="起订量">{{ form.minQty }}</el-descriptions-item>
        <el-descriptions-item label="是否默认">{{ form.isDefault === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ form.validFrom }} ~ {{ form.validTo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusMap[form.status]?.l || form.status }}</el-descriptions-item>
        <el-descriptions-item label="报价单号">{{ form.quoteSource }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ form.auditBy }}</el-descriptions-item>
        <el-descriptions-item label="驳回理由" :span="2">{{ form.rejectReason }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ form.remark }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 驳回理由对话框 -->
    <el-dialog v-model="rejectDialog.visible" title="驳回报价" width="480px">
      <el-input v-model="rejectDialog.reason" type="textarea" :rows="3" placeholder="请输入驳回理由" />
      <template #footer>
        <el-button @click="rejectDialog.visible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="onRejectConfirm">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 批量新增报价 对话框 -->
    <el-dialog v-model="batchDialog.visible" title="批量新增报价" width="1080px" top="5vh" destroy-on-close>
      <el-form :inline="true" label-width="80px" class="batch-form" @submit.prevent>
        <el-form-item label="客户" required>
          <el-select v-model="batchDialog.customerId" placeholder="输入编码/名称搜索" filterable style="width:260px">
            <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
          </el-select>
        </el-form-item>
        <el-form-item label="币种"><el-input v-model="batchDialog.currency" style="width:80px" /></el-form-item>
        <el-form-item label="生效日期"><el-date-picker v-model="batchDialog.validFrom" type="date" value-format="YYYY-MM-DD" style="width:140px" /></el-form-item>
        <el-form-item label="失效日期"><el-date-picker v-model="batchDialog.validTo" type="date" value-format="YYYY-MM-DD" style="width:140px" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="batchDialog.remark" style="width:200px" /></el-form-item>
      </el-form>
      <div style="margin-bottom:8px">
        <el-button type="primary" size="small" @click="addBatchRow"><el-icon><Plus /></el-icon> 添加行</el-button>
        <span style="margin-left:12px;color:#999">共 {{ batchDialog.items.length }} 行</span>
      </div>
      <el-table :data="batchDialog.items" border max-height="420" size="small">
        <el-table-column label="行号" type="index" width="50" align="center" />
        <el-table-column label="物料" min-width="240">
          <template #default="{ row, $index }">
            <el-select v-model="row.materialId" placeholder="输入编码/名称/规格搜索" filterable style="width:100%" @change="onBatchMaterialChange($index)">
              <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="含税单价" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="4" :step="0.01" controls-position="right" style="width:110px" />
          </template>
        </el-table-column>
        <el-table-column label="税率%" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.taxRate" :min="0" :precision="2" :step="1" controls-position="right" style="width:80px" />
          </template>
        </el-table-column>
        <el-table-column label="起订量" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.minQty" :min="0" :precision="2" controls-position="right" style="width:80px" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" @click="batchDialog.items.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="batchDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="onBatchSave">保存({{ batchDialog.items.length }}条)</el-button>
      </template>
    </el-dialog>

    <!-- P1: 批量报价→转销售订单 对话框 -->
    <el-dialog v-model="convertDialog.visible" title="批量报价条目→生成销售订单" width="1080px" top="5vh" destroy-on-close>
      <el-alert v-if="convertDialog.customerName" type="info" :closable="false" show-icon
        :title="`客户: ${convertDialog.customerCode || ''} ${convertDialog.customerName}  共 ${convertDialog.items.length} 条条目`" />
      <el-form :model="convertDialog.extra" label-width="100px" style="margin-top:14px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="订单日期"><el-date-picker v-model="convertDialog.extra.soDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="默认今天" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="统一交付日期"><el-date-picker v-model="convertDialog.extra.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="未填写默认15天后" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="付款条件"><el-input v-model="convertDialog.extra.paymentTerm" placeholder="如: 预付30%,发货前付清" maxlength="100" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销售员">
              <el-select v-model="convertDialog.extra.salesRepId" placeholder="选择销售员" clearable filterable style="width:100%">
                <el-option v-for="u in users" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="发货仓库">
              <el-select v-model="convertDialog.extra.warehouseId" placeholder="默认成品仓" clearable filterable style="width:100%">
                <el-option v-for="w in warehouses" :key="w.warehouseId" :label="`${w.warehouseCode || ''} ${w.warehouseName || ''}`" :value="w.warehouseId">
                  <span style="float:left">{{ w.warehouseCode }} {{ w.warehouseName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ w.warehouseType || '' }} {{ w.address || '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="convertDialog.extra.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-table :data="convertDialog.items" border size="small" max-height="420">
        <el-table-column label="行" width="56" align="center">{{ $index + 1 }}</el-table-column>
        <el-table-column label="物料编码" width="130" prop="materialCode" />
        <el-table-column label="物料名称" min-width="180" prop="materialName" show-overflow-tooltip />
        <el-table-column label="规格" width="130" prop="specification" show-overflow-tooltip />
        <el-table-column label="含税单价" width="110" align="right">
          <template #default="{ row }">{{ row.unitPrice == null ? '-' : Number(row.unitPrice).toFixed(4) }} {{ row.currency }}</template>
        </el-table-column>
        <el-table-column label="税率%" width="80" align="right">
          <template #default="{ row }">{{ row.taxRate == null ? '-' : Number(row.taxRate) }}</template>
        </el-table-column>
        <el-table-column label="数量*" width="160" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0" :precision="4" :step="1" controls-position="right" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="单行交付日期" width="160" align="center">
          <template #default="{ row }">
            <el-date-picker v-model="row.deliveryDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" placeholder="可选/或用统一" />
          </template>
        </el-table-column>
        <el-table-column label="行金额(含税)" width="130" align="right">
          <template #default="{ row }">¥ {{ ((Number(row.unitPrice||0) * Number(row.qty||0)).toFixed(2)) }}</template>
        </el-table-column>
        <el-table-column label="移除" width="60" align="center">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" @click="convertDialog.items.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider>订单金额预览</el-divider>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="未税合计">¥ {{ convertSummary.undisc }}</el-descriptions-item>
        <el-descriptions-item label="税额">¥ {{ convertSummary.tax }}</el-descriptions-item>
        <el-descriptions-item label="价税合计"><b style="color:#F56C6C">¥ {{ convertSummary.total }}</b></el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="convertDialog.visible = false">取消</el-button>
        <el-button type="warning" :loading="convertDialog.submitting" :disabled="convertDialog.items.length === 0" @click="doConvertOrder">
          <el-icon><Goods /></el-icon>&nbsp;生成销售订单
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus, Download, Goods } from '@element-plus/icons-vue'
import { salPriceApi, salCustomerApi, materialApi, salOrderApi, warehouseApi, userApi } from '@/api'

const statusOpts = [
  { v: 0, l: '草稿' }, { v: 1, l: '待审核' }, { v: 2, l: '已审核' }, { v: 3, l: '已停用' }, { v: 4, l: '已驳回' }, { v: 5, l: '已转订单' }
]
const statusMap: Record<number, { l: string; t: any }> = {
  0: { l: '草稿', t: 'info' }, 1: { l: '待审核', t: 'warning' }, 2: { l: '已审核', t: 'success' },
  3: { l: '已停用', t: 'danger' }, 4: { l: '已驳回', t: 'danger' }, 5: { l: '已转订单', t: 'success' }
}

const customers = ref<any[]>([])
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
const materials = ref<any[]>([])
const warehouses = ref<any[]>([])
const users = ref<any[]>([])

const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const batchSaving = ref(false)

const query = reactive({ pageNum: 1, pageSize: 20, customerId: undefined, materialId: undefined, currency: '', status: undefined, customerName: '', onlyValid: 0 } as any)
const onlyValid = ref(false)

const dialog = reactive({ visible: false, title: '' })
const viewDialog = reactive({ visible: false })
const rejectDialog = reactive({ visible: false, id: 0, reason: '' })

// 批量新增报价
const batchDialog = reactive({
  visible: false,
  customerId: undefined as number | undefined,
  currency: 'CNY',
  validFrom: today(),
  validTo: today(365),
  remark: '',
  items: [] as Array<{ materialId: number | undefined; unitPrice: number; taxRate: number; minQty: number }>
})
const formRef = ref()
const form = reactive({} as any)
const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  currency: [{ required: true, message: '请输入币种', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  validFrom: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
  validTo: [{ required: true, message: '请选择失效日期', trigger: 'change' }]
}

const canEdit = (r: any) => r.status === 0 || r.status === 4
const canSubmit = (r: any) => r.status === 0 || r.status === 4

async function loadList(page = 0) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const res = await salPriceApi.list({ ...query, onlyValid: onlyValid.value ? 1 : 0 })
    list.value = res?.rows || []
    total.value = res?.total || 0
  } catch (e: any) { ElMessage.error(e?.message || '查询失败') }
  finally { loading.value = false }
}

function resetQuery() {
  Object.assign(query, { pageNum: 1, customerId: undefined, materialId: undefined, currency: '', status: undefined, customerName: '', onlyValid: 0 })
  onlyValid.value = false
  loadList(1)
}

function resetForm() {
  Object.keys(form).forEach(k => delete form[k])
}

function openDialog(row?: any) {
  resetForm()
  if (row) {
    Object.assign(form, JSON.parse(JSON.stringify(row)))
    dialog.title = '编辑报价'
  } else {
    Object.assign(form, { currency: 'CNY', taxRate: 13, minQty: 0, isDefault: 0, status: 0, validFrom: today(), validTo: today(365) })
    dialog.title = '新增报价'
  }
  dialog.visible = true
}

function today(offset = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d.toISOString().slice(0, 10)
}

function onCustomerChange(v: any) {
  const c = customers.value.find(x => x.customerId === v)
  if (c) {
    form.customerCode = c.customerCode
    form.customerName = c.customerName
  }
}
function onMaterialChange(v: any) {
  const m = materials.value.find(x => x.materialId === v)
  if (m) { form.materialCode = m.materialCode; form.materialName = m.materialName; form.specification = m.specification; form.unit = m.unit }
}

async function onSave() {
  await formRef.value?.validate()
  if (form.validFrom > form.validTo) { ElMessage.warning('生效日期不能晚于失效日期'); return }
  saving.value = true
  try {
    if (form.priceId) { await salPriceApi.edit(form); ElMessage.success('修改成功') }
    else { await salPriceApi.add(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
  finally { saving.value = false }
}

// ============ 批量新增 ============
function openBatchDialog() {
  batchDialog.customerId = undefined
  batchDialog.currency = 'CNY'
  batchDialog.validFrom = today()
  batchDialog.validTo = today(365)
  batchDialog.remark = ''
  batchDialog.items = [{ materialId: undefined, unitPrice: 0, taxRate: 13, minQty: 0 }]
  batchDialog.visible = true
}
function addBatchRow() {
  batchDialog.items.push({ materialId: undefined, unitPrice: 0, taxRate: 13, minQty: 0 })
}
function onBatchMaterialChange(idx: number) {
  // 物料选择变化时无需额外处理, 后端会回填名称
}
async function onBatchSave() {
  if (!batchDialog.customerId) { ElMessage.warning('请选择客户'); return }
  if (!batchDialog.items.length) { ElMessage.warning('请至少添加一行'); return }
  if (batchDialog.validFrom > batchDialog.validTo) { ElMessage.warning('生效日期不能晚于失效日期'); return }
  for (let i = 0; i < batchDialog.items.length; i++) {
    const r = batchDialog.items[i]
    if (!r.materialId) { ElMessage.warning(`第${i + 1}行: 请选择物料`); return }
    if (!r.unitPrice || r.unitPrice <= 0) { ElMessage.warning(`第${i + 1}行: 含税单价必须>0`); return }
  }
  batchSaving.value = true
  try {
    const items = batchDialog.items.map(r => ({
      customerId: batchDialog.customerId,
      currency: batchDialog.currency,
      validFrom: batchDialog.validFrom,
      validTo: batchDialog.validTo,
      remark: batchDialog.remark,
      materialId: r.materialId,
      unitPrice: r.unitPrice,
      taxRate: r.taxRate,
      minQty: r.minQty
    }))
    const res = await salPriceApi.batch(items)
    ElMessage.success(`批量新增成功, 共 ${res?.length || items.length} 条`)
    batchDialog.visible = false
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '批量保存失败') }
  finally { batchSaving.value = false }
}

function onView(row: any) {
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  viewDialog.visible = true
}

async function onRemove(row: any) {
  try { await salPriceApi.remove(row.priceId); ElMessage.success('删除成功'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

async function onSubmit(row: any) {
  try { await salPriceApi.submit(row.priceId); ElMessage.success('已提交审核'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '提交失败') }
}
async function onAudit(row: any) {
  await ElMessageBox.confirm('确认审核通过当前报价？', '审核', { type: 'warning' })
  try { await salPriceApi.audit(row.priceId); ElMessage.success('审核通过'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
function onReject(row: any) {
  rejectDialog.id = row.priceId
  rejectDialog.reason = ''
  rejectDialog.visible = true
}
async function onRejectConfirm() {
  if (!rejectDialog.reason.trim()) { ElMessage.warning('请输入驳回理由'); return }
  saving.value = true
  try { await salPriceApi.reject(rejectDialog.id, rejectDialog.reason); ElMessage.success('已驳回'); rejectDialog.visible = false; loadList() }
  catch (e: any) { ElMessage.error(e?.message || '驳回失败') }
  finally { saving.value = false }
}
async function onDisable(row: any) {
  try { await salPriceApi.disable(row.priceId); ElMessage.success('已停用'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '停用失败') }
}
async function onEnable(row: any) {
  try { await salPriceApi.enable(row.priceId); ElMessage.success('已启用'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '启用失败') }
}
async function onSetDefault(row: any) {
  try { await salPriceApi.setDefault(row.priceId); ElMessage.success('已设为默认'); loadList() }
  catch (e: any) { ElMessage.error(e?.message || '设置失败') }
}

async function doExport() {
  exporting.value = true
  try {
    const resp = await salPriceApi.export({ ...query, onlyValid: onlyValid.value ? 1 : 0, pageNum: 1, pageSize: 10000 } as any)
    saveBlob(resp.data, resp, '销售报价_' + Date.now() + '.xlsx')
  } catch (e: any) { ElMessage.error(e?.message || '导出失败') }
  finally { exporting.value = false }
}
function saveBlob(data: any, resp: any, filename: string) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (resp.headers && resp.headers['content-disposition'] ? decodeURIComponent(resp.headers['content-disposition'].split('filename*=utf-8\'\'')[1] || filename) : filename)
  a.click()
  window.URL.revokeObjectURL(url)
}

async function loadOptions() {
  try {
    const [c, m, w, u] = await Promise.all([
      salCustomerApi.options() as any,
      materialApi.list({ pageNum: 1, pageSize: 1000 } as any),
      warehouseApi.options() as any,
      userApi.options() as any
    ])
    customers.value = c?.data || c || []
    materials.value = (m as any)?.rows || []
    warehouses.value = w?.data || w || []
    users.value = u?.data || u || []
  } catch (e) { console.error('load options failed', e) }
}

onMounted(() => { loadOptions(); loadList(1) })

// ================== P1: 报价批量 → 销售订单 ==================
const priceTableRef = ref()
const selectedRows = ref<any[]>([])
/** 仅: 同客户 + status===2 + orderId 空 的选中条目 */
const selConvertable = computed<any[]>(() => {
  const ok = selectedRows.value.filter(r => r && r.status === 2 && !r.orderId && typeof r.customerId === 'number')
  if (ok.length === 0) return []
  const cid = ok[0].customerId
  return ok.filter(r => r.customerId === cid)
})
const selProblemMsg = computed<string>(() => {
  const S = selectedRows.value
  if (S.length === 0) return ''
  const nonOK = S.length - selConvertable.value.length
  if (nonOK <= 0) {
    const cidSet = new Set(S.filter(r => r && r.status === 2 && !r.orderId).map(r => r.customerId))
    if (cidSet.size > 1) return '选中条目分属不同客户；仅同一客户的可一起转订单'
    return ''
  }
  return `选中 ${S.length} 条，其中 ${nonOK} 条不能转单(非生效状态/已绑定订单/数据不全)。`
})
function onSelectionChange(rows: any[]) { selectedRows.value = rows }

/** 转单对话框状态 */
const convertDialog = reactive({
  visible: false,
  submitting: false,
  customerId: undefined as number | undefined,
  customerCode: '' as string,
  customerName: '' as string,
  items: [] as Array<{
    priceId: number; qty: number; deliveryDate?: string;
    materialCode?: string; materialName?: string; specification?: string; unitPrice?: number; taxRate?: number; currency?: string; minQty?: number
  }>,
  extra: { soDate: '', deliveryDate: '', warehouseId: undefined as number | undefined, salesRepId: undefined as number | undefined, paymentTerm: '', remark: '' }
})
/** 预览金额 */
const convertSummary = computed(() => {
  let undisc = 0, tax = 0, totalAll = 0
  for (const it of convertDialog.items) {
    const taxInclPrice = Number(it.unitPrice || 0)
    const qty = Number(it.qty || 0)
    const rate = Number(it.taxRate == null || it.taxRate === '' ? 13 : it.taxRate)
    // 含税单价: total = price * qty, undisc = total / (1 + rate/100), tax = total - undisc
    const lineTotal = taxInclPrice * qty
    const lineUndisc = lineTotal / (1 + rate / 100)
    const lineTax = lineTotal - lineUndisc
    undisc += lineUndisc
    tax += lineTax
    totalAll += lineTotal
  }
  return {
    undisc: undisc.toFixed(2),
    tax: tax.toFixed(2),
    total: totalAll.toFixed(2)
  }
})
function openConvertDialog() {
  if (selConvertable.value.length === 0) { ElMessage.warning('请先勾选 生效(status=已审核) 且 未转订单 的条目（同一客户）'); return }
  const first = selConvertable.value[0]
  convertDialog.customerId = first.customerId
  convertDialog.customerCode = first.customerCode || ''
  convertDialog.customerName = first.customerName || ''
  convertDialog.items = selConvertable.value.map(r => ({
    priceId: r.priceId,
    qty: Number(r.minQty || 1) > 0 ? Number(r.minQty) : 1,
    deliveryDate: undefined,
    materialCode: r.materialCode,
    materialName: r.materialName,
    specification: r.specification,
    unitPrice: r.unitPrice,
    taxRate: r.taxRate,
    currency: r.currency,
    minQty: r.minQty
  }))
  convertDialog.extra = { soDate: '', deliveryDate: '', warehouseId: undefined, salesRepId: undefined, paymentTerm: '', remark: '' }
  convertDialog.visible = true
}
async function doConvertOrder() {
  if (!convertDialog.customerId) { ElMessage.warning('缺少客户ID'); return }
  const items = convertDialog.items.filter(x => Number(x.qty) > 0)
  if (items.length === 0) { ElMessage.warning('请至少填写1条数量'); return }
  for (const it of items) {
    if (typeof it.priceId !== 'number') { ElMessage.warning('条目缺少priceId'); return }
  }
  try {
    await ElMessageBox.confirm(
      `确认将 ${items.length} 条报价条目转成1张销售订单？\n订单未税合计: ¥${convertSummary.value.undisc}，价税合计: ¥${convertSummary.value.total}`,
      '转销售订单确认', { type: 'warning' })
  } catch { return }
  try {
    convertDialog.submitting = true
    const resp: any = await salPriceApi.convertOrder({
      customerId: convertDialog.customerId,
      items: items.map(i => ({ priceId: i.priceId, qty: Number(i.qty), deliveryDate: i.deliveryDate })),
      extra: {
        soDate: convertDialog.extra.soDate || undefined,
        deliveryDate: convertDialog.extra.deliveryDate || undefined,
        warehouseId: convertDialog.extra.warehouseId,
        salesRepId: convertDialog.extra.salesRepId,
        paymentTerm: convertDialog.extra.paymentTerm || undefined,
        remark: convertDialog.extra.remark || undefined
      }
    })
    const soId: number = (resp as any)?.data ?? (resp as any)
    if (!soId) throw new Error((resp as any)?.msg || '转单失败')
    convertDialog.visible = false
    ElMessage.success('生成销售订单成功 soId=' + soId)
    try { await loadList() } catch { /* ignore */ }
    try {
      const ans = await ElMessageBox.confirm(`是否跳转到此销售订单详情？(soId=${soId})`, '转单成功',
        { confirmButtonText: '跳订单', cancelButtonText: '留在报价', type: 'success' } as any)
      if (ans !== undefined) {
        // 跳到订单页，带soId参数(订单页本身如果没接受则至少进入订单页自己找)
        const href = `${location.pathname}#/sal/order?soId=${soId}`
        const w = window.open(href, '_blank')
        if (!w) location.href = href
      }
    } catch { /* 用户取消跳转 */ }
  } catch (e: any) {
    if (e !== undefined && e !== null && typeof e !== 'string' && !(e instanceof Error)) { /* ElMessageBox取消 */ }
    else ElMessage.error(e?.message || '转单失败')
  } finally {
    convertDialog.submitting = false
  }
}
</script>

<style scoped>
.page-sal-price { padding: 12px; }
.search-form { margin-bottom: 8px; }
.batch-form :deep(.el-form-item) { margin-bottom: 12px; vertical-align: middle; }
.batch-form :deep(.el-form-item__label) { text-align: right; }
.table-toolbar { margin-bottom: 8px; }
</style>
