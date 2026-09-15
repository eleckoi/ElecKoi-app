/** Projects DSH messages through Android, then delegates native wire serialization to pi-ai. */

export const name = 'eleckoi-provider-bridge'
export const inject = ['llm', 'attachments']

const WIRE_MODEL = 'eleckoi-wire'
const ROUTE_TOOL_PREFIX = 'eleckoi_internal_route_'
const WIRE_PROVIDERS = Object.freeze({
  'openai-responses': 'eleckoi-wire-responses',
  'openai-completions': 'eleckoi-wire-chat',
  'openai-completions-thinking': 'eleckoi-wire-chat-thinking',
  'anthropic-messages': 'eleckoi-wire-anthropic',
  'google-generative-ai': 'google',
})
const PROFILE_WIRE_ROUTES = Object.freeze({
  'kimi-k3': Object.freeze({
    'openai-completions': Object.freeze({ provider: 'moonshotai-cn', model: 'kimi-k3' }),
  }),
})
const WIRE_PROVIDER_IDS = new Set([
  ...Object.values(WIRE_PROVIDERS),
  ...Object.values(PROFILE_WIRE_ROUTES).flatMap(routes =>
    Object.values(routes).map(route => route.provider)),
])

export function apply(ctx, config = {}) {
  const baseUrl = requireLoopbackUrl(config.baseUrl)
  const routeModel = requireNonEmpty(config.model, 'ElecKoi bridge model')
  const routeContextWindow = positiveInteger(config.contextWindow, 262144)

  const adapter = {
    providerInfo(provider) {
      return { id: provider, name: 'ElecKoi Android provider bridge' }
    },

    providerRetryPolicy() {
      return undefined
    },

    listModels(provider) {
      return Promise.resolve([{
        provider,
        id: routeModel,
        name: routeModel,
        inputModalities: ['text', 'image'],
      }])
    },

    resolveModel(provider, model) {
      return Promise.resolve({
        provider,
        id: model,
        name: model,
        inputModalities: ['text', 'image'],
        context: { contextWindow: routeContextWindow },
      })
    },

    async prepareCall(provider, model, signal) {
      return {
        model: await this.resolveModel(provider, model, signal),
        stream: options => this.stream(options),
      }
    },

    async *stream(options) {
      if (options.sessionId === undefined) {
        throw new Error('ElecKoi provider bridge requires a DSH session id')
      }
      const prepared = await postJson(`${baseUrl}/provider/prepare`, serializableRequest(options))
      validatePrepared(prepared)
      let requestTokenPending = true
      try {
        const wireRoute = resolveWireRoute(prepared.api, prepared.wireProfile)
        const projected = await admitElecKoiDataImages(prepared.request, ctx.attachments)
        const replayReady = retargetPiAiReplaySources(projected, wireRoute.provider, wireRoute.model)
        const delegated = withRouteTool(replayReady, prepared.requestToken)
        delegated.provider = wireRoute.provider
        delegated.model = wireRoute.model
        delegated.sessionId = options.sessionId
        delegated.signal = options.signal
        if (prepared.reasoningEffort === undefined) {
          delete delegated.reasoningEffort
        } else {
          delegated.reasoningEffort = prepared.reasoningEffort
        }
        for await (const chunk of ctx.llm.stream(delegated)) {
          requestTokenPending = false
          yield chunk
        }
      } finally {
        if (requestTokenPending) {
          try {
            await postJson(`${baseUrl}/provider/cancel`, { requestToken: prepared.requestToken })
          } catch {
            // Android also expires unused one-time routes. Cancellation is only eager cleanup.
          }
        }
      }
    },
  }

  ctx.llm.registerAdapter(['eleckoi-bridge'], adapter)
}

/**
 * The bridge is an adapter boundary wrapped around pi-ai. DSH deliberately exposes replay state
 * only to the adapter instance that owns the historical provider. Agent-loop messages therefore
 * name the outer `eleckoi-bridge` route, while their replay envelope names the inner pi-ai route
 * that produced it. Restore that inner provenance immediately before delegating so DSH keeps the
 * envelope and pi-ai can replay native reasoning/tool metadata on the next step of the same turn.
 */
export function retargetPiAiReplaySources(request, targetProvider, targetModel = WIRE_MODEL) {
  return {
    ...request,
    messages: request.messages.map(message => {
      const source = message?.source
      const response = source?.replayState?.response
      if (
        message?.role !== 'assistant' ||
        source?.kind !== 'model' ||
        response?.kind !== 'pi-ai' ||
        response?.version !== 2 ||
        !WIRE_PROVIDER_IDS.has(response.provider) ||
        typeof response.model !== 'string' ||
        response.model.length === 0
      ) {
        return message
      }
      return {
        ...message,
        source: {
          ...source,
          provider: response.provider,
          model: response.model,
        },
      }
    }),
    provider: targetProvider,
    model: targetModel,
  }
}

