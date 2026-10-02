import test from 'node:test';
import assert from 'node:assert/strict';
import { projectRequestMessages } from '../app/src/main/assets/dsh-plugins/agent-session-bridge/request-projection.mjs';

test('plugin depth and order reach real provider projection without changing stored history', () => {
  const messages = [
    { role: 'system', content: [{ type: 'text', text: 'native' }] },
    { role: 'user', content: [{ type: 'text', text: 'first' }], source: { kind: 'user' } },
    { role: 'assistant', content: [{ type: 'text', text: 'reply' }] },
    { role: 'user', content: [{ type: 'text', text: 'latest' }], source: { kind: 'user' } },
  ];
  const original = JSON.stringify(messages);
  const plan = [{ id: 'memory', anchor: 'beforeLatestUserInput', role: 'system', depth: 1, content: 'remember A', activation: { kind: 'first' } }];
  const result = projectRequestMessages(messages, plan);
  assert.equal(result[3].content[0].text, 'remember A');
  assert.equal(result[4], messages[3]);
  assert.equal(JSON.stringify(messages), original);
});
