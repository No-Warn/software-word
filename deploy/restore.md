# 恢复演练手册（NFR-AVAIL-02：RTO ≤ 1h / RPO ≤ 24h）

## 前置条件

- 备份文件位于 `data/backup/`（由 backup.sh 每日生成）
- 应用已停止或即将停止（恢复期间服务不可用）

## 标准恢复流程（数据损坏/误删场景）

```bash
# 1. 停止应用
sudo systemctl stop market   # 生产；本地开发直接结束进程

# 2. 确认最近备份
ls -1t data/backup/*.db | head -1

# 3. 保留当前损坏库（供事后分析），用最近备份覆盖
mv data/market.db data/market.db.corrupted-$(date +%Y%m%d%H%M%S)
cp data/backup/market-YYYYMMDD-HHMMSS.db data/market.db

# 4. 启动应用
sudo systemctl start market

# 5. 验证（TC-15 抽查项）
curl -s http://localhost:8080/actuator/health        # 期望 {"status":"UP"}
sqlite3 data/market.db "SELECT COUNT(*) FROM goods WHERE status='ON_SALE';"   # 抽查业务数据
```

## WAL 文件注意

- 恢复前若存在 `market.db-wal` / `market.db-shm`，一并移除（旧 WAL 与新库不一致会导致数据错乱）

## 演练记录（填写于验收报告）

| 项 | 值 |
|---|---|
| 演练日期 | （执行时填写） |
| 备份 → 恢复完成耗时 | （目标 RTO ≤ 1h） |
| 数据损失窗口 | （目标 RPO ≤ 24h，即最近一次 02:00 备份） |
| 抽查结果 | health UP / 业务计数一致 |