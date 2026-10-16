import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { useUserStore } from '@/stores/user'
import { useLayoutStore } from '@/stores/layout'
import { hasPerm } from '@/utils/permission'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true, noAuth: true }
  },
  {
    path: '/',
    name: 'Root',
    component: () => import('@/views/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard',            name: 'Dashboard', component: () => import('@/views/dashboard/index.vue'),   meta: { title: '仪表盘',   icon: 'Odometer' } },
      { path: 'profile',              name: 'Profile',   component: () => import('@/views/profile/index.vue'),     meta: { title: '个人中心', hidden: true } },
      // 系统管理
      { path: 'system/user',          name: 'User',      component: () => import('@/views/system/user/index.vue'),  meta: { title: '用户管理', icon: 'User',      perms: ['system:user:list'] } },
      { path: 'system/role',          name: 'Role',      component: () => import('@/views/system/role/index.vue'),  meta: { title: '角色管理', icon: 'Avatar',    perms: ['system:role:list'] } },
      { path: 'system/menu',          name: 'Menu',      component: () => import('@/views/system/menu/index.vue'),  meta: { title: '菜单管理', icon: 'Menu',      perms: ['system:menu:list'] } },
      { path: 'system/dept',          name: 'Dept',      component: () => import('@/views/system/dept/index.vue'),  meta: { title: '部门管理', icon: 'OfficeBuilding', perms: ['system:dept:list'] } },
      { path: 'system/operlog',       name: 'Operlog',   component: () => import('@/views/monitor/operlog/index.vue'), meta: { title: '操作日志', icon: 'Document',   perms: ['monitor:operlog:list'] } },
      { path: 'system/notice',        name: 'Notice',    component: () => import('@/views/system/notice/index.vue'),    meta: { title: '通知管理', icon: 'Bell',       perms: ['system:notice:list'] } },
      // 产品开发 (PDM) — 方案 2: 拆分为 PCBA产品开发 + 成品产品开发 两个子域
      //   共用物料库 + 产品开发页面, 分别负责不同层级的产品定义
      //   PCBA 产品开发: 管电子物料分类/电子物料/PCBA BOM/SMT工艺/客户料号
      { path: 'pdm/category',         name: 'Category',       component: () => import('@/views/pdm/category/index.vue'),   meta: { title: '电子物料分类', icon: 'Collection', perms: ['pdm:category:list'], scope: 'elec' } },
      { path: 'pdm/material',         name: 'Material',       component: () => import('@/views/pdm/material/index.vue'),   meta: { title: '电子物料',     icon: 'Goods',      perms: ['pdm:material:list'], scope: 'elec' } },
      { path: 'pdm/bom',              name: 'Bom',            component: () => import('@/views/pdm/bom/index.vue'),           meta: { title: 'PCBA BOM',     icon: 'Guide',      perms: ['pdm:bom:list'] } },
      { path: 'pdm/process',          name: 'Process',        component: () => import('@/views/pdm/process/index.vue'),   meta: { title: 'SMT工艺路线',    icon: 'Tools',     perms: ['pdm:process:list'] } },
      { path: 'pdm/custmap',          name: 'CustMap',        component: () => import('@/views/pdm/custmap/index.vue'),   meta: { title: '客户料号',       icon: 'Link',      perms: ['pdm:custmap:list'] } },
      //   成品产品开发: 管成品物料分类/成品物料管理/成品 BOM/组装工艺/成品料号映射
      //   共用 PDM 页面组件, 路由 meta.scope=fg 用于页面预设成品维度筛选/标题区分
      { path: 'pdm/fg-category',      name: 'FgCategory',     component: () => import('@/views/pdm/category/index.vue'),   meta: { title: '成品物料分类', icon: 'Collection', perms: ['pdm:fgcategory:list'], scope: 'fg' } },
      { path: 'pdm/fg-material',      name: 'FgMaterial',     component: () => import('@/views/pdm/material/index.vue'),   meta: { title: '成品物料管理', icon: 'Goods',      perms: ['pdm:fgmaterial:list'], scope: 'fg' } },
      { path: 'pdm/fg-bom',           name: 'FgBom',          component: () => import('@/views/pdm/bom/index.vue'),           meta: { title: '成品BOM',      icon: 'Grid',      perms: ['pdm:fgbom:list'],      scope: 'fg' } },
      { path: 'pdm/fg-process',       name: 'FgProcess',      component: () => import('@/views/pdm/process/index.vue'),   meta: { title: '组装工艺路线', icon: 'Operation', perms: ['pdm:fgprocess:list'],   scope: 'fg' } },
      { path: 'pdm/fg-custmap',       name: 'FgCustMap',      component: () => import('@/views/pdm/custmap/index.vue'),   meta: { title: '成品料号映射', icon: 'Link',      perms: ['pdm:fgcustmap:list'],   scope: 'fg' } },
      // 仓库管理(阶段二 WMS) - 方案 C: 电子仓库 + 成品仓库 两个子域并列, 路由扁平化但权限/菜单分开
      //   电子仓库 (menu 1851): 只管元器件/辅料/包材
      { path: 'wms/warehouse',        name: 'Warehouse', component: () => import('@/views/wms/warehouse/index.vue'), meta: { title: '仓库档案', icon: 'House',       perms: ['wms:warehouse:list'] } },
      { path: 'wms/inventory',        name: 'Inventory', component: () => import('@/views/wms/inventory/index.vue'), meta: { title: '电子料库存', icon: 'Box',         perms: ['wms:inventory:list'] } },
      { path: 'wms/stock-in',         name: 'StockIn',   component: () => import('@/views/wms/stock-in/index.vue'),  meta: { title: '电子料入库', icon: 'Download',    perms: ['wms:stockin:list'] } },
      { path: 'wms/stock-out',        name: 'StockOut',  component: () => import('@/views/wms/stock-out/index.vue'), meta: { title: '电子料出库', icon: 'Upload',      perms: ['wms:stockout:list'] } },
      //   成品仓库 (menu 1852): 只管成品整机 (第 1 期扩展: 完工入库单/成品出库单/流水)
      { path: 'fas/finished-inbound',  name: 'FinishedInbound',  component: () => import('@/views/fas/finished-inbound/index.vue'),  meta: { title: '完工入库单', icon: 'Download',   perms: ['wh:finished:inbound:list'] } },
      { path: 'fas/finished-outbound', name: 'FinishedOutbound', component: () => import('@/views/fas/finished-outbound/index.vue'), meta: { title: '成品出库单', icon: 'Upload',     perms: ['wh:finished:outbound:list'] } },
      { path: 'fas/stock',             name: 'FinishedStock',    component: () => import('@/views/fas/stock/index.vue'),             meta: { title: '成品库存台账', icon: 'Box',      perms: ['wh:finished:list'] } },
      { path: 'fas/finished-flow',     name: 'FinishedFlow',     component: () => import('@/views/fas/finished-flow/index.vue'),     meta: { title: '出入库流水', icon: 'Document',   perms: ['wh:finished:flow:list'] } },
      //   配件仓库 (menu 1853): 外购线材/电池/适配器等, 先复用成品台账页面占位, 后续再补齐入库/出库/流水
      { path: 'fas/acc-stock',         name: 'AccStock',         component: () => import('@/views/fas/stock/index.vue'),             meta: { title: '配件库存台账', icon: 'Coin',     perms: ['wh:acc:list'], scope: 'acc' } },
      // 生产管理(阶段三 PMM)
      { path: 'pm/work-order',        name: 'WorkOrder', component: () => import('@/views/pm/work-order/index.vue'), meta: { title: '生产工单', icon: 'Tickets',     perms: ['pm:workorder:list'] } },
      { path: 'pm/work-bom',          name: 'WorkBom',   component: () => import('@/views/pm/work-bom/index.vue'),   meta: { title: '工单BOM', icon: 'Guide',       perms: ['pm:workbom:list'] } },
      { path: 'pm/process-task',      name: 'ProcessTk', component: () => import('@/views/pm/process-task/index.vue'), meta: { title: '工序任务', icon: 'List',      perms: ['pm:processtask:list'] } },
      { path: 'pm/report',            name: 'PmReport',  component: () => import('@/views/pm/report/index.vue'),     meta: { title: '生产报工', icon: 'EditPen',     perms: ['pm:report:list'] } },
      { path: 'pm/quality',           name: 'Quality',   component: () => import('@/views/pm/quality/index.vue'),    meta: { title: '质量不良', icon: 'Warning',     perms: ['pm:quality:list'] } },
      { path: 'pm/schedule',          name: 'Schedule',  component: () => import('@/views/pm/schedule/index.vue'),   meta: { title: '排产计划', icon: 'Calendar',    perms: ['pm:schedule:list'] } },
      { path: 'pm/kanban',            name: 'Kanban',    component: () => import('@/views/pm/kanban/index.vue'),     meta: { title: '生产看板', icon: 'Monitor',     perms: ['pm:kanban:list'] } },
      { path: 'pm/equipment',         name: 'Equipment', component: () => import('@/views/pm/equipment/index.vue'),meta: { title: '设备管理', icon: 'Cpu',         perms: ['pm:equipment:list'] } },
      { path: 'pm/jig',               name: 'Jig',       component: () => import('@/views/pm/jig/index.vue'),        meta: { title: '工装治具', icon: 'Tools',       perms: ['pm:jig:list'] } },
      { path: 'pm/project',           name: 'Project',   component: () => import('@/views/pm/project/index.vue'),    meta: { title: '项目管理', icon: 'Folder',      perms: ['pm:project:list'] } },
      { path: 'pm/category',          name: 'ProjectCat',component: () => import('@/views/pm/category/index.vue'),   meta: { title: '项目分类', icon: 'Menu',        perms: ['pm:category:list'] } },
      { path: 'hrm/employee',         name: 'Employee',  component: () => import('@/views/hrm/employee/index.vue'),  meta: { title: '员工管理', icon: 'User',        perms: ['hrm:employee:list'] } },
      // 后道组装 (FAS) — 专注生产过程，成品仓已迁移到"仓库管理 > 成品仓库"
      { path: 'fas/order',   name: 'FasOrder',   component: () => import('@/views/fas/order/index.vue'),   meta: { title: '组装工单', icon: 'Document',  perms: ['pm:fas:order:list'] } },
      { path: 'fas/bom',     name: 'FasBom',     component: () => import('@/views/fas/bom/index.vue'),     meta: { title: '组装BOM',  icon: 'Grid',     perms: ['pm:fas:bom:list'] } },
      { path: 'fas/step',    name: 'FasStep',    component: () => import('@/views/fas/step/index.vue'),    meta: { title: '工序流转', icon: 'Operation',perms: ['pm:fas:step:list'] } },
      { path: 'fas/record',  name: 'FasRecord',  component: () => import('@/views/fas/record/index.vue'),  meta: { title: '过程记录', icon: 'List',     perms: ['pm:fas:record:list'] } },
      // 公司设置
      { path: 'company/profile', name: 'CompanyProfile', component: () => import('@/views/company/profile/index.vue'), meta: { title: '公司档案', icon: 'Briefcase', perms: ['company:profile:list'] } },
      { path: 'company/factory', name: 'CompanyFactory', component: () => import('@/views/company/factory/index.vue'), meta: { title: '工厂产线', icon: 'Operation', perms: ['company:factory:list'] } },
      { path: 'company/coding',  name: 'CompanyCoding',  component: () => import('@/views/company/coding/index.vue'),  meta: { title: '编码规则', icon: 'EditPen', perms: ['company:coding:list'] } },
      { path: 'company/param',   name: 'CompanyParam',   component: () => import('@/views/company/param/index.vue'),   meta: { title: '业务参数', icon: 'Setting',  perms: ['company:param:list'] } },
      // 采购管理(阶段四 SCM)
      { path: 'scm/requisition',       name: 'ScmReq',    component: () => import('@/views/scm/requisition/index.vue'), meta: { title: '采购申请', icon: 'Document',    perms: ['scm:requisition:list'] } },
      { path: 'scm/purchase',          name: 'ScmPo',     component: () => import('@/views/scm/purchase/index.vue'),    meta: { title: '采购订单', icon: 'ShoppingCart', perms: ['scm:purchase:list'] } },
      { path: 'scm/delivery',          name: 'ScmDel',    component: () => import('@/views/scm/delivery/index.vue'),    meta: { title: '采购到货', icon: 'Van',         perms: ['scm:delivery:list'] } },
      { path: 'scm/return',            name: 'ScmRet',    component: () => import('@/views/scm/return/index.vue'),      meta: { title: '采购退货', icon: 'RefreshLeft', perms: ['scm:return:list'] } },
      { path: 'scm/price',             name: 'ScmPrice',  component: () => import('@/views/scm/price/index.vue'),       meta: { title: '供应商价格', icon: 'Coin',      perms: ['scm:price:list'] } },
      { path: 'scm/supplier',          name: 'ScmSupp',   component: () => import('@/views/scm/supplier/index.vue'),    meta: { title: '供应商管理', icon: 'UserFilled', perms: ['scm:supplier:list'] } },
      { path: 'scm/kanban',            name: 'ScmKanban', component: () => import('@/views/scm/kanban/index.vue'),      meta: { title: '采购看板', icon: 'Monitor',     perms: ['scm:kanban:list'] } },
      // 采购应付对账
      { path: 'scm/ap-payable',        name: 'ScmApPay', component: () => import('@/views/scm/ap-payable/index.vue'),  meta: { title: '应付单据', icon: 'Tickets',     perms: ['scm:appayable:list'] } },
      { path: 'scm/ap-plan',           name: 'ScmApPlan',component: () => import('@/views/scm/ap-plan/index.vue'),     meta: { title: '付款计划', icon: 'Calendar',    perms: ['scm:apayplan:list'] } },
      { path: 'scm/ap-payment',        name: 'ScmApPmt', component: () => import('@/views/scm/ap-payment/index.vue'),  meta: { title: '付款记录', icon: 'Wallet',      perms: ['scm:apayment:list'] } },
      { path: 'scm/ap-kanban',         name: 'ScmApKb',  component: () => import('@/views/scm/ap-kanban/index.vue'),   meta: { title: '应付看板', icon: 'PieChart',    perms: ['scm:apkanban:list'] } },
      // 销售管理
      { path: 'sal/customer',     name: 'SalCustomer', component: () => import('@/views/sal/customer/index.vue'),     meta: { title: '客户管理', icon: 'UserFilled', perms: ['sal:customer:list'] } },
      { path: 'sal/order',        name: 'SalOrder',    component: () => import('@/views/sal/order/index.vue'),        meta: { title: '销售订单', icon: 'Document',    perms: ['sal:order:list'] } },
      { path: 'sal/return',       name: 'SalReturn',   component: () => import('@/views/sal/return/index.vue'),       meta: { title: '销售退货', icon: 'RefreshLeft', perms: ['sal:return:list'] } },
      { path: 'sal/ar-receivable',name: 'SalArRec',    component: () => import('@/views/sal/ar-receivable/index.vue'),meta: { title: '应收单据', icon: 'Tickets',     perms: ['sal:arreceivable:list'] } },
      { path: 'sal/ar-plan',      name: 'SalArPlan',   component: () => import('@/views/sal/ar-plan/index.vue'),     meta: { title: '收款计划', icon: 'Calendar',    perms: ['sal:arplan:list'] } },
      { path: 'sal/ar-receipt',   name: 'SalArRcpt',   component: () => import('@/views/sal/ar-receipt/index.vue'),  meta: { title: '收款记录', icon: 'Wallet',      perms: ['sal:arreceipt:list'] } },
      { path: 'sal/kanban',       name: 'SalKanban',   component: () => import('@/views/sal/kanban/index.vue'),       meta: { title: '销售看板', icon: 'Monitor',     perms: ['sal:kanban:list'] } },
      { path: 'sal/forecast',          name: 'SalForecast',     component: () => import('@/views/sal/forecast/index.vue'),     meta: { title: '销售预测', icon: 'TrendCharts', perms: ['sal:forecast:list'] } },
      { path: 'sal/forecast-kanban',   name: 'SalForecastKb',   component: () => import('@/views/sal/forecast/kanban.vue'),     meta: { title: '预测看板', icon: 'Monitor',     perms: ['sal:forecast:kanban'] } },
      { path: 'sal/price',          name: 'SalPrice',    component: () => import('@/views/sal/price/index.vue'),     meta: { title: '销售报价', icon: 'PriceTag',     perms: ['sal:price:list'] } },
      { path: 'sal/invoice',        name: 'SalInvoice',  component: () => import('@/views/sal/invoice/index.vue'),   meta: { title: '开票管理', icon: 'Document',     perms: ['sal:invoice:list'] } },
      { path: 'sal/kitting',        name: 'SalKitting',  component: () => import('@/views/sal/kitting/index.vue'),   meta: { title: '销售齐套', icon: 'Box',          perms: ['sal:kitting:list'] } },
      // 贴片管理(ASM/SMT 贴装)
      { path: 'asm/kitting',  name: 'AsmKitting',  component: () => import('@/views/asm/kitting/index.vue'),  meta: { title: '贴片配料', icon: 'Box',         perms: ['asm:kitting:list'] } },
      { path: 'asm/sn',       name: 'AsmSn',       component: () => import('@/views/asm/sn/index.vue'),       meta: { title: 'SN码管理',  icon: 'Tickets',       perms: ['asm:sn:list'] } },
      { path: 'asm/feeder',   name: 'AsmFeeder',   component: () => import('@/views/asm/feeder/index.vue'),   meta: { title: 'Feeder上料',icon: 'Link',         perms: ['asm:feeder:list'] } },
      { path: 'asm/trace',    name: 'AsmTrace',    component: () => import('@/views/asm/trace/index.vue'),    meta: { title: '条码追溯',  icon: 'Position',      perms: ['asm:trace:list'] } },
    ]
  },
  {
    path: '/404', name: 'NotFound', component: () => import('@/views/error/404.vue'), meta: { hidden: true, noAuth: true }
  },
  { path: '/:pathMatch(.*)*', redirect: '/404', meta: { hidden: true } }
]

