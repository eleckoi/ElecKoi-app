export const projectionPlugin = 'eleckoi-request-projection'

const ContextPlugin = 'eleckoi-agent-session-bridge'
const ProjectionVersion = 1
const ProjectionPrefix = `ELECKOI_REQUEST_PROJECTION_V${ProjectionVersion}\n`

export function requestInstructions(snapshot) {
  return sortedInjections(snapshot)
    .filter(entry => entry.anchor === 'instructions')
    .map(entry => String(entry.content ?? '').trim())
    .filter(Boolean)
    .join('\n\n')
}

/** Freeze every request-position definition for one generation. */
export function requestProjectionPlan(snapshot) {
  return sortedInjections(snapshot)
    .filter(entry => entry.anchor !== 'instructions')
    .map(entry => structuredClone(entry))
}

/**
 * Build the provider-facing array from real DSH messages for every model request.
 * The current tool flow grows between steps; point 5 is therefore always placed after it.
 */
export function projectRequestMessages(messages, plan = projectionPlanFromMessages(messages)) {
  if (!Array.isArray(messages)) return messages
  const visible = messages.filter(message => !isInternalProjectionMessage(message))
  if (!Array.isArray(plan) || plan.length === 0) return visible
  const system = visible.filter(message => message?.role === 'system')
  const dialogue = visible.filter(message => message?.role !== 'system')
  const latestUserIndex = dialogue.findLastIndex(isDirectUserMessage)
  const currentTurnMessages = latestUserIndex < 0 ? dialogue : dialogue.slice(latestUserIndex)
  const active = plan.filter(entry => injectionActive(entry, currentTurnMessages))
  const projectedSystem = active
    .filter(entry => entry.role === 'system')
    .map(projectionMessage)
  const grouped = anchors => active
    .filter(entry => entry.role !== 'system' && anchors.includes(entry.anchor))
    .map(projectionMessage)
  const beforeHistory = grouped(['beforeToolContext', 'toolContext', 'afterToolContext', 'beforeHistory'])
  const beforeLatestUser = grouped(['afterHistory', 'beforeLatestUserInput'])
  const afterLatestUser = grouped(['afterLatestUserInput', 'beforeToolFlow'])
  const afterToolFlow = grouped(['afterToolFlow'])
  if (latestUserIndex < 0) {
    return [
      ...system,
      ...projectedSystem,
      ...beforeHistory,
      ...dialogue,
      ...beforeLatestUser,
      ...afterLatestUser,
      ...afterToolFlow,
    ]
  }
  return [
    ...system,
    ...projectedSystem,
    ...beforeHistory,
    ...dialogue.slice(0, latestUserIndex),
    ...beforeLatestUser,
    dialogue[latestUserIndex],
    ...afterLatestUser,
    ...dialogue.slice(latestUserIndex + 1),
    ...afterToolFlow,
  ]
}

/**
 * Replace provider-native history from completed turns with Room's authoritative transcript.
 * Tool calls, tool results and reasoning from older DSH turns are execution history, not product
 * conversation history. The active turn starts at the latest direct user message and is retained
 * verbatim so its live tool flow still reaches the provider and the Request context inspector.
 */
export function projectProductHistory(messages, history) {
  if (!Array.isArray(messages)) return messages
  const currentUserIndex = messages.findLastIndex(isDirectUserMessage)
  if (currentUserIndex < 0) return messages
  const firstDialogue = messages.findIndex((message, index) => (
    index <= currentUserIndex && isDialogueMessage(message)
  ))
  const replaceFrom = firstDialogue < 0 ? currentUserIndex : firstDialogue
  const nativeHistory = messages.slice(replaceFrom, currentUserIndex)
  const productHistory = (Array.isArray(history) ? history : [])
    .map(productHistoryMessage)
    .filter(Boolean)
  const authoritative = compactedProjection(productHistory, nativeHistory) ?? productHistory
  return [
    ...messages.slice(0, replaceFrom),
    ...authoritative,
    ...messages.slice(currentUserIndex),
  ]
}

export function projectionPlanFromMessages(messages) {
  const envelope = Array.isArray(messages) ? messages.findLast(isProjectionEnvelope) : undefined
  if (!envelope) return undefined
  return decodeProjectionEnvelope(envelope)
}

export function isProjectionEnvelope(message) {
  return message?.role === 'user' &&
    message?.source?.kind === 'plugin' &&
    message?.source?.plugin === projectionPlugin
}

/** Remove both the current definition envelope and prompt rows left by pre-projection builds. */
export function isInternalProjectionMessage(message) {
  return isProjectionEnvelope(message) || message?.source?.plugin === ContextPlugin
}

/** Keep one current projection definition on the DSH surface. */
export function ensureProjectionEnvelope(session, plan) {
  const current = activeProjectionEnvelope(session)
  const next = projectionEnvelope(plan)
  if (current && messageText(current.data) === messageText(next)) return current
  if (!current && plan.length === 0) return undefined
  if (!current) return session.append('user/message', next, { surfaceOp: 'append' })
  return session.append('user/message', next, {
    surfaceOp: { op: 'replace', startSeq: current.seq, endSeq: current.seq },
    sourceEventSeqs: [current.seq],
  })
}

export function projectionEnvelope(plan) {
  return {
    id: `${projectionPlugin}:v${ProjectionVersion}`,
    role: 'user',
    content: [{ type: 'text', text: `${ProjectionPrefix}${JSON.stringify(plan)}` }],
    source: {
      kind: 'plugin',
      plugin: projectionPlugin,
      form: 'snapshot',
      sections: plan.map(entry => ({
        name: String(entry.traceTitle || entry.id || projectionPlugin),
        text: String(entry.content ?? ''),
      })),
    },
  }
}

