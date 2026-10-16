<template>
  <div class="page-scm-purchase">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="订单号"><el-input v-model="query.poNo" placeholder="订单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="供应商"><el-input v-model="query.supplierName" placeholder="供应商" clearable style="width:180px" /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.poType" placeholder="全部" clearable style="width:160px">
          <el-option v-for="o in sOpt_poType" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="采购大类">
        <el-select v-model="query.bizCategory" placeholder="全部" clearable style="width:160px">
          <el-option v-for="o in BIZ_CATEGORIES" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
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
      <el-button type="primary" v-hasPermi="'scm:purchase:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增采购订单</el-button>
      <el-button v-hasPermi="'scm:price:convert'" type="success" :disabled="selection.length === 0" @click="batchConvertPo">
        <el-icon><Promotion /></el-icon> 批量转PO
        <span v-if="selection.length" class="badge">{{ selection.length }}</span>
      </el-button>
      <el-button v-hasPermi="'scm:purchase:stockIn'" type="warning" :disabled="!curRow || !canStockIn(curRow)"
                 @click="openStockIn(curRow)">
        <el-icon><Tickets /></el-icon> 生成入库单
        <span v-if="curRow">（对 {{ curRow.poNo }}）</span>
      </el-button>
      <el-button type="primary" v-hasPermi="'scm:purchase:createDelivery'"
                 :disabled="!curRow || (curRow.status != null && Number(curRow.status) < 2)"
                 @click="createDelivery(curRow)">
        <el-icon><Van /></el-icon> 生成到货通知单
      </el-button>
      <el-button type="warning" v-hasPermi="'scm:purchase:qcJudge'"
                 :disabled="!canQcJudge(curRow)"
                 @click="openQcJudge(curRow)">
        <el-icon><CircleCheck /></el-icon> 到货质检(IQC)
      </el-button>
      <el-button><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border
              highlight-current-row
              @selection-change="onSelectionChange"
              @current-change="(r:any) => curRow = r || null"
              :row-class-name="({row}:any)=> highlightId && String(row.poId) === String(highlightId) ? 'row-highlight' : ''">
      <el-table-column type="selection" width="44" :selectable="canSelectConvert" />
      <el-table-column prop="poNo" label="订单号" width="160" />
      <el-table-column prop="poType" label="类型" width="90" />
      <el-table-column prop="bizCategory" label="采购大类" width="110">
        <template #default="{ row }">{{ bizLabel(row.bizCategory) }}</template>
      </el-table-column>
      <el-table-column prop="supplierName" label="供应商" width="180" show-overflow-tooltip />
      <el-table-column prop="buyerName" label="采购员" width="100" />
      <el-table-column prop="poDate" label="下单日期" width="110" />
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
      <el-table-column label="操作" width="640" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onViewLines(row)">明细</el-button>
          <el-button v-hasPermi="'scm:purchase:list'" size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'scm:purchase:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-else size="small" link type="primary" disabled :title="cannotEditReason(row)">编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除当前记录？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'scm:purchase:remove'" size="small" link type="danger">删除</el-button></template>
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
          <el-tooltip v-if="!canStockIn(row)" :content="cannotStockInReason(row)" placement="top">
            <el-button size="small" link type="warning" disabled>生成入库单</el-button>
          </el-tooltip>
          <el-button v-else v-hasPermi="'scm:purchase:stockIn'" size="small" link type="warning" @click="openStockIn(row)">
            生成入库单
          </el-button>
          <el-button size="small" link type="info" @click="openPrint(row)">打印</el-button>
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

    <!-- 主单 新增/编辑/详情 -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
        <el-form-item label="订单号" required prop="poNo"><el-input v-model="form.poNo" placeholder="请输入订单号" /></el-form-item>
        <el-form-item label="类型" prop="poType">
          <el-select v-model="form.poType" placeholder="请选择" style="width:100%">
            <el-option v-for="o in fOpt_poType" :key="o.v" :label="o.l" :value="o.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="采购大类" required prop="bizCategory">
          <el-select v-model="form.bizCategory" placeholder="请选择" style="width:100%">
            <el-option v-for="o in BIZ_CATEGORIES" :key="o.v" :label="o.l" :value="o.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" required prop="supplierId">
          <el-select v-model="form.supplierId" filterable placeholder="选择供应商" style="width:100%">
            <el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
              <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ s.contact }} {{ s.phone }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="供应商名称" prop="supplierName"><el-input v-model="form.supplierName" /></el-form-item>
        <el-form-item label="联系人" prop="contactName"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话" prop="contactPhone"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="采购员" prop="buyerId">
          <el-select v-model="form.buyerId" filterable placeholder="选择采购员" style="width:100%">
            <el-option v-for="u in users" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="下单日期" prop="poDate"><el-date-picker v-model="form.poDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="交付日期" prop="deliveryDate"><el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="币种" prop="currency"><el-input v-model="form.currency" /></el-form-item>
        <el-form-item label="税率(%)" prop="taxRate"><el-input-number v-model="form.taxRate" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="折扣率(%)" prop="discountRate"><el-input-number v-model="form.discountRate" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="总金额" prop="totalAmount"><el-input-number v-model="form.totalAmount" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="付款条件" prop="paymentTerm"><el-input v-model="form.paymentTerm" /></el-form-item>
        <el-form-item label="交货条件" prop="deliveryTerm"><el-input v-model="form.deliveryTerm" /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 行明细弹窗 -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="1000px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="订单号">{{ linesOwner.poNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ linesOwner.poType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="采购大类">{{ bizLabel((linesOwner as any).bizCategory) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ linesOwner.supplierName || '-' }}</el-descriptions-item>
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
        <el-table-column prop="receivedQty" label="已收数量" width="100" align="right">
          <template #default="{ row }">{{ row.receivedQty == null ? 0 : Number(row.receivedQty).toLocaleString() }}</template>
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

    <!-- 行编辑弹窗 -->
    <el-dialog v-model="lineDialog" :title="lineDialogTitle" width="600px" destroy-on-close top="10vh">
      <el-form ref="lineFormRef" :model="lineForm" :rules="lineRules" label-width="90px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="lineForm.materialId" filterable placeholder="选择物料" style="width:100%">
            <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId" />
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
          <el-col :span="6"><el-form-item label="订单数量" prop="orderQty"><el-input-number v-model="lineForm.orderQty" :min="0" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="单价"><el-input-number v-model="lineForm.unitPrice" :min="0" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="折扣率"><el-input-number v-model="lineForm.discountRate" :min="0" :max="0.9999" :step="0.01" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="税率"><el-input-number v-model="lineForm.taxRate" :min="0" :max="1" :step="0.01" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="行金额">
              <el-input-number v-model="lineForm.lineAmount" :min="0" :precision="2" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税额">
              <el-input-number v-model="lineForm.taxAmount" :min="0" :precision="2" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="交付日期">
          <el-date-picker v-model="lineForm.requiredDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="lineForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineDialog = false">取消</el-button>
        <el-button type="primary" :loading="lineSubmitLoading" @click="onLineSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 打印预览 -->
    <el-dialog v-model="printVisible" title="打印预览 - 采购订单" width="800px" :close-on-click-modal="false">
      <PurchaseOrderPrint :order="printRow" :visible="printVisible" />
    </el-dialog>

    <!-- ================= P1-B 生成入库单 ================= -->
    <el-dialog v-model="stockInDlg" title="生成采购入库单（分批多次入库）" width="1200px" top="4vh" destroy-on-close>
      <div v-if="stockInDlg && stockOwner" class="stockin-wrap">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="订单号"><b class="c-blue">{{ stockOwner.poNo || '-' }}</b></el-descriptions-item>
          <el-descriptions-item label="供应商">{{ stockOwner.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag v-if="typeof stockOwner.status === 'number'" :type="statusMap[stockOwner.status]?.t || ''">
              {{ statusMap[stockOwner.status]?.l || String(stockOwner.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="下单日期">{{ stockOwner.poDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="交付日期">{{ stockOwner.deliveryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">{{ stockOwner.totalAmount == null ? '-' : '¥' + Number(stockOwner.totalAmount).toFixed(2) }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">入库单头信息</el-divider>
        <el-form :model="si" :inline="true" label-width="80px" class="si-header">
          <el-form-item label="入库仓库 *" required>
            <el-select v-model="si.warehouseId" filterable placeholder="选择仓库" style="width:240px">
              <el-option v-for="w in warehouseOptions" :key="w.warehouseId"
                :label="`${w.warehouseName} (${w.warehouseCode})`" :value="w.warehouseId" />
            </el-select>
          </el-form-item>
          <el-form-item label="入库单号">
            <el-input v-model="si.stockInNo" placeholder="留空由系统生成" style="width:220px" maxlength="50" />
          </el-form-item>
          <el-form-item label="入库日期 *" required>
            <el-date-picker v-model="si.stockInDate" type="date" value-format="YYYY-MM-DD" style="width:180px" />
          </el-form-item>
          <el-form-item label="通用批次">
            <el-input v-model="si.commonBatch" placeholder="不填则行各自填" style="width:200px" maxlength="40" />
          </el-form-item>
          <el-form-item label="备注" style="flex:1;min-width:260px">
            <el-input v-model="si.remark" placeholder="批次说明/物流单号等（可选）" maxlength="200" />
          </el-form-item>
        </el-form>
        <div class="si-hint">
          <el-alert type="warning" :closable="false" show-icon>
            <template #title>
              请填写每行<b>本次入库数量</b>（剩余 = orderQty − receivedQty − returnedQty）。
              本次入库未税：<b class="c-blue">¥ {{ siTotal.toFixed(2) }}</b>
              &nbsp;，入库行数(>0)：<b>{{ siLines.filter(l=> Number(l.qty) > 0).length }} / {{ siLines.length }}</b>
            </template>
          </el-alert>
        </div>
        <div class="si-table">
          <el-table :data="siLines" stripe border>
            <el-table-column label="行号" width="56" align="center" prop="lineNo" />
            <el-table-column label="物料编码" width="150" prop="materialCode" />
            <el-table-column label="物料名称" width="180" prop="materialName" show-overflow-tooltip />
            <el-table-column label="规格" width="140" prop="specification" show-overflow-tooltip />
            <el-table-column label="单位" width="64" prop="unit" />
            <el-table-column label="订单数量" width="100" align="right">
              <template #default="{row}:any">{{ Number(row.orderQty||0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="已收数量" width="100" align="right">
              <template #default="{row}:any">{{ Number(row.receivedQty||0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="已退数量" width="90" align="right">
              <template #default="{row}:any">{{ Number(row.returnedQty||0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="剩余可入量" width="110" align="right">
              <template #default="{row}:any"><b :class="row.remaining <= 0 ? 'c-gray':'c-blue'">{{ row.remaining.toLocaleString() }}</b></template>
            </el-table-column>
            <el-table-column label="本次入库量 *" width="170">
              <template #default="{row}:any">
                <el-input-number v-model="row.qty" :min="0" :max="row.remaining"
                  :precision="isIntUnit(row.unit) ? 0 : 4"
                  :step="isIntUnit(row.unit) ? 1 : 0.1"
                  @change="row.qty = clamp(row.qty, 0, row.remaining)"
                  style="width:100%" size="default" />
              </template>
            </el-table-column>
            <el-table-column label="批次号" width="180">
              <template #default="{row}:any"><el-input v-model="row.batchNo" size="default" maxlength="40" /></template>
            </el-table-column>
            <el-table-column label="行金额(未税)" width="120" align="right">
              <template #default="{row}:any"><b class="c-orange">¥{{ lineAmt(row).toFixed(2) }}</b></template>
            </el-table-column>
            <el-table-column label="行备注" min-width="160">
              <template #default="{row}:any"><el-input v-model="row.remark" size="default" maxlength="100" /></template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="stockInDlg = false">取消</el-button>
        <el-button type="primary" :disabled="!canSubmitStockIn" :loading="stockInSubmitting" @click="submitStockIn">
          <el-icon><Check /></el-icon> 确认入库 ({{ siLines.filter(l=> Number(l.qty) > 0).length }}项)
        </el-button>
      </template>
    </el-dialog>

    <!-- ================= F1-1 生成到货通知单 ================= -->
    <el-dialog v-model="deliveryDialog" title="生成到货通知单" width="560px" destroy-on-close top="12vh">
      <el-form ref="deliveryFormRef" :model="deliveryForm" :rules="deliveryRules" label-width="110px">
        <el-form-item label="PO编号">
          <el-tag v-if="curRow" type="primary">{{ curRow.poNo || '-' }} #{{ curRow.poId }}</el-tag>
          <span v-else>-</span>
        </el-form-item>
        <el-form-item label="送货日期" required prop="deliveryDate">
          <el-date-picker v-model="deliveryForm.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="是否需QC" prop="qcRequired">
          <el-radio-group v-model="deliveryForm.qcRequired">
            <el-radio :value="1">需要IQC</el-radio>
            <el-radio :value="0">免检直入</el-radio>
            <el-radio :label="null">按默认</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="快递单号" prop="expressNo">
          <el-input v-model="deliveryForm.expressNo" maxlength="60" placeholder="可选" />
        </el-form-item>
        <el-form-item label="生产批次" prop="batchNo">
          <el-input v-model="deliveryForm.batchNo" maxlength="40" placeholder="可选（会同步到每行）" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="deliveryForm.remark" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliveryDialog = false">取消</el-button>
        <el-button type="primary" :loading="deliverySubmitting" @click="submitDelivery">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- ================= F1-2 到货质检 IQC ================= -->
    <el-dialog v-model="qcDialog" title="到货质检 (IQC) 判定" width="1320px" top="4vh" destroy-on-close>
      <div v-if="qcDialog" class="qc-wrap">
        <el-form :inline="true" :model="qcHeader" label-width="110px" class="qc-header">
          <el-form-item label="采购订单">
            <el-tag type="primary">{{ qcHeader.poNo || '-' }} #{{ qcHeader.poId }}</el-tag>
          </el-form-item>
          <el-form-item label="选择到货单" required>
            <el-select v-model="qcHeader.deliveryId" filterable placeholder="请选择该PO的到货通知单"
                       style="width:360px" :loading="deliveryListLoading" @change="onDeliveryChange">
              <el-option v-for="d in deliveryOptions" :key="d.deliveryId"
                         :label="`${d.deliveryNo} · 送货日期${d.deliveryDate||'-'} · 状态:${statusMap_delivery[d.status]?.l ?? d.status}`"
                         :value="d.deliveryId" />
            </el-select>
          </el-form-item>
          <el-form-item label="质检日期">
            <el-date-picker v-model="qcHeader.judgeDate" type="date" value-format="YYYY-MM-DD" style="width:160px" />
          </el-form-item>
          <el-form-item label="质检人">
            <el-input v-model="qcHeader.judgeBy" placeholder="可选" style="width:160px" />
          </el-form-item>
        </el-form>
        <div class="qc-hint">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              每行必须录入 inspectedQty（送检量 = 待检），并分配 acceptedQty / concessionQty / rejectedQty / scrappedQty，四者之和 ≤ inspectedQty。
              disposition = 最终处理方式（合格/让步/拒收/报废）。
            </template>
          </el-alert>
        </div>
        <el-table :data="qcRows" stripe border size="small" max-height="520">
          <el-table-column label="行" width="60" align="center" type="index" />
          <el-table-column label="行ID" width="80" prop="lineId" align="center" />
          <el-table-column label="物料ID" width="80" prop="materialId" align="center" />
          <el-table-column label="物料编码" width="140" prop="materialCode" />
          <el-table-column label="物料名称" min-width="160" prop="materialName" show-overflow-tooltip />
          <el-table-column label="规格" width="140" prop="specification" show-overflow-tooltip />
          <el-table-column label="单位" width="60" align="center" prop="unit" />
          <el-table-column label="PO订单量" width="100" align="right">
            <template #default="{row}">{{ Number(row.orderQty||0).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column label="已收量" width="90" align="right">
            <template #default="{row}">{{ Number(row.receivedQty||0).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column label="本次送货量" width="100" align="right">
            <template #default="{row}">{{ Number(row.deliveryQty||0).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column label="待检量" width="100" align="right">
            <template #default="{row}"><b class="c-blue">{{ Number(row.pendingQty||0).toLocaleString() }}</b></template>
          </el-table-column>
          <el-table-column label="送检量 *" width="120">
            <template #default="{row}">
              <el-input-number v-model="row.inspectedQty" :min="0" :precision="4" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="合格" width="110">
            <template #default="{row}">
              <el-input-number v-model="row.acceptedQty" :min="0" :precision="4" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="让步接收" width="110">
            <template #default="{row}">
              <el-input-number v-model="row.concessionQty" :min="0" :precision="4" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="拒收" width="110">
            <template #default="{row}">
              <el-input-number v-model="row.rejectedQty" :min="0" :precision="4" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="报废" width="110">
            <template #default="{row}">
              <el-input-number v-model="row.scrappedQty" :min="0" :precision="4" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="最终处置 *" width="150">
            <template #default="{row}">
              <el-select v-model="row.disposition" placeholder="请选择" style="width:100%">
                <el-option v-for="o in DISPOSITION_OPTIONS" :key="o.v" :label="o.l" :value="o.v" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="不良原因" width="160">
            <template #default="{row}">
              <el-select v-model="row.reasonCode" clearable placeholder="原因代码" style="width:100%">
                <el-option v-for="o in QC_REASON_OPTIONS" :key="o.v" :label="o.l" :value="o.v" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="行备注" min-width="160">
            <template #default="{row}">
              <el-input v-model="row.remark" size="small" maxlength="200" />
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="qcDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!canSubmitQcJudge" :loading="qcSubmitting" @click="submitQcJudge">
          <el-icon><Check /></el-icon> 判定提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, getCurrentInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshLeft, Plus, Download, Tickets, Check, Promotion, Van, CircleCheck } from '@element-plus/icons-vue'
import { scmPoApi, type ScmPoListQ, warehouseApi, scmSupplierApi, userApi, materialApi } from '@/api'
import type { ScmPurchaseOrder, ScmPurchaseOrderLine, WmsWarehouse } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'
import PurchaseOrderPrint from '@/views/print/PurchaseOrderPrint.vue'

defineOptions({ name: 'ScmPurchasePage' })
const { proxy } = getCurrentInstance() as any
const route = useRoute()
const router = useRouter()

const highlightId = ref<string | undefined>(route.query.highlight as string | undefined)
const curRow = ref<any>(null)
const selection = ref<any[]>([])

const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function hasPermOrThrow(p: string) { if (!hasPermission(p)) throw new Error('无此操作权限: ' + p); return true }

const printVisible = ref(false)
const printRow = ref<any>(null)
const openPrint = (row: any) => { printRow.value = row; printVisible.value = true }

// 状态机
const STATUS_LABEL: Record<number, { l:string; t?: string }> = {
  0: { l:'草稿', t:'info' },
  1: { l:'已提交', t:'warning' },
  2: { l:'已审核', t:'primary' },
  3: { l:'已下单', t:'' },
  4: { l:'部分到货', t:'warning' },
  5: { l:'全部到货', t:'' },
  6: { l:'已完成', t:'success' },
  7: { l:'已驳回', t:'danger' },
  9: { l:'取消', t:'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v,m])=>({ v: Number(v), l: m.l, t: m.t }))
const statusMap = STATUS_LABEL

const ACTION_DEF: Record<string, { label: string; type?: any; permission: string; allowFrom: number[]; disabledReason: (s:number)=>string }> = {
  submit:   { label:'提交',   type:'default', permission:'scm:purchase:submit',  allowFrom:[0,7], disabledReason:(s)=>`仅「草稿/驳回」可提交（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  audit:    { label:'审核',   type:'primary', permission:'scm:purchase:audit',   allowFrom:[1],   disabledReason:(s)=>`仅「已提交」可审核（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  release:  { label:'下达',   type:'primary', permission:'scm:purchase:release', allowFrom:[2],   disabledReason:(s)=>`仅「已审核」可下达（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  complete: { label:'完成',   type:'default', permission:'scm:purchase:close',   allowFrom:[5],   disabledReason:(s)=>`仅「全部到货」可完成（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  reject:   { label:'驳回',   type:'default', permission:'scm:purchase:reject',  allowFrom:[1],   disabledReason:(s)=>`仅「已提交」可驳回（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  cancel:   { label:'取消',   type:'danger',  permission:'scm:purchase:cancel',  allowFrom:[0,1,2,7], disabledReason:(s)=>`仅草稿/提交/审核/驳回可取消（当前:${STATUS_LABEL[s]?.l ?? s}）` }
}
const EDITABLE_STATUS = new Set<number>([0, 7])
function canEdit(row: ScmPurchaseOrder)  { return EDITABLE_STATUS.has(Number(row.status)) }
function canRemove(row: ScmPurchaseOrder){ return EDITABLE_STATUS.has(Number(row.status)) }
function cannotEditReason(row: ScmPurchaseOrder)   { return `仅「草稿/驳回」可编辑（当前:${STATUS_LABEL[row.status as number]?.l ?? row.status}）` }
function cannotRemoveReason(row: ScmPurchaseOrder) { return `仅「草稿/驳回」可删除（当前:${STATUS_LABEL[row.status as number]?.l ?? row.status}）` }

// 多选支持
function onSelectionChange(rows: any[]) { selection.value = rows }
// 批量转PO：勾选已审核未关闭的订单行（在其他实现中被使用，这里仅保留按钮入口，调用后端接口）
function canSelectConvert(row: any) {
  return Number(row.status) >= 2 && Number(row.status) <= 5
}
function batchConvertPo() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选需要批量转PO的订单行')
    return
  }
  ElMessageBox.confirm(`确认对选中的 ${selection.value.length} 条采购订单批量转PO？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        const ids = selection.value.map(r => r.poId).filter(Boolean)
        const resp = await proxy?.$request?.('/scm/purchase/batch-convert', 'POST', { poIds: ids })
        if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
        proxy?.$modal?.msgSuccess?.('批量转PO成功') || ElMessage.success('批量转PO成功')
        await loadList(1)
      } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '批量转PO失败') }
    }).catch(() => {})
}

// ============== 入库单逻辑 ==============
function canStockIn(row: any) {
  const s = Number(row?.status)
  return s === 2 || s === 3 || s === 4
}
function cannotStockInReason(row: any) {
  if (!row) return '请先选中行'
  const s = Number(row.status)
  if (s === 0 || s === 1) return '订单尚未 审核/下达，待 status=2(已审核) 后才能入库'
  if (s === 5) return '状态=5(全部到货)，无需再入库'
  if (s === 6) return '状态=6(已完成)，已关闭不能入库'
  if (s === 7) return '被驳回的订单，无法入库'
  if (s === 9) return '订单已取消'
  return `当前状态(${s})不允许入库`
}
const stockInDlg = ref(false)
const stockInSubmitting = ref(false)
const stockOwner = ref<any>(null)
const warehouseOptions = ref<WmsWarehouse[]>([])
const suppliers = ref<any[]>([])
const users = ref<any[]>([])
const materials = ref<any[]>([])
async function loadOptions() {
  try {
    const sResp = await scmSupplierApi.options() as any
    suppliers.value = sResp?.data || sResp || []
  } catch { suppliers.value = [] }
  try {
    const uResp = await userApi.options() as any
    users.value = uResp?.data || uResp || []
  } catch { users.value = [] }
  try {
    const mResp = await materialApi.list({ pageNum: 1, pageSize: 1000 }) as any
    materials.value = (mResp as any)?.rows || []
  } catch { materials.value = [] }
}
const si = reactive<{ warehouseId?: number; stockInNo: string; stockInDate: string; commonBatch: string; remark: string; }>({
  warehouseId: undefined, stockInNo: '', stockInDate: '', commonBatch: '', remark: ''
})
interface SiLine {
  lineId: number; lineNo: number | string; materialId: number; materialCode?: string; materialName?: string
  specification?: string; unit?: string
  orderQty: number; receivedQty: number; returnedQty: number
  unitPrice: number
  remaining: number
  qty: number; batchNo: string; remark: string
}
const siLines = ref<SiLine[]>([])
function todayStr() { const d=new Date(); const m=String(d.getMonth()+1).padStart(2,'0'); const day=String(d.getDate()).padStart(2,'0'); return `${d.getFullYear()}-${m}-${day}` }
function clamp(v: any, lo: number, hi: number): number {
  let x = Number(v); if (!Number.isFinite(x)) x = 0
  return Math.min(hi, Math.max(lo, x))
}
function isIntUnit(u?: string) {
  if (!u) return true
  return /^(PCS|PC|个|台|片|块|颗|套|支|件|张|箱|卷|根|米|只|袋)$/.test(u.toUpperCase())
}
function lineAmt(l: SiLine) { return Number(l.qty) * Number(l.unitPrice || 0) }
const siTotal = computed(() => siLines.value.reduce((s,l)=> s + lineAmt(l), 0))
const canSubmitStockIn = computed(() => {
  if (!si.warehouseId) return false
  if (!si.stockInDate) return false
  const nz = siLines.value.filter(l => Number(l.qty) > 0)
  if (!nz.length) return false
  for (const l of nz) if (l.qty > l.remaining) return false
  return true
})
async function openStockIn(row: any) {
  try { await hasPermOrThrow('scm:purchase:stockIn') } catch (e: any) { ElMessage.error(e?.message); return }
  if (!canStockIn(row)) { ElMessage.warning(cannotStockInReason(row)); return }
  stockOwner.value = row
  if (!warehouseOptions.value.length) {
    try { warehouseOptions.value = (await warehouseApi.options() as any) || [] } catch { warehouseOptions.value = [] }
  }
  const pickDefault = () => {
    if (!warehouseOptions.value.length) return undefined
    const w1 = warehouseOptions.value.find((w:any) => (w.warehouseCode+'').toUpperCase().includes('WH-01'))
    if (w1) return w1.warehouseId
    const w2 = warehouseOptions.value.find((w:any) => (w.warehouseName||'').includes('原材料'))
    if (w2) return w2.warehouseId
    return warehouseOptions.value[0]?.warehouseId
  }
  si.warehouseId = pickDefault()
  si.stockInNo = ''
  si.stockInDate = todayStr()
  si.commonBatch = `PO${row.poId}-${todayStr().replace(/-/g,'')}`
  si.remark = ''
  siLines.value = []
  try {
    const res = await scmPoApi.lines(Number(row.poId)) as any
    const arr: any[] = (res?.rows) || []
    siLines.value = arr.map((r, idx) => {
      const o = Number(r.orderQty || 0); const rc = Number(r.receivedQty || 0); const rt = Number(r.returnedQty || 0)
      const remaining = Math.max(0, o - rc - rt)
      return {
        lineId: Number(r.lineId), lineNo: r.lineNo ?? (idx+1),
        materialId: Number(r.materialId), materialCode: r.materialCode, materialName: r.materialName,
        specification: r.specification, unit: r.unit,
        orderQty: o, receivedQty: rc, returnedQty: rt,
        unitPrice: Number(r.unitPrice || 0),
        remaining, qty: 0, batchNo: '', remark: ''
      } as SiLine
    })
  } catch (e: any) { ElMessage.error(e?.message || '拉取订单行失败'); return }
  stockInDlg.value = true
}
async function submitStockIn() {
  try {
    if (!canSubmitStockIn.value) { ElMessage.warning('请选择仓库、日期，并至少填一行>0的入库数量'); return }
    stockInSubmitting.value = true
    const poId = Number(stockOwner.value?.poId)
    if (!poId) throw new Error('PO ID 丢失')
    const nz = siLines.value.filter(l => Number(l.qty) > 0)
    const lines = nz.map(l => ({
      lineId: l.lineId, qty: Number(l.qty),
      batchNo: l.batchNo || si.commonBatch || undefined,
      unitPrice: Number(l.unitPrice) || undefined,
      remark: l.remark || undefined
    }))
    const body = {
      warehouseId: Number(si.warehouseId!),
      stockInNo: si.stockInNo || undefined,
      stockInDate: si.stockInDate,
      remark: si.remark || undefined,
      lines
    }
    const stockInId = await scmPoApi.generateStockIn(poId, body) as any
    const id = (typeof stockInId === 'number' || typeof stockInId === 'string') ? stockInId : (stockInId?.data ?? stockInId)
    proxy?.$modal?.msgSuccess?.(`入库成功！生成入库单 stockInId=${id}`) || ElMessage.success(`入库成功！生成入库单 stockInId=${id}`)
    stockInDlg.value = false
    await loadList(1)
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '入库失败')
  } finally { stockInSubmitting.value = false }
}

function stateActionsFor(row: ScmPurchaseOrder): any[] {
  const s = Number(row.status)
  return (['submit','audit','release','complete','reject','cancel'] as const).map((key) => {
    const d = ACTION_DEF[key]
    const enabled = d.allowFrom.includes(s)
    return {
      key, label: d.label, type: d.type, permission: d.permission,
      disabled: !enabled,
      disabledReason: enabled ? '' : d.disabledReason(s),
      onClick: (async (r: any) => {
        try {
          await hasPermOrThrow(d.permission)
          const resp = await (scmPoApi as any)[key](r.poId) as any
          if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          proxy?.$modal?.msgSuccess?.(d.label + '成功') || ElMessage.success(d.label + '成功')
          await loadList()
        } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || d.label + '失败') }
      }),
      confirm: (key === 'submit' || key === 'cancel') ? `确认${d.label}当前记录？`
               : (key === 'reject' ? `确认驳回当前订单？驳回后可退回修改并重新提交。` : '')
    }
  })
}
function onActionClick(a: any, row: any) {
  if (a.confirm) { ElMessageBox.confirm(a.confirm, '操作确认', { type:'warning' }).then(()=>a.onClick?.(row)).catch(()=>{}) }
  else { a.onClick?.(row) }
}

const sOpt_poType = [{v:'STOCK',l:'备货'},{v:'JIT',l:'JIT'},{v:'SAMPLE',l:'样品'},{v:'URGENT',l:'加急'}]
const fOpt_poType = sOpt_poType

const BIZ_CATEGORIES = [
  { v: 'ELECTRONIC', l: '电子物料采购' },
  { v: 'ACCESSORY',  l: '配件采购' },
  { v: 'SPARE',      l: '备品备件' }
] as const
function bizLabel(v?: string) {
  if (!v) return '-'
  return BIZ_CATEGORIES.find((b) => b.v === v)?.l ?? v
}

const query = reactive<ScmPoListQ>({ pageNum:1, pageSize:20, poNo:'', supplierName:'', poType:'', bizCategory:'', status: undefined as number|undefined })
const list = ref<ScmPurchaseOrder[]>([])
const total = ref(0)
const loading = ref(false)

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await scmPoApi.list({ pageNum: query.pageNum, pageSize: query.pageSize, poNo: query.poNo, supplierName: query.supplierName, poType: query.poType, bizCategory: query.bizCategory || undefined, status: query.status })) as any
    list.value = (resp?.rows) || []
    total.value = Number(resp?.total) || 0
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '加载失败')
    list.value = []; total.value = 0
  } finally { loading.value = false }
}
function resetQuery() { (query as any).poNo = ''; (query as any).supplierName = ''; (query as any).poType = ''; (query as any).bizCategory = ''; (query as any).status = undefined; loadList(1) }

// ===== 主单CRUD =====
const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => (form.poId ? (readonly.value ? '采购订单详情' : '编辑采购订单') : '新增采购订单'))
const form = reactive<Partial<ScmPurchaseOrder>>({
  poNo:'', poType:'', bizCategory:'ELECTRONIC', supplierId:undefined, supplierName:'', contactName:'', contactPhone:'',
  buyerId:undefined, poDate:'', deliveryDate:'', currency:'', taxRate:undefined, discountRate:undefined,
  totalAmount:undefined, paymentTerm:'', deliveryTerm:'', remark:''
})
function formDefaults() {
  form.poNo=''; form.poType=''; form.bizCategory='ELECTRONIC'; form.supplierId=undefined; form.supplierName=''; form.contactName=''; form.contactPhone=''
  form.buyerId=undefined; form.poDate=''; form.deliveryDate=''; form.currency=''; form.taxRate=undefined; form.discountRate=undefined
  form.totalAmount=undefined; form.paymentTerm=''; form.deliveryTerm=''; form.remark=''
}
function openDialog(row?: any) {
  formDefaults()
  if (row?.poId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: any) {
  formDefaults()
  try {
    const detail = (await scmPoApi.get(row.poId)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.poId) { await scmPoApi.edit(form as any); proxy?.$modal?.msgSuccess?.('修改成功') || ElMessage.success('修改成功') }
    else { await scmPoApi.add(form as any); proxy?.$modal?.msgSuccess?.('新增成功') || ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    proxy?.$modal?.msgError?.(msg) || ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: any) {
  try {
    await hasPermOrThrow('scm:purchase:remove')
    await scmPoApi.remove(row.poId)
    proxy?.$modal?.msgSuccess?.('删除成功') || ElMessage.success('删除成功'); await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '删除失败') }
}

// ===== 行明细 =====
const linesDialog = ref(false)
const linesOwner = ref<ScmPurchaseOrder | null>(null)
const lines = ref<ScmPurchaseOrderLine[]>([])
const linesLoading = ref(false)
const linesTitle = computed(() => `采购订单明细${linesOwner.value?.poNo ? ' - ' + linesOwner.value.poNo : ''}`)
async function onViewLines(row: ScmPurchaseOrder) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    const resp = (await scmPoApi.lines(row.poId!)) as any
    const rows = (resp?.rows) || []
    lines.value = (rows as ScmPurchaseOrderLine[]).slice().sort((a,b)=>(a.lineNo||0)-(b.lineNo||0))
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '加载明细失败'); lines.value = [] }
  finally { linesLoading.value = false }
}

