#!/usr/bin/env node
/**
 * NFR-PERF-01/02 压测脚本（JMeter 替代，本机无 JMeter——CH-010 登记）：
 * 对指定 URL 执行并发请求，统计 P95/P99/错误率。
 * 用法：node scripts/perf-test.js [url] [concurrency=50] [totalRequests=500]
 * 验收口径（NFR-PERF-01）：50 并发列表 P95 ≤ 500ms、错误率 < 1%。
 * 前置：后端已启动（java -jar market.jar）。
 */
const url = process.argv[2] || "http://localhost:8080/api/v1/goods?size=20";
const concurrency = parseInt(process.argv[3] || "50", 10);
const totalRequests = parseInt(process.argv[4] || "500", 10);

const results = [];
let errors = 0;

async function worker(workerId, requestsPerWorker) {
  for (let i = 0; i < requestsPerWorker; i++) {
    const t0 = performance.now();
    try {
      const resp = await fetch(url);
      await resp.arrayBuffer();          // 消费响应体
      const cost = performance.now() - t0;
      results.push(cost);
      if (!resp.ok) errors++;
    } catch (e) {
      errors++;
      results.push(performance.now() - t0);
    }
  }
}

(async () => {
  console.log(`压测目标: ${url}`);
  console.log(`并发数: ${concurrency}, 总请求数: ${totalRequests}`);
  const perWorker = Math.ceil(totalRequests / concurrency);
  const t0 = performance.now();
  await Promise.all(
    Array.from({ length: concurrency }, (_, i) => worker(i, perWorker))
  );
  const wallMs = performance.now() - t0;

  results.sort((a, b) => a - b);
  const at = (p) => results[Math.min(results.length - 1, Math.floor((p / 100) * results.length))];
  const errRate = ((errors / results.length) * 100).toFixed(2);

  console.log("\n===== 压测结果 =====");
  console.log(`完成请求数: ${results.length}`);
  console.log(`吞吐: ${(results.length / (wallMs / 1000)).toFixed(1)} req/s`);
  console.log(`平均: ${at(50).toFixed(0)} ms`);
  console.log(`P90:  ${at(90).toFixed(0)} ms`);
  console.log(`P95:  ${at(95).toFixed(0)} ms   （NFR-PERF-01 口径）`);
  console.log(`P99:  ${at(99).toFixed(0)} ms`);
  console.log(`错误率: ${errRate}%（口径 < 1%）`);
  console.log(`结论: ${parseFloat(at(95)) <= 500 && errors / results.length < 0.01 ? "达标 ✅" : "未达标 ❌"}`);
})();