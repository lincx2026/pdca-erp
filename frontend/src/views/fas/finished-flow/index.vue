<template>
  <div class="fas-page">
    <!-- 汇总卡片 -->
    <el-row :gutter="14">
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="流水笔数" :value="stat.count">
          <template #prefix><el-icon style="color:var(--el-color-primary)"><Document /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="入库数量" :value="stat.inQty">
          <template #prefix><el-icon style="color:var(--el-color-success)"><Download /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="出库数量" :value="stat.outQty">
          <template #prefix><el-icon style="color:var(--el-color-danger)"><Upload /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card">
        <el-statistic title="净变动" :value="stat.netQty">
          <template #prefix><el-icon :style="{ color: stat.netQty >= 0 ? 'var(--el-color-success)' : 'var(--el-color-danger)' }"><TrendCharts /></el-icon></template>
        </el-statistic>
      </el-card></el-col>
    </el-row>

    <!-- 搜索 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="类型">
          <el-select v-model="q.docType" placeholder="全部" clearable style="width:130px" @change="loadList">
            <el-option value="INBOUND" label="入库" />
            <el-option value="OUTBOUND" label="出库" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="q.sourceType" placeholder="全部" clearable style="width:140px" @change="loadList">
            <el-option value="ASSEMBLY_IN" label="组装完工入库" />
            <el-option value="SALES_OUT" label="销售出库" />
            <el-option value="SALES_RETURN" label="销售退货入库" />
            <el-option value="SAMPLE" label="样品出库" />
            <el-option value="TRANSFER" label="调拨出入库" />
            <el-option value="OTHER" label="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="成品名称"><el-input v-model="q.productName" clearable style="width:180px" /></el-form-item>
        <el-form-item label="仓库"><el-input v-model="q.warehouse" clearable style="width:120px" /></el-form-item>
        <el-form-item label="审核时间">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
            value-format="YYYY-MM-DD" @change="onDateChange" style="width:260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
          <el-button :icon="Download" @click="exportCSV" style="margin-left:8px">导出 CSV</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <template #header><div class="list-header"><div class="title"><span class="dot" />成品出入库流水</div></div></template>
      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="stockId" size="default">
        <el-table-column label="单号" prop="docNo" width="170" fixed="left" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.docType==='INBOUND'" type="success" effect="light">入库</el-tag>
            <el-tag v-else type="danger" effect="light">出库</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务类型" width="140">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="成品名称" prop="productName" min-width="200" show-overflow-tooltip />
        <el-table-column label="批次号" prop="batchNo" width="140" />
        <el-table-column label="仓库" prop="warehouse" width="110" />
        <el-table-column label="变动数量" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.flowQty || 0) >= 0 ? 'var(--el-color-success)' : 'var(--el-color-danger)', fontWeight: 600 }">
              {{ ((row.flowQty || 0) > 0 ? '+' : '') + (row.flowQty || 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="审核人" prop="auditBy" width="100" />
        <el-table-column label="审核时间" prop="auditTime" width="160" />
        <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Download, Upload, Document, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { pmFasApi } from '@/api/index'
import type { FasProductStock } from '@/types/models'

const q = reactive({
  pageNum: 1, pageSize: 20,
  docType: '' as string,
  sourceType: '' as string,
  productName: '', warehouse: '',
  beginTime: '' as string, endTime: '' as string
})
const dateRange = ref<[string, string] | null>(null)
const onDateChange = (v: [string, string] | null) => {
  q.beginTime = v?.[0] || ''
  q.endTime = v?.[1] || ''
}

const list = reactive({ loading: false, data: { rows: [] as FasProductStock[], total: 0 } })
const stat = reactive({ count: 0, inQty: 0, outQty: 0, netQty: 0 })

const sourceTypeLabel = (s: string | null) => {
  const map: Record<string, string> = {
    ASSEMBLY_IN: '组装完工入库', SALES_RETURN: '销售退货入库',
    SALES_OUT: '销售出库', SAMPLE: '样品出库', TRANSFER: '调拨出入库', OTHER: '其他'
  }
  return map[s || ''] || (s || '-')
}

const loadList = async () => {
  list.loading = true
  try {
    const r = await pmFasApi.flowList({ ...q })
    const rows = r?.rows || []
    list.data = { rows, total: r?.total || 0 }
    let cnt = 0, inQ = 0, outQ = 0
    rows.forEach((r: any) => {
      cnt++; const f = r.flowQty || 0
      if (f >= 0) inQ += f; else outQ += Math.abs(f)
    })
    stat.count = cnt; stat.inQty = inQ; stat.outQty = outQ; stat.netQty = inQ - outQ
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败') }
  finally { list.loading = false }
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20; q.docType = ''; q.sourceType = ''; q.productName = ''; q.warehouse = ''
  q.beginTime = ''; q.endTime = ''; dateRange.value = null
  loadList()
}
const exportCSV = () => {
  const rows = list.data.rows
  if (!rows || rows.length === 0) return ElMessage.warning('暂无数据可导出')
  const header = ['单号', '类型', '业务类型', '成品名称', '批次号', '仓库', '变动数量', '审核人', '审核时间', '备注']
  const lines = [header.join(',')].concat(
    rows.map(r => [
      (r as any).docNo || '',
      (r as any).docType === 'INBOUND' ? '入库' : '出库',
      sourceTypeLabel((r as any).sourceType),
      `"${((r as any).productName || '').replace(/"/g, '""')}"`,
      (r as any).batchNo || '',
      (r as any).warehouse || '',
      String((r as any).flowQty || 0),
      (r as any).auditBy || '',
      (r as any).auditTime || '',
      `"${((r as any).remark || '').replace(/"/g, '""')}"`
    ].join(','))
  )
  const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `成品出入库流水_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(url)
}

onMounted(loadList)
</script>
