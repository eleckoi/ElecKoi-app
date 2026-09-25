import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { recordRequestContextSnapshot, requestContextPath } from './request-context.mjs'
import {
  activeProjectionEnvelope,
  ensureProjectionEnvelope,
  isInternalProjectionMessage,
  projectProductHistory,
  projectRequestMessages,
  projectionEnvelope,
  requestInstructions,
  requestProjectionPlan,
} from './request-projection.mjs'

export {
  projectProductHistory,
  projectRequestMessages,
  projectionPlugin,
  requestProjectionPlan,
} from './request-projection.mjs'

export const name = 'eleckoi-agent-session-bridge'
export const inject = ['agents', 'attachments', 'agentPresets']

// External plugins are loaded from DSH_HOME. In the ARM64 single executable they cannot resolve
// packages embedded inside the executable, so construct the public DSH message envelopes here.
// The shapes intentionally match dsh-llm 0.1.5's create*Message helpers.
function createUserMessage(input) {
  return { ...structuredClone(input), id: randomUUID(), role: 'user' }
}

function createAssistantMessage(input) {
  return {
    id: randomUUID(),
    role: 'assistant',
    content: structuredClone(input.content),
    source: { kind: 'model', ...structuredClone(input.source) },
  }
}

function createToolResultMessage(input) {
  return createUserMessage({
    source: { kind: 'tool', callId: input.callId },
    content: [{
      type: 'tool-result',
      toolCallId: input.callId,
      content: structuredClone(input.content),
      isError: input.isError,
    }],
  })
}

export function apply(ctx, config = {}) {
  const snapshotRoot = requireNonEmpty(config.snapshotRoot, 'ElecKoi Session snapshot root')
  const requestContextRoot = requireNonEmpty(config.requestContextRoot, 'ElecKoi Request context root')
  const originalCreate = ctx.agents.create
  const originalResume = ctx.agents.resume

  const wrappedCreate = async function (options) {
    const child = options.parentAgent !== undefined || options.meta?.origin === 'subagent'
    const sourceSessionId = child
      ? options.parentAgent?.session?.id ?? options.meta?.parentSession
      : options.sessionId
    if (!sourceSessionId) throw new Error('ElecKoi Agent is missing its source Session id')
    const snapshot = readSnapshot(snapshotRoot, sourceSessionId)
    const seededOptions = child || options.seed !== undefined
      ? options
      : await withConversationSeed(options, snapshot, ctx.attachments)
    return originalCreate.call(
      ctx.agents,
      composeOptions(ctx, seededOptions, snapshotRoot, requestContextRoot, sourceSessionId, child, false),
    )
  }
  const wrappedResume = function (options) {
    const child = options.parentAgent !== undefined
    const sourceSessionId = child ? options.parentAgent?.session?.id : options.resumeSessionId
    if (!sourceSessionId) return originalResume.call(ctx.agents, options)
    try {
      readSnapshot(snapshotRoot, sourceSessionId)
    } catch (error) {
      if (error?.code === 'ENOENT') return originalResume.call(ctx.agents, options)
      throw error
    }
    return originalResume.call(
      ctx.agents,
      composeOptions(ctx, options, snapshotRoot, requestContextRoot, sourceSessionId, child, true),
    )
  }

  ctx.agents.create = wrappedCreate
  ctx.agents.resume = wrappedResume
  return () => {
    if (ctx.agents.create === wrappedCreate) ctx.agents.create = originalCreate
    if (ctx.agents.resume === wrappedResume) ctx.agents.resume = originalResume
  }
}

function composeOptions(rootCtx, options, snapshotRoot, requestContextRoot, sourceSessionId, child, resuming) {
  const snapshot = readSnapshot(snapshotRoot, sourceSessionId)
  const model = child ? snapshot.subagentModel : snapshot.model
  if (!model?.provider || !model?.model) {
    throw new Error(`ElecKoi Session ${sourceSessionId} is missing its model snapshot`)
  }
  if (!child && !snapshot.mountedPresetId) {
    throw new Error(`ElecKoi Session ${sourceSessionId} is missing its Agent Preset snapshot`)
  }
  const originalSetup = options.setup
  return {
    ...options,
    agentOptions: requestAgentOptions(options.agentOptions, model),
    ...(child || resuming ? {} : {
      meta: { ...(options.meta ?? {}), agentPreset: snapshot.mountedPresetId },
    }),
    setup: async (agentCtx, agent) => {
      installRequestPipeline(agentCtx, agent, snapshotRoot, requestContextRoot, sourceSessionId, child)
      const transaction = await originalSetup?.(agentCtx, agent)
      if (!child) await rootCtx.agentPresets.mount(agentCtx, snapshot.mountedPresetId)
      return transaction
    },
  }
}

