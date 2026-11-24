# -*- coding: utf-8 -*-
"""把开发表数据块导出为 Markdown，方便在 GitHub 上直接阅读。"""
import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))


def load(name):
    rows = []
    with open(os.path.join(HERE, name), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def esc(text):
    return str(text).replace("|", "\\|").replace("\n", "<br>")


def main():
    out = []
    out.append("# AppTimer 开发表（0.0.001 复刻路线）\n")
    out.append("把当前 AppTimer（LSPosed 版）0.0.001 / versionCode 1 的开发过程拆成 21 段可独立粘贴的提示词。"
                "新账号按序号 00 → 20 依次把「提示词」整段复制过去，每轮验收通过再进下一轮（20 是发布后补的导航，按需执行）。\n")
    out.append("配套文件：`AppTimer-开发表.xlsx`（四个子表）、`book.json` 与 `*.jsonl`（数据源，改完可用下方命令重建）。\n")

    out.append("## 开发表\n")
    out.append("| 序号 | 阶段 | 目标 | 提示词（整段复制给新会话） | 验收标准 | 涉及文件 | 坑与约定 |")
    out.append("| --- | --- | --- | --- | --- | --- | --- |")
    for part in ["part-1.jsonl", "part-2.jsonl", "part-3.jsonl", "part-4.jsonl"]:
        for r in load(part):
            out.append("| %s | %s | %s | %s | %s | %s | %s |" % (
                esc(r.get("id", "")), esc(r.get("stage", "")), esc(r.get("goal", "")),
                esc(r.get("prompt", "")), esc(r.get("accept", "")),
                esc(r.get("files", "")), esc(r.get("pitfall", ""))))
    out.append("")

    out.append("## 使用说明\n")
    out.append("| 项目 | 内容 |")
    out.append("| --- | --- |")
    for r in load("guide.jsonl"):
        out.append("| %s | %s |" % (esc(r["item"]), esc(r["content"])))
    out.append("")

    out.append("## 环境与命令\n")
    out.append("| 项目 | 值 / 命令 | 说明 |")
    out.append("| --- | --- | --- |")
    for r in load("env.jsonl"):
        out.append("| %s | `%s` | %s |" % (esc(r["item"]), esc(r["value"]), esc(r["note"])))
    out.append("")

    out.append("## 版本流水\n")
    out.append("| 版本 | 提交 | 日期 | 主要变化 | 备注 |")
    out.append("| --- | --- | --- | --- | --- |")
    for r in load("versions.jsonl"):
        out.append("| %s | `%s` | %s | %s | %s |" % (
            esc(r["version"]), esc(r["commit"]), esc(r["date"]),
            esc(r["change"]), esc(r["note"])))
    out.append("")

    out.append("## 重建方式\n")
    out.append("```bash")
    out.append("cd docs/dev-table")
    out.append("python -m pip install openpyxl  # 首次")
    out.append("python <path-to>/excel_build_workbook.py --spec ./book.json --output ./AppTimer-开发表.xlsx --overwrite")
    out.append("python ./gen_md.py")
    out.append("```\n")

    with open(os.path.join(HERE, "开发表.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print("written", os.path.join(HERE, "开发表.md"))


if __name__ == "__main__":
    main()
