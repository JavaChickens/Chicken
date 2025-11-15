# Chicken - Deployment Guide

This guide provides comprehensive instructions for deploying the Chicken Minecraft Server Hosting Platform in various environments, from development to enterprise production.

## Table of Contents

- [Quick Start](#quick-start)
- [System Requirements](#system-requirements)
- [Development Deployment](#development-deployment)
- [Production Deployment](#production-deployment)
- [Docker Deployment](#docker-deployment)
- [Cloud Deployment](#cloud-deployment)
- [Configuration](#configuration)
- [Monitoring & Maintenance](#monitoring--maintenance)
- [Troubleshooting](#troubleshooting)

## Quick Start

### Windows
```cmd
# Clone the repository
git clone https://github.com/JavaChickens/Chicken.git
cd chicken

# Run the startup script
scripts\start.bat
```

### Linux/macOS
```bash
# Clone the repository
git clone https://github.com/JavaChickens/Chicken.git
cd chicken

# Make script executable and run
chmod +x scripts/start.sh
./scripts/start.sh
```

### Docker
```bash
# Quick start with Docker Compose
docker-compose up -d

# Access the application
open http://localhost:8080
```

## System Requirements

### Minimum Requirements
- **CPU**: 2 cores, 2.0 GHz
- **RAM**: 4 GB (2 GB for application + 2 GB for Minecraft servers)
- **Storage**: 10 GB free space
- **OS**: Windows 10+, Linux (Ubuntu 18.04+), macOS 10.14+
- **Java**: OpenJDK 17 or higher
- **Network**: Internet connection for plugin downloads

### Recommended Requirements
- **CPU**: 4+ cores, 3.0 GHz
- **RAM**: 16 GB (4 GB for application + 12 GB for Minecraft servers)
- **Storage**: 100 GB SSD
- **OS**: Windows Server 2019+, Ubuntu 20.04 LTS+, CentOS 8+
- **Java**: OpenJDK 17 LTS
- **Network**: Dedicated server with static IP

### Enterprise Requirements
- **CPU**: 8+ cores, 3.5 GHz
- **RAM**: 32+ GB
- **Storage**: 500+ GB NVMe SSD
- **OS**: Enterprise Linux distribution
- **Database**: PostgreSQL 13+ or MySQL 8+
- **Load Balancer**: Nginx, HAProxy, or cloud load balancer
- **Monitoring**: Prometheus + Grafana stack

## Development Deployment

### Prerequisites
1. Install Java 17+
2. Install Maven 3.6+
3. Install Git

### Setup Steps

1. **Clone and Build**
   ```bash
   git clone https://github.com/JavaChickens/Chicken.git
   cd chicken
   mvn clean package -DskipTests
   ```

2. **Run Application**
   ```bash
   java -jar target/chicken-server-host-1.0.0-RELEASE.jar --spring.profiles.active=dev
   ```

3. **Access Application**
   - Web Interface: http://localhost:8080
   - H2 Console: http://localhost:8080/h2-console
   - API Health: http://localhost:8080/actuator/health

### Development Configuration
```yaml
# application-dev.yml
spring:
  profiles:
    active: dev
  h2:
    console:
      enabled: true
      settings:
        web-allow-others: true

logging:
  level:
    com.chicken: DEBUG
    
chicken:
  server:
    max-servers: 5
    startup-timeout: 60
```

## Production Deployment

### Option 1: Standalone JAR

1. **Build Production JAR**
   ```bash
   mvn clean package -Pprod -DskipTests
   ```

2. **Create System User**
   ```bash
   sudo useradd -r -s /bin/false chicken
   sudo mkdir -p /opt/chicken/{servers,plugins-cache,logs}
   sudo chown -R chicken:chicken /opt/chicken
   ```

3. **Install as System Service**
   ```bash
   # Copy JAR file
   sudo cp target/chicken-server-host-1.0.0-RELEASE.jar /opt/chicken/
   
   # Create systemd service
   sudo tee /etc/systemd/system/chicken.service > /dev/null <<EOF
   [Unit]
   Description=Chicken Minecraft Server Host
   After=network.target
   
   [Service]
   Type=simple
   User=chicken
   Group=chicken
   WorkingDirectory=/opt/chicken
   ExecStart=/usr/bin/java -Xmx4g -Xms2g -XX:+UseG1GC -jar chicken-server-host-1.0.0-RELEASE.jar --spring.profiles.active=prod
   Restart=always
   RestartSec=10
   StandardOutput=journal
   StandardError=journal
   
   [Install]
   WantedBy=multi-user.target
   EOF
   
   # Enable and start service
   sudo systemctl daemon-reload
   sudo systemctl enable chicken
   sudo systemctl start chicken
   ```

4. **Configure Nginx Reverse Proxy**
   ```nginx
   server {
       listen 80;
       server_name your-domain.com;
       
       location / {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```

### Option 2: Database-Backed Deployment

1. **Setup PostgreSQL**
   ```bash
   # Install PostgreSQL
   sudo apt install postgresql postgresql-contrib
   
   # Create database and user
   sudo -u postgres psql
   CREATE DATABASE chicken;
   CREATE USER chicken WITH PASSWORD 'secure_password';
   GRANT ALL PRIVILEGES ON DATABASE chicken TO chicken;
   \q
   ```

2. **Configure Application**
   ```yaml
   # application-prod.yml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/chicken
       username: chicken
       password: ${DB_PASSWORD}
       driver-class-name: org.postgresql.Driver
     jpa:
       hibernate:
         ddl-auto: validate
       properties:
         hibernate:
           dialect: org.hibernate.dialect.PostgreSQLDialect
   ```

3. **Run Database Migrations**
   ```bash
   java -jar chicken-server-host-1.0.0-RELEASE.jar --spring.profiles.active=prod --spring.jpa.hibernate.ddl-auto=update
   ```

## Docker Deployment

### Single Container
```bash
# Build image
docker build -t chicken-server-host .

# Run container
docker run -d \
  --name chicken \
  -p 8080:8080 \
  -v chicken_data:/app/data \
  -v chicken_logs:/app/logs \
  -e SPRING_PROFILES_ACTIVE=docker \
  chicken-server-host
```

### Docker Compose (Recommended)
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f chicken-app

# Scale application
docker-compose up -d --scale chicken-app=3

# Stop services
docker-compose down
```

### Docker Swarm
```bash
# Initialize swarm
docker swarm init

# Deploy stack
docker stack deploy -c docker-compose.yml chicken

# Scale services
docker service scale chicken_chicken-app=3

# Remove stack
docker stack rm chicken
```

## Cloud Deployment

### AWS Deployment

#### EC2 Instance
1. **Launch EC2 Instance**
   - AMI: Amazon Linux 2 or Ubuntu 20.04 LTS
   - Instance Type: t3.medium (minimum) or m5.large (recommended)
   - Security Group: Allow ports 22, 80, 443, 8080

2. **Install Dependencies**
   ```bash
   # Amazon Linux 2
   sudo yum update -y
   sudo yum install -y java-17-amazon-corretto docker
   
   # Ubuntu
   sudo apt update
   sudo apt install -y openjdk-17-jdk docker.io docker-compose
   ```

3. **Deploy Application**
   ```bash
   git clone https://github.com/chicken-project/chicken.git
   cd chicken
   docker-compose up -d
   ```

#### ECS Deployment
```json
{
  "family": "chicken-server-host",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "4096",
  "executionRoleArn": "arn:aws:iam::account:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "chicken-app",
      "image": "chicken-server-host:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod,aws"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/chicken-server-host",
          "awslogs-region": "us-west-2",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

### Google Cloud Platform

#### Compute Engine
```bash
# Create instance
gcloud compute instances create chicken-server \
  --image-family=ubuntu-2004-lts \
  --image-project=ubuntu-os-cloud \
  --machine-type=e2-standard-4 \
  --boot-disk-size=100GB \
  --tags=http-server,https-server

# SSH and deploy
gcloud compute ssh chicken-server
git clone https://github.com/chicken-project/chicken.git
cd chicken
./scripts/start.sh
```

#### Cloud Run
```yaml
# cloudbuild.yaml
steps:
  - name: 'gcr.io/cloud-builders/docker'
    args: ['build', '-t', 'gcr.io/$PROJECT_ID/chicken-server-host', '.']
  - name: 'gcr.io/cloud-builders/docker'
    args: ['push', 'gcr.io/$PROJECT_ID/chicken-server-host']
  - name: 'gcr.io/cloud-builders/gcloud'
    args:
      - 'run'
      - 'deploy'
      - 'chicken-server-host'
      - '--image'
      - 'gcr.io/$PROJECT_ID/chicken-server-host'
      - '--region'
      - 'us-central1'
      - '--platform'
      - 'managed'
      - '--allow-unauthenticated'
```

### Azure Deployment

#### Container Instances
```bash
# Create resource group
az group create --name chicken-rg --location eastus

# Deploy container
az container create \
  --resource-group chicken-rg \
  --name chicken-server-host \
  --image chicken-server-host:latest \
  --cpu 2 \
  --memory 4 \
  --ports 8080 \
  --environment-variables SPRING_PROFILES_ACTIVE=prod
```

## Configuration

### Environment Variables
```bash
# Database Configuration
DB_USERNAME=chicken
DB_PASSWORD=secure_password
DB_URL=jdbc:postgresql://localhost:5432/chicken

# Application Configuration
SPRING_PROFILES_ACTIVE=prod
JAVA_OPTS=-Xmx4g -Xms2g -XX:+UseG1GC

# Security Configuration
ADMIN_USER=admin
ADMIN_PASSWORD=secure_admin_password

# Server Configuration
CHICKEN_SERVER_MAX_SERVERS=50
CHICKEN_SERVER_DEFAULT_MEMORY=2048
CHICKEN_PLUGIN_AUTO_UPDATE=false
```

### Configuration Files

#### Production Configuration
```yaml
# application-prod.yml
server:
  port: 8080
  compression:
    enabled: true

spring:
  datasource:
    url: ${DB_URL:jdbc:h2:file:./data/chicken}
    username: ${DB_USERNAME:chicken}
    password: ${DB_PASSWORD:chicken123}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

logging:
  level:
    root: WARN
    com.chicken: INFO
  file:
    name: /var/log/chicken/chicken.log

chicken:
  server:
    data-directory: /opt/chicken/servers
    max-servers: ${CHICKEN_SERVER_MAX_SERVERS:50}
    default-memory: ${CHICKEN_SERVER_DEFAULT_MEMORY:2048}
  web:
    admin-user: ${ADMIN_USER:admin}
    admin-password: ${ADMIN_PASSWORD:changeme}
    allow-registration: false
  plugin:
    cache-directory: /opt/chicken/plugins-cache
    auto-update: ${CHICKEN_PLUGIN_AUTO_UPDATE:false}
```

## Monitoring & Maintenance

### Health Checks
```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed health with metrics
curl http://localhost:8080/actuator/health/detailed

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

### Log Management
```bash
# View application logs
tail -f /var/log/chicken/chicken.log

# View system service logs
sudo journalctl -u chicken -f

# Docker logs
docker-compose logs -f chicken-app
```

### Database Maintenance
```sql
-- Check database size
SELECT pg_size_pretty(pg_database_size('chicken'));

-- Vacuum and analyze tables
VACUUM ANALYZE minecraft_servers;
VACUUM ANALYZE server_plugins;

-- Check for long-running queries
SELECT pid, now() - pg_stat_activity.query_start AS duration, query 
FROM pg_stat_activity 
WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';
```

### Backup Procedures
```bash
# Database backup
pg_dump -h localhost -U chicken chicken > chicken_backup_$(date +%Y%m%d_%H%M%S).sql

# Server data backup
tar -czf chicken_servers_backup_$(date +%Y%m%d_%H%M%S).tar.gz /opt/chicken/servers/

# Automated backup script
#!/bin/bash
BACKUP_DIR="/opt/backups/chicken"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR
pg_dump -h localhost -U chicken chicken > $BACKUP_DIR/db_backup_$DATE.sql
tar -czf $BACKUP_DIR/servers_backup_$DATE.tar.gz /opt/chicken/servers/

# Keep only last 7 days of backups
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete
find $BACKUP_DIR -name "*.tar.gz" -mtime +7 -delete
```

## Troubleshooting

### Common Issues

#### Port Already in Use
```bash
# Find process using port 8080
sudo netstat -tulpn | grep :8080
sudo lsof -i :8080

# Kill process
sudo kill -9 <PID>
```

#### Out of Memory Errors
```bash
# Check system memory
free -h

# Check Java heap usage
jstat -gc <PID>

# Adjust JVM settings
export JAVA_OPTS="-Xmx8g -Xms4g -XX:+UseG1GC"
```

#### Database Connection Issues
```bash
# Test database connection
psql -h localhost -U chicken -d chicken -c "SELECT version();"

# Check database logs
sudo tail -f /var/log/postgresql/postgresql-13-main.log
```

#### Server Creation Failures
```bash
# Check disk space
df -h

# Check permissions
ls -la /opt/chicken/servers/

# Check server logs
tail -f /opt/chicken/logs/chicken.log
```

### Performance Tuning

#### JVM Tuning
```bash
# G1GC for low latency
-XX:+UseG1GC -XX:MaxGCPauseMillis=200

# Parallel GC for throughput
-XX:+UseParallelGC -XX:ParallelGCThreads=4

# Memory settings
-Xmx8g -Xms4g -XX:NewRatio=3

# GC logging
-Xlog:gc*:gc.log:time,tags
```

#### Database Tuning
```sql
-- PostgreSQL configuration
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = 100;
SELECT pg_reload_conf();
```

#### System Tuning
```bash
# Increase file descriptor limits
echo "chicken soft nofile 65536" >> /etc/security/limits.conf
echo "chicken hard nofile 65536" >> /etc/security/limits.conf

# Optimize network settings
echo "net.core.somaxconn = 65535" >> /etc/sysctl.conf
echo "net.ipv4.tcp_max_syn_backlog = 65535" >> /etc/sysctl.conf
sysctl -p
```

### Support and Documentation

- **GitHub Issues**: https://github.com/chicken-project/chicken/issues
- **Documentation**: https://github.com/chicken-project/chicken/wiki
- **Community Discord**: https://discord.gg/chicken-project
- **Professional Support**: support@chicken-project.com

For additional help, please check the troubleshooting section in the main README or create an issue on GitHub with detailed information about your environment and the problem you're experiencing.
