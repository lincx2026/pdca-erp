export const printColumns = [
  { key: 'index', label: '序号', width: 5 },
  { key: 'code', label: '物料编码', width: 18 },
  { key: 'name', label: '物料名称', width: 31 },
  { key: 'spec', label: '规格', width: 14 },
  { key: 'unit', label: '单位', width: 8 },
  { key: 'quantity', label: '数量', width: 10 },
  { key: 'remark', label: '备注', width: 14 }
] as const

export type ColumnKey = typeof printColumns[number]['key']
export type ColumnWidths = Record<ColumnKey, number>

export function defaultWidths(): ColumnWidths {
  return Object.fromEntries(printColumns.map(column => [column.key, column.width])) as ColumnWidths
}

export function readWidths(value: unknown): ColumnWidths {
  const widths = defaultWidths()
  if (!value || typeof value !== 'object') return widths
  for (const column of printColumns) {
    const width = (value as Record<string, unknown>)[column.key]
    if (typeof width === 'number' && Number.isFinite(width) && width >= 1 && width <= 100) widths[column.key] = width
  }
  return widths
}

export function visibleWidths(widths: ColumnWidths, showSpec: boolean, showRemark: boolean) {
  const columns = printColumns.filter(column => (column.key !== 'spec' || showSpec) && (column.key !== 'remark' || showRemark))
  const validWidths = readWidths(widths)
  const total = columns.reduce((sum, column) => sum + validWidths[column.key], 0)
  return columns.map(column => ({ ...column, percentage: validWidths[column.key] / total * 100 }))
}
