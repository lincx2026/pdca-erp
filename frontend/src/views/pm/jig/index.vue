<template>
  <div class="pm-page">
    <!-- ========= 搜索栏 ========= -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工装编号">
          <el-input v-model="q.jigNo" placeholder="如 JIG-01" clearable @keyup.enter="loadList" style="width:140px" />
        </el-form-item>
        <el-form-item label="工装名称">
          <el-input v-model="q.jigName" placeholder="模糊搜索" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item label="工装类型">
          <el-select v-model="q.jigType" placeholder="全部" clearable style="width:140px" @change="loadList">
            <el-option v-for="t in JIG_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品机种">
          <el-input v-model="q.productCode" placeholder="如 ROCK-XT PRO" clearable @keyup.enter="loadList" style="width:160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:110px" @change="loadList">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="保管人">
          <el-input v-model="q.keeper" placeholder="姓名" clearable @keyup.enter="loadList" style="width:110px" />
        </el-form-item>
        <el-form-item label="仓库">
          <el-input v-model="q.warehouse" placeholder="仓库名" clearable @keyup.enter="loadList" style="width:100px" />
        </el-form-item>
        <el-form-item label="存放位置">
          <el-input v-model="q.location" placeholder="如 3A-2" clearable @keyup.enter="loadList" style="width:100px" />
        </el-form-item>
        <el-form-item label="领用人">
          <el-input v-model="q.borrowerName" placeholder="姓名" clearable @keyup.enter="loadList" style="width:110px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ========= Tabs: 工装档案 / 动作日志 ========= -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />工装治具管理</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:jig:add'" @click="openAdd">新增工装</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- ===== Tab 1: 工装档案 ===== -->
        <el-tab-pane label="工装档案" name="jig">
          <el-table :data="jigList.data.rows || []" v-loading="jigList.loading" stripe border row-key="jigId" size="default">
            <el-table-column label="编号" prop="jigNo" width="110" fixed />
            <el-table-column label="工装名称" prop="jigName" min-width="200" show-overflow-tooltip />
            <el-table-column label="类型" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="jigTypeTag(row.jigType)" size="small" effect="plain">{{ jigTypeLabel(row.jigType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="适用机种" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <div style="line-height:1.3;font-size:12px">
                  <div><b>{{ row.productCode || '-' }}</b></div>
                  <div style="color:var(--el-text-color-secondary)">{{ row.productName || '' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="材质/工艺" width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <div style="line-height:1.3;font-size:12px">
                  <div>{{ row.material || '-' }}</div>
                  <div style="color:var(--el-text-color-secondary)">{{ row.specification || '' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="92" align="center">
              <template #default="{ row }">
                <component :is="statusTag(row.status).tag" :type="statusTag(row.status).type" size="small" effect="light">
                  {{ statusTag(row.status).label }}
                </component>
              </template>
            </el-table-column>
            <el-table-column label="数量 (可用/借出/总)" width="138" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="success" effect="plain">{{ row.qtyAvailable ?? 0 }}</el-tag>
                <span style="color:var(--el-text-color-secondary)"> / </span>
                <el-tag size="small" type="warning" effect="plain">{{ row.qtyBorrowed ?? 0 }}</el-tag>
                <span style="color:var(--el-text-color-secondary)"> / </span>
                <span style="font-weight:500">{{ row.qtyCurrent ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="寿命使用进度" min-width="200" align="center">
              <template #default="{ row }">
                <div v-if="(row.lifeCount || 0) > 0" style="padding:4px 6px">
                  <el-tooltip :content="`已使用 ${row.usedCount || 0} 次 / 寿命阈值 ${row.lifeCount} 次; 达到100%将被锁死需修磨/更换`">
                    <el-progress
                      :percentage="Math.min(100, Math.round(((row.usedCount || 0) / row.lifeCount) * 100))"
                      :color="lifeColor(row)"
                      :stroke-width="12"
                      :format="p => `${p}% (${row.usedCount || 0}/${row.lifeCount})`"
                    />
                  </el-tooltip>
                </div>
                <span v-else style="color:var(--el-text-color-secondary);font-size:12px">
                  已使用 {{ row.usedCount || 0 }} 次 (不限寿命)
                </span>
              </template>
            </el-table-column>
            <el-table-column label="下次校准" width="112" align="center">
              <template #default="{ row }">
                <template v-if="(row.calCycleDays || 0) > 0">
                  <el-tooltip v-if="row.nextCalDate" :content="'上次校准: ' + (row.lastCalDate || '未记录') + '; 周期 ' + row.calCycleDays + ' 天'">
                    <el-tag size="small" :type="daysFromToday(row.nextCalDate) < 15 ? 'warning' : 'success'">
                      {{ row.nextCalDate }}
                    </el-tag>
                  </el-tooltip>
                  <span v-else style="color:var(--el-text-color-secondary)">未设定</span>
                </template>
                <span v-else style="color:var(--el-text-color-disabled)">—</span>
              </template>
            </el-table-column>
            <el-table-column label="领用人" width="92">
              <template #default="{ row }">
                <span v-if="row.borrowerName">{{ row.borrowerName }}</span>
                <span v-else style="color:var(--el-text-color-secondary)">-</span>
              </template>
            </el-table-column>
            <el-table-column label="存放地" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <div style="line-height:1.3;font-size:12px">
                  <div>{{ row.warehouse || '-' }}</div>
                  <div style="color:var(--el-text-color-secondary)">{{ row.location || '' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="420" fixed="right">
              <template #default="{ row }">
                <el-dropdown :trigger="['click']" @command="(cmd:any) => onAction(cmd, row)">
                  <el-button size="small" type="primary" plain v-hasPermi="'pm:jig:operate'">更多动作<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="borrow"     :icon="ShoppingBag"    style="color:var(--el-color-primary)">领用 (扣可用/加借出)</el-dropdown-item>
                      <el-dropdown-item command="return"     :icon="RefreshLeft" style="color:var(--el-color-success)">归还 (加使用次数)</el-dropdown-item>
                      <el-dropdown-item command="calibrate"  :icon="Cpu"    style="color:var(--el-color-warning)">校准/张力测试</el-dropdown-item>
                      <el-dropdown-item command="resharpen"  :icon="Promotion" style="color:#5856d6">修磨/更换钢片 (寿命清零)</el-dropdown-item>
                      <el-dropdown-item command="repair"     :icon="Tools"  style="color:var(--el-color-danger)">维修</el-dropdown-item>
                      <el-dropdown-item command="scrap"      :icon="Delete" style="color:#b42318">报废 (扣总数)</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button link type="primary" v-hasPermi="'pm:jig:query'" @click="openLogDrawer(row)">日志</el-button>
                <el-button link type="primary" v-hasPermi="'pm:jig:edit'"  @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger"  v-hasPermi="'pm:jig:remove'" @click="handleRemove(row)">删除</el-button>
                <el-button link type="info" size="small" @click="openCalibPrint(row)">校准记录</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
            :total="jigList.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadList" @current-change="loadList" />
        </el-tab-pane>

        <!-- ===== Tab 2: 全局动作日志 ===== -->
        <el-tab-pane label="动作日志" name="log">
          <el-form :inline="true" :model="logQ" @submit.prevent="loadLogs" size="default">
            <el-form-item label="工装编号">
              <el-select v-model="logQ.jigId" filterable placeholder="全部" clearable style="width:220px" @change="loadLogs">
                <el-option v-for="j in jigOptions" :key="j.jigId"
                  :label="`${j.jigNo} ${j.jigName}`" :value="j.jigId" />
              </el-select>
            </el-form-item>
            <el-form-item label="动作类型">
              <el-select v-model="logQ.eventType" placeholder="全部" clearable style="width:130px" @change="loadLogs">
                <el-option v-for="t in EVENT_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="结果">
              <el-select v-model="logQ.eventResult" placeholder="全部" clearable style="width:110px" @change="loadLogs">
                <el-option label="成功" value="PASS" />
                <el-option label="失败" value="FAIL" />
                <el-option label="处理中" value="PENDING" />
              </el-select>
            </el-form-item>
            <el-form-item label="领用人/执行人">
              <el-input v-model="logQ.borrowerName" placeholder="姓名" clearable style="width:120px" @keyup.enter="loadLogs" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" plain size="small" :icon="Refresh" @click="loadLogs">刷新</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="logList.data.rows || []" v-loading="logList.loading" stripe border row-key="logId" size="default">
            <el-table-column label="时间" prop="eventTime" width="168" />
            <el-table-column label="工装" width="170">
              <template #default="{ row }">
                <el-tag size="small" style="margin-right:6px">{{ row.jigNo }}</el-tag>
                <span>{{ (jigMap.get(row.jigId)?.jigName) || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="动作" width="110" align="center">
              <template #default="{ row }">
                <component :is="eventTag(row.eventType).tag" :type="eventTag(row.eventType).type" size="small" effect="plain">
                  {{ eventTag(row.eventType).label }}
                </component>
              </template>
            </el-table-column>
            <el-table-column label="标题" prop="eventTitle" min-width="220" show-overflow-tooltip />
            <el-table-column label="结果" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.eventResult==='PASS'"    type="success" size="small">成功</el-tag>
                <el-tag v-else-if="row.eventResult==='FAIL'" type="danger"  size="small">失败</el-tag>
                <el-tag v-else-if="row.eventResult==='PENDING'" type="warning" size="small">处理中</el-tag>
                <span v-else style="color:var(--el-text-color-secondary)">-</span>
              </template>
            </el-table-column>
            <el-table-column label="使用次数" width="96" align="right">
              <template #default="{ row }">
                <span v-if="row.eventType === 'RETURN'">+{{ row.usedCountAdd || 0 }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="花费¥" prop="costAmount" width="90" align="right">
              <template #default="{ row }">{{ formatNum(row.costAmount) }}</template>
            </el-table-column>
            <el-table-column label="执行人/领用人" width="120">
              <template #default="{ row }">
                <span v-if="row.operatorName">{{ row.operatorName }}</span>
                <span v-else-if="row.borrowerName">{{ row.borrowerName }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="详细说明" prop="remark" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" v-hasPermi="'pm:jig:remove'" @click="handleLogRemove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" v-model:current-page="logQ.pageNum" v-model:page-size="logQ.pageSize"
            :total="logList.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadLogs" @current-change="loadLogs" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ========= 工装新增/编辑 Dialog ========= -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增工装治具' : '修改工装治具'" width="920px" destroy-on-close top="6vh">
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-tabs type="border-card">
          <el-tab-pane label="基础信息" name="base">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="工装编号" prop="jigNo"><el-input v-model="editDlg.form.jigNo" :disabled="!editDlg.isNew" maxlength="50" placeholder="如 JIG-01" /></el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="工装名称" prop="jigName"><el-input v-model="editDlg.form.jigName" maxlength="150" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="工装分类" prop="jigType">
                  <el-select v-model="editDlg.form.jigType" placeholder="请选择" style="width:100%">
                    <el-option v-for="t in JIG_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="产品机种编码">
                  <el-input v-model="editDlg.form.productCode" placeholder="关联 pdm_product.product_code" maxlength="50" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="机种名称">
                  <el-input v-model="editDlg.form.productName" maxlength="150" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="供应商名称"><el-input v-model="editDlg.form.vendorName" maxlength="150" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="制作方/厂家"><el-input v-model="editDlg.form.maker" maxlength="100" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="厚度(mm)">
                  <el-input-number v-model="editDlg.form.thicknessMm" :min="0" :precision="2" :max="10" style="width:100%" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="材质/板材">
                  <el-select v-model="editDlg.form.material" allow-create default-first-option filterable placeholder="如 AL5052/电木/SUS304" style="width:100%">
                    <el-option label="激光钢片 304" value="激光钢片 304" />
                    <el-option label="AL5052 铝" value="AL5052" />
                    <el-option label="电木 (合成石)" value="Bakelite" />
                    <el-option label="SUS304 不锈钢" value="SUS304" />
                    <el-option label="FR4 环氧板" value="FR4" />
                    <el-option label="Durostone 合成石" value="Durostone" />
                    <el-option label="ESD 防静电" value="ESD" />
                    <el-option label="铜合金" value="Copper" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="工艺规格/网厚/备注规格">
                  <el-input v-model="editDlg.form.specification" placeholder="如 电铸100μm / 阶梯0.12mm / 12腔 / 370x470mm 等" maxlength="255" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="入库日期">
                  <el-date-picker v-model="editDlg.form.receiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="当前状态" prop="status">
                  <el-select v-model="editDlg.form.status" style="width:100%">
                    <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="单套成本(¥)">
                  <el-input-number v-model="editDlg.form.costAmount" :min="0" :precision="2" style="width:100%" controls-position="right" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
          <el-tab-pane label="寿命/库存/数量" name="life">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="寿命阈值(次数)" prop="lifeCount">
                  <el-input-number v-model="editDlg.form.lifeCount" :min="0" :max="9999999" style="width:100%" controls-position="right" />
                  <div style="color:var(--el-text-color-secondary);font-size:12px;line-height:1.2">0 = 不限制寿命; 达到100%禁止再借用</div>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="已使用次数">
                  <el-input-number v-model="editDlg.form.usedCount" :min="0" style="width:100%" controls-position="right" />
                  <div style="color:var(--el-text-color-secondary);font-size:12px;line-height:1.2">一般自动累加(归还时 usedCountAdd), 无需手动改</div>
                </el-form-item>
              </el-col>
              <el-col :span="8"></el-col>
              <el-col :span="8">
                <el-form-item label="当前总套数" prop="qtyCurrent">
                  <el-input-number v-model="editDlg.form.qtyCurrent" :min="0" style="width:100%" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="可用套数(闲置)" prop="qtyAvailable">
                  <el-input-number v-model="editDlg.form.qtyAvailable" :min="0" style="width:100%" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="借出中套数" prop="qtyBorrowed">
                  <el-input-number v-model="editDlg.form.qtyBorrowed" :min="0" style="width:100%" controls-position="right" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
          <el-tab-pane label="校准/存放/保管" name="cal">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="校准周期(天)" prop="calCycleDays">
                  <el-input-number v-model="editDlg.form.calCycleDays" :min="0" :max="720" style="width:100%" controls-position="right" />
                  <div style="color:var(--el-text-color-secondary);font-size:12px;line-height:1.2">0 = 无需校准</div>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="上次校准">
                  <el-date-picker v-model="editDlg.form.lastCalDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="下次校准">
                  <el-date-picker v-model="editDlg.form.nextCalDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="存放仓库">
                  <el-input v-model="editDlg.form.warehouse" placeholder="如 SMT治具库" maxlength="100" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="存放位置">
                  <el-input v-model="editDlg.form.location" placeholder="如 3号架-A层-2位" maxlength="100" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="保管人姓名">
                  <el-input v-model="editDlg.form.keeperName" maxlength="64" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="附件/图片URL (逗号分隔)">
                  <el-input v-model="editDlg.form.attachmentUrl" type="textarea" :rows="2" maxlength="500" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="备注"><el-input v-model="editDlg.form.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
        </el-tabs>
        <template #footer>
          <el-button @click="editDlg.show = false">取消</el-button>
          <el-button type="primary" @click="submitEdit">确定</el-button>
        </template>
      </el-form>
    </el-dialog>

    <!-- ========= 6 大动作 Dialog ========= -->
    <el-dialog v-model="actionDlg.show" :title="actionDlg.title" width="640px" destroy-on-close top="10vh">
      <el-form :model="actionDlg.form" :rules="actionRulesMap[actionDlg.action || 'borrow']" ref="actionFormRef" label-width="110px">
        <el-row :gutter="16">
          <!-- 通用：事件标题 / 结果 / 时间 / 花费 / 备注 -->
          <el-col :span="24">
            <el-form-item label="动作标题" prop="eventTitle">
              <el-input v-model="actionDlg.form.eventTitle" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="动作结果">
              <el-select v-model="actionDlg.form.eventResult" placeholder="结果" style="width:100%">
                <el-option label="成功" value="PASS" />
                <el-option label="失败" value="FAIL" />
                <el-option label="处理中" value="PENDING" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发生时间" prop="eventTime">
              <el-date-picker v-model="actionDlg.form.eventTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="花费(¥)">
              <el-input-number v-model="actionDlg.form.costAmount" :min="0" :precision="2" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行人姓名">
              <el-input v-model="actionDlg.form.operatorName" maxlength="64" placeholder="谁执行的校准/维修/修磨..." />
            </el-form-item>
          </el-col>

          <!-- ============= 领用 ============= -->
          <template v-if="actionDlg.action === 'borrow'">
            <el-col :span="12"><el-form-item label="领用人" prop="borrowerName"><el-input v-model="actionDlg.form.borrowerName" maxlength="64" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="计划归还日">
                <el-date-picker v-model="actionDlg.form.planReturnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
            </el-col>
          </template>

          <!-- ============= 归还 ============= -->
          <template v-if="actionDlg.action === 'return'">
            <el-col :span="12">
              <el-form-item label="本次使用次数" prop="usedCountAdd">
                <el-input-number v-model="actionDlg.form.usedCountAdd" :min="0" :max="999999" style="width:100%" controls-position="right" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="实际归还日">
                <el-date-picker v-model="actionDlg.form.realReturnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="24" style="color:var(--el-text-color-secondary);font-size:12px;margin:-6px 0 8px">
              归还后会把【本次使用次数】累加到「累计使用次数 usedCount」, 并【自动】修改：可用+1 / 借出-1 / 领用人清空 / 寿命达到100%自动转修磨中(4)
            </el-col>
          </template>

          <!-- ============= 校准/张力测试 ============= -->
          <template v-if="actionDlg.action === 'calibrate'">
            <el-col :span="8">
              <el-form-item label="测量项">
                <el-select v-model="actionDlg.form.measureItem" placeholder="钢网张力/探针压力/扭力" style="width:100%">
                  <el-option label="钢网张力" value="张力Tension" />
                  <el-option label="探针压力" value="探针压力Pressure" />
                  <el-option label="扭力(扳手)" value="扭力Torque" />
                  <el-option label="外观目视" value="目视Appearance" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="单位">
                <el-input v-model="actionDlg.form.measureUnit" placeholder="N/cm / g / N·m" maxlength="10" />
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="测量值">
                <el-input-number v-model="actionDlg.form.measureValue" :precision="3" style="width:100%" controls-position="right" />
              </el-form-item>
            </el-col>
            <el-col :span="3">
              <el-form-item label="Min">
                <el-input-number v-model="actionDlg.form.measureMin" :precision="3" style="width:100%" controls-position="right" />
              </el-form-item>
            </el-col>
            <el-col :span="3">
              <el-form-item label="Max">
                <el-input-number v-model="actionDlg.form.measureMax" :precision="3" style="width:100%" controls-position="right" />
              </el-form-item>
            </el-col>
          </template>

          <!-- ============= 修磨/更换钢片 ============= -->
          <template v-if="actionDlg.action === 'resharpen'">
            <el-col :span="24" style="color:var(--el-color-primary);font-size:12px;margin:-6px 0 8px">
              修磨/PASS后会【重置寿命】(used_count = 0) 并把状态改回闲置(0); 用于钢网重新张网/更换钢片/更换分板PIN/治具海绵板等场景
            </el-col>
          </template>

          <!-- ============= 报废 ============= -->
          <template v-if="actionDlg.action === 'scrap'">
            <el-col :span="24" style="color:#b42318;font-size:12px;margin:-6px 0 8px">
              报废会扣减 qty_current / qty_available 各1套；若总套数被扣到 0，工装状态会自动变成报废(6)。
            </el-col>
          </template>

          <el-col :span="24">
            <el-form-item label="详细说明">
              <el-input v-model="actionDlg.form.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit
                :placeholder="actionDlg.action === 'repair' ? '故障现象/更换配件/维修经过/是否返厂...' : '动作详细说明, 配件更换记录, 测量过程记录等'" />
            </el-form-item>
          </el-col>
        </el-row>
        <template #footer>
          <el-button @click="actionDlg.show = false">取消</el-button>
          <el-button type="primary" :loading="actionDlg.submitting" @click="submitAction">执行{{ actionDlg.titleText }}</el-button>
        </template>
      </el-form>
    </el-dialog>

    <!-- ========= 单工装 动作日志 Drawer (含详情 + Timeline) ========= -->
    <el-drawer v-model="logDrawer.show" :title="`工装 ${logDrawer.jig?.jigNo || ''} 档案与动作流水`" size="760px" destroy-on-close>
      <div v-if="logDrawer.jig" style="padding:8px 2px 12px">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="工装名称" :span="2">{{ logDrawer.jig.jigName }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <component :is="statusTag(logDrawer.jig.status).tag" :type="statusTag(logDrawer.jig.status).type" size="small">
              {{ statusTag(logDrawer.jig.status).label }}
            </component>
          </el-descriptions-item>
          <el-descriptions-item label="分类">{{ jigTypeLabel(logDrawer.jig.jigType) }}</el-descriptions-item>
          <el-descriptions-item label="机种">{{ logDrawer.jig.productCode || '-' }}{{ logDrawer.jig.productName ? '('+logDrawer.jig.productName+')' : '' }}</el-descriptions-item>
          <el-descriptions-item label="材质/规格">
            <div style="line-height:1.3">{{ logDrawer.jig.material || '-' }}<div style="color:var(--el-text-color-secondary);font-size:12px">{{ logDrawer.jig.specification || '' }}</div></div>
          </el-descriptions-item>
          <el-descriptions-item label="数量">
            <el-tag size="small" type="success">{{ logDrawer.jig.qtyAvailable ?? 0 }}</el-tag>
            <span style="color:var(--el-text-color-secondary)"> / </span>
            <el-tag size="small" type="warning">{{ logDrawer.jig.qtyBorrowed ?? 0 }}</el-tag>
            <span style="color:var(--el-text-color-secondary)"> / {{ logDrawer.jig.qtyCurrent ?? 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="寿命进度" :span="2">
            <el-progress
              v-if="(logDrawer.jig.lifeCount || 0) > 0"
              :percentage="Math.min(100, Math.round(((logDrawer.jig.usedCount || 0) / logDrawer.jig.lifeCount) * 100))"
              :color="lifeColor(logDrawer.jig as any)"
              :stroke-width="12"
              :format="p => `${p}% (${logDrawer.jig.usedCount || 0}/${logDrawer.jig.lifeCount})`"
            />
            <span v-else style="color:var(--el-text-color-secondary);font-size:12px">已使用 {{ logDrawer.jig.usedCount || 0 }} 次 (不限寿命)</span>
          </el-descriptions-item>
          <el-descriptions-item label="存放地">{{ logDrawer.jig.warehouse || '-' }} {{ logDrawer.jig.location || '' }}</el-descriptions-item>
          <el-descriptions-item label="保管人">{{ logDrawer.jig.keeperName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前领用人">{{ logDrawer.jig.borrowerName || '未借出' }}</el-descriptions-item>
          <el-descriptions-item label="上次校准">{{ logDrawer.jig.lastCalDate || '无记录' }}</el-descriptions-item>
          <el-descriptions-item label="下次校准">
            <el-tag v-if="logDrawer.jig.nextCalDate" :type="daysFromToday(logDrawer.jig.nextCalDate) < 15 ? 'warning' : 'success'" size="small">
              {{ logDrawer.jig.nextCalDate }}
            </el-tag>
            <span v-else style="color:var(--el-text-color-secondary)">{{ (logDrawer.jig.calCycleDays || 0) > 0 ? '未设定' : '无需校准' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="单套成本(¥)">¥ {{ formatNum(logDrawer.jig.costAmount) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ logDrawer.jig.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 6 大动作快捷按钮 -->
      <div v-if="logDrawer.jig" style="margin-bottom:10px;display:flex;flex-wrap:wrap;gap:8px">
        <el-button type="primary" plain size="small" :icon="ShoppingBag"    @click="openActionFromDrawer('borrow')">领用</el-button>
        <el-button type="success" plain size="small" :icon="RefreshLeft" @click="openActionFromDrawer('return')">归还</el-button>
        <el-button type="warning" plain size="small" :icon="Cpu"    @click="openActionFromDrawer('calibrate')">校准</el-button>
        <el-button plain size="small"       :icon="Promotion" style="border-color:#5856d6;color:#5856d6" @click="openActionFromDrawer('resharpen')">修磨(寿命清零)</el-button>
        <el-button type="danger"  plain size="small" :icon="Tools"  @click="openActionFromDrawer('repair')">维修</el-button>
        <el-button plain size="small"       :icon="Delete"  style="border-color:#b42318;color:#b42318" v-hasPermi="'pm:jig:scrap'" @click="openActionFromDrawer('scrap')">报废</el-button>
      </div>

      <div style="margin-top:12px;border-top:1px dashed var(--el-border-color);padding-top:12px">
        <div style="font-weight:600;font-size:14px;margin-bottom:8px">动作流水 Timeline</div>
        <el-timeline v-if="logDrawer.rows.length > 0">
          <el-timeline-item
            v-for="lg in logDrawer.rows"
            :key="lg.logId"
            :timestamp="lg.eventTime"
            :type="eventTag(lg.eventType).type as any"
            :hollow="lg.eventResult !== 'PASS'">
            <el-card shadow="never" class="timeline-card" size="small">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
                <div style="display:flex;align-items:center;gap:8px">
                  <el-tag :type="eventTag(lg.eventType).type" size="small" effect="plain">{{ eventTag(lg.eventType).label }}</el-tag>
                  <b>{{ lg.eventTitle }}</b>
                </div>
                <div>
                  <el-tag v-if="lg.eventResult==='PASS'"    size="small" type="success" effect="dark">成功</el-tag>
                  <el-tag v-else-if="lg.eventResult==='FAIL'" size="small" type="danger"  effect="dark">失败</el-tag>
                  <el-tag v-else-if="lg.eventResult==='PENDING'" size="small" type="warning">处理中</el-tag>
                </div>
              </div>
              <div style="color:var(--el-text-color-secondary);font-size:12px;display:flex;flex-wrap:wrap;gap:12px;line-height:1.8">
                <div v-if="lg.borrowerName"><b>领用人:</b> {{ lg.borrowerName }}</div>
                <div v-if="lg.planReturnDate"><b>计划归还:</b> {{ lg.planReturnDate }}</div>
                <div v-if="lg.realReturnDate"><b>实际归还:</b> {{ lg.realReturnDate }}</div>
                <div v-if="lg.eventType==='RETURN'"><b>加使用次数:</b> <span style="color:var(--el-color-success)">+{{ lg.usedCountAdd || 0 }}</span></div>
                <div v-if="lg.measureItem"><b>{{ lg.measureItem }}:</b> {{ lg.measureValue }}{{ lg.measureUnit || '' }}
                  <span v-if="lg.measureMin != null || lg.measureMax != null" style="color:var(--el-text-color-secondary)">
                    (范围: {{ lg.measureMin ?? '-' }} ~ {{ lg.measureMax ?? '-' }} {{ lg.measureUnit || '' }})
                  </span>
                </div>
                <div><b>花费:</b> ¥ {{ formatNum(lg.costAmount) }}</div>
                <div v-if="lg.operatorName"><b>执行人:</b> {{ lg.operatorName }}</div>
              </div>
              <div v-if="lg.remark" style="margin-top:6px;font-size:13px;border-left:3px solid var(--el-color-primary-light-5);padding-left:8px">
                {{ lg.remark }}
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无动作流水, 请先登记领用/校准/归还等动作" />
      </div>
    </el-drawer>

    <!-- 打印预览弹窗 -->
    <el-dialog v-model="calibPrintVisible" title="打印预览 - 工装治具校准记录" width="800px" :close-on-click-modal="false">
      <JigCalibrationPrint :jig="calibPrintRow" :visible="calibPrintVisible" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Plus, ShoppingBag, RefreshLeft, Promotion, Cpu, Tools, Delete, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { pmJigApi } from '@/api/index'
import type { PmmJig, PmmJigLog } from '@/types/models'
import JigCalibrationPrint from '@/views/print/JigCalibrationPrint.vue'

// ========== 打印预览 ==========
const calibPrintVisible = ref(false)
const calibPrintRow = ref<any>(null)
const openCalibPrint = (row: any) => { calibPrintRow.value = row; calibPrintVisible.value = true }

// ========== 字典 ==========
const STATUS_OPTIONS: { label: string; value: number }[] = [
  { label: '闲置',   value: 0 },
  { label: '在用',   value: 1 },
  { label: '借出',   value: 2 },
  { label: '校准中', value: 3 },
  { label: '修磨中', value: 4 },
  { label: '维修中', value: 5 },
  { label: '报废',   value: 6 },
]
const JIG_TYPE_OPTIONS: { label: string; value: string }[] = [
  { label: '钢网',              value: 'STENCIL' },
  { label: '载具(贴片/波峰焊)', value: 'CARRIER' },
  { label: 'FCT功能测试治具',   value: 'FCT_FIXTURE' },
  { label: 'ICT在线测试治具',   value: 'ICT_FIXTURE' },
  { label: '回流焊治具',        value: 'REFLOW_FIXTURE' },
  { label: '分板/铣刀治具',     value: 'ROUTER_FIXTURE' },
  { label: '工具类(扭力扳手等)',value: 'TOOL' },
  { label: '其他工装',          value: 'OTHER' },
]
const EVENT_TYPE_OPTIONS: { label: string; value: string }[] = [
  { label: '入库',       value: 'INBOUND' },
  { label: '领用',       value: 'BORROW' },
  { label: '归还',       value: 'RETURN' },
  { label: '校准/张力', value: 'CALIBRATE' },
  { label: '修磨/更换', value: 'RESHARPEN' },
  { label: '维修',       value: 'REPAIR' },
  { label: '报废',       value: 'SCRAP' },
  { label: '抽检',       value: 'INSPECT' },
  { label: '其他',       value: 'OTHER' },
]
const jigTypeLabel = (v?: string) => JIG_TYPE_OPTIONS.find(x => x.value === v)?.label || (v || '-')
const jigTypeTag = (v?: string) => {
  const map: Record<string, any> = {
    STENCIL:       'primary',
    CARRIER:       'warning',
    FCT_FIXTURE:   'success',
    ICT_FIXTURE:   'warning',
    REFLOW_FIXTURE:'info',
    ROUTER_FIXTURE:'danger',
    TOOL:          'danger',
    OTHER:         'info',
  }
  return map[v || ''] || ''
}
const statusTag = (v: any) => {
  const map: Record<string, { label: string; type: any; tag: any }> = {
    '0': { label: '闲置',   type: 'info',    tag: 'ElTag' },
    '1': { label: '在用',   type: 'success', tag: 'ElTag' },
    '2': { label: '借出',   type: 'warning', tag: 'ElTag' },
    '3': { label: '校准中', type: 'warning', tag: 'ElTag' },
    '4': { label: '修磨中', type: 'primary', tag: 'ElTag' },
    '5': { label: '维修中', type: 'danger',  tag: 'ElTag' },
    '6': { label: '报废',   type: 'danger',  tag: 'ElTag' },
  }
  return map[String(v)] || { label: '-', type: 'info', tag: 'ElTag' }
}
const eventTag = (v?: string) => {
  const map: Record<string, { label: string; type: any; tag: any }> = {
    INBOUND:   { label: '入库',       type: 'success', tag: 'ElTag' },
    BORROW:    { label: '领用',       type: 'primary', tag: 'ElTag' },
    RETURN:    { label: '归还',       type: 'success', tag: 'ElTag' },
    CALIBRATE: { label: '校准/张力',  type: 'warning', tag: 'ElTag' },
    RESHARPEN: { label: '修磨/更换',  type: 'primary', tag: 'ElTag' },
    REPAIR:    { label: '维修',       type: 'danger',  tag: 'ElTag' },
    SCRAP:     { label: '报废',       type: 'danger',  tag: 'ElTag' },
    INSPECT:   { label: '抽检',       type: 'info',    tag: 'ElTag' },
    OTHER:     { label: '其他',       type: 'info',    tag: 'ElTag' },
  }
  return map[v || ''] || { label: v || '-', type: 'info', tag: 'ElTag' }
}
const lifeColor = (row: PmmJig) => {
  if (!row.lifeCount) return '#67c23a'
  const p = (row.usedCount || 0) / row.lifeCount
  if (p >= 1) return '#f56c6c'      // 红: 超过寿命
  if (p >= 0.9) return '#e6a23c'    // 橙: 90%以上预警
  return '#67c23a'                   // 绿: 安全
}

// ========== 工具 ==========
const formatNum = (n: any, digits = 2) => {
  if (n === null || n === undefined || n === '') return '0'
  const x = Number(n)
  if (Number.isNaN(x)) return '0'
  return x.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: digits })
}
const daysFromToday = (s?: string) => {
  if (!s) return 9999
  const d = new Date(s), t = new Date()
  t.setHours(0, 0, 0, 0)
  return Math.round((d.getTime() - t.getTime()) / 86400000)
}

// ========== 搜索/列表 ==========
const activeTab = ref<'jig' | 'log'>('jig')
const q = reactive({
  pageNum: 1, pageSize: 20,
  jigNo: '', jigName: '', jigType: '' as string | undefined,
  productCode: '', productName: '', status: undefined as number | undefined,
  keeper: '', warehouse: '', location: '', borrowerName: ''
})
const jigList = reactive({ loading: false, data: { rows: [] as PmmJig[], total: 0 } })
const jigOptions = ref<PmmJig[]>([])
const jigMap = computed(() => {
  const m = new Map<number, PmmJig>()
  jigOptions.value.forEach(e => { if (e.jigId) m.set(e.jigId, e) })
  return m
})
const loadList = async () => {
  jigList.loading = true
  try {
    const r = await pmJigApi.list({ ...q })
    jigList.data = { rows: r?.rows || [], total: r?.total || 0 }
  } finally { jigList.loading = false }
}
const loadOptions = async () => {
  try { jigOptions.value = (await pmJigApi.options()) || [] } catch {}
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20; q.jigNo = ''; q.jigName = ''; q.jigType = ''
  q.productCode = ''; q.productName = ''; q.status = undefined
  q.keeper = ''; q.warehouse = ''; q.location = ''; q.borrowerName = ''
  loadList()
}

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; form: Partial<PmmJig> }>({ show: false, isNew: true, form: {} })
const editFormRef = ref<FormInstance>()
const editRules: FormRules = {
  jigNo:      [{ required: true, message: '工装编号不能为空', trigger: 'blur' }],
  jigName:    [{ required: true, message: '工装名称不能为空', trigger: 'blur' }],
  jigType:    [{ required: true, message: '请选择工装分类', trigger: 'change' }],
  status:     [{ required: true, message: '请选择当前状态', trigger: 'change' }],
  lifeCount:  [{ required: true, message: '请填写寿命阈值(无寿命填0)', trigger: 'blur' }],
  calCycleDays: [{ required: true, message: '请填写校准周期(无需校准填0)', trigger: 'blur' }],
  qtyCurrent: [{ required: true, message: '请填写当前总套数', trigger: 'blur' }],
  qtyAvailable: [{ required: true, message: '请填写可用套数', trigger: 'blur' }],
  qtyBorrowed:  [{ required: true, message: '请填写借出中套数', trigger: 'blur' }],
}
const openAdd = () => {
  editDlg.isNew = true
  editDlg.form = {
    status: 0, lifeCount: 100000, usedCount: 0, calCycleDays: 30,
    qtyCurrent: 1, qtyAvailable: 1, qtyBorrowed: 0, costAmount: 0,
    receiveDate: new Date().toISOString().slice(0, 10)
  }
  editDlg.show = true
}
const openEdit = (row: PmmJig) => {
  editDlg.isNew = false
  editDlg.form = JSON.parse(JSON.stringify(row))
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await editFormRef.value?.validate().catch(() => false)
  if (!ok) return
  try {
    if (editDlg.isNew) {
      await pmJigApi.add(editDlg.form as any)
      ElMessage.success('新增工装成功, 已自动登记入库流水')
    } else {
      await pmJigApi.edit(editDlg.form as any)
      ElMessage.success('修改成功')
    }
    editDlg.show = false
    await Promise.all([loadList(), loadOptions()])
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败') }
}
const handleRemove = async (row: PmmJig) => {
  try {
    await ElMessageBox.confirm(`仅【报废(6)】状态治具允许删除。\n确认删除工装【${row.jigNo} ${row.jigName}】？`, '删除确认', { type: 'warning' })
    await pmJigApi.remove(row.jigId!)
    ElMessage.success('删除成功')
    await Promise.all([loadList(), loadOptions()])
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== 6 大动作 ==========
type ActKey = 'borrow' | 'return' | 'calibrate' | 'resharpen' | 'repair' | 'scrap'
const actionDlg = reactive<{
  show: boolean
  action?: ActKey
  title: string
  titleText: string
  submitting: boolean
  jig?: PmmJig
  form: Partial<PmmJigLog>
}>({ show: false, title: '', titleText: '', submitting: false, form: {} })
const actionFormRef = ref<FormInstance>()
const actionRulesMap: Record<ActKey, FormRules> = {
  borrow:    { eventTitle: [{ required: true, message: '请填写动作标题', trigger: 'blur' }],
               eventTime:  [{ required: true, message: '请选择发生时间', trigger: 'change' }],
               borrowerName: [{ required: true, message: '请填写领用人姓名', trigger: 'blur' }] },
  return:    { eventTitle: [{ required: true, message: '请填写动作标题', trigger: 'blur' }],
               eventTime:  [{ required: true, message: '请选择发生时间', trigger: 'change' }],
               usedCountAdd: [{ required: true, message: '归还必须填写 本次使用次数', trigger: 'blur' }] },
  calibrate: { eventTitle: [{ required: true, message: '请填写动作标题', trigger: 'blur' }],
               eventTime:  [{ required: true, message: '请选择发生时间', trigger: 'change' }] },
  resharpen: { eventTitle: [{ required: true, message: '请填写动作标题', trigger: 'blur' }],
               eventTime:  [{ required: true, message: '请选择发生时间', trigger: 'change' }] },
  repair:    { eventTitle: [{ required: true, message: '请填写动作标题', trigger: 'blur' }],
               eventTime:  [{ required: true, message: '请选择发生时间', trigger: 'change' }] },
  scrap:     { eventTitle: [{ required: true, message: '请填写动作标题', trigger: 'blur' }],
               eventTime:  [{ required: true, message: '请选择发生时间', trigger: 'change' }] },
}
const ACT_TITLE: Record<ActKey, string> = {
  borrow: '领用登记', return: '归还登记', calibrate: '校准/张力测试',
  resharpen: '修磨/更换钢片(寿命清零)', repair: '维修工单执行', scrap: '报废登记'
}
const onAction = (cmd: string, row: PmmJig) => {
  if (!row.jigId) return
  const act = cmd as ActKey
  if (act === 'borrow' &&
      row.lifeCount && row.lifeCount > 0 &&
      (row.usedCount || 0) >= row.lifeCount) {
    ElMessageBox.alert(`该治具已达到寿命阈值 (${row.usedCount}/${row.lifeCount}次), 请先【修磨/更换钢片】后再领用。`,
      '寿命到期禁止领用', { type: 'warning' })
    return
  }
  if (act === 'scrap') {
    ElMessageBox.confirm(`确认对工装【${row.jigNo} ${row.jigName}】执行报废动作(扣1套)?`,
      '报废确认', { type: 'warning' }).then(() => openActionInner(act, row)).catch(() => {})
    return
  }
  openActionInner(act, row)
}
const openActionInner = (act: ActKey, row: PmmJig) => {
  actionDlg.jig = row
  actionDlg.action = act
  actionDlg.title = `${ACT_TITLE[act]} — ${row.jigNo} ${row.jigName}`
  actionDlg.titleText = ACT_TITLE[act]
  actionDlg.submitting = false
  const now = new Date().toISOString().slice(0, 19).replace('T', ' ')
  actionDlg.form = {
    jigId: row.jigId, jigNo: row.jigNo,
    eventType: act.toUpperCase(),
    eventTitle: `${row.jigName} ${ACT_TITLE[act]}`,
    eventResult: 'PASS',
    eventTime: now,
    costAmount: 0,
  }
  // 动作特殊字段默认
  if (act === 'borrow') {
    actionDlg.form.planReturnDate = new Date(Date.now() + 3*86400000).toISOString().slice(0, 10)
  }
  if (act === 'return') {
    actionDlg.form.usedCountAdd = 0
    actionDlg.form.realReturnDate = now.slice(0, 10)
  }
  if (act === 'calibrate') {
    if (row.jigType === 'STENCIL') {
      actionDlg.form.measureItem = '张力Tension'
      actionDlg.form.measureUnit = 'N/cm'
      actionDlg.form.measureValue = 40
      actionDlg.form.measureMin = 30
      actionDlg.form.measureMax = 50
    } else if (row.jigType === 'TOOL') {
      actionDlg.form.measureItem = '扭力Torque'
      actionDlg.form.measureUnit = 'N·m'
      actionDlg.form.measureValue = 5
      actionDlg.form.measureMin = 0
      actionDlg.form.measureMax = 10
    } else if (row.jigType === 'ICT_FIXTURE' || row.jigType === 'FCT_FIXTURE') {
      actionDlg.form.measureItem = '探针压力Pressure'
      actionDlg.form.measureUnit = 'g'
      actionDlg.form.measureValue = 150
      actionDlg.form.measureMin = 100
      actionDlg.form.measureMax = 220
    }
  }
  actionDlg.show = true
}
const openActionFromDrawer = (act: ActKey) => {
  if (!logDrawer.jig) return
  openActionInner(act, logDrawer.jig)
}
const submitAction = async () => {
  const ok = await actionFormRef.value?.validate().catch(() => false)
  if (!ok || !actionDlg.action || !actionDlg.jig?.jigId) return
  actionDlg.submitting = true
  try {
    const fn: Record<ActKey, any> = {
      borrow:    pmJigApi.borrow,
      return:    pmJigApi.returnBack,
      calibrate: pmJigApi.calibrate,
      resharpen: pmJigApi.resharpen,
      repair:    pmJigApi.repair,
      scrap:     pmJigApi.scrap,
    }
    await fn[actionDlg.action](actionDlg.jig.jigId, actionDlg.form)
    ElMessage.success(`${ACT_TITLE[actionDlg.action]} 执行成功, 状态/数量/寿命同步已刷新`)
    actionDlg.show = false
    await Promise.all([loadList(), loadOptions(), loadLogs()])
    if (logDrawer.show) {
      try { logDrawer.jig = await pmJigApi.get(logDrawer.jig.jigId!) } catch {}
      await loadDrawerRows()
    }
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || e?.message || '执行失败') }
  finally { actionDlg.submitting = false }
}

// ========== 全局动作日志 Tab ==========
const logQ = reactive({ pageNum: 1, pageSize: 20, jigId: undefined as number | undefined,
  jigNo: '' as string | undefined, eventType: '' as string | undefined,
  eventTitle: '' as string | undefined, eventResult: '' as string | undefined,
  borrowerName: '' as string | undefined, startTime: '' as string | undefined,
  endTime: '' as string | undefined })
const logList = reactive({ loading: false, data: { rows: [] as PmmJigLog[], total: 0 } })
const loadLogs = async () => {
  logList.loading = true
  try {
    const r = await pmJigApi.logList({ ...logQ })
    logList.data = { rows: r?.rows || [], total: r?.total || 0 }
  } finally { logList.loading = false }
}
const handleLogRemove = async (row: PmmJigLog) => {
  try {
    await ElMessageBox.confirm(`确认删除日志【${row.eventTitle}】？`, '删除确认', { type: 'warning' })
    await pmJigApi.logRemove(row.logId!)
    ElMessage.success('已删除')
    loadLogs()
    if (logDrawer.show && logDrawer.jig?.jigId === row.jigId) loadDrawerRows()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

// ========== 单工装 日志 Drawer ==========
const logDrawer = reactive<{
  show: boolean; jig: PmmJig | null; rows: PmmJigLog[]
}>({ show: false, jig: null, rows: [] })
const openLogDrawer = async (row: PmmJig) => {
  logDrawer.jig = row
  logDrawer.show = true
  await loadDrawerRows()
}
const loadDrawerRows = async () => {
  if (!logDrawer.jig?.jigId) return
  try {
    const r = await pmJigApi.logList({ pageNum: 1, pageSize: 200, jigId: logDrawer.jig.jigId })
    logDrawer.rows = [...(r?.rows || [])].sort((a, b) => (b.eventTime || '').localeCompare(a.eventTime || ''))
  } catch {}
}

onMounted(() => { loadList(); loadOptions(); loadLogs() })
</script>

<style scoped>
.pm-page { display: flex; flex-direction: column; gap: 14px; }
.search-card :deep(.el-form-item) { margin-bottom: 0; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.pager { margin-top: 14px; justify-content: flex-end; display: flex; }
.timeline-card { margin-right: 8px; }
.timeline-card :deep(.el-card__body) { padding: 10px 12px; }
</style>
