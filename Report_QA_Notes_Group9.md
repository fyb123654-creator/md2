# Report 细致核查（Group 9）

## 1. 我做了什么核查

在未提供课程 rubric/评分细则的前提下，我按“零容错提交”标准做了两类核查：

1) **文档本身质量**：用词/拼写一致性、事实数字一致性、表格/图注/章节逻辑是否自洽。  
2) **与项目仓库的可验证一致性**：对照 GitHub 仓库主页 README、pom.xml（Java/JavaFX/JUnit 版本）、以及部分源码（例如 CardManager 的 106 张牌常量、GameController 的 180 秒回合计时等）进行交叉校验。

> 说明：如果你把课程 rubric 或老师“必须包含的清单”发我，我可以再按 rubric 逐条打勾核对（那才是“完全符合项目全部内容/要求”的最后一步）。

---

## 2. 已发现并已在修订版 DOCX 中修复的错误/不严谨点

以下项目我已经 **直接在修订版报告中改正**（不破坏原排版）：

1) **封面标题一致性**：`Monopoly Deal Cards Game` → `Monopoly Deal Card Game`  
2) **用词/拼写一致性（英式拼写体系）**：  
   - `synchronized` → `synchronised`（2 处）  
   - `Centralizes` → `Centralises`（1 处）  
3) **事实数字校验**：贡献证据表（Table 1）里 `36 commits` → `37 commits`（以仓库主页显示的 commit 计数为准）。  
4) **成员贡献表的可验证性与语法**：  
   - 移除/删改了无法在仓库中直接验证、且容易被评审认为“夸大/不实”的表述（如 `undo/rollback`、`single-player mode`、`exit game functionality` 等）。  
   - 修正 Deng Kaile 行的英语语法并将角色描述更清晰化。  
   - 优化 `renaming/chat/avatar` 的表述为更自然的并列结构。

---

## 3. 与仓库对照后确认“基本一致/无明显冲突”的关键点

这些陈述与仓库 README / pom.xml / 部分源码能对上：

- 技术栈：Java 25、JavaFX 25、Maven、JUnit 5。  
- 牌堆规模：CardManager 中明确以常量校验 **106 张牌**。  
- 回合计时：UI 控制器中存在 **180 秒**限制常量。  
- 测试规模：README 写明 **28 tests / 6 test classes**（与你报告一致）。  
- 分层与包结构：`app/cards/core/model/network/rules/ui` 等结构与报告描述一致。  

---

## 4. 仍建议你最后人工逐条确认的“课程要求项”（需要 rubric 才能 100% 断言）

这部分不是报告“写错”，而是**不同课程/老师要求差异很大**，建议你按 rubric 最后核对：

- 是否必须包含：TOC、字数范围、引用/参考文献格式（APA/IEEE/Harvard）、UML 必须使用某工具导出的原图（Papyrus 等）。  
- 是否需要在报告内明确：运行环境要求（JDK/JavaFX 安装方式）、演示脚本/用例、已知限制（Known Issues）与未来工作（Future Work）格式。  
- 团队贡献证明是否要求：commit 截图/统计图、每人最低 commit 数、或必须链接到 GitHub Insights 页面等。

