/**
 * 通知 WebSocket 连接管理
 * - 登录后自动连接 wss://host/ws/notice?token=xxx
 * - 收到 NOTICE_NEW 消息时回调 onMessage
 * - 30s 心跳保活，断线指数退避重连（最大 10 次）
 */
import { ref, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/stores/user'

export interface NoticePushMsg {
  type: string
  noticeId: number
  noticeType: number
  noticeLevel: number
  title: string
  summary?: string
  sendByName?: string
  sendTime?: string
  actionRoute?: string
}

function buildWsUrl(token: string): string {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${location.host}/ws/notice?token=${encodeURIComponent(token)}`
}

export function useNoticeWebSocket(onMessage: (msg: NoticePushMsg) => void) {
  const userStore = useUserStore()
  const connected = ref(false)
  let ws: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempts = 0
  let pingTimer: ReturnType<typeof setInterval> | null = null
  let destroyed = false

  const MAX_RECONNECT = 10

  function connect() {
    if (destroyed || !userStore.token) return
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return

    try {
      ws = new WebSocket(buildWsUrl(userStore.token))
    } catch {
      scheduleReconnect()
      return
    }

    ws.onopen = () => {
      connected.value = true
      reconnectAttempts = 0
      startPing()
    }

    ws.onmessage = (event: MessageEvent) => {
      try {
        const msg = JSON.parse(event.data) as NoticePushMsg
        if (msg.type === 'pong') return
        onMessage(msg)
      } catch {
        /* ignore non-JSON messages */
      }
    }

    ws.onclose = () => {
      connected.value = false
      stopPing()
      if (!destroyed) scheduleReconnect()
    }

    ws.onerror = () => {
      try { ws?.close() } catch { /* ignore */ }
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (destroyed || reconnectAttempts >= MAX_RECONNECT) return
    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
    reconnectTimer = setTimeout(() => {
      reconnectAttempts++
      connect()
    }, delay)
  }

  function startPing() {
    pingTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) {
        try { ws.send('ping') } catch { /* ignore */ }
      }
    }, 30000)
  }

  function stopPing() {
    if (pingTimer) { clearInterval(pingTimer); pingTimer = null }
  }

  function disconnect() {
    destroyed = true
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
    stopPing()
    if (ws) {
      ws.onclose = null
      ws.onerror = null
      try { ws.close() } catch { /* ignore */ }
      ws = null
    }
    connected.value = false
  }

  onBeforeUnmount(() => disconnect())

  return { connected, connect, disconnect }
}
