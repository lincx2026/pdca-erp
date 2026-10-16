<template>
  <div class="pdm-bom-page">
    <!-- 查询区 -->
    <el-card shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent>
        <el-form-item label="BOM编号">
          <el-input v-model="q.bomCode" placeholder="模糊" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="BOM名称">
          <el-input v-model="q.bomName" placeholder="模糊" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="成品编码">
          <el-input v-model="q.productMaterialCode" placeholder="如 RES-26-0001" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="成品分类">
          <el-tree-select
            v-model="q.categoryId"
            :data="catTree"
            :props="{ label: 'categoryName', value: 'categoryId', children: 'children' }"
            check-strictly
            placeholder="全部"
            clearable
            style="width:180px"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="q.bomType" placeholder="全部" clearable style="width:140px">
            <el-option v-for="t in BOM_TYPES" :key="t.val" :label="t.label" :value="t.val" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="q.version" placeholder="如 V1.0" clearable style="width:130px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:120px">
            <el-option label="草稿" :value="0" />
            <el-option label="审批中" :value="1" />
            <el-option label="已启用" :value="2" />
            <el-option label="已停用" :value="3" />
            <el-option label="归档" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="pager.pageNum = 1; loadList()" :icon="Search">搜索</el-button>
          <el-button @click="resetQ" :icon="RefreshRight">重置</el-button>
        </el-form-item>
        <el-form-item style="margin-left:auto">
          <el-button type="primary" @click="openAdd()" :icon="Plus" v-hasPermi="'pdm:bom:add'">新建BOM</el-button>
          <el-button type="success" @click="openImportDlg()" :icon="Upload">导入BOM</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="mt-16">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="bomCode" label="BOM编号" width="220">
          <template #default="{ row }"><b>{{ row.bomCode }}</b></template>
        </el-table-column>
        <el-table-column prop="bomName" label="BOM名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="成品(版本)" width="300">
          <template #default="{ row }">
            <el-tag type="primary" effect="plain" size="small">{{ row.productMaterialCode }}</el-tag>
            <span style="margin:0 6px">{{ row.productMaterialName }}</span>
            <el-tag type="warning" size="small" v-if="row.version">{{ row.version }}</el-tag>
            <el-tag v-if="row.isDefault === 1" type="danger" size="small" effect="dark">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            {{ BOM_TYPES.find(x => x.val === row.bomType)?.label || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="客户 / 机型" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.customerId || row.kitModel" style="color:#606266">
              <el-tag v-if="row.customerName" size="small" type="info">{{ row.customerName }}</el-tag>
              <span v-if="row.customerName && row.kitModel"> / </span>
              <span v-if="row.kitModel" style="color:#409eff;font-weight:600">{{ row.kitModel }}</span>
            </span>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="基数" width="90" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">草稿</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">审批中</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">已启用</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger" size="small">已停用</el-tag>
            <el-tag v-else type="info" effect="dark" size="small">归档</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效/失效" width="210">
          <template #default="{ row }">
            <span>{{ row.effectiveDate || '-' }}</span>
            <span style="margin:0 4px;color:#909399">~</span>
            <span>{{ row.expiryDate || '长期' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="ECO/备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.ecoNo" style="color:#67c23a">[{{ row.ecoNo }}]</span>
            <span>{{ row.remark || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="430" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openView(row)" v-hasPermi="'pdm:bom:list'">详情</el-button>
            <el-button link size="small" type="primary" @click="openEdit(row)" v-hasPermi="'pdm:bom:edit'"
              :disabled="row.status === 2 || row.status === 4">修改</el-button>
            <el-button link size="small" type="primary" @click="openExplode(row)" v-hasPermi="'pdm:bom:explode'">展开</el-button>
            <el-button link size="small" type="primary" @click="openWhereUsed(row.productMaterialId)" v-hasPermi="'pdm:bom:whereused'">反查</el-button>
            <el-button link size="small" type="warning" @click="submitAudit(row)" v-hasPermi="'pdm:bom:audit'"
              :disabled="row.status !== 0 && row.status !== 4">提交</el-button>
            <el-button link size="small" type="success" @click="auditPass(row)" v-hasPermi="'pdm:bom:audit'"
              :disabled="row.status !== 1">通过</el-button>
            <el-button link size="small" type="danger" @click="remove(row)" v-hasPermi="'pdm:bom:remove'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination background layout="total, sizes, prev, pager, next, jumper"
          :total="pager.total" v-model:current-page="pager.pageNum"
          v-model:page-size="pager.pageSize" :page-sizes="[20, 50, 100, 200]"
          @size-change="loadList" @current-change="loadList" />
      </div>
    </el-card>

    <!-- 新增/修改 BOM 对话框 (2 Tab: 基础/行项) -->
    <el-dialog v-model="dlg.visible" :title="dlg.title" width="1000px" destroy-on-close top="5vh" class="bom-dlg">
      <el-tabs v-model="dlg.tab" type="card" class="dlg-tabs">
        <!-- Tab1 基础信息 -->
        <el-tab-pane label="基础信息" name="base">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="BOM编号" prop="bomCode" required>
                  <el-input v-model="form.bomCode" maxlength="64">
                    <template #append>
                      <el-button :icon="MagicStick" @click="genBomCode" v-hasPermi="'pdm:bom:add'">生成</el-button>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="版本号" prop="version" required>
                  <el-input v-model="form.version" placeholder="V1.0" maxlength="30" />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="BOM名称" prop="bomName">
                  <el-input v-model="form.bomName" maxlength="200" show-word-limit />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="产品物料" prop="productMaterialId" required>
                  <div style="display:flex;gap:4px">
                    <el-select v-model="form.productMaterialId" placeholder="输入编码/名称搜索" filterable size="default"
                      style="width:220px" @change="onPickProduct">
                      <el-option v-for="m in materialOptions" :key="m.materialId"
                        :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                        <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                        <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                      </el-option>
                    </el-select>
                    <el-input v-model="form.productMaterialCode" placeholder="编码" disabled style="width:160px" />
                    <el-input v-model="form.productMaterialName" placeholder="名称" disabled style="flex:1" />
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="默认版本">
                  <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
                  <span class="hint ml-8">同成品只能1个默认版本</span>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="产出基数">
                  <el-input-number v-model="form.quantity" :min="0.0001" :precision="4" controls-position="right" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="单位">
                  <el-input v-model="form.unit" placeholder="PCS" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="BOM类型">
                  <el-select v-model="form.bomType" style="width:100%">
                    <el-option v-for="t in BOM_TYPES" :key="t.val" :label="t.label" :value="t.val" />
                  </el-select>
                </el-form-item>
              </el-col>
              <!-- 销售BOM特有字段: 客户+机型 -->
              <template v-if="form.bomType === 3">
                <el-col :span="12">
                  <el-form-item label="客户">
                    <el-select v-model="form.customerId" filterable placeholder="选择客户(销售BOM)" clearable style="width:100%">
                      <el-option v-for="c in customerOpts" :key="c.customerId"
                        :label="`${c.customerCode || ''} ${c.customerName || ''}`" :value="c.customerId" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="齐套机型">
                    <el-input v-model="form.kitModel" placeholder="如 跑步机X1 / 椭圆机E5" maxlength="100" show-word-limit />
                  </el-form-item>
                </el-col>
              </template>
              <el-col :span="12">
                <el-form-item label="生效日">
                  <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="失效日">
                  <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="关联ECO单号">
                  <el-input v-model="form.ecoNo" maxlength="64" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="状态">
                  <el-tag v-if="form.status === 0" type="info">草稿</el-tag>
                  <el-tag v-else-if="form.status === 1" type="warning">审批中</el-tag>
                  <el-tag v-else-if="form.status === 2" type="success">已启用</el-tag>
                  <el-tag v-else-if="form.status === 3" type="danger">已停用</el-tag>
                  <el-tag v-else type="info" effect="dark">归档</el-tag>
                  <span class="hint ml-8">审核人: {{ form.auditBy || '-' }} / {{ form.auditTime || '-' }}</span>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="适用规格">
                  <el-input v-model="form.specification" maxlength="500" show-word-limit />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="备注">
                  <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

        <!-- Tab2 行项清单 -->
        <el-tab-pane label="行项清单" name="lines">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
            <el-alert type="info" :closable="false" show-icon style="max-width:700px"
              title="行项按成品(父=成品)的单层保存；多层BOM请将父项改为半成品ID（或点击『多层展开』查看递归结果）" />
            <div>
              <el-button size="small" type="primary" plain :icon="Plus" @click="addLine"
                v-hasPermi="'pdm:bom:edit'" :disabled="dlg.viewOnly">新增行</el-button>
              <el-button size="small" type="warning" plain :icon="Refresh" @click="sortLineNo"
                :disabled="dlg.viewOnly">重排行号(10,20,30)</el-button>
            </div>
          </div>
          <el-table :data="lineRows" border size="small" max-height="440">
            <el-table-column label="行号" width="84" align="center">
              <template #default="{ row }">
                <el-input-number size="small" v-model="row.lineNo" :min="1" :step="1" :controls="false" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="父件物料" width="200" align="center">
              <template #default="{ row }">
                <el-select size="small" v-model="row.parentMaterialId" placeholder="选择父件" filterable style="width:100%"
                  @change="(v:any)=>fillParent(row, v)">
                  <el-option v-for="m in materialOptions" :key="m.materialId"
                    :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                    <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                    <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                  </el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="子件物料" min-width="420">
              <template #default="{ row }">
                <div style="display:flex;gap:4px;align-items:center;width:100%">
                  <el-select size="small" v-model="row.componentMaterialId" placeholder="输入编码/名称搜索" filterable
                    style="width:200px" @change="(v:any)=>fillComp(row, v)">
                    <el-option v-for="m in materialOptions" :key="m.materialId"
                      :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                      <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                      <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                    </el-option>
                  </el-select>
                  <el-input size="small" v-model="row.componentMaterialCode" placeholder="编码" disabled style="width:160px" />
                  <el-input size="small" v-model="row.componentMaterialName" placeholder="名称" disabled style="flex:1" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="用量" width="100" align="center">
              <template #default="{ row }">
                <el-input-number size="small" v-model="row.quantity" :min="0.0001" :precision="4" :controls="false" style="width:100%"
                  @change="reCalcGross(row)" />
              </template>
            </el-table-column>
            <el-table-column label="单位" width="80" align="center">
              <template #default="{ row }">
                <el-input size="small" v-model="row.unit" placeholder="PCS" />
              </template>
            </el-table-column>
            <el-table-column label="损耗率" width="100" align="center">
              <template #default="{ row }">
                <el-input-number size="small" v-model="row.lossRate" :min="0" :max="0.5" :step="0.01" :precision="4"
                  :controls="false" style="width:100%" @change="reCalcGross(row)" />
              </template>
            </el-table-column>
            <el-table-column label="毛用量" width="100" align="center">
              <template #default="{ row }">
                <span style="color:#409eff;font-weight:600">{{ fmt(row.grossQty) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="供应/属性" min-width="210">
              <template #default="{ row }">
                <el-select size="small" v-model="row.supplyType" style="width:86px">
                  <el-option label="采购" :value="1" />
                  <el-option label="自制" :value="2" />
                  <el-option label="委外" :value="3" />
                  <el-option label="虚拟" :value="4" />
                  <el-option label="领用" :value="5" />
                </el-select>
                <el-checkbox v-model="(row as any).__phantom" @change="row.isPhantom = (row as any).__phantom ? 1 : 0"
                  :checked="row.isPhantom === 1">虚拟</el-checkbox>
                <el-checkbox v-model="(row as any).__optional" @change="row.isOptional = (row as any).__optional ? 1 : 0"
                  :checked="row.isOptional === 1">可选</el-checkbox>
              </template>
            </el-table-column>
            <el-table-column label="替代组号" width="140">
              <template #default="{ row }">
                <el-input size="small" v-model="row.altGroupId" placeholder="ALT-xxx" maxlength="30" />
              </template>
            </el-table-column>
            <el-table-column label="位号/位置" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <el-input size="small" v-model="row.referenceDesignator" placeholder="如 R1,R2,C5" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-switch v-model="(row as any).__status" @change="row.status = (row as any).__status ? 1 : 0"
                  :checked="row.status === 1 || row.status == null" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" fixed="right" align="center">
              <template #default="{ $index }">
                <el-button size="small" link type="danger" v-hasPermi="'pdm:bom:edit'" :disabled="dlg.viewOnly"
                  @click="lineRows.splice($index,1)">移除</el-button>
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

    <!-- 多层展开 抽屉 -->
    <el-drawer v-model="explode.open" :title="explode.title" size="80%" destroy-on-close>
      <div v-if="explode.bomId">
        <div style="display:flex;gap:10px;align-items:center;margin-bottom:10px">
          <span>最大层数：</span>
          <el-input-number v-model="explode.levelMax" :min="1" :max="20" style="width:120px" />
          <el-checkbox v-model="explode.phantomExpand">虚拟件自动下沉</el-checkbox>
          <el-radio-group v-model="explode.viewMode">
            <el-radio-button label="tree">树形</el-radio-button>
            <el-radio-button label="flat">扁平汇总</el-radio-button>
          </el-radio-group>
          <el-button type="primary" @click="doExplode" :icon="Search">执行展开</el-button>
        </div>
        <!-- 树形 -->
        <el-table v-if="explode.viewMode === 'tree'" :data="explode.treeRows" border size="small" row-key="lineId"
          :tree-props="{ children: 'children' }" default-expand-all max-height="60vh">
          <el-table-column label="层级" width="70" align="center">
            <template #default="{ row }">L{{ row.level }}</template>
          </el-table-column>
          <el-table-column label="子件" min-width="260">
            <template #default="{ row }">
              <el-tag size="small" type="primary" effect="plain">{{ row.componentMaterialCode }}</el-tag>
              <span style="margin-left:6px">{{ row.componentMaterialName }}</span>
              <el-tag v-if="row.isPhantom===1" size="small" type="warning" effect="dark" style="margin-left:6px">虚拟</el-tag>
              <el-tag v-if="row.isOptional===1" size="small" type="info" style="margin-left:6px">可选</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="componentSpec" label="规格" min-width="180" show-overflow-tooltip />
          <el-table-column label="父项→本层用量" width="170" align="right">
            <template #default="{ row }">{{ fmt(row.quantity) }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="累计用量" width="130" align="right">
            <template #default="{ row }">
              <b style="color:#e6a23c">{{ fmt(row.pathQty) }}</b>
            </template>
          </el-table-column>
          <el-table-column label="供应" width="80" align="center">
            <template #default="{ row }">
              {{ SUPPLY_TYPES[row.supplyType ?? 1] }}
            </template>
          </el-table-column>
          <el-table-column label="替代组号" width="140">
            <template #default="{ row }">{{ row.altGroupId || '-' }}</template>
          </el-table-column>
          <el-table-column label="位号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.referenceDesignator || '-' }}</template>
          </el-table-column>
        </el-table>
        <!-- 扁平 -->
        <el-table v-else :data="explode.flatRows" border size="small" max-height="60vh">
          <el-table-column label="子件编码" width="170">
            <template #default="{ row }"><b>{{ row.componentMaterialCode }}</b></template>
          </el-table-column>
          <el-table-column prop="componentMaterialName" label="子件名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="componentSpec" label="规格" min-width="180" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column label="累计用量" width="150" align="right">
            <template #default="{ row }">
              <b style="color:#e6a23c">{{ fmt(row.pathQty) }}</b>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!explode.treeRows.length && explode.viewMode==='tree'" description="请先执行展开或暂无子件" />
        <el-empty v-if="!explode.flatRows.length && explode.viewMode==='flat'" description="请先执行展开或暂无子件" />
      </div>
    </el-drawer>

    <!-- Where-Used 反查 抽屉 -->
    <el-drawer v-model="whereUsed.open" :title="whereUsed.title" size="65%" destroy-on-close>
      <div v-if="whereUsed.componentId">
        <div style="display:flex;gap:10px;align-items:center;margin-bottom:10px">
          <el-select v-model="whereUsed.componentId" placeholder="选择物料" filterable style="width:220px" @change="doWhereUsed">
            <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
              <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
            </el-option>
          </el-select>
          <el-select v-model="whereUsed.mode" style="width:150px" @change="doWhereUsed">
            <el-option label="单层反查" value="single" />
            <el-option label="递归反查顶层" value="recursive" />
          </el-select>
          <el-select v-model="whereUsed.bomStatus" placeholder="BOM状态" clearable style="width:130px" @change="doWhereUsed">
            <el-option label="审批中/启用" :value="1" />
            <el-option label="仅启用" :value="2" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="doWhereUsed">查询</el-button>
        </div>
        <el-table v-if="whereUsed.mode==='single'" :data="whereUsed.singleRows" border size="small" max-height="60vh">
          <el-table-column label="BOM编号" width="220">
            <template #default="{ row }">
              <el-link type="primary" @click="openViewById((row as any).bomId)">{{ (row as any).bomCode }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="parentMaterialCode" label="父项编码" width="160" />
          <el-table-column prop="parentMaterialName" label="父项名称" min-width="200" show-overflow-tooltip />
          <el-table-column label="用量" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.quantity) }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="行状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status===1?'success':'info'">{{ row.status===1?'生效':'停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-table v-else :data="whereUsed.recursiveRows" border size="small" max-height="60vh">
          <el-table-column prop="level" label="层级" width="70" align="center" />
          <el-table-column label="父BOM" width="220">
            <template #default="{ row }">
              <el-link type="primary" @click="openViewById(row.bomId)">{{ row.bomCode }}</el-link>
              <span style="margin-left:4px">{{ row.version }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="parentCode" label="父项编码" width="160" />
          <el-table-column prop="parentName" label="父项名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="lineQty" label="本层用量" width="110" align="right" />
          <el-table-column prop="path" label="路径" min-width="300" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>

    <!-- ============ BOM 导入对话框 ============ -->
    <el-dialog v-model="imp.visible" title="Excel 导入 BOM" width="95%" top="3vh" :close-on-click-modal="false">
      <!-- Step 1: 选成品 + 上传 -->
      <div v-if="imp.step === 1">
        <el-alert type="info" :closable="false" style="margin-bottom:16px">
          操作步骤：① 选择成品物料（BOM 的父件） → ② 下载模板并填写行项 → ③ 上传预览 → ④ 确认入库。物料编码无需预先在 PDM 存在，未命中的会自动创建草稿。
        </el-alert>
        <el-form label-width="120px">
          <el-form-item label="成品物料" required>
            <el-select v-model="imp.productMaterialId" filterable remote reserve-keyword
              placeholder="搜索物料编码或名称" :remote-method="searchMaterial" :loading="imp.matLoading"
              style="width:400px" @change="onImpPickProduct">
              <el-option v-for="m in imp.matOptions" :key="m.materialId"
                :label="`${m.materialCode} - ${m.materialName}`" :value="m.materialId" />
            </el-select>
          </el-form-item>
          <el-form-item label="BOM名称">
            <el-input v-model="imp.bomName" placeholder="留空则自动取成品名称+BOM" style="width:400px" />
          </el-form-item>
          <el-form-item label="版本号">
            <el-input v-model="imp.version" placeholder="如 V1.0" style="width:200px" />
          </el-form-item>
          <el-form-item label="模板下载">
            <el-button type="info" @click="downloadBomTpl" :icon="Download">下载导入模板</el-button>
          </el-form-item>
          <el-form-item label="上传 Excel" required>
            <el-upload ref="impUploadRef" :auto-upload="false" :limit="1" accept=".xlsx,.xls"
              :on-change="onImpFileChange" :on-exceed="() => ElMessage.warning('只支持1个文件')">
              <el-button type="primary" :icon="Upload">选择文件</el-button>
              <template #tip>
                <div class="hint">仅支持 .xlsx/.xls，单次最多 500 行</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
        <div style="text-align:center;padding:12px 0">
          <el-button type="primary" size="large" @click="doPreviewImport"
            :loading="imp.previewing" :disabled="!imp.productMaterialId || !imp.file">
            预览导入
          </el-button>
        </div>
      </div>

      <!-- Step 2: 预览结果 -->
      <div v-if="imp.step === 2">
        <el-row :gutter="16" style="margin-bottom:12px">
          <el-col :span="6">
            <el-statistic title="总行数" :value="imp.preview?.totalLines ?? 0" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="错误行" :value="imp.preview?.errorCount ?? 0" title-class="text-danger"/>
          </el-col>
          <el-col :span="6">
            <el-statistic title="警告行" :value="imp.preview?.warningCount ?? 0" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="预创建物料" :value="imp.preview?.precreateMaterials?.length ?? 0" />
          </el-col>
        </el-row>

        <el-alert v-if="imp.preview?.errorCount > 0" type="error" :closable="false" style="margin-bottom:8px">
          有 {{ imp.preview.errorCount }} 行校验失败，请修正后重新上传。
        </el-alert>
        <el-alert v-else type="success" :closable="false" style="margin-bottom:8px">
          校验通过，可确认导入。
        </el-alert>

        <!-- 预创建物料提示 -->
        <el-alert v-if="imp.preview?.precreateMaterials?.length > 0" type="warning" :closable="false" style="margin-bottom:8px">
          以下 {{ imp.preview.precreateMaterials.length }} 个物料编码不在 PDM 主库，导入时将自动创建草稿态物料，需后续在物料管理中补全规格/品牌/MPN。
        </el-alert>

        <el-table :data="imp.preview?.items ?? []" border stripe max-height="400" style="width:100%" size="small">
          <el-table-column prop="lineNo" label="序号" width="60" />
          <el-table-column prop="referenceDesignator" label="位号" width="120" />
          <el-table-column prop="componentMaterialCode" label="物料编码" width="180" />
          <el-table-column prop="componentMaterialName" label="物料名称" width="160" show-overflow-tooltip />
          <el-table-column prop="quantity" label="用量" width="70" />
          <el-table-column prop="unit" label="单位" width="55" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.rowError" type="danger" size="small">{{ row.rowError }}</el-tag>
              <el-tag v-else-if="row.needPrecreate" type="warning" size="small">待创建草稿</el-tag>
              <el-tag v-else type="success" size="small">已匹配</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="警告" width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.rowWarning" style="color:#e6a23c;font-size:12px">{{ row.rowWarning }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div style="text-align:center;padding:16px 0">
          <el-button @click="imp.step = 1">返回修改</el-button>
          <el-button type="primary" size="large" @click="doConfirmImport"
            :loading="imp.confirming" :disabled="imp.preview?.errorCount > 0">
            确认导入 (创建BOM + {{ imp.preview?.precreateMaterials?.length ?? 0 }} 个草稿物料)
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshRight, Plus, MagicStick, Refresh, Upload, Download } from '@element-plus/icons-vue'
import { categoryApi, bomApi, materialApi, salCustomerApi, type BomListQ } from '@/api'
import type { PdmMaterialCategory, PdmBom, PdmBomLine, PdmMaterial, SalCustomer } from '@/types/models'

const BOM_TYPES = [
  { label: '制造BOM', val: 1 },
  { label: '工程BOM', val: 2 },
  { label: '销售BOM', val: 3 },
  { label: '计划BOM', val: 4 },
  { label: '替代BOM', val: 5 }
]
const SUPPLY_TYPES: Record<number, string> = {
  1: '采购', 2: '自制', 3: '委外', 4: '虚拟', 5: '领用'
}
function fmt(v: any) {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return Math.round(n * 1_000_000) / 1_000_000
}

// ========== 查询分页 ==========
const loading = ref(false)
const rows = ref<PdmBom[]>([])
const catTree = ref<PdmMaterialCategory[]>([])
const q = reactive<BomListQ>({
  pageNum: 1, pageSize: 20,
  bomCode: '', bomName: '', productMaterialCode: '',
  productMaterialId: undefined, categoryId: undefined,
  bomType: undefined, status: undefined, version: ''
})
const pager = reactive({ pageNum: 1, pageSize: 20, total: 0 })
async function loadList() {
  loading.value = true
  try {
    const resp: any = await bomApi.list({ ...q, pageNum: pager.pageNum, pageSize: pager.pageSize })
    rows.value = resp?.rows || []
    pager.total = resp?.total || 0
  } finally { loading.value = false }
}
function resetQ() {
  Object.assign(q, {
    bomCode: '', bomName: '', productMaterialCode: '',
    productMaterialId: undefined, categoryId: undefined,
    bomType: undefined, status: undefined, version: ''
  })
  pager.pageNum = 1
  loadList()
}

// ========== 新增/修改对话框 ==========
const formRef = ref<FormInstance>()
const submitting = ref(false)
const dlg = reactive<{ visible: boolean; isEdit: boolean; viewOnly: boolean; title: string; tab: string }>({
  visible: false, isEdit: false, viewOnly: false, title: '', tab: 'base'
})
const form = reactive<Partial<PdmBom>>({
  bomCode: '', bomName: '', version: 'V1.0',
  productMaterialId: undefined as any,
  productMaterialCode: '', productMaterialName: '',
  isDefault: 0, quantity: 1, unit: 'PCS',
  bomType: 1, status: 0,
  customerId: undefined, kitModel: '',
  effectiveDate: undefined, expiryDate: undefined,
  ecoNo: '', specification: '', remark: ''
})
const lineRows = ref<any[]>([])
/* 销售BOM: 客户下拉 */
const customerOpts = ref<SalCustomer[]>([])
async function loadCustomerOpts() {
  try { customerOpts.value = (await salCustomerApi.options()) as SalCustomer[] || [] }
  catch { customerOpts.value = [] }
}
/* 物料下拉 (产品/父件/子件复用) */
const materialOptions = ref<PdmMaterial[]>([])
async function loadMaterialOptions() {
  try {
    const resp: any = await materialApi.list({ pageNum: 1, pageSize: 1000 })
    materialOptions.value = (resp?.rows || []) as PdmMaterial[]
  } catch { materialOptions.value = [] }
}

const rules: FormRules = {
  bomCode: [{ required: true, message: '请输入/生成BOM编号', trigger: 'blur' }],
  bomName: [{ required: true, message: '请输入BOM名称', trigger: 'blur' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  productMaterialId: [{ required: true, message: '请选择产成品物料', trigger: 'change' }]
}
function resetForm() {
  Object.assign(form, {
    bomId: undefined, bomCode: '', bomName: '', version: 'V1.0',
    productMaterialId: undefined as any,
    productMaterialCode: '', productMaterialName: '',
    isDefault: 0, quantity: 1, unit: 'PCS',
    bomType: 1, status: 0, auditBy: '', auditTime: '', rejectReason: '',
    effectiveDate: undefined, expiryDate: undefined,
    ecoNo: '', specification: '', remark: ''
  })
  lineRows.value = []
}
async function genBomCode() {
  try {
    const r: any = await bomApi.nextCode()
    form.bomCode = (typeof r === 'string') ? r : (r?.data ?? r)
  } catch {}
}
async function onPickProduct(id: number | undefined) {
  if (!id) return
  const m: any = materialOptions.value.find(x => x.materialId === id)
  if (m) {
    form.productMaterialCode = m.materialCode
    form.productMaterialName = m.materialName
    if (!form.unit) form.unit = m.unit || 'PCS'
    if (!form.bomName) form.bomName = `${m.materialName || ''} - 主BOM`
    // 父项默认填成品
    lineRows.value.forEach((l: any) => {
      if (!l.parentMaterialId) l.parentMaterialId = id
    })
  } else {
    // 兜底: 本地未命中再走单条查询
    try {
      const mm: any = await materialApi.get(id)
      if (mm) {
        form.productMaterialCode = mm.materialCode
        form.productMaterialName = mm.materialName
        if (!form.unit) form.unit = mm.unit || 'PCS'
        if (!form.bomName) form.bomName = `${mm.materialName || ''} - 主BOM`
        lineRows.value.forEach((l: any) => {
          if (!l.parentMaterialId) l.parentMaterialId = id
        })
      }
    } catch {}
  }
}
function fillParent(row: any, id: number) {
  if (!id) return
  const m: any = materialOptions.value.find(x => x.materialId === id)
  if (m) row.parentMaterialCode = m.materialCode
}
async function fillComp(row: any, id: number) {
  if (!id) return
  const m: any = materialOptions.value.find(x => x.materialId === id)
  if (m) {
    row.componentMaterialCode = m.materialCode
    row.componentMaterialName = m.materialName
    row.componentSpec = m.specification
    if (!row.unit) row.unit = m.unit || 'PCS'
  } else {
    try {
      const mm: any = await materialApi.get(id) as PdmMaterial
      if (mm) {
        row.componentMaterialCode = mm.materialCode
        row.componentMaterialName = mm.materialName
        row.componentSpec = mm.specification
        if (!row.unit) row.unit = mm.unit || 'PCS'
      }
    } catch {}
  }
}
function reCalcGross(row: any) {
  const q = Number(row.quantity ?? 0)
  const loss = Math.min(0.5, Math.max(0, Number(row.lossRate ?? 0)))
  const denom = 1 - loss
  row.grossQty = denom <= 0 ? q : q / denom
}
function addLine() {
  const n: any = {
    lineNo: (lineRows.value.length + 1) * 10,
    parentMaterialId: form.productMaterialId ?? (null as any),
    componentMaterialId: null as any,
    componentMaterialCode: '', componentMaterialName: '', componentSpec: '',
    quantity: 1, unit: 'PCS', lossRate: 0, grossQty: 1,
    altGroupId: '',
    isPhantom: 0, isOptional: 0, supplyType: 1,
    __phantom: false, __optional: false,
    referenceDesignator: '', mfrPartNo: '',
    status: 1, __status: true,
    remark: ''
  }
  lineRows.value.push(n)
}
function sortLineNo() {
  lineRows.value.forEach((l: any, i: number) => { l.lineNo = (i + 1) * 10 })
}

async function openAdd() {
  resetForm()
  dlg.isEdit = false
  dlg.viewOnly = false
  dlg.title = '新建 BOM'
  dlg.tab = 'base'
  if (customerOpts.value.length === 0) await loadCustomerOpts()
  if (materialOptions.value.length === 0) await loadMaterialOptions()
  await genBomCode()
  dlg.visible = true
}
async function openEdit(row: PdmBom) {
  resetForm()
  if (materialOptions.value.length === 0) await loadMaterialOptions()
  const detail: any = await bomApi.get(row.bomId!)
  Object.assign(form, detail)
  const lines = (detail?.lines || []) as any[]
  lineRows.value = lines.map((l, i) => ({
    ...l,
    __phantom: l.isPhantom === 1,
    __optional: l.isOptional === 1,
    __status: (l.status ?? 1) === 1,
    lineNo: l.lineNo ?? (i + 1) * 10
  }))
  dlg.isEdit = true
  dlg.viewOnly = false
  dlg.title = '修改 BOM - ' + detail.bomCode
  dlg.tab = 'base'
  dlg.visible = true
}
async function openView(row: PdmBom) {
  resetForm()
  if (materialOptions.value.length === 0) await loadMaterialOptions()
  const detail: any = await bomApi.get(row.bomId!)
  Object.assign(form, detail)
  const lines = (detail?.lines || []) as any[]
  lineRows.value = lines.map((l: any, i: number) => ({
    ...l,
    __phantom: l.isPhantom === 1,
    __optional: l.isOptional === 1,
    __status: (l.status ?? 1) === 1,
    lineNo: l.lineNo ?? (i + 1) * 10
  }))
  dlg.isEdit = false
  dlg.viewOnly = true
  dlg.title = 'BOM详情 - ' + detail.bomCode
  dlg.tab = 'base'
  dlg.visible = true
}
async function openViewById(id: number) {
  const r: any = rows.value.find(x => x.bomId === id)
  if (r) { await openView(r); return }
  try {
    const detail: any = await bomApi.get(id)
    await openView(detail)
  } catch {}
}
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  // 行项基本校验
  for (const l of lineRows.value) {
    if (!l.componentMaterialId || l.componentMaterialId <= 0) {
      ElMessage.warning('行项存在未填写子件ID，请先填写或移除该行')
      dlg.tab = 'lines'
      return
    }
    if (!l.parentMaterialId) l.parentMaterialId = form.productMaterialId
  }
  submitting.value = true
  try {
    if (dlg.isEdit) {
      await bomApi.edit(form as any)
      ElMessage.success('主档修改成功')
      // 行项 replaceAll
      try { await bomApi.replaceLines(form.bomId!, buildCleanLines()) }
      catch (e: any) { ElMessage.error('行项保存失败: ' + (e?.message || e)); throw e }
    } else {
      (form as any).lines = buildCleanLines()
      const r: any = await bomApi.add(form as any)
      const id = (typeof r === 'number') ? r : (r?.data ?? r)
      if (!id) throw new Error('新增返回ID为空')
      form.bomId = id
      ElMessage.success('新增成功，当前状态【草稿】，可点击列表「提交」送审')
    }
    dlg.visible = false
    loadList()
  } finally { submitting.value = false }
}
function buildCleanLines(): PdmBomLine[] {
  return lineRows.value.map((l: any) => ({
    lineId: l.lineId,
    bomId: l.bomId,
    lineNo: l.lineNo,
    parentMaterialId: l.parentMaterialId ?? form.productMaterialId,
    componentMaterialId: l.componentMaterialId,
    componentMaterialCode: l.componentMaterialCode ?? '',
    componentMaterialName: l.componentMaterialName ?? '',
    componentSpec: l.componentSpec ?? '',
    quantity: l.quantity,
    unit: l.unit ?? 'PCS',
    lossRate: l.lossRate ?? 0,
    altGroupId: l.altGroupId ?? '',
    isPhantom: l.isPhantom ?? 0,
    isOptional: l.isOptional ?? 0,
    supplyType: l.supplyType ?? 1,
    referenceDesignator: l.referenceDesignator ?? '',
    mfrPartNo: l.mfrPartNo ?? '',
    status: l.status ?? 1,
    effectiveDate: l.effectiveDate,
    expiryDate: l.expiryDate,
    remark: l.remark ?? ''
  }))
}

// ========== 列表行操作 ==========
async function submitAudit(row: PdmBom) {
  await ElMessageBox.confirm(`确认提交 BOM【${row.bomCode}】审核？`, '提示', { type: 'warning' })
  try { await bomApi.submitAudit(row.bomId!); ElMessage.success('已提交'); loadList() } catch {}
}
async function auditPass(row: PdmBom) {
  await ElMessageBox.confirm(`确认通过 BOM【${row.bomCode}】审核？`, '提示', { type: 'success' })
  try { await bomApi.auditPass(row.bomId!); ElMessage.success('已通过'); loadList() } catch {}
}
async function remove(row: PdmBom) {
  await ElMessageBox.confirm(`确认删除 BOM【${row.bomCode}】？仅草稿/驳回可删`, '提示', { type: 'error' })
  try { await bomApi.remove(row.bomId!); ElMessage.success('已删除'); loadList() } catch {}
}

// ========== 多层展开 抽屉 ==========
const explode = reactive<{
  open: boolean; title: string; bomId: number | null;
  levelMax: number; phantomExpand: boolean; viewMode: 'tree' | 'flat';
  treeRows: any[]; flatRows: any[]
}>({
  open: false, title: 'BOM 多层展开', bomId: null,
  levelMax: 10, phantomExpand: true, viewMode: 'tree',
  treeRows: [], flatRows: []
})
function openExplode(row: PdmBom) {
  explode.open = true
  explode.bomId = row.bomId ?? null
  explode.title = `展开 - ${row.bomCode} ${row.bomName || ''}`
  explode.treeRows = []
  explode.flatRows = []
  setTimeout(doExplode, 80)
}
async function doExplode() {
  if (!explode.bomId) return
  try {
    if (explode.viewMode === 'tree') {
      const list = await bomApi.explodeTree(explode.bomId, explode.levelMax, explode.phantomExpand) as any[]
      explode.treeRows = buildTreeFromExplode(list)
    } else {
      const map = await bomApi.explodeFlat(explode.bomId, explode.levelMax, explode.phantomExpand) as any
      explode.flatRows = Object.values(map ?? {})
    }
  } catch (e: any) { ElMessage.error(e?.message || '展开失败') }
}
function buildTreeFromExplode(list: any[]): any[] {
  // level 顺序构建：简单做法 — level 0 是顶层，把低 level 的设为高 level 最近祖先的 children
  const stack: any[] = []
  const roots: any[] = []
  for (const l of list) {
    const node = { ...l, children: [] }
    const lv = Number(l.level ?? 0)
    while (stack.length && stack[stack.length - 1].__lv >= lv) stack.pop()
    if (!stack.length) roots.push(node)
    else stack[stack.length - 1].children.push(node)
    node.__lv = lv
    stack.push(node)
  }
  return roots
}

// ========== Where-Used 抽屉 ==========
const whereUsed = reactive<{
  open: boolean; title: string; componentId: number | null;
  mode: 'single' | 'recursive'; bomStatus: number | null;
  singleRows: any[]; recursiveRows: any[]
}>({
  open: false, title: 'Where-Used 反查', componentId: null,
  mode: 'recursive', bomStatus: null,
  singleRows: [], recursiveRows: []
})
function openWhereUsed(componentId: number | undefined) {
  if (!componentId) return
  whereUsed.open = true
  whereUsed.componentId = componentId
  whereUsed.title = `Where-Used - 物料ID ${componentId}`
  whereUsed.singleRows = []
  whereUsed.recursiveRows = []
  setTimeout(doWhereUsed, 80)
}
async function doWhereUsed() {
  if (!whereUsed.componentId) return
  try {
    const lStatus = 1
    const bStatus = whereUsed.bomStatus ?? undefined as any
    if (whereUsed.mode === 'single') {
      whereUsed.singleRows = await bomApi.whereUsedSingle(whereUsed.componentId, lStatus, bStatus) as any[]
      // 补 bomCode/bomName 显示
      for (const r of whereUsed.singleRows) {
        try {
          const detail: any = await bomApi.get(r.bomId)
          r.bomCode = detail.bomCode
          r.bomName = detail.bomName
          r.version = detail.version
        } catch { /* ignore */ }
      }
    } else {
      whereUsed.recursiveRows = await bomApi.whereUsedRecursive(whereUsed.componentId, lStatus, bStatus, 10) as any[]
    }
  } catch (e: any) { ElMessage.error(e?.message || '反查失败') }
}

// ========== BOM Excel 导入 ==========
const impUploadRef = ref()
const imp = reactive<{
  visible: boolean; step: 1 | 2;
  productMaterialId: number | undefined; bomName: string; version: string;
  file: File | null; previewing: boolean; confirming: boolean;
  matLoading: boolean; matOptions: PdmMaterial[];
  preview: any;
}>({
  visible: false, step: 1,
  productMaterialId: undefined, bomName: '', version: 'V1.0',
  file: null, previewing: false, confirming: false,
  matLoading: false, matOptions: [],
  preview: null
})
function openImportDlg() {
  imp.visible = true
  imp.step = 1
  imp.productMaterialId = undefined
  imp.bomName = ''
  imp.version = 'V1.0'
  imp.file = null
  imp.preview = null
  imp.matOptions = materialOptions.value
  impUploadRef.value?.clearFiles()
}
async function searchMaterial(query: string) {
  if (!query) { imp.matOptions = materialOptions.value; return }
  imp.matLoading = true
  try {
    const resp: any = await materialApi.list({ pageNum: 1, pageSize: 50, materialCode: query })
    const resp2: any = await materialApi.list({ pageNum: 1, pageSize: 50, materialName: query })
    const merged = [...(resp?.rows || []), ...(resp2?.rows || [])]
    imp.matOptions = merged.filter((m: any, i: number, a: any[]) =>
      a.findIndex((x: any) => x.materialId === m.materialId) === i)
  } finally { imp.matLoading = false }
}
function onImpPickProduct(id: number | undefined) {
  if (!id) return
  const m: any = imp.matOptions.find(x => x.materialId === id)
  if (m && !imp.bomName) imp.bomName = `${m.materialName} BOM`
}
function onImpFileChange(file: any) {
  imp.file = file.raw
}
async function downloadBomTpl() {
  try {
    const resp = await bomApi.downloadBomTemplate()
    const data = resp?.data ?? resp
    const buf = data instanceof Blob ? data : new Blob([data as any])
    const url = window.URL.createObjectURL(buf)
    const a = document.createElement('a')
    a.href = url
    a.download = 'BOM行项导入模板.xlsx'
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (e: any) { ElMessage.error(e?.message || '下载模板失败') }
}
async function doPreviewImport() {
  if (!imp.productMaterialId) { ElMessage.warning('请先选择成品物料'); return }
  if (!imp.file) { ElMessage.warning('请选择 Excel 文件'); return }
  imp.previewing = true
  try {
    const resp: any = await bomApi.previewBomImport(imp.file, imp.productMaterialId, imp.bomName, imp.version)
    imp.preview = resp?.data ?? resp
    imp.step = 2
    if (imp.preview?.errorCount > 0) {
      ElMessage.warning(`校验发现 ${imp.preview.errorCount} 行错误，请修正后重新上传`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '预览失败')
  } finally { imp.previewing = false }
}
async function doConfirmImport() {
  if (!imp.preview) return
  imp.confirming = true
  try {
    const resp: any = await bomApi.confirmBomImport(imp.preview)
    const bomId = resp?.data ?? resp
    ElMessage.success(`导入成功！BOM ID=${bomId}，状态=草稿`)
    imp.visible = false
    pager.pageNum = 1
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally { imp.confirming = false }
}

// ========== 初始化 ==========
onMounted(async () => {
  catTree.value = await categoryApi.list({}) as any
  loadCustomerOpts()
  loadMaterialOptions()
  loadList()
})
</script>

<style scoped lang="scss">
.pdm-bom-page {
  .mt-16 { margin-top: 16px }
  .pagination-wrap { display: flex; justify-content: flex-end; padding-top: 12px }
  .hint { color: #909399; font-size: 12px }
  .ml-8 { margin-left: 8px }
}
.bom-dlg {
  :deep(.el-dialog__body) { padding-top: 8px }
  .dlg-tabs { margin-top: 0 }
}
</style>
