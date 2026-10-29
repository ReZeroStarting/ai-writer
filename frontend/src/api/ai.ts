import client from './client'
import type { AiRequest } from '@/types'

/**
 * 流式请求的公共配置。
 *
 * <p>两个细节不能省：
 * - `responseType: 'stream'` + `adapter: 'fetch'`：浏览器端拿到 ReadableStream，
 *   才能边生成边渲染（XHR 适配器会等整个响应结束）；
 * - `timeout: 0`：客户端默认 30s 超时对 SSE 是**错误**的——长文续写/自动写作
 *   往往超过 30 秒，超时会把已经开始输出的流掐断，表现为「写一半停住」。
 */
const sseConfig = { responseType: 'stream' as const, adapter: 'fetch' as const, timeout: 0 }

export const aiApi = {
  generateOutline: (data: AiRequest) => client.post('/ai/generate-outline', data, sseConfig),
  continue: (data: AiRequest) => client.post('/ai/continue', data, sseConfig),
  polish: (data: AiRequest) => client.post('/ai/polish', data, sseConfig),
  expand: (data: AiRequest) => client.post('/ai/expand', data, sseConfig),
  shorten: (data: AiRequest) => client.post('/ai/shorten', data, sseConfig),
  autoWrite: (data: AiRequest) => client.post('/ai/auto-write', data, sseConfig),
  imitateWrite: (data: AiRequest) => client.post('/ai/imitate-write', data, sseConfig),
}
