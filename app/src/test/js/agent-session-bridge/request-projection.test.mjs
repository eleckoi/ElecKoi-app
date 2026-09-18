import assert from 'node:assert/strict'
import test from 'node:test'

import {
  ensureProjectionEnvelope,
  projectProductHistory,
  projectRequestMessages,
} from '../../../main/assets/dsh-plugins/agent-session-bridge/request-projection.mjs'

const textMessage = (id, role, text, source) => ({
  id,
  role,
  content: [{ type: 'text', text }],
  source,
})

const projectionPlan = [
  prompt('point-1', 'beforeHistory', 1, 'user', 'POINT_1'),
  prompt('cache', 'beforeHistory', 2, 'user', 'CACHE'),
  prompt('point-2', 'beforeHistory', 3, 'assistant', 'POINT_2'),
  prompt('point-3', 'beforeLatestUserInput', 4, 'user', 'POINT_3'),
  prompt('point-4', 'beforeToolFlow', 5, 'assistant', 'POINT_4'),
  prompt('point-5', 'afterToolFlow', 6, 'user', 'POINT_5'),
  {
    ...prompt('after-read', 'afterToolFlow', 7, 'assistant', 'AFTER_READ'),
    activation: { kind: 'afterTool', toolName: 'read' },
  },
]

test('projects the PC insertion contract into every model request exactly once', () => {
  const messages = requestMessages()
  const projected = projectRequestMessages(messages, projectionPlan)

  assert.deepEqual(
    projected.map(readableValue),
    [
      'SYSTEM',
      'POINT_1',
      'CACHE',
      'POINT_2',
      'OLD_USER',
      'OLD_ASSISTANT',
      'POINT_3',
      'LATEST_USER',
      'POINT_4',
      'TOOL_CALL',
      'TOOL_RESULT',
      'POINT_5',
      'AFTER_READ',
    ],
  )
  assert.deepEqual(
    projected
      .filter(message => String(message.id).startsWith('eleckoi-request-projection:'))
      .map(message => message.role),
    ['user', 'user', 'assistant', 'user', 'assistant', 'user', 'assistant'],
  )
  for (const entry of projectionPlan) {
    assert.equal(
      projected.filter(message => message.content?.[0]?.text === entry.content).length,
      1,
      `${entry.id} must occur once`,
    )
  }
})

test('current-turn tool activation ignores calls retained in older history', () => {
  const messages = [
    textMessage('system', 'system', 'SYSTEM', { kind: 'system' }),
    {
      id: 'old-call',
      role: 'assistant',
      source: { kind: 'model' },
      content: [{ type: 'tool-call', id: 'old', name: 'read', arguments: '{}' }],
    },
    textMessage('old-user', 'user', 'OLD_USER', { kind: 'user' }),
    textMessage('latest-user', 'user', 'LATEST_USER', { kind: 'user' }),
  ]

  assert.equal(
    projectRequestMessages(messages, projectionPlan)
      .some(message => message.content?.[0]?.text === 'AFTER_READ'),
    false,
  )
})

