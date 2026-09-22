from pathlib import Path
import os

notes = """# XNAT Android v1.0.4

自动续费、实时监控与危险操作交互更新。

- 服务器详情新增 iOS 风格自动续费开关，通过 Mobile API v1 与 XNAT Panel v1.0.8 同步
- 自动续费即时保存，失败自动回滚开关状态，避免界面与服务端不一致
- 实时资源监控由 5 秒调整为 3 秒刷新，并使用“● 实时”状态提示
- CPU / 内存保持双列，硬盘改为整行；实时网络独立整行显示下载 / 上传瞬时速率
- 重装与删除确认页新增“填入编号”，只填写机器编号，不会自动执行危险操作
- 删除提示同步 Panel-only 行为，不会连接或删除 Host 上可能仍存在的实例
- 继续使用 Mobile API v1；旧 Panel 无 auto_renew 字段时自动续费入口安全降级
- 适配 XNAT Panel v1.0.8 / Host Agent v1.0.4

**由 𝐍𝐀𝐌𝐄𝐋𝐄𝐒𝐒 和 GPT 倾力打造**
"""

runner_temp = Path(os.environ.get("RUNNER_TEMP", "/tmp"))
runner_temp.mkdir(parents=True, exist_ok=True)
(runner_temp / "xnat-release-notes.md").write_text(notes, encoding="utf-8")
print("Prepared XNAT Android v1.0.4 release notes")
