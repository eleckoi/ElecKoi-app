# 验证与兼容范围

日期：2026-10-02。Android 基线为 `62c522bf4ab72501bf86a28b78ae50bfb03e4d97`。PC 宿主实现不在本次改动中。

## 本地回归

| 验证 | 结果 | 主要覆盖 |
| --- | --- | --- |
| JS 契约、测试工具与 Prompt 投影 | 35/35 通过 | 同源 TH/global/ST 入口；变量有序写入/失败回滚/会话隔离；消息普通编辑/插删/metadata；事件顺序/once；生成前等待与 depth；空白首屏；报告状态/导出契约 |
| 既有 DSH provider 投影测试 | 5/5 通过 | 原生插入锚点、当前工具回合、历史和定义更新 |
| TypeScript 声明 | 通过 | `plugins.d.ts` 类型检查 |
| SDK author 单元测试 | 19/19 通过 | 原有路由、能力目录、桥接请求契约 |
| conversation 单元测试 | 286/286 通过 | 宏、角色设定、生成准备、消息编辑和聊天呈现回归 |
| app 单元测试 | 55/55 通过 | 应用服务、架构和运行时插件契约 |
| 相关 engine 单元测试 | 26/26 通过 | 辅助生成、注入快照、消息/世界书投影、Room ledger |
| debug APK 与 instrumentation APK | 构建通过 | 应用、SDK author、conversation 的 Kotlin/资源/DEX 打包 |
| API 35 模拟器 instrumentation | 12/12 通过 | SDK 9 项、conversation 3 项；WHPX 硬件加速、隔离测试应用 |

```powershell
node --test tools/test-roleplay-bootstrap.mjs tools/test-api-probe.mjs tools/test-tavern-compat.mjs tools/test-plugin-projection.mjs
node --test app/src/test/js/agent-session-bridge/request-projection.test.mjs
npx --yes --package typescript tsc --noEmit --skipLibCheck --lib es2022,dom sdk/author/types/plugins.d.ts
.\gradlew.bat :sdk:author:testDebugUnitTest :feature:conversation:testDebugUnitTest :engine:testDebugUnitTest --tests '*Plugin*Test' --tests '*RoomConversationLedgerTest'
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug :feature:conversation:assembleDebugAndroidTest :sdk:author:assembleDebugAndroidTest
```

最后一项单元测试命令只过滤 engine；SDK 和 conversation 跑完整单元集。engine 全套曾有 4 项失败和 13 项跳过，失败项为 `RuntimeGuestLayoutTest` 1 项及 `RuntimePathsSessionLogTest` 3 项；同一未修改基线复跑出现相同失败。相关测试通过不表示 engine 全套通过。

## 已执行的隔离真机测试

测试设备为 Android 16 / API 36。测试应用使用隔离数据，不修改用户已有会话。

- `PluginStoreDeviceTest`：私有 SQLite 重开/隔离、参数化 SQL、失败事务回滚；超过默认 CursorWindow 的多 MB JSON 与 Unicode 分块读取/枚举。
- `PluginRuntimeDeviceTest`：真实 WebView entry、本地资源、消息桥、生成前钩子和 API 测试面板；加载超过 3 MB 的旧报告、180 次连续 KV 读取、导出取消。
- `PluginFileExportDeviceTest`：UTF-8 JSON 实际写入/读回、取消和文件 provider 写入失败；测试文件随后删除。
- `PluginViewportDeviceTest`：同一次调用测量原生 `100vh` 和 `innerHeight`，均非零且相等。
- `PluginMessagesDeviceTest`：Room 上的用户普通编辑、任意插入和指定删除。
- `EmptyTranscriptDeviceTest`：生产 RoleplayWebChatHost、文档、SDK 和 Kotlin 桥在空消息会话完成 bootstrap，首屏 committed 且无 pending RPC。

这些测试使用真实 WebView、SQLite、Room 和桥接；部分业务 gateway 为替身，不等价于完整应用逐项验收。viewport 测试没有覆盖旋转、分屏和键盘交互。

`minimal-memory` 集成测试加载原始插件：真实消息事件读取原生回复 ID，HTTP 请求发送至 MockWebServer，返回 JSON 存入私有 SQLite，下一轮生成前读取并注入 depth=1 的 once 快照。测试还验证重启恢复、UI 清理和重复回复不重复提取。模型服务为本地测试服务，不是外部真实模型。

最终复检另补 `PluginGenerationTimingDeviceTest`，走生产发送、输入校验、Room 持久化和发布流程，在生成前钩子验证当前输入触发 scan_depth=1 的世界书；在请求模型前终止测试。`PluginStoreDeviceTest` 另补删除世界书后全局/角色/会话绑定解除及重开断言。这些新增回归及既有 SDK/空白首屏测试已在 API 35 模拟器执行，12 项全部通过；未使用外部模型。

## 用户导出的 API 报告

最新导出报告按每个 `group::name` 的最后一次结果计为 59 项，58 通过、1 失败；没有仍在 running 的调用。失败为在空会话读取 `messages.current` 返回 `CONTEXT_UNAVAILABLE`，属于该原有接口的预期行为。API probe 0.1.3 已补上明确错误码断言，并用 Node 回归验证不会把其他异常算作成功。该版本的完整真机报告尚未重新导出。

同份报告记录 82 个宿主路由通过、25 个未测、1 个失败；TH/global 56 个通过、11 个未测；ST context 11 个通过、2 个未测。四项真实模型用例和两项网络用例通过，文件选择器导出由用户确认正常。世界书 CRUD/绑定通过，但主 Agent 多步工具回合的完整线上扫描与注入尚未做端到端验收。

## 兼容清单口径

`node tools/generate-plugin-compatibility.mjs` 根据冻结的上游定义生成 `compatibility.csv`。函数存在只算 partial；supported 只用于已验证的特定契约。

| 层 | supported | partial | deferred |
| --- | ---: | ---: | ---: |
| TH/global 179 项 | 16 | 47 | 116 |
| ST context 146 项 | 0 | 21 | 125 |
| TH events 82 项 | 0 | 11 | 71 |
| ST events 104 项 | 0 | 11 | 93 |
| iframe events 9 项 | 0 | 7 | 2 |

这些层重叠，不能相加为支持的独立 API 数。宿主共有 108 个路由，包含原有 51 个、新增 57 个（新增中 3 个为内部握手）；并非所有路由都已在用户设备按完整业务语义验收。完整酒馆生态、Shujuku 移植和 PC 实现不在本次范围内。
