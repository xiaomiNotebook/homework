# 小米便签项目 Git 分支使用说明

适用仓库：<https://github.com/xiaomiNotebook/homework>

本说明面向小组全体成员。第一次操作前请先通读，尤其注意：**每个人只能在自己的 `member-*` 分支上开发，不要直接修改 `master`、`develop` 或 `main`。**

## 1. 分支用途

| 分支 | 用途 | 谁可以提交 |
|---|---|---|
| `main` | 建仓时的历史分支，保留但不用于日常开发 | 不提交 |
| `master` | 最终稳定版本、阶段成果和交付版本 | 组长通过 Pull Request 合并 |
| `develop` | 六个人的代码集成、联调和测试分支 | 通过 Pull Request 合并 |
| `member-1` | 1 号成员的个人工作分支 | 1 号成员 |
| `member-2` | 2 号成员的个人工作分支 | 2 号成员 |
| `member-3` | 3 号成员的个人工作分支 | 3 号成员 |
| `member-4` | 4 号成员的个人工作分支 | 4 号成员 |
| `member-5` | 5 号成员的个人工作分支 | 5 号成员 |
| `member-6` | 6 号成员的个人工作分支 | 6 号成员 |

请在小组群里明确每个人对应的编号，确定后不要互换。

工作流如下：

```text
member-1 ─┐
member-2 ─┤
member-3 ─┤
member-4 ─┼── Pull Request ──> develop ── 测试通过 ──> master
member-5 ─┤
member-6 ─┘
```

## 2. 第一次使用

### 2.1 安装并配置 Git

安装 Git 后，打开 Git Bash、PowerShell 或 VS Code 终端，设置自己的姓名和 GitHub 邮箱：

```bash
git config --global user.name "你的姓名"
git config --global user.email "你的 GitHub 邮箱"
```

姓名建议使用真实姓名或小组统一格式。不要把密码、访问令牌写进仓库文件。

### 2.2 下载仓库

进入自己准备存放项目的目录，执行：

```bash
git clone https://github.com/xiaomiNotebook/homework.git
cd homework
```

如果电脑提示登录 GitHub，请按浏览器提示登录自己的 GitHub 账号。

### 2.3 切换到个人分支

先确认组长分配给自己的编号。以下以 `member-1` 为例：

```bash
git fetch origin
git switch member-1
git branch --show-current
```

最后一条命令必须显示 `member-1`。其他成员把命令中的编号改成自己的编号。

如果显示 `master` 或 `develop`，不要开始修改文件，先切换回个人分支。

## 3. 每次开始工作前

先让自己的分支吸收 `develop` 中其他人已经合并的更新：

```bash
git fetch origin
git switch member-1
git merge origin/develop
```

将 `member-1` 换成自己的分支名。

如果显示 `Already up to date.`，说明已经是最新状态。如果出现冲突且自己不会处理，执行下面的命令取消本次合并，并联系组长：

```bash
git merge --abort
```

不要在不理解后果的情况下运行 `git reset --hard`、`git clean -fd` 或强制推送。

## 4. 仓库目录与文件放置规则

### 4.1 完整目录用途

```text
homework/
├── src/
│   ├── original/                   # 官方原始提交的只读快照
│   └── maintained/                 # 全组唯一工作代码：先标注，后维护和优化
├── doc/
│   ├── annotations/                # 六人的代码阅读与标注过程材料
│   │   ├── member-1/ ... member-6/
│   ├── report-reading-maintenance/ # 《开源代码阅读、标注和维护报告》
│   ├── report-quality/             # 《开源软件质量分析报告》和工具原始结果
│   ├── ppt/                        # PPT、演示脚本和答辩题库
│   ├── UML/                        # 用例图、包图、类图、顺序图的源文件和导出图
│   ├── screenshots/                # 最终报告和 PPT 选用的公共截图与索引
│   ├── testing/                    # 全组单元测试、手工测试和回归测试汇总
│   └── evidence/                   # 六项维护任务的个人设计、测试和前后截图证据
│       ├── member-1/ ... member-6/
└── README.md                       # 项目来源、目录、构建、分支和成果导航
```

`src/original` 是官方源码基线，只用于比较、质量分析和保留版权，任何成员都不得在这里做日常修改。

`src/maintained` 初始内容与官方源码相同，是代码标注和后续六项维护的唯一代码工作目录。构建项目时把 `src/maintained` 当作项目根目录，其中包含 `AndroidManifest.xml`、`res/` 和真正的 Java 源码目录 `src/net/micode/notes/`。

`doc/annotations/member-N` 保存代码标注过程材料；`doc/evidence/member-N` 保存 M1—M6 维护功能的设计、测试和截图。两个目录用途不同，不要混放。

### 4.2 六个人的代码标注范围

