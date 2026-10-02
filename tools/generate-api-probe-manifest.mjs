import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const root = new URL('../', import.meta.url);
export async function inventory() {
  const catalog = await readFile(new URL('sdk/author/src/main/java/com/eleckoi/android/sdk/author/AuthorApiCatalog.kt', root), 'utf8');
  const plugin = await readFile(new URL('sdk/author/src/main/java/com/eleckoi/android/sdk/author/plugins/PluginAuthorApi.kt', root), 'utf8');
  const baseline = [...catalog.matchAll(/definition\("([^"]+)"/g)].map(match => match[1]);
  const added = [...plugin.split(').map')[0].matchAll(/"([a-z]+\.[A-Za-z]+)"/g)].map(match => match[1]);
  const internal = new Set(['plugins.bootstrap', 'plugins.runtimeReady', 'plugins.hookResult']);
  const notes = {
    'files.saveText': '手动：导出 JSON，选择保存位置并检查文件；取消应返回 saved=false；不使用剪贴板',
    'messages.regenerate': '手动：测试会话中重新生成 AI 消息，检查轨迹与消息内容',
    'messages.editAndRegenerate': '手动：修改测试用户消息并重生成，检查后续消息',
    'openings.select': '手动：切换真实存在的开场白，确认原生选中状态',
    'chat.selectModel': '手动：选择模型并读回 chat.getModels.current',
    'chat.send': '手动：测试会话发送消息，检查模型回复与事件',
    'input.send': '手动：设置测试输入后发送，检查输入清空及消息',
    'chat.open': '手动：指定已存在 sessionId，检查 chat.current.id 切换',
    'chat.delete': '手动：删除测试会话，检查 chat.list 不再包含它',
  };
  const params = {
    'files.saveText': { name: 'api-test.json', mimeType: 'application/json', text: '{"ok":true}' },
    'storage.get': { key: 'manual-test' }, 'storage.set': { key: 'manual-test', value: { ok: true } },
    'storage.delete': { key: 'manual-test' }, 'storage.sql': { database: 'api-probe.sqlite', statements: [{ sql: 'SELECT 1 AS value' }] },
    'storage.transaction': { database: 'api-probe.sqlite', statements: [{ sql: 'SELECT 1 AS value' }] },
    'network.request': { url: 'https://httpbin.org/get' }, 'variables.readScope': { scope: 'script' },
    'variables.writeScope': { scope: 'script', value: { test: 1 } }, 'settings.set': { value: { test: 1 } },
    'worldbooks.get': { name: '测试世界书' }, 'presets.get': { id: '' },
  };
  const routes = [...baseline.map(name => ({ name, origin: 'baseline' })), ...added.map(name => ({ name, origin: 'new', internal: internal.has(name) }))]
    .map(item => ({ ...item, ...(notes[item.name] ? { manual: notes[item.name] } : {}), ...(params[item.name] ? { params: params[item.name] } : {}) }));
  if (new Set(routes.map(item => item.name)).size !== routes.length) throw new Error('重复宿主接口名称');
  return { version: '0.1.0', routes };
}
export async function generate() {
  const value = await inventory();
  await writeFile(new URL('examples/api-probe/route-manifest.json', root), JSON.stringify(value, null, 2) + '\n');
  console.log(`API probe: ${value.routes.length} routes (${value.routes.filter(item => item.origin === 'new').length} new)`);
}
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) await generate();
