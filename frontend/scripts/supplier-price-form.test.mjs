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
  assert.equal(form.validTo, null)
})

test('existing supplier quote dates are preserved and new quotes default to no expiry', () => {
  const source = readFileSync(new URL('../src/views/scm/price/index.vue', import.meta.url), 'utf8')
  const defaults = source.match(/function formDefaults\(\) \{([\s\S]*?)\n\}/)?.[1]
  const open = source.match(/function openDialog\(row\?: any\) \{([\s\S]*?)\n\}/)?.[1]
  assert.ok(defaults && open)
  const form = {}
  const ctx = { form, readonly: { value: false }, materialOptions: { value: [] }, supplierOptions: { value: [] }, query: {}, dialog: { value: false }, ensureMaterialOption() {}, ensureSupplierOption() {}, Object }
  ctx.formDefaults = () => runInNewContext(defaults.replaceAll('(form as any)', 'form'), ctx)
  const openDialog = row => runInNewContext(open, { ...ctx, row })
  openDialog({ priceId: 1, materialId: 2, supplierId: 3, validTo: '2027-06-30' })
  assert.equal(form.validTo, '2027-06-30')
  openDialog()
  assert.equal(form.validTo, null)
  assert.match(source, /留空表示长期有效/)
})