test('replaces completed DSH tool history but keeps the active turn tool flow', () => {
  const currentToolCall = {
    id: 'current-call-message',
    role: 'assistant',
    source: { kind: 'model' },
    content: [{ type: 'tool-call', id: 'current-call', name: 'read', arguments: '{}' }],
  }
  const currentToolResult = {
    id: 'current-result-message',
    role: 'user',
    source: { kind: 'tool', callId: 'current-call' },
    content: [{ type: 'tool-result', toolCallId: 'current-call', content: 'CURRENT_RESULT' }],
  }
  const projected = projectProductHistory([
    textMessage('system', 'system', 'SYSTEM', { kind: 'system' }),
    textMessage('old-thought', 'assistant', 'OLD_THOUGHT', { kind: 'model' }),
    {
      id: 'old-call-message',
      role: 'assistant',
      source: { kind: 'model' },
      content: [{ type: 'tool-call', id: 'old-call', name: 'read', arguments: '{}' }],
    },
    {
      id: 'old-result-message',
      role: 'user',
      source: { kind: 'tool', callId: 'old-call' },
      content: [{ type: 'tool-result', toolCallId: 'old-call', content: 'OLD_RESULT' }],
    },
    textMessage('old-final', 'assistant', '<FINAL>OLD_ANSWER</FINAL>', { kind: 'model' }),
    textMessage('latest-user', 'user', 'LATEST_USER', { kind: 'user' }),
    currentToolCall,
    currentToolResult,
  ], [
    textMessage('history-user', 'user', 'OLD_QUESTION', { kind: 'plugin' }),
    textMessage('history-assistant', 'assistant', 'OLD_ANSWER', { kind: 'plugin' }),
    {
      id: 'stale-history-call',
      role: 'assistant',
      source: { kind: 'plugin' },
      content: [{ type: 'tool-call', id: 'stale', name: 'read', arguments: '{}' }],
    },
    {
      id: 'stale-history-result',
      role: 'user',
      source: { kind: 'tool', callId: 'stale' },
      content: [{ type: 'tool-result', toolCallId: 'stale', content: 'STALE_RESULT' }],
    },
  ])

  assert.deepEqual(projected.map(readableValue), [
    'SYSTEM',
    'OLD_QUESTION',
    'OLD_ANSWER',
    'LATEST_USER',
    'TOOL_CALL',
    'TOOL_RESULT',
  ])
  assert.equal(JSON.stringify(projected).includes('OLD_THOUGHT'), false)
  assert.equal(JSON.stringify(projected).includes('OLD_RESULT'), false)
  assert.equal(JSON.stringify(projected).includes('STALE_RESULT'), false)
  assert.equal(projected.at(-2), currentToolCall)
  assert.equal(projected.at(-1), currentToolResult)
})

test('keeps one durable definition and replaces it only when content changes', () => {
  const events = []
  const session = fakeSession(events)

  ensureProjectionEnvelope(session, projectionPlan)
  ensureProjectionEnvelope(session, projectionPlan)
  ensureProjectionEnvelope(
    session,
    projectionPlan.map(entry => entry.id === 'point-1'
      ? { ...entry, content: 'POINT_1_UPDATED' }
      : entry),
  )

  assert.equal(events.length, 2)
  assert.equal(events[0].surfaceOp, 'append')
  assert.deepEqual(events[1].surfaceOp, { op: 'replace', startSeq: 0, endSeq: 0 })
  assert.deepEqual(events[1].sourceEventSeqs, [0])
  assert.deepEqual(session.surface.nodes, [1])
})

function prompt(id, anchor, order, role, content) {
  return {
    id,
    anchor,
    order,
    role,
    content,
    activation: { kind: 'first' },
  }
}

function requestMessages() {
  return [
    textMessage('system', 'system', 'SYSTEM', { kind: 'system' }),
    textMessage(
      'eleckoi-injection-obsolete',
      'user',
      'OBSOLETE_PROMPT_COPY',
      { kind: 'plugin', plugin: 'eleckoi-agent-session-bridge', form: 'snapshot' },
    ),
    textMessage('old-user', 'user', 'OLD_USER', { kind: 'user' }),
    textMessage('old-assistant', 'assistant', 'OLD_ASSISTANT', { kind: 'model' }),
    textMessage('latest-user', 'user', 'LATEST_USER', { kind: 'user' }),
    {
      id: 'call',
      role: 'assistant',
      source: { kind: 'model' },
      content: [{ type: 'tool-call', id: 'call-1', name: 'read', arguments: '{}' }],
    },
    {
      id: 'result',
      role: 'user',
      source: { kind: 'tool', callId: 'call-1' },
      content: [{ type: 'tool-result', toolCallId: 'call-1', content: 'TOOL_RESULT' }],
    },
  ]
}

function readableValue(message) {
  const block = message.content?.[0]
  if (block?.type === 'text') return block.text
  if (block?.type === 'tool-call') return 'TOOL_CALL'
  if (block?.type === 'tool-result') return 'TOOL_RESULT'
  return ''
}

function fakeSession(events) {
  return {
    surface: { nodes: [] },
    eventAt(seq) {
      return events.find(event => event.seq === seq)
    },
    append(type, data, options) {
      const event = { seq: events.length, type, data, ...options }
      events.push(event)
      if (options.surfaceOp === 'append') {
        this.surface.nodes.push(event.seq)
      } else if (options.surfaceOp?.op === 'replace') {
        const index = this.surface.nodes.indexOf(options.surfaceOp.startSeq)
        this.surface.nodes.splice(index, 1, event.seq)
      }
      return event
    },
  }
}
