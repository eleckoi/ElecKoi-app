import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';
import { inventory } from './generate-api-probe-manifest.mjs';
const root = new URL('../', import.meta.url);
const source = await readFile(new URL('examples/api-probe/suite.js', root), 'utf8');
const manifest = await inventory();
function fixture(call = async () => null, hooks = {}) {
  const api = { call, compatibility: { helpers: ['example'] }, flush: async () => {} };
  const published = [];
  const context = vm.createContext({ setTimeout, clearTimeout, console, ElecKoi: api });
  vm.runInContext(source, context);
  return { api, context, published, runner: context.ElecKoiApiProbe.create({ api, manifest, publish: async report => published.push(report), ...hooks }) };
}
test('清单与当前 Kotlin 路由完全一致，原有 / 新增 / 内部协议单独统计', async () => {
  const saved = JSON.parse(await readFile(new URL('examples/api-probe/route-manifest.json', root), 'utf8'));
  assert.deepEqual(saved, manifest);
  assert.equal(manifest.routes.length, 108);
  assert.equal(manifest.routes.filter(item => item.origin === 'new').length, 57);
  assert.equal(manifest.routes.filter(item => item.internal).length, 3);
});
test('初始结果全部未测，存在入口不会被标记为通过', () => {
  const { runner } = fixture();
  assert.equal(runner.report.routes.length, 108);
  assert.ok(runner.report.routes.every(item => item.status === 'untested'));
  assert.equal(runner.report.helpers[0].status, 'untested');
});
test('手动调用没有断言只记已调用；错误预期会失败，正确预期才通过', async () => {
  const { runner } = fixture(async () => ({ nested: [1, '中文'] }));
  const row = runner.report.routes.find(item => item.name === 'storage.get');
  await runner.invoke('route', 'storage.get', {}, undefined, false); assert.equal(row.status, 'called');
  await assert.rejects(runner.invoke('route', 'storage.get', {}, {}, true), /期望/); assert.equal(row.status, 'fail');
  await runner.invoke('route', 'storage.get', {}, { nested: [1, '中文'] }, true); assert.equal(row.status, 'pass');
});
test('宿主异常记录原始错误并传播，不能变成成功结果', async () => {
  const { runner } = fixture(async () => { throw new Error('disk is full'); });
  await assert.rejects(runner.invoke('route', 'storage.set', {}, null, true), /disk is full/);
  assert.equal(runner.report.calls[0].ok, false);
  assert.match(runner.report.calls[0].error, /disk is full/);
  assert.equal(runner.report.routes.find(item => item.name === 'storage.set').status, 'fail');
});
test('组内单项失败后继续执行，并保留失败断言', async () => {
  const methods = [];
  const { runner } = fixture(async method => {
    methods.push(method);
    if (method === 'plugins.bootstrap') return { conversationId: 'test', messages: [] };
    if (method === 'app.getInfo') return 3;
    return {};
  });
  await runner.run('read');
  assert.equal(runner.report.tests.length, 22);
  assert.equal(runner.report.tests[0].status, 'fail');
  assert.equal(runner.report.tests[1].status, 'pass');
  assert.equal(runner.report.running, false);
  assert.ok(methods.includes('events.list'));
  assert.match(runner.report.tests[0].error, /期望对象或数组/);
});
test('报告恢复保留未测项，把中断中的测试明确标为不完整', () => {
  const { runner } = fixture();
  runner.restore({ version: '0.1.0', routes: [{ name: 'storage.get', status: 'running', evidence: 'pending' }], tests: [{ status: 'running', name: 'unfinished' }], workspace: { conversationId: 'test' } });
  assert.equal(runner.report.routes.find(item => item.name === 'storage.get').status, 'untested');
  assert.equal(runner.report.tests[0].status, 'fail');
  assert.match(runner.report.tests[0].error, /不完整/);
  assert.equal(runner.report.routes.find(item => item.name === 'generation.invoke').status, 'untested');
});

