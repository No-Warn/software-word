-- =============================================================
-- 种子数据 V1（设计文档 02§2.4.3）
-- 幂等：INSERT OR IGNORE，可重复执行
-- 说明：admin 账号由应用启动器创建（密码经 BCrypt(ADMIN_PASSWORD) 写入，
--       见 03§3.7，不在此文件出现明文/哈希）
-- =============================================================

INSERT OR IGNORE INTO category (id, name, sort, status) VALUES (1, '教材', 1, 'ACTIVE');
INSERT OR IGNORE INTO category (id, name, sort, status) VALUES (2, '数码', 2, 'ACTIVE');
INSERT OR IGNORE INTO category (id, name, sort, status) VALUES (3, '生活', 3, 'ACTIVE');
INSERT OR IGNORE INTO category (id, name, sort, status) VALUES (4, '运动', 4, 'ACTIVE');

-- 敏感词最小表（约 20 词，[假设 A-05]，来源待确认 Q8；词表可热扩充）
INSERT OR IGNORE INTO sensitive_word (word) VALUES
    ('代开发票'), ('刷单'), ('兼职日结'), ('加微信'), ('加QQ'),
    ('赌博'), ('网络贷款'), ('色情'), ('外挂'), ('代考'),
    ('办证'), ('毒品'), ('枪支'), ('诈骗'), ('返利'),
    ('刷信誉'), ('刷好评'), ('低价代充'), ('游戏币出售'), ('代练');