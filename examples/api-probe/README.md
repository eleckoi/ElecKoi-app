# ElecKoi API 逐项测试插件

这是测试工具，不是数据库业务插件。每个入口独立记录“通过 / 失败 / 未测 / 已调用 / 待人工确认 / 人工通过”，提供原始 RPC 返回值、失败断言和 JSON 报告。函数存在或 RPC 没抛错不会自动算作通过。

## 手机使用

1. 构建并安装本分支的 debug App；对话页左下角菜单 → 插件 → 导入插件。
2. 在下载目录选择 `eleckoi-api-probe.zip`，然后打开“API 逐项测试”。同 ID 再次导入会更新插件。
3. 点“创建独立测试会话”，保持在这个会话。测试消息会在用例完成后清理。
4. 先运行数据、原有输入/消息、酒馆助手别名、ST 上下文和只读组。
5. 角色/用户/预设/正则组会临时写入再恢复原值；模型组选择已配置模型；网络组填写可访问的 HTTP 和延迟响应 URL。
6. HTML 面板与插件管理页需肉眼确认。最后点“导出 JSON”，在 Android 系统文件选择器中选择位置并保存 `.json` 文件；取消会显示“已取消文件保存”，写入失败会显示原始错误，不使用剪贴板。

关闭面板不会停止脚本中的测试。结果存储在测试插件自己的 KV 中，重新打开或重启运行时仍可读取；中断的用例会明确显示结果不完整。清空结果保留测试会话 ID，不删除会话。

当前版本 0.1.3。创建测试会话等待原生 `chat.changed` 就绪后再读取上下文。报告发布失败会结束 running 并保留错误；普通用例失败继续下一项。面板显示当前调用/用例和耗时。空会话的 `messages.current` 必须返回 `CONTEXT_UNAVAILABLE`，其他错误仍判失败；已有消息时必须读取成功。更新测试插件时也应使用本分支的 APK。

## 覆盖口径

清单从 Kotlin 实际路由生成：108 个宿主路由，包含原有 51 个、本次新增 57 个；新增中的 3 个是内部握手协议。TH 全局函数、ST context 函数分开列出，不能相加当作独立宿主能力数量。

当前有自动用例计划的入口：81 个宿主路由、56 个 TH/global 函数、11 个 ST context 函数；此外启动时检查 `plugins.runtimeReady`，创建测试会话时检查 `chat.create`，两个 UI 打开入口由用户确认。自动用例包括：

| 测试组 | 主要断言 |
| --- | --- |
| 数据 / 插件 | KV 精确往返、删除；SQLite 参数、提交、失败回滚；8 种变量 scope；消息普通修改、metadata、swipes、旧版本冲突、指定删除；世界书 CRUD/绑定；Prompt 与宏注册/移除；子插件 entry 执行、启停、重启、删除；事件回传 |
| 原有输入 / 消息 / 媒体 | 输入框设置/追加/清空/恢复；旧消息读取；纯文本媒体空列表、不存在附件错误。后两项不代表已测真实图片/视频解码 |
| TH / global | 消息索引与编辑；同步变量队列；事件排序/once/清理；世界书条目/绑定别名；角色/头像路径/预设/模型目录；Slash 管道；脚本按钮；Prompt 别名 |
| ST context | 会话 ID、请求头、宏、Slash；直接修改 chat 后 saveChat；metadata；设置；setExtensionPrompt |
| 共享资料 | 角色、用户资料、预设、正则的读写与恢复；正则格式化别名 |
| 模型 | invoke 结果结构与指令标记；流式拼接；后台 start/get 完成；取消后的最终 cancelled 状态；生成前等待 JS 钩子并注入唯一标记 |
| 网络 | HTTP 状态、响应头与正文；取消未完成请求后必须失败 |
| 原有只读 | 22 项结构冒烟检查；空会话当前消息检查预期错误码，其余检查返回 JSON 类型 |

模型组最多发出四次请求，使用所选配置，会消耗 API 用量。模型指令标记失败可能来自服务商/模型本身，报告保存实际返回文本以便区分。生成内容不会写入聊天。

没有自动用例的旧接口（如真实音频播放、切开场白、聊天发送/重生成/切换/删除等）保留“未测”。可以点击接口名称，在控制台填参数与预期 JSON 验证；TH/ST 参数为参数数组，宿主为参数对象。控制台不提供函数序列化，涉及回调的功能使用内置测试用例或扩展 `suite.js`。ST `getTokenCount` 在当前实现明确报不支持，不会伪造 token 数。

API 路由行记录最近一次测试结果；历史失败保留在用例记录。完整报告不能仅凭“当前路由都绿”判断兼容全部上游语义。此次插件是执行这些检查的工具，不表示所有列出的接口已经在用户环境通过。

## 开发与验证

```powershell
node tools/generate-api-probe-manifest.mjs
node --test tools/test-api-probe.mjs tools/test-tavern-compat.mjs tools/test-plugin-projection.mjs
powershell -ExecutionPolicy Bypass -File tools/package-api-probe.ps1
```

ZIP 入口、HTML、CSS、JS、路由清单全部位于 ZIP 根目录，生成在 `build/plugin-examples/eleckoi-api-probe.zip`。脚本运行时负责执行；面板仅发送命令与展示报告，因此关闭面板不会丢失运行任务。

隔离手机 instrumentation 使用真实 Android WebView、原生消息桥和 PluginStore，加载这些原始资源，检查启动、报告、KV 成功/失败断言、180 次连续 KV 读取、面板按钮往返与导出取消。面板复用生产代码 `AuthorPluginPanelWebView` 的 SDK 注入和本地资源加载；业务 gateway 使用测试替身。文件测试另外验证实际 JSON 文件 UTF-8 写入、读回、取消与写入失败。系统文件选择器的实际交互仍需用户确认；这些测试不能替代整个 AuthorPluginService 的 108 项真机逐项验证。
