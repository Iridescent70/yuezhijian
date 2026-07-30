const formatter = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatMoney(value: number | string): string {
  const amount = typeof value === 'number' ? value : Number(value)
  return formatter.format(Number.isFinite(amount) ? amount : 0)
}
