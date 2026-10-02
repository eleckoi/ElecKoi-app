(function exposeProbe(global) {
  'use strict';
  const copy = value => value === undefined ? null : JSON.parse(JSON.stringify(value));
  const canonical = value => JSON.stringify(value, (_, item) => item && typeof item === 'object' && !Array.isArray(item)
    ? Object.fromEntries(Object.keys(item).sort().map(key => [key, item[key]])) : item);
  function assert(ok, detail) { if (!ok) throw new Error(detail); }
  function equal(actual, expected, label = '返回值不符合预期') {
    assert(canonical(actual) === canonical(expected), `${label}\n期望：${canonical(expected)}\n实际：${canonical(actual)}`);
  }
  async function rejects(action, pattern) {
    try { await action(); } catch (error) { assert(pattern.test(String(error)), `错误不符合预期：${error}`); return; }
    throw new Error('期望调用失败，但调用成功');
  }
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  async function until(read, predicate, description, millis = 30000) {
    const deadline = Date.now() + millis;
    let value;
    do { value = await read(); if (predicate(value)) return value; await sleep(100); } while (Date.now() < deadline);
    throw new Error(`${description}：等待 ${millis / 1000}s 后仍未满足；最后结果 ${canonical(value)}`);
  }

  function create({ api, globals = global, manifest, pluginId = 'eleckoi-api-probe', publish = async () => {}, progress = async () => {} }) {
    let running = false;
    const groups = new Map();
    const st = globals.SillyTavern?.getContext() || {};
    const report = { version: '0.1.0', updatedAt: '', tests: [], routes: manifest.routes.map(item => ({ ...item, status: 'untested', evidence: '' })),
      helpers: api.compatibility.helpers.map(name => ({ name, status: 'untested', evidence: '' })),
      contextMethods: Object.keys(st).filter(name => typeof st[name] === 'function').map(name => ({ name, status: 'untested', evidence: '' })),
      calls: [], workspace: null, running: false, activeCall: null, activeTest: null, error: null };
    let context, runId;
    const row = (name, type = 'route') => (type === 'route' ? report.routes : type === 'context' ? report.contextMethods : report.helpers).find(item => item.name === name);
    async function emit() { report.updatedAt = new Date().toISOString(); await publish(copy(report)); }
    function mark(names, status, evidence, type = 'route') {
      for (const name of names) { const value = row(name, type); if (value) Object.assign(value, { status, evidence }); }
    }
    async function rpc(method, params = {}) {
      const start = Date.now();
      const call = { method, startedAt: start, status: 'running' };
      report.calls.push(call); report.activeCall = { method, startedAt: start };
      try {
        await progress({ activeCall: report.activeCall, activeTest: report.activeTest, running: report.running });
        const result = await api.call(method, { pluginId, ...params });
        // Do not recursively embed this report in its own storage.list result.
        const recorded = method === 'storage.list' && result && typeof result === 'object'
          ? Object.fromEntries(Object.entries(result).map(([key, value]) => [key,
            key === 'probe-report' || key.startsWith('probe-command-') ? '[测试报告/命令结果，避免报告自嵌套]' : value])) : result;
        Object.assign(call, { ms: Date.now() - start, status: 'pass', ok: true, result: copy(recorded) });
        return result;
      } catch (error) {
        Object.assign(call, { ms: Date.now() - start, status: 'fail', ok: false, error: String(error), code: error.code }); throw error;
      } finally { report.activeCall = null; }
    }
    function add(group, name, methods, action, helpers = []) {
      const entries = groups.get(group) || []; entries.push({ name, methods, action, helpers }); groups.set(group, entries);
    }
    function scope() { return { conversationId: context.conversationId, characterId: context.characterId, presetId: context.presetId }; }
    function requireWorkspace() {
      assert(report.workspace && context.conversationId === report.workspace.conversationId, '先点“创建测试会话”，并留在该测试会话');
    }
    async function withMessages(action) {
      requireWorkspace();
      const ids = [`${runId}-user`, `${runId}-assistant`];
      try {
        const messages = await rpc('messages.insert', { ...scope(), messages: [
          { id: ids[0], role: 'user', content: `用户 ${runId}`, metadata: { probe: true } },
          { id: ids[1], role: 'assistant', content: `回复 ${runId}`, metadata: { probe: true } }] });
        assert(ids.every(id => messages.some(item => item.id === id)), '插入后没有读回原生消息 ID');
        await action(ids, messages);
      } catch (error) {
        try { await cleanup(); } catch (cleanupError) { throw new AggregateError([error, cleanupError], '测试与临时消息清理均失败'); }
        throw error;
      }
      await cleanup();
      async function cleanup() {
        const messages = await rpc('messages.read', scope());
        const remaining = ids.filter(id => messages.some(item => item.id === id));
        if (remaining.length) await rpc('messages.delete', { ...scope(), ids: remaining });
      }
    }

    add('data', '上下文与插件目录', ['plugins.bootstrap', 'plugins.list'], async () => {
      const value = await rpc('plugins.bootstrap');
      assert(typeof value.conversationId === 'string' && Array.isArray(value.messages), 'bootstrap 缺少会话/消息');
      const plugins = await rpc('plugins.list');
      assert(plugins[pluginId]?.id === pluginId && typeof plugins[pluginId].source === 'string', '插件目录没有当前测试插件');
    });
    add('data', 'KV 写入、读取、枚举、删除', ['storage.set', 'storage.get', 'storage.list', 'storage.delete'], async () => {
      const key = `${runId}-kv`, value = { text: '中文\n引号 " 和 emoji 🐳', nested: [1, true, null] };
      try {
        equal(await rpc('storage.set', { key, value }), null);
        equal(await rpc('storage.get', { key }), value);
        equal((await rpc('storage.list'))[key], value);
        equal(await rpc('storage.delete', { key }), 1);
        equal(await rpc('storage.get', { key }), null);
      } finally { await rpc('storage.delete', { key }); }
    });
    add('data', 'SQLite 参数、事务提交与失败回滚', ['storage.sql', 'storage.transaction'], async () => {
      const database = 'api-probe.sqlite';
      const sql = statements => rpc('storage.sql', { database, statements });
      await sql([{ sql: 'CREATE TABLE IF NOT EXISTS probe(id TEXT PRIMARY KEY, value TEXT)' }]);
      try {
        await sql([{ sql: 'INSERT INTO probe VALUES(?,?)', params: [runId, "中文 ' A"] }]);
        equal((await sql([{ sql: 'SELECT value FROM probe WHERE id=?', params: [runId] }]))[0].rows, [{ value: "中文 ' A" }]);
        const committed = await rpc('storage.transaction', { database, statements: [{ sql: 'UPDATE probe SET value=? WHERE id=?', params: ['committed', runId] }] });
        assert(committed[0].changes === 1, '事务没有报告一行修改');
        await rejects(() => rpc('storage.transaction', { database, statements: [
          { sql: 'UPDATE probe SET value=? WHERE id=?', params: ['rolled back', runId] }, { sql: 'INSERT INTO missing_probe_table VALUES(1)' }] }), /missing_probe_table|no such table/);
        equal((await sql([{ sql: 'SELECT value FROM probe WHERE id=?', params: [runId] }]))[0].rows, [{ value: 'committed' }]);
      } finally { await sql([{ sql: 'DELETE FROM probe WHERE id=?', params: [runId] }]); }
    });
    add('data', '插件设置保存并读回', ['settings.get', 'settings.set'], async () => {
      const before = await rpc('settings.get');
      const value = { ...before, probe: { runId, array: [1, 2] } };
      try { equal(await rpc('settings.set', { value }), value); equal(await rpc('settings.get'), value); }
      finally { equal(await rpc('settings.set', { value: before }), before); }
    });
    add('data', '八种变量作用域读写与恢复', ['variables.readScope', 'variables.writeScope'], async () => withMessages(async ids => {
      for (const name of ['message', 'chat', 'character', 'preset', 'global', 'script', 'extension', 'plugin']) {
        const options = { ...scope(), scope: name, messageId: ids[1] };
        const before = await rpc('variables.readScope', options);
        const value = { ...before, [runId]: { scope: name, value: [1, '中文'] } };
        try {
          equal(await rpc('variables.writeScope', { ...options, value }), value, `writeScope(${name})`);
          equal(await rpc('variables.readScope', options), value, `readScope(${name})`);
        } finally { await rpc('variables.writeScope', { ...options, value: before }); }
      }
    }));
    add('data', '消息普通编辑、metadata、文本候选回复与指定删除', ['messages.read', 'messages.insert', 'messages.update', 'messages.metadata', 'messages.swipes', 'messages.delete'], async () => withMessages(async ids => {
      let messages = await rpc('messages.update', { ...scope(), messages: [{ id: ids[0], content: 'edited user', metadata: { probe: { count: 2 } } }] });
      equal(messages.find(item => item.id === ids[0]).content, 'edited user');
      equal(messages.find(item => item.id === ids[0]).metadata.probe, { count: 2 });
      const metadata = { nested: ['中文', runId] };
      equal(await rpc('messages.metadata', { ...scope(), id: ids[1], value: metadata }), metadata);
      equal(await rpc('messages.metadata', { ...scope(), id: ids[1] }), metadata);
      const swipes = [`reply ${runId}`, 'another reply'];
      equal(await rpc('messages.swipes', { ...scope(), id: ids[1], swipes, swipe_id: 1 }), { swipes, swipe_id: 1 });
      messages = await rpc('messages.read', scope());
      equal(messages.find(item => item.id === ids[1]).content, 'another reply');
      await rejects(() => rpc('messages.update', { ...scope(), messages: [{ id: ids[0], expectedContent: 'stale', content: 'wrong' }] }), /其他操作修改|stale/);
      messages = await rpc('messages.delete', { ...scope(), ids: [ids[0]] });
      assert(!messages.some(item => item.id === ids[0]) && messages.some(item => item.id === ids[1]), '指定删除错误地删除了后续回复');
    }));
    add('data', '命名世界书 CRUD 与三个绑定范围', ['worldbooks.list', 'worldbooks.get', 'worldbooks.put', 'worldbooks.delete', 'worldbooks.bind', 'worldbooks.bindings'], async () => {
      requireWorkspace();
      const name = `API-probe-${runId}`, book = { name, entries: [{ uid: 1, enabled: true, content: '测试世界书', strategy: { type: 'constant' }, position: { role: 'system', depth: 1, order: 2 } }] };
      const originals = {};
      try {
        equal(await rpc('worldbooks.put', { name, book }), book);
        equal(await rpc('worldbooks.get', { name }), book);
        assert((await rpc('worldbooks.list')).includes(name), '世界书未出现在列表中');
        for (const binding of ['global', 'character', 'chat']) {
          const options = { ...scope(), scope: binding };
          originals[binding] = await rpc('worldbooks.bindings', options);
          equal(await rpc('worldbooks.bind', { ...options, names: [name] }), [name]);
          equal(await rpc('worldbooks.bindings', options), [name]);
        }
        const updated = { ...book, entries: [{ ...book.entries[0], content: 'updated' }] };
        equal(await rpc('worldbooks.put', { name, book: updated }), updated);
      } finally {
        for (const [binding, names] of Object.entries(originals)) await rpc('worldbooks.bind', { ...scope(), scope: binding, names });
        equal(await rpc('worldbooks.delete', { name }), true);
        assert(!(await rpc('worldbooks.list')).includes(name), '删除后列表仍有测试世界书');
      }
    });
    add('data', 'Prompt 注册与移除（本项不宣称已进入模型请求）', ['prompts.set', 'prompts.remove'], async () => {
      const entries = [{ id: runId, ...scope(), role: 'system', depth: 1, content: 'test', should_scan: true }];
      equal(await rpc('prompts.set', { entries }), null); equal(await rpc('prompts.remove'), null);
    });
    add('data', '注册宏读回与移除', ['macros.register', 'macros.unregister'], async () => {
      const name = `probe_${runId}`;
      const stop = api.macros.register(name, 'macro-value');
      try { await api.flush(); equal(api.macros.substitute(`{{${name}}}`), 'macro-value'); }
      finally { stop(); await api.flush(); }
      equal(api.macros.substitute(`{{${name}}}`), `{{${name}}}`);
    });
    add('data', 'UI 注册、列表与注销（显示另行确认）', ['ui.register', 'ui.list', 'ui.unregister'], async () => {
      const descriptor = { id: runId, label: '临时测试按钮', kind: 'script-button' };
      try {
        equal(await rpc('ui.register', { descriptor }), null);
        const value = (await rpc('ui.list')).find(item => item.id === runId && item.pluginId === pluginId);
        assert(value?.label === descriptor.label, 'UI 注册项未读回');
      } finally { equal(await rpc('ui.unregister', { id: runId }), null); }
      assert(!(await rpc('ui.list')).some(item => item.id === runId && item.pluginId === pluginId), 'UI 注销后仍存在');
    });
    add('data', '跨 WebView 事件回传', ['plugins.emitEvent'], async () => {
      const event = `probe-echo-${runId}`, payload = { value: runId };
      let observed;
      const stop = api.events.on(event, value => { observed = value; });
      try { equal(await rpc('plugins.emitEvent', { event, payload }), null); await until(() => observed, value => !!value, '宿主事件没有回传'); equal(observed, payload); }
      finally { stop(); }
    });
    add('data', '临时子插件安装、启停、恢复与删除', ['plugins.install', 'plugins.setEnabled', 'plugins.remove'], async () => {
      const id = `probe-child-${runId}`;
      const source = `await ElecKoi.storage.kv.set('value', ${JSON.stringify(runId)}); await ElecKoi.ui.emit('probe-child-ready', {value:${JSON.stringify(runId)}});`;
      let readyCount = 0;
      const stop = api.events.on('plugin.event', packet => { if (packet.pluginId === id && packet.event === 'probe-child-ready') readyCount++; });
      try {
        equal(await rpc('plugins.install', { id, manifest: { id, name: 'API 临时子插件', version: '0.1', source, enabled: false } }), true);
        equal((await rpc('plugins.list'))[id].enabled, false);
        equal(await rpc('plugins.setEnabled', { id, enabled: true }), true);
        await until(() => readyCount, value => value === 1, '子插件没有执行 entry');
        equal(await rpc('storage.get', { pluginId: id, key: 'value' }), runId);
        equal(await rpc('plugins.setEnabled', { id, enabled: false }), true);
        equal((await rpc('plugins.list'))[id].enabled, false);
        equal(await rpc('plugins.setEnabled', { id, enabled: true }), true);
        await until(() => readyCount, value => value === 2, '子插件启用后没有重新执行 entry');
      } finally { stop(); equal(await rpc('plugins.remove', { id }), true); }
      assert(!(await rpc('plugins.list'))[id], '删除后子插件仍在目录中');
    });

    add('shared', '角色读写（临时改名后恢复）', ['characters.list', 'characters.read', 'characters.write'], async () => {
      const before = await rpc('characters.read', scope());
      assert((await rpc('characters.list')).some(value => value.id === before.id), '当前角色没有列在目录中');
      const value = { ...before, name: `${before.name} [API test]` };
      try {
        equal((await rpc('characters.write', { ...scope(), character: value })).name, value.name);
        equal((await rpc('characters.read', scope())).name, value.name);
      } finally { equal((await rpc('characters.write', { ...scope(), character: before })).name, before.name); }
    });
    add('shared', '用户资料读写（临时改名后恢复）', ['personas.get', 'personas.set'], async () => {
      const before = await rpc('personas.get'), persona = { ...before, name: `${before.name} [API test]` };
      try { equal(await rpc('personas.set', { persona }), persona); equal(await rpc('personas.get'), persona); }
      finally { equal(await rpc('personas.set', { persona: before }), before); }
    });
    add('shared', '预设读取、选择、修改与恢复', ['presets.list', 'presets.get', 'presets.select', 'presets.update'], async () => {
      const before = await rpc('presets.get', { id: context.presetId });
      assert((await rpc('presets.list')).some(value => value.id === before.id), '当前预设不在列表中');
      equal((await rpc('presets.select', { id: before.id })).id, before.id);
      const name = `${before.name} [API test]`;
      try {
        equal((await rpc('presets.update', { id: before.id, preset: { name } })).name, name);
        equal((await rpc('presets.get', { id: before.id })).name, name);
      } finally { equal((await rpc('presets.update', { id: before.id, preset: { name: before.name } })).name, before.name); }
    });
    add('shared', '正则保存与恢复', ['regex.get', 'regex.set'], async () => {
      const before = await rpc('regex.get', scope());
      assert(Array.isArray(before.character_rules), '正则缺少 character_rules');
      const rule = { id: runId, name: 'API 测试', enabled: false, order: 999, targets: ['AiOutput'], pattern: 'API_PROBE', replacement: 'test', display_only: true, prompt_only: false };
      const rules = { ...before, character_rules: [...before.character_rules, rule] };
      try {
        await rpc('regex.set', { ...scope(), rules });
        assert((await rpc('regex.get', scope())).character_rules.some(value => value.id === runId), '新增测试正则没有读回');
      } finally {
        await rpc('regex.set', { ...scope(), rules: before });
        assert(!(await rpc('regex.get', scope())).character_rules.some(value => value.id === runId), '测试正则没有清理');
      }
    });

    add('model', '独立模型请求与流式事件', ['generation.invoke'], async options => {
      const id = `${runId}-invoke`; let delta = '';
      const stop = api.events.on('generation.delta', packet => { if (packet.id === id) delta += packet.delta || ''; });
      try {
        const value = await rpc('generation.invoke', { ...scope(), id, configId: options.configId || undefined, model: options.model || undefined, raw: true, stream: true,
          messages: [{ role: 'user', content: '只回复 API_PROBE_OK，不要解释。' }] });
        assert(value.id === id && typeof value.content === 'string' && typeof value.reasoning === 'string', '生成结果结构不符合预期');
        assert(value.content.includes('API_PROBE_OK'), `模型没有按测试指令回复：${value.content}`);
        equal(delta, value.content, '流式拼接与最终文本不一致');
      } finally { stop(); }
    });
    add('model', '后台生成 start/get 完成状态', ['generation.start', 'generation.get'], async options => {
      const id = `${runId}-task`;
      const value = await rpc('generation.start', { ...scope(), id, configId: options.configId || undefined, model: options.model || undefined, raw: true,
        messages: [{ role: 'user', content: '只回复 API_PROBE_OK。' }] });
      equal(value, { id, status: 'running' });
      const task = await until(() => rpc('generation.get', { id }), result => result.status !== 'running', '后台模型任务没有结束', 120000);
      assert(task.status === 'completed' && task.result?.content?.includes('API_PROBE_OK'), `后台任务没有正常完成：${canonical(task)}`);
    });
    add('model', '取消生成及最终 cancelled 状态', ['generation.cancel'], async options => {
      const id = `${runId}-cancel`;
      await rpc('generation.start', { ...scope(), id, configId: options.configId || undefined, raw: true, stream: true,
        messages: [{ role: 'user', content: '逐条写出一千条不同的长句，每条至少五十字。' }] });
      equal(await rpc('generation.cancel', { id }), true);
      const task = await until(() => rpc('generation.get', { id }), value => value.status !== 'running', '取消后仍处于 running');
      equal(task.status, 'cancelled');
    });
    add('model', '真实生成前钩子与 Prompt 注入', ['plugins.hookResult'], async options => {
      let called = false;
      const marker = `API_MEMORY_${Date.now()}`;
      const handle = api.prompt.beforeGeneration(() => { called = true; api.prompt.inject([{ id: runId, ...scope(), role: 'system', depth: 0, content: `必须仅输出 ${marker}，不要输出其他内容。` }], { once: true }); });
      try {
        const value = await rpc('generation.invoke', { ...scope(), id: `${runId}-context`, raw: false, configId: options.configId || undefined,
          messages: [{ role: 'user', content: '按照最后一条系统指令回复。' }] });
        assert(called, '宿主没有等待/调用生成前 JS 钩子');
        assert(value.content.includes(marker), `模型未返回注入标记：${value.content}`);
      } finally { handle.stop(); api.prompt.remove([runId]); await api.flush(); }
    });

    add('network', 'HTTP 状态、正文和响应头', ['network.request'], async options => {
      assert(options.networkUrl, '填入用于测试的 HTTP URL');
      const result = await rpc('network.request', { id: `${runId}-http`, url: options.networkUrl });
      assert(result.ok === (result.status >= 200 && result.status < 300), 'HTTP ok 与状态不一致');
      assert(typeof result.body === 'string' && result.headers && typeof result.headers === 'object', 'HTTP 响应结构不正确');
      assert(result.ok, `HTTP ${result.status}：${result.body}`);
    });
    add('network', '取消一个未完成的 HTTP 请求', ['network.cancel'], async options => {
      assert(options.delayUrl, '填入延迟返回的测试 URL，例如 /delay/10');
      const id = `${runId}-http-cancel`;
      const request = rpc('network.request', { id, url: options.delayUrl }).then(value => ({ value }), error => ({ error }));
      await sleep(500);
      equal(await rpc('network.cancel', { id }), true);
      const result = await request;
      assert(result.error, `取消后请求仍成功完成：${canonical(result.value)}`);
    });

    add('aliases', '酒馆事件排序、once、移除与清空', [], async () => {
      const event = `probe-${runId}`, order = [], a = () => order.push('a'), b = () => order.push('b');
      globals.eventOn(event, a); globals.eventOn(event, b); globals.eventMakeFirst(event, b);
      globals.eventOnce(event, () => order.push('once'));
      try {
        await globals.eventEmit(event); equal(order, ['b', 'a', 'once']);
        globals.eventMakeLast(event, b); globals.eventRemoveListener(event, a);
        await globals.eventEmit(event); equal(order, ['b', 'a', 'once', 'b']);
        globals.eventClearEvent(event); await globals.eventEmit(event); equal(order.length, 4);
      } finally { globals.eventClearEvent(event); }
    }, ['eventOn', 'eventOnce', 'eventMakeFirst', 'eventMakeLast', 'eventRemoveListener', 'eventClearEvent', 'eventEmit']);
    add('aliases', '酒馆同步变量写队列与删除', [], async () => {
      const options = { type: 'script' }, before = globals.getVariables(options);
      try {
        globals.replaceVariables({ count: 1 }, options);
        globals.updateVariablesWith(value => ({ ...value, count: value.count + 1 }), options);
        globals.insertOrAssignVariables({ extra: true }, options); globals.insertVariables({ count: 99, other: true }, options);
        equal(globals.getVariables(options), { count: 2, extra: true, other: true });
        equal(globals.deleteVariable('extra', options), true); await api.flush();
        equal(await rpc('variables.readScope', { ...scope(), scope: 'script' }), { count: 2, other: true });
      } finally { globals.replaceVariables(before, options); await api.flush(); }
    }, ['getVariables', 'replaceVariables', 'updateVariablesWith', 'insertOrAssignVariables', 'insertVariables', 'deleteVariable']);
    add('aliases', '酒馆消息负索引、编辑与指定删除', [], async () => {
      requireWorkspace();
      const before = await globals.getChatMessages(); let id;
      try {
        await globals.createChatMessages([{ role: 'assistant', message: runId }]);
        const last = (await globals.getChatMessages('-1'))[0]; id = last.native_id;
        equal(last.message, runId); equal(globals.getLastMessageId(), before.length); equal(globals.getCurrentMessageId(), before.length);
        await globals.setChatMessages([{ message_id: -1, message: 'edited via TH', probe: { ok: true } }]);
        equal((await globals.getChatMessages('-1'))[0].probe, { ok: true });
        await globals.deleteChatMessages('-1'); equal((await globals.getChatMessages()).length, before.length); id = null;
      } finally { if (id) await rpc('messages.delete', { ...scope(), ids: [id] }); }
    }, ['getChatMessages', 'setChatMessages', 'createChatMessages', 'deleteChatMessages', 'getLastMessageId', 'getCurrentMessageId']);
    add('aliases', '酒馆世界书别名和条目更新', [], async () => {
      const name = `Alias-probe-${runId}`;
      try {
        await globals.createWorldbook(name, [{ uid: 1, content: 'old' }]);
        assert((await globals.getWorldbookNames()).includes(name), '酒馆世界书名称没有读回');
        await globals.replaceWorldbook(name, [{ uid: 1, content: 'new' }]);
        equal((await globals.getWorldbook(name))[0].content, 'new');
        await globals.updateWorldbookWith(name, entries => [...entries, { uid: 2, content: 'added' }]);
        await globals.createWorldbookEntries(name, [{ uid: 3, content: 'third' }]);
        assert((await globals.getLorebookNames()).includes(name), '旧世界书名称别名没有读回');
        await globals.deleteWorldbookEntries(name, value => value.uid === 1);
        equal((await globals.getLorebookEntries(name))[0].uid, 2);
        await globals.setLorebookEntries(name, []); equal(await globals.getWorldbook(name), []);
      } finally { await globals.deleteWorldbook(name); }
    }, ['createWorldbook', 'getWorldbookNames', 'replaceWorldbook', 'getWorldbook', 'updateWorldbookWith', 'createWorldbookEntries', 'deleteWorldbookEntries', 'getLorebookNames', 'getLorebookEntries', 'setLorebookEntries', 'deleteWorldbook']);
    add('aliases', '注册宏、基础 Slash 管道及自定义命令', [], async () => {
      const handle = globals.registerSlashCommand('probe_echo', (_, body) => `value:${body}`);
      try { equal(await globals.triggerSlash('/echo a | /probe_echo {{pipe}}'), 'value:a'); equal(await globals.triggerSlashWithResult('/echo b'), 'b'); }
      finally { handle.stop(); }
      assert(typeof globals.getScriptId() === 'string', 'getScriptId 没有返回字符串');
      equal(globals.substituteMacros('{{char}}'), context.characterName);
    }, ['registerSlashCommand', 'triggerSlash', 'triggerSlashWithResult', 'getScriptId', 'substituteMacros']);

    add('aliases', '角色、头像、模型目录与预设读取别名', [], async () => {
      const character = await globals.getCharData();
      equal(character.id, context.characterId);
      equal(await globals.getCharAvatarPath(), character.avatar);
      const preset = await globals.getPreset(); equal(preset.id, context.presetId);
      assert((await globals.getPresetNames()).includes(preset.name), '预设名字未出现在列表中');
      const configs = await rpc('chat.getModels');
      const expected = [...new Set(configs.items.flatMap(config => config.models?.map(model => model.id) || [config.defaultModel]))].filter(Boolean).sort();
      equal(await globals.getModelList(), expected);
    }, ['getCharData', 'getCharAvatarPath', 'getPreset', 'getPresetNames', 'getModelList']);
    add('aliases', '世界书聊天 / 角色 / 全局绑定别名与恢复', [], async () => {
      requireWorkspace();
      const name = `Binding-probe-${runId}`;
      const beforeChat = await globals.getChatWorldbookName(), beforeGlobal = await globals.getGlobalWorldbookNames(), beforeChar = await globals.getCharWorldbookNames();
      try {
        await globals.createWorldbook(name, []);
        await globals.rebindChatWorldbook(name); equal(await globals.getChatWorldbookName(), name);
        await globals.rebindGlobalWorldbooks([name]); equal(await globals.getGlobalWorldbookNames(), [name]);
        await globals.rebindCharWorldbooks({ additional: [name] }); equal(await globals.getCharWorldbookNames(), { primary: `character:${context.characterId}`, additional: [name] });
      } finally {
        await globals.rebindChatWorldbook(beforeChat); await globals.rebindGlobalWorldbooks(beforeGlobal); await globals.rebindCharWorldbooks(beforeChar); await globals.deleteWorldbook(name);
      }
    }, ['getChatWorldbookName', 'rebindChatWorldbook', 'getGlobalWorldbookNames', 'rebindGlobalWorldbooks', 'getCharWorldbookNames', 'rebindCharWorldbooks']);
    add('aliases', '脚本按钮注册与事件名', [], async () => {
      const name = `button-${runId}`;
      try {
        globals.appendInexistentScriptButtons([{ name }]); await api.flush();
        assert((await globals.getScriptButtons()).some(item => item.id === name && item.pluginId === pluginId), '按钮未注册');
        equal(globals.getButtonEvent(name), `plugin:${pluginId}:button:${name}`);
      } finally { await api.ui.unregister(name); }
    }, ['appendInexistentScriptButtons', 'getScriptButtons', 'getButtonEvent']);
    add('aliases', 'Prompt 别名注册 / 移除与写队列', [], async () => {
      try {
        globals.injectPrompts([{ id: runId, role: 'system', depth: 1, content: runId }]); await api.flush();
        equal(api.prompt.preview().find(item => item.id === runId).content, runId);
      } finally { globals.uninjectPrompts([runId]); await api.flush(); }
      assert(!api.prompt.preview().some(item => item.id === runId), 'Prompt 没有移除');
    }, ['injectPrompts', 'uninjectPrompts']);

    add('shared', '预设替换与正则格式化的酒馆别名', [], async () => {
      const preset = await globals.getPreset(), before = await rpc('regex.get', scope());
      try {
        equal((await globals.loadPreset(preset.id)).id, preset.id);
        const name = `${preset.name} [alias test]`;
        equal((await globals.replacePreset(preset.id, { name })).name, name);
        await globals.replaceTavernRegexes([{ id: runId, name: 'alias probe', enabled: true, pattern: 'API_PROBE', replacement: 'MATCHED', order: 0, targets: ['AiOutput'], display_only: true }]);
        assert((await globals.getTavernRegexes()).some(rule => rule.id === runId), '正则别名未返回新增规则');
        assert((await globals.formatAsTavernRegexedString('API_PROBE', 'ai_output', 'display')).includes('MATCHED'), '启用的测试正则未参与格式化');
      } finally { await globals.replacePreset(preset.id, { name: preset.name }); await rpc('regex.set', { ...scope(), rules: before }); }
    }, ['loadPreset', 'replacePreset', 'getTavernRegexes', 'replaceTavernRegexes', 'formatAsTavernRegexedString']);

    add('context', 'ST 上下文 ID、请求头、宏与 Slash 返回值', [], async () => {
      const ctx = globals.SillyTavern.getContext();
      equal(ctx.getCurrentChatId(), context.conversationId); equal(ctx.getRequestHeaders(), { 'Content-Type': 'application/json' });
      equal(ctx.substituteParams('{{char}}'), context.characterName); equal(ctx.substituteParamsExtended('{{char}}'), context.characterName);
      equal(await ctx.executeSlashCommands('/echo context-probe'), { pipe: 'context-probe' });
      const name = `ctx_${runId}`;
      try { ctx.registerMacro(name, 'CTX'); await api.flush(); equal(ctx.substituteParams(`{{${name}}}`), 'CTX'); }
      finally { ctx.unregisterMacro(name); await api.flush(); }
      equal(ctx.substituteParams(`{{${name}}}`), `{{${name}}}`);
    });
    add('context', 'ST 消息直接修改后 saveChat，metadata 与设置持久化', [], async () => withMessages(async ids => {
      await until(() => globals.SillyTavern.getContext().chat.some(item => item.native_id === ids[1]), value => value, 'ST 上下文没有收到新消息');
      const ctx = globals.SillyTavern.getContext(), metadata = copy(ctx.chatMetadata), settings = copy(ctx.extensionSettings);
      try {
        ctx.chat.find(item => item.native_id === ids[1]).mes = 'edited via ST';
        await ctx.saveChat(); equal((await rpc('messages.read', scope())).find(item => item.id === ids[1]).content, 'edited via ST');
        ctx.chatMetadata = { ...metadata, probe: runId }; await ctx.saveMetadata(); equal((await rpc('messages.metadata', scope())).probe, runId);
        ctx.extensionSettings.probe = runId; ctx.saveSettingsDebounced(); await api.flush(); equal((await rpc('settings.get')).probe, runId);
      } finally {
        ctx.chatMetadata = metadata; await ctx.saveMetadata();
        Object.keys(ctx.extensionSettings).forEach(key => delete ctx.extensionSettings[key]); Object.assign(ctx.extensionSettings, settings); ctx.saveSettingsDebounced(); await api.flush();
      }
    }));
    add('context', 'ST setExtensionPrompt 有效条目与移除', [], async () => {
      const ctx = globals.SillyTavern.getContext();
      try { ctx.setExtensionPrompt(runId, 'ST prompt', 1, 1, true, 0); await api.flush(); equal(api.prompt.preview().find(item => item.id === runId).content, 'ST prompt'); }
      finally { api.prompt.remove([runId]); await api.flush(); }
    });

    const oldReads = ['app.getInfo', 'app.getCapabilities', 'context.current', 'variables.getState', 'variables.getConfig', 'openings.list', 'openings.current',
      'messages.list', 'messages.current', 'chat.current', 'chat.list', 'chat.getGenerationState', 'chat.getAgentTrajectory', 'chat.getModels',
      'character.current', 'settingLibrary.current', 'settingLibrary.getSummary', 'audio.getState', 'audio.getPlaylist', 'audio.getSettings', 'input.get', 'events.list'];
    for (const method of oldReads) add('read', method, [method], async () => {
      if (method === 'messages.current' && context.messages.length === 0) {
        try { await rpc(method); } catch (error) {
          assert(error.code === 'CONTEXT_UNAVAILABLE', `空会话错误码不符合预期：${error.code}: ${error}`);
          return;
        }
        throw new Error('空会话应明确返回 CONTEXT_UNAVAILABLE');
      }
      const result = await rpc(method);
      assert(result !== undefined, '没有返回 JSON 值');
      // This is an explicitly labelled shape smoke test, not equivalence with SillyTavern.
      if (!['messages.current', 'openings.current', 'variables.getConfig', 'input.get'].includes(method)) assert(result && typeof result === 'object', `期望对象或数组，实际 ${canonical(result)}`);
    });

    add('legacy', '原有输入框 API 写入、追加、清空与恢复', ['input.get', 'input.set', 'input.append', 'input.clear'], async () => {
      requireWorkspace();
      const before = await rpc('input.get');
      try {
        equal(await rpc('input.set', { text: 'API probe' }), { text: 'API probe' });
        await until(() => rpc('input.get'), value => value.text === 'API probe', '原生输入没有更新');
        equal(await rpc('input.append', { text: ' 中文' }), { text: 'API probe 中文' });
        await until(() => rpc('input.get'), value => value.text === 'API probe 中文', '追加没有更新');
        equal(await rpc('input.clear'), { text: '' });
        await until(() => rpc('input.get'), value => value.text === '', '清空没有更新');
      } finally { await rpc('input.set', before); }
    });
    add('legacy', '原有消息读取与纯文本媒体空列表 / 不存在错误', ['messages.get', 'media.getMessageAttachments', 'media.getMessageAttachment'], async () => withMessages(async ids => {
      await until(() => rpc('messages.list'), value => value.some(message => message.id === ids[1]), '原生消息快照未刷新');
      const message = await rpc('messages.get', { id: ids[1] });
      assert(message.content === `回复 ${runId}` || message.text === `回复 ${runId}`, '原有消息读取内容不符');
      equal(await rpc('media.getMessageAttachments', { messageId: ids[1] }), { items: [] });
      await rejects(() => rpc('media.getMessageAttachment', { messageId: ids[1], attachmentId: 'not-an-attachment' }), /找不到|not_found|NOT_FOUND/i);
    }));

    const contextNames = [
      ['getCurrentChatId', 'getRequestHeaders', 'substituteParams', 'substituteParamsExtended', 'executeSlashCommands', 'registerMacro', 'unregisterMacro'],
      ['saveChat', 'saveMetadata', 'saveSettingsDebounced'], ['setExtensionPrompt'],
    ];
    groups.get('context').forEach((test, index) => { test.contextMethods = contextNames[index]; });
    for (const [group, entries] of groups) for (const test of entries) {
      for (const name of test.methods) { const item = row(name); if (item) (item.plan ||= []).push(`${group}: ${test.name}`); }
      for (const name of test.helpers) { const item = row(name, 'helper'); if (item) (item.plan ||= []).push(`${group}: ${test.name}`); }
      for (const name of test.contextMethods || []) { const item = row(name, 'context'); if (item) (item.plan ||= []).push(`${group}: ${test.name}`); }
    }

    return {
      report, rpc,
      async init() {
        context = await rpc('plugins.bootstrap');
        assert(typeof context.conversationId === 'string' && Array.isArray(context.messages), 'bootstrap 缺少会话 / 消息');
        mark(['plugins.bootstrap'], 'pass', '真实桥接快照含会话及消息');
        equal(await rpc('plugins.runtimeReady'), null); mark(['plugins.runtimeReady'], 'pass', '内部 ready 握手返回 null');
        await emit();
      },
      async prepare() {
        assert(!running, '测试正在运行');
        const before = await rpc('plugins.bootstrap');
        let resolveChanged, timer;
        const changed = new Promise(resolve => { resolveChanged = resolve; });
        // The command is accepted before its draft loads. Bootstrap is unavailable during
        // that transition; wait for the native event emitted only after the new draft is ready.
        const stop = api.events.on('chat.changed', event => {
          if (event.conversationId && event.conversationId !== before.conversationId) resolveChanged(event.conversationId);
        });
        try {
          const result = await api.chat.create({ characterId: before.characterId });
          assert(result?.accepted === true, `创建测试会话被拒绝：${canonical(result)}`);
          const conversationId = await Promise.race([changed, new Promise((_, reject) => {
            timer = setTimeout(() => reject(new Error('创建命令已接受，但 30s 内没有收到新会话就绪事件')), 30000);
          })]);
          const next = await rpc('plugins.bootstrap', { conversationId });
          equal(next.conversationId, conversationId, '就绪事件与新会话快照不一致');
          report.workspace = { conversationId, originalConversationId: before.conversationId, characterId: next.characterId };
          context = next;
          mark(['chat.create'], 'pass', '新会话就绪事件和上下文 ID 一致'); await emit();
          return report.workspace;
        } finally { stop(); clearTimeout(timer); }
      },
      async run(group, options = {}) {
        assert(!running, '测试正在运行'); assert(groups.has(group), `未知测试组：${group}`);
        running = true; report.running = true; report.error = null;
        runId = `probe-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
        let active, failed;
        function markTest(test, status, evidence) {
          mark(test.methods, status, evidence); mark(test.helpers, status, evidence, 'helper');
          mark(test.contextMethods || [], status, evidence, 'context');
        }
        try {
          context = await rpc('plugins.bootstrap');
          if (['aliases', 'context'].includes(group)) await until(() => globals.SillyTavern.getContext().getCurrentChatId(), value => value === context.conversationId, '兼容上下文仍停在旧会话');
          for (const test of groups.get(group)) {
            const item = { name: test.name, group, status: 'running', startedAt: Date.now() }; report.tests.push(item);
            active = { test, item }; report.activeTest = { name: item.name, startedAt: item.startedAt };
            markTest(test, 'running', test.name); await emit();
            try {
              await test.action(options); item.status = 'pass';
              markTest(test, 'pass', test.name);
            } catch (error) {
              item.status = 'fail'; item.error = String(error); item.causes = error.errors?.map(value => String(value));
              markTest(test, 'fail', `${test.name}：${error}`);
            }
            item.ms = Date.now() - item.startedAt; active = null; report.activeTest = null; await emit();
          }
        } catch (error) {
          failed = error; report.error = `测试组中断：${error}`;
          if (active) {
            Object.assign(active.item, { status: 'fail', error: report.error, ms: Date.now() - active.item.startedAt });
            markTest(active.test, 'fail', report.error);
          }
        } finally {
          running = false; report.running = false; report.activeTest = null; report.activeCall = null;
          try { await emit(); }
          catch (error) {
            report.error = `最终报告发布失败：${error}`;
            failed = failed ? new AggregateError([failed, error], '测试中断且最终报告发布失败') : error;
          }
        }
        if (failed) throw failed;
        return copy(report);
      },
      async invoke(kind, name, params, expected, hasExpected) {
        assert(!running, '测试正在运行，结束后可手动调用');
        try {
          let result;
          if (kind === 'route') result = await rpc(name, params);
          else {
            const target = kind === 'context' ? globals.SillyTavern.getContext() : globals;
            assert(typeof target[name] === 'function', `未提供函数：${name}`);
            result = await target[name](...params); await api.flush();
          }
          if (hasExpected) { equal(result, expected); mark([name], 'pass', '手动调用并通过用户填写的 JSON 断言', kind); }
          else mark([name], 'called', '调用成功但未填写返回值断言；不计为通过', kind);
          return result;
        } catch (error) { mark([name], 'fail', `手动测试失败：${error}`, kind); throw error; }
        finally { await emit(); }
      },
      restore(saved) {
        if (!saved || saved.version !== report.version) return;
        for (const key of ['routes', 'helpers', 'contextMethods']) for (const item of report[key]) {
          const previous = saved[key]?.find(value => value.name === item.name);
          if (previous) Object.assign(item, { status: previous.status === 'running' ? 'untested' : previous.status, evidence: previous.evidence });
        }
        report.tests = (saved.tests || []).map(item => item.status === 'running' ? { ...item, status: 'fail', error: '运行时中断，结果不完整' } : item);
        report.calls = saved.calls || []; report.workspace = saved.workspace || null;
      },
      async clear() {
        assert(!running, '测试正在运行');
        for (const list of [report.routes, report.helpers, report.contextMethods]) for (const item of list) Object.assign(item, { status: 'untested', evidence: '' });
        report.tests = []; report.calls = []; report.error = null; await emit();
      },
      async confirm(names, detail) { mark(names, 'manual-pass', detail); await emit(); },
      async openVisual() {
        await rpc('ui.open', { id: 'api-probe-visual' }); mark(['ui.open'], 'manual', '已发出打开请求，等待点击测试页面上的“看得见”'); await emit();
      },
      async openManager() {
        await rpc('plugins.openManager'); mark(['plugins.openManager'], 'manual', '等待人工确认插件管理页可见'); await emit();
      },
    };
  }
  global.ElecKoiApiProbe = { create, equal, assert };
})(typeof window === 'undefined' ? globalThis : window);
