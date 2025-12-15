# SIEM Solution Architecture Diagrams

## Table of Contents
1. [System Architecture Diagrams](#system-architecture-diagrams)
2. [Data Flow Diagrams](#data-flow-diagrams)
3. [Deployment Diagrams](#deployment-diagrams)
4. [Component Interaction Diagrams](#component-interaction-diagrams)

---

## System Architecture Diagrams

### Overall System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              DATA SOURCES LAYER                                      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ Windows  │  │  Linux   │  │  macOS   │  │ Network  │  │  Cloud   │            │
│  │Endpoints │  │ Servers  │  │Endpoints │  │ Devices  │  │ Services │            │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘            │
│       │             │               │             │             │                    │
└───────┼─────────────┼───────────────┼─────────────┼─────────────┼────────────────────┘
        │             │               │             │             │
        │             │               │             │             │
        ▼             ▼               ▼             ▼             ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           COLLECTION LAYER                                           │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌────────────────┐    ┌────────────────┐    ┌────────────────┐                   │
│  │ SIEM Agents    │    │   Agentless    │    │  Cloud APIs    │                   │
│  │ (Go-based)     │    │  Collectors    │    │  (REST/gRPC)   │                   │
│  │                │    │  (Syslog/SNMP) │    │                │                   │
│  │ • Log Files    │    │ • Network Logs │    │ • CloudTrail   │                   │
│  │ • Process Mon  │    │ • Firewall     │    │ • Azure Logs   │                   │
│  │ • File Integrity│   │ • IDS/IPS      │    │ • GCP Logs     │                   │
│  │ • Network Mon  │    │ • Routers      │    │ • O365         │                   │
│  └────────┬───────┘    └────────┬───────┘    └────────┬───────┘                   │
│           │                     │                      │                            │
└───────────┼─────────────────────┼──────────────────────┼────────────────────────────┘
            │                     │                      │
            └─────────────────────┴──────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                            INGESTION LAYER                                           │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────┐      ┌──────────────────┐      ┌─────────────────┐               │
│  │Load Balancer│ ───→ │  Apache Kafka    │ ───→ │ Stream Processor│               │
│  │(HAProxy/    │      │  Message Queue   │      │ (Apache Flink)  │               │
│  │ NGINX)      │      │                  │      │                 │               │
│  └─────────────┘      └──────────────────┘      └─────────────────┘               │
│                              ↓                           ↓                           │
└──────────────────────────────┼───────────────────────────┼───────────────────────────┘
                               │                           │
                               ▼                           ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          PROCESSING LAYER                                            │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │Normalization │→ │ Enrichment   │→ │ Correlation  │→ │ML Detection  │          │
│  │   Service    │  │   Service    │  │   Engine     │  │   Service    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                                       │
│  ┌──────────────────────────────────────────────────────────────────────┐          │
│  │                      Rule Engine                                      │          │
│  │  • Sigma Rules  • YARA Rules  • Custom Detection Rules               │          │
│  └──────────────────────────────────────────────────────────────────────┘          │
│                               ↓                                                      │
└───────────────────────────────┼──────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                            STORAGE LAYER                                             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ HOT STORAGE  │  │WARM STORAGE  │  │COLD STORAGE  │  │   METADATA   │          │
│  │              │  │              │  │              │  │              │          │
│  │Elasticsearch │  │ ClickHouse   │  │   S3/MinIO   │  │ PostgreSQL   │          │
│  │  (0-30 days) │  │(30-365 days) │  │  (1-7 years) │  │  + Redis     │          │
│  │              │  │              │  │              │  │              │          │
│  │ • Real-time  │  │ • Analytics  │  │ • Compliance │  │ • Users      │          │
│  │ • Search     │  │ • Reporting  │  │ • Archive    │  │ • Config     │          │
│  │ • Dashboards │  │ • Trends     │  │ • Forensics  │  │ • Alerts     │          │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘          │
│                               ↓                                                      │
└───────────────────────────────┼──────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                      ANALYTICS & RESPONSE LAYER                                      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │Query Service │  │Alert Manager │  │SOAR Integration│ │  Reporting  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │Threat Intel  │  │Investigation │  │ Notification │  │ Case Mgmt    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘          │
│                               ↓                                                      │
└───────────────────────────────┼──────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         PRESENTATION LAYER                                           │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Web UI     │  │  REST API    │  │  Mobile App  │  │  CLI Tools   │          │
│  │  (React)     │  │  (GraphQL)   │  │(React Native)│  │    (Go)      │          │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                                       │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Diagrams

### Event Processing Pipeline

```
┌──────────────┐
│ Raw Event    │
│ from Source  │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────────┐
│      Agent Collection Module         │
│                                       │
│ 1. Collect event from source         │
│ 2. Pre-filter (reduce noise)         │
│ 3. Local buffer (reliability)        │
│ 4. Batch events (efficiency)         │
│ 5. Compress (bandwidth)              │
└──────────────┬───────────────────────┘
               │ TLS 1.3
               ▼
┌──────────────────────────────────────┐
│      Load Balancer / API Gateway     │
│                                       │
│ 1. TLS termination                   │
│ 2. Authentication (API key/cert)     │
│ 3. Rate limiting                     │
│ 4. Route to Kafka                    │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│        Apache Kafka Queue            │
│                                       │
│ Topic: raw-events                    │
│ Partitions: 10                       │
│ Replication: 3                       │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│     Stream Processor (Flink)         │
│                                       │
│ 1. Parse event                       │
│ 2. Validate schema                   │
│ 3. Deduplicate                       │
└──────────────┬───────────────────────┘
               │
               ├─────────────────────────────────┐
               │                                 │
               ▼                                 ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│  Normalization Service   │    │   Enrichment Service     │
│                          │    │                          │
│ • Convert to ECS format  │    │ • GeoIP lookup          │
│ • Standardize fields     │    │ • Threat intel check    │
│ • Type conversion        │    │ • Asset context         │
│ • Timestamp UTC          │    │ • User context          │
└──────────┬───────────────┘    └──────────┬───────────────┘
           │                               │
           └───────────┬───────────────────┘
                       │
                       ▼
        ┌──────────────────────────────────┐
        │     Correlation Engine            │
        │                                   │
        │ 1. Temporal correlation           │
        │ 2. Entity correlation             │
        │ 3. Pattern matching               │
        │ 4. Sequence detection             │
        └──────────┬────────────────────────┘
                   │
                   ├──────────────────────────────┐
                   │                              │
                   ▼                              ▼
    ┌──────────────────────┐      ┌──────────────────────┐
    │   Rule Engine        │      │  ML Detection        │
    │                      │      │                      │
    │ • Sigma rules        │      │ • Anomaly detection  │
    │ • YARA rules         │      │ • Behavioral analysis│
    │ • Custom rules       │      │ • Pattern learning   │
    └──────────┬───────────┘      └──────────┬───────────┘
               │                             │
               └──────────┬──────────────────┘
                          │
                          ▼
           ┌──────────────────────────────────┐
           │    Alert Generation               │
           │                                   │
           │ 1. Create alert                   │
           │ 2. Calculate confidence           │
           │ 3. Risk scoring                   │
           │ 4. Deduplicate alerts             │
           └──────────┬────────────────────────┘
                      │
                      ├──────────────────────────────┐
                      │                              │
                      ▼                              ▼
       ┌──────────────────────┐      ┌──────────────────────┐
       │  Store Events        │      │  Store Alerts        │
       │                      │      │                      │
       │ → Elasticsearch (hot)│      │ → PostgreSQL         │
       │ → ClickHouse (warm)  │      │ → Redis (cache)      │
       │ → S3 (cold)          │      │                      │
       └──────────────────────┘      └──────────┬───────────┘
                                               │
                                               ▼
                                ┌──────────────────────┐
                                │ Notification Service │
                                │                      │
                                │ • Email              │
                                │ • Slack              │
                                │ • PagerDuty          │
                                │ • Webhook            │
                                └──────────────────────┘
```

---

## Deployment Diagrams

### Kubernetes Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Kubernetes Cluster (Multi-AZ)                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                        Ingress Layer                                │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │    │
│  │  │   Ingress    │  │   Ingress    │  │   Ingress    │            │    │
│  │  │ Controller   │  │ Controller   │  │ Controller   │            │    │
│  │  │    (AZ-1)    │  │    (AZ-2)    │  │    (AZ-3)    │            │    │
│  │  └──────────────┘  └──────────────┘  └──────────────┘            │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                  ↓                                          │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                       Service Mesh (Istio)                          │    │
│  │                    (mTLS, Traffic Management)                       │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                  ↓                                          │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                     Application Pods                                │    │
│  │                                                                      │    │
│  │  Namespace: siem-collection                                         │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐       │    │
│  │  │ agent-manager  │  │ log-collector  │  │network-analyzer│       │    │
│  │  │ Deployment(3)  │  │ Deployment(5)  │  │ Deployment(3)  │       │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘       │    │
│  │                                                                      │    │
│  │  Namespace: siem-processing                                         │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐       │    │
│  │  │  normalizer    │  │  enrichment    │  │  correlation   │       │    │
│  │  │ Deployment(5)  │  │ Deployment(5)  │  │ Deployment(5)  │       │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘       │    │
│  │                                                                      │    │
│  │  Namespace: siem-analytics                                          │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐       │    │
│  │  │ query-service  │  │ alert-service  │  │   reporting    │       │    │
│  │  │ Deployment(5)  │  │ Deployment(3)  │  │ Deployment(3)  │       │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘       │    │
│  │                                                                      │    │
│  │  Namespace: siem-frontend                                           │    │
│  │  ┌────────────────┐  ┌────────────────┐                           │    │
│  │  │    web-ui      │  │  api-gateway   │                           │    │
│  │  │ Deployment(3)  │  │ Deployment(5)  │                           │    │
│  │  └────────────────┘  └────────────────┘                           │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                  ↓                                          │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                      StatefulSets                                   │    │
│  │                                                                      │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐       │    │
│  │  │     Kafka      │  │ Elasticsearch  │  │  PostgreSQL    │       │    │
│  │  │  StatefulSet   │  │  StatefulSet   │  │  StatefulSet   │       │    │
│  │  │   (3 replicas) │  │  (5 replicas)  │  │  (3 replicas)  │       │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘       │    │
│  │                                                                      │    │
│  │  ┌────────────────┐  ┌────────────────┐                           │    │
│  │  │  ClickHouse    │  │  Redis Cluster │                           │    │
│  │  │  StatefulSet   │  │  StatefulSet   │                           │    │
│  │  │  (3 replicas)  │  │  (6 replicas)  │                           │    │
│  │  └────────────────┘  └────────────────┘                           │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    Persistent Volumes                               │    │
│  │                      (SSD/NVMe Storage)                             │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                  ↑
                                  │
                    ┌─────────────┴──────────────┐
                    │                             │
         ┌──────────┴──────────┐    ┌───────────┴───────────┐
         │  Monitoring Stack   │    │   External Services   │
         │                     │    │                       │
         │ • Prometheus        │    │ • S3 (Cold Storage)   │
         │ • Grafana           │    │ • Threat Intel APIs   │
         │ • Jaeger            │    │ • SOAR Platform       │
         │ • ELK (Logs)        │    │ • Notification APIs   │
         └─────────────────────┘    └───────────────────────┘
```

---

## Component Interaction Diagrams

### Alert Generation Sequence

```
Agent       Load Balancer    Kafka       Processor     Rules Engine    Alert Service    PostgreSQL    Notifier
  │               │             │             │              │               │              │            │
  │─Event─────────►│            │             │              │               │              │            │
  │               │             │             │              │               │              │            │
  │               │──Publish───→│             │              │               │              │            │
  │               │             │             │              │               │              │            │
  │               │             │──Consume───→│              │               │              │            │
  │               │             │             │              │               │              │            │
  │               │             │             │─Normalize───►│               │              │            │
  │               │             │             │              │               │              │            │
  │               │             │             │◄─Normalized──│               │              │            │
  │               │             │             │              │               │              │            │
  │               │             │             │─Enrich──────►│               │              │            │
  │               │             │             │              │               │              │            │
  │               │             │             │◄─Enriched────│               │              │            │
  │               │             │             │              │               │              │            │
  │               │             │             │──Match Rules─────────────────►│              │            │
  │               │             │             │              │               │              │            │
  │               │             │             │              │◄─Rule Match───│              │            │
  │               │             │             │              │               │              │            │
  │               │             │             │              │─Create Alert─────────────────►│            │
  │               │             │             │              │               │              │            │
  │               │             │             │              │               │──Save Alert─→│            │
  │               │             │             │              │               │              │            │
  │               │             │             │              │               │◄─Saved───────│            │
  │               │             │             │              │               │              │            │
  │               │             │             │              │               │──Notify──────────────────►│
  │               │             │             │              │               │              │            │
  │               │             │             │              │               │              │            │──Send Email───►
  │               │             │             │              │               │              │            │──Send Slack───►
  │               │             │             │              │               │              │            │
```

### Query Flow

```
User → Web UI → API Gateway → Query Service → Storage (ES/ClickHouse) → Response
  │       │         │              │                     │
  │       │         │              │                     │
  1. Enter Search Query            │                     │
  │       │         │              │                     │
  2. Submit Request ────────────────►                    │
  │       │         │              │                     │
  3. Authenticate & Authorize      │                     │
  │       │         │              │                     │
  4. Forward Query ─────────────────►                    │
  │       │         │              │                     │
  5. Build Query & Route           │                     │
  │       │         │              │                     │
  6. Execute Search ────────────────────────────────────►│
  │       │         │              │                     │
  7. Return Results ◄────────────────────────────────────│
  │       │         │              │                     
  8. Cache Results (Redis)         │                     
  │       │         │              │                     
  9. Format Response ◄──────────────┘                     
  │       │         │                                     
  10. Send to Client ◄──────────────┘                     
  │       │                                               
  11. Display Results ◄────────────┘                      
  │                                                       
```

---

## Network Architecture

### Multi-Region Deployment

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Global Load Balancer                                │
│                         (CloudFlare / Route53)                                │
└────────────┬──────────────────────────┬──────────────────────┬───────────────┘
             │                          │                      │
             ▼                          ▼                      ▼
┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
│   Region: US-East   │   │   Region: EU-West   │   │   Region: AP-South  │
├─────────────────────┤   ├─────────────────────┤   ├─────────────────────┤
│                     │   │                     │   │                     │
│ ┌─────────────────┐ │   │ ┌─────────────────┐ │   │ ┌─────────────────┐ │
│ │  K8s Cluster    │ │   │ │  K8s Cluster    │ │   │ │  K8s Cluster    │ │
│ │  (3 AZs)        │ │   │ │  (3 AZs)        │ │   │ │  (3 AZs)        │ │
│ └─────────────────┘ │   │ └─────────────────┘ │   │ └─────────────────┘ │
│                     │   │                     │   │                     │
│ ┌─────────────────┐ │   │ ┌─────────────────┐ │   │ ┌─────────────────┐ │
│ │ Elasticsearch   │◄┼───┼─┤ Elasticsearch   │◄┼───┼─┤ Elasticsearch   │ │
│ │ (Cross Cluster  │ │   │ │ (Cross Cluster  │ │   │ │ (Cross Cluster  │ │
│ │  Replication)   │─┼───┼─► Replication)   │─┼───┼─► Replication)   │ │
│ └─────────────────┘ │   │ └─────────────────┘ │   │ └─────────────────┘ │
│                     │   │                     │   │                     │
│ ┌─────────────────┐ │   │ ┌─────────────────┐ │   │ ┌─────────────────┐ │
│ │  PostgreSQL     │◄┼───┼─┤  PostgreSQL     │◄┼───┼─┤  PostgreSQL     │ │
│ │  (Replication)  │─┼───┼─►  (Replication)  │─┼───┼─►  (Replication)  │ │
│ └─────────────────┘ │   │ └─────────────────┘ │   │ └─────────────────┘ │
│                     │   │                     │   │                     │
└─────────────────────┘   └─────────────────────┘   └─────────────────────┘
            │                       │                        │
            └───────────┬───────────┴───────────┬────────────┘
                        │                       │
                        ▼                       ▼
              ┌─────────────────┐    ┌─────────────────────┐
              │  Global S3      │    │  Kafka Mirror Maker │
              │  (Cross-Region  │    │  (Event Streaming)  │
              │   Replication)  │    └─────────────────────┘
              └─────────────────┘
```

---

## Conclusion

These diagrams provide visual representations of the SIEM architecture across multiple dimensions:
- **System Architecture**: Overall component organization
- **Data Flow**: How events move through the system
- **Deployment**: Kubernetes-based deployment strategy
- **Interactions**: Service communication patterns
- **Network**: Multi-region distribution

Use these diagrams alongside the detailed documentation for implementation guidance.
