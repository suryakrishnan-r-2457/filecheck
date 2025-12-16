# Visual Architecture Diagrams

## 1. Component Architecture

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         CLOUD TIER - Log360Cloud                           │
│                                                                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐       │
│  │   Web Console    │  │  Analytics       │  │  Threat Intel    │       │
│  │   & Dashboards   │  │  Engine          │  │  Feed            │       │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘       │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │              SIEM Core - Event Processing                         │    │
│  │  - Event Ingestion (700+ log sources)                             │    │
│  │  - Real-time Correlation Engine                                   │    │
│  │  - Anomaly Detection (ML-based)                                   │    │
│  │  - Alert Generation & Routing                                     │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │              Zoho Logs Cloud Storage & Indexing                   │    │
│  │  - Distributed File System (DFS)                                  │    │
│  │  - AES-256 Encryption at Rest                                     │    │
│  │  - Multi-region Replication                                       │    │
│  │  - Configurable Retention (30 days - 7 years)                     │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                            │
└────────────────────────────┬───────────────────────────────────────────────┘
                             │
                             │ HTTPS/TLS (Port 443)
                             │ Encrypted Log Transmission
                             │
┌────────────────────────────┴───────────────────────────────────────────────┐
│                      ON-PREMISES TIER - Integration Layer                  │
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │                    Log360Cloud Agent (Collector)                     │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐        │ │
│  │  │ Log Collector  │  │ Syslog Server  │  │  Forwarder     │        │ │
│  │  │ (Multi-source) │  │ (Port 514/6514)│  │  to Cloud      │        │ │
│  │  └────────────────┘  └────────────────┘  └────────────────┘        │ │
│  │  ┌──────────────────────────────────────────────────────────────┐  │ │
│  │  │ Offline Buffer: 2GB cache for network outages               │  │ │
│  │  └──────────────────────────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                    ▲                                       │
│                                    │ Syslog (RFC 5424, JSON)               │
│                                    │                                       │
│  ┌─────────────────────────────────┴───────────────────────────────────┐ │
│  │              DataSecurity Plus Server (DLP Management)              │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐        │ │
│  │  │  DLP Engine    │  │ Agent Manager  │  │ Policy Manager │        │ │
│  │  └────────────────┘  └────────────────┘  └────────────────┘        │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐        │ │
│  │  │ PostgreSQL/    │  │ Syslog         │  │ Web Console    │        │ │
│  │  │ SQL Server DB  │  │ Forwarder      │  │ (Port 8443)    │        │ │
│  │  └────────────────┘  └────────────────┘  └────────────────┘        │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                    ▲                                       │
│                                    │ DLP Events (HTTPS, Port 8443)         │
│                                    │                                       │
└────────────────────────────────────┴───────────────────────────────────────┘
                                     │
                      ┌──────────────┴──────────────┐
                      │                             │
┌─────────────────────┴─────────┐  ┌────────────────┴────────────────┐
│   ENDPOINT TIER - Workstations │  │  ENDPOINT TIER - File Servers  │
│                                 │  │                                 │
│  ┌──────────────────────────┐  │  │  ┌──────────────────────────┐  │
│  │  Log360Cloud Agent       │  │  │  │  Log360Cloud Agent       │  │
│  │  - System event logs     │  │  │  │  - System event logs     │  │
│  │  - Application logs      │  │  │  │  - Application logs      │  │
│  │  - Security logs         │  │  │  │  - Security logs         │  │
│  │  - Port: 8999, 9163      │  │  │  │  - Port: 8999, 9163      │  │
│  └──────────────────────────┘  │  │  └──────────────────────────┘  │
│                                 │  │                                 │
│  ┌──────────────────────────┐  │  │  ┌──────────────────────────┐  │
│  │  DLP Agent               │  │  │  │  DLP Agent               │  │
│  │  - Minifilter Driver     │  │  │  │  - Minifilter Driver     │  │
│  │  - File activity monitor │  │  │  │  - File activity monitor │  │
│  │  - Device control        │  │  │  │  - Share auditing        │  │
│  │  - USB/Email/Print       │  │  │  │  - NAS integration       │  │
│  │  - Port: 8801*, 9164*    │  │  │  │  - Port: 8801*, 9164*    │  │
│  └──────────────────────────┘  │  │  └──────────────────────────┘  │
│                                 │  │                                 │
│  * Auto-adjusted to avoid       │  │  * Auto-adjusted to avoid       │
│    conflicts with Log360 agent  │  │    conflicts with Log360 agent  │
│                                 │  │                                 │
└─────────────────────────────────┘  └─────────────────────────────────┘
```

*Note: Both agents coexist on the same endpoints without conflicts*

---

## 2. Data Flow Architecture

```
┌──────────────┐
│  End User    │
│  Activity    │
└──────┬───────┘
       │
       │ (1) File Operation: Copy file to USB
       │
       ▼
