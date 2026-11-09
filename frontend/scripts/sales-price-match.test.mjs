import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

test('single sales line uses the unwrapped customer quote with decimal tax rate', async () => {
  const source = readFileSync(new URL('../src/views/sal/order/index.vue', import.meta.url), 'utf8')
  const match = source.match(/async function onLineMatchPrice\(\) \{([\s\S]*?)\n\}\nasync function onLineSubmit/)
  assert.ok(match)
  const lineForm = { materialId: 42, orderQty: 2, unitPrice: 0, taxRate: 0 }
  const context = { lineForm, linesOwner: { value: { customerId: 10 } }, salPriceApi: { match: async () => ({ priceId: 1, unitPrice: 113, untaxedUnitPrice: 100, taxRate: 13, currency: 'CNY' }) }, ElMessage: { success() {}, info() {} }, recalcLineAmount() {}, Number }
  await runInNewContext(`(async () => {${match[1].replaceAll(' as any', '')}\n})()`, context)
  assert.equal(lineForm.unitPrice, 100)
  assert.equal(lineForm.taxRate, 0.13)
})
