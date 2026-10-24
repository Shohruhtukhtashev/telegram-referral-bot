# Deployment Guide

This guide covers different deployment scenarios for the Telegram Referral Bot.

## Table of Contents
1. [Local Development](#local-development)
2. [Production with PostgreSQL](#production-with-postgresql)
3. [Webhook Setup](#webhook-setup)
4. [Docker Deployment](#docker-deployment)
5. [Cloud Deployment](#cloud-deployment)

## Local Development

### Quick Start with H2 Database

```bash
# 1. Set environment variables
export TELEGRAM_BOT_TOKEN="your_bot_token"
export TELEGRAM_BOT_USERNAME="your_bot_username"
export PRIVATE_CHANNEL_LINK="https://t.me/+your_invite_link"

# 2. Run with dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The bot will use in-memory H2 database and long polling mode.

## Production with PostgreSQL

### 1. Install PostgreSQL

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**CentOS/RHEL:**
```bash
sudo yum install postgresql-server postgresql-contrib
sudo postgresql-setup initdb
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**Windows:**
Download from [PostgreSQL Downloads](https://www.postgresql.org/download/windows/)

### 2. Create Database

```bash
# Switch to postgres user
sudo -u postgres psql

# In psql console:
CREATE DATABASE telegram_bot;
CREATE USER bot_user WITH PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE telegram_bot TO bot_user;
\q
```

### 3. Configure Application

Edit `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/telegram_bot
    username: bot_user
    password: secure_password
```

### 4. Build and Run

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/telegram-referral-bot-1.0.0.jar
```

## Webhook Setup

### Requirements
- Public domain with HTTPS
- Valid SSL certificate
- Accessible from internet

### 1. Configure Webhook Mode

Edit `application.yml`:
```yaml
telegram:
  bot:
    mode: webhook
  webhook:
    url: https://yourdomain.com
    path: /webhook

server:
  port: 8080
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: your_password
    key-store-type: PKCS12
```

### 2. Generate SSL Certificate

**Using Let's Encrypt:**
```bash
sudo apt install certbot
sudo certbot certonly --standalone -d yourdomain.com
```

**Convert to PKCS12:**
```bash
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/yourdomain.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/yourdomain.com/privkey.pem \
  -out keystore.p12 \
  -name tomcat
```

### 3. Deploy and Verify

```bash
# Start application
java -jar telegram-referral-bot-1.0.0.jar

# Verify webhook
curl https://api.telegram.org/bot<TOKEN>/getWebhookInfo
```

## Docker Deployment

### 1. Create Dockerfile

```dockerfile
FROM maven:3.8.6-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/target/telegram-referral-bot-1.0.0.jar app.jar

ENV TELEGRAM_BOT_TOKEN=""
ENV TELEGRAM_BOT_USERNAME=""
ENV PRIVATE_CHANNEL_LINK=""

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2. Create docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: telegram_bot
      POSTGRES_USER: bot_user
      POSTGRES_PASSWORD: secure_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - bot-network
    restart: unless-stopped

  bot:
    build: .
    environment:
      TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN}
      TELEGRAM_BOT_USERNAME: ${TELEGRAM_BOT_USERNAME}
      PRIVATE_CHANNEL_LINK: ${PRIVATE_CHANNEL_LINK}
      ADMIN_CHAT_IDS: ${ADMIN_CHAT_IDS}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/telegram_bot
      SPRING_DATASOURCE_USERNAME: bot_user
      SPRING_DATASOURCE_PASSWORD: secure_password
    ports:
      - "8080:8080"
    depends_on:
      - postgres
    networks:
      - bot-network
    restart: unless-stopped

volumes:
  postgres_data:

networks:
  bot-network:
    driver: bridge
```

### 3. Deploy with Docker

```bash
# Create .env file
cat > .env << EOF
TELEGRAM_BOT_TOKEN=your_token
TELEGRAM_BOT_USERNAME=your_username
PRIVATE_CHANNEL_LINK=your_link
ADMIN_CHAT_IDS=123456789
EOF

# Start services
docker-compose up -d

# View logs
docker-compose logs -f bot

# Stop services
docker-compose down
```

## Cloud Deployment

### Deploy to Heroku

```bash
# 1. Install Heroku CLI
# 2. Login
heroku login

# 3. Create app
heroku create my-telegram-bot

# 4. Add PostgreSQL
heroku addons:create heroku-postgresql:hobby-dev

# 5. Set environment variables
heroku config:set TELEGRAM_BOT_TOKEN=your_token
heroku config:set TELEGRAM_BOT_USERNAME=your_username
heroku config:set PRIVATE_CHANNEL_LINK=your_link
heroku config:set ADMIN_CHAT_IDS=123456789

# 6. Deploy
git push heroku main

# 7. Check logs
heroku logs --tail
```

### Deploy to AWS (EC2)

```bash
# 1. Launch EC2 instance (Ubuntu 22.04)
# 2. SSH into instance
ssh -i your-key.pem ubuntu@your-ec2-ip

# 3. Install Java 17
sudo apt update
sudo apt install openjdk-17-jdk

# 4. Install PostgreSQL
sudo apt install postgresql postgresql-contrib

# 5. Setup database
sudo -u postgres createdb telegram_bot
sudo -u postgres psql -c "ALTER USER postgres PASSWORD 'your_password';"

# 6. Upload JAR file
scp -i your-key.pem target/telegram-referral-bot-1.0.0.jar ubuntu@your-ec2-ip:~/

# 7. Create systemd service
sudo nano /etc/systemd/system/telegram-bot.service
```

Service file content:
```ini
[Unit]
Description=Telegram Referral Bot
After=network.target postgresql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu
ExecStart=/usr/bin/java -jar telegram-referral-bot-1.0.0.jar
Restart=on-failure
RestartSec=10

Environment="TELEGRAM_BOT_TOKEN=your_token"
Environment="TELEGRAM_BOT_USERNAME=your_username"
Environment="PRIVATE_CHANNEL_LINK=your_link"
Environment="ADMIN_CHAT_IDS=123456789"

[Install]
WantedBy=multi-user.target
```

```bash
# 8. Start service
sudo systemctl daemon-reload
sudo systemctl enable telegram-bot
sudo systemctl start telegram-bot

# 9. Check status
sudo systemctl status telegram-bot
```

### Deploy to DigitalOcean

1. Create Ubuntu Droplet
2. Follow AWS EC2 steps above
3. Optional: Setup firewall

```bash
# Allow SSH and HTTP/HTTPS
sudo ufw allow 22
sudo ufw allow 80
sudo ufw allow 443
sudo ufw enable
```

## Monitoring and Maintenance

### View Logs

```bash
# SystemD
sudo journalctl -u telegram-bot -f

# Docker
docker-compose logs -f bot

# Application logs
tail -f logs/spring.log
```

### Database Backup

```bash
# PostgreSQL backup
pg_dump -U bot_user telegram_bot > backup_$(date +%Y%m%d).sql

# Restore
psql -U bot_user telegram_bot < backup_20240101.sql
```

### Update Application

```bash
# 1. Build new version
mvn clean package -DskipTests

# 2. Stop service
sudo systemctl stop telegram-bot

# 3. Replace JAR
sudo cp target/telegram-referral-bot-1.0.0.jar /opt/telegram-bot/

# 4. Start service
sudo systemctl start telegram-bot
```

## Troubleshooting

### Check if bot is running
```bash
ps aux | grep telegram-referral-bot
```

### Check port availability
```bash
netstat -tuln | grep 8080
```

### Test database connection
```bash
psql -U bot_user -d telegram_bot -h localhost
```

### Verify webhook
```bash
curl https://api.telegram.org/bot<TOKEN>/getWebhookInfo
```

---

## Deploy to Railway (Tavsiya etiladi)

Railway platformasida loyihani 3 daqiqada ishga tushirish:

### 1. GitHub repozitoriyasini ulash
1. [railway.app](https://railway.app) ga kiring va GitHub profilingiz orqali tizimga kiring.
2. **"New Project"** -> **"Deploy from GitHub repo"** tugmasini bosing va ushbu repozitoriyani tanlang.

### 2. PostgreSQL ma'lumotlar bazasini qo'shish
1. Loyiha oynasida **"+ New"** -> **"Database"** -> **"Add PostgreSQL"** ni bosing.
2. Railway avtomatik ravishda `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` o'zgaruvchilarini taqdim etadi. Loyiha `application.yml` fayli ushbu o'zgaruvchilarni avtomatik taniydi!

### 3. Environment Variables (Muhit o'zgaruvchilari)ni sozlash
Bot xizmati (**Variables** bo'limi)ga quyidagi o'zgaruvchilarni qo'shing:

| O'zgaruvchi nomi | Tavsif | Misol |
| :--- | :--- | :--- |
| `TELEGRAM_BOT_TOKEN` | Asosiy User bot tokeni | `123456789:ABCdef...` |
| `TELEGRAM_BOT_USERNAME` | Asosiy bot username (@ siz) | `my_referral_bot` |
| `TELEGRAM_ADMIN_BOT_TOKEN` | Admin bot tokeni | `987654321:XYZabc...` |
| `TELEGRAM_ADMIN_BOT_USERNAME` | Admin bot username (@ siz) | `my_admin_bot` |
| `ADMIN_CHAT_IDS` | Adminlarning Telegram chat ID lari | `698738577,657778959` |
| `BOT_IMAGES_BASE_PATH` | Rasmlar saqlanadigan yo'l | `/app/data/images` |
| `INVITE_LINK_EXPIRE_HOURS` | Taklif havolasi amal qilish muddati | `24` |
| `INVITE_LINK_MEMBER_LIMIT` | Taklif havolasi odam soni limiti | `1` |

> **Eslatma:** Railway PostgreSQL bilan bog'lanish avtomatik amalga oshadi, qo'shimcha DB URL kiritish shart emas.

---

## Security Checklist

- [ ] Use strong database passwords
- [ ] Keep Java and dependencies updated
- [ ] Store tokens in environment variables
- [ ] Regularly backup database
- [ ] Monitor application logs
- [ ] Limit admin access

---

For more help, check the main [README.md](README.md)