// 行编辑
const lineDialog = ref(false)
const lineSubmitLoading = ref(false)
const lineFormRef = ref<FormInstance | null>(null)
const lineEditingId = ref<number | null>(null)
const lineForm = reactive<Partial<ScmPurchaseOrderLine>>({
  poId: undefined, materialId: 1, materialCode: '', materialName: '', specification: '', unit: 'PCS',
  orderQty: 0, receivedQty: 0, returnedQty: 0, unitPrice: 0, discountRate: 0, taxRate: 0, lineAmount: 0,
  taxAmount: 0, requiredDate: '', remark: ''
})
const lineRules: FormRules = {
  materialId: [{ required: true, message: '物料必填', trigger: 'change' }],
  orderQty:   [{ required: true, validator: (_r: any, v: any, cb: any) => (v != null && Number(v) > 0) ? cb() : cb(new Error('订单数量需大于0')), trigger: 'change' }],
}
const lineDialogTitle = computed(() => lineEditingId.value ? '编辑订单行' : '新增订单行')
function resetLineForm() {
  Object.assign(lineForm, {
    poId: linesOwner.value?.poId ?? undefined, materialId: 1, materialCode: '', materialName: '',
    specification: '', unit: 'PCS', orderQty: 0, receivedQty: 0, returnedQty: 0, unitPrice: 0,
    discountRate: 0, taxRate: 0, lineAmount: 0, taxAmount: 0,
    requiredDate: linesOwner.value?.deliveryDate ?? '', remark: ''
  })
  lineEditingId.value = null
}
function openLineDialog(row?: ScmPurchaseOrderLine) {
  if (!linesOwner.value?.poId) { ElMessage.warning('缺少主单ID'); return }
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
async function onLineSubmit() {
  lineSubmitLoading.value = true
  try {
    await lineFormRef.value?.validate()
    recalcLineAmount()
    const payload = { ...lineForm, poId: linesOwner.value!.poId! }
    if (lineEditingId.value) {
      await scmPoApi.editLine({ ...payload, lineId: lineEditingId.value } as any)
      proxy?.$modal?.msgSuccess?.('修改行成功') || ElMessage.success('修改行成功')
    } else {
      await scmPoApi.addLine(payload as any)
      proxy?.$modal?.msgSuccess?.('新增行成功') || ElMessage.success('新增行成功')
    }
    lineDialog.value = false
    await onViewLines(linesOwner.value!)
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    proxy?.$modal?.msgError?.(msg) || ElMessage.error(msg)
  } finally { lineSubmitLoading.value = false }
}
async function onRemoveLine(row: ScmPurchaseOrderLine) {
  try { await ElMessageBox.confirm(`确定删除行号 ${row.lineNo} ?`, '提示', { type: 'warning' }) } catch { return }
  try { await scmPoApi.removeLine(row.lineId as number); proxy?.$modal?.msgSuccess?.('删除成功') || ElMessage.success('删除成功'); await onViewLines(linesOwner.value!) }
  catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '删除失败') }
}

