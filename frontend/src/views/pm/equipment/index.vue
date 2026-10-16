<template>
  <div class="pm-page">
    <!-- ========= 搜索栏 ========= -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="设备编码">
          <el-input v-model="q.equipmentNo" placeholder="如 SMT-01" clearable @keyup.enter="loadList" style="width:150px" />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="q.equipmentName" placeholder="模糊搜索" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="q.equipmentType" placeholder="全部" clearable style="width:130px" @change="loadList">
            <el-option v-for="t in EQUIPMENT_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:110px" @change="loadList">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="q.equipLevel" placeholder="全部" clearable style="width:100px" @change="loadList">
            <el-option label="关键设备" :value="1" />
            <el-option label="一般设备" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="车间">
          <el-input v-model="q.workshop" placeholder="SMT车间" clearable @keyup.enter="loadList" style="width:130px" />
        </el-form-item>
        <el-form-item label="工作中心">
          <el-input v-model="q.workCenter" placeholder="SMT-01" clearable @keyup.enter="loadList" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ========= Tabs: 设备列表 / 事件日志 ========= -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />设备管理</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:equipment:add'" @click="openAdd">新增设备</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- ===== Tab 1: 设备档案 ===== -->
        <el-tab-pane label="设备档案" name="equip">
          <el-table :data="equipList.data.rows || []" v-loading="equipList.loading" stripe border row-key="equipmentId" size="default">
            <el-table-column label="等级" width="82" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.equipLevel === 1" type="danger" size="small" effect="dark">关键</el-tag>
                <el-tag v-else type="info" size="small">一般</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="编码" prop="equipmentNo" width="92" fixed />
            <el-table-column label="设备名称" prop="equipmentName" min-width="220" show-overflow-tooltip />
            <el-table-column label="类型" width="98" align="center">
              <template #default="{ row }">
                <span :title="equipmentTypeLabel(row.equipmentType)">{{ equipmentTypeLabel(row.equipmentType, 5) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="品牌/型号" width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <div style="line-height:1.3;font-size:12px">
                  <div><b>{{ row.brand || '-' }}</b></div>
                  <div style="color:var(--el-text-color-secondary)">{{ row.model || '-' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="车间/工作中心" width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <div style="line-height:1.3;font-size:12px">
                  <div>{{ row.workshop || '-' }}</div>
                  <div style="color:var(--el-text-color-secondary)">{{ row.workCenter || '-' }}</div>
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
            <el-table-column label="产能" width="120" align="right">
              <template #default="{ row }">
                <div>{{ formatNum(row.capacityPerHour) }}</div>
                <div style="color:var(--el-text-color-secondary);font-size:12px">{{ row.capacityUnit || 'PCS' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="下次保养" width="112" align="center">
              <template #default="{ row }">
                <el-tooltip v-if="row.nextMaintainDate" :content="'上次保养: ' + (row.lastMaintainDate || '未记录') + '; 周期 ' + row.maintainCycleDays + ' 天'">
                  <el-tag size="small" :type="daysFromToday(row.nextMaintainDate) < 15 ? 'warning' : 'success'">
                    {{ row.nextMaintainDate }}
                  </el-tag>
                </el-tooltip>
                <span v-else style="color:var(--el-text-color-secondary)">未设定</span>
              </template>
            </el-table-column>
            <el-table-column label="下次校准" width="112" align="center">
              <template #default="{ row }">
                <template v-if="row.calibrationCycleDays && row.calibrationCycleDays > 0">
                  <el-tooltip v-if="row.nextCalDate" :content="'上次校准: ' + (row.lastCalDate || '未记录') + '; 周期 ' + row.calibrationCycleDays + ' 天'">
                    <el-tag size="small" :type="daysFromToday(row.nextCalDate) < 20 ? 'warning' : 'success'">
                      {{ row.nextCalDate }}
                    </el-tag>
                  </el-tooltip>
                  <span v-else style="color:var(--el-text-color-secondary)">未设定</span>
                </template>
                <span v-else style="color:var(--el-text-color-disabled)">—</span>
              </template>
            </el-table-column>
            <el-table-column label="投产日期" prop="installDate" width="108" />
            <el-table-column label="操作员" prop="operatorNames" width="130" show-overflow-tooltip />
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" v-hasPermi="'pm:equipment:query'" @click="openLogs(row)">日志</el-button>
                <el-button link type="primary" v-hasPermi="'pm:equipment:edit'"  @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger"  v-hasPermi="'pm:equipment:remove'" @click="handleRemove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
            :total="equipList.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadList" @current-change="loadList" />
        </el-tab-pane>

        <!-- ===== Tab 2: 事件日志 (全局视图) ===== -->
        <el-tab-pane label="事件日志" name="log">
          <el-form :inline="true" :model="logQ" @submit.prevent="loadLogs" size="default">
            <el-form-item label="设备编码">
              <el-select v-model="logQ.equipmentId" filterable placeholder="全部" clearable style="width:220px" @change="loadLogs">
                <el-option v-for="e in equipmentOptions" :key="e.equipmentId"
                  :label="`${e.equipmentNo} ${e.equipmentName}`" :value="e.equipmentId" />
              </el-select>
            </el-form-item>
            <el-form-item label="事件类型">
              <el-select v-model="logQ.eventType" placeholder="全部" clearable style="width:120px" @change="loadLogs">
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
            <el-form-item>
              <el-button type="primary" plain size="small" :icon="Refresh" @click="loadLogs">刷新</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="logList.data.rows || []" v-loading="logList.loading" stripe border row-key="logId" size="default">
            <el-table-column label="时间" prop="eventTime" width="168" />
            <el-table-column label="设备" width="160">
              <template #default="{ row }">
                <el-tag size="small" style="margin-right:6px">{{ row.equipmentNo }}</el-tag>
                <span>{{ (equipmentMap.get(row.equipmentId)?.equipmentName) || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">
                <component :is="eventTag(row.eventType).tag" :type="eventTag(row.eventType).type" size="small" effect="plain">
                  {{ eventTag(row.eventType).label }}
                </component>
              </template>
            </el-table-column>
            <el-table-column label="标题" prop="eventTitle" min-width="240" show-overflow-tooltip />
            <el-table-column label="结果" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.eventResult==='PASS'"    type="success" size="small">成功</el-tag>
                <el-tag v-else-if="row.eventResult==='FAIL'" type="danger"  size="small">失败</el-tag>
                <el-tag v-else-if="row.eventResult==='PENDING'" type="warning" size="small">处理中</el-tag>
                <span v-else style="color:var(--el-text-color-secondary)">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态变更" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.oldStatus != null && row.newStatus != null">
                  {{ statusTag(row.oldStatus).label }} → <b>{{ statusTag(row.newStatus).label }}</b>
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="花费(¥)" prop="costAmount" width="100" align="right">
              <template #default="{ row }">{{ formatNum(row.costAmount) }}</template>
            </el-table-column>
            <el-table-column label="执行人" prop="operatorName" width="96" />
            <el-table-column label="详细说明" prop="remark" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" v-hasPermi="'pm:equipment:remove'" @click="handleLogRemove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" v-model:current-page="logQ.pageNum" v-model:page-size="logQ.pageSize"
            :total="logList.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadLogs" @current-change="loadLogs" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ========= 设备新增/编辑 Dialog ========= -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增设备' : '修改设备'" width="880px" destroy-on-close top="6vh">
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-tabs type="border-card">
          <el-tab-pane label="基础信息" name="base">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="设备编码" prop="equipmentNo"><el-input v-model="editDlg.form.equipmentNo" :disabled="!editDlg.isNew" maxlength="50" placeholder="如 SMT-01" /></el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="设备名称" prop="equipmentName"><el-input v-model="editDlg.form.equipmentName" maxlength="200" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="设备类型" prop="equipmentType">
                  <el-select v-model="editDlg.form.equipmentType" placeholder="请选择设备类型" style="width:100%">
                    <el-option v-for="t in EQUIPMENT_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="品牌"><el-input v-model="editDlg.form.brand" maxlength="100" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="型号"><el-input v-model="editDlg.form.model" maxlength="100" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="出厂序列号"><el-input v-model="editDlg.form.serialNo" maxlength="100" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="固定资产编号"><el-input v-model="editDlg.form.assetNo" maxlength="50" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="所属车间"><el-input v-model="editDlg.form.workshop" placeholder="如 SMT车间" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="工作中心"><el-input v-model="editDlg.form.workCenter" placeholder="如 SMT-01" /></el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="具体位置"><el-input v-model="editDlg.form.location" placeholder="如 1号厂房A区12号工位" maxlength="200" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="供应商">
                  <el-select v-model="editDlg.form.supplierId" filterable placeholder="选择供应商" style="width:100%" @change="onSupplierChange">
                    <el-option v-for="s in supplierOptions" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
                      <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
                      <span style="float:right;color:#999;font-size:12px">{{ s.contact }} {{ s.phone }}</span>
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="供应商名称"><el-input v-model="editDlg.form.supplierName" maxlength="200" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="设备等级" prop="equipLevel">
                  <el-radio-group v-model="editDlg.form.equipLevel">
                    <el-radio-button :value="1">关键设备</el-radio-button>
                    <el-radio-button :value="2">一般设备</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="当前状态" prop="status">
                  <el-select v-model="editDlg.form.status" style="width:100%">
                    <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
          <el-tab-pane label="采购/投产日期" name="dates">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="购入日期">
                  <el-date-picker v-model="editDlg.form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="保修到期">
                  <el-date-picker v-model="editDlg.form.warrantyEndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="投产日期">
                  <el-date-picker v-model="editDlg.form.installDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
          <el-tab-pane label="产能/保养/校准" name="maint">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="每小时产能" prop="capacityPerHour">
                  <el-input-number v-model="editDlg.form.capacityPerHour" :min="0" :precision="2" style="width:100%" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="产能单位">
                  <el-select v-model="editDlg.form.capacityUnit" style="width:100%" allow-create default-first-option filterable>
                    <el-option label="CPH (贴片机)" value="CPH" />
                    <el-option label="UPH (测试机)" value="UPH" />
                    <el-option label="BOARDS/小时" value="BOARDS" />
                    <el-option label="PCS/小时" value="PCS" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8"></el-col>
              <el-col :span="8">
                <el-form-item label="保养周期(天)" prop="maintainCycleDays">
                  <el-input-number v-model="editDlg.form.maintainCycleDays" :min="0" :max="720" style="width:100%" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="上次保养">
                  <el-date-picker v-model="editDlg.form.lastMaintainDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="下次保养">
                  <el-date-picker v-model="editDlg.form.nextMaintainDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                  <div style="color:var(--el-text-color-secondary);font-size:12px;line-height:1.2">不填则根据上次保养日+周期自动计算</div>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="校准周期(天)" prop="calibrationCycleDays">
                  <el-input-number v-model="editDlg.form.calibrationCycleDays" :min="0" :max="720" style="width:100%" controls-position="right" />
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
            </el-row>
          </el-tab-pane>
          <el-tab-pane label="操作员/备注" name="ops">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="操作员ID">
                  <el-input v-model="editDlg.form.operatorIds" placeholder="如 3,4 (逗号分隔)" maxlength="200" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="操作员姓名">
                  <el-input v-model="editDlg.form.operatorNames" placeholder="如 王磊,赵晓 (逗号分隔)" maxlength="500" />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="备注"><el-input v-model="editDlg.form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
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

    <!-- ========= 单设备 事件日志 Drawer + 新增日志 ========= -->
    <el-drawer v-model="logDrawer.show" :title="`设备 ${logDrawer.equipment?.equipmentNo || ''} 事件日志`" size="720px" destroy-on-close>
      <div style="padding:8px 4px 12px">
        <el-descriptions :column="3" border size="small" v-if="logDrawer.equipment">
          <el-descriptions-item label="设备名称">{{ logDrawer.equipment.equipmentName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ equipmentTypeLabel(logDrawer.equipment.equipmentType) }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <component :is="statusTag(logDrawer.equipment.status).tag" :type="statusTag(logDrawer.equipment.status).type" size="small">
              {{ statusTag(logDrawer.equipment.status).label }}
            </component>
          </el-descriptions-item>
          <el-descriptions-item label="车间/工位" :span="2">{{ logDrawer.equipment.workshop }} {{ logDrawer.equipment.location }}</el-descriptions-item>
          <el-descriptions-item label="操作员">{{ logDrawer.equipment.operatorNames || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下次保养">{{ logDrawer.equipment.nextMaintainDate || '未设定' }}</el-descriptions-item>
          <el-descriptions-item label="下次校准">{{ logDrawer.equipment.nextCalDate || '无校准需求' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ logDrawer.equipment.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <div style="margin-bottom:8px">
        <el-button type="primary" plain :icon="Plus" v-hasPermi="'pm:equipment:log'" @click="openLogAdd">新增事件</el-button>
      </div>
      <el-table :data="logDrawer.rows || []" v-loading="logDrawer.loading" size="small" stripe border>
        <el-table-column label="时间" prop="eventTime" width="160" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <component :is="eventTag(row.eventType).tag" :type="eventTag(row.eventType).type" size="small" effect="plain">
              {{ eventTag(row.eventType).label }}
            </component>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="72" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.eventResult==='PASS'" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="row.eventResult==='FAIL'" type="danger" size="small">失败</el-tag>
            <el-tag v-else type="info" size="small">{{ row.eventResult || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" prop="eventTitle" min-width="200" show-overflow-tooltip />
        <el-table-column label="花费¥" prop="costAmount" width="84" align="right">
          <template #default="{ row }">{{ formatNum(row.costAmount) }}</template>
        </el-table-column>
        <el-table-column label="执行人" prop="operatorName" width="84" />
        <el-table-column label="说明" prop="remark" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-drawer>

    <!-- ========= 新增事件日志 Dialog ========= -->
    <el-dialog v-model="logDlg.show" title="新增事件日志" width="720px" destroy-on-close>
      <el-form :model="logDlg.form" :rules="logRules" ref="logFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="事件类型" prop="eventType">
              <el-select v-model="logDlg.form.eventType" placeholder="请选择" style="width:100%" @change="onEventTypeChange">
                <el-option v-for="t in EVENT_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
              <div style="color:var(--el-text-color-secondary);font-size:12px;line-height:1.2">
                状态变更: 自动同步主表状态; 保养/校准: 自动重算下次日期; 维修 PASS: 自动把状态改回在用
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="事件结果">
              <el-select v-model="logDlg.form.eventResult" placeholder="选择结果" style="width:100%">
                <el-option label="成功" value="PASS" />
                <el-option label="失败" value="FAIL" />
                <el-option label="处理中" value="PENDING" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="事件标题" prop="eventTitle">
              <el-input v-model="logDlg.form.eventTitle" maxlength="200" placeholder="如 Q3季度保养: 吸嘴更换+丝杆润滑" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发生时间" prop="eventTime">
              <el-date-picker v-model="logDlg.form.eventTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="花费金额(¥)">
              <el-input-number v-model="logDlg.form.costAmount" :min="0" :precision="2" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="logDlg.form.eventType === 'STATUS_CHANGE'">
            <el-form-item label="原状态" prop="oldStatus">
              <el-select v-model="logDlg.form.oldStatus" placeholder="变更前状态" style="width:100%">
                <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="logDlg.form.eventType === 'STATUS_CHANGE'">
            <el-form-item label="新状态" prop="newStatus">
              <el-select v-model="logDlg.form.newStatus" placeholder="变更后状态 (后端会同步到设备主表)" style="width:100%">
                <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行人">
              <el-select v-model="logDlg.form.operatorId" filterable placeholder="选择执行人" style="width:100%" @change="onOperatorChange">
                <el-option v-for="u in userOptions" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行人姓名">
              <el-input v-model="logDlg.form.operatorName" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细说明">
              <el-input v-model="logDlg.form.remark" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="配件、操作过程、故障描述、结果等" />
            </el-form-item>
          </el-col>
        </el-row>
        <template #footer>
          <el-button @click="logDlg.show = false">取消</el-button>
          <el-button type="primary" @click="submitLogAdd">保存</el-button>
        </template>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { pmEquipmentApi, scmSupplierApi, userApi } from '@/api/index'
import type { PmmEquipment, PmmEquipmentLog } from '@/types/models'

// ========== 字典 ==========
const STATUS_OPTIONS: { label: string; value: number }[] = [
  { label: '闲置', value: 0 },
  { label: '在用', value: 1 },
  { label: '保养中', value: 2 },
  { label: '维修中', value: 3 },
  { label: '报废', value: 4 },
]
const EQUIPMENT_TYPE_OPTIONS: { label: string; value: string }[] = [
  { label: '印刷机',   value: 'PRINTER' },
  { label: 'SPI锡膏检测', value: 'SPI' },
  { label: '贴片机',   value: 'PLACEMENT' },
  { label: '回流焊',   value: 'REFLOW' },
  { label: 'AOI外观检测', value: 'AOI' },
  { label: '波峰焊',   value: 'WAVE' },
  { label: 'ICT在线测试', value: 'ICT' },
  { label: 'FCT功能测试', value: 'FCT' },
  { label: 'X-RAY',    value: 'XRAY' },
  { label: '其他',     value: 'OTHER' },
]
const EVENT_TYPE_OPTIONS: { label: string; value: string }[] = [
  { label: '状态变更', value: 'STATUS_CHANGE' },
  { label: '保养',     value: 'MAINTAIN' },
  { label: '维修',     value: 'REPAIR' },
  { label: '校准',     value: 'CALIBRATION' },
  { label: '事故',     value: 'ACCIDENT' },
  { label: '其他',     value: 'OTHER' },
]
const equipmentTypeLabel = (v?: string, trunc = 99) => {
  const t = EQUIPMENT_TYPE_OPTIONS.find(e => e.value === v)
  const s = t?.label || (v || '-')
  return trunc < 99 && s.length > trunc ? s.slice(0, trunc) + '…' : s
}
const statusTag = (v: any) => {
  const map: Record<string, { label: string; type: any; tag: any }> = {
    '0': { label: '闲置', type: 'info',    tag: 'ElTag' },
    '1': { label: '在用', type: 'success', tag: 'ElTag' },
    '2': { label: '保养中', type: 'warning', tag: 'ElTag' },
    '3': { label: '维修中', type: 'danger', tag: 'ElTag' },
    '4': { label: '报废', type: 'info', tag: 'ElTag' },
  }
  return map[String(v)] || { label: '-', type: 'info', tag: 'ElTag' }
}
const eventTag = (v?: string) => {
  const map: Record<string, { label: string; type: any; tag: any }> = {
    'STATUS_CHANGE': { label: '状态变更', type: 'primary', tag: 'ElTag' },
    'MAINTAIN':      { label: '保养', type: 'success', tag: 'ElTag' },
    'REPAIR':        { label: '维修', type: 'danger',  tag: 'ElTag' },
    'CALIBRATION':   { label: '校准', type: 'warning', tag: 'ElTag' },
    'ACCIDENT':      { label: '事故', type: 'danger',  tag: 'ElTag' },
    'OTHER':         { label: '其他', type: 'info',    tag: 'ElTag' },
  }
  return map[v || ''] || { label: v || '-', type: 'info', tag: 'ElTag' }
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
const activeTab = ref<'equip' | 'log'>('equip')
const q = reactive({ pageNum: 1, pageSize: 20, equipmentNo: '', equipmentName: '', equipmentType: '' as string | undefined,
  status: undefined as number | undefined, equipLevel: undefined as number | undefined, workshop: '', workCenter: '' })
const equipList = reactive({ loading: false, data: { rows: [] as PmmEquipment[], total: 0 } })
const equipmentOptions = ref<PmmEquipment[]>([])
const equipmentMap = computed(() => {
  const m = new Map<number, PmmEquipment>()
  equipmentOptions.value.forEach(e => { if (e.equipmentId) m.set(e.equipmentId, e) })
  return m
})
const supplierOptions = ref<any[]>([])
const userOptions = ref<any[]>([])

const loadList = async () => {
  equipList.loading = true
  try {
    const r = await pmEquipmentApi.list({ ...q })
    equipList.data = { rows: r?.rows || [], total: r?.total || 0 }
  } finally { equipList.loading = false }
}
const loadOptions = async () => {
  try { equipmentOptions.value = (await pmEquipmentApi.options()) || [] } catch {}
}
const loadSupplierUserOptions = async () => {
  try { const r = await scmSupplierApi.options() as any; supplierOptions.value = r?.data || r || [] } catch { supplierOptions.value = [] }
  try { const r = await userApi.options() as any; userOptions.value = r?.data || r || [] } catch { userOptions.value = [] }
}
const onSupplierChange = (id: any) => {
  const s = supplierOptions.value.find(x => x.supplierId === id)
  if (s) editDlg.form.supplierName = s.supplierName
}
const onOperatorChange = (id: any) => {
  const u = userOptions.value.find(x => x.userId === id)
  if (u) logDlg.form.operatorName = u.nickName || u.userName
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20; q.equipmentNo = ''; q.equipmentName = ''; q.equipmentType = undefined
  q.status = undefined; q.equipLevel = undefined; q.workshop = ''; q.workCenter = ''
  loadList()
}

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; form: Partial<PmmEquipment> }>({
  show: false, isNew: true, form: {}
})
const editFormRef = ref<FormInstance>()
const editRules: FormRules = {
  equipmentNo:     [{ required: true, message: '设备编码不能为空', trigger: 'blur' }],
  equipmentName:   [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
  equipmentType:   [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  status:          [{ required: true, message: '请选择当前状态', trigger: 'change' }],
  equipLevel:      [{ required: true, message: '请选择设备等级', trigger: 'change' }],
  capacityPerHour: [{ required: true, message: '请填写每小时标准产能', trigger: 'blur' }],
  maintainCycleDays: [{ required: true, message: '请填写保养周期(天), 无填0', trigger: 'blur' }],
  calibrationCycleDays: [{ required: true, message: '请填写校准周期(天), 无校准需求填0', trigger: 'blur' }],
}
const openAdd = () => {
  editDlg.isNew = true
  editDlg.form = { status: 1, equipLevel: 2, capacityPerHour: 0, capacityUnit: 'CPH', maintainCycleDays: 90, calibrationCycleDays: 0 }
  editDlg.show = true
}
const openEdit = (row: PmmEquipment) => {
  editDlg.isNew = false
  editDlg.form = JSON.parse(JSON.stringify(row))
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await editFormRef.value?.validate().catch(() => false)
  if (!ok) return
  try {
    if (editDlg.isNew) {
      await pmEquipmentApi.add(editDlg.form as any)
      ElMessage.success('新增设备成功')
    } else {
      await pmEquipmentApi.edit(editDlg.form as any)
      ElMessage.success('修改设备成功')
    }
    editDlg.show = false
    await Promise.all([loadList(), loadOptions()])
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败') }
}
const handleRemove = async (row: PmmEquipment) => {
  try {
    await ElMessageBox.confirm(`确认删除设备【${row.equipmentNo} ${row.equipmentName}】？`, '删除确认', { type: 'warning' })
    await pmEquipmentApi.remove(row.equipmentId!)
    ElMessage.success('删除成功')
    await Promise.all([loadList(), loadOptions()])
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== 全局事件日志 Tab ==========
const logQ = reactive({ pageNum: 1, pageSize: 20, equipmentId: undefined as number | undefined,
  equipmentNo: '' as string | undefined, eventType: '' as string | undefined, eventResult: '' as string | undefined })
const logList = reactive({ loading: false, data: { rows: [] as PmmEquipmentLog[], total: 0 } })
const loadLogs = async () => {
  logList.loading = true
  try {
    const r = await pmEquipmentApi.logList({ ...logQ })
    logList.data = { rows: r?.rows || [], total: r?.total || 0 }
  } finally { logList.loading = false }
}
const handleLogRemove = async (row: PmmEquipmentLog) => {
  try {
    await ElMessageBox.confirm(`确认删除日志【${row.eventTitle}】？`, '删除确认', { type: 'warning' })
    await pmEquipmentApi.logRemove(row.logId!)
    ElMessage.success('已删除')
    loadLogs()
    if (logDrawer.show && logDrawer.equipment?.equipmentId === row.equipmentId) loadDrawerLogs()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

// ========== 单设备 事件日志 Drawer ==========
const logDrawer = reactive<{ show: boolean; loading: boolean; equipment: PmmEquipment | null; rows: PmmEquipmentLog[] }>({
  show: false, loading: false, equipment: null, rows: []
})
const openLogs = async (row: PmmEquipment) => {
  logDrawer.equipment = row
  logDrawer.show = true
  await loadDrawerLogs()
}
const loadDrawerLogs = async () => {
  if (!logDrawer.equipment?.equipmentId) return
  logDrawer.loading = true
  try {
    const r = await pmEquipmentApi.logList({ pageNum: 1, pageSize: 200, equipmentId: logDrawer.equipment.equipmentId })
    logDrawer.rows = r?.rows || []
  } finally { logDrawer.loading = false }
}

// ========== 新增日志 ==========
const logDlg = reactive<{ show: boolean; form: Partial<PmmEquipmentLog> }>({ show: false, form: {} })
const logFormRef = ref<FormInstance>()
const logRules: FormRules = {
  eventType:  [{ required: true, message: '请选择事件类型', trigger: 'change' }],
  eventTitle: [{ required: true, message: '请填写事件标题', trigger: 'blur' }],
  eventTime:  [{ required: true, message: '请选择发生时间', trigger: 'change' }],
}
const openLogAdd = () => {
  if (!logDrawer.equipment) return
  logDlg.form = {
    equipmentId: logDrawer.equipment.equipmentId,
    equipmentNo: logDrawer.equipment.equipmentNo,
    eventType: 'MAINTAIN',
    eventResult: 'PASS',
    eventTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
    costAmount: 0,
    oldStatus: logDrawer.equipment.status,
    newStatus: logDrawer.equipment.status,
  }
  logDlg.show = true
}
const onEventTypeChange = () => {
  if (logDlg.form.eventType !== 'STATUS_CHANGE') {
    logDlg.form.oldStatus = undefined
    logDlg.form.newStatus = undefined
  }
}
const submitLogAdd = async () => {
  const ok = await logFormRef.value?.validate().catch(() => false)
  if (!ok) return
  try {
    await pmEquipmentApi.logAdd(logDlg.form as any)
    ElMessage.success('已保存事件日志, 相关状态/日期同步已刷新')
    logDlg.show = false
    await Promise.all([loadList(), loadOptions(), loadDrawerLogs(), loadLogs()])
    // 同步 drawer 里的 equipment 快照 (用于状态展示)
    if (logDrawer.equipment?.equipmentId) {
      try { logDrawer.equipment = await pmEquipmentApi.get(logDrawer.equipment.equipmentId) } catch {}
    }
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败') }
}

onMounted(() => { loadList(); loadOptions(); loadLogs(); loadSupplierUserOptions() })
</script>

<style scoped>
.pm-page { display: flex; flex-direction: column; gap: 14px; }
.search-card :deep(.el-form-item) { margin-bottom: 0; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.pager { margin-top: 14px; justify-content: flex-end; display: flex; }
</style>
