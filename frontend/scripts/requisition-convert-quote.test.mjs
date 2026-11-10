import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/views/scm/requisition/index.vue', import.meta.url), 'utf8')
const match = source.match(/async function submitConvert\(\) \{[\s\S]*?\n\}\n\nonMounted/)
assert.ok(match)
const script = ts.transpile(match[0].replace(/\n\nonMounted$/, '') + '\nglobalThis.submitConvert = submitConvert')

function harness(estimate) {
  const calls = { estimates: [], conversions: [], dialogs: [], routes: [] }
  const context = {
    canSubmitConvert: { value: true }, convertSubmitting: { value: false }, convertDlg: { value: true },
    selection: { value: [{ status: 2, requisitionId: 4 }] },
    hd: { paymentTerm: '月结30天', currency: 'CNY', taxRate: 13, deliveryDateOffsetDays: 20 },
    suppliers: { value: [{ supplierId: 2, supplierName: 'supplier' }] },
    scmReqApi: {
      lines: async () => ({ rows: [{ status: 2, lineId: 10, materialId: 17, materialCode: '32017', qty: 100, convertedQty: 0 }], total: 1 }),
      estimate: async (...args) => { calls.estimates.push(args); return estimate },
      convertPurchaseOrder: async body => { calls.conversions.push(body); return { poIds: [1] } }
    },
    ElMessage: { warning() {}, error() {}, success() {} },
    ElMessageBox: { confirm: async (...args) => { calls.dialogs.push(args) }, alert: async (...args) => { calls.dialogs.push(args) } },
    hasPermission: () => true, router: { push: async route => calls.routes.push(route) },
    proxy: null, loadList: async () => {}, Number, Object, Promise, JSON, setTimeout
  }
  runInNewContext(script, context)
  return { context, calls }
}

test('a missing quote explains why and links to the affected material', async () => {
  const { context, calls } = harness({ estimateSource: 'REFERENCE', errorMsg: '供应商报价尚未审核' })
  await context.submitConvert()
  assert.deepEqual(calls.estimates[0], [17, 100, undefined, 'CNY'])
  assert.match(calls.dialogs[0][0], /32017：供应商报价尚未审核/)
  assert.equal(calls.routes[0].query.materialId, '17')
  assert.equal(calls.conversions.length, 0)
})

test('conversion sends the same currency, quote and price shown in preview', async () => {
  const { context, calls } = harness({ estimateSource: 'QUOTE', supplierId: 2, supplierPriceId: 8, estimateCurrency: 'CNY', estimatePrice: 2.2, estimateAmount: 220 })
  await context.submitConvert()
  assert.deepEqual(calls.estimates[0], [17, 100, undefined, 'CNY'])
  assert.match(calls.dialogs[0][0], /32017.*2\.2000/)
  assert.equal(calls.conversions[0].currency, 'CNY')
  assert.equal(calls.conversions[0].expectedPrices['10'].priceId, 8)
  assert.equal(calls.conversions[0].expectedPrices['10'].unitPrice, 2.2)
  assert.equal(calls.conversions[0].expectedPrices['10'].supplierId, 2)
  assert.equal(calls.conversions[0].expectedPrices['10'].currency, 'CNY')
  assert.equal(calls.conversions[0].expectedPrices['10'].qty, 100)
})

test('material quote tab preserves legacy records and links to formal purchasing quotes', () => {
  const material = readFileSync(new URL('../src/views/pdm/material/index.vue', import.meta.url), 'utf8')
  assert.match(material, /scmPriceApi\.list\(\{ pageNum: 1, pageSize: 20, materialId: Number\(id\) \}\)/)
  assert.match(material, /历史物料报价（只读参考，不用于采购转单）/)
  assert.doesNotMatch(material, /supplierApi\.replaceAll/)
  assert.match(material, /path: '\/scm\/price'/)
})
