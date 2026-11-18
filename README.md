# homework

## 项目说明

本仓库用于“小米便签”开源软件质量分析、标注、维护与优化课程项目。

## 仓库结构

```text
homework/
├── src/
│   ├── original/                   # 官方源码只读快照
│   └── maintained/                 # 标注、维护和优化的唯一工作代码
├── doc/
│   ├── annotations/member-1..6/    # 六人的代码阅读与标注材料
│   ├── evidence/member-1..6/       # 六项维护任务的个人证据包
│   ├── report-reading-maintenance/ # 阅读、标注和维护报告
│   ├── report-quality/             # 质量分析报告及工具原始结果
│   ├── ppt/                        # PPT、演示脚本和答辩材料
│   ├── UML/                        # UML 源文件和导出图
│   ├── screenshots/                # 最终公共截图和索引
│   └── testing/                    # 测试材料和汇总结果
└── README.md
```

- `src/original/` 永远不用于日常修改。
- `src/maintained/` 初始内容与原始快照一致；代码标注和后续维护都在这里完成。构建时把该目录视为项目根目录。
- 标注过程材料放入 `doc/annotations/member-N/`，维护设计、测试和前后截图放入 `doc/evidence/member-N/`，不要混放。

详细分工和操作方法见：

- [`doc/项目六人分工与进度计划.md`](doc/项目六人分工与进度计划.md)
- [`doc/Git分支使用说明.md`](doc/Git分支使用说明.md)

## 上游来源

- 官方仓库：<https://github.com/MiCode/Notes>
- 小组 Fork：<https://github.com/xiaomiNotebook/Notes>
- 导入分支：`master`
- 导入提交：`81a66024fe48eaa2206b854430856fe3875ff3ba`

官方项目的 `NOTICE`、README 和源代码版权声明保留在 `src/original/` 与工作副本 `src/maintained/` 中。小组 Fork 保留官方完整提交历史；本仓库不包含嵌套 Git 仓库。

## 分支约定

- `master`：原始代码与最终稳定版本。
- `develop`：日常集成、联调、测试和审核。
- `member-1` 至 `member-6`：六位成员的个人工作分支，均从 `develop` 创建。

成员完成修改后向 `develop` 提交 Pull Request，经审核和测试后合并；最终稳定版本再合并到 `master`。