// ==================== F1-1 生成到货通知单 ====================
const deliveryDialog = ref(false)
const deliverySubmitting = ref(false)
const deliveryFormRef = ref<FormInstance>()
const deliveryForm = reactive({
  deliveryDate: todayStr(),
  qcRequired: null as 0|1|null,
  expressNo: '',
  batchNo: '',
  remark: ''
})
const deliveryRules: FormRules = {
  deliveryDate: [{ required: true, message: '送货日期必填', trigger: 'change' }]
}
function createDelivery(row: any) {
  if (!row || !row.poId) { ElMessage.warning('请先选中一条采购订单'); return }
  if (Number(row.status) < 2) { ElMessage.warning('只有已审核(status≥2)的PO才能生成到货通知单'); return }
  curRow.value = row
  deliveryForm.deliveryDate = todayStr()
  deliveryForm.qcRequired = 1
  deliveryForm.expressNo = ''
  deliveryForm.batchNo = ''
  deliveryForm.remark = ''
  deliveryDialog.value = true
}
async function submitDelivery() {
  if (!curRow.value?.poId) { ElMessage.warning('缺失poId'); return }
  try {
    await deliveryFormRef.value?.validate()
    deliverySubmitting.value = true
    const resp = await proxy?.$request?.('/scm/delivery/from-purchase-order', 'POST', {
      poId: curRow.value.poId,
      headerExtra: {
        qc_required: deliveryForm.qcRequired,
        express_no: deliveryForm.expressNo,
        batch_no: deliveryForm.batchNo,
        remark: deliveryForm.remark
      }
    })
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '生成失败')
    proxy?.$modal?.msgSuccess?.('生成到货通知单成功') || ElMessage.success('生成到货通知单成功')
    deliveryDialog.value = false
    await loadList()
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '生成失败')
  } finally { deliverySubmitting.value = false }
}