function installRequestPipeline(agentCtx, agent, snapshotRoot, requestContextRoot, sourceSessionId, child) {
  const read = () => readSnapshot(snapshotRoot, sourceSessionId)
  const reroutedRequests = new WeakSet()
  const reroute = options => {
    reroutedRequests.add(options)
    return agentCtx.llm.stream(options)
  }
  let assembled
  const disposeInstructions = child ? () => {} : agentCtx.systemPrompt.section({
    name: 'eleckoi:session-instructions',
    order: 1,
    // DSH interpolates section text, but does not rescan substituted values.
    // Keep user-authored {{...}} syntax literal instead of parsing it as a DSH variable.
    text: '{{eleckoi_session_instructions}}',
  })
  const disposeAssembly = agentCtx.on('system-prompt/assemble', async (_assembly, _context, next) => {
    const snapshot = read()
    assembled = structuredClone(child ? snapshot.subagentModel : snapshot.model)
    const result = await next()
    return {
      ...result,
      variables: {
        ...result.variables,
        ...(!child ? { eleckoi_session_instructions: requestInstructions(snapshot) } : {}),
        provider: assembled.provider,
        model: assembled.model,
      },
    }
  })
  const disposeRequest = agentCtx.on('agent/request', async (_payload, next) => {
    const inherited = await next()
    if (!assembled) return inherited
    const {
      provider: _provider,
      model: _model,
      reasoningEffort: _reasoningEffort,
      temperature: _temperature,
      topP: _topP,
      maxTokens: _maxTokens,
      ...rest
    } = inherited
    return {
      ...rest,
      provider: assembled.provider,
      model: assembled.model,
      ...(assembled.reasoningEffort === undefined ? {} : { reasoningEffort: assembled.reasoningEffort }),
      ...(assembled.temperature === undefined ? {} : { temperature: assembled.temperature }),
      ...(assembled.topP === undefined ? {} : { topP: assembled.topP }),
      ...(assembled.maxTokens === undefined ? {} : { maxTokens: assembled.maxTokens }),
    }
  })
  // Keep one durable projection definition in DSH, but never send that internal JSON to a model.
  // Every ordinary request is rebuilt from the real surface so tool continuations preserve the
  // exact 1..5 placement contract without appending another copy of the prompt text to history.
  const disposeStreamProjection = agentCtx.on('llm/stream', (options, next) => {
    // DSH deep-freezes every Agent-loop request. A projected request must therefore be a new
    // one-shot request, and this process-local marker lets its nested waterfall reach the adapter.
    if (reroutedRequests.has(options)) return next()
    if (options?.purpose === 'compaction') {
      const { tools: _tools, reasoningEffort: _reasoningEffort, ...rest } = options
      return reroute({
        ...rest,
        messages: Array.isArray(options.messages)
          ? options.messages.filter(message => !isInternalProjectionMessage(message))
          : options.messages,
      })
    }
    // The SEA-loaded external plugin cannot import dsh-llm's process-local WeakSet helper.
    // The loop's own request is nevertheless uniquely scoped by this Agent's Session id and by
    // having no auxiliary purpose (session-title/compaction calls must remain untouched).
    if (child || options?.purpose !== undefined || options?.sessionId !== agent.session.id ||
      !Array.isArray(options?.messages)) return next()
    const snapshot = read()
    const plan = requestProjectionPlan(snapshot)
    ensureProjectionEnvelope(agent.session, plan)
    const productMessages = projectProductHistory(options.messages, snapshot.history)
    const messages = projectRequestMessages(productMessages, plan)
    recordRequestContextSnapshot(
      requestContextPath(requestContextRoot, agent.session.id),
      agent.session,
      messages,
      plan,
    )
    return reroute({ ...options, messages })
  })
  const disposeProjection = child ? () => {} : agentCtx.on(
    'agent/pre-step',
    async ({ signal }, next) => {
      const decision = await next()
      if (decision.kind === 'reject' || signal.aborted) return decision
      const plan = requestProjectionPlan(read())
      if (plan.length === 0 || activeProjectionEnvelope(agent.session)) return decision
      return {
        ...decision,
        messages: [...decision.messages, projectionEnvelope(plan)],
      }
    },
  )
  return () => {
    disposeProjection()
    disposeStreamProjection()
    disposeRequest()
    disposeAssembly()
    disposeInstructions()
  }
}

async function withConversationSeed(options, snapshot, attachments) {
  if (snapshot.historyProjection === 'Native') return options
  const history = await admitDataImages(
    Array.isArray(snapshot.history) ? snapshot.history : [],
    attachments,
  )
  const seed = createConversationSeed(history, snapshot.model)
  return seed.length === 0 ? options : { ...options, seed }
}

