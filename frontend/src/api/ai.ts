import client from './client'
import type { AiRequest } from '@/types'

export const aiApi = {
  generateOutline: (data: AiRequest) =>
    client.post('/ai/generate-outline', data, { responseType: 'stream', adapter: 'fetch' }),
  continue: (data: AiRequest) =>
    client.post('/ai/continue', data, { responseType: 'stream', adapter: 'fetch' }),
  polish: (data: AiRequest) =>
    client.post('/ai/polish', data, { responseType: 'stream', adapter: 'fetch' }),
  expand: (data: AiRequest) =>
    client.post('/ai/expand', data, { responseType: 'stream', adapter: 'fetch' }),
  shorten: (data: AiRequest) =>
    client.post('/ai/shorten', data, { responseType: 'stream', adapter: 'fetch' }),
}
