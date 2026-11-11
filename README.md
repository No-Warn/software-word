# software-word

校园二手交易平台（拾光集市）软件工程理论基础作业。

## 项目结构

- `backend/`：Spring Boot 3 + SQLite 后端
- `frontend/`：Vue 3 + Vite 前端
- `doc/`：需求、设计、开发等项目文档
- `deploy/`：部署与数据库备份相关文件

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js 22+ 和 npm

> Node.js 22 是推荐版本；使用较低版本时，部分前端依赖可能提示 Node 引擎版本不匹配。

## 本地开发启动

首次运行会自动创建开发用 SQLite 数据库并初始化表结构。无需单独安装数据库服务。

### 1. 启动后端

在终端一执行：

```bash
cd backend
export ADMIN_PASSWORD='请设置一个本地管理员密码'
mvn spring-boot:run
```

后端默认地址为 `http://localhost:8080`。开发数据库默认位于 `backend/data/market-dev.db`。

验证后端健康状态：

```bash
curl http://localhost:8080/actuator/health
```

正常时响应中会包含 `"status":"UP"`。

### 2. 启动前端

保持后端运行，在终端二执行：

```bash
cd frontend
npm install
npm run dev
```

打开 Vite 输出的本地地址，默认是 `http://localhost:5173`。开发服务器会将 `/api` 请求代理到 `http://localhost:8080`。

停止前端或后端时，在相应终端按 `Ctrl+C`。

## 注册与管理员登录

- 普通用户注册邮箱的域名需以 `.edu.cn` 结尾，例如 `student@university.edu.cn`。可通过环境变量 `CAMPUS_EMAIL_SUFFIX` 调整后端允许的域名后缀。
- 管理员登录邮箱固定为 `admin@campus.local`，管理员页面地址为 `http://localhost:5173/admin`。
- 首次启动且数据库中尚无管理员账号时，后端使用 `ADMIN_PASSWORD` 创建管理员。若管理员账号已经存在，重新设置该变量不会重置已有密码。
- 请勿将真实密码、JWT 密钥或生产环境配置提交到代码库。生产部署请使用安全的密钥配置，不要使用示例或开发默认值。

## 构建

后端打包：

```bash
cd backend
mvn -DskipTests package
```

前端构建：

```bash
cd frontend
npm install
npm run build
```

前端静态文件生成在 `frontend/dist/`。

## 参考资料

- 项目文档：[`doc/`](doc/)
- 参考项目：[华为云案例中心](https://devstation.connect.huaweicloud.com/space/devportal/casecenter/a75b424f52e74de590ffff74b721e601/1)
