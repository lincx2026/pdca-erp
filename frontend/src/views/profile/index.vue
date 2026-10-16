<template>
  <div class="profile-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon><User /></el-icon>
          <span>个人中心</span>
        </div>
      </template>

      <el-row :gutter="20">
        <!-- 左侧: 用户摘要 -->
        <el-col :span="6">
          <div class="user-summary">
            <el-avatar :size="80" :icon="UserFilled" />
            <h3 class="nick">{{ form.nickName || '未设置' }}</h3>
            <p class="name">{{ userStore.userInfo?.userName || '-' }}</p>
            <el-tag v-for="r in userStore.userInfo?.roles" :key="r" size="small" type="info" class="role-tag">{{ roleLabel(r) }}</el-tag>
            <el-divider />
            <ul class="meta-list">
              <li><span class="meta-label">部门</span><span class="meta-value">{{ userStore.userInfo?.deptName || '-' }}</span></li>
              <li><span class="meta-label">邮箱</span><span class="meta-value">{{ form.email || '-' }}</span></li>
              <li><span class="meta-label">手机</span><span class="meta-value">{{ form.phonenumber || '-' }}</span></li>
              <li><span class="meta-label">登录IP</span><span class="meta-value">{{ loginIp || '-' }}</span></li>
              <li><span class="meta-label">登录时间</span><span class="meta-value">{{ loginDate || '-' }}</span></li>
              <li><span class="meta-label">注册时间</span><span class="meta-value">{{ createTime || '-' }}</span></li>
            </ul>
          </div>
        </el-col>

        <!-- 右侧: Tab -->
        <el-col :span="18">
          <el-tabs v-model="activeTab">
            <!-- Tab 1: 基本资料 -->
            <el-tab-pane label="基本资料" name="profile">
              <el-form ref="profileFormRef" :model="form" :rules="profileRules" label-width="100px" style="max-width: 520px">
                <el-form-item label="用户名">
                  <el-input :model-value="userStore.userInfo?.userName" disabled />
                </el-form-item>
                <el-form-item label="部门">
                  <el-input :model-value="userStore.userInfo?.deptName" disabled />
                </el-form-item>
                <el-form-item label="昵称" prop="nickName">
                  <el-input v-model="form.nickName" placeholder="请输入昵称" maxlength="30" show-word-limit />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="50" />
                </el-form-item>
                <el-form-item label="手机号" prop="phonenumber">
                  <el-input v-model="form.phonenumber" placeholder="请输入手机号" maxlength="11" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="saving" @click="onSaveProfile">保存修改</el-button>
                  <el-button @click="onResetProfile">重置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- Tab 2: 修改密码 -->
            <el-tab-pane label="修改密码" name="pwd">
              <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px" style="max-width: 520px">
                <el-form-item label="旧密码" prop="oldPassword">
                  <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                  <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少6位" />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="pwdSaving" @click="onSavePwd">提交修改</el-button>
                  <el-button @click="onResetPwd">重置</el-button>
                </el-form-item>
              </el-form>
              <el-alert title="修改密码后需重新登录" type="info" :closable="false" show-icon style="max-width:520px;margin-top:8px" />
            </el-tab-pane>

            <!-- Tab 3: 登录信息 -->
            <el-tab-pane label="登录信息" name="login">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="用户名">{{ userStore.userInfo?.userName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="昵称">{{ form.nickName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="部门">{{ userStore.userInfo?.deptName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="角色">{{ (userStore.userInfo?.roles || []).map(roleLabel).join(', ') || '-' }}</el-descriptions-item>
                <el-descriptions-item label="登录IP">{{ loginIp || '-' }}</el-descriptions-item>
                <el-descriptions-item label="登录时间">{{ loginDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="注册时间">{{ createTime || '-' }}</el-descriptions-item>
                <el-descriptions-item label="数据范围">{{ dataScopeLabel }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'

const userStore = useUserStore()
const router = useRouter()

const activeTab = ref('profile')
const saving = ref(false)
const pwdSaving = ref(false)

// 基本资料表单
const profileFormRef = ref<FormInstance>()
const form = reactive({
  nickName: userStore.userInfo?.nickName || '',
  email: userStore.userInfo?.email || '',
  phonenumber: userStore.userInfo?.phonenumber || ''
})

// 登录信息(只读, 由 get-info 返回)
const loginIp = ref('')
const loginDate = ref('')
const createTime = ref('')

// 密码表单
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 角色标签映射
function roleLabel(key: string): string {
  const map: Record<string, string> = { admin: '管理员', common: '普通角色', custom: '自定义' }
  return map[key] || key
}

const dataScopeLabel = (() => {
  const s = (userStore as any).userInfo as any
  const scope = s?.dataScope
  const map: Record<number, string> = { 1: '全部数据', 2: '自定义数据', 3: '本部门数据', 4: '本部门及以下', 5: '仅本人' }
  return map[scope] || '-'
})()

// 表单校验规则
const profileRules: FormRules = {
  nickName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }],
  phonenumber: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }]
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: (_r, val, cb) => val === pwdForm.newPassword ? cb() : cb(new Error('两次输入的密码不一致')), trigger: 'blur' }
  ]
}

// 保存基本资料
async function onSaveProfile() {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await authApi.updateProfile({
        nickName: form.nickName,
        email: form.email,
        phonenumber: form.phonenumber
      })
      ElMessage.success('基本资料修改成功')
      // 更新本地 store
      if (userStore.userInfo) {
        userStore.userInfo.nickName = form.nickName
        userStore.userInfo.email = form.email
        userStore.userInfo.phonenumber = form.phonenumber
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '修改失败')
    } finally {
      saving.value = false
    }
  })
}

