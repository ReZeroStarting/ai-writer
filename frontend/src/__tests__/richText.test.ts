import { describe, it, expect } from 'vitest'
import { plainToHtml, htmlToPlain, ensureHtml, looksLikeHtml } from '@/utils/richText'

describe('richText', () => {
  it('纯文本转成段落，空行分段、段内换行变 br', () => {
    expect(plainToHtml('第一段。\n\n第二段，第一行。\n第二行。')).toBe(
      '<p>第一段。</p><p>第二段，第一行。<br>第二行。</p>',
    )
  })

  it('转义模型输出里的 HTML', () => {
    const html = plainToHtml('他写下 <script>alert(1)</script>')
    expect(html).toContain('&lt;script&gt;')
    expect(html).not.toContain('<script>')
  })

  it('HTML 转回纯文本时保留分段', () => {
    expect(htmlToPlain('<p>甲</p><p>乙<br>丙</p>')).toBe('甲\n\n乙\n丙')
  })

  it('往返无损', () => {
    const plain = '雾还没散。\n\n他站在渡口。\n\n船来了。'
    expect(htmlToPlain(plainToHtml(plain))).toBe(plain)
  })

  it('ensureHtml 不会二次包装已有 HTML', () => {
    expect(ensureHtml('<h2>标题</h2><p>正文</p>')).toBe('<h2>标题</h2><p>正文</p>')
    expect(ensureHtml('纯文本')).toBe('<p>纯文本</p>')
    expect(ensureHtml('')).toBe('')
  })

  it('looksLikeHtml 只认块级标签', () => {
    expect(looksLikeHtml('<p>x</p>')).toBe(true)
    expect(looksLikeHtml('普通一句话')).toBe(false)
  })
})
