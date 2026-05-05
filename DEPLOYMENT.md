# Deployment Guide

## Production Deployment Checklist

### Pre-Deployment

- [ ] Update all environment variables in production
- [ ] Generate strong JWT secret (at least 32 bytes)
- [ ] Set secure admin password (BCrypt hashed recommended)
- [ ] Configure production database connection
- [ ] Set up SMTP credentials (Gmail App Password or AWS SES)
- [ ] Configure CORS allowed origins for production domain
- [ ] Review and update `application-prod.yml`
- [ ] Run all tests: `./mvnw test`
- [ ] Build production JAR: `./mvnw clean package -DskipTests`

### Environment Variables (Production)

```bash
# Database
DATABASE_URL=jdbc:postgresql://your-db-host:5432/sri_karthikeya_caterers
DB_USERNAME=your_db_user
DB_PASSWORD=your_secure_db_password

# Admin Credentials
ADMIN_PASSWORD=$2a$10$... # BCrypt hashed password

# JWT
JWT_SECRET=your-production-jwt-secret-at-least-32-bytes-long

# SMTP
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=info@srikarthikeyacaterers.in
SMTP_PASSWORD=your_gmail_app_password

# Application
REVIEW_BASE_URL=https://srikarthikeyacaterers.in
FRONTEND_URL=https://srikarthikeyacaterers.in
PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

### Database Setup

1. **Create Production Database**
```sql
CREATE DATABASE sri_karthikeya_caterers;
CREATE USER skc_user WITH ENCRYPTED PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE sri_karthikeya_caterers TO skc_user;
```

2. **Run Schema (if not using auto-creation)**
```bash
psql -U skc_user -d sri_karthikeya_caterers -f src/main/resources/schema.sql
```

3. **Verify Tables**
```sql
\dt
```

### Deployment Options

## Option 1: Traditional Server (Linux)

### 1. Install Java 21
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-21-jdk

# Verify
java -version
```

### 2. Upload JAR
```bash
scp target/skc-0.0.1-SNAPSHOT.jar user@server:/opt/skc/
```

### 3. Create Systemd Service
```bash
sudo nano /etc/systemd/system/skc.service
```

```ini
[Unit]
Description=Sri Karthikeya Caterers Backend
After=network.target postgresql.service

[Service]
Type=simple
User=skc
WorkingDirectory=/opt/skc
ExecStart=/usr/bin/java -jar /opt/skc/skc-0.0.1-SNAPSHOT.jar
EnvironmentFile=/opt/skc/.env
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 4. Start Service
```bash
sudo systemctl daemon-reload
sudo systemctl enable skc
sudo systemctl start skc
sudo systemctl status skc
```

### 5. View Logs
```bash
sudo journalctl -u skc -f
```

## Option 2: Docker

### 1. Create Dockerfile
```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/skc-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2. Build Image
```bash
docker build -t skc-backend:latest .
```

### 3. Run Container
```bash
docker run -d \
  --name skc-backend \
  -p 8080:8080 \
  --env-file .env \
  --restart unless-stopped \
  skc-backend:latest
```

### 4. Docker Compose (with PostgreSQL)
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:14-alpine
    environment:
      POSTGRES_DB: sri_karthikeya_caterers
      POSTGRES_USER: skc_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    restart: unless-stopped

  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/sri_karthikeya_caterers
      DB_USERNAME: skc_user
      DB_PASSWORD: ${DB_PASSWORD}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      SMTP_USERNAME: ${SMTP_USERNAME}
      SMTP_PASSWORD: ${SMTP_PASSWORD}
      REVIEW_BASE_URL: ${REVIEW_BASE_URL}
      FRONTEND_URL: ${FRONTEND_URL}
      SPRING_PROFILES_ACTIVE: prod
    depends_on:
      - postgres
    restart: unless-stopped

volumes:
  postgres_data:
```

Run:
```bash
docker-compose up -d
```

## Option 3: AWS Elastic Beanstalk

### 1. Install EB CLI
```bash
pip install awsebcli
```

### 2. Initialize
```bash
eb init -p "Corretto 21" skc-backend --region us-east-1
```

### 3. Create Environment
```bash
eb create skc-prod --database.engine postgres --database.username skc_user
```

### 4. Set Environment Variables
```bash
eb setenv \
  ADMIN_PASSWORD=$ADMIN_PASSWORD \
  JWT_SECRET=$JWT_SECRET \
  SMTP_USERNAME=$SMTP_USERNAME \
  SMTP_PASSWORD=$SMTP_PASSWORD \
  REVIEW_BASE_URL=https://your-domain.com \
  FRONTEND_URL=https://your-domain.com \
  SPRING_PROFILES_ACTIVE=prod
