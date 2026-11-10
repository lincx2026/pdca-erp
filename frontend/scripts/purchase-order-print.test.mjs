import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
const component = readFileSync(new URL('../src/views/print/PurchaseOrderPrint.vue', import.meta.url), 'utf8').replace(/\r\n/g, '\n')
function loader(getData) {
  const match = component.match(/let requestSequence = 0\nconst loadData = async \(\) => \{[\s\S]*?\n\}\n\nwatch/)
  assert.ok(match)
  const context = {
    props: { order: { poId: 1 } }, loading: { value: false }, loaded: { value: false }, loadError: { value: '' },
    detailOrder: { value: null }, detailLines: { value: [] }, company: { value: null },
    companyApi: { purchaseOrderPrintProfile: async () => ({ companyName: 'test' }) },
    scmPoApi: { printData: getData }, Promise, String, Array, Error
  }
  runInNewContext(ts.transpile(match[0].replace(/\n\nwatch$/, '') + '\nglobalThis.loadData = loadData'), context)
  return context
}
test('purchase print refuses failed or mismatched data and recovers on retry', async () => {
  let response = () => { throw new Error('502') }
  const c = loader(async id => response(id))
  c.detailLines.value = [{ materialCode: 'stale' }]
  await c.loadData()
  assert.equal(c.loaded.value, false); assert.equal(c.detailLines.value.length, 0)
  response = () => ({ order: { poId: 2 }, lines: [{}] })
  await c.loadData(); assert.equal(c.loaded.value, false)
  response = () => ({ order: { poId: 1 }, lines: [{ materialCode: '32017' }] })
  await c.loadData(); assert.equal(c.loaded.value, true); assert.equal(c.detailLines.value[0].materialCode, '32017')
})
test('purchase print requires actual lines and confirms the order that was printed', async () => {
  assert.match(component, /:disabled="!canPrint"/)
  assert.match(component, /detailLines\.value\.length > 0/)
  const match = component.match(/const doPrint = \(\) => \{[\s\S]*?\n\}\n\n<\/script>/)
  assert.ok(match)
  let timer, confirmed, opens = 0
  const c = { printRef: { value: { innerHTML: '<table></table>' } }, canPrint: { value: false }, detailOrder: { value: { poId: 1 } },
    window: { open() { opens++; return { document: { write() {}, close() {} }, focus() {}, print() {}, close() {} } } },
    printCSS: () => '', setTimeout(fn) { timer = fn }, ElMessage: { warning() {}, error() {}, success() {} },
    ElMessageBox: { confirm: async () => {} }, scmPoApi: { confirmPrint: async id => { confirmed = id } }, emit() {} }
  runInNewContext(ts.transpile(match[0].replace(/\n\n<\/script>$/, '') + '\nglobalThis.doPrint = doPrint'), c)
  c.doPrint(); assert.equal(opens, 0)
  c.canPrint.value = true; c.doPrint(); c.detailOrder.value = { poId: 2 }; await timer()
  assert.equal(confirmed, 1)
})
