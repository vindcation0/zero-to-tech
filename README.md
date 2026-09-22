# 文字实验室 (Zero to Tech)

一个中文文本智能分析小工具：输入一段话，由大模型给出情感倾向评分、情绪标签和全文拼音，并把每次分析的结果持久化存入数据库。基于 Cookie + UUID 实现了无感知的多用户数据隔离。

零到全栈课程的贯穿项目。

## 技术栈

- 前端：React 19 ＋ Vite 8（SPA 单页架构、Anime.js 动画驱动、Credentials 凭证透传）
- 后端：Spring Boot 3.3 ＋ Java 21
- AI 分析：Spring AI ＋ DeepSeek 大模型（结构化输出）
- 存储：MySQL 8.0 ＋ MyBatis-Plus 3.5.7（多租户用户数据隔离）
- 会话与安全：长效 Cookie ＋ UUID（自动下发、防越权删除）
- 线上：Nginx 反向代理 ＋ Linux (Ubuntu) 后台守护常驻

## 本地跑起来

需要：Node.js 18+、Java 21、MySQL 8.0+

**1. 数据库准备**

本地启动 MySQL，创建数据库：
```sql
CREATE DATABASE IF NOT EXISTS zero_to_tech CHARACTER SET utf8mb4;
```
*(数据表会在后端首次启动时通过 `schema.sql` 自动建好)*

**2. 配置文件说明 (多环境 Profile 方案)**

项目采用标准的 Spring Boot Profile 机制进行开发与线上环境隔离：
* `application.yml`：通用基础配置（默认激活 `dev` 环境）。
* `application-dev.yml`：本地开发专用配置（连本地 MySQL，开启详细 SQL 打印）。
* `application-prod.yml`：生产部署专用配置（通过系统环境变量或启动参数传参，防密码泄露）。

**3. 本地启动**

```bash
cd server
mvn spring-boot:run           # → 默认加载 dev Profile，监听 http://localhost:8080
```

**4. 前端启动**（另开一个终端）

```bash
npm install
npm run dev                   # → 打开 http://localhost:5173
```

---

## 部署到服务器

前提：服务器上已装好 Java 21、MySQL 8.0 和 Nginx，且 Nginx 的 80 端口已反向代理到本地的 `8080` 端口。

**1. 本地一键打包**

在项目根目录执行：
```bash
npm run build:server          # 编译前端，并自动同步到后端的 static 静态资源目录
cd server
mvn clean package -DskipTests # 打包生成全栈单一 Jar 包 (target/server-0.0.1-SNAPSHOT.jar)
```

**2. 上传到服务器并启动**

将本地打好的 Jar 包上传到服务器（如 `/home/ubuntu/app/` 并重命名为 `app.jar`）：

使用生产环境 Profile（`prod`）启动，并通过环境变量或启动参数安全注入生产密码：

```bash
cd ~/app

# 方式 A：启动参数直接注入（最快捷直观）
nohup java -jar app.jar \
  --spring.profiles.active=prod \
  --spring.datasource.password="你的服务器MySQL密码" \
  --spring.ai.openai.api-key="你的DeepSeek密钥" \
  > app.log 2>&1 &

# 方式 B：通过系统环境变量注入（安全防进程查看泄露）
export DB_PASSWORD="你的服务器MySQL密码"
export DEEPSEEK_API_KEY="你的DeepSeek密钥"
nohup java -jar app.jar --spring.profiles.active=prod > app.log 2>&1 &
```

`nohup ... &` 让服务在 SSH 断开后继续后台常驻运行，日志自动写进 `app.log`。

查看日志、停止服务：
```bash
tail -f app.log               # 实时看日志
ps -ef | grep app.jar         # 找到进程号 (PID)
kill -9 进程号                 # 停掉旧服务
```

**3. Nginx 配置（80 端口转发）**

在 `/etc/nginx/sites-available/default` 中配置反向代理：
```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```
执行 `sudo nginx -s reload` 重载 Nginx。

**4. 云安全组放行**

去云平台控制台的安全组 / 防火墙，放行 `80` 端口（以及 `8080` 备用）。

**5. 验证**

浏览器访问 `http://服务器公网IP/text-lab`，输入中文点击“开始分析”，即可体验完整的 SSE 流式打字机效果及历史记录多用户隔离功能。