┌────────────────────────────────────────┐
│  FILE SYSTEM (Windows Kernel)          │
│  ┌──────────────────────────────────┐  │
│  │  Minifilter Driver Stack         │  │
│  │  ┌────────────────────────────┐  │  │
│  │  │ DLP Agent Filter (Driver)  │  │  │
│  │  └────────────┬───────────────┘  │  │
│  └───────────────┼──────────────────┘  │
└──────────────────┼─────────────────────┘
                   │
                   │ (2) Intercept I/O Request
                   │
                   ▼
┌────────────────────────────────────────┐
│  DLP AGENT (User Mode Service)         │
│  ┌──────────────────────────────────┐  │
│  │  Policy Evaluation Engine        │  │
│  │  - Check content patterns        │  │
│  │  - Check device type (USB)       │  │
│  │  - Check user permissions        │  │
│  │  - Check file classification     │  │
│  └──────────────┬───────────────────┘  │
└──────────────────┼─────────────────────┘
                   │
                   │ (3) Policy Match: BLOCK + ALERT
                   │
     ┌─────────────┼─────────────┐
     │             │             │
     ▼             ▼             ▼
┌─────────┐  ┌──────────┐  ┌─────────────────┐
│ Block   │  │ Notify   │  │ Send Event to   │
│ File    │  │ User     │  │ DSP Server      │
│ Transfer│  │ (Popup)  │  │                 │
└─────────┘  └──────────┘  └────────┬────────┘
                                    │
                                    │ (4) DLP Event (HTTPS)
                                    │
                                    ▼
┌──────────────────────────────────────────────────┐
│  DATASECURITY PLUS SERVER                        │
│  ┌────────────────────────────────────────────┐  │
│  │  Event Processor                           │  │
│  │  - Store in local database                 │  │
│  │  - Generate local alert                    │  │
│  │  - Apply retention policy                  │  │
│  └────────────────┬───────────────────────────┘  │
│                   │                               │
│  ┌────────────────┴───────────────────────────┐  │
│  │  SIEM Integration Module                   │  │
│  │  - Transform to Syslog format (RFC 5424)   │  │
│  │  - Add contextual information              │  │
│  │  - Queue for forwarding                    │  │
│  └────────────────┬───────────────────────────┘  │
└───────────────────┼──────────────────────────────┘
                    │
                    │ (5) Syslog Message (UDP/TCP Port 514/6514)
                    │     Format: JSON, CEF, or LEEF
                    │
                    ▼
┌──────────────────────────────────────────────────┐
│  LOG360CLOUD AGENT (On-Prem Collector)           │
│  ┌────────────────────────────────────────────┐  │
│  │  Syslog Receiver (Port 514/6514)           │  │
│  └────────────────┬───────────────────────────┘  │
│                   │                               │
│  ┌────────────────┴───────────────────────────┐  │
│  │  Event Parser & Enricher                   │  │
│  │  - Parse DLP fields (user, file, action)   │  │
│  │  - Enrich with local context               │  │
│  │  - Add agent metadata                      │  │
│  └────────────────┬───────────────────────────┘  │
│                   │                               │
│  ┌────────────────┴───────────────────────────┐  │
│  │  Event Buffer (2GB cache)                  │  │
│  │  - Queue events for upload                 │  │
│  │  - Persist during network outages          │  │
│  │  - Compress for efficiency                 │  │
│  └────────────────┬───────────────────────────┘  │
└───────────────────┼──────────────────────────────┘
                    │
                    │ (6) Encrypted Upload (HTTPS/TLS Port 443)
                    │
                    ▼
