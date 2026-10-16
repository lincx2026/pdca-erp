<template>
  <el-dropdown trigger="click" @visible-change="onVisible" :hide-on-click="false" @command="onCommand">
    <span class="notice-trigger">
      <el-tooltip content="消息中心">
        <el-badge :value="unreadTotal" :hidden="unreadTotal <= 0" :max="99" class="badge-root" :type="hasUrgent ? 'danger' : 'warning'">
          <el-button text circle size="default"><el-icon :size="20"><Bell /></el-icon></el-button>
        </el-badge>
      </el-tooltip>
    </span>
    <template #dropdown>
      <el-dropdown-menu class="notice-dropdown-menu">
        <!-- 头部：标题 + 全部已读 + tab -->
        <div class="nd-header" @click.stop>
          <div class="nd-title">
            <el-icon><Bell /></el-icon>
            <span>消息中心</span>
            <el-tag v-if="unreadTotal > 0" type="danger" effect="dark" round style="margin-left:6px">{{ unreadTotal }} 条未读</el-tag>
          </div>
          <el-button link type="primary" :disabled="unreadTotal === 0" @click="markAll">全部标为已读</el-button>
        </div>

        <!-- Tab 分类切换 -->
        <div class="nd-tabs" @click.stop>
          <div
            v-for="t in TABS" :key="t.key"
            class="nd-tab"
            :class="{ active: activeTab === t.key }"
            @click="activeTab = t.key; loadList(true)">
            <span>{{ t.label }}</span>
            <el-badge v-if="tabUnread[t.key] > 0" :value="tabUnread[t.key]" class="tab-badge" />
          </div>
        </div>

        <!-- 列表 -->
        <div class="nd-list" @click.stop>
          <el-scrollbar :max-height="420" always>
            <div v-if="listLoading" class="nd-loading"><el-icon class="spin"><Loading /></el-icon> 加载中...</div>
            <div v-else-if="list.length === 0" class="nd-empty">
              <el-icon :size="40" color="#cbd5e1"><ChatDotRound /></el-icon>
              <div>暂无{{ currentTabLabel }}消息</div>
            </div>
            <div v-else>
              <div
                v-for="n in list" :key="n.noticeId"
                class="nd-item"
                :class="{ unread: n.readFlag === 0 }"
                @click="handleItemClick(n)">
                <div class="nd-item-head">
                  <el-tag :type="lvTag(n.noticeLevel)" size="small" effect="plain">{{ lvLabel(n.noticeLevel) }}</el-tag>
                  <el-tag :type="typeTag(n.noticeType)" size="small" style="margin-left:6px">{{ typeLabel(n.noticeType) }}</el-tag>
                  <span class="nd-item-time">{{ formatTime(n.sendTime || n.createTime) }}</span>
                  <span v-if="n.readFlag === 0" class="nd-dot"></span>
                </div>
                <div class="nd-item-title">
                  <el-icon v-if="n.isTop === 1" color="#f59e0b" style="flex-shrink:0;margin-right:4px"><Top /></el-icon>
                  <span>{{ n.title }}</span>
                </div>
                <div class="nd-item-sum" v-if="n.summary">{{ n.summary }}</div>
                <div class="nd-item-footer">
                  <span class="sender">{{ n.sendByName || '系统' }}</span>
                  <div class="nd-item-actions">
                    <el-button link size="small" type="primary" @click.stop="goAction(n)" v-if="n.actionRoute">
                      <el-icon><Promotion /></el-icon> 前往处理
                    </el-button>
                    <el-button link size="small" @click.stop="doMarkRead(n)" v-if="n.readFlag === 0">标为已读</el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-scrollbar>
        </div>

        <!-- 底部：查看全部 -->
        <div class="nd-footer" @click.stop>
          <el-button link type="primary" @click="goAll" style="width:100%;justify-content:center">
            查看全部消息 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </el-dropdown-menu>
    </template>
  </el-dropdown>

  <!-- 详情Dialog -->
  <el-dialog v-model="detailVisible" :title="detail?.title || '通知详情'" width="640px" destroy-on-close>
    <template v-if="detail">
      <div class="d-meta">
        <el-tag :type="typeTag(detail.noticeType)">{{ typeLabel(detail.noticeType) }}</el-tag>
        <el-tag :type="lvTag(detail.noticeLevel)" size="small" style="margin-left:6px">{{ lvLabel(detail.noticeLevel) }}</el-tag>
        <el-tag v-if="detail.isTop === 1" type="warning" effect="dark" style="margin-left:6px">置顶</el-tag>
        <span class="d-info">
          <span>{{ detail.sendByName || '系统消息' }}</span> ·
          <span>{{ detail.sendTime || detail.createTime }}</span>
        </span>
      </div>
      <el-divider style="margin:10px 0" />
      <div v-if="detail.summary" class="d-summary"><b>摘要：</b>{{ detail.summary }}</div>
      <div v-if="detail.content" class="d-content" v-html="detail.content" />
      <div v-else class="d-empty">（无正文）</div>
      <div v-if="detail.actionRoute" style="margin-top:14px;text-align:right">
        <el-button type="primary" @click="goRoute(detail.actionRoute!); detailVisible=false">
          前往处理 <el-icon><Promotion /></el-icon>
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import {
  Bell, Loading, ChatDotRound, Top, Promotion, ArrowRight
} from '@element-plus/icons-vue'
import { noticeUserApi } from '@/api'
import type { SysNotice, NoticeCountResp } from '@/types/models'
import { useNoticeWebSocket, type NoticePushMsg } from '@/composables/useNoticeWebSocket'