function onResetProfile() {
  form.nickName = userStore.userInfo?.nickName || ''
  form.email = userStore.userInfo?.email || ''
  form.phonenumber = userStore.userInfo?.phonenumber || ''
  profileFormRef.value?.clearValidate()
}

// 修改密码
async function onSavePwd() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    pwdSaving.value = true
    try {
      await authApi.updatePwd({
        oldPassword: pwdForm.oldPassword,
        newPassword: pwdForm.newPassword
      })
      ElMessage.success('密码修改成功, 请重新登录')
      // 退出登录
      setTimeout(() => {
        userStore.logout()
        router.replace('/login')
      }, 1500)
    } catch (e: any) {
      ElMessage.error(e?.message || '修改密码失败')
    } finally {
      pwdSaving.value = false
    }
  })
}

function onResetPwd() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate()
}

// 初始化: 拉取最新用户信息(含 login_ip/login_date)
onMounted(async () => {
  try {
    const info = await userStore.fetchUserInfo() as any
    // get-info 返回的 user 对象可能有 loginIp/loginDate/createTime
    const u = (info as any)?.user || (userStore as any).userInfo
    // userInfo 的属性可能没有 login_ip, 需要额外请求获取; 这里尝试从 raw response 取
    // 由于 fetchUserInfo 已封装, 暂时从 store 取
    loginIp.value = u?.loginIp || ''
    loginDate.value = u?.loginDate || ''
    createTime.value = u?.createTime || ''
    // 同步表单
    form.nickName = userStore.userInfo?.nickName || ''
    form.email = userStore.userInfo?.email || ''
    form.phonenumber = userStore.userInfo?.phonenumber || ''
  } catch {
    // ignore
  }
})
</script>

<style lang="scss" scoped>
.profile-page { padding: 16px; }
.card-header { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.user-summary {
  text-align: center;
  padding: 20px 0;
  .nick { margin: 12px 0 4px; font-size: 18px; font-weight: 600; }
  .name { margin: 0 0 8px; color: var(--el-text-color-secondary); font-size: 13px; }
  .role-tag { margin: 0 4px 4px 0; }
  .meta-list {
    list-style: none; padding: 0; margin: 0; text-align: left;
    li { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; border-bottom: 1px solid var(--el-border-color-lighter); }
    .meta-label { color: var(--el-text-color-secondary); }
    .meta-value { color: var(--el-text-color-primary); font-weight: 500; }
  }
}
</style>
