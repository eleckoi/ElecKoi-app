/** Sends DSH's client-visible contextPressure projection to the Android presentation host. */

export const name = 'eleckoi-context-pressure-bridge'

export function apply(ctx, config = {}) {
  const baseUrl = requireLoopbackUrl(config.baseUrl)
  const pendingBySession = new Map()
  const activeSessions = new Set()
  const latestBySession = new Map()
  const sequenceBySession = new Map()

  ctx.inject(['sessionProjections'], (projectionCtx) => {
    projectionCtx.sessionProjections.onChanged((session, key, value, seq) => {
      if (key !== 'contextPressure' && key !== 'contextBreakdown') return
      const sessionId = session?.id
      if (typeof sessionId !== 'string' || !/^[A-Za-z0-9._:-]{1,160}$/.test(sessionId)) return
      if (!Number.isSafeInteger(seq) || seq < 0) return
      const current = latestBySession.get(sessionId) ?? {}
      if (key === 'contextPressure') {
        const pressure = normalizeContextPressure(value)
        if (pressure === undefined) return
        current.pressure = pressure
      } else {
        const breakdown = normalizeContextBreakdown(value)
        if (breakdown === undefined) return
        current.breakdown = breakdown
      }
      latestBySession.set(sessionId, current)
      if (!current.pressure) return
      const nextSequence = Math.max((sequenceBySession.get(sessionId) ?? -1) + 1, seq)
      sequenceBySession.set(sessionId, nextSequence)
      pendingBySession.set(sessionId, {
        sessionId,
        seq: nextSequence,
        value: { ...current.pressure, ...current.breakdown },
      })
      if (!activeSessions.has(sessionId)) void drain(sessionId)
    })
  })

  async function drain(sessionId) {
    activeSessions.add(sessionId)
    try {
      while (pendingBySession.has(sessionId)) {
        const payload = pendingBySession.get(sessionId)
        pendingBySession.delete(sessionId)
        try {
          await postJson(`${baseUrl}/context-pressure`, payload)
        } catch {
          // The screen may release its route between a native projection event and delivery.
          // This bridge is observational; a later native change carries the complete fresh value.
        }
      }
    } finally {
      activeSessions.delete(sessionId)
      if (pendingBySession.has(sessionId)) void drain(sessionId)
    }
  }
}

function normalizeContextPressure(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
  const normalized = {}
  for (const key of ['pressureTokens']) {
    const candidate = value[key]
    if (candidate === undefined) continue
    if (!Number.isSafeInteger(candidate) || candidate < 0) return undefined
    normalized[key] = candidate
  }
  const projectedTokens = value.projectedTokens ?? projectedFromSurface(value)
  if (projectedTokens !== undefined) {
    if (!Number.isSafeInteger(projectedTokens) || projectedTokens < 0) return undefined
    normalized.projectedTokens = projectedTokens
  }
  if (value.contextWindow !== undefined) {
    if (!Number.isSafeInteger(value.contextWindow) || value.contextWindow <= 0) return undefined
    normalized.contextWindow = value.contextWindow
  }
  return normalized
}

function projectedFromSurface(value) {
  const pressureTokens = value.pressureTokens
  const surfaceTokens = value.surfaceTokens
  const sampledSurfaceTokens = value.sampledSurfaceTokens
  if (
    !Number.isSafeInteger(pressureTokens) || pressureTokens < 0 ||
    !Number.isSafeInteger(surfaceTokens) || surfaceTokens < 0 ||
    !Number.isSafeInteger(sampledSurfaceTokens) || sampledSurfaceTokens < 0
  ) return undefined
  return Math.max(0, pressureTokens + surfaceTokens - sampledSurfaceTokens)
}

function normalizeContextBreakdown(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
  const source = value.breakdown && typeof value.breakdown === 'object' && !Array.isArray(value.breakdown)
    ? value.breakdown
    : value
  const normalized = {}
  for (const key of ['systemTokens', 'toolsTokens', 'messageTokens']) {
    const candidate = source[key]
    if (!Number.isSafeInteger(candidate) || candidate < 0) return undefined
    normalized[key] = candidate
  }
  return normalized
}

function requireLoopbackUrl(value) {
  if (typeof value !== 'string' || !/^http:\/\/127\.0\.0\.1:\d{1,5}\/[A-Za-z0-9_-]{24,128}\/host-tools$/.test(value)) {
    throw new Error('ElecKoi context-pressure URL is not a session-scoped loopback route')
  }
  return value
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`ElecKoi context-pressure bridge HTTP ${response.status}: ${text.slice(0, 500)}`)
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new Error('ElecKoi context-pressure bridge returned non-JSON content')
  }
}
