-- =============================================================
-- 校园二手交易平台 · 数据库基线 V1（对应设计文档 02§2.4.1~2.4.2）
-- 约定：时间戳一律使用 INTEGER（epoch milliseconds，UTC）
--       枚举用 TEXT + CHECK 约束（SQLite 无原生 ENUM，02§2.4.1 注）
--       全部语句幂等（IF NOT EXISTS），可重复执行
-- 口径来源：需求文档 01§1.7.2 枚举表（唯一口径，含 CH-004 增补 TradeRole）
-- =============================================================

PRAGMA foreign_keys = ON;

-- -------------------------------------------------------------
-- user 用户表（02§2.4.2）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT    NOT NULL UNIQUE,                          -- 登录邮箱（BR-01 后缀校验在应用层）
    password_hash TEXT    NOT NULL,                                 -- BCrypt(10)，NFR-SEC-01
    nickname      TEXT    NOT NULL,
    avatar_url    TEXT,
    bio           TEXT    DEFAULT '',
    role          TEXT    NOT NULL DEFAULT 'USER'
                  CHECK (role IN ('USER', 'ADMIN')),                -- Role 枚举
    status        TEXT    NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'BANNED')),           -- UserStatus 枚举
    fail_count    INTEGER NOT NULL DEFAULT 0,                       -- 连续登录失败次数（NFR-SEC-05）
    lock_until    INTEGER,                                          -- 锁定截止时间（epoch ms）
    password_changed_at INTEGER,                                    -- 最近改密时刻；token.iat 早于它则失效（FR-AUTH-04，CH-007）
    created_at    INTEGER NOT NULL
);

-- -------------------------------------------------------------
-- category 分类字典（种子数据见 data.sql）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS category (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    name   TEXT    NOT NULL UNIQUE,
    sort   INTEGER NOT NULL DEFAULT 0,
    status TEXT    NOT NULL DEFAULT 'ACTIVE'
);

-- -------------------------------------------------------------
-- goods 商品表（GoodsStatus 七态，BR-02 状态机迁移由应用层守卫）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS goods (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_id     INTEGER NOT NULL REFERENCES user(id),
    category_id   INTEGER REFERENCES category(id),
    title         TEXT    NOT NULL,                                 -- ≤60 字符，应用层校验
    description   TEXT    DEFAULT '',                               -- ≤2000 字符，应用层校验
    condition     TEXT    NOT NULL
                  CHECK (condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'WELL_USED')),  -- 成色枚举
    price         REAL    NOT NULL
                  CHECK (price >= 0.01 AND price <= 99999.99),      -- BR-09
    images_json   TEXT    NOT NULL DEFAULT '[]',                    -- JSON 数组：["/api/v1/files/xx", ...] 1~9 张（BR-10）
    status        TEXT    NOT NULL DEFAULT 'PENDING_AUDIT'
                  CHECK (status IN ('DRAFT', 'PENDING_AUDIT', 'REJECTED',
                                    'ON_SALE', 'LOCKED', 'SOLD', 'OFF_SHELF')),
    reject_reason TEXT,                                             -- 审核驳回原因（FR-ADMIN-01 必填）
    published_at  INTEGER,                                          -- 审核通过时刻（列表排序依据）
    deleted       INTEGER NOT NULL DEFAULT 0,                       -- 逻辑删除（FR-GOODS-04）
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_goods_status_published ON goods (status, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_goods_category ON goods (category_id);
CREATE INDEX IF NOT EXISTS idx_goods_seller ON goods (seller_id);

-- -------------------------------------------------------------
-- orders 订单表（表名避开保留字 order；OrderStatus 五态）
-- amount 为下单时价格快照（BR-12）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    order_no        TEXT    NOT NULL UNIQUE,                        -- ORD+时间戳+随机（实现细节）
    goods_id        INTEGER NOT NULL REFERENCES goods(id),
    buyer_id        INTEGER NOT NULL REFERENCES user(id),
    seller_id       INTEGER NOT NULL REFERENCES user(id),
    amount          REAL    NOT NULL
                    CHECK (amount >= 0.01 AND amount <= 99999.99),  -- 价格快照（BR-12）
    status          TEXT    NOT NULL DEFAULT 'PENDING_PAY'
                    CHECK (status IN ('PENDING_PAY', 'PAID', 'COMPLETED',
                                      'CANCELLED', 'CLOSED')),
    pay_voucher_no  TEXT,                                           -- 模拟支付凭证（BR-04：PAY+时间戳+4位随机）
    pay_time        INTEGER,
    complete_time   INTEGER,
    cancel_reason   TEXT,
    timeout_at      INTEGER NOT NULL,                               -- 支付截止 = created_at + 15min（BR-03）
    created_at      INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_voucher ON orders (pay_voucher_no) WHERE pay_voucher_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_buyer ON orders (buyer_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_seller ON orders (seller_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_timeout ON orders (status, timeout_at);  -- 超时扫描（02§2.6.3）

-- -------------------------------------------------------------
-- message 商品留言（一层回复 parent_id；BR-11 敏感词在应用层）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    goods_id   INTEGER NOT NULL REFERENCES goods(id),
    sender_id  INTEGER NOT NULL REFERENCES user(id),
    parent_id  INTEGER REFERENCES message(id),
    content    TEXT    NOT NULL,                                   -- ≤200 字符，应用层校验
    is_read    INTEGER NOT NULL DEFAULT 0,
    deleted    INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_message_goods ON message (goods_id, created_at);

-- -------------------------------------------------------------
-- review 交易互评（BR-07：每订单每人 1 次，UNIQUE 兜底）
-- ReviewRole 枚举：BUYER_TO_SELLER / SELLER_TO_BUYER（口径补充，随 CH-006 登记）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS review (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id    INTEGER NOT NULL REFERENCES orders(id),
    reviewer_id INTEGER NOT NULL REFERENCES user(id),
    reviewee_id INTEGER NOT NULL REFERENCES user(id),
    role        TEXT    NOT NULL
                CHECK (role IN ('BUYER_TO_SELLER', 'SELLER_TO_BUYER')),
    rating      INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content     TEXT    DEFAULT '',
    created_at  INTEGER NOT NULL,
    UNIQUE (order_id, reviewer_id)
);

CREATE INDEX IF NOT EXISTS idx_review_reviewee ON review (reviewee_id);

-- -------------------------------------------------------------
-- report 举报（ReportTargetType / ReportStatus 枚举）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    reporter_id INTEGER NOT NULL REFERENCES user(id),
    target_type TEXT    NOT NULL CHECK (target_type IN ('GOODS', 'USER')),
    target_id   INTEGER NOT NULL,
    reason      TEXT    NOT NULL,
    detail      TEXT    DEFAULT '',                                -- ≤200 字符
    status      TEXT    NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED')),
    handler_id  INTEGER,
    handle_note TEXT,
    handled_at  INTEGER,
    created_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_report_status ON report (status);

-- -------------------------------------------------------------
-- audit_log 管理操作审计（NFR-SEC-04）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    operator_id INTEGER NOT NULL,                                -- 管理员 user.id
    action      TEXT    NOT NULL,                                -- AUDIT_GOODS / BAN_USER / ARBITRATE / HANDLE_REPORT
    target_type TEXT    NOT NULL,
    target_id   INTEGER NOT NULL,
    detail      TEXT    DEFAULT '',
    created_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_operator ON audit_log (operator_id, created_at);

-- -------------------------------------------------------------
-- sensitive_word 敏感词（BR-11，最小词表见 data.sql [假设 A-05]）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sensitive_word (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    word TEXT    NOT NULL UNIQUE
);