/**
 * SSE（text/event-stream）解析。
 *
 * <p>为什么需要它：后端所有流式接口都用 Spring MVC 返回 `Flux<String>` 且声明
 * `produces = text/event-stream`，Spring 会把每个元素写成一条 SSE 事件：
 *
 * ```
 * data:庙\n
 * \n
 * data:里\n
 * \n
 * ```
 *
 * 早期前端直接把响应体当纯文本拼接（`outputText += decoder.decode(chunk)`），
 * 结果把协议帧当正文显示——用户看到的是 `data:庙 data:里 ...`，或（换了 provider
 * 后帧更碎时）以为「AI 返回了空内容」。这层解析就是补上这个协议。
 *
 * <p>两端的分工（实测 Spring MVC 的行为，别再各写一套）：
 * - 后端：不自己拼 `data:` 前缀；正文里的换行由 Spring 拆成多条 `data:` 行；
 * - 前端：按空行分帧、把多条 `data:` 用 `\n` 拼回、只吃掉冒号后的一个前导空格。
 *
 * <p>同时兼容两种形态，避免又一次「静默空响应」：
 * - 标准 SSE 帧（`data:` 前缀、空行分帧、支持多行 data 与 event 名）；
 * - 服务端若改回纯文本直出（无 `data:` 前缀），整段按原样当正文。
 */

/** 一帧已解析的 SSE 事件。 */
export interface SseFrame {
  /** 事件名，缺省 `message` */
  event: string
  /** data 字段内容（多行以 \n 连接） */
  data: string
}

/** 只需要 getReader()，便于用假流做单测，也不必依赖具体运行时实现。 */
export interface ByteStreamLike {
  getReader(): {
    read(): Promise<{ done: boolean; value?: Uint8Array }>
    releaseLock?(): void
  }
}

const CRLF = /\r\n|\r/g

/**
 * 逐帧解析字节流。调用方用 `for await` 消费即可，读到流结束才返回。
 */
export async function* readSseFrames(stream: ByteStreamLike): AsyncGenerator<SseFrame> {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      if (!value) continue
      // stream: true 让多字节字符（中文 3 字节）跨 chunk 时不被打断
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(CRLF, '\n')

      let idx: number
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const block = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const frame = parseBlock(block)
        if (frame) yield frame
      }
    }

    // 流末尾没有空行收尾时，剩余内容同样是一帧（合法 SSE 允许省略末尾空行）
    buffer += decoder.decode()
    buffer = buffer.replace(CRLF, '\n')
    if (buffer.trim()) {
      const frame = parseBlock(buffer)
      if (frame) yield frame
    }
  } finally {
    reader.releaseLock?.()
  }
}

/** 解析单个事件块（已用空行切开）。不是有效载荷时返回 null。 */
function parseBlock(block: string): SseFrame | null {
  if (!block.trim()) return null

  const dataLines: string[] = []
  let event = 'message'
  let sawField = false

  for (const rawLine of block.split('\n')) {
    // 注意：不能去掉行尾空白——英文正文里 token 之间的空格是正文的一部分
    const line = rawLine
    if (!line) continue
    // 注释行（心跳）：": keep-alive"
    if (line.startsWith(':')) continue

    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    // 按 SSE 规范，冒号后**只**去掉一个前导空格（后端会为真正的行首空格多补一个）
    if (value.startsWith(' ')) value = value.slice(1)

    if (field === 'event') {
      event = value
      sawField = true
    } else if (field === 'data') {
      dataLines.push(value)
      sawField = true
    } else if (field === 'id' || field === 'retry') {
      sawField = true
    } else {
      // 不是合法字段：说明服务端直出的是纯文本，整块当正文
      return { event: 'message', data: block }
    }
  }

  if (!sawField) return null
  return { event, data: dataLines.join('\n') }
}

/**
 * 判断流式正文是否是「后端转达的上游失败原因」。
 *
 * <p>后端约定（{@code AiProvider#stream}）：上游报错时不中断连接，而是把一句可读
 * 说明当作普通文本帧推下来，例如「【AI 服务未授权】请检查后端配置的 DEEPSEEK_API_KEY
 * 是否有效。」。这类文本绝不能写进正文/稿件，调用方应改成弹错误提示。
 *
 * <p>判据取「短 + 以【】开头并很快闭合」，避免把正常段落里的方括号误判成错误。
 */
export function isUpstreamErrorNotice(text: string): boolean {
  const t = (text || '').trim()
  if (!t.startsWith('【') || t.length > 240) return false
  const close = t.indexOf('】')
  return close > 1 && close <= 24
}

/**
 * 边读边回调正文。会跳过 SSE 结束标记 `[DONE]`；
 * 若服务端发来 `event: error`，抛出异常交给调用方的 catch 统一提示。
 */
export async function streamSseText(
  stream: ByteStreamLike,
  onChunk: (text: string) => void,
): Promise<void> {
  if (!stream || typeof stream.getReader !== 'function') {
    // 走到这里说明响应体不是流（例如浏览器不支持 ReadableStream，或适配器降级成 XHR），
    // 明确报错，别让用户对着一个空面板猜
    throw new Error('当前浏览器不支持流式响应，请更换浏览器或升级版本')
  }
  for await (const frame of readSseFrames(stream)) {
    if (frame.event === 'error') throw new Error(frame.data || 'AI 调用失败')
    if (frame.data === '[DONE]') continue
    if (frame.data) onChunk(frame.data)
  }
}
