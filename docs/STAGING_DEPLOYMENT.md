# Staging Deployment Guide

## Prerequisites

1. **VPS/Server** with Docker + Docker Compose installed
2. **MySQL 8.0** (external, can use managed DB like RDS, Cloud SQL, or self-hosted)
3. **Domain/subdomain** pointed to your server (optional but recommended)
4. **GitHub repository** with Actions secrets configured

## Step 1: Configure GitHub Secrets

Go to **Settings → Secrets and variables → Actions** in your GitHub repository and add:

| Secret Name | Description | Example |
|---|---|---|
| `SSH_STAGING_HOST` | Server IP or hostname | `103.98.123.456` |
| `SSH_STAGING_USER` | SSH username | `deploy` |
| `SSH_STAGING_KEY` | Private SSH key (with write access) | `-----BEGIN OPENSSH...` |
| `MYSQL_HOST` | MySQL host | `localhost` or `rds.endpoint.amazonaws.com` |
| `MYSQL_PORT` | MySQL port | `3306` |
| `MYSQL_DATABASE` | Database name | `hospital_scheduler` |
| `MYSQL_USER` | MySQL username | `hospital` |
| `MYSQL_PASSWORD` | MySQL password | `your-password` |
| `JWT_SECRET` | JWT signing secret (min 32 chars) | `your-secret-key-here` |
| `MAIL_HOST` | SMTP host | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP port | `587` |
| `MAIL_USERNAME` | SMTP username | `your-email@gmail.com` |
| `MAIL_PASSWORD` | SMTP password/App password | `xxxx xxxx xxxx xxxx` |
| `APP_EMAIL_ENABLED` | Enable emails | `true` |
| `APP_EMAIL_FROM` | From email address | `noreply@hospital-scheduler.example.com` |
| `APP_EMAIL_CONFLICT_ENABLED` | Enable conflict alerts | `true` |
| `NEXT_PUBLIC_API_URL` | API URL for frontend | `https://staging-api.example.com/api/v1` |
| `NEXT_PUBLIC_WS_URL` | WebSocket URL | `wss://staging-api.example.com/ws` |

## Step 2: Prepare Server

```bash
# SSH into your server
ssh deploy@your-server-ip

# Create app directory
sudo mkdir -p /opt/hospital-scheduler
sudo chown deploy:deploy /opt/hospital-scheduler

# Install Docker if not present
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker deploy

# Log out and back in, then:
cd /opt/hospital-scheduler

# Copy docker-compose.staging.yml from the repo
# (via git clone or scp)
```

## Step 3: Configure Environment

```bash
# Create environment file
nano .env.staging
```

Copy from `.env.staging.example` and fill in real values:

```bash
SPRING_PROFILE=staging

# MySQL
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=hospital_scheduler
MYSQL_USER=hospital
MYSQL_PASSWORD=your-password

# Backend
BACKEND_PORT=8080
JWT_SECRET=your-32-char-minimum-secret
LOG_LEVEL=INFO

# Email
APP_EMAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
APP_EMAIL_FROM=noreply@your-domain.com
APP_EMAIL_CONFLICT_ENABLED=true

# Frontend
FRONTEND_PORT=3000
NEXT_PUBLIC_API_URL=https://staging.your-domain.com/api/v1
NEXT_PUBLIC_WS_URL=wss://staging.your-domain.com/ws
```

## Step 4: First-Time Database Setup

If using a new MySQL instance:

```sql
CREATE DATABASE hospital_scheduler CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'hospital'@'%' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON hospital_scheduler.* TO 'hospital'@'%';
FLUSH PRIVILEGES;
```

Then run the schema:

```bash
docker run --rm -e MYSQL_HOST=your-mysql-host \
  -e MYSQL_DATABASE=hospital_scheduler \
  -e MYSQL_USER=hospital \
  -e MYSQL_PASSWORD=your-password \
  ghcr.io/your-username/hospital-scheduler-backend:latest \
  --spring.jpa.hibernate.ddl-auto=update
```

Or manually import `hospital_scheduler_business_final.sql`.

## Step 5: Deploy

Every push to `main` automatically triggers staging deployment via GitHub Actions.

Manual deploy:

```bash
cd /opt/hospital-scheduler
docker compose -f docker-compose.staging.yml --env-file .env.staging pull
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d
docker compose -f docker-compose.staging.yml --env-file .env.staging logs -f
```

## Step 6: Verify

```bash
# Check health
curl http://localhost:8080/actuator/health
curl http://localhost:3000

# Check logs
docker compose -f docker-compose.staging.yml logs backend --tail=50
docker compose -f docker-compose.staging.yml logs frontend --tail=50
```

## Rollback

```bash
# Stop current
docker compose -f docker-compose.staging.yml down

# Restore previous compose file
cp docker-compose.staging.yml.bak docker-compose.staging.yml

# Start with previous version
docker compose -f docker-compose.staging.yml up -d
```

## Nginx Reverse Proxy (Recommended)

```nginx
# /etc/nginx/sites-available/hospital-staging
server {
    listen 80;
    server_name staging.your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }

    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

Then enable HTTPS with certbot:
```bash
sudo certbot --nginx -d staging.your-domain.com
```
