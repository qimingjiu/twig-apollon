# 工作日志（WORKLOG）

> 协作机制：总控（Kimi）拆任务单 + 验收；ZCode 实现。每单一次只做一件边界清晰的事；
> 验收通过前不派下一单；任何实现不得由总控动手。红线：日志/diff/输出/截图说明中不得出现 apiKey 明文。
> 本文件自身未提交，下次 commit 时一并纳入（`git add -A` 会自动扫到）。

## 固定事实（环境）

- 仓库根：`C:\Users\selini\.zcode\workspace\default\aitoolbox`，分支 `main`
- 远端：`https://github.com/qimingjiu/twig-apollon`（公开仓库；项目代号「阿波罗」是有意命名）
- 区外副本：`D:\dev\aitoolbox.git`（裸仓库）、`D:\dev\aitoolbox-backup`（文件副本）
- 构建：`JAVA_HOME=D:\dev\tools\jdk-17.0.20.1+1`，`gradlew.bat assembleDebug`，SDK 在 `D:/dev/android-sdk`
- 验证设备：模拟器 AVD `aitoolbox`（android-35，常开，emulator-5554），已配置真实可用的 DeepSeek（deepseek-flash）——**E2E 会真实消耗少量 token，测试 prompt 一律用短句**
- 冒烟产物目录：`D:\dev\aitoolbox-smoke\`（截图 + UI dump）
- 已知环境坑：① ZCode 工作区曾被整体清空一次（代码已恢复，四层备份在）；② 本机 git 直连 GitHub 会被重置（Connection reset）——**推送走本地代理 `127.0.0.1:7890`（7897 亦可），命令 `git -c http.proxy=http://127.0.0.1:7890 -c https.proxy=... push origin main`**；远端核验一律走 `https://api.github.com/repos/qimingjiu/twig-apollon/commits?per_page=1`；③ `adb input text` 无法输入中文（系统层 NPE），E2E 用 ASCII 等价句式；④ AOSP 键盘会弹无关权限对话框，点 DON'T ALLOW 即可，或换 GBoard
- 安全备忘：一次还原核对时 apiKey 明文进过 ZCode 本地会话日志（未进 git、未上传），**建议抽空去 DeepSeek 后台轮换该 key**

## 2026-09-13 会话

### 已完成并验收（任务 1–8，全部通过）

| # | 内容 | 提交 |
|---|---|---|
| 1 | git init + .gitignore + 区外裸仓库 | `9c077c7` |
| 2 | 模拟器冒烟四点（空状态引导/直接操作页/二维码保存分享/让AI来兜底） | —（验证单） |
| 3 | 聊天记录本地持久化（200 条上限，ToolCall 剔除 image_base64） | `8ad5f44` |
| 4 | SSE 流式输出 + 停止生成（修显式 null 增量污染的真 bug） | `06ac61d` |
| 5 | 设置页「清空聊天记录」（确认弹窗，文件同步清空） | `0bcc7ca` |
| 6 | 流式增长跟随滚动修复（末条超高补 animateScrollBy） | `4b349fe` |
| 7 | ToolParam.default 元数据 + 表单预填 + 布尔 Switch 化 | `615f881` |
| 8 | days 去预填（条件参数不预填）+ §7 补录两条缓行方向 | `1b8db78` |

### 任务 9（书架 v1）：已验收、已提交、已推送 ✅

- **结论**：验收全部通过（材料见下），用户放行后提交为 `904fdbc`（8 文件 +238/−38，含 WORKLOG.md 首次入库），经本地代理 `127.0.0.1:7890` 推送成功，API 复核远端 HEAD = `904fdbc`。
- **现场核对**（卡死恢复后）：`git status` 与卡死前记录完全一致——7 个文件 +173/−38 恰为任务单允许清单，另有未提交的 WORKLOG.md 自身。未重新实现，直接进入验收。
- **代码抽查**（对照任务单要求 1–4，全过）：
  - `ToolDef` 尾参 `category="其他"`，置于 `run` 之前（尾随 lambda 绑定约束，已写注释）；
  - 10 个工具分类值与任务单映射逐一相符（文本与编码 3 / 数字与计算 4 / 生成与图像 2 / 设备与系统 1）；
  - ToolboxScreen：`expanded` 单值状态实现互斥（点同书脊收拢、点他书脊只开新层）、`animateContentSize`、胶囊 round-robin 三行 LazyRow、书脊深色纯色+竖排分类名+分类色圆章+层板；
  - ToolRunScreen：captionSuffix 三态（必填/留空用默认值/选填），Text 与 Enum 字段统一走它；
  - `openAiSchemas()` 零改动（diff 中无该函数）；DESIGN §4 C2、§5 表加分类列、修订之十已录。
- **验收执行记录**（emulator-5554，纯本地，全部通过）：
  - `gradlew.bat assembleDebug` BUILD SUCCESSFUL；
  - 截图 a `shelf_collapsed.png`：4 层书架收拢态，书脊竖排分类名+分类色圆章+层板（类序为注册序：生成与图像/文本与编码/数字与计算/设备与系统，规格未限定顺序）；
  - 截图 b `shelf_expanded.png`：「数字与计算」展开，右侧 4 胶囊三行（单位换算+日期计算 / 进制转换 / 随机数），round-robin 正确；
  - 截图 c `shelf_open_tool.png`：点「单位换算」胶囊直达直接操作页，必填参数 caption「（必填）」可见；
  - 验证 d（互斥，选「点另一书脊」支路）：展开「数字与计算」后点「文本与编码」，仅新层展开旧层收拢 → `shelf_mutex.png`。附注：进工具页返回后书架全收拢（`remember` 非.saveable，导航重建即复位），静态版无碍，v2 动画时一并考虑；
  - `git log --oneline` 恰 7 个提交（`9c077c7`..`1b8db78`）；直连 `git ls-remote` 未试（按已知坑②直接走 API）：**远端 HEAD = `1b8db78dc8df...`，与本地一致，任务 8 提交确认已推送**（上条 WORKLOG 的未决项就此销项）。
- **交回总控后**：用户裁决通过并下达提交指令 → 提交 `904fdbc` + 代理推送 + API 复核，全链路闭环（详见本节首行）。

### 已裁决记录（勿翻案，除非有新证据）

- 停止生成：内容流出前点停止不插空气泡（busy 复位即反馈）。
- 「已清空」反馈停留设置页：不修（§1 暂缓审美打磨）。
- 流式动画每 delta 重启：不修，实测 40 行无跳跃。
- 条件参数（date/date2/days 类）不预填；default 只标常驻默认值参数。
- 「AI 误导长按保存」systemPrompt 补句：**backlog，下次自然触到 ChatViewModel 时带上，不单开任务**。
- 清空记录时若流式进行中，回复会"复活"：无害边界，不修。
- 书架目录形态：用户拍板书架方案；书脊=极简深色纯色+烫金小字+分类色藏书章（不做纹理/多彩配色）；不做分屏（纵横双滚动轴足够）；v1 静态、v2 再做抽出动画+AI 联动微光（需 ChatViewModel 向 UI 暴露当前执行工具的信号）。

### 待用户裁决 / 缓行方向（§7 已补录两条）

- **新增工具圈定**：用户调查中（候选曾提：做决定/文本行处理/时区换算/BMI；等用户结论）。
- 系统控制类工具组（手电/音量/指南针，免权限已核实）——想要但缓行。
- MCP server 模式（官方 Kotlin SDK 可用）——想要但缓行，动工前单独立项设计。
