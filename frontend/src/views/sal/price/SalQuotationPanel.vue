<template>
  <section class="quotations">
    <div class="section-title">销售报价单</div>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="quotationNo" label="报价单号" width="150" />
      <el-table-column prop="title" label="报价名称/型号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="customerName" label="客户" min-width="180" show-overflow-tooltip />
      <el-table-column prop="currency" label="币种" width="70" />
      <el-table-column label="有效期" width="210"><template #default="{row}">{{ row.validFrom }} ~ {{ row.validTo }}</template></el-table-column>
      <el-table-column prop="lineCount" label="明细数" width="80" />
      <el-table-column label="状态" width="90"><template #default="{row}">{{ statusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="操作" width="310" fixed="right"><template #default="{row}">
        <el-button link type="primary" @click="openExisting(row, true)">详情</el-button>
        <el-button v-if="[0,4].includes(row.status)" v-hasPermi="'sal:price:edit'" link type="primary" @click="openExisting(row, false)">编辑</el-button>
        <el-button v-if="[0,4].includes(row.status)" v-hasPermi="'sal:price:submit'" link type="primary" @click="action(row,'submit')">提交</el-button>
        <el-button v-if="row.status===1" v-hasPermi="'sal:price:audit'" link type="success" @click="action(row,'audit')">审核</el-button>
        <el-button v-if="row.status===1" v-hasPermi="'sal:price:reject'" link type="warning" @click="action(row,'reject')">驳回</el-button>
        <el-button v-if="row.status===2" v-hasPermi="'sal:price:edit'" link @click="action(row,'disable')">停用</el-button>
        <el-button v-if="row.status===3" v-hasPermi="'sal:price:edit'" link @click="action(row,'enable')">启用</el-button>
        <el-button v-if="[0,4].includes(row.status)" v-hasPermi="'sal:price:remove'" link type="danger" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load" />
    <el-dialog v-model="visible" :title="readOnly ? '报价单详情' : form.quotationId ? '编辑报价单' : '新增报价单'" width="1000px" destroy-on-close>
      <el-tabs v-model="tab">
        <el-tab-pane label="基本信息" name="header">
          <el-form :model="form" label-width="110px" :disabled="readOnly">
            <el-form-item label="报价名称/型号" required><el-input v-model="form.title" maxlength="200" placeholder="手工填写报价名称或型号" /></el-form-item>
            <el-form-item label="客户" required><el-select v-model="form.customerId" filterable placeholder="选择客户" style="width:100%"><el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" /></el-select></el-form-item>
            <el-row :gutter="12"><el-col :span="8"><el-form-item label="币种" required><el-input v-model="form.currency" maxlength="10" /></el-form-item></el-col><el-col :span="8"><el-form-item label="生效日期" required><el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="失效日期" required><el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row>
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane :label="`报价明细（${form.items.length}）`" name="items">
          <el-button v-if="!readOnly" type="primary" @click="openPicker">批量选择物料</el-button>
          <el-table :data="form.items" border max-height="360" style="margin-top:12px">
            <el-table-column prop="materialCode" label="编码" min-width="120" /><el-table-column prop="materialName" label="物料名称" min-width="150" /><el-table-column prop="specification" label="规格" min-width="160" show-overflow-tooltip />
            <el-table-column label="含税单价" width="155"><template #default="{row}"><el-input-number v-model="row.unitPrice" :min="0" :precision="4" :disabled="readOnly" controls-position="right" style="width:135px" /></template></el-table-column>
            <el-table-column label="税率%" width="125"><template #default="{row}"><el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" :disabled="readOnly" controls-position="right" style="width:105px" /></template></el-table-column>
            <el-table-column label="起订量" width="125"><template #default="{row}"><el-input-number v-model="row.minQty" :min="0" :precision="2" :disabled="readOnly" controls-position="right" style="width:105px" /></template></el-table-column>
            <el-table-column v-if="!readOnly" label="操作" width="70"><template #default="{$index}"><el-button link type="danger" @click="form.items.splice($index,1)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <template #footer><el-button @click="visible=false">关闭</el-button><el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存报价单</el-button></template>
    </el-dialog>
    <el-dialog v-model="pickerVisible" title="批量选择物料" width="850px" append-to-body destroy-on-close>
      <el-input v-model="keyword" placeholder="输入编码、名称、规格或关键词" clearable @keyup.enter="search"><template #append><el-button @click="search">查询</el-button></template></el-input>
      <el-table :data="options" row-key="materialId" border max-height="360" @selection-change="selected=$event" style="margin-top:12px">
        <el-table-column type="selection" width="50" /><el-table-column prop="materialCode" label="编码" width="150" /><el-table-column prop="materialName" label="名称" min-width="150" /><el-table-column prop="specification" label="规格" min-width="160" show-overflow-tooltip /><el-table-column prop="searchKeywords" label="关键词" min-width="140" show-overflow-tooltip />
      </el-table>
      <el-pagination v-model:current-page="pickerPage" :page-size="50" :total="pickerTotal" layout="total, prev, pager, next" @current-change="search" />
      <template #footer><el-button @click="pickerVisible=false">取消</el-button><el-button type="primary" :disabled="!selected.length" @click="addSelected">加入明细</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { materialApi, salQuotationApi } from '@/api'
