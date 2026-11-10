import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'

const component = readFileSync(new URL('../src/views/print/StockOutPrint.vue', import.meta.url), 'utf8')
const settingsSource = readFileSync(new URL('../src/views/print/stockOutPrintSettings.ts', import.meta.url), 'utf8')
const exports = {}
runInNewContext(ts.transpile(settingsSource, { module: ts.ModuleKind.CommonJS }), { exports, Object, Number })

test('column widths restore defaults and normalize only visible columns', () => {
  const defaults = exports.defaultWidths()
  const widths = exports.readWidths({ name: 50, spec: 25, code: null, index: -1 })
  assert.equal(widths.name, 50)
  assert.equal(widths.spec, 25)
  assert.equal(widths.code, defaults.code)
  assert.equal(widths.index, defaults.index)
  const all = exports.visibleWidths(widths, true, true)
  const hidden = exports.visibleWidths(widths, false, false)
  assert.ok(Math.abs(all.reduce((sum, item) => sum + item.percentage, 0) - 100) < 1e-9)
  assert.ok(Math.abs(hidden.reduce((sum, item) => sum + item.percentage, 0) - 100) < 1e-9)
  assert.equal(hidden.some(item => item.key === 'spec' || item.key === 'remark'), false)
  assert.ok(hidden.find(item => item.key === 'name').percentage > all.find(item => item.key === 'name').percentage)
  assert.equal(exports.readWidths(undefined).name, defaults.name)
})

function loader(getDetail, getProfile = async () => ({ companyName: 'test' })) {
  const match = component.match(/let requestSequence = 0\nconst loadData = async \(\) => \{[\s\S]*?\n\}\n\nwatch/)
  assert.ok(match)
  const code = match[0].replace(/\n\nwatch$/, '') + '\nglobalThis.loadData = loadData; globalThis.invalidate = () => { requestSequence++ }'
  const context = {
    props: { order: { stockOutId: 1, warehouseId: 7, warehouseName: 'warehouse' } },
    loading: { value: false }, loaded: { value: false }, loadError: { value: '' },
    detailOrder: { value: null }, detailLines: { value: [] }, company: { value: null },
    companyApi: { stockOutPrintProfile: getProfile }, stockOutApi: { get: getDetail }, Promise, String, Array, Error
  }
  runInNewContext(ts.transpile(code), context)
  return context
}

test('failed or incomplete detail never produces a printable document, and retry succeeds', async () => {
  let fail = true
  const c = loader(async () => { if (fail) throw new Error('502'); return { stockOutId: 1, warehouseId: 7, lines: [{ materialCode: 'M1' }] } })
  c.detailLines.value = [{ materialCode: 'old' }]
  await c.loadData()
  assert.equal(c.loaded.value, false)
  assert.equal(c.detailOrder.value, null)
  assert.equal(c.detailLines.value.length, 0)
  assert.match(c.loadError.value, /重新加载/)
  fail = false
  await c.loadData()
  assert.equal(c.loaded.value, true)
  assert.equal(c.detailLines.value[0].materialCode, 'M1')
  assert.equal(c.detailOrder.value.warehouseName, 'warehouse')
  assert.equal(c.loadError.value, '')
  const wrong = loader(async () => ({ stockOutId: 2, lines: [{}] }))
  await wrong.loadData()
  assert.equal(wrong.loaded.value, false)
})

test('a late response from a previous document cannot replace the current one', async () => {
  let resolveFirst
  const c = loader(id => id === 1 ? new Promise(resolve => { resolveFirst = resolve }) : Promise.resolve({ stockOutId: 2, lines: [{ materialCode: 'new' }] }))
  const first = c.loadData()
  c.props.order = { stockOutId: 2 }
  await c.loadData()
  resolveFirst({ stockOutId: 1, lines: [{ materialCode: 'old' }] })
  await first
  assert.equal(c.detailOrder.value.stockOutId, 2)
  assert.equal(c.detailLines.value[0].materialCode, 'new')
})

test('print button and handler both reject unavailable or empty data', () => {
  assert.match(component, /:disabled="!canPrint"/)
  assert.match(component, /detailLines\.value\.length > 0/)
  assert.match(component, /if \(!el \|\| !canPrint\.value\) return/)
  assert.doesNotMatch(component, /Array\.from\(\{ length: 5 \}/)
  assert.match(component, /confirmPrint\(printedId\)/)
  assert.match(component, /<colgroup><col v-for="column in columns"/)
  assert.match(component, /\.data-table\{table-layout:fixed\}/)
})

test('print confirmation uses the printed document even if selection changes', async () => {
  const match = component.match(/const doPrint = \(\) => \{[\s\S]*?\n\}\n\n<\/script>/)
  assert.ok(match)
  let opens = 0
  let timer
  let confirmed
  const c = {
    printRef: { value: { innerHTML: '<table>document</table>' } }, canPrint: { value: false },
    detailOrder: { value: { stockOutId: 1 } },
    window: { open() { opens++; return { document: { write() {}, close() {} }, focus() {}, print() {}, close() {} } } },
    printCSS: () => '', setTimeout(fn) { timer = fn },
    ElMessage: { warning() {}, error() {}, success() {} }, ElMessageBox: { confirm: async () => {} },
    stockOutApi: { confirmPrint: async id => { confirmed = id } }, emit() {}
  }
  runInNewContext(ts.transpile(match[0].replace(/\n\n<\/script>$/, '') + '\nglobalThis.doPrint = doPrint'), c)
  c.doPrint()
  assert.equal(opens, 0)
  c.canPrint.value = true
  c.doPrint()
  c.detailOrder.value = { stockOutId: 2 }
  await timer()
  assert.equal(opens, 1)
  assert.equal(confirmed, 1)
})
