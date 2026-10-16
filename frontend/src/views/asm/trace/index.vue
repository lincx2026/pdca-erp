<template>
  <div class="asm-page trace-page">
    <!-- 顶部搜索区 -->
    <el-card class="search-card trace-search" shadow="never">
      <div class="search-inner">
        <el-input
          v-model="snCodeInput"
          placeholder="请输入要追溯的 SN 码 / 料盘号 / 批次号"
          size="large"
          clearable
          @keyup.enter="doQuery"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" :icon="Search" :loading="loadingAny" @click="doQuery">追溯查询</el-button>
        <el-button size="large" :icon="Refresh" @click="resetAll">重置</el-button>
      </div>
    </el-card>

    <!-- 基础信息卡片 -->
    <el-card v-if="snInfo.snCode" class="sn-info-card" shadow="hover">
      <template #header>
        <div class="info-header">
          <div class="info-title">
            <el-tag :type="snTypeTag(snInfo.snType)" size="large" effect="dark">{{ snTypeLabel(snInfo.snType) }}</el-tag>
            <span class="sn-code">{{ snInfo.snCode }}</span>
          </div>
          <div class="info-sub">
            <el-tag :type="currentStatusTag(snInfo.currentStatus)" size="small">{{ currentStatusLabel(snInfo.currentStatus) }}</el-tag>
            <span v-if="snInfo.currentProcess" class="chip">
              <el-icon><Operation /></el-icon>
              {{ snInfo.currentProcess }}
            </span>
          </div>
        </div>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="层级">{{ snInfo.snLevel === 1 ? '原材料' : snInfo.snLevel === 2 ? '半成品' : snInfo.snLevel === 3 ? '成品' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="料号">{{ snInfo.materialCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ snInfo.materialName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ snInfo.specification || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ snInfo.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ snInfo.qty || 0 }} {{ snInfo.unit || '' }}</el-descriptions-item>
        <el-descriptions-item label="工单">{{ snInfo.workOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ snInfo.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="首次出现">{{ snInfo.firstSeenTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后更新">{{ snInfo.lastSeenTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生产日期">{{ snInfo.productionDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ snInfo.expiryDate || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 追溯结果区 -->
    <el-card v-if="snInfo.snCode" class="trace-card" shadow="never">
      <el-tabs v-model="activeTab" class="trace-tabs">
        <!-- 追溯节点链（默认） -->
        <el-tab-pane label="追溯节点链" name="nodes">
          <template #label>
            <span class="tab-label">
              <el-icon><Connection /></el-icon>
              追溯节点链
              <el-badge v-if="nodes.length" :value="nodes.length" class="tab-badge" />
            </span>
          </template>

          <div v-loading="loading.nodes" class="nodes-wrapper">
            <el-empty v-if="!loading.nodes && nodes.length === 0" description="暂无追溯节点" />
            <el-timeline v-else class="trace-timeline">
              <el-timeline-item
                v-for="(node, idx) in nodes"
                :key="node.nodeId || idx"
                :timestamp="node.nodeTime || ''"
                placement="top"
                :color="nodeResultColor(node.nodeResult)"
                size="large"
              >
                <div class="node-card" :class="`node-${(node.nodeResult || '').toLowerCase()}`">
                  <div class="node-header">
                    <el-tag :type="nodeTypeTag(node.nodeType)" effect="dark" size="small" class="node-type">
                      {{ node.nodeType }}
                    </el-tag>
                    <span class="node-process">{{ node.processName || node.processCode || '-' }}</span>
                    <el-tag :type="nodeResultTag(node.nodeResult)" size="small" class="node-result">
                      {{ nodeResultIcon(node.nodeResult) }} {{ node.nodeResult || '-' }}
                    </el-tag>
                  </div>
                  <el-descriptions :column="3" border size="small" class="node-desc">
                    <el-descriptions-item label="设备">{{ node.equipmentCode || node.equipmentName || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="程序">{{ node.programName || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="Feeder">{{ node.feederNo || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="操作员">{{ node.operatorName || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="时间">{{ node.nodeTime || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="缺陷" v-if="node.defectCode">{{ node.defectCode }} - {{ node.defectDesc || '' }}</el-descriptions-item>
                    <el-descriptions-item label="备注" :span="node.remark ? 3 : 0" v-if="node.remark">{{ node.remark }}</el-descriptions-item>
                  </el-descriptions>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-tab-pane>

        <!-- 正向追溯（成品→原材料） -->
        <el-tab-pane label="正向追溯（成品→原材料）" name="upstream">
          <template #label>
            <span class="tab-label">
              <el-icon><Top /></el-icon>
              正向追溯
              <el-badge v-if="upstream.length" :value="upstream.length" class="tab-badge" />
            </span>
          </template>

          <div v-loading="loading.upstream">
            <el-empty v-if="!loading.upstream && upstream.length === 0" description="未找到上游构成" />
            <el-table v-else :data="upstream" stripe border size="default" row-key="nodeId || snId || code">
              <el-table-column label="序号" type="index" width="70" align="center" />
              <el-table-column label="层级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.level != null" size="small" :type="row.level <= 1 ? 'info' : row.level === 2 ? 'warning' : 'primary'" effect="plain">
                    L{{ row.level }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="SN码/批次" prop="snCode" width="180" show-overflow-tooltip />
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.snType" :type="snTypeTag(row.snType)" size="small">{{ row.snType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="料号" prop="materialCode" width="150" show-overflow-tooltip />
              <el-table-column label="名称" prop="materialName" min-width="160" show-overflow-tooltip />
              <el-table-column label="数量" prop="qty" width="90" align="right" />
              <el-table-column label="单位" prop="unit" width="70" align="center" />
              <el-table-column label="批次号" prop="batchNo" width="130" show-overflow-tooltip />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.snCode" link type="primary" @click="deepTrace(row.snCode)">继续追溯</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 反向追溯（原材料→成品） -->
        <el-tab-pane label="反向追溯（原材料→成品）" name="downstream">
          <template #label>
            <span class="tab-label">
              <el-icon><Bottom /></el-icon>
              反向追溯
              <el-badge v-if="downstream.length" :value="downstream.length" class="tab-badge" />
            </span>
          </template>

          <div v-loading="loading.downstream">
            <el-empty v-if="!loading.downstream && downstream.length === 0" description="未找到下游使用" />
            <el-table v-else :data="downstream" stripe border size="default" row-key="nodeId || snId || code">
              <el-table-column label="序号" type="index" width="70" align="center" />
              <el-table-column label="成品SN码" prop="snCode" width="180" show-overflow-tooltip />
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.snType" :type="snTypeTag(row.snType)" size="small">{{ row.snType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="料号" prop="materialCode" width="150" show-overflow-tooltip />
              <el-table-column label="名称" prop="materialName" min-width="160" show-overflow-tooltip />
              <el-table-column label="工单" prop="workOrderNo" width="130" show-overflow-tooltip />
              <el-table-column label="当前工序" prop="currentProcess" width="120" show-overflow-tooltip />
              <el-table-column label="当前状态" width="110" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.currentStatus != null" :type="currentStatusTag(row.currentStatus)" size="small">{{ currentStatusLabel(row.currentStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" prop="createTime" width="170" />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.snCode" link type="primary" @click="deepTrace(row.snCode)">追溯此件</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 空状态（未查询时） -->
    <el-card v-else class="empty-card" shadow="never">
      <el-empty description="请在上方输入 SN 码 / 料盘号 / 批次号 开始追溯">
        <p class="empty-tip">支持：原材料批次 → 半成品 → 成品 的全链路追溯</p>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Connection, Operation, Top, Bottom } from '@element-plus/icons-vue'
import { asmSnApi, asmTraceApi } from '@/api'
import type { AsmSn, AsmSnNode } from '@/types/models'

const route = useRoute()
const router = useRouter()

// ========== 枚举工具 ==========
const snTypes = [
  { value: 'RAW', label: '原材料' },
  { value: 'WIP', label: '半成品' },
  { value: 'FG', label: '成品' },
  { value: 'REEL', label: '料盘' }
]
function snTypeLabel(v?: string) { return snTypes.find(s => s.value === v)?.label || v || '-' }
function snTypeTag(v?: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    RAW: 'info', WIP: 'warning', FG: 'success', REEL: 'primary' as any
  }
  return v ? (map[v] || '') : ''
}

const currentStatusOptions = [
  { value: 0, label: '待生产' }, { value: 1, label: '生产中' }, { value: 2, label: '在制' },
  { value: 3, label: '已完工' }, { value: 4, label: '不良' }, { value: 5, label: '报废' }, { value: 6, label: '已出货' }
]
function currentStatusLabel(v?: number) { return currentStatusOptions.find(s => s.value === v)?.label || (v ?? '-') }
function currentStatusTag(v?: number): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    0: 'info', 1: 'primary' as any, 2: 'warning', 3: 'success', 4: 'danger', 5: 'danger', 6: 'info'
  }
  return v !== undefined ? (map[v] || '') : ''
}

function nodeTypeTag(v?: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    INBOUND: 'info', KITTING: '', LOAD: 'warning',
    SMT: 'primary' as any, REFLOW: 'primary' as any, AOI: 'success', ICT: 'success',
    FUNCTION: 'success', REWORK: 'danger', PACKAGE: '', OUTBOUND: 'info'
  }
  return v ? (map[v] || '') : ''
}

function nodeResultTag(v?: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    OK: 'success', PASS: 'success', REWORK: 'warning', NG: 'danger', FAIL: 'danger'
  }
  return v ? (map[v] || '') : ''
}
function nodeResultColor(v?: string): string {
  const map: Record<string, string> = { OK: '#67c23a', PASS: '#67c23a', REWORK: '#e6a23c', NG: '#f56c6c', FAIL: '#f56c6c' }
  return v ? (map[v] || '#909399') : '#909399'
}
function nodeResultIcon(v?: string): string {
  const map: Record<string, string> = { OK: '✓', PASS: '✓', REWORK: '↻', NG: '✗', FAIL: '✗' }
  return v ? (map[v] || '') : ''
}

// ========== 状态 ==========
const snCodeInput = ref('')
const snInfo = reactive<Partial<AsmSn>>({})
const activeTab = ref('nodes')

const nodes = ref<AsmSnNode[]>([])
const upstream = ref<any[]>([])
const downstream = ref<any[]>([])

const loading = reactive({
  nodes: false,
  upstream: false,
  downstream: false
})
const loadingAny = computed(() => loading.nodes || loading.upstream || loading.downstream)

// ========== 查询 ==========
async function doQuery() {
  const code = snCodeInput.value.trim()
  if (!code) { ElMessage.warning('请输入要追溯的 SN 码'); return }

  // 同步到 URL query
  router.replace({ path: '/asm/trace', query: { snCode: code } })

  // 先尝试获取 SN 主表信息
  try {
    const snList = await asmSnApi.list({ snCode: code, pageNum: 1, pageSize: 1 } as any)
    if (snList?.rows?.length) {
      Object.assign(snInfo, snList.rows[0])
    } else {
      // 清理旧数据
      Object.keys(snInfo).forEach(k => delete (snInfo as any)[k])
      snInfo.snCode = code
    }
  } catch {
    Object.keys(snInfo).forEach(k => delete (snInfo as any)[k])
    snInfo.snCode = code
  }

  // 并行加载三个维度
  activeTab.value = 'nodes'
  loadNodes(code)
  loadUpstream(code)
  loadDownstream(code)
}

async function loadNodes(code: string) {
  loading.nodes = true
  try {
    const res = await asmTraceApi.nodes(code)
    nodes.value = (res || []).sort((a: AsmSnNode, b: AsmSnNode) => (a.seqNo || 0) - (b.seqNo || 0))
  } catch (e: any) {
    nodes.value = []
    ElMessage.error(e.message || '加载追溯节点失败')
  } finally { loading.nodes = false }
}

async function loadUpstream(code: string) {
  loading.upstream = true
  try {
    upstream.value = await asmTraceApi.upstream(code) || []
  } catch (e: any) {
    upstream.value = []
  } finally { loading.upstream = false }
}

async function loadDownstream(code: string) {
  loading.downstream = true
  try {
    downstream.value = await asmTraceApi.downstream(code) || []
  } catch (e: any) {
    downstream.value = []
  } finally { loading.downstream = false }
}

function resetAll() {
  snCodeInput.value = ''
  Object.keys(snInfo).forEach(k => delete (snInfo as any)[k])
  nodes.value = []
  upstream.value = []
  downstream.value = []
  router.replace({ path: '/asm/trace' })
}

function deepTrace(code: string) {
  snCodeInput.value = code
  doQuery()
}

// ========== 生命周期 ==========
onMounted(() => {
  // 从 URL query.snCode 自动查询
  if (route.query.snCode) {
    snCodeInput.value = route.query.snCode as string
    doQuery()
  }
})

watch(() => route.query.snCode, (val) => {
  if (val && val !== snCodeInput.value) {
    snCodeInput.value = val as string
    doQuery()
  }
})
</script>

<style scoped>
.trace-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.trace-search .search-inner { display: flex; gap: 12px; align-items: center; }
.trace-search .el-input { flex: 1; max-width: 500px; }

.sn-info-card { margin-bottom: 12px; }
.info-header { display: flex; justify-content: space-between; align-items: center; }
.info-title { display: flex; align-items: center; gap: 12px; }
.sn-code { font-size: 20px; font-weight: 700; color: #303133; letter-spacing: 0.5px; }
.info-sub { display: flex; gap: 8px; align-items: center; }
.chip { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 12px; background: #ecf5ff; color: #409eff; font-size: 13px; }

.trace-tabs .tab-label { display: inline-flex; align-items: center; gap: 6px; }
.tab-badge { margin-left: 4px; }

.nodes-wrapper { min-height: 200px; padding: 8px 0; }
.trace-timeline { padding: 0 20px; }

.node-card {
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 16px;
  transition: all 0.2s;
}
.node-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.08); border-color: #409eff; }

.node-card.node-ng, .node-card.node-fail { border-left: 4px solid #f56c6c; }
.node-card.node-ok, .node-card.node-pass { border-left: 4px solid #67c23a; }
.node-card.node-rework { border-left: 4px solid #e6a23c; }

.node-header { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.node-type { flex-shrink: 0; }
.node-process { font-weight: 600; font-size: 14px; color: #303133; flex: 1; }
.node-result { flex-shrink: 0; }
.node-desc { font-size: 13px; }

.empty-card { min-height: 300px; display: flex; align-items: center; justify-content: center; }
.empty-tip { color: #909399; font-size: 13px; margin-top: 8px; }
</style>
