import { describe, it, expect } from 'vitest'
import { readSseFrames, streamSseText, isUpstreamErrorNotice, type ByteStreamLike, type SseFrame } from '@/utils/sse'

/** 把若干字节块伪装成 ReadableStream，用来稳定复现「跨 chunk 截断」这类边界。 */
function fakeStream(chunks: Uint8Array[]): ByteStreamLike {
  let i = 0
  return {
    getReader: () => ({
      read: async () =>
        i < chunks.length ? { done: false, value: chunks[i++] } : { done: true },
    }),
  }
}

const enc = new TextEncoder()

function chunksOf(text: string, size: number): Uint8Array[] {
  const bytes = enc.encode(text)
  const out: Uint8Array[] = []
  for (let i = 0; i < bytes.length; i += size) out.push(bytes.slice(i, i + size))
  return out
}

async function collect(stream: ByteStreamLike): Promise<SseFrame[]> {
  const frames: SseFrame[] = []
  for await (const f of readSseFrames(stream)) frames.push(f)
  return frames
}

async function textOf(raw: string, chunkSize = 7): Promise<string> {
  let out = ''
  await streamSseText(fakeStream(chunksOf(raw, chunkSize)), (t) => { out += t })
  return out
}

describe('readSseFrames', () => {
  it('parses standard frames emitted by Spring MVC Flux<String>', async () => {
    const raw = 'data:庙\n\ndata:里\n\ndata:住着\n\ndata:一个\n\ndata:老和尚\n\n'
    const frames = await collect(fakeStream(chunksOf(raw, 5)))
    expect(frames.map(f => f.data)).toEqual(['庙', '里', '住着', '一个', '老和尚'])
    expect(frames.every(f => f.event === 'message')).toBe(true)
  })

  it('does not corrupt multi-byte characters split across chunks', async () => {
    // 每个中文字符 3 字节，chunkSize=1 必然把字符切在中间
    const raw = 'data:从前有座山\n\ndata:山里有座庙\n\n'
    expect(await textOf(raw, 1)).toBe('从前有座山山里有座庙')
  })

  it('handles CRLF line endings', async () => {
    const raw = 'data:甲\r\n\r\ndata:乙\r\n\r\n'
    expect(await textOf(raw)).toBe('甲乙')
  })

  it('joins multi-line data fields with a newline', async () => {
    const raw = 'data:第一行\ndata:第二行\n\n'
    expect(await textOf(raw)).toBe('第一行\n第二行')
  })

  it('ignores comment (heartbeat) lines and unknown fields', async () => {
    const raw = ': keep-alive\n\ndata:正文\n\nid: 7\ndata:尾巴\n\n'
    expect(await textOf(raw)).toBe('正文尾巴')
  })

  it('keeps a trailing frame that has no closing blank line', async () => {
    const raw = 'data:开头\n\ndata:结尾'
    expect(await textOf(raw)).toBe('开头结尾')
  })

  it('falls back to raw text when the server does not use SSE framing', async () => {
    // 若后端某天改回纯文本直出，不能再出现「静默空响应」
    const raw = '这是一段没有任何 data: 前缀的正文。'
    expect(await textOf(raw)).toBe(raw)
  })

  it('returns nothing for an empty stream', async () => {
    expect(await collect(fakeStream([]))).toEqual([])
  })

  it('keeps meaningful whitespace inside data (English word spacing)', async () => {
    // 后端按规范把「正文以空格开头」编码成两个空格；解析时只吃掉一个
    const raw = 'data:Hello \n\ndata:  world\n\n'
    expect(await textOf(raw)).toBe('Hello  world')
  })

  it('could reconstruct an English sentence from token frames', async () => {
    const raw = 'data:Hello\n\ndata:  there\n\ndata:,\n\ndata:  how\n\ndata:  are\n\ndata:  you\n\ndata:?\n\n'
    expect(await textOf(raw)).toBe('Hello there, how are you?')
  })

})

describe('streamSseText', () => {
  it('skips the [DONE] sentinel', async () => {
    const raw = 'data:一\n\ndata:二\n\ndata:[DONE]\n\n'
    expect(await textOf(raw)).toBe('一二')
  })

  it('throws when an error event arrives', async () => {
    const raw = 'event: error\ndata:【AI 服务未授权】请检查 DEEPSEEK_API_KEY\n\n'
    await expect(textOf(raw)).rejects.toThrow('【AI 服务未授权】')
  })

  it('passes upstream error text through when it arrives as a plain message frame', async () => {
    // 后端当前约定：上游失败时把可读原因当普通消息帧推给前端
    const raw = 'data:【AI 服务未授权】请检查后端配置的 DEEPSEEK_API_KEY 是否有效。\n\n'
    expect(await textOf(raw)).toBe('【AI 服务未授权】请检查后端配置的 DEEPSEEK_API_KEY 是否有效。')
  })
})

describe('isUpstreamErrorNotice', () => {
  it('recognizes the backend error notices', () => {
    expect(isUpstreamErrorNotice('【AI 服务未授权】请检查后端配置的 DEEPSEEK_API_KEY 是否有效。')).toBe(true)
    expect(isUpstreamErrorNotice('  【AI 服务限流】请求过于频繁或额度不足，请稍后再试。  ')).toBe(true)
    expect(isUpstreamErrorNotice('【网络异常】无法连接 AI 服务，请检查网络或代理设置。')).toBe(true)
  })

  it('does not mistake normal prose for an error', () => {
    expect(isUpstreamErrorNotice('庙里住着一个老和尚。')).toBe(false)
    expect(isUpstreamErrorNotice('')).toBe(false)
    // 正文里出现方括号但不在开头
    expect(isUpstreamErrorNotice('他说：【别走】然后关上了门。')).toBe(false)
    // 过长的文本（正常章节）即使以【开头也不算错误通知
    expect(isUpstreamErrorNotice('【长章节】' + '文'.repeat(300))).toBe(false)
  })
})