test('messages.current 只接受空会话的明确上下文错误，已有消息仍须读取成功', async () => {
  for (const [messages, errorCode, expected] of [
    [[], 'CONTEXT_UNAVAILABLE', 'pass'],
    [[], 'NOT_FOUND', 'fail'],
    [[], null, 'fail'],
    [[{ id: 'one', content: 'hello' }], null, 'pass'],
    [[{ id: 'one', content: 'hello' }], 'CONTEXT_UNAVAILABLE', 'fail'],
  ]) {
    const { runner } = fixture(async method => {
      if (method === 'plugins.bootstrap') return { conversationId: 'test', messages };
      if (method === 'messages.current' && errorCode) throw Object.assign(new Error('no message'), { code: errorCode });
      return messages[0] || {};
    });
    await runner.run('read');
    assert.equal(runner.report.tests.find(item => item.name === 'messages.current').status, expected);
    assert.equal(runner.report.routes.find(item => item.name === 'messages.current').status, expected);
  }
});
test('创建会话等待就绪事件，不在 draft 清空的过渡期读取 bootstrap', async () => {
  let current = 'before', listener, stopped = false;
  const bootstrapIds = [];
  const { api, runner } = fixture(async (method, params) => {
    assert.equal(method, 'plugins.bootstrap');
    assert.notEqual(current, null, '过渡期错误读取了上下文');
    bootstrapIds.push(current);
    return { conversationId: params.conversationId || current, characterId: 'char', messages: [] };
  });
  api.events = { on(event, callback) { assert.equal(event, 'chat.changed'); listener = callback; return () => { stopped = true; }; } };
  api.chat = { async create() {
    current = null;
    setTimeout(() => { current = 'after'; listener({ conversationId: 'after' }); }, 10);
    return { accepted: true };
  } };
  const workspace = await runner.prepare();
  assert.deepEqual(bootstrapIds, ['before', 'after']);
  assert.equal(workspace.conversationId, 'after');
  assert.equal(workspace.originalConversationId, 'before');
  assert.equal(stopped, true);
});
test('创建命令被拒绝时传播原因并移除临时监听器', async () => {
  let stopped = false;
  const { api, runner } = fixture(async () => ({ conversationId: 'before', characterId: 'char', messages: [] }));
  api.events = { on: () => () => { stopped = true; } };
  api.chat = { create: async () => ({ accepted: false, message: 'AI 正在生成' }) };
  await assert.rejects(runner.prepare(), /AI 正在生成/);
  assert.equal(stopped, true);
  assert.equal(runner.report.workspace, null);
});
test('ST 同名入口不会借用 TH 的成功状态；undefined 返回能记录而不炸掉', async () => {
  const { context } = fixture();
  const globals = { example: () => undefined, SillyTavern: { getContext: () => ({ example: () => undefined }) } };
  const runner = context.ElecKoiApiProbe.create({ api: { call: async () => null, compatibility: { helpers: ['example'] }, flush: async () => {} }, globals, manifest });
  await runner.invoke('helper', 'example', [], undefined, false);
  assert.equal(runner.report.helpers[0].status, 'called');
  assert.equal(runner.report.contextMethods[0].status, 'untested');
});
test('自包含报告不会经 storage.list 无限嵌套，原始返回值仍用于断言', async () => {
  const returned = { 'probe-report': { calls: [1, 2, 3] }, example: { ok: true } };
  const { runner } = fixture(async () => returned);
  const result = await runner.rpc('storage.list');
  assert.equal(result, returned);
  assert.equal(typeof runner.report.calls[0].result['probe-report'], 'string');
  assert.deepEqual(JSON.parse(JSON.stringify(runner.report.calls[0].result.example)), { ok: true });
});
test('插件 entry 与 HTML 内联脚本可解析；面板资源全部存在', async () => {
  const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
  new AsyncFunction(await readFile(new URL('examples/api-probe/plugin.js', root), 'utf8'));
  for (const file of ['panel.html', 'visual.html']) {
    const html = await readFile(new URL(`examples/api-probe/${file}`, root), 'utf8');
    for (const match of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)) new vm.Script(match[1]);
    for (const match of html.matchAll(/(?:src|href)="([^"]+)"/g)) await readFile(new URL(`examples/api-probe/${match[1]}`, root));
  }
});

test('开始发布失败终止当前项，最终发布明确结束 running 并保留原错误', async () => {
  let publications = 0;
  const snapshots = [];
  const { runner } = fixture(async () => ({ conversationId: 'test', messages: [] }), {
    publish: async report => {
      if (++publications === 1) throw new Error('report write failed');
      snapshots.push(report);
    },
  });
  await assert.rejects(runner.run('read'), /report write failed/);
  assert.equal(runner.report.running, false);
  assert.equal(runner.report.tests[0].status, 'fail');
  assert.match(runner.report.tests[0].error, /report write failed/);
  assert.equal(snapshots.at(-1).running, false);
  assert.equal(snapshots.at(-1).tests[0].status, 'fail');
});

test('所有报告发布都失败时仍清除内存状态，错误包含首尾两次失败', async () => {
  const { runner } = fixture(async () => ({ conversationId: 'test', messages: [] }), {
    publish: async () => { throw new Error('disk full'); },
  });
  await assert.rejects(runner.run('read'), error => {
    assert.equal(error.errors.length, 2);
    assert.ok(error.errors.every(item => /disk full/.test(String(item))));
    return true;
  });
  assert.equal(runner.report.running, false);
  assert.equal(runner.report.activeTest, null);
  assert.ok(!runner.report.tests.some(item => item.status === 'running'));
});

test('RPC 等待期间记录当前接口和开始时间，结束后清除当前调用', async () => {
  let complete;
  const events = [];
  const { runner } = fixture(() => new Promise(resolve => { complete = resolve; }), {
    progress: async state => events.push(state),
  });
  const request = runner.rpc('storage.get');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(runner.report.calls[0].status, 'running');
  assert.equal(runner.report.activeCall.method, 'storage.get');
  assert.equal(events[0].activeCall.method, 'storage.get');
  complete({ ok: true }); await request;
  assert.equal(runner.report.calls[0].status, 'pass');
  assert.equal(runner.report.activeCall, null);
});
