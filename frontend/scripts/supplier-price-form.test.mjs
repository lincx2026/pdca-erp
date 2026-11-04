import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

test('opening a new supplier quote clears an earlier quote id and detail fields', () => {
  const source = readFileSync(new URL('../src/views/scm/price/index.vue', import.meta.url), 'utf8')
  const body = source.match(/function formDefaults\(\) \{([\s\S]*?)\n\}/)?.[1]
  assert.ok(body, 'supplier price form reset must exist')
  const form = { priceId: 42, materialId: 1, supplierId: 2, unitPrice: 9, supplierName: 'previous' }
  runInNewContext(body.replaceAll('(form as any)', 'form'), { form })
  assert.equal(form.priceId, undefined)
  assert.equal(form.supplierName, undefined)
  assert.equal(form.materialId, undefined)
  assert.equal(form.unitPrice, undefined)
  assert.equal(form.isDefault, 0)
})