const props = defineProps<{ customers: any[]; filters: any; onlyValid: boolean }>()
const rows = ref<any[]>([]), total = ref(0), page = ref(1), loading = ref(false)
const visible = ref(false), readOnly = ref(false), saving = ref(false), tab = ref('header')
const pickerVisible = ref(false), keyword = ref(''), pickerPage = ref(1), pickerTotal = ref(0), options = ref<any[]>([]), selected = ref<any[]>([])
const form = reactive<any>({ quotationId: null, title: '', customerId: null, currency: 'CNY', validFrom: '', validTo: '', remark: '', items: [] })
function day(offset = 0) { const d = new Date(); d.setDate(d.getDate()+offset); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const statusLabel = (s: number) => ({ 0:'草稿', 1:'待审核', 2:'已审核', 3:'停用', 4:'已驳回' } as Record<number,string>)[s] || String(s)
async function load() { loading.value = true; try { const result: any = await salQuotationApi.list({ ...props.filters, onlyValid: props.onlyValid, pageNum: page.value, pageSize: 20 }); rows.value = result?.rows || []; total.value = Number(result?.total || 0) } catch (e: any) { ElMessage.error(e?.message || '报价单查询失败') } finally { loading.value = false } }
watch(() => [props.filters.customerId, props.filters.materialId, props.filters.currency, props.filters.status, props.onlyValid], () => { page.value=1; void load() })
function openNew() { Object.assign(form, { quotationId: null, title: '', customerId: null, currency: 'CNY', validFrom: day(), validTo: day(365), remark: '', items: [] }); tab.value='header'; readOnly.value=false; visible.value=true }
async function openExisting(row: any, view: boolean) { try { const result: any = await salQuotationApi.get(row.quotationId); Object.assign(form, result.header, { items: result.items || [] }); tab.value='header'; readOnly.value=view; visible.value=true } catch (e: any) { ElMessage.error(e?.message || '加载报价单失败') } }
async function openPicker() { keyword.value=''; pickerPage.value=1; selected.value=[]; pickerVisible.value=true; await search() }
async function search() { try { const result: any = await materialApi.search(keyword.value, pickerPage.value, 50); options.value=result?.rows || []; pickerTotal.value=Number(result?.total || 0); selected.value=[] } catch (e: any) { ElMessage.error(e?.message || '物料查询失败') } }
function addSelected() { for (const m of selected.value) if (!form.items.some((i: any) => i.materialId===m.materialId)) form.items.push({ materialId:m.materialId,materialCode:m.materialCode,materialName:m.materialName,specification:m.specification,unitPrice:0,taxRate:13,minQty:0 }); pickerVisible.value=false }
async function save() { if (!form.title?.trim() || !form.customerId || !form.validFrom || !form.validTo || form.validFrom>form.validTo || !form.items.length) { ElMessage.warning('请填写报价名称、客户、有效期并添加物料明细'); return }
  if (form.items.some((i: any) => i.unitPrice==null || i.unitPrice<0 || i.taxRate==null || i.taxRate<0 || i.taxRate>100 || i.minQty<0)) { ElMessage.warning('请检查明细单价、税率和起订量'); return }
  saving.value=true; try { const body = { title:form.title.trim(),customerId:form.customerId,currency:form.currency,validFrom:form.validFrom,validTo:form.validTo,remark:form.remark,items:form.items }; if (form.quotationId) await salQuotationApi.edit(form.quotationId,body); else await salQuotationApi.add(body); visible.value=false; ElMessage.success('报价单已保存'); await load() } catch(e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value=false } }
async function action(row: any, key: string) { try { let body; if (key==='reject') { const answer=await ElMessageBox.prompt('请输入驳回原因','驳回报价单',{ inputValidator:(v: string) => !!v?.trim() || '请输入原因' }); body={reason:answer.value.trim()} } else await ElMessageBox.confirm(`确认${({submit:'提交',audit:'审核通过',disable:'停用',enable:'启用'} as Record<string,string>)[key]}报价单？`,'操作确认'); await salQuotationApi.action(row.quotationId,key,body); ElMessage.success('操作成功'); await load() } catch(e: any) { if(e!=='cancel' && e!=='close') ElMessage.error(e?.message || '操作失败') } }
async function remove(row: any) { try { await ElMessageBox.confirm('确认删除该报价单和所有明细？','操作确认',{type:'warning'}); await salQuotationApi.remove(row.quotationId); ElMessage.success('已删除'); await load() } catch(e: any) { if(e!=='cancel' && e!=='close') ElMessage.error(e?.message || '删除失败') } }
onMounted(load)
defineExpose({ openNew, load })
</script>
<style scoped>
.quotations { margin-bottom: 20px; }
.section-title { font-weight: 600; margin: 14px 0 8px; }
.quotations :deep(.el-pagination) { display: flex; justify-content: flex-end; margin-top: 10px; }
</style>
