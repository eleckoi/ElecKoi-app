await ElecKoi.ready();
async function resource(name, json = false) {
  const response = await fetch(name);
  if (!response.ok) throw new Error(`插件资源 ${name}: HTTP ${response.status}`);
  return json ? response.json() : response.text();
}
const suiteSource = await resource('suite.js');
new Function('window', suiteSource)(window);
const manifest = await resource('route-manifest.json', true);
const panel = await resource('panel.html');
const visual = await resource('visual.html');
const runner = ElecKoiApiProbe.create({ api: ElecKoi, globals: window, manifest,
  progress: state => ElecKoi.ui.emit('probe-progress', state),
  async publish(report) {
    await ElecKoi.storage.kv.set('probe-report', report);
    await ElecKoi.ui.emit('probe-report-updated', { updatedAt: report.updatedAt });
  },
});
runner.restore(await ElecKoi.storage.kv.get('probe-report'));

ElecKoi.events.on('probe-command', async command => {
  const { id, action, options = {} } = command;
  try {
    await ElecKoi.ui.emit('probe-command-accepted', { id });
    let result;
    if (action === 'prepare') result = await runner.prepare();
    else if (action === 'run') { await runner.run(command.group, options); result = { group: command.group, tests: runner.report.tests.length }; }
    else if (action === 'all') {
      for (const group of ['data', 'legacy', 'aliases', 'context', 'read', 'shared', 'model', 'network']) await runner.run(group, options);
      result = { tests: runner.report.tests.length };
    } else if (action === 'invoke') result = await runner.invoke(command.kind, command.name, command.params, command.expected, command.hasExpected);
    else if (action === 'visual') result = await runner.openVisual();
    else if (action === 'manager') result = await runner.openManager();
    else if (action === 'confirm-manager') result = await runner.confirm(['plugins.openManager'], '用户确认插件管理页面可见');
    else if (action === 'clear') result = await runner.clear();
    else throw new Error(`未知测试命令：${action}`);
    const outcome = { id, ok: true, result: result === undefined ? null : result };
    await ElecKoi.storage.kv.set(`probe-command-${id}`, outcome);
    await ElecKoi.ui.emit('probe-command-result', outcome);
  } catch (error) {
    const outcome = { id, ok: false, error: String(error), stack: error.stack, report: runner.report,
      causes: error.errors?.map(value => String(value)) };
    await ElecKoi.storage.kv.set(`probe-command-${id}`, outcome);
    await ElecKoi.ui.emit('probe-command-result', outcome);
    console.error('[API probe]', error);
  }
});
ElecKoi.events.on('probe-visual-visible', async metrics => {
  await runner.confirm(['ui.open'], `用户确认 HTML 面板可见；100vh=${metrics.vh}px，innerHeight=${metrics.innerHeight}px`);
  await ElecKoi.ui.open('api-probe-panel');
});
await ElecKoi.ui.register({ id: 'api-probe-panel', label: 'API 逐项测试', kind: 'panel', html: panel });
await ElecKoi.ui.register({ id: 'api-probe-settings', label: 'API 测试与报告', kind: 'settings', html: panel });
await ElecKoi.ui.register({ id: 'api-probe-message', label: 'API 测试', kind: 'message-button', html: panel });
await ElecKoi.ui.register({ id: 'api-probe-visual', label: 'API / 视口显示测试', kind: 'panel', html: visual });
await runner.init();
