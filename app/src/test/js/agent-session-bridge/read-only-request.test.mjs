import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'

import { apply } from '../../../main/assets/dsh-plugins/agent-session-bridge/agent-session-bridge.mjs'

test('reroutes a projected copy without mutating DSH read-only request options', async () => {
  const root = mkdtempSync(join(tmpdir(), 'eleckoi-read-only-request-'))
  try {
    const snapshotRoot = join(root, 'snapshots')
    const requestContextRoot = join(root, 'request-context')
    mkdirSync(snapshotRoot)
    writeFileSync(join(snapshotRoot, 'session.json'), JSON.stringify({
      schemaVersion: 3,
      sessionId: 'session',
      mountedPresetId: 'default-agent',
      model: { provider: 'deepseek', model: 'deepseek-chat' },
      subagentModel: { provider: 'deepseek', model: 'deepseek-chat' },
      historyProjection: 'Native',
      history: [],
      injections: [{
        id: 'point-3',
        anchor: 'beforeLatestUserInput',
        role: 'user',
        content: 'PROMPT',
        order: 1,
        traceTitle: '角色设定',
        traceSource: '设定插入点 3',
        activation: { kind: 'first' },
      }],
    }))

    const context = fakeRootContext()
    apply(context, { snapshotRoot, requestContextRoot })
    const composed = await context.agents.create({ sessionId: 'session' })
    const listeners = new Map()
    const session = fakeSession()
    let streamListener
    let terminalCalls = 0
    const agentContext = {
      systemPrompt: { section: () => () => {} },
      on(event, listener) {
        listeners.set(event, listener)
        if (event === 'llm/stream') streamListener = listener
        return () => {}
      },
      llm: {
        stream(options) {
          return streamListener(options, () => {
            terminalCalls += 1
            return options
          })
        },
      },
    }
    await composed.setup(agentContext, { session })

    const originalMessages = [textMessage('user', 'HELLO', { kind: 'user' })]
    const readOnlyOptions = deepFreeze({
      provider: 'deepseek',
      model: 'deepseek-chat',
      sessionId: 'session',
      messages: originalMessages,
    })
    const result = listeners.get('llm/stream')(readOnlyOptions, () => {
      throw new Error('the frozen Agent-loop request must be replaced before terminal dispatch')
    })

    assert.equal(terminalCalls, 1)
    assert.equal(readOnlyOptions.messages, originalMessages)
    assert.notEqual(result, readOnlyOptions)
    assert.deepEqual(result.messages.map(messageText), ['PROMPT', 'HELLO'])
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('records product dialogue instead of completed-turn tool events in Request context', async () => {
  const root = mkdtempSync(join(tmpdir(), 'eleckoi-product-history-request-'))
  try {
    const snapshotRoot = join(root, 'snapshots')
    const requestContextRoot = join(root, 'request-context')
    mkdirSync(snapshotRoot)
    writeFileSync(join(snapshotRoot, 'session.json'), JSON.stringify({
      schemaVersion: 3,
      sessionId: 'session',
      mountedPresetId: 'default-agent',
      model: { provider: 'deepseek', model: 'deepseek-chat' },
      subagentModel: { provider: 'deepseek', model: 'deepseek-chat' },
      historyProjection: 'Native',
      history: [
        textMessage('history-user', 'OLD_QUESTION', { kind: 'plugin' }),
        { ...textMessage('history-assistant', 'OLD_ANSWER', { kind: 'plugin' }), role: 'assistant' },
      ],
      injections: [],
    }))

    const context = fakeRootContext()
    apply(context, { snapshotRoot, requestContextRoot })
    const composed = await context.agents.create({ sessionId: 'session' })
    const listeners = new Map()
    const session = fakeSession()
    let streamListener
    const agentContext = {
      systemPrompt: { section: () => () => {} },
      on(event, listener) {
        listeners.set(event, listener)
        if (event === 'llm/stream') streamListener = listener
        return () => {}
      },
      llm: { stream: options => streamListener(options, () => options) },
    }
    await composed.setup(agentContext, { session })

    const oldToolCall = {
      id: 'old-call-message',
      role: 'assistant',
      source: { kind: 'model' },
      content: [{ type: 'tool-call', id: 'old-call', name: 'read', arguments: '{}' }],
    }
    const oldToolResult = {
      id: 'old-result-message',
      role: 'user',
      source: { kind: 'tool', callId: 'old-call' },
      content: [{ type: 'tool-result', toolCallId: 'old-call', content: 'OLD_TOOL_RESULT' }],
    }
    const result = listeners.get('llm/stream')({
      provider: 'deepseek',
      model: 'deepseek-chat',
      sessionId: 'session',
      messages: [
        oldToolCall,
        oldToolResult,
        { ...textMessage('old-final', '<FINAL>OLD_ANSWER</FINAL>', { kind: 'model' }), role: 'assistant' },
        textMessage('latest-user', 'LATEST_USER', { kind: 'user' }),
      ],
    }, () => {
      throw new Error('the Agent request must be rerouted through the projected copy')
    })

    assert.deepEqual(result.messages.map(messageText), ['OLD_QUESTION', 'OLD_ANSWER', 'LATEST_USER'])
    assert.equal(JSON.stringify(result.messages).includes('OLD_TOOL_RESULT'), false)
    const persisted = readFileSync(join(requestContextRoot, 'session.jsonl'), 'utf8')
    assert.equal(persisted.includes('OLD_TOOL_RESULT'), false)
    assert.equal(persisted.includes('OLD_QUESTION'), true)
    assert.equal(persisted.includes('LATEST_USER'), true)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

function fakeRootContext() {
  return {
    attachments: {},
    agentPresets: { mount: async () => {} },
    agents: {
      create: async options => options,
      resume: options => options,
    },
  }
}

function fakeSession() {
  const events = [{ type: 'step/start', seq: 4, time: 10, data: { turn: 1, step: 1 } }]
  return {
    id: 'session',
    surface: { nodes: [] },
    eventAt(seq) {
      return events.find(event => event.seq === seq)
    },
    snapshotEvents() {
      return events
    },
    append(type, data, options) {
      const event = { seq: events.length, type, data, ...options }
      events.push(event)
      if (options.surfaceOp === 'append') this.surface.nodes.push(event.seq)
      return event
    },
  }
}

function textMessage(id, text, source) {
  return { id, role: 'user', content: [{ type: 'text', text }], source }
}

function messageText(message) {
  return message.content.map(block => block.text || '').join('')
}

function deepFreeze(value) {
  if (!value || typeof value !== 'object' || Object.isFrozen(value)) return value
  Object.values(value).forEach(deepFreeze)
  return Object.freeze(value)
}
