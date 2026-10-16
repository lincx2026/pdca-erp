<template>
  <div class="page-sal-ar-receivable">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="应收单号"><el-input v-model="query.receivableNo" placeholder="应收单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="客户">
        <el-select v-model="query.customerId" placeholder="全部客户" clearable filterable style="width:220px">
          <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务日期"><el-date-picker v-model="query.bizDate" type="date" value-format="YYYY-MM-DD" placeholder="YYYY-MM-DD" style="width:160px" /></el-form-item>
      <el-form-item label="到期日"><el-date-picker v-model="query.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="YYYY-MM-DD" style="width:160px" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:150px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="逾期">
        <el-select v-model="query.isOverdue" placeholder="全部" clearable style="width:120px">
          <el-option label="是" :value="1" />
          <el-option label="否" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'sal:arreceivable:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增应收单</el-button>
      <el-button v-hasPermi="'sal:arreceivable:export'" :loading="exporting" @click="doExport"><el-icon><Download /></el-icon> 导出</el-button>
      <el-button v-hasPermi="'sal:arreceivable:reconcile'" @click="openBatchDialog"><el-icon><Check /></el-icon> 批量对账</el-button>
      <el-button v-hasPermi="'sal:arreceivable:export'" :loading="stmtExporting" @click="openStatementDialog"><el-icon><Document /></el-icon> 对账单导出</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="receivableNo" label="应收单号" width="180" />
      <el-table-column prop="bizType" label="业务类型" width="100" />
      <el-table-column prop="customerName" label="客户" width="180" show-overflow-tooltip />
      <el-table-column prop="bizDate" label="业务日期" width="110" />
      <el-table-column prop="creditDays" label="信用期(天)" width="95" align="right" />
      <el-table-column prop="dueDate" label="到期日" width="110" />
      <el-table-column prop="currency" label="币种" width="70" align="center" />
      <el-table-column prop="totalAmount" label="应收金额" width="130" align="right">
        <template #default="{ row }">{{ row.totalAmount == null ? '-' : fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="receivedAmount" label="已收金额" width="130" align="right">
        <template #default="{ row }">{{ row.receivedAmount == null ? '-' : fmt(row.receivedAmount) }}</template>
      </el-table-column>
      <el-table-column prop="unreceivedAmount" label="未收金额" width="130" align="right">
        <template #default="{ row }">
          <span :class="{ overdue: Number(row.isOverdue) === 1 }">{{ row.unreceivedAmount == null ? '-' : fmt(row.unreceivedAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="逾期" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="Number(row.isOverdue) === 1" type="danger">是({{ row.overdueDays ?? 0 }}天)</el-tag>
          <el-tag v-else type="success">否</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l || String(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="600" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onViewLines(row)">明细</el-button>
          <el-button size="small" link @click="onViewPlans(row)">计划</el-button>
          <el-button size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'sal:arreceivable:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-else size="small" link type="primary" disabled>编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除当前应收单？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'sal:arreceivable:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <template v-for="a in actions(row)" :key="a.key">
            <el-tooltip v-if="a.disabled" :content="a.disabledReason" placement="top">
              <el-button size="small" link disabled>{{ a.label }}</el-button>
            </el-tooltip>
            <el-button v-else-if="hasPermission(a.permission)" v-hasPermi="[a.permission]"
              size="small" link :type="a.type" @click="a.onClick?.(row)">{{ a.label }}</el-button>
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

    <!-- 主单弹窗 -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="应收单号" prop="receivableNo"><el-input v-model="form.receivableNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="业务类型"><el-input v-model="form.bizType" placeholder="IN/OUT/MANUAL" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="客户" required prop="customerId">
            <el-select v-model="form.customerId" placeholder="选择客户" filterable style="width:100%" @change="onCustomerChange">
              <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="币种"><el-input v-model="form.currency" placeholder="CNY" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="汇率"><el-input-number v-model="form.exchangeRate" :precision="6" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="信用期(天)"><el-input-number v-model="form.creditDays" :precision="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="业务日期" required prop="bizDate"><el-date-picker v-model="form.bizDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="到期日" required prop="dueDate"><el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-descriptions title="金额" :column="3" border size="small" style="margin-bottom:8px;">
          <el-descriptions-item label="税前">{{ fmt(form.undiscAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ fmt(form.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="折扣">{{ fmt(form.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="应收总额">{{ fmt(form.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="折本总额">{{ fmt(form.cnyTotalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未收">{{ fmt(form.unreceivedAmount) }}</el-descriptions-item>
        </el-descriptions>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 明细行弹窗 -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="1100px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="应收单号">{{ linesOwner.receivableNo }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ linesOwner.customerName }}</el-descriptions-item>
          <el-descriptions-item label="业务/到期日">{{ linesOwner.bizDate }} / {{ linesOwner.dueDate }}</el-descriptions-item>
          <el-descriptions-item label="应收/未收">{{ fmt(linesOwner.totalAmount) }} / {{ fmt(linesOwner.unreceivedAmount) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider content-position="left">
        <span style="display:flex; align-items:center; gap:10px;">
          <span>应收行 ({{ lines.length }})</span>
          <el-button type="primary" size="small" @click="openLineDialog()">新增行</el-button>
        </span>
      </el-divider>
      <el-table v-loading="linesLoading" :data="lines" stripe border size="small" max-height="460">
        <el-table-column prop="lineNo" label="行号" width="60" align="center" />
        <el-table-column prop="lineSrc" label="来源" width="80" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格" width="140" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="qty" label="数量" width="100" align="right">
          <template #default="{ row }">{{ row.qty == null ? '-' : Number(row.qty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="100" align="right">
          <template #default="{ row }">{{ row.unitPrice == null ? '-' : fmt(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="80" align="right">
          <template #default="{ row }">{{ row.taxRate == null ? '-' : (Number(row.taxRate) * 100).toFixed(0) + '%' }}</template>
        </el-table-column>
        <el-table-column prop="lineAmount" label="行金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.lineAmount) }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="110" />
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openLineDialog(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="onRemoveLine(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="linesDialog = false">关闭</el-button></template>
    </el-dialog>

    <!-- 行弹窗 -->
    <el-dialog v-model="lineDialog" :title="lineDialogTitle" width="580px" destroy-on-close top="10vh">
      <el-form ref="lineFormRef" :model="lineForm" :rules="lineRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="lineForm.materialId" placeholder="选择物料" filterable style="width:100%" @change="onMaterialChange">
            <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName || ''}`" :value="m.materialId">
              <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
              <span style="float:right; color:var(--el-text-color-secondary); font-size:12px;">{{ m.specification }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="物料编码"><el-input v-model="lineForm.materialCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料名称"><el-input v-model="lineForm.materialName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="lineForm.specification" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="lineForm.unit" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="数量" prop="qty"><el-input-number v-model="lineForm.qty" :precision="4" controls-position="right" style="width:100%" @change="recalcLine" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单价"><el-input-number v-model="lineForm.unitPrice" :precision="4" controls-position="right" style="width:100%" @change="recalcLine" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="税率%"><el-input-number v-model="taxPercent" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" @change="onTaxChange" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="税前金额"><el-input-number v-model="lineForm.undiscAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税额"><el-input-number v-model="lineForm.taxAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="行金额">
          <el-input-number v-model="lineForm.lineAmount" :precision="2" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="批次"><el-input v-model="lineForm.batchNo" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="lineForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineDialog = false">取消</el-button>
        <el-button type="primary" :loading="lineSubmitLoading" @click="onLineSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 收款计划弹窗 -->
    <el-dialog v-model="plansDialog" title="收款计划" width="900px" destroy-on-close top="8vh">
      <div v-if="plansOwner">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="应收单号">{{ plansOwner.receivableNo }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ plansOwner.customerName }}</el-descriptions-item>
          <el-descriptions-item label="应收总额">{{ fmt(plansOwner.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未收金额">{{ fmt(plansOwner.unreceivedAmount) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider />
      <div style="display:flex; gap:8px; align-items:center; margin-bottom:8px; flex-wrap:wrap;">
        <span class="tip">分期之和需 = 100%</span>
        <el-button size="small" @click="addPhaseRow()">+ 分期</el-button>
        <el-button size="small" type="primary" :disabled="!plansOwner" :loading="planLoading" @click="onGeneratePlans">保存分期 / 重建计划</el-button>
      </div>
      <el-table :data="phases" border size="small">
        <el-table-column label="期次" width="80">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="名称" width="160">
          <template #default="{ row }"><el-input v-model="row.phaseName" placeholder="如 30% 首款" /></template>
        </el-table-column>
        <el-table-column label="比例(%)" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.ratio" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" @change="recomputePhaseAmounts" />
          </template>
        </el-table-column>
        <el-table-column label="计划日期" width="180">
          <template #default="{ row }"><el-date-picker v-model="row.planDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="计划金额" min-width="140" align="right">
          <template #default="{ row }">{{ row._amount == null ? '-' : fmt(row._amount) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ $index }">
            <el-button v-if="phases.length > 1" size="small" link type="danger" @click="phases.splice($index,1); recomputePhaseAmounts()">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex; justify-content:space-between; margin-top:8px; color:var(--el-text-color-secondary); font-size:12px;">
        <span>比例合计: <b :style="{ color: ratioSum === 100 ? 'var(--el-color-success)' : 'var(--el-color-danger)' }">{{ ratioSum.toFixed(2) }}%</b></span>
        <span>现有计划数: {{ existingPlans.length }}</span>
      </div>
      <el-divider content-position="left">现有计划</el-divider>
      <el-table v-loading="plansLoading" :data="existingPlans" border size="small" max-height="260">
        <el-table-column prop="phaseNo" label="期次" width="70" />
        <el-table-column prop="planDate" label="日期" width="120" />
        <el-table-column prop="ratio" label="比例(%)" width="100" align="right" />
        <el-table-column prop="planAmount" label="计划金额" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.planAmount) }}</template>
        </el-table-column>
        <el-table-column prop="actualAmount" label="已收" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.actualAmount) }}</template>
        </el-table-column>
        <el-table-column prop="unpaidAmount" label="未收" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.unpaidAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="planStatusMap[row.status as number]?.t || ''">{{ planStatusMap[row.status as number]?.l ?? row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="plansDialog = false">关闭</el-button></template>
    </el-dialog>

    <!-- 提交对账弹窗(0→1)：录入 attachment/remark -->
    <el-dialog v-model="submitReconcileDialog" title="提交对账" width="560px" destroy-on-close>
      <el-form :model="submitReconcileForm" label-width="100px">
        <el-form-item label="应收单号"><el-input v-model="submitReconcileForm.receivableNo" disabled /></el-form-item>
        <el-form-item label="客户签字人"><el-input v-model="submitReconcileForm.signBy" placeholder="如客户已签字请填写签字人姓名（提交阶段可空）" /></el-form-item>
        <el-form-item label="对账单附件">
          <el-input v-model="submitReconcileForm.attachment" placeholder="附件URL/编号（可空）" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="submitReconcileForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitReconcileDialog = false">取消</el-button>
        <el-button type="primary" :loading="reconcileSubmitting" @click="onSubmitReconcile">提交</el-button>
      </template>
    </el-dialog>

    <!-- 确认对账弹窗(1→2)：录入 signBy(必填)/signTime/voucherId -->
    <el-dialog v-model="confirmReconcileDialog" title="确认对账" width="560px" destroy-on-close>
      <el-form :model="confirmReconcileForm" label-width="100px">
        <el-form-item label="应收单号"><el-input v-model="confirmReconcibleNo" disabled /></el-form-item>
        <el-form-item label="客户签字人" required>
          <el-input v-model="confirmReconcileForm.signBy" placeholder="客户签字人姓名(必填)" />
        </el-form-item>
        <el-form-item label="签字时间">
          <el-date-picker v-model="confirmReconcileForm.signTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="留空=当前时间" style="width:100%" />
        </el-form-item>
        <el-form-item label="财务凭证号">
          <el-input-number v-model="confirmReconcileForm.voucherId" :min="1" :precision="0" controls-position="right" style="width:100%" placeholder="留空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmReconcileDialog = false">取消</el-button>
        <el-button type="success" :loading="reconcileConfirming" @click="onConfirmReconcile">确认对账</el-button>
      </template>
    </el-dialog>

    <!-- 批量对账弹窗 -->
    <el-dialog v-model="batchDialog" title="批量对账（按客户+业务日期范围，0→2 直通）" width="520px" destroy-on-close>
      <el-form :model="batchForm" label-width="100px">
        <el-form-item label="客户" required>
          <el-select v-model="batchForm.customerId" placeholder="选择客户" filterable style="width:100%">
            <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
          </el-select>
        </el-form-item>
        <el-form-item label="起始日期" required>
          <el-date-picker v-model="batchForm.dateFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="batchForm.dateTo" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <div class="tip">提示：批量对账将指定客户在该业务日期范围内「待对账(0)」的应收单推进至「对账完成(2)」，签字/凭证字段后续可在详情中补录。</div>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="onBatchReconcile">执行</el-button>
      </template>
    </el-dialog>

    <!-- 对账单导出弹窗 -->
    <el-dialog v-model="statementDialog" title="客户对账单导出" width="520px" destroy-on-close>
      <el-form :model="statementForm" label-width="100px">
        <el-form-item label="客户" required>
          <el-select v-model="statementForm.customerId" placeholder="选择客户" filterable style="width:100%">
            <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
          </el-select>
        </el-form-item>
        <el-form-item label="起始日期" required>
          <el-date-picker v-model="statementForm.dateFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="statementForm.dateTo" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <div class="tip">导出含：客户/周期 + 期初未收 + 本期蓝字/红字 + 本期收款 + 期末未收 + 明细列表</div>
      </el-form>
      <template #footer>
        <el-button @click="statementDialog = false">取消</el-button>
        <el-button type="primary" :loading="stmtExporting" @click="onExportStatement">导出 Excel</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus, Download, Check, Document } from '@element-plus/icons-vue'
import { salArReceivableApi, salCustomerApi, materialApi, type SalArReceivableListQ } from '@/api'
import type { SalArReceivable, SalArReceivableLine, SalArReceiptPlan, SalCustomer, PdmMaterial } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'SalArReceivablePage' })

const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function fmt(v: any) { return v == null || v === '' ? '-' : '¥' + Number(v).toFixed(2) }

const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '待对账', t: 'info' },
  1: { l: '对账中', t: 'warning' },
  2: { l: '对账完成', t: 'primary' },
  3: { l: '部分收', t: 'warning' },
  4: { l: '收款完成', t: 'success' },
  9: { l: '作废', t: 'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v, m]) => ({ v: Number(v), l: m.l }))
const statusMap = STATUS_LABEL

const PLAN_STATUS: Record<number, { l: string; t?: string }> = {
  0: { l: '待收', t: 'info' }, 1: { l: '部分收', t: 'warning' },
  2: { l: '已收清', t: 'success' }, 3: { l: '收款中', t: 'primary' }, 9: { l: '作废', t: 'info' }
}
const planStatusMap = PLAN_STATUS

const ACTIONS = [
  { key: 'submitReconcile', label: '提交对账', type: 'primary', permission: 'sal:arreceivable:reconcile',
    allowFrom: [0], disabledReason: (s: number) => `仅「待对账」可提交（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'confirmReconcile', label: '确认对账', type: 'success', permission: 'sal:arreceivable:reconcile',
    allowFrom: [1], disabledReason: (s: number) => `仅「对账中」可确认（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'undoReconcile', label: '撤销对账', type: 'warning', permission: 'sal:arreceivable:reconcile',
    allowFrom: [2], disabledReason: (s: number) => `仅「对账完成」可撤销（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'generatePlans', label: '生成收款计划', type: 'default', permission: 'sal:arreceivable:edit',
    allowFrom: [0, 1, 2], disabledReason: (s: number) => `仅「未进入收款流程」可生成计划（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'cancel', label: '作废', type: 'danger', permission: 'sal:arreceivable:cancel',
    allowFrom: [0, 1, 2], disabledReason: (s: number) => `仅「未进入收款流程」可作废（当前:${STATUS_LABEL[s]?.l ?? s}）` }
]
function actions(row: SalArReceivable) {
  const s = Number(row.status)
  return ACTIONS.map(a => {
    const enabled = a.allowFrom.includes(s)
    return {
      ...a,
      disabled: !enabled,
      disabledReason: enabled ? '' : a.disabledReason(s),
      onClick: async (r: SalArReceivable) => {
        if (a.key === 'generatePlans') { onViewPlans(r); return }
        if (a.key === 'submitReconcile') { openSubmitDialog(r); return }
        if (a.key === 'confirmReconcile') { openConfirmDialog(r); return }
        if (a.key === 'undoReconcile') {
          try { await ElMessageBox.confirm(`确认撤销对账 应收单 ${r.receivableNo} ? 已发生收款的不可撤销。`, '操作确认', { type: 'warning' }) } catch { return }
          try {
            const resp = (await salArReceivableApi.undoReconcile(r.receivableId!)) as any
            if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
            ElMessage.success('撤销对账成功'); await loadList()
          } catch (e: any) { ElMessage.error(e?.message || '撤销对账失败') }
          return
        }
        // cancel
        try { await ElMessageBox.confirm(`确认作废应收单 ${r.receivableNo} ?`, '操作确认', { type: 'warning' }) } catch { return }
        try {
          const resp = (await salArReceivableApi.cancel(r.receivableId!)) as any
          if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          ElMessage.success('作废成功'); await loadList()
        } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
      }
    }
  })
}

function canEdit(row: SalArReceivable)   { return [0, 1].includes(Number(row.status)) }
function canRemove(row: SalArReceivable) { return Number(row.status) === 0 }

const query = reactive<SalArReceivableListQ>({
  pageNum: 1, pageSize: 20, receivableNo: '', customerId: undefined, customerName: '',
  bizDate: '', dueDate: '', status: undefined, isOverdue: undefined
})
const list = ref<SalArReceivable[]>([])
const total = ref(0)
const loading = ref(false)
const customers = ref<SalCustomer[]>([])
const materials = ref<PdmMaterial[]>([])

async function loadCustomers() {
  try {
    const resp = (await salCustomerApi.options()) as any
    customers.value = resp?.data || resp || []
  } catch (e: any) { ElMessage.error(e?.message || '加载客户失败') }
}
async function loadMaterials() {
  try {
    const resp = (await materialApi.list({ pageNum: 1, pageSize: 1000 })) as any
    materials.value = (resp as any)?.rows || []
  } catch (e: any) { ElMessage.error(e?.message || '加载物料失败') }
}
function onCustomerChange() {
  const c = customers.value.find(x => x.customerId === form.customerId)
  if (c) form.customerName = c.customerName
}
function onMaterialChange() {
  const m = materials.value.find(x => x.materialId === lineForm.materialId)
  if (m) {
    lineForm.materialCode = m.materialCode
    lineForm.materialName = m.materialName
    lineForm.specification = m.specification || ''
    lineForm.unit = m.unit || 'PCS'
  }
}

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await salArReceivableApi.list({ ...query })) as any
    list.value = resp?.rows || []; total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, { receivableNo: '', customerId: undefined, customerName: '', bizDate: '', dueDate: '', status: undefined, isOverdue: undefined, pageNum: 1 })
  loadList(1)
}

// 主单弹窗
const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => form.receivableId ? (readonly.value ? '应收单详情' : '编辑应收单') : '新增应收单')
const form = reactive<Partial<SalArReceivable>>({
  receivableNo: '', bizType: 'MANUAL', customerId: undefined as any, customerName: '',
  currency: 'CNY', exchangeRate: 1, bizDate: '', creditDays: 0, dueDate: '',
  undiscAmount: 0, discountAmount: 0, taxAmount: 0, totalAmount: 0, cnyTotalAmount: 0,
  receivedAmount: 0, unreceivedAmount: 0, remark: ''
})
function formDefaults() {
  Object.assign(form, {
    receivableId: undefined, receivableNo: '', bizType: 'MANUAL', customerId: undefined as any, customerName: '',
    currency: 'CNY', exchangeRate: 1, bizDate: '', creditDays: 0, dueDate: '',
    undiscAmount: 0, discountAmount: 0, taxAmount: 0, totalAmount: 0, cnyTotalAmount: 0,
    receivedAmount: 0, unreceivedAmount: 0, remark: ''
  })
}
function openDialog(row?: SalArReceivable) {
  formDefaults()
  if (row?.receivableId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: SalArReceivable) {
  formDefaults()
  try {
    const detail = (await salArReceivableApi.get(row.receivableId!)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.receivableId) { await salArReceivableApi.edit(form as any); ElMessage.success('修改成功') }
    else { await salArReceivableApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: SalArReceivable) {
  try { await salArReceivableApi.remove(row.receivableId!); ElMessage.success('删除成功'); await loadList() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
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
    const resp = await salArReceivableApi.export({ ...query, pageNum: 1, pageSize: 10000 })
    saveBlob(resp.data, resp, `应收单_${Date.now()}.xlsx`)
    ElMessage.success('导出成功')
  } catch (e: any) { ElMessage.error(e?.message || '导出失败') }
  finally { exporting.value = false }
}

// 明细弹窗
const linesDialog = ref(false)
const linesOwner = ref<SalArReceivable | null>(null)
const lines = ref<SalArReceivableLine[]>([])
const linesLoading = ref(false)
const linesTitle = computed(() => `应收行 ${linesOwner.value?.receivableNo ? '- ' + linesOwner.value.receivableNo : ''}`)
async function onViewLines(row: SalArReceivable) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    const resp = (await salArReceivableApi.lines(row.receivableId!)) as any
    lines.value = (resp?.rows || []).slice().sort((a: SalArReceivableLine, b: SalArReceivableLine) => (a.lineNo || 0) - (b.lineNo || 0))
  } catch (e: any) { ElMessage.error(e?.message || '加载明细失败') }
  finally { linesLoading.value = false }
}

const lineDialog = ref(false)
const lineSubmitLoading = ref(false)
const lineFormRef = ref<FormInstance | null>(null)
const lineEditingId = ref<number | null>(null)
const taxPercent = ref<number>(13)
const lineForm = reactive<Partial<SalArReceivableLine>>({
  receivableId: undefined, lineSrc: 'MANUAL', materialId: 1, materialCode: '', materialName: '',
  specification: '', unit: 'PCS', qty: 0, unitPrice: 0, taxRate: 0.13,
  undiscAmount: 0, taxAmount: 0, lineAmount: 0, batchNo: '', remark: ''
})
const lineRules = {
  materialId: [{ required: true, message: '物料ID必填', trigger: 'blur' }],
  qty: [{ required: true, validator: (_r: any, v: any, cb: any) => (v != null && Number(v) >= 0) ? cb() : cb(new Error('数量必填')), trigger: 'change' }]
} as any
const lineDialogTitle = computed(() => lineEditingId.value ? '编辑应收行' : '新增应收行')
function resetLineForm() {
  Object.assign(lineForm, {
    lineId: undefined, receivableId: linesOwner.value?.receivableId ?? undefined, lineSrc: 'MANUAL',
    materialId: 1, materialCode: '', materialName: '', specification: '', unit: 'PCS',
    qty: 0, unitPrice: 0, taxRate: 0.13, undiscAmount: 0, taxAmount: 0, lineAmount: 0,
    batchNo: '', remark: ''
  })
  taxPercent.value = 13
  lineEditingId.value = null
}
function openLineDialog(row?: SalArReceivableLine) {
  if (!linesOwner.value?.receivableId) { ElMessage.warning('缺少应收单ID'); return }
  resetLineForm()
  if (row) {
    Object.assign(lineForm, { ...row })
    lineEditingId.value = (row.lineId as number) ?? null
    taxPercent.value = Number(row.taxRate ?? 0.13) * 100
  }
  lineDialog.value = true
}
function onTaxChange() {
  lineForm.taxRate = Number((taxPercent.value / 100).toFixed(6))
  recalcLine()
}
function recalcLine() {
  const q = Number(lineForm.qty ?? 0); const p = Number(lineForm.unitPrice ?? 0); const t = Number(lineForm.taxRate ?? 0)
  const undisc = Number((q * p).toFixed(2))
  const tax = Number((undisc * t).toFixed(2))
  lineForm.undiscAmount = undisc
  lineForm.taxAmount = tax
  lineForm.lineAmount = Number((undisc + tax).toFixed(2))
}
async function onLineSubmit() {
  lineSubmitLoading.value = true
  try {
    await lineFormRef.value?.validate()
    recalcLine()
    const payload = { ...lineForm, receivableId: linesOwner.value!.receivableId! }
    if (lineEditingId.value) {
      await salArReceivableApi.editLine({ ...payload, lineId: lineEditingId.value } as any)
      ElMessage.success('修改成功')
    } else {
      await salArReceivableApi.addLine(payload as any)
      ElMessage.success('新增成功')
    }
    lineDialog.value = false
    await onViewLines(linesOwner.value!)
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { lineSubmitLoading.value = false }
}
async function onRemoveLine(row: SalArReceivableLine) {
  try { await ElMessageBox.confirm(`确定删除行号 ${row.lineNo} ?`, '提示', { type: 'warning' }) } catch { return }
  try { await salArReceivableApi.removeLine(row.lineId as number); ElMessage.success('删除成功'); await onViewLines(linesOwner.value!) }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// 计划弹窗
const plansDialog = ref(false)
const plansOwner = ref<SalArReceivable | null>(null)
const existingPlans = ref<SalArReceiptPlan[]>([])
const plansLoading = ref(false)
const planLoading = ref(false)
const phases = ref<any[]>([
  { phaseNo: 1, phaseName: '首款', ratio: 30, planDate: '' },
  { phaseNo: 2, phaseName: '尾款', ratio: 70, planDate: '' }
])
const ratioSum = computed(() => phases.value.reduce((s, p) => s + Number(p.ratio || 0), 0))
function recomputePhaseAmounts() {
  const total = Number(plansOwner.value?.totalAmount ?? 0)
  phases.value.forEach(p => {
    p._amount = Number(((Number(p.ratio || 0) / 100) * total).toFixed(2))
  })
}
function addPhaseRow() {
  phases.value.push({ phaseNo: phases.value.length + 1, phaseName: `第${phases.value.length + 1}期`, ratio: 0, planDate: '', _amount: 0 })
  recomputePhaseAmounts()
}
async function onViewPlans(row: SalArReceivable) {
  plansOwner.value = row; existingPlans.value = []; plansDialog.value = true; plansLoading.value = true
  try {
    const data = (await salArReceivableApi.plans(row.receivableId!)) as any
    existingPlans.value = (data?.data || data || []).slice()
    const defaultDue = row.dueDate || ''
    phases.value = existingPlans.value.length ? existingPlans.value.map((p, i) => ({
      phaseNo: p.phaseNo ?? (i + 1), phaseName: `第${i + 1}期`,
      ratio: Number(p.ratio ?? 0), planDate: p.planDate || defaultDue
    })) : [
      { phaseNo: 1, phaseName: '首款', ratio: 30, planDate: defaultDue },
      { phaseNo: 2, phaseName: '尾款', ratio: 70, planDate: defaultDue }
    ]
    recomputePhaseAmounts()
  } catch (e: any) { ElMessage.error(e?.message || '加载计划失败') }
  finally { plansLoading.value = false }
}
async function onGeneratePlans() {
  if (!plansOwner.value) return
  const sum = ratioSum.value
  if (Math.abs(sum - 100) > 0.01) { ElMessage.warning(`分期比例之和必须 = 100%（当前 ${sum.toFixed(2)}%）`); return }
  for (const p of phases.value) if (!p.planDate) { ElMessage.warning(`第${p.phaseNo ?? '-'}期计划日期必填`); return }
  planLoading.value = true
  try {
    const resp = (await salArReceivableApi.generatePlans(plansOwner.value.receivableId!, phases.value.map(p => ({
      phaseNo: Number(p.phaseNo), phaseName: p.phaseName, ratio: Number(p.ratio), planDate: p.planDate
    })))) as any
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '生成失败')
    ElMessage.success('生成成功')
    await onViewPlans(plansOwner.value); await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '生成失败') }
  finally { planLoading.value = false }
}

/* ================ 对账两级流程 + 反审 + 批量 + 对账单导出 ================ */
// 提交对账(0→1)
const submitReconcileDialog = ref(false)
const reconcileSubmitting = ref(false)
const submitReconcileForm = reactive<{ receivableId?: number; receivableNo?: string; signBy?: string; attachment?: string; remark?: string }>({})
function openSubmitDialog(row: SalArReceivable) {
  Object.assign(submitReconcileForm, { receivableId: row.receivableId, receivableNo: row.receivableNo, signBy: '', attachment: '', remark: '' })
  submitReconcileDialog.value = true
}
async function onSubmitReconcile() {
  if (!submitReconcileForm.receivableId) return
  reconcileSubmitting.value = true
  try {
    const resp = (await salArReceivableApi.submitReconcile(submitReconcileForm.receivableId, {
      signBy: submitReconcileForm.signBy, attachment: submitReconcileForm.attachment, remark: submitReconcileForm.remark
    })) as any
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    ElMessage.success('提交对账成功'); submitReconcileDialog.value = false; await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '提交对账失败') }
  finally { reconcileSubmitting.value = false }
}
// 确认对账(1→2)
const confirmReconcileDialog = ref(false)
const reconcileConfirming = ref(false)
const confirmReconcibleNo = ref('')
const confirmReconcileForm = reactive<{ receivableId?: number; signBy: string; signTime?: string; voucherId?: number }>({ signBy: '', signTime: '', voucherId: undefined })
function openConfirmDialog(row: SalArReceivable) {
  Object.assign(confirmReconcileForm, { receivableId: row.receivableId, signBy: '', signTime: '', voucherId: undefined })
  confirmReconcibleNo.value = row.receivableNo || ''
  confirmReconcileDialog.value = true
}
async function onConfirmReconcile() {
  if (!confirmReconcileForm.receivableId) return
  if (!confirmReconcileForm.signBy || !confirmReconcileForm.signBy.trim()) { ElMessage.warning('客户签字人必填'); return }
  reconcileConfirming.value = true
  try {
    const resp = (await salArReceivableApi.confirmReconcile(confirmReconcileForm.receivableId, {
      signBy: confirmReconcileForm.signBy,
      signTime: confirmReconcileForm.signTime || undefined,
      voucherId: confirmReconcileForm.voucherId
    })) as any
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    ElMessage.success('确认对账成功'); confirmReconcileDialog.value = false; await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '确认对账失败') }
  finally { reconcileConfirming.value = false }
}
// 批量对账(0→2)
const batchDialog = ref(false)
const batchSubmitting = ref(false)
const batchForm = reactive<{ customerId?: number; dateFrom: string; dateTo: string }>({ customerId: undefined, dateFrom: '', dateTo: '' })
function openBatchDialog() {
  Object.assign(batchForm, { customerId: query.customerId ?? undefined, dateFrom: '', dateTo: '' })
  batchDialog.value = true
}
async function onBatchReconcile() {
  if (!batchForm.customerId) { ElMessage.warning('客户必选'); return }
  if (!batchForm.dateFrom || !batchForm.dateTo) { ElMessage.warning('起止日期必填'); return }
  try { await ElMessageBox.confirm(`确认对所选客户的待对账应收单批量推进至「对账完成」?`, '批量对账', { type: 'warning' }) } catch { return }
  batchSubmitting.value = true
  try {
    const resp = (await salArReceivableApi.batchReconcile({
      customerId: batchForm.customerId, dateFrom: batchForm.dateFrom, dateTo: batchForm.dateTo
    })) as any
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    ElMessage.success(`批量对账完成，共推进 ${resp?.data ?? 0} 条`); batchDialog.value = false; await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '批量对账失败') }
  finally { batchSubmitting.value = false }
}
// 对账单导出
const statementDialog = ref(false)
const stmtExporting = ref(false)
const statementForm = reactive<{ customerId?: number; dateFrom: string; dateTo: string }>({ customerId: undefined, dateFrom: '', dateTo: '' })
function openStatementDialog() {
  Object.assign(statementForm, { customerId: query.customerId ?? undefined, dateFrom: '', dateTo: '' })
  statementDialog.value = true
}
async function onExportStatement() {
  if (!statementForm.customerId) { ElMessage.warning('客户必选'); return }
  if (!statementForm.dateFrom || !statementForm.dateTo) { ElMessage.warning('起止日期必填'); return }
  stmtExporting.value = true
  try {
    const resp = await salArReceivableApi.exportStatement(statementForm.customerId, statementForm.dateFrom, statementForm.dateTo)
    saveBlob(resp.data, resp, `客户对账单_${statementForm.customerId}_${statementForm.dateFrom}_${statementForm.dateTo}.xlsx`)
    ElMessage.success('导出成功'); statementDialog.value = false
  } catch (e: any) { ElMessage.error(e?.message || '导出失败') }
  finally { stmtExporting.value = false }
}

onMounted(async () => {
  await Promise.all([loadCustomers(), loadMaterials()])
  await loadList()
})
</script>

<style scoped>
.page-sal-ar-receivable { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.overdue { color: var(--el-color-danger); font-weight: 600; }
.lines-header { margin-bottom: 4px; }
.tip { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