下表路径均相对于 `src/maintained/src/net/micode/notes/`。每个人只标注自己分配的文件，不得借标注任务修改程序行为。

| 成员分支 | 必须阅读并标注的文件 | 计划规模 |
|---|---|---:|
| `member-1` | `data/*.java`；`model/Note.java`；`tool/DataUtils.java`；`tool/ResourceParser.java` | 7 个文件，约 1522 行 |
| `member-2` | `ui/FoldersListAdapter.java`；`ui/NoteItemData.java`；`ui/NotesListActivity.java`；`ui/NotesListAdapter.java`；`ui/NotesListItem.java` | 5 个文件，约 1382 行 |
| `member-3` | `model/WorkingNote.java`；`ui/NoteEditActivity.java`；`ui/NoteEditText.java`；`widget/*.java` | 6 个文件，约 1472 行 |
| `member-4` | `gtask/data/*.java`；`tool/GTaskStringUtils.java` | 7 个文件，约 1347 行 |
| `member-5` | `gtask/remote/*.java`；`gtask/exception/*.java` | 6 个文件，约 1462 行 |
| `member-6` | `tool/BackupUtils.java`；`ui/AlarmAlertActivity.java`；`ui/AlarmInitReceiver.java`；`ui/AlarmReceiver.java`；`ui/DateTimePicker.java`；`ui/DateTimePickerDialog.java`；`ui/DropdownMenu.java`；`ui/NotesPreferenceActivity.java` | 8 个文件，约 1416 行 |

这些行数是根据当前基线做的工作量参考，不是最终报告数据。最终注释行数必须在标注完成后从冻结版本重新统计。

### 4.3 每个人的标注结果放在哪里

代码注释直接写进本人负责的 `src/maintained/.../*.java` 文件。与此同时，每个人必须在自己的 `doc/annotations/member-N/` 下完成：

```text
01-scope-and-reading.md    # 文件清单、模块职责、关键流程和疑问
02-function-class-map.md   # 功能—包—类—方法映射
03-annotation-log.md       # 逐文件注释位置、目的和真实统计
04-peer-review.md          # 复核意见、修订结果、构建结果和 PR 链接
```

注释应解释职责、设计意图、前置条件、生命周期、边界、异常路径和不容易看懂的算法，不要逐句翻译代码。发现缺陷时创建 Issue 记录，不要夹带到标注 PR 中修复。

复核关系固定为：成员 1 复核成员 6；成员 2 复核成员 1；成员 3 复核成员 2；成员 4 复核成员 3；成员 5 复核成员 4；成员 6 复核成员 5。

### 4.4 修改文件时的注意事项

1. 不要修改或删除 `src/original` 中的 `NOTICE`、原始 README 和版权声明。
2. 所有代码注释、维护和优化都在 `src/maintained` 完成。
3. 标注任务只加注释和过程文档，不改变代码行为；功能修改留到维护任务。
4. 不要提交密码、访问令牌、个人隐私、编译缓存和大型临时文件。
5. 修改核心文件前先在群里说明，避免两个人同时大改同一个文件。

## 5. 查看并提交自己的修改

### 5.1 查看修改状态

```bash
git status
git diff
```

确认只包含本次任务相关文件。

### 5.2 将文件加入待提交区

推荐按文件或目录添加：

```bash
git add src/maintained/文件名
git add doc/annotations/member-1/文件名
git add doc/evidence/member-1/文件名
git add doc/testing/文件名
```

也可以在确认所有修改都应提交后使用：

```bash
git add .
```

随后检查将要提交的内容：

```bash
git diff --staged
```

如果误添加了某个文件，可以取消暂存，但不会删除文件内容：

```bash
git restore --staged 文件名
```

### 5.3 创建提交

```bash
git commit -m "类型: 简短说明"
```

建议使用以下类型：

| 类型 | 用途 | 示例 |
|---|---|---|
| `feat` | 新增功能 | `feat: 实现便签复制功能` |
| `fix` | 修复问题 | `fix: 修复无搜索结果时崩溃` |
| `docs` | 代码标注或文档 | `docs: 完成列表模块代码标注` |
| `test` | 增加或修改测试 | `test: 增加便签删除测试记录` |
| `refactor` | 重构代码 | `refactor: 整理便签查询逻辑` |
| `chore` | 目录或配置维护 | `chore: 整理截图目录` |

一次提交尽量只完成一类事情。不要使用“修改一下”“最终版”“123”等无法说明内容的提交信息。

### 5.4 推送到自己的远程分支

以 `member-1` 为例：

```bash
git push origin member-1
```

其他成员替换为自己的分支名。推送前可再次执行 `git branch --show-current`，避免推错分支。

## 6. 在 GitHub 发起 Pull Request

代码推送成功后：

