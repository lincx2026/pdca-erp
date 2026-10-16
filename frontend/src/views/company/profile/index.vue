<template>
  <div class="company-page">
    <el-card class="list-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />公司档案</div>
          <div>
            <el-button type="primary" :icon="Check" :loading="saving" v-hasPermi="'company:profile:edit'" @click="handleSave">保存</el-button>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <!-- ========= 基本信息 ========= -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="公司全称" prop="companyName">
              <el-input v-model="form.companyName" maxlength="150" placeholder="营业执照上的全称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公司简称">
              <el-input v-model="form.shortName" maxlength="50" placeholder="如 智健" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Logo URL">
              <el-input v-model="form.logoUrl" maxlength="500" placeholder="公司 Logo 图片地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="法人代表">
              <el-input v-model="form.legalPerson" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" maxlength="20" placeholder="手机或座机" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系邮箱">
              <el-input v-model="form.contactEmail" maxlength="100" placeholder="企业邮箱" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- ========= 地址信息 ========= -->
        <el-divider content-position="left">地址信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="18">
            <el-form-item label="公司地址">
              <el-input v-model="form.address" maxlength="255" placeholder="详细地址" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="邮政编码">
              <el-input v-model="form.postalCode" maxlength="10" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- ========= 财务信息 ========= -->
        <el-divider content-position="left">财务信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="税号">
              <el-input v-model="form.taxNo" maxlength="30" placeholder="统一社会信用代码/税号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开户银行">
              <el-input v-model="form.bankName" maxlength="100" placeholder="如 中国工商银行xx支行" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="银行账号">
              <el-input v-model="form.bankAccount" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发票抬头">
              <el-input v-model="form.invoiceTitle" maxlength="150" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认税率(%)">
              <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认币种">
              <el-select v-model="form.currency" placeholder="请选择" style="width:100%">
                <el-option label="人民币 (CNY)" value="CNY" />
                <el-option label="美元 (USD)" value="USD" />
                <el-option label="欧元 (EUR)" value="EUR" />
                <el-option label="港币 (HKD)" value="HKD" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="金额小数位">
              <el-input-number v-model="form.decimalPlaces" :min="0" :max="6" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- ========= 备注 ========= -->
        <el-divider content-position="left">其他</el-divider>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <div v-if="form.updateTime" class="meta">
          最近更新: {{ form.updateBy || '-' }} @ {{ form.updateTime }}
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { companyApi } from '@/api/index'
import type { CompanyProfile } from '@/types/models'

const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Partial<CompanyProfile>>({
  companyName: '', currency: 'CNY', taxRate: 13, decimalPlaces: 2
})

const rules: FormRules = {
  companyName: [{ required: true, message: '请输入公司全称', trigger: 'blur' }]
}

const loadProfile = async () => {
  loading.value = true
  try {
    const r = await companyApi.profileGet()
    if (r) Object.assign(form, r)
  } catch (e: any) {
    // 无档案时留空, 保存时新建
  } finally { loading.value = false }
}

const handleSave = async () => {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    await companyApi.profileSave(form)
    ElMessage.success('保存成功')
    await loadProfile()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { saving.value = false }
}

onMounted(() => { loadProfile() })
</script>

<style scoped>
.company-page { display: flex; flex-direction: column; gap: 14px; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.meta { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 8px; }
</style>