const router = useRouter()

const unreadTotal = ref(0)
const hasUrgent = ref(false)
const countResp = reactive<NoticeCountResp>({ total: 0, type1: 0, type2: 0, type3: 0, type4: 0 })

const TYPE_OPT: [number, string][] = [[1, '系统公告'], [2, '业务通知'], [3, '预警提醒'], [4, '审批待办']]
const LV_OPT: [number, string][] = [[1, '普通'], [2, '重要'], [3, '紧急']]

const typeLabel = (n?: number) => TYPE_OPT.find(t => t[0] === n)?.[1] || '通知'
const lvLabel = (n?: number) => LV_OPT.find(t => t[0] === n)?.[1] || '普通'
function typeTag(n?: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (n === 1) return 'primary'
  if (n === 2) return 'success'
  if (n === 3) return 'danger'
  if (n === 4) return 'warning'
  return 'info'
}
function lvTag(n?: number): '' | 'warning' | 'danger' {
  if (n === 2) return 'warning'
  if (n === 3) return 'danger'
  return ''
}
function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(5, 16) // MM-DD HH:mm
}

/** Tab: key = noticeType (0 = ALL) */
const TABS: { key: number; label: string }[] = [
  { key: 0, label: '全部' },
  { key: 1, label: '公告' },
  { key: 2, label: '业务' },
  { key: 3, label: '预警' },
]
const activeTab = ref(0)
const dropdownVisible = ref(false)
const currentTabLabel = computed(() => TABS.find(t => t.key === activeTab.value)?.label || '')
const tabUnread = computed(() => ({
  0: countResp.total, 1: countResp.type1, 2: countResp.type2,
  3: countResp.type3, 4: countResp.type4,
}))

const list = ref<SysNotice[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(10)

async function fetchCount() {
  try {
    const res = (await noticeUserApi.count()) as any
    const r: any = res || {}
    countResp.total = Number(r.total || 0)
    countResp.type1 = Number(r.type1 || 0)
    countResp.type2 = Number(r.type2 || 0)
    countResp.type3 = Number(r.type3 || 0)
    countResp.type4 = Number(r.type4 || 0)
    unreadTotal.value = countResp.total
    hasUrgent.value = (countResp.type3 || 0) > 0
  } catch (e: any) {
    // 未登录或 401 忽略
  }
}

async function loadList(reset = false) {
  if (reset) page.value = 1
  listLoading.value = true
  try {
    const res: any = await noticeUserApi.list({
      pageNum: page.value, pageSize: pageSize.value,
      noticeType: activeTab.value || undefined,
      isRead: undefined, keyword: undefined,
    })
    list.value = res.rows || []
  } finally { listLoading.value = false }
}

async function doMarkRead(n: SysNotice) {
  try {
    await noticeUserApi.markRead(n.noticeId!)
    n.readFlag = 1
    unreadTotal.value = Math.max(0, unreadTotal.value - 1)
    const k = `type${n.noticeType}` as keyof NoticeCountResp
    if ((countResp as any)[k] != null) (countResp as any)[k] = Math.max(0, (countResp as any)[k] - 1)
  } catch (e: any) { /* ignore */ }
}

async function markAll() {
  await noticeUserApi.markAll()
  ElMessage.success('已全部标为已读')
  unreadTotal.value = 0
  countResp.total = 0; countResp.type1 = 0; countResp.type2 = 0; countResp.type3 = 0; countResp.type4 = 0
  list.value.forEach(n => (n.readFlag = 1))
}

const detailVisible = ref(false)
const detail = ref<SysNotice | null>(null)
async function handleItemClick(n: SysNotice) {
  // 标记已读并打开详情
  if (n.readFlag === 0) doMarkRead(n)
  try {
    const res: any = await noticeUserApi.get(n.noticeId!)
    detail.value = Object.assign({}, n, res)
    detailVisible.value = true
  } catch (e: any) {
    detail.value = n
    detailVisible.value = true
  }
}
function goAction(n: SysNotice) {
  if (n.readFlag === 0) doMarkRead(n)
  goRoute(n.actionRoute!)
}
function goRoute(path: string) {
  if (!path) return
  if (path.startsWith('http')) { window.open(path, '_blank'); return }
  try {
    router.push(path.startsWith('/') ? path : '/' + path)
  } catch (e: any) { ElMessage.warning('无法跳转到：' + path) }
}

function onVisible(v: boolean) {
  dropdownVisible.value = v
  if (v) { activeTab.value = 0; loadList(true) }
}
function onCommand() { /* noop, el-dropdown command not used */ }

function goAll() {
  // 第一期：跳转到首页（暂无用户中心页面）；后续可做 /notice/my
  router.push('/dashboard')
}

// WebSocket 实时推送 + 轮询兜底
function onWsMessage(msg: NoticePushMsg) {
  if (msg.type !== 'NOTICE_NEW') return
  // 增加未读计数
  unreadTotal.value++
  const k = `type${msg.noticeType}` as keyof NoticeCountResp
  if ((countResp as any)[k] != null) (countResp as any)[k]++
  if (msg.noticeType === 3) hasUrgent.value = true
  // 如果下拉面板已展开，刷新列表
  if (dropdownVisible.value) loadList(true)
  // 浏览器通知
  showBrowserNotification(msg)
  // 页面内 Toast 通知
  ElNotification({
    title: msg.title,
    message: msg.summary || '',
    type: msg.noticeLevel === 3 ? 'error' : msg.noticeLevel === 2 ? 'warning' : 'info',
    duration: 5000,
    position: 'top-right',
  })
}

function showBrowserNotification(msg: NoticePushMsg) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return
  try {
    new Notification(msg.title, {
      body: msg.summary || '',
      icon: '/favicon.ico',
      tag: `notice-${msg.noticeId}`,
    })
  } catch { /* ignore */ }
}

