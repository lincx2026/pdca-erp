<template>
  <div class="login-wrapper">
    <div class="login-box">
      <div class="login-left">
        <div class="brand">
          <img src="/logo.png" class="logo" alt="logo" />
          <div>
            <h1>智健ERP系统</h1>
            <p class="sub">{{ companyName }} · 面向PCBA行业的自研管理系统</p>
          </div>
        </div>
      </div>

      <div class="login-right">
        <h2 class="form-title">欢迎登录</h2>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" type="text" autocomplete="username" placeholder="请输入账号" :prefix-icon="User" maxlength="30" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password maxlength="20" />
          </el-form-item>
          <!-- T20260831: 外网测试期间隐藏图形验证码（AI无法识别），后端不传uuid+captcha字段时跳过验证码校验 -->
          <el-form-item prop="captcha" class="captcha-row" style="display:none" v-if="false">
            <el-input v-model="form.captcha" placeholder="验证码" :prefix-icon="Key" maxlength="4" style="flex:1" />
            <div class="captcha-img" @click="refreshCaptcha" title="点击刷新">
              <img v-if="captchaImg" :src="captchaImg" alt="captcha" class="captcha-img-real" />
              <span v-else>{{ captchaText }}</span>
            </div>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="form.remember">记住账号</el-checkbox>
            <span class="forget" @click="onForget">忘记密码?</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="login-btn" size="large" :loading="loading" @click="onSubmit">
              登 录
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
    <p class="copy">© {{ year }} {{ companyName }} · 智健ERP系统 v1.0.0</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const companyName = computed(() => import.meta.env.VITE_COMPANY_NAME)
const year = new Date().getFullYear()

const formRef = ref<FormInstance>()
const loading = ref(false)

interface LoginFormState {
  username: string
  password: string
  captcha: string
  remember: boolean
}
const form = reactive<LoginFormState>({
  username: localStorage.getItem('pcba_remember_user') || 'admin',
  password: '',
  captcha: '',
  remember: !!localStorage.getItem('pcba_remember_user')
})

const captchaText = ref('')
const captchaImg = ref('')
const captchaUuid = ref('')
const captchaLoading = ref(false)

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    // 调后端 /auth/captcha 获取 uuid + code + img (Base64)
    const data = await authApi.captcha() as any
    captchaUuid.value = (data && data.uuid) ? data.uuid : ''
    captchaText.value = (data && data.code) ? data.code : ''
    captchaImg.value = (data && data.img) ? (data.img.startsWith('data:') ? data.img : `data:image/png;base64,${data.img}`) : ''
  } catch (e: any) {
    // 接口失败时回退：前端本地生成，保证页面可用
    captchaUuid.value = 'local-' + Date.now()
    const chars = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'
    let s = ''
    for (let i = 0; i < 4; i++) s += chars[Math.floor(Math.random() * chars.length)]
    captchaText.value = s
    captchaImg.value = ''
    ElMessage.warning('验证码接口异常, 已切换为本地模式')
  } finally {
    captchaLoading.value = false
  }
}

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }, { min: 2, max: 30, message: '2~30位', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 5, max: 20, message: '5~20位', trigger: 'blur' }],
  // T20260831: 外网测试期间关闭验证码前端校验（后端也会跳过，因为请求体不带captcha+uuid）
  captcha: [{ required: false, message: '请输入验证码', trigger: 'blur' }, {
    validator: (_r, _v, cb) => cb(),
    trigger: 'blur'
  }]
}

async function onSubmit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  loading.value = true
  try {
    // T20260831: 外网测试期间不在请求体传captcha+uuid → 后端跳过验证码校验（仅username+password登录）
    await userStore.login({
      username: form.username.trim(),
      password: form.password
    } as any)
    if (form.remember) localStorage.setItem('pcba_remember_user', form.username.trim())
    else localStorage.removeItem('pcba_remember_user')

    await userStore.fetchUserInfo()
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(decodeURIComponent(redirect))
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
    await refreshCaptcha()
    form.captcha = ''
  } finally {
    loading.value = false
  }
}

function onForget() {
  ElMessage.warning('请联系系统管理员重置密码')
}

// T20260831: 外网测试期间不调验证码接口（隐藏了验证码，没必要浪费请求）
onMounted(() => {
  // refreshCaptcha()
})
</script>

<style lang="scss" scoped>
.login-wrapper {
  height: 100vh; width: 100vw;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background:
    radial-gradient(1200px 600px at 15% 20%, rgba(59,130,246,.18), transparent 60%),
    radial-gradient(900px 500px at 85% 80%, rgba(16,185,129,.16), transparent 60%),
    linear-gradient(135deg, #0f172a 0%, #1e3a8a 50%, #064e3b 100%);
  color: #fff;
}
.login-box {
  width: 960px; max-width: 95vw; height: 540px; max-height: 95vh;
  background: #fff; color: #111827; border-radius: 16px;
  box-shadow: 0 30px 80px rgba(0,0,0,.3);
  display: flex; overflow: hidden;
}
.login-left {
  flex: 1; padding: 32px;
  background: linear-gradient(135deg, #1e3a8a, #0f172a 90%);
  color: #fff; display: flex; flex-direction: column; justify-content: space-between;
  h1 { font-size: 28px; margin-bottom: 4px; letter-spacing: 1px; }
  .sub { opacity: .85; margin: 0; font-size: 13px; }
  .brand { display: flex; align-items: center; gap: 14px; }
  .logo { width: 48px; height: 48px; }
  .tip {
    :deep(.el-alert__content) { line-height: 1.8; color: #111827; }
    background: #fff; color: #111827; border: none;
  }
}
.login-right {
  width: 420px; padding: 40px 36px;
  display: flex; flex-direction: column;
  .form-title { font-size: 24px; margin-bottom: 24px; font-weight: 600; }
  .login-btn { width: 100%; letter-spacing: 8px; }
  .captcha-row { display: flex; gap: 12px; align-items: center; }
  .captcha-img {
    width: 110px; height: 40px; flex-shrink: 0;
    display: flex; align-items: center; justify-content: center;
    font-size: 20px; font-weight: 700; letter-spacing: 4px;
    background: linear-gradient(135deg, #eff6ff, #ecfeff);
    color: #1e3a8a; border: 1px solid #dbeafe; border-radius: 6px;
    user-select: none; cursor: pointer; font-style: italic;
    overflow: hidden;
    img {
      width: 100%; height: 100%; object-fit: cover;
    }
    &.is-loading {
      opacity: .6; pointer-events: none;
    }
  }
  .forget { margin-left: auto; color: #3b82f6; cursor: pointer; font-size: 13px; }
  .footer-links { margin-top: 4px; display: flex; justify-content: space-between; }
}
.copy { margin-top: 16px; opacity: .7; font-size: 13px; }
@media (max-width: 768px) {
  .login-box { width: 95vw; height: auto; flex-direction: column; }
  .login-left { padding: 20px; }
  .login-right { width: 100%; padding: 24px; }
}
</style>
