import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';

const source = await readFile(new URL('../app/src/main/assets/frontend/preview/tavern-compat.js', import.meta.url), 'utf8');
const copy = value => JSON.parse(JSON.stringify(value));
function host() {
  let current = 'chat-a';
  const calls = [], errors = [], subscribers = new Map(), scopes = new Map();
  const snapshots = new Map(['chat-a', 'chat-b'].map((id, index) => [id, {
    conversationId: id, characterId: `char-${index}`, characterName: `Role ${index}`, userName: 'User', presetId: 'preset-1',
    messages: [{ id: `${id}-user`, role: 'user', content: 'hello', metadata: {} }, { id: `${id}-ai`, role: 'assistant', content: 'world', metadata: {} }],
    variables: { chat: {}, character: {}, preset: {}, global: {}, script: {}, extension: {}, plugin: {}, message: {} }, settings: {}, metadata: {},
  }]));
  let failWrite = false;
  const api = {
    async call(method, params = {}) {
      calls.push({ method, params: copy(params) });
      const id = params.conversationId || current, snapshot = snapshots.get(id);
      switch (method) {
        case 'plugins.bootstrap': {
          const result = copy(snapshots.get(current));
          for (const [key, value] of scopes) {
            const [chat, scope] = key.split('/'); if (chat === current) result.variables[scope] = copy(value);
          }
          return result;
        }
        case 'variables.writeScope':
          if (failWrite) throw new Error('disk is full');
          scopes.set(`${id}/${params.scope}`, copy(params.value)); return params.value;
        case 'messages.update':
          for (const update of params.messages) {
            const message = snapshot.messages.find(value => value.id === update.id);
            if (update.expectedContent !== undefined && message.content !== update.expectedContent) throw new Error('stale message');
            if (update.content !== undefined) message.content = update.content;
            message.metadata = update.metadata;
          }
          return copy(snapshot.messages);
        case 'messages.insert': snapshot.messages.splice(params.index, 0, ...params.messages.map((value, index) => ({ ...value, id: `insert-${index}`, metadata: {} }))); return copy(snapshot.messages);
        case 'messages.delete': snapshot.messages = snapshot.messages.filter(value => !params.ids.includes(value.id)); return copy(snapshot.messages);
        case 'messages.metadata': if (params.value) snapshot.metadata = params.value; return copy(snapshot.metadata);
        case 'settings.set': snapshot.settings = params.value; return params.value;
        case 'generation.invoke': return { id: params.id, content: 'generated', reasoning: 'thought' };
        case 'worldbooks.get': return { name: params.name, entries: [] };
        case 'network.request': return { status: 200, ok: true, body: '{"x":1}', headers: {} };
        default: return null;
      }
    },
    events: { on(name, callback) { const callbacks = subscribers.get(name) || new Set(); callbacks.add(callback); subscribers.set(name, callbacks); return () => callbacks.delete(callback); } },
    chat: { getModels: async () => ({ configs: [] }) }, input: { set: async value => calls.push({ input: value }), send: async () => calls.push({ send: true }) },
    audio: {}, messages: {},
  };
  const context = vm.createContext({ console: { error: (...args) => errors.push(args) },
    CustomEvent: class { constructor(name, options) { this.name = name; this.detail = options?.detail; } },
    Date, Math, Map, Set, Promise, AggregateError, JSON, Object, Array, Number, String, RegExp,
    dispatchEvent() {}, addEventListener() {}, ElecKoi: api });
  context.window = context; vm.runInContext(source, context, { filename: 'tavern-compat.js' });
  return { context, calls, errors, snapshots, scopes, change: id => { current = id; }, fail: value => { failWrite = value; },
    async emit(name, payload) { for (const callback of subscribers.get(name) || []) callback(payload); await new Promise(resolve => setTimeout(resolve, 20)); } };
}

