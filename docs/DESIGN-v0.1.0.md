# AI工具箱 · 设计文档 v0.1.0

* 版本：v0.1.0
* 日期：2026-09-13
* 性质：现状快照 + 变更地图。描述的是**代码此刻是什么样**，不是计划书。
* 纪律：文档不许领先代码。计划只进 §7 开放清单，做完才搬进正文；破坏 §4 契约的改动必须同步改 §4。

## §0 这份文档怎么用

写什么（四类）：**地图**（§2/§3）、**契约**（§4）、**决策及理由**（散注在各节）、**边界**（§1/§7）。

不写什么：对未来的预测、实现细节（读代码）、教程。

日常维护只有两个触发点，成本极低：

1. **加/删工具** → 只更新 §5 工具清单（一行）。
2. **到里程碑** → 更新 §8 诚实两栏。

只有当你的改动**破坏了 §4 里某条契约**时，才需要动 §4 并版本号 +0.1。

## §1 定位与边界

一句话：**「一个木函」式本地工具箱，但统一入口是 AI 聊天——AI 通过 function calling 直接调用手机里的工具。**

性质（v0.1.0 的裁决）：

* 工具 = 本地确定性代码，AI 只负责选工具和填参数，绝不替工具编造结果。
* 单人项目，UI 现阶段是功能脚手架，**后续会整体重做**（用户另行设计）。因此 UI 层的可替换性是硬要求（见 C1/C7）。
* 本项目**不适用**任何「仅 MIT 依赖」之类铁律；选库只看是否最顺手（当前：OkHttp、Compose、zxing，均 Apache-2.0/BSD 系）。

v0.1.0 明确不做：UI 审美打磨、聊天记录持久化、流式输出、消息取消、相机类工具（二维码识别）、需要第三方 API key 的外部数据工具、非 OpenAI 兼容协议、多语言。

## §2 分层地图

```
ui/（ChatScreen · ToolboxScreen · ToolRunScreen · SettingsScreen · ImageOutputActions）+ MainActivity
   ↓ 聊天页只从 ViewModel 的 StateFlow 取数；工具直接操作页例外，直调 ToolRegistry.execute（§3 既定）
ChatViewModel（Agent 循环 · 会话状态 · 供应商状态）
   ↓                                ↓
agent/ToolRegistry              data/LlmClient（唯一网络出口）
   ↓                                ↓
tools/*（10 个工具实现）         data/ProviderStore（供应商配置持久化）
```

| 层 | 位置 | 职责 | 可替换性 |
|---|---|---|---|
| UI | `app/src/main/java/com/selini/aitoolbox/ui/` + `MainActivity.kt` | 四个页面 + 共用图片动作件 + 导航 | **可整体重写**，只依赖 ViewModel 的 StateFlow |
| 会话/编排 | `ChatViewModel.kt` | function calling 循环、消息组装、系统提示词、供应商选择 | 改聊天行为只动这里 |
| 工具注册 | `agent/ToolRegistry.kt` | `ToolDef`/`ToolParam` 定义、给模型生成 JSON Schema、统一执行与兜错 | 稳定，基本不用动 |
| 工具实现 | `tools/TextTools.kt` · `NumberTools.kt` · `MiscTools.kt` | 10 个本地工具 | **加工具只动这里** |
| 网络 | `data/LlmClient.kt` | chat + listModels 两个方法、错误码→中文文案 | 换协议加新方法或新文件，不改调用方 |
| 配置存储 | `data/ProviderStore.kt` | Provider 数据类、预设模板、SharedPreferences 持久化 | |

依赖规则：**箭头只能向下**。`data` 不认识 `agent` 和 `ui`；`tools` 不认识 `ChatViewModel`；`ui` 不直接 import `LlmClient`。唯一例外：`ui` 的工具箱目录与直接操作页（ToolRunScreen）可读 `ToolRegistry` 元数据并直接 `execute`（§3 既定路径）。

## §3 「我想改 X」速查表

| 我想要… | 动哪里 | 不用动 |
|---|---|---|
| **加一个新工具** | `tools/` 对应文件写一个 `ToolDef`，塞进该文件顶部的 `all` 列表 | 注册表、Schema 生成、聊天 UI、工具箱 UI 全部自动生效 |
| 改某个工具对 AI 的说法/参数说明 | 该工具 `ToolDef` 的 `desc` / `ToolParam.desc` 文案 | 工具实现逻辑 |
| 改对话行为（轮数、系统提示词、上下文怎么带） | `ChatViewModel.kt` 的 `runAgent()` / `systemPrompt()` | 工具、网络 |
| 换模型 / 加供应商 / 改错误文案 | `data/LlmClient.kt`；预设模板在 `data/ProviderStore.kt` 的 `ProviderPresets` | 聊天循环 |
| **重做 UI**（计划中） | 整体重写 `ui/` 包 + `MainActivity.kt` | `agent/` `tools/` `data/` `ChatViewModel` 对外暴露的 StateFlow |
| 给工具做「直接操作页」（不经过 AI） | 新增 ui 页面，直接调 `ToolRegistry.execute(def, argsJson, context)`；元数据用 `ToolDef` 里现成的 | 工具实现 |
| 加非 OpenAI 兼容协议（如 Anthropic） | `data/` 新建 adapter，仿照 `LlmClient` 的两个方法签名 | `ChatViewModel` 只需换调用点 |
| 改密钥/配置存储方式 | `data/ProviderStore.kt` | 其余全部 |

## §4 契约（破坏任何一条之前，先改本节）

