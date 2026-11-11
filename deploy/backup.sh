#!/usr/bin/env bash
# NFR-AVAIL-02：SQLite 每日备份（cron：0 2 * * *），WAL 安全快照（.backup 在线备份），
# 保留最近 7 份（Q7 默认口径）。用法：DB_FILE=xxx bash backup.sh 或默认 /opt/market/data/market.db
set -euo pipefail

DATA_DIR="${DATA_DIR:-/opt/market/data}"
DB_FILE="${DB_FILE:-$DATA_DIR/market.db}"
BACKUP_DIR="$(dirname "$DB_FILE")/backup"
KEEP=7

if [ ! -f "$DB_FILE" ]; then
    echo "ERROR: 数据库文件不存在: $DB_FILE" >&2
    exit 1
fi
mkdir -p "$BACKUP_DIR"

STAMP=$(date +%Y%m%d-%H%M%S)
TARGET="$BACKUP_DIR/$(basename "$DB_FILE" .db)-$STAMP.db"
sqlite3 "$DB_FILE" ".backup '$TARGET'"

# 清理过期备份（保留最近 KEEP 份）
ls -1t "$BACKUP_DIR"/$(basename "$DB_FILE" .db)-*.db 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f

echo "backup done: $TARGET（当前保留 $(ls -1 "$BACKUP_DIR"/$(basename "$DB_FILE" .db)-*.db | wc -l) 份）"