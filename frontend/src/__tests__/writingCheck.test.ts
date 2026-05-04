import { describe, it, expect } from 'vitest'
import { analyzeWriting } from '@/utils/writingCheck'

describe('analyzeWriting', () => {
  const sample = `
天色微明，李大壮推开院门走了出去。街道上空无一人，只有几只麻雀在树梢间叽叽喳喳地叫着。
他深深地吸了一口气，看着远处连绵起伏的山峦，心里说不清是什么滋味。
"你真的要走吗？"身后传来一个轻柔的声音。
李大壮转过身，看见妻子站在门口，眼眶红红地望着他。他张了张嘴，想说什么，却最终只是点了点头。
`

  const report = analyzeWriting(sample)

  it('detects sentence count', () => {
    expect(report.sentenceCount).toBeGreaterThanOrEqual(3)
  })

  it('computes average sentence length', () => {
    expect(report.avgSentenceLen).toBeGreaterThan(0)
  })

  it('finds dialogue ratio > 0', () => {
    expect(report.dialogueRatio).toBeGreaterThan(0)
  })

  it('returns a readability score 0–100', () => {
    expect(report.readabilityScore).toBeGreaterThanOrEqual(0)
    expect(report.readabilityScore).toBeLessThanOrEqual(100)
  })

  it('returns suggestions array', () => {
    expect(Array.isArray(report.suggestions)).toBe(true)
  })

  it('detects punctuation', () => {
    expect(report.punctuationProfile.length).toBeGreaterThan(0)
  })

  it('handles empty text gracefully', () => {
    const r = analyzeWriting('')
    expect(r.sentenceCount).toBe(0)
    expect(r.readabilityScore).toBe(0)
  })

  it('detects cliches', () => {
    const text = '他突然感到一股前所未有的力量，不可思议地瞪大了眼睛。只见一道光芒从天空中划过，他顿时呆住了。'
    const r = analyzeWriting(text)
    expect(r.cliches.length).toBeGreaterThan(0)
  })
})
