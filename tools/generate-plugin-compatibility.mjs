import { readFile, writeFile } from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../', import.meta.url);
const inventory = JSON.parse((await readFile(new URL('docs/author-plugins/upstream-inventory.json', root), 'utf8')).replace(/^\uFEFF/, ''));
const source = await readFile(new URL('app/src/main/assets/frontend/preview/tavern-compat.js', root), 'utf8');
const snapshot = {conversationId:'test', characterId:'test', characterName:'Role', userName:'User', presetId:'test',
  messages:[], variables:{chat:{}, message:{}}, settings:{}, metadata:{}};
const scope = vm.createContext({console, CustomEvent:class {}, dispatchEvent(){}, addEventListener(){},
  ElecKoi:{call:async()=>snapshot, events:{on:()=>()=>{}}, messages:{}, variables:{}, audio:{}, chat:{}, input:{}}});
scope.window = scope;
vm.runInContext(source, scope);
await scope.ElecKoi.ready();

// Presence is never a proof of complete compatibility. Only audited contracts get `supported`.
const supported = new Set(['getLastMessageId','getVariables','replaceVariables','updateVariablesWith','insertOrAssignVariables','insertVariables','deleteVariable',
  'eventOn','eventOnce','eventMakeFirst','eventMakeLast','eventRemoveListener','eventClearEvent','eventClearAll','eventEmit','getScriptId']);
function restriction(name) {
  if (/Preset/.test(name)) return '异步原生 ID 和 ElecKoi 预设格式；不是 TH 同步名字签名';
  if (/Worldbook|Lorebook/.test(name)) return '命名库基础 CRUD/绑定；原生库格式、关键词/递归/概率/锚点未完整等价';
  if (/generate|Generation|ModelList/i.test(name)) return '辅助生成不运行 Agent 工具；支持主流 provider/stream/cancel；custom_api URL 覆盖未适配';
  if (/Messages|saveChat|chatMetadata|saveMetadata|^chat$/.test(name)) return '真实 active branch/metadata；隐藏和候选回复全部酒馆语义未等价';
  if (/Regex/.test(name)) return 'ElecKoi 正则格式和作用域';
  if (/Audio/.test(name)) return '原生音频格式与频道';
  if (/Macro|substitute/.test(name)) return 'user/char/lastMessageId 和注册宏基础子集';
  if (/Slash/.test(name)) return '基础管道和注册回调；不包含完整酒馆 Slash parser';
  if (/Buttons/.test(name)) return '宿主插件入口；无酒馆 DOM';
  if (/Char/.test(name)) return 'ElecKoi 原生角色格式';
  return '已实现入口；签名/负载与完整酒馆行为未全部等价';
}
const rows = [['layer','interface','status','restriction','verification']];
for (const name of inventory.helperGlobal) {
  const present = Object.hasOwn(scope, name);
  rows.push(['TavernHelper/global', name, !present ? 'deferred' : supported.has(name) ? 'supported' : 'partial',
    !present ? '本轮未实现' : supported.has(name) ? '已审计的基础契约；同步持久化操作需 flush' : restriction(name), present ? 'tools/test-tavern-compat.mjs; validation.md' : '']);
}
const context = scope.SillyTavern.getContext();
for (const name of inventory.stTop) {
  const present = Object.hasOwn(context, name);
  rows.push(['SillyTavern.getContext', name, present ? 'partial' : 'deferred', present ? restriction(name) : '本轮未实现', present ? 'tools/test-tavern-compat.mjs; validation.md' : '']);
}
for (const [layer, names, exposed] of [['TavernHelper.events', inventory.helperEvents, scope.tavern_events], ['SillyTavern.events', inventory.stEvents, scope.tavern_events], ['iframe.events', inventory.iframeEvents, scope.iframe_events]]) {
  for (const name of names) {
    const present = Object.hasOwn(exposed, name);
    rows.push([layer, name, present ? 'partial' : 'deferred', present ? '同名常量及基本分发；事件负载以宿主协议为准' : '未模拟酒馆专用事件', present ? 'tools/test-tavern-compat.mjs; PluginRuntimeDeviceTest' : '']);
  }
}
const csv = rows.map(row => row.map(value => '"' + value.replaceAll('"', '""') + '"').join(',')).join('\n') + '\n';
await writeFile(new URL('docs/author-plugins/compatibility.csv', root), csv);
for (const layer of [...new Set(rows.slice(1).map(row=>row[0]))]) {
  const items = rows.slice(1).filter(row=>row[0]===layer);
  console.log(layer, Object.fromEntries(['supported','partial','deferred'].map(status=>[status,items.filter(row=>row[2]===status).length])));
}
