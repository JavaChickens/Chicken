# Chicken - Enterprise Minecraft Server Hosting Platform

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green.svg)](https://spring.io/projects/spring-boot)

## Overview

Chicken is a production-ready, enterprise-grade Minecraft server hosting platform that provides:

- **Web-based Management Interface** - Modern React-style web UI for server administration
- **Free Minecraft Server Hosting** - Host multiple Minecraft servers with resource management
- **Plugin Management System** - Install and manage Bukkit/Paper plugins via web interface
- **Command Line Interface** - Full CLI for server administration and automation
- **RESTful API** - Complete REST API for integration and automation
- **Multi-server Support** - Host and manage multiple Minecraft servers simultaneously

## Features

### 🌐 Web Interface
- Server creation and management with all Minecraft versions
- Real-time server status and player monitoring
- Advanced plugin marketplace with search
- Player management (admin/VIP assignment)
- Self-hosting with automatic Netlify deployment
- Modern responsive design with Bootstrap 5

### 🎮 Minecraft Server Support
- Support for ALL Minecraft versions (latest to earliest snapshots)
- Paper/Spigot/Bukkit/Vanilla server types
- Automatic server JAR downloads from official sources
- Spigot building from source with BuildTools
- Advanced plugin marketplace integration
- Real-time player statistics and management

### 💻 Command Line Interface
- Complete server lifecycle management
- Advanced plugin installation with marketplace search
- Player statistics and management commands
- Admin/VIP assignment via CLI
- Username-based admin account creation
- Bulk operations and automation support

### 🔧 Enterprise Features
- Production-ready plugin marketplace with SpigotMC/Bukkit/GitHub integration
- Multi-tenant architecture with user isolation
- Advanced player management and permissions
- Self-hosting capabilities with Node.js integration
- Comprehensive version support (1.7.2 to latest snapshots)
- GitHub Actions CI/CD pipeline
- Dependabot integration for security updates

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- 4GB+ RAM recommended
- 10GB+ disk space

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/chicken-project/chicken.git
   cd chicken
   ```

2. **Build the project**
   ```bash
   mvn clean package
   ```

3. **Run the application**
   ```bash
   java -jar target/chicken-server-host-1.0.0-RELEASE.jar
   ```

4. **Access the web interface**
   - Open http://localhost:8080 in your browser
   - Default admin credentials: admin/admin

### CLI Usage

```bash
# Create a new Minecraft server with admin user
java -jar chicken.jar server create --name "MyServer" --version "1.20.4" --type "paper" --username "YourUsername"

# Install a plugin from marketplace
java -jar chicken.jar plugin install --server "MyServer" --plugin "EssentialsX"

# Start a server
java -jar chicken.jar server start --name "MyServer"

# View server statistics and players
java -jar chicken.jar stats --server "MyServer"

# Make a player admin
java -jar chicken.jar player admin --server "MyServer" --player "PlayerName"

# Make a player VIP
java -jar chicken.jar player vip --server "MyServer" --player "PlayerName"

# List all servers with stats
java -jar chicken.jar server list

# Show system status
java -jar chicken.jar status
```

## API Documentation

The REST API is available at `/api/v1/` with comprehensive endpoints:

### Server Management
- `GET /api/v1/servers` - List all servers with filtering
- `POST /api/v1/servers` - Create a new server
- `GET /api/v1/servers/{id}` - Get server details
- `POST /api/v1/servers/{id}/start` - Start a server
- `POST /api/v1/servers/{id}/stop` - Stop a server
- `POST /api/v1/servers/{id}/restart` - Restart a server
- `DELETE /api/v1/servers/{id}` - Delete a server
- `GET /api/v1/servers/stats` - Get system statistics

### Plugin Management
- `GET /api/v1/servers/{id}/plugins` - List server plugins
- `POST /api/v1/servers/{id}/plugins` - Install a plugin
- `DELETE /api/v1/servers/{id}/plugins/{name}` - Remove a plugin
- `POST /api/v1/servers/{id}/plugins/{name}/enable` - Enable a plugin
- `POST /api/v1/servers/{id}/plugins/{name}/disable` - Disable a plugin
- `GET /api/v1/plugins/marketplace` - Search plugin marketplace
- `GET /api/v1/plugins/popular` - Get popular plugins
- `GET /api/v1/plugins/updates` - Get plugins with updates

## Configuration

Configuration is managed through `application.yml`:

```yaml
chicken:
  server:
    data-directory: ./servers
    max-servers: 50
    default-memory: 2048
    default-version: "1.20.4"
    startup-timeout: 120
    shutdown-timeout: 60
  web:
    port: 8080
    admin-user: admin
    admin-password: admin
    allow-registration: true
    session-timeout: 60
  plugin:
    cache-directory: ./plugins-cache
    auto-update: false
    download-timeout: 300
    max-plugin-size: 100
```

### Environment Variables
```bash
# Database
DB_USERNAME=chicken
DB_PASSWORD=secure_password

# Admin Account
ADMIN_USER=admin
ADMIN_PASSWORD=secure_password

# Netlify Integration
NETLIFY_TOKEN=your_netlify_token

# Server Limits
CHICKEN_SERVER_MAX_SERVERS=50
CHICKEN_PLUGIN_AUTO_UPDATE=false
```

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web Interface │    │   REST API      │    │   CLI Interface │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
         ┌─────────────────────────────────────────────────┐
         │            Core Service Layer                   │
         ├─────────────────────────────────────────────────┤
         │  Server Manager │ Plugin Manager │ File Manager │
         └─────────────────────────────────────────────────┘
                                 │
         ┌─────────────────────────────────────────────────┐
         │              Data Layer                         │
         ├─────────────────────────────────────────────────┤
         │    H2 Database    │    File System Storage      │
         └─────────────────────────────────────────────────┘
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- **Documentation**: [Wiki](https://github.com/chicken-project/chicken/wiki)
- **Issues**: [GitHub Issues](https://github.com/chicken-project/chicken/issues)
- **Discussions**: [GitHub Discussions](https://github.com/chicken-project/chicken/discussions)

## New Features in v1.0.0

### 🚀 Advanced Plugin Marketplace
- Real-time integration with SpigotMC, Bukkit, and GitHub repositories
- Advanced search functionality across multiple plugin sources
- Automatic plugin metadata extraction and version management
- Popular plugin recommendations with download statistics

### 📊 Player Management & Statistics
- Real-time player count and connection monitoring
- Admin and VIP permission management via CLI and web interface
- Player activity tracking and session management
- RCON integration for live server communication

### 🌍 Self-Hosting & Deployment
- Automatic Node.js environment setup
- Dynamic Minecraft server website generation
- Netlify integration for automatic deployment under "JavaChicken"
- Custom domain support and SSL certificate management

### 🎮 Complete Version Support
- Support for ALL Minecraft versions from 1.7.2 to latest snapshots
- Integration with Mojang's official version manifest API
- Automatic server JAR downloads for all supported versions
- Spigot building from source with BuildTools integration

### 🔧 Production Enhancements
- GitHub Actions CI/CD pipeline with automated testing
- Dependabot integration for security updates
- Docker multi-stage builds with security best practices
- Comprehensive monitoring with Prometheus and Grafana

## Acknowledgments

- Spring Boot team for the excellent framework
- Paper team for the Minecraft server implementation
- Bukkit and SpigotMC communities for plugin ecosystem
- Mojang for Minecraft version manifest API
- Netlify for hosting platform integration