export function createConversationSeed(history, modelSelection) {
  const events = []
  let sequence = 0
  let turn = 0
  let step = 0
  let openTurn = false
  let openStep = false
  let stepHasToolResult = false
  let assistantContent = []
  const callEventSequences = new Map()
  const append = (type, data, surface = false, sourceEventSeqs) => {
    const event = {
      type,
      seq: sequence,
      time: Date.now() + sequence,
      data,
      ...(surface ? { surfaceOp: 'append' } : {}),
      ...(sourceEventSeqs === undefined ? {} : { sourceEventSeqs }),
    }
    events.push(event)
    sequence += 1
    return event
  }
  const beginTurn = () => {
    turn += 1
    step = 0
    append('turn/start', { turn })
    openTurn = true
  }
  const beginStep = () => {
    if (!openTurn) beginTurn()
    step += 1
    append('step/start', { turn, step })
    openStep = true
    stepHasToolResult = false
    assistantContent = []
    callEventSequences.clear()
  }
  const flushAssistant = () => {
    if (assistantContent.length === 0) return
    if (!openStep) beginStep()
    const content = assistantContent
    assistantContent = []
    append('assistant/message', {
      turn,
      step,
      stream: [],
      message: createAssistantMessage({
        content,
        source: {
          provider: modelSelection.provider,
          model: modelSelection.model,
        },
      }),
    }, true)
    for (const block of content) {
      if (block?.type !== 'tool-call') continue
      const call = append('tool/call', {
        turn,
        step,
        callId: block.id,
        name: block.name,
        arguments: block.arguments,
      })
      callEventSequences.set(block.id, call.seq)
    }
  }
  const endStep = () => {
    if (!openStep) return
    flushAssistant()
    append('step/end', { turn, step })
    openStep = false
    stepHasToolResult = false
    assistantContent = []
    callEventSequences.clear()
  }
  const endTurn = () => {
    if (!openTurn) return
    endStep()
    append('turn/end', { turn, reason: { kind: 'completed' } })
    openTurn = false
  }
  for (const message of history) {
    if (!message || !Array.isArray(message.content)) continue
    if (message.role === 'user' && message.source?.kind !== 'tool') {
      endTurn()
      beginTurn()
      append('user/message', createUserMessage({
        content: message.content,
        source: { kind: 'user' },
      }), true)
      continue
    }
    if (message.role === 'assistant') {
      if (stepHasToolResult) endStep()
      if (!openTurn) beginTurn()
      if (!openStep) beginStep()
      assistantContent.push(...message.content)
      continue
    }
    if (message.role === 'user' && message.source?.kind === 'tool') {
      if (!openTurn) beginTurn()
      if (!openStep) beginStep()
      flushAssistant()
      const block = message.content[0]
      const callId = message.source.callId
      const callEventSeq = callEventSequences.get(callId)
      if (block?.type !== 'tool-result' || block.toolCallId !== callId || callEventSeq === undefined) {
        throw new Error('ElecKoi history contains an unpaired tool result for ' + String(callId ?? 'unknown'))
      }
      append('tool/result', {
        turn,
        step,
        message: createToolResultMessage({
          callId,
          content: block.content,
          isError: block.isError === true,
        }),
      }, true, [callEventSeq])
      stepHasToolResult = true
    }
  }
  endTurn()
  return events
}

function requestAgentOptions(inherited, model) {
  const {
    provider: _provider,
    model: _model,
    maxTokens: _maxTokens,
    reasoningEffort: _reasoningEffort,
    ...rest
  } = inherited ?? {}
  return {
    ...rest,
    provider: model.provider,
    model: model.model,
    ...(model.maxTokens === undefined ? {} : { maxTokens: model.maxTokens }),
    ...(model.reasoningEffort === undefined ? {} : { reasoningEffort: model.reasoningEffort }),
  }
}

async function admitDataImages(messages, attachments) {
  if (!Array.isArray(messages)) return messages
  return Promise.all(messages.map(async message => ({
    ...message,
    content: await admitContent(message?.content, attachments),
  })))
}

async function admitContent(content, attachments) {
  if (!Array.isArray(content)) return content
  return Promise.all(content.map(async block => {
    if (!block || typeof block !== 'object' || Array.isArray(block)) return block
    if (block.type === 'eleckoi-data-image') {
      const match = /^data:(image\/(?:png|jpeg|webp|gif));base64,([A-Za-z0-9+/]+={0,2})$/.exec(block.dataUrl)
      if (!match) throw new Error('ElecKoi history image is not a supported base64 data URL')
      const attachment = await attachments.saveImage({
        data: Uint8Array.from(Buffer.from(match[2], 'base64')),
        mediaType: match[1],
      })
      return { type: 'image', attachment }
    }
    if (block.type === 'tool-result') return { ...block, content: await admitContent(block.content, attachments) }
    return block
  }))
}

function readSnapshot(root, sessionId) {
  const safeId = String(sessionId ?? '')
  if (!/^[A-Za-z0-9._:-]{1,160}$/.test(safeId)) throw new Error('Invalid ElecKoi Session id')
  const value = JSON.parse(readFileSync(join(root, `${safeId}.json`), 'utf8'))
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid ElecKoi Session snapshot')
  return value
}

function requireNonEmpty(value, label) {
  if (typeof value !== 'string' || value.length === 0) throw new Error(`${label} is missing`)
  return value
}
