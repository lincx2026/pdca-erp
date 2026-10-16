<template>
  <div class="pdm-material-page">
    <el-row :gutter="16">
      <!-- 左: 分类树筛选 -->
      <el-col :span="5">
        <el-card shadow="never" class="dept-tree-card">
          <template #header>
            <div class="card-header">
              <span>分类筛选</span>
              <el-button size="small" text @click="treeQ = ''; flatTree = allFlat">重置</el-button>
            </div>
          </template>
          <el-input v-model="treeQ" placeholder="搜索分类" clearable size="small" style="margin-bottom: 8px" :prefix-icon="Search" />
          <el-tree
            ref="treeRef"
            :data="flatTree"
            :props="{ label: 'categoryName', value: 'categoryId', children: 'children' }"
            node-key="categoryId"
            :expand-on-click-node="false"
            :default-expand-all="true"
            :highlight-current="true"
            @node-click="onPickCategory"
          >
            <template #default="{ data }">
              <span class="cat-node">
                <el-tag type="info" effect="plain" size="small">{{ data.categoryCode }}</el-tag>
                <span>{{ data.categoryName }}</span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右: 查询条件 + 表格 -->
      <el-col :span="19">
        <el-card shadow="never">
          <el-form :inline="true" :model="q" @submit.prevent>
            <el-form-item label="物料编码">
              <el-input v-model="q.materialCode" placeholder="如 RES-26-0001" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item label="物料名称">
              <el-input v-model="q.materialName" placeholder="模糊匹配" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="q.materialType" placeholder="全部" clearable style="width: 140px">
                <el-option v-for="t in MATERIAL_TYPES" :key="t.val" :label="t.label" :value="t.val" />
              </el-select>
            </el-form-item>
            <el-form-item label="MPN">
              <el-input v-model="q.mpn" placeholder="原厂料号" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="q.status" placeholder="全部" clearable style="width: 110px">
                <el-option label="草稿" :value="0" />
                <el-option label="审核中" :value="1" />
                <el-option label="已启用" :value="2" />
                <el-option label="已失效" :value="3" />
                <el-option label="已驳回" :value="4" />
              </el-select>
            </el-form-item>
            <el-form-item label="采购归属">
              <el-select v-model="q.procureScope" placeholder="全部" clearable style="width: 150px">
                <el-option v-for="s in PROCURE_SCOPES" :key="s.value" :label="s.label" :value="s.value">
                  <span style="display:inline-flex;align-items:center;gap:8px">
                    <el-tag size="small" :type="s.tagType" effect="light">{{ s.labelShort || s.label.split(/采购|成品/)[0] || s.value }}</el-tag>
                    <span style="font-size:12px;color:#909399">{{ s.desc }}</span>
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="pager.pageNum = 1; loadList()" :icon="Search">搜索</el-button>
              <el-button @click="resetQ" :icon="RefreshRight">重置</el-button>
            </el-form-item>
            <el-form-item style="margin-left: auto">
              <el-button :icon="Document" @click="downloadTpl" v-if="canImport">下载模板</el-button>
              <el-button type="warning" :icon="UploadFilled" @click="importOpen = true" v-if="canImport">导入物料</el-button>
              <el-button type="primary" plain :icon="Download" @click="doExport" v-if="canExport">导出</el-button>
              <el-button type="success" @click="openAdd()" :icon="Plus" v-if="canAdd">新增物料</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="mt-16">
          <el-table v-loading="loading" :data="rows" border stripe>
            <el-table-column prop="materialCode" label="物料编码" width="170">
              <template #default="{ row }"><b>{{ row.materialCode }}</b></template>
            </el-table-column>
            <el-table-column prop="materialName" label="物料名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="specification" label="规格型号" min-width="180" show-overflow-tooltip />
            <el-table-column label="分类" width="140">
              <template #default="{ row }">
                <el-tag size="small" v-if="row.categoryCode">{{ row.categoryCode }}</el-tag>
                <span v-if="row.categoryName" style="margin-left:4px">{{ row.categoryName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="采购归属" width="120" align="center">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  effect="light"
                  :type="(PROCURE_SCOPES.find(s=>s.value===row.procureScope)?.tagType as any) || 'info'">
                  {{ procureScopeLabel(row.procureScope) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column prop="brand" label="品牌" width="100" show-overflow-tooltip />
            <el-table-column prop="mpn" label="MPN" width="140" show-overflow-tooltip />
            <el-table-column prop="abcClass" label="ABC" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.abcClass === 'A'" type="danger" size="small">A</el-tag>
                <el-tag v-else-if="row.abcClass === 'B'" type="warning" size="small">B</el-tag>
                <el-tag v-else type="info" size="small">C</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.status === 0" type="info" size="small">草稿</el-tag>
                <el-tag v-else-if="row.status === 1" type="warning" size="small">审核中</el-tag>
                <el-tag v-else-if="row.status === 2" type="success" size="small">已启用</el-tag>
                <el-tag v-else-if="row.status === 3" type="danger" size="small">已失效</el-tag>
                <el-tag v-else type="danger" effect="dark" size="small">已驳回</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="360" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="openView(row)" v-if="canQuery">详情</el-button>
                <el-button link size="small" type="primary" @click="openEdit(row)"
                           v-if="canEdit"
                           :disabled="row.status === 2" :title="row.status === 2 ? '已启用请先停用再修改' : ''">修改</el-button>
                <el-button link size="small" type="warning" @click="submitAudit(row)"
                           v-if="canAudit"
                           :disabled="row.status !== 0 && row.status !== 4">提交审核</el-button>
                <el-button link size="small" type="success" @click="auditPass(row)"
                           v-if="canAudit"
                           :disabled="row.status !== 1">通过</el-button>
                <el-button link size="small" type="danger" @click="remove(row)" v-if="canRemove">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next, jumper"
              :total="pager.total"
              v-model:current-page="pager.pageNum"
              v-model:page-size="pager.pageSize"
              :page-sizes="[20, 50, 100, 200]"
              @size-change="loadList"
              @current-change="loadList"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/修改物料对话框 (30字段用 tabs 分组) -->
    <el-dialog v-model="dlg.visible" :title="dlg.title" width="880px" destroy-on-close top="5vh" class="material-dlg">
      <el-tabs v-model="dlg.tab" type="card" class="dlg-tabs">
        <!-- Tab 1: 基础信息 -->
        <el-tab-pane label="基础信息" name="base">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="分类" prop="categoryId" required>
                  <el-tree-select
                    v-model="form.categoryId"
                    :data="catTreeForDlg"
                    :props="{ label: 'categoryName', value: 'categoryId', children: 'children' }"
                    check-strictly
                    placeholder="请选择分类"
                    style="width: 100%"
                    @change="onDlgCategoryChange"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="采购归属" prop="procureScope" required>
                  <el-tooltip placement="top-start" :content="'决定物料归属哪条采购线、入哪个仓。系统会按分类自动推断，也可手改。'">
                    <el-select v-model="form.procureScope" placeholder="选择采购归属" style="width: 100%">
                      <el-option v-for="s in PROCURE_SCOPES" :key="s.value" :label="s.label" :value="s.value">
                        <div style="display:flex;align-items:center;gap:10px">
                          <el-tag size="small" :type="s.tagType" effect="light">{{ s.labelShort }}</el-tag>
                          <span style="font-size:12px;color:#909399">{{ s.desc }}</span>
                        </div>
                      </el-option>
                    </el-select>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="物料编码" prop="materialCode" required>
                  <el-input v-model="form.materialCode" placeholder="点击右侧按钮按规则生成" maxlength="50">
                    <template #append>
                      <el-button :icon="MagicStick" :disabled="!form.categoryId" @click="genCode" v-if="canAdd">生成编码</el-button>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="物料名称" prop="materialName">
                  <el-input v-model="form.materialName" maxlength="200" show-word-limit />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="物料类型">
                  <el-select v-model="form.materialType" placeholder="请选择" style="width: 100%">
                    <el-option v-for="t in MATERIAL_TYPES" :key="t.val" :label="t.label + ' (' + t.val + ')'" :value="t.val" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="规格型号" prop="specification" required>
                  <el-input v-model="form.specification" maxlength="500" show-word-limit placeholder="如: 0402 1KΩ ±1% 1/16W；要求完整可追溯的规格描述，用于 MPN + 品牌唯一识别" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="计量单位">
                  <el-input v-model="form.unit" placeholder="PCS" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="品牌/制造商" prop="brand" required>
                  <el-input v-model="form.brand" maxlength="100" placeholder="如 YAGEO / TDK / 国巨 / 立隆" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="原厂料号 MPN" prop="mpn" required>
                  <el-input v-model="form.mpn" maxlength="100" placeholder="原厂 Catalog No，例 RC0402FR-071KL" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="RoHS合规">
                  <el-radio-group v-model="form.rohsCompliant">
                    <el-radio :value="1">合规</el-radio>
                    <el-radio :value="0">不合规</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="存储条件">
                  <el-select v-model="form.storageCondition" placeholder="常温" clearable style="width: 100%">
                    <el-option v-for="s in STORAGE_CONDITIONS" :key="s" :label="s" :value="s" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="有效期天数">
                  <el-input-number v-model="form.validDays" :min="0" :max="9999" controls-position="right" placeholder="空=无限期" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: 分类/计划/供应商 -->
        <el-tab-pane label="分类与计划" name="plan">
          <el-form :model="form" label-width="140px">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="ABC分类">
                  <el-radio-group v-model="form.abcClass">
                    <el-radio value="A">A 高价值</el-radio>
                    <el-radio value="B">B 中</el-radio>
                    <el-radio value="C">C 低</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="人工覆盖">
                  <el-switch v-model="form.abcManualFlag" :active-value="1" :inactive-value="0" />
                  <span class="hint ml-8">打开=不自动重算</span>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="序列号管理">
                  <el-switch v-model="form.serialTracked" :active-value="1" :inactive-value="0" />
                  <span class="hint ml-8">每片独立 SN</span>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="安全库存最小">
                  <el-input-number v-model="form.safetyStockMin" :min="0" :precision="4" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="安全库存最大">
                  <el-input-number v-model="form.safetyStockMax" :min="0" :precision="4" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="标准损耗率">
                  <el-input-number v-model="form.lossRate" :min="0" :max="0.5" :step="0.01" :precision="4" controls-position="right" style="width: 100%" />
                  <span class="hint">0 ~ 0.5 (0~50%)</span>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="采购提前期(天)">
                  <el-input-number v-model="form.leadTimeDays" :min="0" :max="365" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="最小订货量">
                  <el-input-number v-model="form.minOrderQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="最小包装量">
                  <el-input-number v-model="form.minPackageQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="默认供应商1">
                  <el-select v-model="form.defaultSupplier1" placeholder="W3.5 供应商" disabled style="width: 100%">
                    <el-option label="(阶段二上线)" :value="0" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="默认供应商2">
                  <el-select v-model="form.defaultSupplier2" placeholder="阶段二" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="默认供应商3">
                  <el-select v-model="form.defaultSupplier3" placeholder="阶段二" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

        <!-- Tab 3: 状态 & 备注 -->
        <el-tab-pane label="状态与备注" name="status">
          <el-form :model="form" label-width="120px">
            <el-alert type="info" :closable="false" show-icon class="mb-16">
              状态流转: 草稿 → 审核中 → 已启用 / 已驳回 → (驳回可再次提交) → 已启用可点击 停用 变为 已失效
            </el-alert>
            <el-descriptions :column="2" border size="default" class="mb-16">
              <el-descriptions-item label="当前状态">
                <el-tag v-if="form.status === 0" type="info">草稿</el-tag>
                <el-tag v-else-if="form.status === 1" type="warning">审核中</el-tag>
                <el-tag v-else-if="form.status === 2" type="success">已启用</el-tag>
                <el-tag v-else-if="form.status === 3" type="danger">已失效</el-tag>
                <el-tag v-else type="danger" effect="dark">已驳回</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="审核人/时间">
                {{ form.auditBy || '-' }}<span v-if="form.auditTime"> / {{ form.auditTime }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="驳回原因" :span="2">
                <span v-if="form.rejectReason" style="color: var(--el-color-danger)">{{ form.rejectReason }}</span>
                <span v-else>-</span>
              </el-descriptions-item>
            </el-descriptions>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="5" maxlength="1000" show-word-limit />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 4: 供应商阶梯价 -->
        <el-tab-pane label="供应商报价" name="supplier">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
            <el-alert type="info" :closable="false" show-icon
              title="默认价格(★)会被采购下单时自动带入；优先序1为首选，缺货再依次切换。" />
            <div>
              <el-button size="small" type="primary" plain :icon="Plus" @click="addSupplierRow"
                v-hasPermi="'pdm:supplier:edit'" :disabled="dlg.viewOnly || !form.materialId && !dlg.isAdd">新增供应商</el-button>
            </div>
          </div>
          <el-table :data="supplierRows" border size="small" max-height="350">
            <el-table-column label="优先序" width="94" align="center">
              <template #default="{ row, $index }">
                <div style="display:flex;align-items:center;justify-content:center;gap:4px">
                  <el-input-number v-model="row.priority" size="small" :min="1" :max="9" controls-position="right" style="width:68px" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="★默认" width="60" align="center">
              <template #default="{ row }">
                <el-radio v-model="supplierDefaultIdx" :label="row.__idx" @change="supplierRows.forEach((r,i)=>r.isDefault=(i===supplierDefaultIdx?1:0))">
                  <span v-if="supplierDefaultIdx === row.__idx" style="color:#f56c6c">★</span>
                </el-radio>
              </template>
            </el-table-column>
            <el-table-column label="供应商" min-width="240">
              <template #default="{ row }">
                <div style="display:flex;gap:4px;align-items:center">
                  <el-select size="small" v-model="row.supplierId" placeholder="选择供应商" filterable style="width:160px"
                    @change="(v:any)=>fillSupplierName(row, v)">
                    <el-option v-for="s in supplierOptions" :key="s.supplierId"
                      :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
                      <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
                      <span style="float:right;color:#999;font-size:12px">{{ s.contact }} {{ s.phone }}</span>
                    </el-option>
                  </el-select>
                  <el-input size="small" v-model="row.supplierName" placeholder="供应商名称" style="flex:1" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="供应商料号(SPN)" width="140">
              <template #default="{ row }"><el-input size="small" v-model="row.supplierPn" placeholder="料号" /></template>
            </el-table-column>
            <el-table-column label="品牌/MPN" width="150">
              <template #default="{ row }">
                <el-input size="small" v-model="row.brand" placeholder="品牌" style="width:49%;margin-right:2px"/>
                <el-input size="small" v-model="row.mpn" placeholder="MPN" style="width:49%"/>
              </template>
            </el-table-column>
            <el-table-column label="MOQ/MPQ" width="160">
              <template #default="{ row }">
                <el-input-number size="small" :min="0" v-model="row.moq" :step="1" :controls="false" placeholder="MOQ" style="width:47%;margin-right:6px"/>
                <el-input-number size="small" :min="0" v-model="row.mpq" :step="1" :controls="false" placeholder="MPQ" style="width:47%"/>
              </template>
            </el-table-column>
            <el-table-column label="交期(天)" width="100">
              <template #default="{ row }">
                <el-input-number size="small" :min="0" :max="365" v-model="row.leadTimeDays" :controls="false" style="width:100%"/>
              </template>
            </el-table-column>
            <el-table-column label="阶梯1(≥Q)" width="140" align="center">
              <template #default="{ row }">
                <div style="display:flex;gap:2px">
                  <el-input-number size="small" :min="0" v-model="row.tier1Qty" :precision="4" :controls="false" placeholder="数量" style="width:48%"/>
                  <el-input-number size="small" :min="0" v-model="row.tier1Price" :precision="6" :controls="false" placeholder="单价" style="width:48%"/>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="阶梯2(≥Q)" width="140" align="center">
              <template #default="{ row }">
                <div style="display:flex;gap:2px">
                  <el-input-number size="small" :min="0" v-model="row.tier2Qty" :precision="4" :controls="false" placeholder="≥数量" style="width:48%"/>
                  <el-input-number size="small" :min="0" v-model="row.tier2Price" :precision="6" :controls="false" placeholder="单价" style="width:48%"/>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="阶梯3(≥Q)" width="140" align="center">
              <template #default="{ row }">
                <div style="display:flex;gap:2px">
                  <el-input-number size="small" :min="0" v-model="row.tier3Qty" :precision="4" :controls="false" placeholder="≥数量" style="width:48%"/>
                  <el-input-number size="small" :min="0" v-model="row.tier3Price" :precision="6" :controls="false" placeholder="单价" style="width:48%"/>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="币种/税率" width="150">
              <template #default="{ row }">
                <el-select size="small" v-model="row.currency" style="width:62px">
                  <el-option label="CNY" value="CNY" /><el-option label="USD" value="USD" />
                  <el-option label="EUR" value="EUR" /><el-option label="HKD" value="HKD" />
                  <el-option label="JPY" value="JPY" />
                </el-select>
                <el-input-number size="small" v-model="row.taxRate" :min="0" :max="1" :step="0.01" :precision="4" :controls="false" style="width:76px;margin-left:4px"/>
              </template>
            </el-table-column>
            <el-table-column label="有效期" width="230">
              <template #default="{ row }">
                <el-date-picker size="small" v-model="row.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="起" style="width:46%"/>
                <span style="margin:0 4px">~</span>
                <el-date-picker size="small" v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="止(空=长期)" style="width:46%"/>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-select size="small" v-model="row.status" style="width:100%">
                  <el-option label="草稿" :value="0" />
                  <el-option label="生效" :value="1" />
                  <el-option label="停用" :value="2" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" fixed="right" align="center">
              <template #default="{ $index }">
                <el-button size="small" link type="danger" v-hasPermi="'pdm:supplier:edit'" :disabled="dlg.viewOnly"
                  @click="supplierRows.splice($index,1); fixSupplierIdx()">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="color:#909399;font-size:12px;margin-top:6px">
            提示：首次保存物料时，供应商报价会随物料一同提交；若仅新增物料不想填写可跳过。
          </div>
        </el-tab-pane>

        <!-- Tab 5: 替代料 -->
        <el-tab-pane label="替代料" name="alt">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
            <div>
              <el-alert type="warning" :closable="false" show-icon style="max-width:700px"
                :title="null">
                <template #default>
                  <span>替代料审批流：<b>草稿 → 提交 → 审批 → 生效(=2)</b>，MRP/缺料分析只会使用状态=2的关系。单向(A→B)=仅主料缺料时切替代；互替=A/B任意一方缺料都可切换。</span>
                </template>
              </el-alert>
            </div>
            <div>
              <el-tag style="margin-right:8px" v-if="altRowsGroupId">组号：{{ altRowsGroupId }}</el-tag>
              <el-button size="small" :icon="Refresh" plain @click="newAltGroup" :disabled="dlg.viewOnly || !form.materialId && !dlg.isAdd">新组号</el-button>
              <el-button size="small" type="primary" plain :icon="Plus" @click="addAltRow"
                :disabled="dlg.viewOnly || (!form.materialId && !dlg.isAdd)" v-hasPermi="'pdm:alt:edit'">新增替代料</el-button>
            </div>
          </div>
          <el-table :data="altRows" border size="small" max-height="320">
            <el-table-column label="优先级" width="88" align="center">
              <template #default="{ row }">
                <el-input-number size="small" v-model="row.priority" :min="1" :max="999" :controls="false" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="替代类型" width="152">
              <template #default="{ row }">
                <el-select size="small" v-model="row.altType" style="width:100%">
                  <el-option label="完全互替" :value="1" />
                  <el-option label="单向(主料→替代料)" :value="2" />
                  <el-option label="条件限定(BOM行)" :value="3" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="替代料 (Alt)" min-width="440">
              <template #default="{ row }">
                <div style="display:flex;gap:4px;align-items:center;width:100%">
                  <el-select size="small" v-model="row.altMaterialId" placeholder="输入编码/名称搜索" filterable
                    style="width:200px" @change="(v:any)=>fillAltMaterial(row, v)">
                    <el-option v-for="m in materialOptions" :key="m.materialId"
                      :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                      <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                      <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                    </el-option>
                  </el-select>
                  <el-input size="small" v-model="row.altMaterialCode" placeholder="编码(只读展示)" disabled style="width:150px" />
                  <el-input size="small" v-model="row.altMaterialName" placeholder="名称(只读展示)" disabled style="flex:1" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="替代比例" width="130">
              <template #default="{ row }">
                <div style="display:flex;align-items:center">
                  <el-input-number size="small" v-model="row.subRate" :min="0.0001" :precision="6" :step="0.1" :controls="false" style="width:100%" />
                  <span style="font-size:12px;color:#909399;margin-left:2px">主料1 = 替代料X</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="有效期" width="230">
              <template #default="{ row }">
                <el-date-picker size="small" v-model="row.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="起" style="width:46%"/>
                <span style="margin:0 4px">~</span>
                <el-date-picker size="small" v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="止" style="width:46%"/>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.status===0" type="info">草稿</el-tag>
                <el-tag v-else-if="row.status===1" type="warning">审批中</el-tag>
                <el-tag v-else-if="row.status===2" type="success">生效</el-tag>
                <el-tag v-else-if="row.status===3" type="info">停用</el-tag>
                <el-tag v-else type="danger" effect="dark">驳回</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="驳回原因" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.status===4 && row.rejectReason" style="color:#f56c6c">{{ row.rejectReason }}</span>
                <el-input size="small" v-model="row.remark" placeholder="备注" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" fixed="right" align="center">
              <template #default="{ $index }">
                <el-button size="small" link type="danger" v-hasPermi="'pdm:alt:edit'" :disabled="dlg.viewOnly"
                  @click="altRows.splice($index,1)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button :disabled="dlg.viewOnly" type="primary" :loading="submitting" @click="submit" v-if="!dlg.viewOnly">保存</el-button>
      </template>
    </el-dialog>

    <!-- ================= W3.2 导入对话框 ================= -->
    <el-dialog v-model="importOpen" title="导入物料主档" width="860px" top="6vh" destroy-on-close>
      <div class="import-desc">
        <el-alert type="info" :closable="false" show-icon :title="null">
          <template #default>
            <ol style="margin:0;padding-left:20px;line-height:1.9">
              <li>请先 <b>下载模板</b>，按列填写数据，必填列带 <span style="color:#f56c6c">*</span> 标识</li>
              <li>分类/类型/存储/ABC/单位/状态 均支持下拉选择，请勿修改表头行与示例行</li>
              <li>物料编码若已存在：开启「更新支持」=覆盖；否则该行失败</li>
              <li>单次最多 20000 条数据；处理完所有行后在下方查看失败明细（含行号+原因）</li>
            </ol>
          </template>
        </el-alert>
      </div>

      <el-form :model="{}" label-width="110px" class="mt-12">
        <el-form-item label="选择文件">
          <el-upload
            ref="importUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :before-upload="beforeImportUpload"
            :on-change="(f:any)=>beforeImportUpload(f as any)"
            drag>
            <el-icon class="el-icon--upload"><upload /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择 .xlsx/.xls</em></div>
            <template #tip>
              <div class="el-upload__tip" style="color:#909399;font-size:12px">
                单文件 ≤ 10MB，行数 ≤ 20000
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="更新支持">
          <el-switch v-model="importUpdateSupport"
                     active-text="编码存在时更新覆盖（草稿/驳回才允许）"
                     inactive-text="编码存在时报错（仅新增模式）" />
        </el-form-item>
        <el-form-item v-if="importLoading" label="上传进度">
          <el-progress style="width:100%" :percentage="importProgress" :stroke-width="14" />
        </el-form-item>
      </el-form>

      <!-- 结果卡片 -->
      <el-card v-if="importResult" shadow="never" class="mt-12 result-card">
        <template #header>
          <div style="display:flex;align-items:center;gap:12px">
            <b>导入结果</b>
            <el-tag type="success">共 {{ importResult.totalRows }} 行</el-tag>
            <el-tag type="success">✅ 成功 {{ importResult.successCount }}</el-tag>
            <el-tag type="danger">❌ 失败 {{ importResult.failCount }}</el-tag>
          </div>
        </template>
        <el-alert v-if="importResult.failCount > 0"
          type="error" :closable="false" show-icon style="margin-bottom:10px">
          <template #title>
            <span style="font-weight:600">共 {{ importResult.failCount }} 行导入失败，请根据下表修正 Excel 后重新上传。</span>
            <span style="color:#909399;font-weight:400;margin-left:8px">（可按「行号」在 Excel 中快速定位）</span>
          </template>
        </el-alert>
        <el-table v-if="importResult.failRows?.length" :data="importResult.failRows" size="small"
          border stripe max-height="320" style="width:100%" :scrollbar-always-on="true">
          <el-table-column prop="rowNum" label="行号" width="74" align="center" fixed="left">
            <template #default="{ row }">
              <span :style="{color:row.rowNum<0?'#909399':'#f56c6c',fontWeight:600}">
                {{ row.rowNum < 0 ? '系统' : row.rowNum }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="materialCode" label="物料编码" width="170" show-overflow-tooltip fixed="left" />
          <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="categoryName" label="分类" width="110" show-overflow-tooltip />
          <el-table-column prop="specification" label="规格型号" min-width="180" show-overflow-tooltip />
          <el-table-column prop="brand" label="品牌/制造商" min-width="130" show-overflow-tooltip />
          <el-table-column prop="mpn" label="原厂MPN" min-width="140" show-overflow-tooltip />
          <el-table-column prop="procureScopeLabel" label="采购归属" width="100" show-overflow-tooltip />
          <el-table-column prop="errorMsg" label="失败原因" min-width="280" fixed="right">
            <template #default="{ row }">
              <div style="color:#f56c6c;white-space:pre-wrap">{{ row.errorMsg }}</div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="所有行导入成功，棒！" :image-size="80" />
      </el-card>

      <template #footer>
        <el-button @click="importOpen = false">取消</el-button>
        <el-button :icon="Document" @click="downloadTpl()">下载模板</el-button>
        <el-button type="primary" :icon="UploadFilled" :loading="importLoading" :disabled="!importFile" @click="importSubmit">
          {{ importLoading ? `处理中 ${importProgress}%` : '开始导入' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch, computed, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshRight, Plus, MagicStick, Document, UploadFilled, Download, Upload, Refresh } from '@element-plus/icons-vue'
import { categoryApi, materialApi, codeGenApi, supplierApi, alternateApi, scmSupplierApi, type MaterialListQ, type MaterialImportResult } from '@/api'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'
import type { UploadFile } from 'element-plus'
import type { PdmMaterial, PdmMaterialCategory, PdmMaterialSupplier, PdmMaterialAlternate, ScmSupplier } from '@/types/models'

const route = useRoute()
// 根据路由 meta.scope 区分电子域/成品域 → 权限前缀不同
const SCOPE: 'elec' | 'fg' = (route.meta as any)?.scope === 'fg' ? 'fg' : 'elec'
const PERM_PREFIX = SCOPE === 'fg' ? 'pdm:fgmaterial:' : 'pdm:material:'
const userPerms = computed(() => useUserStore().userInfo?.permissions || [])
const canQuery  = computed(() => hasPerm(PERM_PREFIX + 'list',   userPerms.value) || hasPerm(PERM_PREFIX + 'query', userPerms.value))
const canAdd    = computed(() => hasPerm(PERM_PREFIX + 'add',    userPerms.value))
const canEdit   = computed(() => hasPerm(PERM_PREFIX + 'edit',   userPerms.value))
const canRemove = computed(() => hasPerm(PERM_PREFIX + 'remove', userPerms.value))
const canImport = computed(() => hasPerm(PERM_PREFIX + 'import', userPerms.value))
const canExport = computed(() => hasPerm(PERM_PREFIX + 'export', userPerms.value))
const canAudit  = computed(() => hasPerm(PERM_PREFIX + 'audit',  userPerms.value))

const MATERIAL_TYPES = [
  { label: 'PCB空板/裸板', val: 'PCB' },
  { label: '电阻',        val: 'RESISTOR' },
  { label: '电容',        val: 'CAPACITOR' },
  { label: '电感/磁珠',    val: 'INDUCTOR' },
  { label: '集成电路/芯片', val: 'IC' },
  { label: '连接器/接插件', val: 'CONNECTOR' },
  { label: '结构件/外壳',   val: 'STRUCT' },
  { label: 'PCB焊盘辅料',  val: 'PAD' },
  { label: '生产辅料',     val: 'ASSIST' },
  { label: '焊锡膏/锡线',  val: 'SOLDER' },
  { label: '胶类/固化剂',  val: 'GLUE' },
  { label: '标签/条码纸',  val: 'LABEL' },
  { label: '包装材料',     val: 'PACKAGE' },
  { label: '治具/测试架',  val: 'FIXTURE' },
  { label: '半成品',       val: 'WIP' },
  { label: '成品PCBA',     val: 'FINISHED' }
]
/* ==================== 采购归属 (Priority 1 新增, 贯穿 SCM 采购分工 + 入库自动路由) ==================== */
const PROCURE_SCOPES = [
  { value: 'ELEC', labelShort: '电子',  label: '电子物料采购', tagType: 'warning' as const,
    desc: 'SMT/PCBA 生产用料(阻容/IC/PCB/半成品), 进 WMS 电子仓' },
  { value: 'PACK', labelShort: '包辅',  label: '包材/辅料采购', tagType: 'info'    as const,
    desc: '辅料/包材/说明书/外箱/治具, 进 WMS 电子仓对应子仓' },
  { value: 'ACC',  labelShort: '配件',  label: '外购配件采购', tagType: 'success' as const,
    desc: '线材/电池/适配器等外购成品, 进「配件仓 1853」' },
  { value: 'SELF', labelShort: '自制',  label: '成品自制(不采购)', tagType: 'primary' as const,
    desc: '我们自己组装的整机机型/套料, 完工入「成品仓 1852」, 不触发采购' }
] as const
type ProcureScope = typeof PROCURE_SCOPES[number]['value']
function procureScopeLabel(v?: string) {
  return PROCURE_SCOPES.find(s => s.value === v)?.label || (v || '—')
}
/** 根据 scope 路径 + 分类名/分类 id, 智能推断默认的 procure_scope (仅新增时用, 老数据不动) */
function inferProcureScope(categoryId?: number | string): ProcureScope {
  const cat = findCategoryById(categoryId)
  const nm = (cat?.categoryName || '').trim()
  const scope = (cat?.materialScope || '').toUpperCase()
  // PCBA 产品开发: 辅料/包材类→PACK, 其余→ELEC
  if (scope === 'PCBA' || SCOPE === 'elec') {
    if (/辅料|包材|彩盒|包装|说明书|外箱|泡沫|焊锡|胶|标签|条码|治具|测试架/.test(nm)) return 'PACK'
    return 'ELEC'
  }
  // 成品产品开发
  if (/线材|电池|适配器|电源|充电器|外壳|配件|挂绳|保护膜|五金|塑胶|铭牌|结构件/.test(nm)) return 'ACC'
  if (/整机|机型|成品|套装|套料/.test(nm) || cat?.categoryCode?.toUpperCase().startsWith('FG')) return 'SELF'
  if (/结构件|塑胶件|冲压件|铭牌|胶/.test(nm)) return 'PACK'
  return 'ACC'
}
function findCategoryById(id?: number | string): PdmMaterialCategory | undefined {
  if (id == null) return undefined
  const idN = typeof id === 'string' ? parseInt(id, 10) : id
  const walk = (nodes: PdmMaterialCategory[]): PdmMaterialCategory | undefined => {
    for (const n of nodes) {
      if (n.categoryId === idN) return n
      if (n.children) {
        const r = walk(n.children as PdmMaterialCategory[])
        if (r) return r
      }
    }
    return undefined
  }
  return walk(allFlat.value) || walk(catTreeForDlg.value)
}
const STORAGE_CONDITIONS = ['常温', '防潮', '冷藏 ≤10℃', 'MSD-1', 'MSD-2', 'MSD-2a', 'MSD-3', 'MSD-4', 'MSD-5a', 'MSD-5', 'MSD-6']

// ============== 左分类树 ==============
const treeQ = ref('')
const allFlat = ref<PdmMaterialCategory[]>([])
const treeRef = ref<any>(null)
const flatTree = computed(() => {
  if (!treeQ.value) return allFlat.value
  const kw = treeQ.value.toLowerCase()
  // 简单过滤: 命中的节点保留, 父级要展开才能看到 -> 这里直接返回扁平过滤结果
  const walk = (list: PdmMaterialCategory[]): PdmMaterialCategory[] => {
    const result: PdmMaterialCategory[] = []
    for (const n of list) {
      const ok = (n.categoryName && n.categoryName.toLowerCase().includes(kw)) ||
                 (n.categoryCode && n.categoryCode.toLowerCase().includes(kw))
      const children = n.children ? walk(n.children) : []
      if (ok || children.length) {
        result.push({ ...n, children: children.length ? children : n.children })
      }
    }
    return result
  }
  return walk(allFlat.value)
})
function onPickCategory(data: PdmMaterialCategory) {
  q.categoryId = data.categoryId === q.categoryId ? undefined : data.categoryId
  pager.pageNum = 1
  loadList()
}

// ============== 查询分页 ==============
const loading = ref(false)
const rows = ref<PdmMaterial[]>([])
const q = reactive<MaterialListQ & { procureScope?: string }>({
  pageNum: 1, pageSize: 20,
  categoryId: undefined,
  materialCode: '', materialName: '', materialType: '', mpn: '',
  status: undefined,
  procureScope: undefined
})
const pager = reactive({ pageNum: 1, pageSize: 20, total: 0 })
watch([() => pager.pageNum, () => pager.pageSize], ([pn, ps]) => { q.pageNum = pn; q.pageSize = ps })
async function loadList() {
  loading.value = true
  try {
    const resp: any = await materialApi.list(q)
    rows.value = resp?.rows || []
    pager.total = resp?.total || 0
  } finally { loading.value = false }
}
function resetQ() {
  q.materialCode = ''
  q.materialName = ''
  q.materialType = ''
  q.mpn = ''
  q.status = undefined
  q.procureScope = undefined
  q.categoryId = undefined
  treeRef.value?.setCurrentKey(null)
  pager.pageNum = 1
  loadList()
}

// ============== 新增/修改对话框 ==============
const catTreeForDlg = ref<PdmMaterialCategory[]>([])
const formRef = ref<FormInstance>()
const submitting = ref(false)
const dlg = reactive<{ visible: boolean; isEdit: boolean; viewOnly: boolean; title: string; tab: string }>({
  visible: false, isEdit: false, viewOnly: false, title: '', tab: 'base'
})
const form = reactive<Partial<PdmMaterial> & { procureScope?: ProcureScope | '' }>({
  categoryId: undefined,
  procureScope: '',
  materialCode: '',
  materialName: '',
  specification: '',
  materialType: '',
  unit: 'PCS',
  brand: '',
  mpn: '',
  rohsCompliant: 1,
  storageCondition: '常温',
  validDays: undefined,
  abcClass: 'C',
  abcManualFlag: 0,
  safetyStockMin: 0,
  safetyStockMax: 0,
  leadTimeDays: 7,
  minOrderQty: 1,
  minPackageQty: 1,
  lossRate: 0,
  serialTracked: 0,
  status: 0,
  remark: ''
})
const rules: FormRules = {
  categoryId:   [{ required: true, message: '请选择分类', trigger: 'change' }],
  procureScope: [{ required: true, message: '请选择采购归属(决定入哪个仓+归属哪条采购线)', trigger: 'change' }],
  materialCode: [{ required: true, message: '请输入或生成物料编码', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  specification:[
    { required: true, message: '请输入规格型号(与MPN+品牌一起用于唯一定位原厂物料)', trigger: 'blur' },
    { min: 2, max: 500, message: '规格型号长度 2-500 字符', trigger: 'blur' }
  ],
  brand: [
    { required: true, message: '请输入品牌/制造商(例 YAGEO/TDK)', trigger: 'blur' },
    { min: 2, max: 100, message: '品牌长度 2-100 字符', trigger: 'blur' }
  ],
  mpn: [
    { required: true, message: '请输入原厂料号 MPN(原厂 Catalog No.)', trigger: 'blur' },
    { min: 2, max: 100, message: 'MPN 长度 2-100 字符', trigger: 'blur' }
  ]
}
function resetForm() {
  Object.keys(form).forEach(k => delete (form as any)[k])
  Object.assign(form, {
    categoryId: undefined, procureScope: '',
    materialCode: '', materialName: '', specification: '',
    materialType: '', unit: 'PCS', brand: '', mpn: '', rohsCompliant: 1,
    storageCondition: '常温', validDays: undefined, abcClass: 'C', abcManualFlag: 0,
    safetyStockMin: 0, safetyStockMax: 0, leadTimeDays: 7, minOrderQty: 1, minPackageQty: 1,
    lossRate: 0, serialTracked: 0, status: 0, remark: ''
  })
  // W3.3 重置供应商报价/替代料
  supplierRows.value.length = 0
  altRows.value.length = 0
  supplierDefaultIdx.value = -1
  altRowsGroupId.value = ''
}

/* ============ W3.3 供应商阶梯价 Tab4 ============ */
type SupplierExt = Partial<PdmMaterialSupplier> & { __idx?: number }
const supplierRows = ref<SupplierExt[]>([])
const supplierDefaultIdx = ref<number>(-1)

/* ============ 下拉选项 (供应商 + 物料) ============ */
const supplierOptions = ref<ScmSupplier[]>([])
const materialOptions = ref<PdmMaterial[]>([])
async function loadSupplierOptions() {
  try {
    const resp: any = await scmSupplierApi.options()
    supplierOptions.value = ((resp?.data ?? resp) || []) as ScmSupplier[]
  } catch { supplierOptions.value = [] }
}
async function loadMaterialOptions() {
  try {
    const resp: any = await materialApi.list({ pageNum: 1, pageSize: 1000 })
    materialOptions.value = (resp?.rows || []) as PdmMaterial[]
  } catch { materialOptions.value = [] }
}
function fillSupplierName(row: any, id: number) {
  if (!id) return
  const s = supplierOptions.value.find(x => x.supplierId === id)
  if (s) row.supplierName = s.supplierName
}
function fillAltMaterial(row: any, id: number) {
  if (!id) return
  const m: any = materialOptions.value.find(x => x.materialId === id)
  if (m) {
    row.altMaterialCode = m.materialCode
    row.altMaterialName = m.materialName
  }
}

function fixSupplierIdx() {
  supplierRows.value.forEach((r, i) => r.__idx = i)
  // 如果默认索引越界, 回退到-1
  if (!supplierRows.value.some((r, i) => r.isDefault === 1 && i === supplierDefaultIdx.value)) {
    const firstDefault = supplierRows.value.findIndex(r => r.isDefault === 1)
    supplierDefaultIdx.value = firstDefault
  }
}

function addSupplierRow() {
  const n: SupplierExt = {
    __idx: supplierRows.value.length,
    supplierId: 0 as any, supplierName: '',
    supplierPn: '', mpn: form.mpn || '', brand: form.brand || '',
    priority: supplierRows.value.length + 1,
    moq: 1, mpq: 1, leadTimeDays: 7,
    tier1Qty: 1, tier1Price: 0,
    currency: 'CNY', taxRate: 0.13,
    isDefault: supplierRows.value.length === 0 ? 1 : 0,
    status: 1
  }
  if (n.isDefault === 1) supplierDefaultIdx.value = n.__idx
  supplierRows.value.push(n)
}

/** 打开新增/编辑/详情时加载已有供应商报价 */
async function loadSuppliers(materialId: number) {
  try {
    const list = await supplierApi.list(materialId) as any as PdmMaterialSupplier[]
    supplierRows.value = list.map((r, i) => ({ ...r, __idx: i })) as any
    const dIdx = supplierRows.value.findIndex(r => r.isDefault === 1)
    supplierDefaultIdx.value = dIdx < 0 ? -1 : dIdx
  } catch {
    supplierRows.value = []
  }
}

/* ============ W3.3 替代料 Tab5 ============ */
type AltExt = Partial<PdmMaterialAlternate>
const altRows = ref<AltExt[]>([])
const altRowsGroupId = ref<string>('')

async function newAltGroup() {
  try {
    const g = await alternateApi.nextGroupId() as any
    altRowsGroupId.value = g
    altRows.value.forEach(a => (a as any).groupId = g)
    ElMessage.success('已生成新替代组号: ' + g)
  } catch {}
}

function addAltRow() {
  if (!altRowsGroupId.value) {
    ElMessage.warning('请先分配替代组号（点击「新组号」）或先保存物料后系统会自动生成')
  }
  const n: AltExt = {
    groupId: altRowsGroupId.value || '',
    altMaterialId: 0 as any,
    altMaterialCode: '', altMaterialName: '',
    altType: 1,
    priority: altRows.value.length + 1,
    subRate: 1,
    status: 0,
    remark: ''
  }
  altRows.value.push(n)
}

async function loadAlternates(mainMaterialId: number) {
  try {
    const list = await alternateApi.list({ mainMaterialId }) as any as PdmMaterialAlternate[]
    altRows.value = [...list] as any
    altRowsGroupId.value = list[0]?.groupId || ''
  } catch {
    altRows.value = []
    altRowsGroupId.value = ''
  }
}

async function openAdd() {
  resetForm()
  dlg.isEdit = false
  dlg.viewOnly = false
  dlg.title = '新增物料'
  dlg.tab = 'base'
  if (q.categoryId) {
    form.categoryId = q.categoryId
    await nextTick()
  }
  catTreeForDlg.value = await categoryApi.excludeTree(null) as any
  // 预加载供应商/物料下拉
  if (supplierOptions.value.length === 0) loadSupplierOptions()
  if (materialOptions.value.length === 0) loadMaterialOptions()
  dlg.visible = true
}

async function openEdit(row: PdmMaterial) {
  resetForm()
  const detail = await materialApi.get(row.materialId) as any
  Object.assign(form, detail)
  dlg.isEdit = true
  dlg.viewOnly = false
  dlg.title = '修改物料 - ' + detail.materialCode
  dlg.tab = 'base'
  catTreeForDlg.value = await categoryApi.excludeTree(null) as any
  // 预加载供应商/物料下拉
  if (supplierOptions.value.length === 0) loadSupplierOptions()
  if (materialOptions.value.length === 0) loadMaterialOptions()
  // W3.3: 异步加载供应商报价与替代料
  if (form.materialId) {
    await Promise.all([
      loadSuppliers(form.materialId).catch(() => {}),
      loadAlternates(form.materialId).catch(() => {})
    ])
  }
  dlg.visible = true
}
async function openView(row: PdmMaterial) {
  resetForm()
  const detail = await materialApi.get(row.materialId) as any
  Object.assign(form, detail)
  dlg.isEdit = false
  dlg.viewOnly = true
  dlg.title = '物料详情 - ' + detail.materialCode
  dlg.tab = 'base'
  catTreeForDlg.value = await categoryApi.excludeTree(null) as any
  // 预加载供应商/物料下拉
  if (supplierOptions.value.length === 0) loadSupplierOptions()
  if (materialOptions.value.length === 0) loadMaterialOptions()
  // W3.3: 异步加载供应商报价与替代料
  if (form.materialId) {
    await Promise.all([
      loadSuppliers(form.materialId).catch(() => {}),
      loadAlternates(form.materialId).catch(() => {})
    ])
  }
  dlg.visible = true
}

function onDlgCategoryChange(id: number | undefined) {
  if (!id) return
  // 保留已手填编码, 不清空
}

async function genCode() {
  if (!form.categoryId) { ElMessage.warning('请先选择分类'); return }
  try {
    const codes = await codeGenApi.generate(form.categoryId, 1) as any
    if (codes && codes.length) {
      form.materialCode = codes[0]
      ElMessage.success('已生成编码: ' + codes[0])
    }
  } catch {}
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    // W3.3: 若供应商行有 isDefault 未设置，修正后再提交
    fixSupplierIdx()

    let savedMaterialId: number | undefined
    if (dlg.isEdit) {
      await materialApi.edit(form as any)
      savedMaterialId = form.materialId
      ElMessage.success('修改成功')
    } else {
      const r: any = await materialApi.add(form as any)
      // 兼容 materialApi.add 返回值: R<number> 即 {code,msg,data} 的 data=物料ID，或接口直接返回 number
      let id = (typeof r === 'number') ? r : (r?.data ?? r)
      if (typeof id !== 'number') id = undefined
      savedMaterialId = id ?? form.materialId
      ElMessage.success('新增成功, 当前状态为【草稿】, 可在列表点击「提交审核」')
    }

    // W3.3: 保存供应商报价与替代料（新增时必须拿到 materialId 后才能调用）
    if (savedMaterialId) {
      // ---- Tab4: 供应商报价 replaceAll ----
      const cleanSuppliers = supplierRows.value.map(({ __idx, ...rest }) => ({
        ...rest,
        materialId: savedMaterialId!,
        // 阶梯价校验: 数量为空 → 0
        tier1Qty: (rest as any).tier1Qty ?? 1,
        tier1Price: (rest as any).tier1Price ?? 0,
      })) as any
      try {
        await supplierApi.replaceAll(savedMaterialId, cleanSuppliers)
      } catch (e: any) {
        ElMessage.error('供应商报价保存失败: ' + (e?.message || e))
        throw e
      }

      // ---- Tab5: 替代料 replaceAll ----
      const cleanAlts = altRows.value.filter(a => a.altMaterialId && a.altMaterialId > 0).map(a => ({
        ...a,
        mainMaterialId: savedMaterialId!,
        groupId: (a as any).groupId || altRowsGroupId.value || undefined
      })) as any
      // 新增物料时若组号为空，强制生成一个再写入
      if (!dlg.isEdit && cleanAlts.length > 0 && !cleanAlts[0].groupId) {
        try {
          const g = await alternateApi.nextGroupId() as any
          cleanAlts.forEach((a: any) => { if (!a.groupId) a.groupId = g })
          altRowsGroupId.value = g
        } catch { /* ignore */ }
      }
      try {
        await alternateApi.replaceAll(savedMaterialId, cleanAlts)
      } catch (e: any) {
        ElMessage.error('替代料保存失败: ' + (e?.message || e))
        throw e
      }
    }

    dlg.visible = false
    loadList()
  } finally { submitting.value = false }
}

// ============== 行操作 ==============
async function submitAudit(row: PdmMaterial) {
  await ElMessageBox.confirm(`确认提交物料【${row.materialCode}】审核？`, '提示', { type: 'warning' })
  try {
    await materialApi.submitAudit(row.materialId)
    ElMessage.success('已提交审核')
    loadList()
  } catch {}
}
async function auditPass(row: PdmMaterial) {
  await ElMessageBox.confirm(`确认通过物料【${row.materialCode}】审核?`, '提示', { type: 'success' })
  try {
    await materialApi.auditPass(row.materialId)
    ElMessage.success('已通过, 状态变更为 已启用')
    loadList()
  } catch {}
}
async function remove(row: PdmMaterial) {
  await ElMessageBox.confirm(`确认删除物料【${row.materialCode}】? 已启用状态禁止删除`, '提示', { type: 'error' })
  try {
    await materialApi.remove(row.materialId)
    ElMessage.success('已删除')
    loadList()
  } catch {}
}

// ============== 初始化 ==============
onMounted(async () => {
  allFlat.value = await categoryApi.list({}) as any
  const cid = route.query.categoryId
  if (cid) {
    const id = Number(cid)
    if (id) q.categoryId = id
  }
  loadList()
  // 预加载供应商/物料下拉 (供供应商报价与替代料 Tab 使用)
  loadSupplierOptions()
  loadMaterialOptions()
})

/* ============ W3.2 导入导出 ============ */
/** 浏览器把 Blob 保存为本地文件（从 response header Content-Disposition 取文件名）*/
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
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}

async function downloadTpl() {
  try {
    const resp = await materialApi.downloadTemplate()
    saveBlob(resp.data, resp, '物料导入模板.xlsx')
    ElMessage.success('模板已下载')
  } catch (e: any) { ElMessage.error(e?.message || '下载失败') }
}

async function doExport() {
  try {
    const resp = await materialApi.export({ ...q })
    saveBlob(resp.data, resp, `物料主档_${Date.now()}.xlsx`)
    ElMessage.success('导出成功')
  } catch (e: any) { ElMessage.error(e?.message || '导出失败') }
}

/* ---------- 导入对话框 ---------- */
const importOpen = ref(false)
const importLoading = ref(false)
const importProgress = ref(0)
const importFile = ref<File | null>(null)
const importUploadRef = ref<any>(null)
const importUpdateSupport = ref(false)
const importResult = ref<MaterialImportResult | null>(null)

function clearImport() {
  importFile.value = null
  importProgress.value = 0
  importResult.value = null
  if (importUploadRef.value?.clearFiles) importUploadRef.value.clearFiles()
}
watch(importOpen, (v) => { if (!v) clearImport() })

function beforeImportUpload(file: UploadFile) {
  const f = file.raw as File
  if (!f) return false
  if (!/\.(xlsx|xls)$/i.test(f.name)) {
    ElMessage.error('仅支持 .xlsx / .xls 文件')
    return false
  }
  if (f.size > 10 * 1024 * 1024) {
    ElMessage.error('文件不能超过 10MB')
    return false
  }
  importFile.value = f
  return false // 阻止 el-upload 自动上传，由 importSubmit() 手动调用 axios
}

async function importSubmit() {
  if (!importFile.value) { ElMessage.warning('请先选择 Excel 文件'); return }
  importLoading.value = true
  importProgress.value = 0
  importResult.value = null
  try {
    const r: any = await materialApi.import(
      importFile.value,
      importUpdateSupport.value,
      (p) => { importProgress.value = p }
    )
    // axios 拦截器 request.ts 在 code=200 时自动 return res.data（MaterialImportResult 本体，没有 code 字段），
    // 极少数情况下拦截器未解包（比如非 200 code 或 blob），会得到完整 R<{code,msg,data}>。
    // 通过特征字段识别 MaterialImportResult（totalRows / failCount / failRows 任一存在），
    // 而不是依赖 code===200 判断，否则拦截器解包后 code 为 undefined 会误走错误分支。
    const isImportResult = (p: any) =>
      p && typeof p === 'object' && (
        'totalRows' in p || 'failCount' in p || Array.isArray(p.failRows) || Array.isArray(p.successCount)
      )

    let parsed: MaterialImportResult | null = null
    let bizMsg = ''
    if (isImportResult(r)) {
      parsed = r as MaterialImportResult
    } else if (r && (r.code === 200 || r.code === 0) && isImportResult(r.data)) {
      // 拦截器未解包，手动取 data
      parsed = r.data as MaterialImportResult
    } else if (r && typeof r.msg === 'string') {
      bizMsg = r.msg
      // 有些 200 响应里同时带 code 和 data，再尝试一次
      if (isImportResult(r.data)) parsed = r.data as MaterialImportResult
    }

    if (parsed) {
      importResult.value = parsed
      const ok = parsed.successCount ?? 0
      const fail = parsed.failCount ?? 0
      if (fail === 0) {
        ElMessage.success(`✅ 导入成功：新增/更新 共 ${ok} 条`)
      } else {
        ElMessageBox.alert(
          `成功 ${ok} 条，失败 ${fail} 条。\n请在下方「失败详情」表格中按行号定位 Excel 问题并修正后重新上传。\n提示：行号列「系统」表示导入过程中发生的非数据行异常（如 Excel 格式/类型错误）。`,
          `导入完成（含失败 ${fail} 行）`,
          { type: 'warning', confirmButtonText: '查看明细' }
        ).catch(() => {})
      }
      loadList()
    } else {
      // 业务级错误：后端返回了 code!=200，或响应结构异常
      ElMessageBox.alert(bizMsg || '导入失败', '导入失败', { type: 'error', confirmButtonText: '我知道了' })
        .catch(() => {})
    }
  } catch (e: any) {
    // 网络/500/解析异常：尽量提取完整消息
    let msg: string
    if (e?.response?.data?.msg) {
      msg = e.response.data.msg as string
    } else if (e?.message) {
      msg = e.message as string
    } else {
      msg = '导入失败（网络异常或服务端错误）'
    }
    // 若错误响应中带有业务明细，也尝试展示 failRows
    const errData = e?.response?.data?.data || e?.response?.data || e?.data?.data || e?.data
    if (errData && Array.isArray(errData.failRows)) {
      importResult.value = errData as MaterialImportResult
    }
    ElMessageBox.alert(
      `${msg}\n\n[说明] 常见原因：1) 非 .xlsx/.xls 文件；2) Excel 模板列名不匹配或缺列；3) 文件损坏；4) 服务端异常（可联系管理员查看后端日志）。`,
      '导入失败（异常）',
      { type: 'error', confirmButtonText: '关闭' }
    ).catch(() => {})
  } finally {
    importLoading.value = false
    if (importProgress.value < 100) importProgress.value = 100
  }
}
</script>

<style scoped lang="scss">
.pdm-material-page {
  .dept-tree-card :deep(.el-card__body) { padding: 12px; max-height: calc(100vh - 200px); overflow-y: auto }
  .card-header { display: flex; justify-content: space-between; align-items: center }
  .cat-node {
    display: inline-flex; align-items: center; gap: 6px;
    .el-tag { flex-shrink: 0 }
  }
  .mt-16 { margin-top: 16px }
  .pagination-wrap { display: flex; justify-content: flex-end; padding-top: 12px }
  .hint { color: #909399; font-size: 12px }
  .ml-8 { margin-left: 8px }
  .mb-16 { margin-bottom: 16px }
}
.material-dlg {
  :deep(.el-dialog__body) { padding-top: 8px }
  .dlg-tabs { margin-top: 0 }
}
</style>