// ==================== F1-2 到货质检(IQC) ====================
const DISPOSITION_OPTIONS = [
  { v: 'ACCEPT', l: 'ACCEPT 合格' },
  { v: 'CONCESSION', l: 'CONCESSION 让步接收' },
  { v: 'REJECT', l: 'REJECT 拒收' },
  { v: 'SCRAP', l: 'SCRAP 报废' }
]
const QC_REASON_OPTIONS = [
  { v: 'DIM', l: '尺寸超差' },
  { v: 'APP', l: '外观不良' },
  { v: 'FUNC', l: '功能失效' },
  { v: 'PACK', l: '包装破损' },
  { v: 'DOC', l: '文件缺失' },
  { v: 'OTHER', l: '其他' }
]
const statusMap_delivery: Record<number, { l: string }> = {
  0: { l: '草稿' }, 1: { l: '送货中' }, 2: { l: '部分收' },
  3: { l: '已收货' }, 4: { l: '待检' }, 5: { l: '已检' }, 9: { l: '作废' }
}

const qcDialog = ref(false)
const qcSubmitting = ref(false)
const qcHeader = reactive<{
  poId: any; poNo: string; deliveryId: any; judgeDate: string; judgeBy: string;
}>({ poId: undefined, poNo: '', deliveryId: undefined, judgeDate: todayStr(), judgeBy: '' })

