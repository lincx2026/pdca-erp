<template>
  <div class="kanban-scm">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="6" v-for="c in cards" :key="c.key">
        <el-card shadow="hover" class="stat-card" :class="'card-' + c.key">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-num">{{ c.value }}</div>
          <div class="stat-sub">{{ c.sub }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header><div class="card-header">最新采购申请</div></template>
          <el-table v-loading="loadingReq" :data="rowsReq" size="small">
            <el-table-column prop="requisitionNo" label="申请单号" width="160" />
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="applicantName" label="申请人" width="100" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><el-tag :type="reqMap[row.status||0]?.t || 'info'">{{ reqMap[row.status||0]?.l || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header><div class="card-header">最新采购订单</div></template>
          <el-table v-loading="loadingPo" :data="rowsPo" size="small">
            <el-table-column prop="poNo" label="订单号" width="160" />
            <el-table-column prop="supplierName" label="供应商" show-overflow-tooltip />
            <el-table-column label="金额" width="140">
              <template #default="{ row }">{{ row.totalAmount == null ? '-' : '¥' + Number(row.totalAmount).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><el-tag :type="poMap[row.status||0]?.t || 'info'">{{ poMap[row.status||0]?.l || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header><div class="card-header">今日到货 / 在途</div></template>
          <el-table v-loading="loadingDel" :data="rowsDel" size="small">
            <el-table-column prop="deliveryNo" label="到货单号" width="160" />
            <el-table-column prop="poNo" label="订单号" width="140" />
            <el-table-column prop="supplierName" label="供应商" show-overflow-tooltip />
            <el-table-column label="状态" width="120">
              <template #default="{ row }"><el-tag :type="delMap[row.status||0]?.t || 'info'">{{ delMap[row.status||0]?.l || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header><div class="card-header">Top 5 供应商(按最新报价/状态)</div></template>
          <el-table v-loading="loadingSup" :data="rowsSup" size="small">
            <el-table-column prop="supplierCode" label="编码" width="140" />
            <el-table-column prop="supplierName" label="名称" show-overflow-tooltip />
            <el-table-column prop="level" label="级别" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { scmReqApi, scmPoApi, scmDeliveryApi, scmSupplierApi } from '@/api'

defineOptions({ name: 'ScmKanbanPage' })

const loadingReq = ref(false)
const loadingPo  = ref(false)
const loadingDel = ref(false)
const loadingSup = ref(false)
const rowsReq = ref<any[]>([])
const rowsPo  = ref<any[]>([])
const rowsDel = ref<any[]>([])
const rowsSup = ref<any[]>([])
const totalReq = ref(0)
const totalPo  = ref(0)
const totalDel = ref(0)
const totalSup = ref(0)

const reqMap: Record<number, {l:string;t?:string}> = {
  0: { l: '草稿', t: 'info' }, 1: { l: '已提交', t: 'warning' },
  2: { l: '已审核', t: 'primary' }, 3: { l: '已转采购单', t: 'success' },
  4: { l: '已驳回', t: 'danger' }, 9: { l: '取消', t: 'info' }
}
const poMap: Record<number, {l:string;t?:string}> = {
  0: { l: '草稿', t: 'info' }, 1: { l: '已提交', t: 'warning' },
  2: { l: '已审核', t: 'primary' }, 3: { l: '已下单', t: '' },
  4: { l: '部分到货', t: 'warning' }, 5: { l: '全部到货', t: '' },
  6: { l: '已完成', t: 'success' }, 7: { l: '已驳回', t: 'danger' },
  9: { l: '取消', t: 'info' }
}
const delMap: Record<number, {l:string;t?:string}> = {
  0: { l: '待收货', t: 'info' }, 1: { l: '收货中', t: 'warning' },
  2: { l: '已收货', t: 'primary' }, 3: { l: 'IQC检验中', t: 'warning' },
  4: { l: '入库完成', t: 'success' }, 5: { l: '部分退货', t: '' }, 9: { l: '取消', t: 'info' }
}

async function load() {
  loadingReq.value = loadingPo.value = loadingDel.value = loadingSup.value = true
  try {
    const [req, po, del, sup] = await Promise.all([
      scmReqApi.list({ pageNum: 1, pageSize: 5 }) as any,
      scmPoApi.list({ pageNum: 1, pageSize: 5 }) as any,
      scmDeliveryApi.list({ pageNum: 1, pageSize: 5 }) as any,
      scmSupplierApi.list({ pageNum: 1, pageSize: 5 }) as any
    ])
    rowsReq.value = (req && req.rows) || []
    totalReq.value = (req && req.total) || 0
    rowsPo.value  = (po  && po.rows)  || []
    totalPo.value  = (po  && po.total)  || 0
    rowsDel.value = (del && del.rows) || []
    totalDel.value = (del && del.total) || 0
    rowsSup.value = (sup && sup.rows) || []
    totalSup.value = (sup && sup.total) || 0
  } finally {
    loadingReq.value = loadingPo.value = loadingDel.value = loadingSup.value = false
  }
}

const cards = computed(() => [
  { key: 'req', label: '采购申请', value: totalReq.value, sub: '在册总数' },
  { key: 'po',  label: '采购订单', value: totalPo.value,  sub: '在册总数' },
  { key: 'del', label: '到货/在途', value: totalDel.value, sub: '在册总数' },
  { key: 'sup', label: '供应商数', value: totalSup.value, sub: '在册活跃供应商' }
])

onMounted(load)
</script>

<style scoped>
.kanban-scm { padding: 16px; }
.stat-card { border-radius: 12px; }
.card-req { background: linear-gradient(135deg, #eef2ff, #fff); }
.card-po  { background: linear-gradient(135deg, #eff6ff, #fff); }
.card-del { background: linear-gradient(135deg, #ecfeff, #fff); }
.card-sup { background: linear-gradient(135deg, #fef3c7, #fff); }
.stat-label { color: #6b7280; font-size: 13px; }
.stat-num { font-size: 28px; font-weight: 700; margin: 8px 0 4px; color: #111827; }
.stat-sub { color: #9ca3af; font-size: 12px; }
.card-header { font-weight: 600; }
</style>
