#!/usr/bin/env python3
"""数据体检脚本（02§2.8 / T-22）：扫描非法状态组合与跨表一致性，供运维每周巡检。
用法：python3 scripts/check-data.py [db路径]，默认 backend/data/market-dev.db。
发现不一致输出明细并以非零码退出（CI/巡检可拦截）。
"""
import sqlite3
import sys

DB = sys.argv[1] if len(sys.argv) > 1 else "backend/data/market-dev.db"

conn = sqlite3.connect(DB)
issues = []

# 1. LOCKED 商品但无有效订单（PENDING_PAY/PAID）
rows = conn.execute(
    """SELECT g.id FROM goods g WHERE g.status='LOCKED' AND g.deleted=0
       AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.goods_id=g.id
                       AND o.status IN ('PENDING_PAY','PAID'))"""
).fetchall()
issues += [f"goods #{r[0]}: LOCKED 但无有效订单" for r in rows]

# 2. SOLD 商品但无 COMPLETED 订单
rows = conn.execute(
    """SELECT g.id FROM goods g WHERE g.status='SOLD' AND g.deleted=0
       AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.goods_id=g.id AND o.status='COMPLETED')"""
).fetchall()
issues += [f"goods #{r[0]}: SOLD 但无 COMPLETED 订单" for r in rows]

# 3. PAID 订单但商品未锁定
rows = conn.execute(
    """SELECT o.id FROM orders o JOIN goods g ON g.id=o.goods_id
       WHERE o.status='PAID' AND g.status<>'LOCKED'"""
).fetchall()
issues += [f"order #{r[0]}: PAID 但商品状态={r[1] if len(r)>1 else '?'} 异常" for r in rows]

# 4. 评价引用了未完成订单
rows = conn.execute(
    """SELECT r.id FROM review r JOIN orders o ON o.id=r.order_id
       WHERE o.status<>'COMPLETED'"""
).fetchall()
issues += [f"review #{r[0]}: 所属订单未完成" for r in rows]

# 5. 封禁用户仍有在售/交易中商品（BR-08）
rows = conn.execute(
    """SELECT g.id FROM goods g JOIN user u ON u.id=g.seller_id
       WHERE u.status='BANNED' AND g.status IN ('ON_SALE','LOCKED')"""
).fetchall()
issues += [f"goods #{r[0]}: 卖家已封禁但商品状态异常" for r in rows]

# 6. COMPLETED 订单缺少互评之外的一方（每订单最多 2 条评价）
rows = conn.execute(
    """SELECT order_id, COUNT(*) FROM review GROUP BY order_id HAVING COUNT(*) > 2"""
).fetchall()
issues += [f"order #{r[0]}: 评价数 {r[1]} 超过 2" for r in rows]

conn.close()

if issues:
    print(f"[数据体检] 发现 {len(issues)} 处不一致：")
    for i in issues:
        print("  -", i)
    sys.exit(1)
print("[数据体检] 通过：无非法状态组合")