# Log360Cloud & DataSecurity Plus Integration Architecture

Comprehensive architecture documentation for integrating ManageEngine Log360Cloud (SIEM) with DataSecurity Plus (DLP) for unified security monitoring and data loss prevention.

## 📋 Documentation Overview

This repository contains complete architecture documentation including:

- **Architecture Document** ([ARCHITECTURE.md](ARCHITECTURE.md))
  - Executive summary and integration benefits
  - High-level system architecture
  - Database design with ERD
  - Agent deployment strategy
  - Class diagrams and object models
  - Integration workflows
  - Security and compliance considerations
  - Deployment architectures (Small, Medium, Enterprise)
  - Implementation roadmap

- **Database Schema** ([database-schema.sql](database-schema.sql))
  - Complete PostgreSQL/SQL Server DDL
  - Multi-tenant support
  - Security events and DLP events tables
  - Correlation and alert management
  - Audit logging and compliance
  - Performance-optimized indexes
  - Sample views for reporting

- **Class Diagram** ([class-diagram.puml](class-diagram.puml))
  - PlantUML diagram showing all classes
  - Core entities (Organization, Tenant, User)
  - Agent management (Log360Agent, DLPAgent)
  - Event management (SecurityEvent, DLPEvent)
  - Policy and alert management
  - Integration and correlation components

- **Sequence Diagrams**
  - [DLP-SIEM Integration Flow](sequence-diagram-dlp-siem.puml)
  - [Agent Deployment Process](sequence-diagram-agent-deployment.puml)

- **Implementation Guide** ([IMPLEMENTATION-GUIDE.md](IMPLEMENTATION-GUIDE.md))
  - Prerequisites and system requirements
  - Step-by-step installation instructions
  - Configuration examples (policies, correlation rules, dashboards)
  - Testing and validation procedures
  - Troubleshooting guide
  - Best practices

## 🎯 Key Features

### Unified Security Platform
- **Single Pane of Glass**: Centralized monitoring of DLP events and security incidents
- **Real-time Correlation**: Detect complex threats by correlating file activities with security events
- **Cloud-Native SIEM**: Unlimited scalability with Log360Cloud

### Dual Agent Architecture
- **Independent Agents**: Log360Cloud and DLP agents operate without conflicts
- **Port Management**: Automatic port conflict resolution
- **Offline Buffering**: 2GB local cache for both agents during network outages

### Advanced DLP Capabilities
- **Content-Based Detection**: Regex patterns for PII, PHI, PCI data
- **Device Control**: Block USB, CD, Bluetooth, WiFi
- **File Quarantine**: Automatic quarantine of sensitive files
- **Policy Enforcement**: Real-time blocking with user notifications

### Enterprise-Grade Features
- **Multi-Tenancy**: MSSP support with complete tenant isolation
- **Compliance**: GDPR, HIPAA, PCI-DSS, SOX reporting
- **Audit Trails**: Immutable logs with encryption
- **Role-Based Access Control**: Granular permissions

## 🚀 Quick Start

### 1. Review Architecture
Start with [ARCHITECTURE.md](ARCHITECTURE.md) to understand the integration design.

### 2. Set Up Database
```bash
# PostgreSQL
psql -U postgres -d your_database -f database-schema.sql

# SQL Server
sqlcmd -S your_server -d your_database -i database-schema.sql
```

### 3. Deploy Agents
Follow the [IMPLEMENTATION-GUIDE.md](IMPLEMENTATION-GUIDE.md) for step-by-step deployment.

### 4. Configure Integration
```yaml
# DataSecurity Plus → Log360Cloud
Protocol: Syslog
Format: JSON
Port: 514/6514
```

## 📊 Viewing Diagrams

### PlantUML Diagrams
To view the PlantUML diagrams:

**Option 1: Online**
- Visit [PlantUML Online](http://www.plantuml.com/plantuml/uml/)
- Paste the contents of `.puml` files

**Option 2: VS Code**
- Install [PlantUML extension](https://marketplace.visualstudio.com/items?itemName=jebbs.plantuml)
- Open `.puml` files and press `Alt+D`

**Option 3: Command Line**
```bash
# Install PlantUML
brew install plantuml  # macOS
apt install plantuml   # Ubuntu

# Generate PNG
plantuml class-diagram.puml
plantuml sequence-diagram-dlp-siem.puml
plantuml sequence-diagram-agent-deployment.puml
```

## 🏗️ Architecture Highlights

### System Components
```
┌─────────────────────────────────────┐
│     Log360Cloud (Cloud SIEM)        │
│  - Event Correlation                │
│  - Threat Detection                 │
│  - Compliance Reporting             │
└───────────────▲─────────────────────┘
                │ HTTPS/TLS
┌───────────────┴─────────────────────┐
│        On-Premises Layer            │
│  ┌──────────────┐  ┌──────────────┐ │
│  │ Log360 Agent │  │ DSP Server   │ │
│  └──────────────┘  └──────────────┘ │
│  ┌──────────────────────────────────┐│
│  │     DLP Agents (Endpoints)       ││
│  └──────────────────────────────────┘│
└─────────────────────────────────────┘
```

### Database Schema Highlights
- **14+ Core Tables**: Organizations, Tenants, Users, Agents, Events, Policies, Alerts
- **Multi-Tenant**: Complete isolation between tenants
- **Scalable**: Partitioning support for 10M+ events/day
- **Compliant**: Audit trails and data retention policies

### Integration Flow
1. **File Activity** → DLP Agent captures file event
2. **Policy Evaluation** → DLP engine enforces policy
3. **Local Action** → Block/Allow/Quarantine file
4. **Syslog Forwarding** → DSP sends event to Log360 Agent
5. **Cloud Upload** → Log360 Agent uploads to cloud SIEM
6. **Correlation** → Events correlated with security incidents
7. **Alert** → SOC analyst notified of threats

## 🔧 Use Cases

### 1. Insider Threat Detection
Correlate DLP violations with after-hours access and unusual file activities.

### 2. Ransomware Detection
Detect mass file modifications combined with suspicious process execution.

### 3. Data Exfiltration Prevention
Block sensitive data transfers to USB, email, cloud storage.

### 4. Compliance Monitoring
Track PII/PHI/PCI access with comprehensive audit trails.

## 📈 Deployment Sizes

| Size | Endpoints | Agents | Server Specs |
|------|-----------|--------|--------------|
| Small | < 500 | Log360 + DLP | 8GB RAM, 4 vCPU |
| Medium | 500-5000 | Log360 + DLP | 32GB RAM, 16 vCPU |
| Enterprise | > 5000 | Log360 + DLP | 64GB RAM, 32 vCPU (Cluster) |

## 🔐 Security Features

- **Encryption**: TLS 1.2+ for all agent communication
- **Authentication**: MFA support, OAuth 2.0
- **Authorization**: RBAC with granular permissions
- **Audit**: Immutable audit logs for all actions
- **Compliance**: Built-in templates for GDPR, HIPAA, PCI-DSS

## 📞 Support & Resources

- [ManageEngine Log360Cloud](https://www.manageengine.com/cloud-siem/)
- [DataSecurity Plus Documentation](https://www.manageengine.com/data-security/)
- [SIEM Integration Guide](https://www.manageengine.com/data-security/help/settings/siem-integration.html)

## 📝 License

This documentation is provided for reference and implementation purposes.

## 🤝 Contributing

This is an architecture reference document. For implementation-specific questions, consult the ManageEngine support team.

---

**Version**: 1.0  
**Last Updated**: December 2024  
**Author**: Enterprise Security Architecture Team