const router = createRouter({ history: createWebHashHistory(), routes, scrollBehavior: () => ({ top: 0 }) })
NProgress.configure({ showSpinner: false })

/** 全局标记：避免权限守卫重复并发触发 fetchUserInfo */
let _fetchingUserInfo: Promise<any> | null = null

/**
 * 判断 menus 是否是"可信的全量"数据。
 * 异常判定（任一满足则视为"旧缓存/未加载"，必须重新 fetch）：
 *  - null/空数组
 *  - 顶层菜单 < 3（正常 admin 至少含仪表盘/系统管理/系统监控 3 项）
 *  - 顶层项里看不到 menuId=185(仓储管理) 或 path=='wms'
 * 这是针对早期 persist 未 pick['token'] 时写入的脏 menus(长度=2) 做兜底。
 */
function menusLooksStaleOrIncomplete(menus: any[] | undefined): boolean {
  if (!menus || !menus.length) return true
  if (menus.length < 3) return true
  const norm = (p: any) => String(p || '').replace(/^\/+|\/+$/g, '').toLowerCase()
  /** 递归扁平化菜单树, 支持菜单挂在 2/3 级的场景 (如 贴片管理 作为 产品管理 的子级) */
  const flatten = (list: any[] | undefined): any[] => {
    if (!list) return []
    const out: any[] = []
    for (const m of list) {
      out.push(m)
      if (Array.isArray(m.children) && m.children.length) out.push(...flatten(m.children))
    }
    return out
  }
  const flat = flatten(menus)
  const hasWms = (m: any) => (m && (m.menuId === 185 || norm(m.path) === 'wms'))
  const hasPm  = (m: any) => (m && (m.menuId === 250 || norm(m.path) === 'pm'))
  const hasScm = (m: any) => (m && (m.menuId === 300 || norm(m.path) === 'scm'))
  const hasSal = (m: any) => (m && (m.menuId === 500 || norm(m.path) === 'sal'))
  const hasAsm = (m: any) => (m && (m.menuId === 700 || norm(m.path) === 'asm'))
  return !flat.some(hasWms) || !flat.some(hasPm) || !flat.some(hasScm) || !flat.some(hasSal) || !flat.some(hasAsm)
}

