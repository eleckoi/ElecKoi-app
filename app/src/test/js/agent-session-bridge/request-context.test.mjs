import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'

import {
  recordRequestContextSnapshot,
  requestContextItems,
} from '../../../main/assets/dsh-plugins/agent-session-bridge/request-context.mjs'

test('stores one deduplicated definition set and one exact snapshot per real request', () => {
  const directory = mkdtempSync(join(tmpdir(), 'eleckoi-request-context-'))
  try {
    const file = join(directory, 'thread.jsonl')
    const session = fakeSession({ seq: 7, time: 100, data: { turn: 1, step: 1 } })
    const messages = requestMessages('PROMPT A')
    const plan = requestPlan('PROMPT A')

    recordRequestContextSnapshot(file, session, messages, plan)
    session.boundary = { type: 'step/start', seq: 12, time: 200, data: { turn: 1, step: 2 } }
    recordRequestContextSnapshot(file, session, messages, plan)
    session.boundary = { type: 'step/start', seq: 18, time: 300, data: { turn: 2, step: 1 } }
    recordRequestContextSnapshot(file, session, requestMessages('PROMPT B'), requestPlan('PROMPT B'))

    const rows = readFileSync(file, 'utf8').trim().split(/\r?\n/).map(JSON.parse)
    const requests = rows.filter(row => row.type === 'request')
    const definitions = rows.filter(row => row.type === 'definition')
    assert.deepEqual(requests.map(row => row.requestSeq), [7, 12, 18])
    assert.equal(definitions.filter(row => row.content === 'PROMPT A').length, 1)
    assert.equal(definitions.filter(row => row.content === 'PROMPT B').length, 1)
    assert.deepEqual(
      requestContextItems(messages, plan).map(item => [item.order, item.role, item.kind, item.content]),
      [
        [1, 'system', 'system', 'SYSTEM'],
        [2, 'user', 'prompt', 'PROMPT A'],
        [3, 'user', 'user', 'HELLO'],
        [4, 'user', 'tool', '工具返回结果\nRESULT'],
      ],
    )
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
})

function requestMessages(promptText) {
  return [
    message('system', 'system', 'SYSTEM', { kind: 'system' }),
    message('eleckoi-request-projection:prompt', 'user', promptText, { kind: 'plugin' }),
    message('user', 'user', 'HELLO', { kind: 'user' }),
    {
      id: 'result',
      role: 'user',
      source: { kind: 'tool', callId: 'call' },
      content: [{
        type: 'tool-result',
        toolCallId: 'call',
        content: [{ type: 'text', text: 'RESULT' }],
      }],
    },
  ]
}

function requestPlan(content) {
  return [{
    id: 'prompt',
    role: 'user',
    anchor: 'beforeHistory',
    traceTitle: '角色设定',
    traceSource: '设定插入点 1',
    content,
  }]
}

function message(id, role, text, source) {
  return { id, role, source, content: [{ type: 'text', text }] }
}

function fakeSession(boundary) {
  return {
    boundary: { type: 'step/start', ...boundary },
    snapshotEvents() {
      return [this.boundary]
    },
  }
}