const deliveryListLoading = ref(false)
const deliveryOptions = ref<any[]>([])

interface QcRow {
  lineId: number; materialId: number; materialCode?: string; materialName?: string; specification?: string; unit?: string
  orderQty: number; receivedQty: number; deliveryQty: number; pendingQty: number
  inspectedQty: number; acceptedQty: number; concessionQty: number; rejectedQty: number; scrappedQty: number
  disposition: string; reasonCode?: string; remark?: string
}
const qcRows = ref<QcRow[]>([])

function canQcJudge(row: any): boolean {
  if (!row?.poId) return false
  const s = Number(row.status)
  return s === 3 || s === 4 || s === 5 // 已下单/部分到货/全部到货
}
async function openQcJudge(row: any) {
  if (!canQcJudge(row)) {
    ElMessage.warning('仅 已下单/部分到货/全部到货 状态可执行到货质检')
    return
  }
  curRow.value = row
  qcHeader.poId = row.poId
  qcHeader.poNo = row.poNo || ''
  qcHeader.deliveryId = undefined
  qcHeader.judgeDate = todayStr()
  qcHeader.judgeBy = ''
  qcRows.value = []
  qcDialog.value = true
  // 自动拉取该PO最新 delivery 列表
  deliveryListLoading.value = true
  try {
    const resp = await proxy?.$request?.('/scm/delivery/list', 'POST', {
      pageNum: 1, pageSize: 50, poId: row.poId
    })
    const list = (resp?.rows) || (resp?.data?.rows) || []
    deliveryOptions.value = list.slice().sort((a: any, b: any) => {
      return Number(b.deliveryId || 0) - Number(a.deliveryId || 0)
    })
    if (deliveryOptions.value.length) {
      qcHeader.deliveryId = deliveryOptions.value[0].deliveryId
      await onDeliveryChange(qcHeader.deliveryId)
    }
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '拉取到货单失败')
  } finally { deliveryListLoading.value = false }
}
async function onDeliveryChange(deliveryId: any) {
  qcRows.value = []
  if (!deliveryId) return
  try {
    const resp = await proxy?.$request?.(`/scm/delivery/${deliveryId}/lines`, 'POST', {})
    const arr = (resp?.rows) || (resp?.data?.rows) || []
    qcRows.value = arr.map((r: any) => {
      const orderQty = Number(r.orderQty || 0)
      const receivedQty = Number(r.receivedQty || 0)
      const deliveryQty = Number(r.deliveryQty || r.qty || 0)
      // pendingQty = max(0, deliveryQty - receivedQty_from_previous_inspection); 简单处理取 deliveryQty
      const pendingQty = Math.max(0, deliveryQty)
      return {
        lineId: Number(r.lineId || r.poLineId || r.deliveryLineId || 0),
        materialId: Number(r.materialId || 0),
        materialCode: r.materialCode,
        materialName: r.materialName,
        specification: r.specification,
        unit: r.unit,
        orderQty, receivedQty, deliveryQty,
        pendingQty,
        inspectedQty: pendingQty,
        acceptedQty: 0,
        concessionQty: 0,
        rejectedQty: 0,
        scrappedQty: 0,
        disposition: 'ACCEPT',
        reasonCode: '',
        remark: ''
      } as QcRow
    })
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '拉取到货单明细失败')
  }
}
const canSubmitQcJudge = computed(() => {
  if (!qcHeader.deliveryId) return false
  if (!qcRows.value.length) return false
  for (const r of qcRows.value) {
    const ins = Number(r.inspectedQty) || 0
    if (ins <= 0) return false
    const sum = Number(r.acceptedQty||0) + Number(r.concessionQty||0) + Number(r.rejectedQty||0) + Number(r.scrappedQty||0)
    if (sum > ins + 1e-6) return false
    if (!r.disposition) return false
  }
  return true
})
async function submitQcJudge() {
  if (!canSubmitQcJudge.value) {
    ElMessage.warning('请选择到货单并完善每行质检判定')
    return
  }
  try {
    qcSubmitting.value = true
    const judgements = qcRows.value.map(r => ({
      lineId: r.lineId,
      inspectedQty: Number(r.inspectedQty) || 0,
      acceptedQty: Number(r.acceptedQty) || 0,
      concessionQty: Number(r.concessionQty) || 0,
      rejectedQty: Number(r.rejectedQty) || 0,
      scrappedQty: Number(r.scrappedQty) || 0,
      disposition: r.disposition,
      reasonCode: r.reasonCode || undefined,
      remark: r.remark || undefined
    }))
    const resp = await proxy?.$request?.(`/scm/delivery/${qcHeader.deliveryId}/qc-judge`, 'POST', {
      judgeDate: qcHeader.judgeDate,
      judgeBy: qcHeader.judgeBy || undefined,
      judgements
    })
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '判定失败')
    proxy?.$modal?.msgSuccess?.('IQC判定提交成功') || ElMessage.success('IQC判定提交成功')
    qcDialog.value = false
    await loadList()
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '判定失败')
  } finally { qcSubmitting.value = false }
}

onMounted(() => { loadList(); loadOptions() })
</script>

<style scoped>
.page-scm-purchase { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.lines-header { margin-bottom: 4px; }
.lines-footer { margin-top: 8px; display: flex; justify-content: flex-end; }
.lines-total { color: var(--el-text-color-secondary); font-size: 12px; }
:deep(.row-highlight) { background-color: #ecf5ff !important; box-shadow: inset 3px 0 0 #409EFF; }
.stockin-wrap { padding: 0 8px; }
.si-header { background:#f5f7fa; padding: 12px 14px; border-radius: 6px; }
.si-hint { margin: 14px 0 10px; }
.si-table { margin-top: 6px; }
.badge { display:inline-block; min-width:18px; padding:0 6px; margin-left:6px; border-radius:9px; background:#67C23A; color:#fff; font-size:12px; text-align:center; line-height:18px; }
.c-blue { color:#409EFF; }
.c-orange { color:#E6A23C; }
.c-red { color:#F56C6C; }
.c-gray { color:#C0C4CC; }

/* QC dialog */
.qc-wrap { padding: 0 4px; }
.qc-header { background:#f5f7fa; padding:12px 14px; border-radius:6px; }
.qc-hint { margin: 12px 0 10px; }
</style>