router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  document.title = (to.meta?.title ? to.meta.title + ' - ' : '') + (import.meta.env.VITE_APP_TITLE || '智健ERP系统')
  const userStore = useUserStore()

  if (to.meta.noAuth) return next()
  if (!userStore.isLogin) {
    const redirect = to.fullPath === '/login' ? undefined : encodeURIComponent(to.fullPath)
    return next('/login' + (redirect ? '?redirect=' + redirect : ''))
  }

  // 需要拉取用户信息的场景：
  // 1) userInfo 缺失
  // 2) menus 看起来不完整/是旧缓存 (即使 userInfo 非空也要强制回源一次)
  const needFetch = !userStore.userInfo || menusLooksStaleOrIncomplete(userStore.menus as any[])
  if (needFetch) {
    if (!_fetchingUserInfo) {
      _fetchingUserInfo = userStore.fetchUserInfo()
        .finally(() => { _fetchingUserInfo = null })
    }
    try {
      await _fetchingUserInfo
      next()
    } catch (e) {
      userStore.reset()
      next('/login')
    }
    return
  }
  // 菜单/按钮权限拦截: 路由级 perms 控制
  const perms: string[] | undefined = (to.meta as any).perms
  if (perms && perms.length) {
    const all = userStore.userInfo?.permissions || []
    const hasMatch = hasPerm(perms, all)
    console.log('[router] perm check', { perms, all: Array.from(all as any), hasMatch })
    if (!hasMatch) {
      console.log('[router] → PERM DENIED → /dashboard')
      useLayoutStore().flashForbidden = to.fullPath
      return next('/dashboard')
    }
  }
  console.log('[router] → ALLOWED')
  next()
})

router.afterEach(() => NProgress.done())
export default router