test('global, TavernHelper and SillyTavern share one hydrated context', async () => {
  const { context: c } = host(); await c.ElecKoi.ready();
  assert.equal(c.getChatMessages, c.TavernHelper.getChatMessages);
  assert.equal(c.SillyTavern.getContext().chat[1].mes, 'world');
  assert.deepEqual(copy(await c.getChatMessages('-1')), [copy(await c.getChatMessages('1'))[0]]);
  assert.equal((await c.getChatMessages('0-1')).length, 2);
});
test('sync variables queue real persistence in order and retain namespace', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  assert.equal(c.replaceVariables({ count: 1 }), undefined);
  c.updateVariablesWith(value => ({ count: value.count + 2 }));
  c.insertOrAssignVariables({ other: true }, { type: 'script' });
  assert.equal(c.getVariables().count, 3); await c.ElecKoi.flush();
  assert.deepEqual(h.scopes.get('chat-a/chat'), { count: 3 });
  assert.deepEqual(h.scopes.get('chat-a/script'), { other: true });
});
test('persistence failure rolls cache back and flush rejects visibly', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready(); h.fail(true);
  c.replaceVariables({ count: 9 }); await assert.rejects(c.ElecKoi.flush(), /persistence failed/);
  assert.deepEqual(copy(c.getVariables()), {}); assert.equal(h.errors.length, 1);
  h.fail(false); c.replaceVariables({ count: 2 }); await c.ElecKoi.flush(); assert.equal(c.getVariables().count, 2);
});
test('multiple failed queued writes restore the last committed value', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  c.replaceVariables({ count: 1 }); await c.ElecKoi.flush(); h.fail(true);
  c.replaceVariables({ count: 2 }); c.replaceVariables({ count: 3 });
  await assert.rejects(c.ElecKoi.flush(), /persistence failed/);
  assert.equal(c.getVariables().count, 1);
});
test('async variable updater retains its captured chat across a switch', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  let resume; const wait = new Promise(resolve => { resume = resolve; });
  const update = c.updateVariablesWith(async () => { await wait; return { late: true }; });
  h.change('chat-b'); await h.emit('chat.changed', { conversationId: 'chat-b' }); resume(); await update;
  assert.deepEqual(h.scopes.get('chat-a/chat'), { late: true }); assert.deepEqual(copy(c.getVariables()), {});
});
test('late reply commit uses the original chat and native ID', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready(); h.change('chat-b');
  await h.emit('chat.changed', { conversationId: 'chat-b' });
  let committed; c.ElecKoi.events.on('message.committed', value => { committed = value; });
  await h.emit('agent.run.finished', { conversationId: 'chat-a', message: { id: 'chat-a-ai' } });
  assert.deepEqual(copy(committed), { conversationId: 'chat-a', messageId: 'chat-a-ai' });
});
test('before-generation event is local and invokeRaw returns a structured result', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready(); let count = 0;
  c.ElecKoi.events.on('generation.before', () => { count++; });
  await c.__ElecKoiBeforeGeneration({ token: 'g', conversationId: 'chat-a', purpose: 'chat' });
  assert.equal(count, 1);
  assert.equal((await c.ElecKoi.generation.invokeRaw({ prompt: 'extract' })).content, 'generated');
  assert.equal(h.calls.filter(value => value.method === 'generation.invoke').at(-1).params.raw, true);
  assert.equal(c.iframe_events.STREAM_TOKEN_RECEIVED_FULLY, 'js_stream_token_received_fully');
});
test('event priority, once, duplicate registration and cleanup', async () => {
  const { context: c } = host(); await c.ElecKoi.ready(); const order = [];
  const one = () => order.push('one'), two = () => order.push('two');
  c.eventOn('x', one); c.eventOn('x', one); c.eventOn('x', two); c.eventMakeFirst('x', two);
  c.eventOnce('x', async () => { await Promise.resolve(); order.push('once'); });
  await c.eventEmit('x'); await c.eventEmit('x');
  assert.deepEqual(order, ['two', 'one', 'once', 'two', 'one']);
  c.eventClearAll(); await c.eventEmit('x'); assert.equal(order.length, 5);
});
test('normal edit and custom metadata use native IDs without generation', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  await c.setChatMessages([{ message_id: -1, message: 'edited', memory: { entities: ['A'] } }]);
  assert.equal(h.snapshots.get('chat-a').messages[1].content, 'edited');
  assert.deepEqual(h.snapshots.get('chat-a').messages[1].metadata.memory, { entities: ['A'] });
  assert.equal(h.calls.filter(call => call.method === 'generation.invoke').length, 0);
});
test('insert renumbers messages and delete is not suffix deletion', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  await c.createChatMessages([{ role: 'system', message: 'context' }], { insert_before: 1 });
  await c.deleteChatMessages('0'); const messages = await c.getChatMessages();
  assert.equal(messages.length, 2); assert.equal(messages[1].native_id, 'chat-a-ai');
});
test('saveChat commits arbitrary extension fields and stale edits fail', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  const context = c.SillyTavern.getContext(); context.chat[1].mes = 'save'; context.chat[1].database = { rows: 4 };
  await context.saveChat(); assert.deepEqual(h.snapshots.get('chat-a').messages[1].metadata.database, { rows: 4 });
  context.chat[1].mes = 'new'; h.snapshots.get('chat-a').messages[1].content = 'other';
  await assert.rejects(context.saveChat(), /stale message/);
});
test('raw generation preserves explicit ordered prompts and does not send chat', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  const result = await c.generateRaw({ ordered_prompts: [{ role: 'system', content: 'raw only' }, { role: 'user', content: 'extract' }], return_reasoning: true });
  assert.deepEqual(copy(result), { text: 'generated', reasoning: 'thought' });
  const request = h.calls.find(call => call.method === 'generation.invoke').params;
  assert.equal(request.raw, true); assert.deepEqual(request.messages, [{ role: 'system', content: 'raw only' }, { role: 'user', content: 'extract' }]);
  assert.equal(h.calls.filter(call => call.send).length, 0);
});
test('before-generation awaits plugin writes, filters and once lifecycle', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  c.ElecKoi.prompt.beforeGeneration(async () => { c.replaceVariables({ ready: true }); });
  c.injectPrompts([{ id: 'memory', role: 'system', content: 'remember', depth: 2, filter: () => true }], { once: true });
  await c.__ElecKoiBeforeGeneration({ token: 't1', conversationId: 'chat-a', purpose: 'chat' });
  const acknowledge = h.calls.findIndex(call => call.method === 'plugins.hookResult');
  assert.ok(acknowledge > h.calls.findIndex(call => call.method === 'variables.writeScope'));
  assert.equal(h.calls.filter(call => call.method === 'prompts.set').at(-1).params.entries[0].depth, 2);
  assert.equal(c.ElecKoi.prompt.preview().length, 0);
});
test('chat switch refreshes before event and leaves previous writes scoped', async () => {
  const h = host(), c = h.context; await c.ElecKoi.ready();
  c.replaceVariables({ first: true }); h.change('chat-b');
  let id; c.eventOn(c.tavern_events.CHAT_CHANGED, value => { id = value; assert.equal(c.SillyTavern.getContext().getCurrentChatId(), value); });
  await h.emit('chat.changed', { conversationId: 'chat-b' });
  assert.equal(id, 'chat-b'); assert.deepEqual(h.scopes.get('chat-a/chat'), { first: true }); assert.deepEqual(copy(c.getVariables()), {});
});
test('host HTTP response preserves status and parsers', async () => {
  const { context: c } = host(); await c.ElecKoi.ready();
  const response = await c.ElecKoi.net.fetch('http://example.test'); assert.equal(response.status, 200); assert.deepEqual(await response.json(), { x: 1 });
});
test('unsupported semantics fail explicitly', async () => {
  const { context: c } = host(); await c.ElecKoi.ready();
  await assert.rejects(c.setChatMessages([{ message_id: 0, role: 'assistant' }]), /role/);
  assert.throws(() => c.injectPrompts([{ id: 'x', content: 'x', depth: -1 }]), /non-negative/);
  await assert.rejects(c.triggerSlash('/unknown'), /Unsupported/);
});
