# homework

## 项目说明

本仓库用于“小米便签”开源软件质量分析、标注、维护与优化课程项目。

## 仓库结构

- `src/original/`：官方 MiCode Notes 源码快照，仅用于对照，原则上不直接修改。
- `src/maintained/`：小组标注、维护和优化后的代码。
- `doc/UML/`：UML 图与设计分析材料。
- `doc/screenshots/`：功能运行与验证截图。
- `doc/testing/`：测试计划、测试用例与测试报告。

## 上游来源

- 官方仓库：<https://github.com/MiCode/Notes>
- 小组 Fork：<https://github.com/xiaomiNotebook/Notes>
- 导入分支：`master`
- 导入提交：`81a66024fe48eaa2206b854430856fe3875ff3ba`

官方项目的 `NOTICE`、README 和源代码版权声明均保留在 `src/original/` 中。小组 Fork 保留官方完整提交历史；本仓库不包含嵌套 Git 仓库。

## 分支约定

- `master`：原始代码与最终稳定版本。
- `develop`：日常集成、联调、测试和审核。
- `member-1` 至 `member-6`：六位成员的个人工作分支，均从 `develop` 创建。

成员完成修改后向 `develop` 提交 Pull Request，经审核和测试后合并；最终稳定版本再合并到 `master`。
