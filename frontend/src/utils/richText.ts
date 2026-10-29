/**
 * 文档正文的纯文本 / HTML 互转。
 *
 * <p>后端入库统一存 HTML（编辑器 Tiptap 只认 HTML），但 AI 流式产出的、以及
 * 写作台画布上显示的都是纯文本。两边在边界上转换，避免「打开文档一看全是
 * 一整段、没有分段」这种问题。
 *
 * <p>规则与后端 `HtmlText` 保持一致：空行分段、段内换行转 &lt;br&gt;。
 */

const BLOCK_TAG = /<\s*(p|br|div|h[1-6]|blockquote|ul|ol|li)\b[^>]*>/i

export function looksLikeHtml(text: string | null | undefined): boolean {
  return !!text && BLOCK_TAG.test(text)
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function unescapeHtml(text: string): string {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
}

/** 纯文本 → 段落化 HTML */
export function plainToHtml(plain: string | null | undefined): string {
  if (!plain || !plain.trim()) return ''
  const normalized = plain.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  return normalized
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map((block) => {
      // 模型常以 markdown 标题开头（"## 第一章"），转成真标题，别显示成井号
      if (!block.includes('\n') && /^#{1,6}\s+/.test(block)) {
        return `<h2>${escapeHtml(block.replace(/^#{1,6}\s+/, ''))}</h2>`
      }
      return `<p>${escapeHtml(block).replace(/\n/g, '<br>')}</p>`
    })
    .join('')
}

/** 已经是 HTML 就原样返回，否则段落化 */
export function ensureHtml(content: string | null | undefined): string {
  if (!content || !content.trim()) return ''
  return looksLikeHtml(content) ? content : plainToHtml(content)
}

/** HTML → 纯文本 */
export function htmlToPlain(html: string | null | undefined): string {
  if (!html) return ''
  if (!looksLikeHtml(html)) return html
  const text = html
    .replace(/<\/p\s*>/gi, '\n\n')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(div|h[1-6]|li|blockquote)\s*>/gi, '\n')
    .replace(/<[^>]*>/g, '')
  return unescapeHtml(text)
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}
