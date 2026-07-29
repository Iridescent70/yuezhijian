import { describe, expect, it } from 'vitest'
import { formatMoney } from './formatMoney'

describe('formatMoney', () => {
  it('formats numeric values as CNY', () => {
    expect(formatMoney(128.5)).toContain('128.50')
  })

  it('uses zero for invalid values', () => {
    expect(formatMoney('invalid')).toContain('0.00')
  })
})