function sortedInjections(snapshot) {
  return (Array.isArray(snapshot.injections) ? snapshot.injections : [])
    .filter(entry => entry && typeof entry === 'object' && String(entry.content ?? '').trim())
    .sort((left, right) =>
      contextAnchorOrder(left.anchor) - contextAnchorOrder(right.anchor) ||
      (left.order ?? 1) - (right.order ?? 1) ||
      String(left.id).localeCompare(String(right.id)),
    )
}

export function activeProjectionEnvelope(session) {
  for (const seq of session.surface.nodes.toReversed()) {
    const event = session.eventAt(seq)
    if (event?.type === 'user/message' && isProjectionEnvelope(event.data)) return event
  }
}

function decodeProjectionEnvelope(message) {
  const text = messageText(message)
  if (!text.startsWith(ProjectionPrefix)) return []
  try {
    const value = JSON.parse(text.slice(ProjectionPrefix.length))
    return Array.isArray(value) ? value.filter(isProjectionEntry) : []
  } catch {
    return []
  }
}

function isProjectionEntry(value) {
  return value && typeof value === 'object' &&
    typeof value.id === 'string' &&
    typeof value.anchor === 'string' &&
    (value.role === 'system' || value.role === 'user' || value.role === 'assistant') &&
    typeof value.content === 'string'
}

function projectionMessage(entry) {
  const role = entry.role === 'assistant' ? 'assistant' : entry.role === 'system' ? 'system' : 'user'
  return {
    id: `${projectionPlugin}:${entry.id}`,
    role,
    content: [{ type: 'text', text: String(entry.content ?? '') }],
    source: role === 'assistant'
      ? { kind: 'model', provider: 'eleckoi', model: 'prompt-projection' }
      : {
          kind: 'plugin',
          plugin: ContextPlugin,
          form: 'snapshot',
          label: String(entry.traceSource ?? ''),
          sections: [{
            name: String(entry.traceTitle || entry.id || ContextPlugin),
            text: String(entry.content ?? ''),
          }],
        },
  }
}

function productHistoryMessage(item, index) {
  if (!item || (item.role !== 'user' && item.role !== 'assistant')) return null
  const content = Array.isArray(item.content)
    ? item.content.filter(isProductDialogueBlock).map(block => structuredClone(block))
    : []
  if (content.length === 0) return null
  return {
    id: `eleckoi-product-history-${index}`,
    role: item.role,
    content,
    source: { kind: 'plugin', plugin: 'eleckoi-product-history' },
  }
}

function isProductDialogueBlock(block) {
  return block?.type === 'text' || block?.type === 'image' || block?.type === 'eleckoi-data-image'
}

function compactedProjection(productHistory, nativeHistory) {
  const checkpointIndex = nativeHistory.findLastIndex(isCompactionCheckpoint)
  if (checkpointIndex < 0) return null
  const checkpoint = nativeHistory[checkpointIndex]
  const nativeTail = nativeHistory.slice(checkpointIndex + 1).filter(isDialogueMessage)
  if (nativeTail.length > productHistory.length) return null
  const productTail = nativeTail.length === 0 ? [] : productHistory.slice(-nativeTail.length)
  if (!productTail.every((product, index) => messagesMatch(product, nativeTail[index]))) return null
  return [checkpoint, ...productTail]
}

function isDirectUserMessage(message) {
  return message?.role === 'user' && message?.source?.kind === 'user'
}

function isDialogueMessage(message) {
  return (message?.role === 'user' || message?.role === 'assistant') && message?.source?.kind !== 'tool'
}

function isCompactionCheckpoint(message) {
  return message?.role === 'user' && messageText(message).includes('<compacted-summary>')
}

function messagesMatch(left, right) {
  if (left?.role !== right?.role) return false
  return normalizedDialogueText(messageText(left), left.role) ===
    normalizedDialogueText(messageText(right), right.role)
}

function normalizedDialogueText(value, role) {
  const trimmed = value.trim()
  if (role !== 'assistant') return trimmed
  const start = trimmed.indexOf('<FINAL>')
  if (start < 0) return trimmed
  const bodyStart = start + '<FINAL>'.length
  const end = trimmed.lastIndexOf('</FINAL>')
  return trimmed.slice(bodyStart, end >= bodyStart ? end : undefined).trim()
}

function contextAnchorOrder(anchor) {
  const index = ContextAnchorOrder.indexOf(anchor)
  return index < 0 ? ContextAnchorOrder.length : index
}

function injectionActive(entry, messages) {
  const activation = entry?.activation ?? {}
  if (activation.kind === 'first' || activation.kind === 'immediate') return true
  const calls = messages.flatMap(message => Array.isArray(message?.content)
    ? message.content.filter(block => block?.type === 'tool-call')
    : [])
  if (activation.kind === 'afterTool') return calls.some(call => call.name === activation.toolName)
  if (activation.kind !== 'afterToolArgument') return false
  return calls.some(call => {
    if (call.name !== activation.toolName) return false
    let args
    try { args = JSON.parse(call.arguments ?? '{}') } catch { return false }
    const value = args?.[activation.argumentName]
    return Array.isArray(value) ? value.includes(activation.value) : value === activation.value
  })
}

function messageText(message) {
  return (Array.isArray(message?.content) ? message.content : [])
    .filter(block => block?.type === 'text')
    .map(block => String(block.text ?? ''))
    .join('\n')
}

const ContextAnchorOrder = [
  'beforeToolContext',
  'toolContext',
  'afterToolContext',
  'beforeHistory',
  'afterHistory',
  'beforeLatestUserInput',
  'afterLatestUserInput',
  'beforeToolFlow',
  'afterToolFlow',
]
