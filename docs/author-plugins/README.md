# Android 插件宿主与酒馆兼容层（preview）

这一版提供可移植插件的宿主能力：独立脚本运行时、消息和变量、插件私有存储、辅助模型生成、生成前处理、Prompt 注入、世界书绑定和 HTML UI。`examples/minimal-memory` 是接口验收脚本，不是完整数据库插件，也不是 Shujuku 移植。

架构为 **共用 JS → ElecKoi 作者 API JSON 协议 → Android Kotlin 宿主**。PC 本轮没有修改生产实现。PC 后续可以复用 `tavern-compat.js`、协议和契约测试，并实现自己的存储/模型/UI 网关。

## 加载和生命周期

ZIP 根目录放 `plugin.json`、entry JS 和可选资源：

```json
{"id":"example","name":"例子","version":"0.1.0","entry":"plugin.js","enabled":true}
```

从聊天左下角菜单进入“插件”，导入 ZIP。也支持包含 `source` 字符串的单文件 JSON。entry 是支持顶层 `await` 的脚本函数，在 SDK 和聊天快照就绪后执行。相对资源使用插件 URL 根目录；第三方模块请预先打包到 entry，当前不提供 npm 安装器。

应用持有每个启用插件的独立 WebView。关闭插件面板、重绘消息、切换聊天不会重启 entry。切聊天会刷新上下文并发送事件；应用进程重启后从注册表恢复启用插件。插件停用、删除和重装前等待保存队列，随后清理监听、UI、Prompt 与注册宏。应用退出后没有额外的系统定时任务。

运行时入口和资源由本地 URL 拦截返回，不依赖外部服务器。HTML 模板中的 `<script>` 不会破坏 entry 的外层解析。UI WebView 使用 `MATCH_PARENT` LayoutParams 和 Compose 确定高度约束；原生 `vh` 可用。

## 正式 API

原有 51 个 API 保留原签名，新增 57 个路由（其中 3 个为内部握手），合计 108 个。新增路由见 `PluginAuthorApi.kt`，类型见 `sdk/author/types/plugins.d.ts`。JSON 调用沿用现有 `apiVersion: "0.1.0"`、request id、`ok/result` 或 `error.code/message`；用 `ElecKoi.app.getCapabilities()` 查询当前宿主路由。

| 命名空间 | 功能 |
| --- | --- |
| `ready()` / `flush()` | 等待上下文；等待酒馆同步写入队列并抛出保存失败 |
| `plugins` | 安装、列表、启停、删除、打开管理页 |
| `messages` | 完整 active branch 读取、普通编辑、插入、指定删除、metadata、文本候选回复 |
| `variables.readScope/writeScope` | message/chat/character/preset/global/script/extension/plugin 结构化变量 |
| `storage.kv` | 每插件键值存储 |
| `storage.sqlite` | 每插件、每数据库名独立的 SQLite 文件，参数化 SQL 与事务 |
| `settings` | 每插件持久化设置 |
| `files.saveText` | 系统文件选择器保存 UTF-8 文本/JSON，返回保存位置/字节数或取消；写入错误会抛出 |
| `generation` | invoke/invokeRaw/start/get/cancel；独立于主聊天的辅助模型调用 |
| `prompt` | inject/remove/preview/beforeGeneration；role、depth、顺序、filter、once、扫描 |
| `net` | 宿主 HTTP、响应状态/正文、取消；浏览器 fetch 仍可使用 |
| `worldbooks` | 命名库 CRUD；global/character/chat 绑定；角色内置库使用 `character:ID` |
| `ui` | panel/settings/message-button/script-button；HTML 页面及跨面板事件 |
| `characters/personas/presets/regex/macros` | 原生资料、预设、正则与插件宏入口 |

作用域键包含会话/角色/预设 ID；script/extension/plugin 还包含插件 ID。结构化插件变量与原有 Agent/MVU 变量状态分开保存。数据库位于应用私有目录 `files/author-plugins`，与宿主 Room 库分离；同名 `memory.db` 不会让两个插件共享文件。

```javascript
await ElecKoi.ready();
const db = await ElecKoi.storage.sqlite.open('memory.db');
await db.execute('CREATE TABLE IF NOT EXISTS memories(content TEXT)');
await db.execute('INSERT INTO memories(content) VALUES(?)', ['Alice 到达高塔']);
const rows = await db.query('SELECT content FROM memories');
```

事务使用 SQL 数组，不支持把 JS 回调传入原生事务：

```javascript
await db.transaction([
  {sql: 'DELETE FROM memories', params: []},
  {sql: 'INSERT INTO memories(content) VALUES(?)', params: ['新的记忆']}
]);
```

## 模型与 Prompt

`invokeRaw` 或 `invoke({raw:true})` 仅发送指定的有序消息，不自动附加角色预设、不写聊天回复。默认选择当前配置，也可以传 `configId` 和 `model`。支持 Chat Completions、Responses、Anthropic Messages 和 Gemini，以及流式 delta/reasoning 和取消。`responseFormat:'json'` 对 Chat Completions、Responses 和 Gemini 配置原生 JSON 模式；Anthropic 当前仍需通过 Prompt 要求 JSON，不保证结构化输出。provider 参数放在 `parameters` 中透传。

```javascript
const result = await ElecKoi.generation.invokeRaw({
  purpose: 'memory-extraction', responseFormat: 'json',
  messages: [{role: 'user', content: '提取下面文字中的事件，返回 JSON：...'}]
});
const data = JSON.parse(result.content);
```