```

### 5. Deploy
```bash
eb deploy
```

## Option 4: Heroku

### 1. Create Heroku App
```bash
heroku create skc-backend
```

### 2. Add PostgreSQL
```bash
heroku addons:create heroku-postgresql:mini
```

### 3. Set Environment Variables
```bash
heroku config:set \
  ADMIN_PASSWORD=$ADMIN_PASSWORD \
  JWT_SECRET=$JWT_SECRET \
  SMTP_USERNAME=$SMTP_USERNAME \
  SMTP_PASSWORD=$SMTP_PASSWORD \
  REVIEW_BASE_URL=https://your-domain.com \
  FRONTEND_URL=https://your-domain.com \
  SPRING_PROFILES_ACTIVE=prod
```

### 4. Deploy
```bash
git push heroku main
```

## Option 5: Google Cloud Run

### 1. Build Container
```bash
gcloud builds submit --tag gcr.io/PROJECT_ID/skc-backend
```

### 2. Deploy
```bash
gcloud run deploy skc-backend \
  --image gcr.io/PROJECT_ID/skc-backend \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars="SPRING_PROFILES_ACTIVE=prod,..." \
  --add-cloudsql-instances=PROJECT_ID:REGION:INSTANCE
```

## Post-Deployment

### 1. Health Check
```bash
curl https://your-domain.com/api/public/reviews/featured
```

### 2. Test Admin Login
```bash
curl -X POST https://your-domain.com/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"info@srikarthikeyacaterers.in","password":"your_password"}'
```

### 3. Monitor Logs
- Check application logs for errors
- Monitor database connections
- Watch for failed email sends

### 4. Set Up Monitoring
- Application performance monitoring (APM)
- Database monitoring
- Error tracking (Sentry, Rollbar)
- Uptime monitoring

### 5. Configure Backups
- Database backups (daily)
- Application logs archival
- Configuration backups

## Nginx Reverse Proxy (Optional)

```nginx
server {
    listen 80;
    server_name api.srikarthikeyacaterers.in;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable HTTPS with Let's Encrypt:
```bash
sudo certbot --nginx -d api.srikarthikeyacaterers.in
```

## Security Hardening

1. **Firewall Rules**
```bash
# Allow only necessary ports
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

2. **Database Security**
- Use strong passwords
- Restrict database access to application server only
- Enable SSL for database connections
- Regular security updates

3. **Application Security**
- Keep dependencies updated
- Use HTTPS only
- Implement rate limiting
- Regular security audits

4. **Secrets Management**
- Use AWS Secrets Manager / Google Secret Manager
- Never commit secrets to git
- Rotate secrets regularly

## Rollback Procedure

### Systemd Service
```bash
sudo systemctl stop skc
# Replace JAR with previous version
sudo systemctl start skc
```

### Docker
```bash
docker stop skc-backend
docker run -d --name skc-backend skc-backend:previous-tag
```

### Elastic Beanstalk
```bash
eb deploy --version previous-version
```

## Troubleshooting

### Application Won't Start
1. Check Java version: `java -version`
2. Verify environment variables
3. Check database connectivity
4. Review application logs

### Database Connection Errors
1. Verify database is running
2. Check connection string
3. Verify credentials
4. Check firewall rules

### Email Sending Fails
1. Verify SMTP credentials
2. Check Gmail App Password
3. Review email logs in `system_logs` table
4. Test SMTP connection manually

### High Memory Usage
1. Adjust JVM heap size: `-Xmx512m -Xms256m`
2. Monitor with: `jstat -gc <pid> 1000`
3. Enable GC logging

## Maintenance

### Regular Tasks
- [ ] Weekly: Review application logs
- [ ] Weekly: Check database size and performance
- [ ] Monthly: Update dependencies
- [ ] Monthly: Review and rotate secrets
- [ ] Quarterly: Security audit
- [ ] Quarterly: Performance optimization

### Backup Strategy
- Database: Daily automated backups, 30-day retention
- Application logs: Weekly archival
- Configuration: Version controlled in git

## Support

For deployment issues:
- Email: info@srikarthikeyacaterers.in
- Check logs: `sudo journalctl -u skc -f`
- Review documentation: README.md, QUICKSTART.md
