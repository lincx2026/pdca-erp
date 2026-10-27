import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const TreeStore = require('element-plus/lib/components/tree/src/model/tree-store.js').default
const roleSource = readFileSync(new URL('../src/views/system/role/index.vue', import.meta.url), 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const roleAst = ts.createSourceFile('role.ts', roleSource, ts.ScriptTarget.Latest, true)
const compile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText

for (const handler of ['onSubmit', 'onAuthMenuSubmit']) {
  for (const [checked, expected] of [[[10], [10, 11, 12, 13, 14, 15]], [[12, 13], [10, 11, 12, 13]]]) {
    test(`${handler} preserves directories and pages for checked ${checked}`, async () => {
      const tree = new TreeStore({ key: 'menuId', props: { children: 'children' }, defaultCheckedKeys: checked,
        data: [{ menuId: 10, children: [{ menuId: 11, children: [{ menuId: 12 }, { menuId: 13 }] }, { menuId: 14, children: [{ menuId: 15 }] }] }] })
      tree.initialize()
      let submitted
      const context = vm.createContext({ form: {}, tabName: {}, formRef: { value: { validate: async () => true } },
        menuTreeRef: { value: tree }, authMenuTreeRef: { value: tree }, submitLoading: {},
        dialog: { mode: 'edit' }, authDialog: { roleId: 2 }, reload() {}, ElMessage: { success() {} },
        roleApi: { edit: async body => { submitted = body.menuIds }, authMenu: async body => { submitted = body.menuIds } } })
      const fn = roleAst.statements.find(n => ts.isFunctionDeclaration(n) && n.name.text === handler)
      assert.ok(fn, `${handler} exists`)
      vm.runInContext(compile(fn.getText(roleAst)), context)
      await context[handler]()
      assert.deepEqual(Array.from(submitted).sort((a, b) => a - b), expected)
    })
  }
}

const permissionExports = {}
vm.runInNewContext(compile(readFileSync(new URL('../src/utils/permission.ts', import.meta.url), 'utf8')), { exports: permissionExports })
const routerSource = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const routerAst = ts.createSourceFile('router.ts', routerSource, ts.ScriptTarget.Latest, true)
let guard
function findGuard(node) {
  if (ts.isCallExpression(node) && node.expression.getText(routerAst) === 'router.beforeEach') guard = node.arguments[0].getText(routerAst)
  ts.forEachChild(node, findGuard)
}
findGuard(routerAst)
const staleCheck = routerAst.statements.find(n => ts.isFunctionDeclaration(n) && n.name?.text === 'menusLooksStaleOrIncomplete')

for (const initiallyLoaded of [true, false]) {
  test(`restricted user checks route permissions after ${initiallyLoaded ? 'cached' : 'initial'} load`, async () => {
    let fetches = 0
    const user = { isLogin: true, userInfo: initiallyLoaded ? { permissions: ['pdm:material:list'] } : null,
      menus: [{ menuId: 10 }], async fetchUserInfo() { fetches++; this.userInfo = { permissions: ['pdm:material:list'] } }, reset() {} }
    const layout = {}
    const context = vm.createContext({ console: { log() {} }, document: {}, NProgress: { start() {} },
      useUserStore: () => user, useLayoutStore: () => layout, hasPerm: permissionExports.hasPerm })
    const code = `let _fetchingUserInfo = null; ${staleCheck?.getText(routerAst) ?? ''}; var runGuard = ${guard.replaceAll('import.meta.env', '({})')}`
    vm.runInContext(compile(code), context)
    let destination
    await context.runGuard({ fullPath: '/system/user', meta: { perms: ['system:user:list'] } }, {}, path => { destination = path })
    assert.equal(destination, '/dashboard')
    assert.equal(layout.flashForbidden, '/system/user')
    assert.equal(fetches, initiallyLoaded ? 0 : 1)
    await context.runGuard({ fullPath: '/pdm/material', meta: { perms: ['pdm:material:list'] } }, {}, path => { destination = path })
    assert.equal(destination, undefined)
    assert.equal(fetches, initiallyLoaded ? 0 : 1, 'Small valid menu must not trigger repeated loads')
  })
}
