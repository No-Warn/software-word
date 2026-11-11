#!/usr/bin/env bash
# 本地 CI 门禁脚本（假设 A-15：无远程 CI 环境，本地门禁与 03§3.9.1 测试策略一致）
# 用法：bash scripts/ci.sh   —— 任一步骤失败即非零退出（门禁不可绕过）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> [1/3] 后端门禁：mvn verify（编译 + 单测 + 集成测试）"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk17}"
export PATH="$JAVA_HOME/bin:$HOME/.local/maven/bin:$HOME/.local/node/bin:$PATH"
(cd "$ROOT/backend" && mvn -B verify)

echo "==> [2/3] 前端门禁：vite build"
(cd "$ROOT/frontend" && npm run build)

echo "==> [3/3] CI_OK 全部门禁通过"