┌──────────────────────────────────────────────────┐
│  LOG360CLOUD (Cloud SIEM)                         │
│  ┌────────────────────────────────────────────┐  │
│  │  Ingestion Layer                           │  │
│  │  - Receive encrypted events                │  │
│  │  - Decrypt and validate                    │  │
│  │  - Deduplicate                             │  │
│  └────────────────┬───────────────────────────┘  │
│                   │                               │
│  ┌────────────────┴───────────────────────────┐  │
│  │  Indexing & Storage (Zoho Logs)            │  │
│  │  - Index all fields for fast search        │  │
│  │  - Store in distributed file system        │  │
│  │  - Apply retention policies                │  │
│  └────────────────┬───────────────────────────┘  │
│                   │                               │
│  ┌────────────────┴───────────────────────────┐  │
│  │  Correlation Engine                        │  │
│  │  - Match correlation rules:                │  │
│  │    * Multiple DLP violations + file access │  │
│  │    * After-hours login + data transfer     │  │
│  │    * Mass file modifications               │  │
│  │  - Calculate confidence score              │  │
│  │  - Generate correlated incidents           │  │
│  └────────────────┬───────────────────────────┘  │
│                   │                               │
│  ┌────────────────┴───────────────────────────┐  │
│  │  Alerting & Response                       │  │
│  │  - Create alerts (Email, SMS, Webhook)     │  │
│  │  - Update dashboards                       │  │
│  │  - Trigger workflows (SOAR integration)    │  │
│  └────────────────┬───────────────────────────┘  │
└───────────────────┼──────────────────────────────┘
                    │
                    │ (7) Alert Notification
                    │
                    ▼
┌──────────────────────────────────────────────────┐
│  SOC ANALYST / SECURITY TEAM                      │
│  ┌────────────────────────────────────────────┐  │
│  │  Dashboard View                            │  │
│  │  - Correlated Incident: "Insider Threat"   │  │
│  │  - Confidence: 95%                         │  │
│  │  - Related Events:                         │  │
│  │    * 5 DLP violations (USB block)          │  │
│  │    * 20 file accesses (sensitive folders)  │  │
│  │    * VPN login from unusual location       │  │
│  │  - Recommended Actions: Block USB devices  │  │
│  └────────────────────────────────────────────┘  │
│                                                   │
│  (8) Investigation & Response                     │
│  - Review full event timeline                     │
│  - Execute response actions                       │
│  - Document incident                              │
│  - Close ticket                                   │
└───────────────────────────────────────────────────┘
```

---

## 3. Agent Coexistence Architecture

```
┌────────────────────────────────────────────────────────────┐
│                      ENDPOINT (Windows/Linux)              │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │              Operating System Kernel                  │ │
│  │  ┌────────────────────────────────────────────────┐  │ │
│  │  │  File System Filter Stack (Windows)            │  │ │
│  │  │  ┌──────────────────────────────────────────┐  │  │ │
│  │  │  │  DLP Agent Minifilter Driver (Priority 1) │  │  │ │
│  │  │  │  - Intercepts all file I/O operations     │  │  │ │
│  │  │  │  - Lowest in stack for first interception │  │  │ │
│  │  │  └──────────────────────────────────────────┘  │  │ │
│  │  └────────────────────────────────────────────────┘  │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │              User Mode Services                       │ │
│  │                                                       │ │
│  │  ┌────────────────────────┐  ┌────────────────────┐ │ │
│  │  │  Log360Cloud Agent     │  │  DLP Agent         │ │ │
│  │  ├────────────────────────┤  ├────────────────────┤ │ │
│  │  │ Service Name:          │  │ Service Name:      │ │ │
│  │  │   Log360CloudAgent     │  │   DSPAgent         │ │ │
│  │  ├────────────────────────┤  ├────────────────────┤ │ │
│  │  │ Ports Used:            │  │ Ports Used:        │ │ │
│  │  │   8999 (HTTPS)         │  │   8801* (HTTP)     │ │ │
│  │  │   9163 (Config)        │  │   9164* (Config)   │ │ │
│  │  ├────────────────────────┤  ├────────────────────┤ │ │
│  │  │ Functions:             │  │ Functions:         │ │ │
│  │  │ - Collect system logs  │  │ - File monitoring  │ │ │
│  │  │ - Collect app logs     │  │ - Policy enforce   │ │ │
│  │  │ - Forward to cloud     │  │ - Device control   │ │ │
│  │  │ - Receive Syslog       │  │ - Quarantine files │ │ │
│  │  ├────────────────────────┤  ├────────────────────┤ │ │
│  │  │ CPU: ~1-2%             │  │ CPU: ~2-3%         │ │ │
│  │  │ RAM: ~100-200 MB       │  │ RAM: ~200-300 MB   │ │ │
│  │  │ Disk: ~2GB buffer      │  │ Disk: ~2GB buffer  │ │ │
│  │  └────────────────────────┘  └────────────────────┘ │ │
│  │                                                       │ │
│  │  *Note: Ports auto-adjusted if Log360 already uses   │ │
│  │         default ports 8800/9163                      │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │              Network Communication                    │ │
│  │                                                       │ │
│  │  Log360 Agent → Cloud (HTTPS:443)                    │ │
│  │  Log360 Agent ← Syslog (UDP/TCP:514/6514)            │ │
│  │  DLP Agent → DSP Server (HTTPS:8443)                 │ │
│  │                                                       │ │
│  │  NO CONFLICTS: Different services, different ports   │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

