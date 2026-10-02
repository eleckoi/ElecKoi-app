(async () => {
  'use strict';
  const $ = id => document.getElementById(id);
  const labels = { pass: '通过', fail: '失败', running: '运行中', untested: '未测', called: '已调用', manual: '待确认', 'manual-pass': '人工通过' };
  const json = value => JSON.stringify(value, null, 2);
  let report, reading = false, currentCommand = null, progress;
  function renderBusy() {
    const state = progress || report;
    if (!state) return;
    const current = state.activeCall || state.activeTest;
    $('busy').textContent = state.running ? `运行中：${current?.method || current?.name || '准备测试'}${current ? ` · ${Math.floor((Date.now() - current.startedAt) / 1000)}s` : ''}` : '空闲';
  }
  function error(value) { $('error').textContent = String(value); console.error(value); }
  window.addEventListener('error', event => error(event.error || event.message));
  window.addEventListener('unhandledrejection', event => error(event.reason));
  function count(items) {
    const totals = {};
    for (const item of items) totals[item.status] = (totals[item.status] || 0) + 1;
    return Object.entries(totals).map(([status, value]) => `${labels[status] || status} ${value}`).join(' · ');
  }
  function badge(status) { const node = document.createElement('span'); node.className = `status ${status}`; node.textContent = labels[status] || status; return node; }
  function detail(title, value) {
    const node = document.createElement('details'), summary = document.createElement('summary'), pre = document.createElement('pre');
    summary.textContent = title; node.append(summary); node.addEventListener('toggle', () => { if (node.open) pre.textContent = json(value); }); node.append(pre); return node;
  }
  function renderRoutes() {
    if (!report) return;
    const kind = $('surface').value, all = kind === 'route' ? report.routes : kind === 'context' ? report.contextMethods : report.helpers;
    $('counts').textContent = `${all.length} 项：${count(all)}`;
    const filter = $('filter').value.toLowerCase(), status = $('status').value;
    const rows = [];
    for (const item of all.filter(item => (!status || status === item.status) && json(item).toLowerCase().includes(filter))) {
      const tr = document.createElement('tr'), name = document.createElement('td'), state = document.createElement('td'), info = document.createElement('td'), button = document.createElement('button'), source = document.createElement('small');
      button.textContent = item.name; source.textContent = item.internal ? '内部协议' : item.origin === 'new' ? '本次新增' : item.origin === 'baseline' ? '原有宿主' : kind === 'context' ? 'ST context' : 'TH / global';
      button.onclick = () => { $('kind').value = kind; $('method').value = item.name; $('params').value = json(kind === 'route' ? item.params || {} : []); $('expected').value = ''; $('method').scrollIntoView({ behavior: 'smooth', block: 'center' }); };
      name.append(button, source); state.append(badge(item.status)); info.textContent = item.evidence || (item.plan?.join('\n')) || item.manual || '未配置自动用例：可在控制台填写参数与预期 JSON';
      tr.append(name, state, info); rows.push(tr);
    }
    $('routes').replaceChildren(...rows);
  }
  function render() {
    if (!report) return;
    renderBusy();
    $('workspace').textContent = report.workspace ? `测试会话：${report.workspace.conversationId}；原会话：${report.workspace.originalConversationId}` : '尚未创建测试会话';
    for (const button of document.querySelectorAll('[data-group],[data-action="prepare"],[data-action="all"],[data-action="clear"],#invoke')) button.disabled = report.running || !!currentCommand;
    renderRoutes(); $('test-counts').textContent = count(report.tests);
    $('tests').replaceChildren(...report.tests.slice().reverse().map(test => detail(`${labels[test.status]} · ${test.group} · ${test.name} (${test.ms ?? '…'}ms)`, test)));
    $('calls').replaceChildren(...report.calls.slice().reverse().map(call => detail(`${call.status === 'running' ? '运行中' : call.ok ? '返回' : '错误'} · ${call.method} · ${call.ms ?? '…'}ms`, call)));
  }
  function metrics() {
    const probe = document.createElement('div'); probe.style.cssText = 'position:fixed;left:0;top:0;height:100vh;width:1px;visibility:hidden;pointer-events:none';
    document.documentElement.append(probe); const vh = probe.getBoundingClientRect().height; probe.remove();
    $('viewport').textContent = `100vh=${vh}px · innerHeight=${innerHeight}px · clientHeight=${document.documentElement.clientHeight}px · WebView ${navigator.userAgent.match(/Chrome\/([\d.]+)/)?.[1] || '未知'}`;
  }
  async function complete(value) {
    if (value.id !== currentCommand) return;
    currentCommand = null;
    progress = null;
    if (value.report) { report = value.report; render(); }
    $('command-state').textContent = value.ok ? '命令已完成；每项结果见下方报告' : '命令失败';
    if (!value.ok) error(value.error + (value.causes ? '\n' + value.causes.join('\n') : ''));
    const { report: snapshot, ...outcome } = value;
    $('output').textContent = value.ok ? json(value.result) : json(outcome);
    render();
    // Clear panel state before acknowledging, so an acknowledgement error cannot leave it busy.
    await ElecKoi.storage.kv.delete(`probe-command-${value.id}`);
    if (!snapshot) await refresh();
  }
  async function refresh() {
    if (reading) return;
    reading = true;
    try {
      const value = await ElecKoi.storage.kv.get('probe-report');
      if (value && value.updatedAt > (report?.updatedAt || '')) {
        report = value; progress = null; render();
        if (report.error) error(report.error);
      }
      if (currentCommand) { const outcome = await ElecKoi.storage.kv.get(`probe-command-${currentCommand}`); if (outcome) await complete(outcome); }
      metrics();
    } finally { reading = false; renderBusy(); }
  }
  function options() { return { configId: $('config').value, model: $('model').value, networkUrl: $('network-url').value, delayUrl: $('delay-url').value }; }
  async function send(command) {
    if (currentCommand) throw new Error('上一条命令仍在运行');
    $('error').textContent = ''; currentCommand = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    $('command-state').textContent = '正在发送到独立脚本运行时…'; render();
    try { await ElecKoi.ui.emit('probe-command', { id: currentCommand, options: options(), ...command }); }
    catch (failure) { currentCommand = null; render(); throw failure; }
  }
  const act = action => async () => { try { await action(); } catch (failure) { error(failure); } };
  await ElecKoi.ready();
  ElecKoi.events.on('probe-report-updated', act(refresh));
  ElecKoi.events.on('probe-progress', value => { progress = value; renderBusy(); });
  ElecKoi.events.on('probe-command-accepted', value => { if (value.id === currentCommand) $('command-state').textContent = '运行时已接收命令，结果持续更新'; });
  ElecKoi.events.on('probe-command-result', async value => { try { await complete(value); } catch (failure) { error(failure); } });
  for (const button of document.querySelectorAll('[data-group]')) button.onclick = act(() => send({ action: 'run', group: button.dataset.group }));
  for (const button of document.querySelectorAll('[data-action]')) button.onclick = act(() => send({ action: button.dataset.action }));
  $('refresh').onclick = act(refresh);
  $('filter').oninput = renderRoutes; $('surface').onchange = renderRoutes; $('status').onchange = renderRoutes;
  $('invoke').onclick = act(() => {
    const kind = $('kind').value, params = JSON.parse($('params').value), expectedText = $('expected').value.trim();
    if (kind === 'route' ? !params || Array.isArray(params) || typeof params !== 'object' : !Array.isArray(params)) throw new Error(kind === 'route' ? '宿主参数需要 JSON 对象' : 'TH / ST 参数需要 JSON 数组');
    return send({ action: 'invoke', kind, name: $('method').value, params, hasExpected: !!expectedText, expected: expectedText ? JSON.parse(expectedText) : undefined });
  });
  $('export').onclick = act(async () => {
    await refresh(); if (!report) throw new Error('报告尚未就绪');
    const result = await ElecKoi.files.saveText({ name: `eleckoi-api-report-${Date.now()}.json`, mimeType: 'application/json', text: json(report) });
    $('command-state').textContent = result.saved ? `JSON 文件已保存（${result.bytes} 字节）` : '已取消文件保存';
    $('output').textContent = json(result);
  });
  const models = await ElecKoi.chat.getModels();
  for (const config of models.items) { const option = document.createElement('option'); option.value = config.configId; option.textContent = `${config.name} (${config.defaultModel})`; $('config').append(option); }
  $('config').value = models.current.configId;
  await refresh(); window.addEventListener('resize', metrics);
  setInterval(() => { if (!document.hidden) refresh().catch(error); }, 1500);
  setInterval(renderBusy, 1000);
})().catch(error => { document.getElementById('error').textContent = String(error); console.error(error); });
