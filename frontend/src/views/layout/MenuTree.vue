<template>
  <template v-for="item in items" :key="item.menuId">
    <!--
      目录/分组: M 类型 且有可渲染子项 (M/C), 或任意节点只要含可渲染 children (兜底扁平化漏配)
      注意: 后端只把 M/C 放进 menus (F 已在 SQL 过滤), 但显式再过滤一次防御数据异常.
    -->
    <el-sub-menu
      v-if="hasRenderableChildren(item)"
      :index="buildPath(parentPath, item.path)"
    >
      <template #title>
        <el-icon v-if="item.icon"><component :is="iconFor(item.icon)" /></el-icon>
        <span>{{ item.menuName }}</span>
      </template>
      <MenuTree
        :items="renderableChildren(item)"
        :parent-path="buildPath(parentPath, item.path)"
      />
    </el-sub-menu>
    <!-- 叶子菜单: 非 F(按钮) 类型 且 有 path -->
    <el-menu-item
      v-else-if="isRenderableLeaf(item)"
      :index="buildPath(parentPath, item.path)"
    >
      <el-icon v-if="item.icon"><component :is="iconFor(item.icon)" /></el-icon>
      <template #title>{{ item.menuName }}</template>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { SysMenu } from '@/types/models'
import * as Icons from '@element-plus/icons-vue'
import type { Component } from 'vue'

defineProps<{
  items: SysMenu[]
  parentPath?: string
}>()

/** 可渲染的菜单类型: 目录(M) 或 页面(C) */
function isMenuNode(m: SysMenu | undefined | null): boolean {
  if (!m) return false
  const t = m.menuType
  return t === 'M' || t === 'C'
}
/** 取一个节点下可被递归渲染的子节点 (剔除 null/按钮F) */
function renderableChildren(m: SysMenu): SysMenu[] {
  const c = m.children
  if (!c || !Array.isArray(c)) return []
  return c.filter(isMenuNode)
}
/** 该节点是否是一个"可展开"的 sub-menu */
function hasRenderableChildren(m: SysMenu): boolean {
  if (m.menuType === 'F') return false
  return renderableChildren(m).length > 0
}
/** 该节点是否是菜单项 (非按钮非空 path) */
function isRenderableLeaf(m: SysMenu): boolean {
  if (!isMenuNode(m)) return false
  return !!m.path
}

/** 拼接父路径与子路径, 得到绝对路由路径 (如: parent=pdm + child=category → /pdm/category)
 *  特例: 若 child 以 '/' 开头则视为"绝对路径路由", 直接使用, 不再拼接父级
 *       (用于三级菜单: 如 产品管理→贴片管理→贴片配料 的贴片配料 path=/asm/kitting
 *        不希望拼出 /pdm/asm/kitting, 因为前端路由只注册了 asm/kitting)
 */
function buildPath(parent: string | undefined, child: string | undefined): string {
  const c = (child || '').replace(/\/+$/, '')
  // child 是绝对路径: 直接返回 (以 / 开头)
  if (/^\//.test(child || '')) {
    return c || '/'
  }
  const p = (parent || '').replace(/^\/+|\/+$/g, '')
  const c2 = (child || '').replace(/^\/+/, '')
  if (!p && !c2) return '/'
  if (!p) return '/' + c2
  if (!c2) return '/' + p
  return '/' + p + '/' + c2
}

// 图标名 -> 组件
const IconMap = Icons as Record<string, Component>

// 别名映射: 处理 kebab-case、不存在的图标名、语义别名
const IconAlias: Record<string, string> = {
  // 原有 DB 中不存在的图标名 → Element Plus 实际组件名
  system: 'Setting',
  component: 'Box',
  cpu: 'Cpu',
  'shopping-cart': 'ShoppingCart',
  wallet: 'Wallet',
  'shopping-bag': 'ShoppingBag',
  // 常见 kebab-case → PascalCase 快速映射
  'tree-table': 'TreeTable',
  'office-building': 'OfficeBuilding',
  'user-filled': 'UserFilled',
  'edit-pen': 'EditPen',
  'circle-check': 'CircleCheck',
  'circle-check-filled': 'CircleCheckFilled',
  'refresh-left': 'RefreshLeft',
  'credit-card': 'CreditCard',
  'data-line': 'DataLine',
  'data-analysis': 'DataAnalysis',
  'data-board': 'DataBoard',
  'trend-charts': 'TrendCharts',
  'info-filled': 'InfoFilled',
  'success-filled': 'SuccessFilled',
  'question-filled': 'QuestionFilled',
  'warning-filled': 'WarningFilled',
  'star-filled': 'StarFilled',
  'map-location': 'MapLocation',
  'place': 'Location',
  'location': 'Location',
  'sort': 'Sort',
  'rank': 'Rank',
  'document': 'Document',
  'documents': 'Documents',
  'files': 'Files',
  'folder': 'Folder',
  'menu': 'Menu',
  'user': 'User',
  'setting': 'Setting',
  'tools': 'Tools',
  'house': 'House',
  'money': 'Money',
  'coin': 'Coin',
  'goods': 'Goods',
  'box': 'Box',
  'tickets': 'Tickets',
  'list': 'List',
  'operation': 'Operation',
  'calendar': 'Calendar',
  'download': 'Download',
  'upload': 'Upload',
  'building': 'Building',
  'van': 'Van',
  'phone': 'Phone',
  'connection': 'Connection',
  'monitor': 'Monitor',
  'lock': 'Lock',
  'key': 'Key',
  'flag': 'Flag',
  'bell': 'Bell',
  'search': 'Search',
  'plus': 'Plus',
  'delete': 'Delete',
  'edit': 'Edit',
  'view': 'View',
  'warning': 'Warning',
  'star': 'Star',
  'heart': 'Heart',
  // DB 中遗留的 kebab/非标准名 → Element Plus 实际组件名兜底
  'peoples': 'UserFilled',   // 角色管理 (复数用户)
  'log': 'Document',          // 操作日志 (文件)
  'truck': 'Van',             // 采购到货 (Element Plus 中是 Van)
  'guide': 'Guide',           // BOM管理/工单BOM
  'tree': 'OfficeBuilding',   // 部门管理/物料分类 (Tree 不存在, 用楼)
  'link': 'Link',              // 客户料号 (链接)
  'warning': 'WarningFilled', // 质量不良 (实心警告)
  'money': 'Money',
  'histogram': 'Histogram',
  'data-analysis': 'DataAnalysis',
  'document-checked': 'DocumentChecked',
  'price-tag': 'PriceTag',
  'data-line': 'DataLine',
  'trend-charts': 'TrendCharts',
  'refresh-left': 'RefreshLeft',
  'edit-pen': 'EditPen',
  'shopping-cart': 'ShoppingCart',
  'shopping-bag': 'ShoppingBag',
  'user-filled': 'UserFilled',
}

/** kebab-case / snake_case → PascalCase */
function toPascalCase(s: string): string {
  return s.split(/[-_\s]/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join('')
}

function iconFor(name: string | undefined): Component | undefined {
  if (!name) return undefined
  // 1. 别名映射优先 (处理不存在的图标名和 kebab-case)
  const alias = IconAlias[name]
  if (alias && IconMap[alias]) return IconMap[alias]
  // 2. 直接匹配 (已经是 PascalCase)
  if (IconMap[name]) return IconMap[name]
  // 3. kebab/snake → PascalCase 转换后匹配
  const pascal = toPascalCase(name)
  if (IconMap[pascal]) return IconMap[pascal]
  // 4. fallback 到默认 Menu 图标
  return IconMap['Menu']
}
</script>
