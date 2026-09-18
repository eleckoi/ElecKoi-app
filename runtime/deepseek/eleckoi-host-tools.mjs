/**
 * DeepSeek Harness adapter for tools whose authorization and implementation stay in Android.
 * The Cordis plugin owns only schema registration and one authenticated loopback round trip.
 */

export const name = 'eleckoi-host-tools'
export const inject = ['tools']

const MAX_CATALOG_TOOLS = 128
const MAX_NAME_CHARS = 128
const MAX_DESCRIPTION_CHARS = 8 * 1024

export function apply(ctx, config = {}) {
  const baseUrl = requireLoopbackUrl(config.baseUrl)
  const tools = config.tools
  if (!Array.isArray(tools) || tools.length > MAX_CATALOG_TOOLS) {
    throw new Error('ElecKoi host tool catalog is invalid')
  }

  const seen = new Set()
  for (const candidate of tools) {
    const definition = requireToolDefinition(candidate)
    if (seen.has(definition.name)) {
      throw new Error(`ElecKoi host tool is duplicated: ${definition.name}`)
    }
    seen.add(definition.name)
    ctx.tools.register({
      name: definition.name,
      description: definition.description,
      parameters: definition.parameters,
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            content: { type: 'string' },
          },
          required: ['content'],
        },
        render(_args, value) {
          return [{ type: 'text', text: value.content }]
        },
      },
      async execute(args, exec) {
        const result = await requestJson(
          `${baseUrl}/call`,
          {
            sessionId: requireSessionId(exec),
            name: definition.name,
            arguments: args,
          },
          exec.signal,
        )
        if (!result || typeof result.content !== 'string' || typeof result.success !== 'boolean') {
          throw new Error('ElecKoi host tool returned an invalid result')
        }
        if (!result.success) throw new Error(result.content || 'ElecKoi host tool failed')
        return { content: result.content }
      },
      presentCall(args) {
        return { card: 'generic', title: definition.name, kind: 'other', rawInput: args }
      },
    })
  }
}

function requireSessionId(exec) {
  const sessionId = exec?.agent?.session?.header?.id
  if (typeof sessionId !== 'string' || !/^[A-Za-z0-9._:-]{1,160}$/.test(sessionId)) {
    throw new Error('ElecKoi host tool call has no valid DSH session identity')
  }
  return sessionId
}

function requireLoopbackUrl(value) {
  if (typeof value !== 'string' || !/^http:\/\/127\.0\.0\.1:\d{1,5}\/[A-Za-z0-9_-]{24,128}\/host-tools$/.test(value)) {
    throw new Error('ElecKoi host tool URL is not a session-scoped loopback route')
  }
  return value
}

function requireToolDefinition(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('ElecKoi host tool definition is not an object')
  }
  const { name: toolName, description, parameters } = value
  if (typeof toolName !== 'string'
    || !/^[A-Za-z_][A-Za-z0-9_.:-]*$/.test(toolName)
    || toolName.length > MAX_NAME_CHARS) {
    throw new Error('ElecKoi host tool name is invalid')
  }
  if (typeof description !== 'string' || description.length > MAX_DESCRIPTION_CHARS) {
    throw new Error(`ElecKoi host tool description is invalid: ${toolName}`)
  }
  if (!parameters || typeof parameters !== 'object' || Array.isArray(parameters)
    || parameters.type !== 'object') {
    throw new Error(`ElecKoi host tool parameters are invalid: ${toolName}`)
  }
  return { name: toolName, description, parameters }
}

async function requestJson(url, body, signal) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
    ...(signal === undefined ? {} : { signal }),
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`ElecKoi host tool bridge HTTP ${response.status}: ${text.slice(0, 500)}`)
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new Error('ElecKoi host tool bridge returned non-JSON content')
  }
}
