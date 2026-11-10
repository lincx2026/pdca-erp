import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/views/sal/order/index.vue', import.meta.url), 'utf8')
function load(name, next) {
  const start = source.indexOf(`function ${name}(`)
  const end = source.indexOf(`\n${next}`, start)
  assert.ok(start >= 0 && end > start)
  const code = source.slice(start, end).replaceAll(': SalSalesOrder', '').replaceAll('value?: number, currency?: string', 'value, currency')
  return runInNewContext(`(${code})`)
}
const itemSummary = load('itemSummary', 'function quantitySummary')
const quantitySummary = load('quantitySummary', 'function orderAmount')
const formatMoney = load('formatMoney', 'async function openPrint')

test('order summaries keep each unit separate and retain the order currency', () => {
  assert.equal(itemSummary({ lineCount: 2, firstMaterialName: '控制器', firstSpecification: 'E27M' }), '控制器 / E27M 等 2 项')
  assert.equal(itemSummary({ lineCount: 0 }), '—')
  assert.equal(quantitySummary({ quantityByUnit: [{ unit: 'PCS', quantity: 1200.5 }, { unit: 'KG', quantity: 3 }] }), '1,200.5 PCS；3 KG')
  assert.equal(quantitySummary({ quantityByUnit: [] }), '—')
  assert.equal(formatMoney(1234.5, 'USD'), 'USD 1,234.50')
  assert.equal(formatMoney(0, undefined), '未注明币种 0.00')
  assert.equal(formatMoney(null, 'CNY'), '—')
})

test('state actions retain status restrictions and cancelling reject does not call API', async () => {
  const start = source.indexOf('const STATUS_LABEL:')
  const end = source.indexOf('\nconst sOpt_soType', start)
  assert.ok(start >= 0 && end > start)
  let rejected = 0
  const context = {
    hasPermOrThrow: () => true,
    ElMessageBox: { prompt: () => Promise.reject('cancel') },
    salOrderApi: { reject: async () => { rejected++ } },
    ElMessage: { error() {}, success() {} },
    loadList: async () => {},
    Number
  }
  const script = ts.transpile(`(function(){${source.slice(start, end)}; return stateActionsFor})()`)
  const actions = runInNewContext(script, context)({ soId: 7, status: 1 })
  assert.equal(actions.find(a => a.key === 'audit').disabled, false)
  assert.equal(actions.find(a => a.key === 'stockOut').disabled, true)
  await actions.find(a => a.key === 'reject').onClick({ soId: 7, soNo: 'SO7' })
  assert.equal(rejected, 0)
})