* **C1 依赖方向**：§2 的箭头只能向下。UI 可整体丢弃重写，上层永远不知道下层的实现细节。
* **C2 工具协议**：每个工具 = 一个 `ToolDef`（给 AI 的 `name`/`desc`/`params` + 给 UI 的 `title`/`emoji`/`summary`/`example` + 执行函数）。执行结果**必须**是带 `"ok"` 字段的 JSON；`ok=false` 时错误信息会原样回传给模型让它自纠参数重试。新工具不许弹 toast、不许直接改 UI。
* **C3 网络唯一出口**：所有 HTTP 只发生在 `data/LlmClient`。任何层不得私自发请求（包括工具——现阶段工具全部纯本地）。
* **C4 Agent 循环**：上限 6 轮（`MAX_ROUNDS`）；`tool_calls` 必须与 `role=tool` 消息按 `tool_call_id` 配对回传；重建历史时只带 user/assistant 文本（工具轮次不持久化，这是已知取舍）。失败兜底：任何异常转成聊天气泡里的可读中文，App 不崩。
* **C5 供应商协议**：一切供应商按 OpenAI 兼容处理——`{base}/chat/completions` + `{base}/models`。`/models` 不存在的服务商允许手动填模型名。模型名以拉取到的列表为准，**不许硬编码预填过时模型**（v0.1 踩过：deepseek-chat 已退役）。
* **C6 密钥边界**：API Key 只存本机 SharedPreferences，不进仓库、不进日志、不上传。
* **C7 UI 数据供给**：UI 只消费 ViewModel 暴露的 `StateFlow`（items/busy/draft/providers/selectedId），单向流；UI 不持有业务状态。

## §5 工具清单（v0.1.0 = 10 个）

| name | 文件 | 一句话 |
|---|---|---|
| generate_password | TextTools.kt | 密码学安全随机密码，可控字符类与长度 |
| encode_decode | TextTools.kt | Base64/URL 编解码，MD5/SHA1/SHA256 摘要 |
| format_json | TextTools.kt | JSON 校验 + 缩进美化 |
| amount_to_chinese | TextTools.kt | 金额转人民币大写（0~1万亿） |
| convert_unit | NumberTools.kt | 长度/重量/温度/数据/速度/面积，中英文单位 |
| convert_base | NumberTools.kt | 2~36 任意进制整数转换 |
| random_number | NumberTools.kt | 范围随机整数，可去重（骰子/抽签） |
| generate_qr_code | MiscTools.kt | 文本→二维码 PNG，聊天内嵌显示（`image_base64` 约定） |
| date_calc | MiscTools.kt | 今天/加减天数/日期间隔/星期几 |
| get_device_info | MiscTools.kt | 品牌/型号/系统/屏幕/CPU/电量（无权限） |

加工具流程：写 `ToolDef` → 进 `all` → 跑通 → 在本表加一行。完事。

## §6 数据与存储

* SharedPreferences `"providers"`：`{"selected": id, "providers": [{id,name,baseUrl,apiKey,model}…]}`。
* 聊天记录：仅内存，进程死即失（v0.1 已知取舍，见 §7）。
* 权限：`INTERNET`；另有 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion=28`，仅 Android 9 及以下在点「保存到相册」时运行时申请；Android 10+ 走 MediaStore 免权限）。「分享」经 FileProvider 暴露缓存目录文件，无权限。10 个工具本身仍全部零权限。

## §7 已知债务与开放清单（◆ 未裁决 / 未开工）

* ◆ Markdown 渲染（AI 回复里的 `**粗体**` 现在原样显示）→ 归入 UI 重做。
* ◆ 聊天记录持久化（SQLite/文件）与跨进程恢复。
* ◆ 流式输出（SSE）与「停止生成」按钮。
* ◆ 二维码识别（要相机/相册权限，第一个带权限的工具，需先立权限纪律）。
* ◆ 外部数据类工具（天气/快递/汇率）：第一个带第三方 key 的工具，会冲击 C3 的表述，动工前先改契约。
* ◆ Anthropic 协议 adapter（Claude 系端点）。
* ◆ 真机发行：签名配置、应用图标、minSdk 复核（当前 26）。

## §8 诚实两栏（与仓库保持一致，里程碑时更新）

* **已实现（2026-09-13）**：多供应商配置（URL+Key+拉取模型列表+点选）、10 个本地工具、function calling 循环（自纠重试、6 轮上限）、二维码聊天内嵌、错误中文兜底、模拟器端到端验证（deepseek-flash）、聊天空状态引导、二维码保存/分享、工具直接操作页（表单按 ToolDef 自动生成，不经过 AI）。
* **未实现**：§7 全部。

---

## 变更记录

* v0.1.0（2026-09-13）：初版，对应最小 MVP 跑通状态。
* v0.1.0 修订（2026-09-13 晚）：项目文件曾被工作区意外清理，全量恢复（内容不变），并在 `D:\dev\aitoolbox-backup` 留安全副本。
* v0.1.0 修订（2026-09-13 晚，之二）：聊天空状态首次引导（一句人话 + 3 个示例入口，点按填草稿不代发）；二维码卡片就地「保存到相册 / 分享」；§6 权限清单同步。依据 `docs/UI-UX-设计原则-草案.md` 第二节「待做」。
* v0.1.0 修订（2026-09-13 晚，之三）：双模式落地——点工具箱卡片从「把示例填进聊天草稿」改为「打开直接操作页」（ToolRunScreen，参数表单按 ToolDef.params 自动生成；图片结果复用 ImageActions）；AI 路径保留在聊天入口与页内「让 AI 来」兜底。§7 移除「工具直接操作页」◆；§2 登记 ui→ToolRegistry 例外。