const { connected: wsConnected, connect: wsConnect, disconnect: wsDisconnect } = useNoticeWebSocket(onWsMessage)

let _timer: any = null
onMounted(() => {
  fetchCount()
  wsConnect()
  // 请求浏览器通知权限
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission().catch(() => {})
  }
  // 兜底轮询：5 分钟一次（WebSocket 断线时补偿）
  _timer = setInterval(fetchCount, 300_000)
})
onBeforeUnmount(() => {
  if (_timer) clearInterval(_timer)
  wsDisconnect()
})

// 对外暴露刷新方法（登录/登出后可调用）
defineExpose({ refresh: fetchCount })
</script>

<style lang="scss" scoped>
.badge-root { }
.notice-trigger {
  display: inline-flex; align-items: center; cursor: pointer;
}
.notice-dropdown-menu {
  width: 420px; max-width: 95vw; padding: 0; user-select: none;
  :deep(.el-dropdown-menu__item) { padding: 0; }
  .el-dropdown-menu__item:hover { background: transparent; }
}
.nd-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border-bottom: 1px solid #f3f4f6;
  .nd-title { font-weight:600; color:#111827; display:flex; align-items:center; }
}
.nd-tabs {
  display: flex; border-bottom: 1px solid #f3f4f6; background:#fafafa;
  .nd-tab {
    padding: 10px 14px; font-size:13px; color:#6b7280; cursor:pointer;
    position:relative; display:flex; align-items:center; gap:6px;
    &.active { color:#f59e0b; border-bottom: 2px solid #f59e0b; background:#fff; font-weight:600; }
    .tab-badge { :deep(.el-badge__content) { transform: scale(0.85); top:6px; right:-8px; } }
  }
}
.nd-list { }
.nd-loading { padding:30px 0; text-align:center; color:#9ca3af; font-size:13px; display:flex; align-items:center; justify-content:center; gap:6px; }
.nd-empty { padding:36px 16px; text-align:center; color:#9ca3af; font-size:13px;
  div { margin-top:8px; }
}
.nd-item {
  padding: 12px 16px; border-bottom: 1px solid #f3f4f6; cursor:pointer; transition: background .15s;
  &:hover { background: #fffbeb; }
  &.unread { background: #fffbeb77; }
  .nd-item-head { display:flex; align-items:center; font-size:12px; color:#6b7280; position:relative;
    .nd-item-time { margin-left:auto; color:#9ca3af; }
    .nd-dot { width:8px; height:8px; border-radius:50%; background:#ef4444; margin-left:8px; flex-shrink:0; }
  }
  .nd-item-title { margin:6px 0 4px; font-weight:600; color:#111827; font-size:14px; line-height:1.45; display:flex; align-items:flex-start; word-break:break-all; }
  .nd-item-sum { color:#6b7280; font-size:12px; line-height:1.5;
    display:-webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  }
  .nd-item-footer { margin-top:8px; display:flex; align-items:center; justify-content:space-between;
    .sender { color:#9ca3af; font-size:12px; }
    .nd-item-actions { display:flex; gap:6px; }
  }
}
.nd-footer {
  border-top: 1px solid #f3f4f6; padding: 8px 0;
  :deep(.el-button) { font-size:13px; }
}

.d-meta { display:flex; align-items:center; flex-wrap:wrap; gap:6px;
  .d-info { margin-left: auto; color:#6b7280; font-size:13px; }
}
.d-summary { background:#fffbeb; padding:10px 12px; border-radius:6px; color:#92400e; font-size:13px; margin-bottom:12px; }
.d-content { font-size:14px; line-height:1.75; color:#1f2937; }
.d-empty { color:#9ca3af; text-align:center; padding:24px; }
:deep(.d-content) p { margin:0 0 10px; }
:deep(.d-content) ul, :deep(.d-content) ol { padding-left:24px; margin-bottom:10px; }
:deep(.d-content) b { color:#111827; }

.spin { animation: spin 1s linear infinite; display:inline-block; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
