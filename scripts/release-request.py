from pathlib import Path
import os

notes = """# XNAT Android v1.0.5

适配 XNAT Panel v1.1.0 的服务器删除安全语义。

- 普通删除现在明确表示会永久删除 Host 上真实 VPS、系统盘和端口转发
- 只有 Host 确认实例删除成功后，Panel 才清理服务器记录
- Host 删除失败时服务器仍保留在 Panel，不再显示错误的 Panel-only 删除说明
- 管理员“强制从 Panel 移除”仍只存在于 Web 管理后台，不向普通用户客户端暴露
- 自动续费、3 秒实时资源监控、机器编号一键填入等 v1.0.4 功能保持不变
- Mobile API 继续保持 v1
- 配套 XNAT Panel v1.1.0 / Host Agent v1.0.5

**由 𝐍𝐀𝐌𝐄𝐋𝐄𝐒𝐒 和 GPT 倾力打造**
"""

runner_temp = Path(os.environ.get("RUNNER_TEMP", "/tmp"))
runner_temp.mkdir(parents=True, exist_ok=True)
(runner_temp / "xnat-release-notes.md").write_text(notes, encoding="utf-8")
print("Prepared XNAT Android v1.0.5 release notes")
