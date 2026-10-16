<template>
  <div class="pm-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工单">
          <el-select v-model="q.workOrderId" placeholder="选择工单" clearable filterable @change="loadList" style="width:240px">
            <el-option
              v-for="o in workOrderOptions"
              :key="o.workOrderId"
              :label="`${o.orderNo} - ${o.materialName || ''}`"
              :value="o.workOrderId!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="q.materialCode" placeholder="物料编码" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="q.materialName" placeholder="物料名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />工单BOM</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:workbom:add'" @click="openAdd">新增BOM行</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="bomLineId" size="default">
        <el-table-column label="工单ID" prop="workOrderId" width="90" align="center" />
        <el-table-column label="行号" prop="lineNo" width="70" align="center" />
        <el-table-column label="物料编码" prop="materialCode" width="150" show-overflow-tooltip />
        <el-table-column label="物料名称" prop="materialName" min-width="180" show-overflow-tooltip />
        <el-table-column label="规格" prop="specification" min-width="140" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="80" align="center" />
        <el-table-column label="单件用量" prop="qtyPerUnit" width="100" align="right" />
        <el-table-column label="需求数量" prop="requiredQty" width="100" align="right" />
        <el-table-column label="已发料" prop="issuedQty" width="90" align="right" />
        <el-table-column label="已退料" prop="returnedQty" width="90" align="right" />
        <el-table-column label="损耗率(%)" prop="scrapRate" width="100" align="right" />
        <el-table-column label="仓库ID" prop="warehouseId" width="90" align="center" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'pm:workbom:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="'pm:workbom:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
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

    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增BOM行' : '修改BOM行'" width="680px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单" prop="workOrderId">
              <el-select v-model="editDlg.form.workOrderId" placeholder="选择工单" filterable style="width:100%" @change="onWorkOrderChange">
                <el-option
                  v-for="o in workOrderOptions"
                  :key="o.workOrderId"
                  :label="`${o.orderNo} - ${o.materialName || ''}`"
                  :value="o.workOrderId!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行号">
              <el-input-number v-model="editDlg.form.lineNo" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料" prop="materialId">
              <el-select v-model="editDlg.form.materialId" placeholder="选择物料" filterable style="width:100%" @change="onMaterialChange">
                <el-option
                  v-for="m in materialOptions"
                  :key="m.materialId"
                  :label="`${m.materialCode} - ${m.materialName}`"
                  :value="m.materialId"
                >
                  <div style="display:flex;justify-content:space-between;width:100%">
                    <span>{{ m.materialCode }} - {{ m.materialName }}</span>
                    <span style="color:#999;font-size:12px">{{ m.specification }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="editDlg.form.unit" placeholder="PCS" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单件用量" prop="qtyPerUnit">
              <el-input-number v-model="editDlg.form.qtyPerUnit" :min="0" :precision="4" :step="0.01" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需求数量">
              <el-input-number v-model="editDlg.form.requiredQty" :min="0" :precision="4" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="已发料">
              <el-input-number v-model="editDlg.form.issuedQty" :min="0" :precision="4" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="已退料">
              <el-input-number v-model="editDlg.form.returnedQty" :min="0" :precision="4" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="损耗率(%)">
              <el-input-number v-model="editDlg.form.scrapRate" :min="0" :max="100" :precision="2" :step="0.1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库">
              <el-select v-model="editDlg.form.warehouseId" placeholder="选择仓库" filterable clearable style="width:100%" @change="onWarehouseChange">
                <el-option
                  v-for="w in warehouseOptions"
                  :key="w.warehouseId"
                  :label="`${w.warehouseCode} - ${w.warehouseName}`"
                  :value="w.warehouseId!"
                >
                  <div style="display:flex;justify-content:space-between;width:100%">
                    <span>{{ w.warehouseCode }} - {{ w.warehouseName }}</span>
                    <span style="color:#999;font-size:12px">{{ w.warehouseType }}{{ w.address ? ' · ' + w.address : '' }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editDlg.form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { workBomApi, workOrderApi, materialApi, warehouseApi } from '@/api'
import type { PmmWorkOrderBom, PmmWorkOrder, PdmMaterial, WmsWarehouse } from '@/types/models'

// ========== 下拉选项 ==========
const workOrderOptions = ref<PmmWorkOrder[]>([])
const materialOptions = ref<PdmMaterial[]>([])
const warehouseOptions = ref<WmsWarehouse[]>([])

async function loadOptions() {
  try {
    const [woRes, matRes, whRes] = await Promise.all([
      workOrderApi.options(),
      materialApi.list({ pageNum: 1, pageSize: 1000 }),
      warehouseApi.options()
    ])
    workOrderOptions.value = (woRes as any)?.data || (woRes as any) || []
    materialOptions.value = ((matRes as any)?.rows || (matRes as any)?.data?.rows || []) as PdmMaterial[]
    warehouseOptions.value = (whRes as any)?.data || (whRes as any) || []
  } catch (e: any) {
    // 忽略选项加载失败，不阻塞主列表
  }
}

const q = reactive({
  pageNum: 1, pageSize: 20,
  workOrderId: undefined as number | undefined,
  materialCode: '', materialName: ''
})
const list = reactive({ loading: false, data: { rows: [] as PmmWorkOrderBom[], total: 0 } })

async function loadList() {
  list.loading = true
  try {
    const res = await workBomApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) { ElMessage.error(e.message || '查询失败') }
  finally { list.loading = false }
}
function resetQ() {
  Object.assign(q, { workOrderId: undefined, materialCode: '', materialName: '', pageNum: 1 })
  loadList()
}

const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<PmmWorkOrderBom> })
const editRules = {
  workOrderId: [{ required: true, message: '工单ID不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  qtyPerUnit: [{ required: true, message: '单件用量不能为空', trigger: 'blur' }]
}

// 工单选择变更：保持已填字段不动，仅作记录
function onWorkOrderChange() { /* 自动联动可选 */ }

// 物料选择变更：自动回填 unit / 物料信息
function onMaterialChange(materialId?: number) {
  if (!materialId) return
  const m = materialOptions.value.find(x => x.materialId === materialId)
  if (m) {
    if (m.unit && !editDlg.form.unit) editDlg.form.unit = m.unit
    if (m.materialCode) editDlg.form.materialCode = m.materialCode
    if (m.materialName) editDlg.form.materialName = m.materialName
    if (m.specification) editDlg.form.specification = m.specification
  }
}

// 仓库选择变更：暂无额外字段回填
function onWarehouseChange(_warehouseId?: number) { /* 预留 */ }

function openAdd() {
  editDlg.isNew = true
  editDlg.form = { lineNo: 1, qtyPerUnit: 1, unit: 'PCS', scrapRate: 0 }
  editDlg.show = true
}
async function openEdit(row: PmmWorkOrderBom) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await workBomApi.get(row.bomLineId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await workBomApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await workBomApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}
async function handleRemove(row: PmmWorkOrderBom) {
  await ElMessageBox.confirm('确认删除该BOM行？', '提示', { type: 'warning' })
  await workBomApi.remove(row.bomLineId!); ElMessage.success('删除成功'); loadList()
}

onMounted(() => { loadList(); loadOptions() })
</script>

<style scoped>
.pm-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