1. 打开 <https://github.com/xiaomiNotebook/homework>。
2. 进入 **Pull requests**，点击 **New pull request**。
3. `base` 选择 `develop`。
4. `compare` 选择自己的 `member-*` 分支。
5. 检查页面显示的文件和修改内容。
6. 填写标题和说明，然后点击 **Create pull request**。

Pull Request 标题示例：

```text
docs: 完成成员 2 列表模块代码标注
feat: 完成 M1 便签复制功能
```

说明建议包含：

```text
完成内容：
- 修改了哪些功能或文档
- 为什么这样修改
- 属于代码标注 PR 还是维护任务 PR

测试情况：
- 进行了哪些测试
- 测试结果如何

材料位置：
- 标注材料或维护证据所在目录
- Issue、提交和证据链接

需要组长注意：
- 是否存在未解决问题
- 是否会影响其他成员的文件
```

务必确认目标分支是 `develop`，不要直接向 `master` 或 `main` 发起日常开发 Pull Request。

## 7. Pull Request 审核与修改

组长审核后可能提出修改意见。成员继续在自己的分支修改、提交并推送即可：

```bash
git switch member-1
# 修改文件
git add 相关文件
git commit -m "fix: 根据审核意见修改说明"
git push origin member-1
```

新的提交会自动出现在原 Pull Request 中，不需要重复创建 Pull Request。

审核通过后由组长合并到 `develop`。

## 8. 处理合并冲突

出现冲突时不要删除别人的代码，也不要强制推送。先执行：

```bash
git status
```

冲突文件中通常会出现：

```text
(示例) <<<<<<< HEAD
自己的内容
(示例) =======
对方的内容
(示例) >>>>>>> origin/develop
```

与相关成员或组长确认应该保留哪些内容，编辑文件并删除这些冲突标记，然后执行：

```bash
git add 冲突文件
git commit -m "merge: 解决与 develop 的冲突"
git push origin member-1
```

如果没有把握，先执行 `git merge --abort` 取消合并并请组长协助，不要随意尝试破坏性命令。

## 9. 组长操作规范

组长主要负责：

1. 确认成员的 Pull Request 目标是 `develop`。
2. 在 **Files changed** 中检查实际改动，重点检查版权声明、核心代码和不应上传的文件。
3. 确认相关测试已经完成，再把成员 Pull Request 合并到 `develop`。
4. 组织全组在 `develop` 上进行集成测试。
5. 阶段成果稳定后，从 `develop` 向 `master` 创建 Pull Request。
6. 审核并合并后，在 `master` 上形成稳定交付版本。
7. 保留 `main` 历史分支，不删除、不用于日常开发。

建议组长不要直接复制成员文件到 `master`，应使用 Pull Request 保留审核和提交记录。

## 10. 提交前检查清单

每位成员提交前逐项确认：

- [ ] 当前分支是自己的 `member-*` 分支。
- [ ] 已同步最新的 `origin/develop`。
- [ ] 没有修改 `src/original` 中的许可证、NOTICE 或版权声明。
- [ ] 代码修改都位于 `src/maintained`，材料放入了正确的 `doc` 子目录。
- [ ] 若为标注 PR，已补齐 `doc/annotations/member-N` 的四项材料且没有改变程序行为。
- [ ] 若为维护 PR，已补齐 `doc/evidence/member-N` 的设计、测试和截图证据。
- [ ] 没有提交密码、令牌、个人隐私或无关文件。
- [ ] 已检查 `git diff` 和 `git diff --staged`。
- [ ] 提交信息能够清楚说明修改内容。
- [ ] 已完成与本次修改相关的测试。
- [ ] Pull Request 的目标分支是 `develop`。
- [ ] Pull Request 中写明完成内容和测试结果。

## 11. 常见问题

### 提示 `not a git repository`

当前终端不在仓库目录中。先进入仓库：

```bash
cd homework
```

### 不知道当前在哪个分支

```bash
git branch --show-current
```

### 看不到远程的新分支或更新

```bash
git fetch origin
git branch --all
```

### 推送被拒绝

先不要强制推送。执行：

```bash
git fetch origin
git merge origin/member-1
```

把 `member-1` 换成自己的分支，解决可能出现的冲突后再推送。如果仍不确定，联系组长处理。

### 修改到一半需要临时切换任务

优先先完成一个小提交。如果修改尚未达到可提交状态，不要随意切换分支，先咨询组长如何保存当前工作。

## 12. 最简操作速查

日常工作最常用的一组命令如下，以 `member-1` 为例：

```bash
cd homework
git fetch origin
git switch member-1
git merge origin/develop

# 修改并测试文件

git status
git add 相关文件
git diff --staged
git commit -m "feat: 清楚说明本次修改"
git push origin member-1
```

推送后，在 GitHub 上创建 `member-1` 到 `develop` 的 Pull Request，等待组长审核。
