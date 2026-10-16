<template>
  <div class="inventory-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="仓库">
          <el-select v-model="q.warehouseId" placeholder="全部仓库" clearable filterable style="width:220px">
            <el-option v-for="w in warehouseOpts" :key="w.warehouseId"
                       :label="`${w.warehouseCode} - ${w.warehouseName}`"
                       :value="w.warehouseId" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="q.materialCode" placeholder="物料编码" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="q.materialName" placeholder="物料名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:110px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />电子料库存</div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="inventoryId" size="default">
        <el-table-column label="仓库" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.warehouseCode || '' }}</span>
            <span>{{ row.warehouseName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="160" fixed />
        <el-table-column label="物料名称" prop="materialName" min-width="180" show-overflow-tooltip />
        <el-table-column label="规格" prop="specification" min-width="160" show-overflow-tooltip />
        <el-table-column label="批次号" prop="batchNo" width="140" />
        <el-table-column label="库存数量" prop="quantity" width="110" align="right" />
        <el-table-column label="锁定数量" prop="lockedQty" width="110" align="right" />
        <el-table-column label="可用数量" prop="availableQty" width="110" align="right" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" width="180" />
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="q.pageNum"
        v-model:page-size="q.pageSize"
        :total="list.data.total || 0"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { inventoryApi, warehouseApi } from '@/api'
import type { WmsInventory, WmsWarehouse } from '@/types/models'

// ============ 列表 ============
const q = reactive({
  pageNum: 1, pageSize: 20,
  warehouseId: undefined as number | undefined,
  materialCode: '', materialName: '',
  status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as WmsInventory[], total: 0 } })

async function loadList() {
  list.loading = true
  try {
    const res = await inventoryApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally {
    list.loading = false
  }
}
function resetQ() {
  q.warehouseId = undefined; q.materialCode = ''; q.materialName = ''; q.status = undefined
  q.pageNum = 1
  loadList()
}

// ============ 仓库下拉 ============
const warehouseOpts = ref<WmsWarehouse[]>([])
async function loadWarehouseOpts() {
  try { warehouseOpts.value = await warehouseApi.options() || [] }
  catch { warehouseOpts.value = [] }
}

// ============ 初始化 ============
onMounted(() => {
  loadWarehouseOpts()
  loadList()
})
</script>

<style scoped>
.inventory-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
.mono { font-family: 'Courier New', monospace; font-size: 13px; margin-right: 6px; color: #303133; }
.muted { color: #909399; font-size: 13px; }
</style>
