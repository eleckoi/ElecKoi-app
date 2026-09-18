import assert from 'node:assert/strict'
import test from 'node:test'

import { apply } from '../../../main/assets/dsh-plugins/context-pressure/context-pressure.mjs'

test('forwards native DSH pressure and breakdown as one current snapshot', async () => {
  let onChanged
  const requests = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (_url, options) => {
    requests.push(JSON.parse(options.body))
    return { ok: true, status: 200, text: async () => '{"accepted":true}' }
  }
  try {
    apply({
      inject(_dependencies, setup) {
        setup({
          sessionProjections: {
            onChanged(listener) { onChanged = listener },
          },
        })
      },
    }, {
      baseUrl: `http://127.0.0.1:12345/${'a'.repeat(32)}/host-tools`,
    })

    onChanged(
      { id: 'session' },
      'contextPressure',
      {
        pressureTokens: 16_557,
        contextWindow: 1_000_000,
        surfaceTokens: 5_948,
        sampledSurfaceTokens: 5_857,
      },
      12,
    )
    await waitFor(() => requests.length === 1)
    onChanged(
      { id: 'session' },
      'contextBreakdown',
      {
        breakdown: { systemTokens: 21, toolsTokens: 1_427, messageTokens: 5_948 },
      },
      12,
    )
    await waitFor(() => requests.length === 2)

    assert.equal(requests[0].value.contextWindow, 1_000_000)
    assert.equal(requests[0].value.projectedTokens, 16_648)
    assert.deepEqual(requests[1].value, {
      pressureTokens: 16_557,
      projectedTokens: 16_648,
      contextWindow: 1_000_000,
      systemTokens: 21,
      toolsTokens: 1_427,
      messageTokens: 5_948,
    })
    assert.ok(requests[1].seq > requests[0].seq)
  } finally {
    globalThis.fetch = originalFetch
  }
})

async function waitFor(predicate) {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    if (predicate()) return
    await new Promise(resolve => setTimeout(resolve, 0))
  }
  throw new Error('timed out waiting for context-pressure delivery')
}
