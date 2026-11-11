package com.campus.market.config;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * SQLite 数据源（设计 02§2.4.4 / 开发 03§3.4）。
 * WAL 提升读写并发（NFR-PERF-01）；busy_timeout 缓解写排队；
 * PRAGMA 仅对当前连接有效（已通过 sqlite3 实测验证），必须由 SQLiteConfig 为每个连接注入。
 * TransactionTemplate 供编程式逐单事务（T-11 超时关闭，02§2.6.3 逐单语义）。
 */
@Configuration
public class DataConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Bean
    public DataSource dataSource() {
        // SQLite 不会自动创建父目录：从 url 提取 db 文件路径并确保父目录存在
        String path = url.substring("jdbc:sqlite:".length());
        int query = path.indexOf('?');
        if (query > 0) {
            path = path.substring(0, query);
        }
        try {
            java.nio.file.Files.createDirectories(
                    java.nio.file.Paths.get(path).toAbsolutePath().getParent());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法创建数据目录: " + path, e);
        }

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        // xerial 3.45 真实 API（javap 确认）：busy_timeout 走 setPragma，外键走 enforceForeignKeys
        config.setPragma(SQLiteConfig.Pragma.BUSY_TIMEOUT, "5000");
        config.enforceForeignKeys(true);
        // 事务模式 IMMEDIATE：BEGIN 即获取写锁，消除 WAL 下 deferred 事务"先读后写"
        // 并发升级失败产生的 SQLITE_BUSY_SNAPSHOT（TC-03 并发下单失败根因，busy_timeout 无法缓解）
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(url);
        return dataSource;
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}