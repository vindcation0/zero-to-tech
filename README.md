# 文字实验室 (Zero to Tech)

一个中文文本智能分析小工具：输入一段话，由大模型给出情感倾向评分、情绪标签和全文拼音，并把每次分析的结果持久化存入数据库，支持回显与历史管理。

零到全栈课程的贯穿项目。

## 技术栈

- 前端：React 19 ＋ Vite 8（SPA 单页架构、Anime.js 动画驱动）
- 后端：Spring Boot 3.3 ＋ Java 21
- AI 分析：Spring AI ＋ DeepSeek 大模型（结构化输出）
- 存储：MySQL 8.0 ＋ MyBatis-Plus 3.5.7（自动建表与持久化）
- 线上：Nginx 反向代理 ＋ Linux (Ubuntu) 后台守护常驻

## 本地跑起来

需要：Node.js 18+、Java 21、MySQL 8.0+

**1. 数据库准备**

本地启动 MySQL，创建数据库：
```sql
CREATE DATABASE IF NOT EXISTS zero_to_tech CHARACTER SET utf8mb4;
```
*(数据表会在后端首次启动时通过 `schema.sql` 自动建好)*

**2. 配置文件**

进入 `server/src/main/resources/`，在 `application-secret.yml` 中填入你的本地 MySQL 密码和 DeepSeek API Key（参见下方「配置说明」）。

**3. 后端启动**

```bash
cd server
mvn spring-boot:run           # → 监听 http://localhost:8080
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

**2. 上传到服务器**

将本地打好的 Jar 包和独立的敏感配置文件上传到服务器同一目录下（如 `/home/ubuntu/app/`）：
* `server-0.0.1-SNAPSHOT.jar` → 重命名为 `app.jar`
* `server/src/main/resources/application-secret.yml` → 传到同级目录，按需填入服务器密码

**3. 后端：在后台跑起来**

```bash
cd ~/app
nohup java -jar app.jar > app.log 2>&1 &
```

`nohup ... &` 让它在 SSH 断开后继续后台常驻运行，日志自动写进 `app.log`。

查看日志、停止服务：
```bash
tail -f app.log               # 实时看日志
ps -ef | grep app.jar         # 找到进程号 (PID)
kill -9 进程号                 # 停掉旧服务
```

**4. Nginx 配置（80 端口转发）**

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

**5. 云安全组放行**

去云平台控制台的安全组 / 防火墙，放行 `80` 端口（以及 `8080` 备用）。

**6. 验证**

浏览器访问 `http://服务器公网IP/text-lab`，输入中文点击“开始分析”。
看到拼音、情绪分滚动展示，且下方实时追加了一条 MySQL 历史记录，点击历史项可回填，即代表部署成功。

---

## 配置说明

为保证密钥安全与易用性，本项目将敏感配置抽离为独立文件：
👉 文件路径：`server/src/main/resources/application-secret.yml`
*(Spring Boot 会自动优先读取 Jar 包外部同级目录的 `application-secret.yml`，在服务器修改此文件无需重新打包)*

| 配置路径 | 说明 | 本地配置示例 | 线上配置示例 |
| :--- | :--- | :--- | :--- |
| `custom.datasource.host` | MySQL 数据库地址 | `localhost` | `localhost` |
| `custom.datasource.port` | MySQL 端口 | `3306` | `3306` |
| `custom.datasource.database` | 数据库名称 | `zero_to_tech` | `zero_to_tech` |
| `custom.datasource.username` | 数据库用户名 | `root` | `root` |
| `custom.datasource.password` | 数据库密码 | `你的本地密码` | `你的服务器密码` |
| `custom.ai.api-key` | DeepSeek API 密钥 | `sk-xxxx` | `sk-xxxx` |
