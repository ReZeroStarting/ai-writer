export interface SentenceInfo {
  text: string
  length: number
}

export interface WritingReport {
  sentenceCount: number
  avgSentenceLen: number
  longSentences: SentenceInfo[]       // >30 chars
  adverbDensity: number               // per 1000 chars
  topAdverbs: { word: string; count: number }[]
  topWords: { word: string; count: number }[]
  avgParagraphLen: number
  longestParagraph: number
  dialogueRatio: number               // 0–1
  punctuationProfile: { mark: string; count: number }[]
  cliches: { phrase: string; count: number }[]
  readabilityScore: number            // 0–100
  suggestions: string[]
}

const SENTENCE_RE = /[^。！？…~\.\!\?\n]+[。！？…~\.\!\?]|\S+/g
const DIALOGUE_RE = /「[^」]*」|"[^"]*"|"[^"]*"|'[^']*'|'[^']*'/g
const PUNCTUATION_RE = /[，。！？…~、：；""「」『』（）\.\,\!\?\:\;\(\)\[\]]/g

const CLICHES = [
  '一股', '突然', '只见', '忽然', '猛然', '顿时', '当下',
  '不可思议', '难以置信', '前所未有', '惊天动地',
  '说时迟那时快', '不知不觉', '悄然', '蓦然',
  '下定决心', '咬紧牙关', '攥紧拳头', '深吸一口气',
  '不由', '不由得', '居然', '竟然',
]

const ADVERBS = [
  '地', '得', '很', '非常', '特别', '十分', '极其', '格外',
  '颇为', '甚为', '太', '极', '挺', '相当', '尤其',
  '略微', '稍微', '几乎', '简直', '差不多',
]

const STOP_WORDS = new Set([
  '的', '了', '在', '是', '我', '有', '和', '就', '不', '人', '都', '一',
  '一个', '上', '也', '很', '到', '说', '要', '去', '你', '会', '着',
  '没有', '看', '好', '自己', '这', '他', '她', '它', '们', '那', '些',
  '什么', '怎么', '哪', '吗', '吧', '呢', '啊', '哦', '嗯', '哈',
  '被', '把', '从', '以', '对', '与', '但', '而', '或', '所', '如',
  '可以', '这个', '那个', '这样', '那样', '因为', '所以', '如果',
  '只是', '还是', '已经', '然后', '之后', '之前', '时候', '知道',
  '来', '中', '大', '为', '上', '个', '多', '得', '出', '还', '能',
  '再', '又', '让', '向', '等', '其', '将', '两', '里', '后', '前',
  '想', '做', '用', '过', '给', '最', '更', '只', '可', '没',
  '么', '之', '事', '点', '此', '身', '道', '头', '手', '开',
  '间', '眼', '下', '面', '声', '边', '次', '些', '样', '正',
  '便', '方', '才', '之', '已', '于',
  // common transition/prose words
  '只见', '突然', '忽然', '顿时', '当下', '不由', '不由得', '一股',
  '还是', '只是', '已经', '可以', '因为', '所以', '虽然', '但是',
  '不过', '而且', '然后', '之后', '一直', '这时', '这次',
])

function tokenize(text: string): string[] {
  // Simple 2-char sliding window + single chars as fallback
  const cleaned = text.replace(/[，。！？…~、：；""「」『』（）\.\,\!\?\:\;\(\)\[\]\s\n]/g, ' ')
  const tokens: string[] = []
  let i = 0
  while (i < cleaned.length) {
    if (cleaned[i] === ' ') { i++; continue }
    // Try 2-char token
    if (i + 1 < cleaned.length && cleaned[i + 1] !== ' ') {
      tokens.push(cleaned.slice(i, i + 2))
      i += 2
    } else {
      tokens.push(cleaned[i])
      i++
    }
  }
  return tokens
}

function freqMap(words: string[]): Map<string, number> {
  const map = new Map<string, number>()
  for (const w of words) {
    map.set(w, (map.get(w) || 0) + 1)
  }
  return map
}

function sortedTop(map: Map<string, number>, n: number, filter?: (w: string) => boolean) {
  const entries = [...map.entries()].filter(([w]) => !filter || filter(w))
  entries.sort((a, b) => b[1] - a[1])
  return entries.slice(0, n).map(([word, count]) => ({ word, count }))
}

export function analyzeWriting(text: string): WritingReport {
  const clean = text.trim()
  if (!clean) return { sentenceCount: 0, avgSentenceLen: 0, longSentences: [], adverbDensity: 0, topAdverbs: [], topWords: [], avgParagraphLen: 0, longestParagraph: 0, dialogueRatio: 0, punctuationProfile: [], cliches: [], readabilityScore: 0, suggestions: [] }
  const suggestions: string[] = []

  // --- Sentence analysis ---
  const sentences = clean.match(SENTENCE_RE) || []
  const sentenceCount = sentences.length
  const avgSentenceLen = sentenceCount > 0
    ? Math.round(sentences.reduce((s, x) => s + x.length, 0) / sentenceCount)
    : 0
  const longSentences = sentences
    .filter(s => s.length > 30)
    .map(s => ({ text: s.length > 60 ? s.slice(0, 60) + '…' : s, length: s.length }))

  // --- Adverb density ---
  const advCounts = freqMap(ADVERBS.map(a => {
    const c = (clean.match(new RegExp(a, 'g')) || []).length
    return c > 0 ? Array(c).fill(a) : []
  }).flat())
  const totalAdverbs = [...advCounts.values()].reduce((s, c) => s + c, 0)
  const adverbDensity = clean.length > 0 ? Math.round((totalAdverbs / clean.length) * 10000) / 10 : 0
  const topAdverbs = sortedTop(advCounts, 5)

  // --- Word frequency ---
  const tokens = tokenize(clean)
  const wordFreq = freqMap(tokens)
  const topWords = sortedTop(wordFreq, 15, w => w.length > 1 && !STOP_WORDS.has(w))

  // --- Paragraph analysis ---
  const paragraphs = clean.split(/\n{1,}/).filter(p => p.trim())
  const avgParagraphLen = paragraphs.length > 0
    ? Math.round(paragraphs.reduce((s, p) => s + p.length, 0) / paragraphs.length)
    : 0
  const longestParagraph = paragraphs.length > 0
    ? Math.max(...paragraphs.map(p => p.length))
    : 0

  // --- Dialogue ratio ---
  const dialogueMatches = clean.match(DIALOGUE_RE) || []
  const dialogueChars = dialogueMatches.reduce((s, m) => s + m.length, 0)
  const dialogueRatio = clean.length > 0 ? Math.round((dialogueChars / clean.length) * 1000) / 1000 : 0

  // --- Punctuation profile ---
  const punctMatches = clean.match(PUNCTUATION_RE) || []
  const punctFreq = freqMap(punctMatches)
  const punctuationProfile = sortedTop(punctFreq, 8).map(({ word, count }) => ({ mark: word, count }))

  // --- Cliché detection ---
  const clicheCounts: { phrase: string; count: number }[] = []
  for (const c of CLICHES) {
    const count = (clean.match(new RegExp(c, 'g')) || []).length
    if (count > 0) clicheCounts.push({ phrase: c, count })
  }
  clicheCounts.sort((a, b) => b.count - a.count)

  // --- Suggestions ---
  if (sentenceCount > 0) {
    const longRatio = longSentences.length / sentenceCount
    if (longRatio > 0.3) suggestions.push(`长句占比 ${Math.round(longRatio * 100)}%，建议适当拆分，提高可读性`)
    if (avgSentenceLen > 25) suggestions.push(`平均句长 ${avgSentenceLen} 字偏长，短句更有节奏感`)
  }
  if (adverbDensity > 5) suggestions.push(`副词密度 ${adverbDensity}/千字偏高，减少"地"字结构和程度副词`)
  if (dialogueRatio < 0.1 && clean.length > 500) suggestions.push('对话占比偏低，适当加入对话可增强画面感')
  if (longestParagraph > 500) suggestions.push(`最长段落 ${longestParagraph} 字，建议分段便于手机阅读`)
  if (clicheCounts.length > 3) suggestions.push(`检测到 ${clicheCounts.length} 种常见套话，考虑替换为更独特的表达`)

  if (suggestions.length === 0 && clean.length > 100) {
    suggestions.push('文本质量良好，未发现明显问题')
  }

  // --- Readability score (0–100) ---
  let score = 70
  if (avgSentenceLen > 15 && avgSentenceLen <= 25) score += 10
  else if (avgSentenceLen > 25) score -= 10
  if (longSentences.length / Math.max(sentenceCount, 1) < 0.2) score += 5
  else score -= 5
  if (adverbDensity < 3) score += 5
  else if (adverbDensity > 5) score -= 10
  if (dialogueRatio > 0.15) score += 5
  if (longestParagraph > 300) score -= 5
  if (clicheCounts.length > 2) score -= 10
  score = Math.max(0, Math.min(100, score))

  return {
    sentenceCount, avgSentenceLen, longSentences,
    adverbDensity, topAdverbs,
    topWords,
    avgParagraphLen, longestParagraph,
    dialogueRatio,
    punctuationProfile,
    cliches: clicheCounts,
    readabilityScore: score,
    suggestions,
  }
}