`raw:false` 使用原生角色历史、预设/设定库的投影、关键词/EJS、宏和 prompt 正则，再插入插件注入。不运行 Agent 工具循环；它是辅助模型请求，不等同于主聊天的完整 DSH 回合。`generateRaw` 酒馆入口默认返回文本；`ElecKoi.generation.invokeRaw` 正式入口返回 `{id,content,reasoning,model}`。

后台任务通过 `generation.start(options)` 返回 id，`generation.get(id)` 读取 running/completed/failed/cancelled。任务状态在应用进程内保存。原生长模型/网络调用不受普通 RPC 的 10 秒超时限制。

```javascript
ElecKoi.prompt.beforeGeneration(async ({conversationId, purpose}) => {
  const rows = await db.query('SELECT content FROM memories');
  ElecKoi.prompt.inject([{
    id: 'memory', conversationId, role: 'system', depth: 1,
    content: rows.map(row => row.content).join('\n')
  }], {once: true});
});
```

生成前钩子等待异步监听和写入队列，随后冻结注入快照。once 只消费对应会话的一次生成任务；同一 Agent 回合后续模型步骤沿用快照。depth 从最新 user/assistant 消息倒数，0 为末尾。钩子失败明确阻止该次生成；30 秒超时会报错。钩子内的额外模型请求使用 raw 模式，避免再次进入同一生成前钩子。

主聊天先保存并发布当前用户输入，再执行生成前钩子和世界书扫描。命名世界书扫描绑定库的当前历史及 `should_scan` 注入文本；辅助 `raw:false` 请求还包含本次传入消息，且不把这些消息写入历史。命中条目进入 Agent 上下文快照，在同一回合每次 provider 请求中投影；工具返回不会在回合内重新扫描。原生设定库的 Agent 查询工具继续独立工作。删除命名世界书会同步解除其所有绑定。

独立生成事件 `generation.started/delta/finished/failed` 与主聊天 `message.committed` 分开。提交事件携带原始 `conversationId` 和原生 `messageId`，异步提取应一直使用这些 ID，防止切聊天后写错会话。

## UI 和外部访问

```javascript
await ElecKoi.ui.register({
  id: 'memory-settings', label: '记忆设置', kind: 'settings',
  html: '<!doctype html><html><head></head><body>我的设置</body></html>'
});
await ElecKoi.ui.register({id:'memory', label:'记忆', kind:'message-button',
  onClick: payload => ElecKoi.ui.open('memory-settings')});
```

settings 入口会加入应用设置页，也能从插件管理页打开。message-button 出现在默认 Web transcript 的消息操作栏；自定义前端可通过 `ui.list/open` 自行显示入口。HTML 面板与 entry 使用同一个插件存储命名空间，关闭面板不停止 entry。

`ui.emit(event,payload)` 发送给本插件的逻辑运行时和面板，接收用 `ElecKoi.events.on(event,listener)`。本地 `events.emit` 只通知当前 JS 上下文。按钮回调含原生 messageId。

```javascript
const controller = new AbortController();
const response = await ElecKoi.net.fetch('https://example.com/api', {
  method:'POST', headers:{'Content-Type':'application/json'},
  body:JSON.stringify({query:'memory'}), signal:controller.signal
});
if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
```

## 酒馆兼容范围

`TavernHelper`、常用全局函数与 `SillyTavern.getContext()` 共用一份实现；同步变量和上下文从已就绪快照读取，保存经有序队列提交。SDK 不通过阻塞 WebView 主线程模拟同步 Kotlin RPC。消息使用原生 UUID，酒馆楼层/负索引由 JS 转换。

逐项清单见 [compatibility.csv](compatibility.csv)，冻结源定义和版本见 [upstream-inventory.json](upstream-inventory.json)。清单按 TH、ST context、各层事件分别列出，不将重叠层相加。`partial` 表示有调用入口但不是完整上游语义；`deferred` 表示本轮未实现。

仍有明确差异：

- 酒馆 `getPreset/loadPreset/getPresetNames` 在这里是异步、按原生 ID 工作，预设使用 ElecKoi 数据格式。新代码使用正式 `ElecKoi.presets`；不宣称与 TH 的同步名字接口等价。
- 候选回复支持文本数组和选择，不是 DSH 分支图旋转。消息 metadata 可持久化；酒馆全部 hide/swipe/attachment 行为没有完整映射。
- 命名世界书支持启用、constant/基本关键词、role/order/depth；完整的概率、递归、向量化、时序与酒馆专用插入锚点尚未等价。角色内置库采用 ElecKoi 原生格式，不把 TH 数据结构直接当成原生设定库。
- 正则、音频、角色、宏、Slash 和脚本按钮目前是基础子集；Slash 只支持文档内实现的 /send、/trigger、/gen、/genraw、/echo、/setvar、/getvar，以及注册回调。
- 酒馆服务器路由、扩展安装器、群聊和依赖酒馆 DOM 的 UI/渲染事件未实现；不会伪造已渲染事件。精确 provider token 计数明确抛出未实现错误。

异常以 Promise rejection、console、`eleckoi:plugin-error` 和插件管理页错误显示。未知 API、未支持命令和数据库/HTTP 错误不会返回假成功。沿用原有作者 API 权限与桥接限制，本轮没有另加确认流程。

## 测试与打包

```powershell
node --test tools/test-tavern-compat.mjs tools/test-plugin-projection.mjs
pwsh -File tools/package-memory-example.ps1
pwsh -File tools/package-api-probe.ps1
```

ZIP 输出 `build/plugin-examples/eleckoi-memory-contract-test.zip`。在手机导入后，测试面板可以选提取模型、查看/修改私有表格、检查 100vh。真实聊天完成后自动提取，下一轮注入最近 12 条记忆。详细验证记录见 [validation.md](validation.md)。