export function resolveWireRoute(api, wireProfile) {
  if (wireProfile !== 'default') {
    const profileRoute = PROFILE_WIRE_ROUTES[wireProfile]?.[api]
    if (profileRoute === undefined) {
      throw new Error(`ElecKoi provider bridge does not support profile ${wireProfile} on ${api}`)
    }
    return profileRoute
  }
  const provider = WIRE_PROVIDERS[api]
  if (provider === undefined) {
    throw new Error(`ElecKoi provider bridge does not support protocol ${api}`)
  }
  return { provider, model: WIRE_MODEL }
}

function serializableRequest(options) {
  const request = {
    provider: options.provider,
    model: options.model,
    messages: options.messages,
    sessionId: String(options.sessionId),
  }
  for (const key of ['system', 'tools', 'temperature', 'topP', 'maxTokens', 'stop', 'purpose', 'reasoningEffort']) {
    if (options[key] !== undefined) request[key] = options[key]
  }
  return request
}

function withRouteTool(request, requestToken) {
  return {
    ...request,
    tools: [
      ...(Array.isArray(request.tools) ? request.tools : []),
      {
        name: `${ROUTE_TOOL_PREFIX}${requestToken}`,
        description: 'Internal one-time ElecKoi provider route. Never exposed to the model.',
        parameters: { type: 'object', properties: {}, additionalProperties: false },
      },
    ],
  }
}

async function admitElecKoiDataImages(request, attachments) {
  const messages = await Promise.all(request.messages.map(async message => ({
    ...message,
    content: await admitContentBlocks(message.content, attachments),
  })))
  return { ...request, messages }
}

async function admitContentBlocks(content, attachments) {
  if (!Array.isArray(content)) return content
  return Promise.all(content.map(async block => {
    if (!block || typeof block !== 'object' || Array.isArray(block)) return block
    if (block.type === 'eleckoi-data-image') {
      const image = parseDataImage(block.dataUrl)
      const attachment = await attachments.saveImage({
        data: Uint8Array.from(Buffer.from(image.base64, 'base64')),
        mediaType: image.mediaType,
      })
      return { type: 'image', attachment }
    }
    if (block.type === 'tool-result' && Array.isArray(block.content)) {
      return { ...block, content: await admitContentBlocks(block.content, attachments) }
    }
    return block
  }))
}

function parseDataImage(value) {
  if (typeof value !== 'string') throw new Error('ElecKoi history image is missing its data URL')
  const match = /^data:(image\/(?:png|jpeg|webp|gif));base64,([A-Za-z0-9+/]+={0,2})$/.exec(value)
  if (!match) throw new Error('ElecKoi history image is not a supported base64 data URL')
  return { mediaType: match[1], base64: match[2] }
}

function validatePrepared(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw invalidPrepared()
  for (const key of ['requestToken', 'api', 'model', 'wireProfile']) {
    if (typeof value[key] !== 'string' || value[key].length === 0) throw invalidPrepared()
  }
  if (!value.request || typeof value.request !== 'object' || !Array.isArray(value.request.messages)) {
    throw invalidPrepared()
  }
  if (
    value.reasoningEffort !== undefined &&
    !['off', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'].includes(value.reasoningEffort)
  ) {
    throw invalidPrepared()
  }
  resolveWireRoute(value.api, value.wireProfile)
}

function invalidPrepared() {
  return new Error('ElecKoi provider bridge received an invalid preparation response')
}

function requireLoopbackUrl(value) {
  if (typeof value !== 'string' || !/^http:\/\/127\.0\.0\.1:\d{1,5}\/[A-Za-z0-9_-]{24,128}\/host-tools$/.test(value)) {
    throw new Error('ElecKoi provider bridge URL is not a session-scoped loopback route')
  }
  return value
}

function requireNonEmpty(value, label) {
  if (typeof value !== 'string' || value.length === 0) throw new Error(`${label} is missing`)
  return value
}

function positiveInteger(value, defaultValue) {
  return Number.isSafeInteger(value) && value > 0 ? value : defaultValue
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
  const text = await response.text()
  if (!response.ok) {
    let message = text.slice(0, 1000)
    try { message = JSON.parse(text)?.error?.message ?? message } catch {}
    throw new Error(`ElecKoi provider bridge HTTP ${response.status}: ${message}`)
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new Error('ElecKoi provider bridge returned non-JSON content')
  }
}
