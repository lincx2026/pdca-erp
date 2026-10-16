<template>
  <div class="page">
    <el-card shadow="never" class="mb-16">
      <el-form :inline="true" :model="query">
        <el-form-item label="系统模块"><el-input v-model="query.title" clearable placeholder="模块标题模糊" /></el-form-item>
        <el-form-item label="操作人"><el-input v-model="query.operName" clearable placeholder="操作人账号模糊" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.businessType" clearable placeholder="全部" style="width:140px">
            <el-option v-for="t in BT_LIST" :key="t[0]" :label="t[1]" :value="t[0]" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:120px">
            <el-option label="成功" :value="0" /><el-option label="失败" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="dateRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始" end-placeholder="结束" range-separator="~" style="width:360px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page=1;reload()">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="danger" :icon="Delete" v-hasPermi="'monitor:operlog:remove'" @click="handleClean">清空</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column label="ID" prop="operId" width="80" align="right" />
        <el-table-column label="模块" prop="title" min-width="120" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{row}">
            <el-tag :type="btColor(row.businessType)" size="small">{{ btLabel(row.businessType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方法" prop="method" min-width="220" show-overflow-tooltip />
        <el-table-column label="请求方式" prop="requestMethod" width="90" align="center" />
        <el-table-column label="操作人" prop="operName" width="110" />
        <el-table-column label="IP" prop="operIp" width="130" />
        <el-table-column label="耗时(ms)" prop="costTime" width="90" align="right" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="row.status===0?'success':'danger'">{{ row.status===0?'成功':'失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" prop="operTime" width="170" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{row}">
            <el-button link type="primary" v-hasPermi="'monitor:operlog:query'" @click="detail=row; detailDialog=true">详情</el-button>
            <el-popconfirm title="确定删除?" @confirm="removeRow(row)">
              <template #reference>
                <el-button link type="danger" v-hasPermi="'monitor:operlog:remove'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt-16 center"
        background v-model:current-page="page" v-model:page-size="pageSize"
        :page-sizes="[10,20,50,100]" layout="total,sizes,prev,pager,next,jumper"
        :total="total" @size-change="reload()" @current-change="reload()" />
    </el-card>

    <el-dialog v-model="detailDialog" title="操作日志详情" width="720px" destroy-on-close>
      <el-descriptions :column="1" border size="small" v-if="detail">
        <el-descriptions-item label="模块">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ btLabel(detail.businessType) }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ detail.method }}</el-descriptions-item>
        <el-descriptions-item label="HTTP 方法">{{ detail.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ detail.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="URL">{{ detail.operUrl }}</el-descriptions-item>
        <el-descriptions-item label="IP / 位置">{{ (detail.operIp || '-') + (detail.operLocation ? ' / ' + detail.operLocation : '') }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre v-if="detail.operParam" class="json-pre"><code>{{ pretty(detail.operParam) }}</code></pre>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="响应结果">
          <pre v-if="detail.jsonResult" class="json-pre"><code>{{ pretty(detail.jsonResult) }}</code></pre>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="detail.status===0?'success':'danger'" size="small">{{ detail.status===0?'成功':'失败' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" v-if="detail.status===1">
          <code style="color:#ef4444">{{ detail.errorMsg || '-' }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costTime }} ms</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.operTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete } from '@element-plus/icons-vue'
import { operlogApi } from '@/api'
import type { SysOperLog } from '@/types/models'

const loading = ref(false)
const rows = ref<SysOperLog[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const dateRange = ref<string[]>([])
const query = reactive<{ title?: string; operName?: string; businessType?: number; status?: number }>({})

const BT_LIST: [number, string][] = [
  [0, '其他'], [1, '新增'], [2, '修改'], [3, '删除'], [4, '授权'],
  [5, '导出'], [6, '导入'], [7, '强退'], [8, '生成代码'], [9, '清空数据']
]
const BT_MAP = new Map(BT_LIST)
function btLabel(n?: number) { return n == null ? '-' : (BT_MAP.get(n) || '其他') }
function btColor(n?: number): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  if (n === 1) return 'success'
  if (n === 2) return 'warning'
  if (n === 3) return 'danger'
  if (n === 4) return 'primary'
  return 'info'
}
function pretty(s: string) {
  try { return JSON.stringify(JSON.parse(s), null, 2) }
  catch { return s }
}

async function reload() {
  loading.value = true
  try {
    const [beginTime, endTime] = dateRange.value.length === 2 ? dateRange.value : [undefined as any, undefined as any]
    const res = await operlogApi.list({
      pageNum: page.value, pageSize: pageSize.value,
      title: query.title, operName: query.operName, businessType: query.businessType,
      status: query.status, beginTime, endTime
    }) as any
    rows.value = res.rows || []; total.value = Number(res.total || 0)
  } finally { loading.value = false }
}
function resetQuery() { query.title = ''; query.operName = ''; query.businessType = undefined; query.status = undefined; dateRange.value = []; page.value = 1; reload() }
async function handleClean() {
  await ElMessageBox.confirm('确定清空全部操作日志? 清空后不可恢复。', '提示', { type: 'warning' })
  await operlogApi.clean()
  ElMessage.success('已清空'); reload()
}
async function removeRow(row: SysOperLog) {
  await operlogApi.remove([row.operId!])
  ElMessage.success('删除成功'); reload()
}

const detailDialog = ref(false)
const detail = ref<SysOperLog | null>(null)

onMounted(reload)
</script>
<style lang="scss" scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
.center { display: flex; justify-content: center; }
.json-pre {
  margin: 0; max-height: 300px; overflow: auto;
  background: #0f172a; color: #cbd5e1; border-radius: 6px; padding: 12px 16px;
  font-family: ui-monospace, Consolas, Menlo, "Courier New", monospace;
  font-size: 12px; line-height: 1.6;
}
</style>