---

## 4. Database Entity Relationship Diagram (Simplified)

```
┌─────────────────┐
│  Organization   │
│  (MSSP Root)    │
└────────┬────────┘
         │ 1:N
         ▼
┌─────────────────┐        ┌─────────────────┐
│    Tenant       │───1:N──│   Department    │
│  (Customer)     │        └─────────────────┘
└────────┬────────┘
         │ 1:N
    ┌────┼────┬─────────────────┐
    │    │    │                 │
    ▼    ▼    ▼                 ▼
┌───────┐┌────────┐┌──────────┐┌────────────┐
│ User  ││ Agent  ││DLPPolicy ││AuditLog    │
└───┬───┘└───┬────┘└────┬─────┘└────────────┘
    │        │           │
    │ 1:N    │ 1:N       │ 1:N
    │        │           │
    ▼        ▼           ▼
┌──────────────┐    ┌─────────┐
│SecurityEvent │    │DLPEvent │
│DLPEvent      │    │         │
└──────┬───────┘    └────┬────┘
       │ 1:1             │ 1:1
       │                 │
       ▼                 ▼
    ┌────────┐      ┌─────────┐
    │ Alert  │      │DLPRule  │
    └────────┘      └─────────┘
       │ N:M
       │
       ▼
┌────────────────────┐
│CorrelatedIncident │
└────────────────────┘
```

Key Relationships:
- Organization → Tenant (1:N) - Multi-tenancy support
- Tenant → User, Agent, Policy (1:N) - Tenant isolation
- User → Events (1:N) - User activity tracking
- Agent → Events (1:N) - Event source tracking
- Event → Alert (1:1) - Alert generation
- CorrelatedIncident ← Event (N:M) - Event aggregation

---

## 5. Deployment Topology

### Small Deployment (< 500 Endpoints)

```
                  Internet
                     │
                     ▼
        ┌────────────────────────┐
        │   Log360Cloud (SaaS)   │
        │   - Managed by vendor  │
        └────────────────────────┘
                     ▲
                     │ HTTPS
                     │
    ┌────────────────┴────────────────┐
    │    Corporate Network            │
    │                                 │
    │  ┌──────────────────────────┐  │
    │  │ Log360 Agent (1 server)  │  │
    │  │ - 8GB RAM, 4 vCPU        │  │
    │  │ - Syslog receiver        │  │
    │  └──────────────────────────┘  │
    │                                 │
    │  ┌──────────────────────────┐  │
    │  │ DSP Server (1 server)    │  │
    │  │ - 16GB RAM, 8 vCPU       │  │
    │  │ - PostgreSQL bundled     │  │
    │  └──────────────────────────┘  │
    │                                 │
    │  ┌──────────────────────────┐  │
    │  │ Endpoints: ~500          │  │
    │  │ - Log360 agents: All     │  │
    │  │ - DLP agents: Critical   │  │
    │  └──────────────────────────┘  │
    └─────────────────────────────────┘

Total Cost: ~$20K-40K/year
```

### Enterprise Deployment (> 5000 Endpoints)

```
                    Internet
                       │
      ┌────────────────┼────────────────┐
      │                │                │
      ▼                ▼                ▼
┌──────────┐    ┌──────────┐    ┌──────────┐
│Log360    │    │Log360    │    │Log360    │
│US Region │    │EU Region │    │AP Region │
└──────────┘    └──────────┘    └──────────┘
      ▲                ▲                ▲
      │                │                │
┌─────┴────────────────┴────────────────┴─────┐
│     Global Corporate Network (MPLS/SD-WAN)  │
│                                              │
│  ┌────────────┐ ┌────────────┐ ┌──────────┐│
│  │Log360 Farm │ │DSP Cluster │ │PostgreSQL││
│  │10+ servers │ │Active-Act. │ │HA Cluster││
│  └────────────┘ └────────────┘ └──────────┘│
│                                              │
│  ┌──────────────────────────────────────┐  │
│  │ Endpoints: 10,000+                   │  │
│  │ - Distributed across regions         │  │
│  │ - Tiered deployment (critical first) │  │
│  └──────────────────────────────────────┘  │
└──────────────────────────────────────────────┘

Total Cost: ~$500K-1M/year
```

---

**These diagrams provide visual understanding of the integration architecture.**  
**Refer to ARCHITECTURE.md for detailed explanations.**
