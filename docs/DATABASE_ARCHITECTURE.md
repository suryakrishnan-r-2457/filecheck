# Database Architecture for SIEM Solution

## Table of Contents
1. [Overview](#overview)
2. [Database Selection Strategy](#database-selection-strategy)
3. [Database Schemas](#database-schemas)
4. [Data Tier Architecture](#data-tier-architecture)
5. [Database Diagrams](#database-diagrams)
6. [Scalability & Performance](#scalability--performance)
7. [Backup & Recovery](#backup--recovery)

---

## Overview

The SIEM solution employs a polyglot persistence strategy, using multiple database technologies optimized for specific use cases. This approach ensures optimal performance, scalability, and cost-effectiveness.

### Database Stack Overview

```
┌──────────────────────────────────────────────────────────────┐
│                    Application Layer                          │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│              Data Access Layer (Abstraction)                  │
└──────────────────────────────────────────────────────────────┘
        ↓            ↓            ↓            ↓           ↓
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐  ┌──────┐
│Elastic   │  │ClickHouse│  │PostgreSQL│  │  Redis  │  │ S3   │
│search    │  │          │  │          │  │ Cluster │  │/MinIO│
│(Hot Data)│  │(Warm Data)│  │(Metadata)│  │ (Cache) │  │(Cold)│
└──────────┘  └──────────┘  └──────────┘  └─────────┘  └──────┘
```

---

## Database Selection Strategy

### 1. Elasticsearch (Hot Storage)
**Purpose**: Real-time search and recent event storage (0-30 days)

**Why Elasticsearch:**
- Full-text search capabilities
- Fast aggregations for dashboards
- JSON document storage
- Horizontal scalability
- Near real-time indexing

**Use Cases:**
- Recent security events
- Real-time alerting
- Interactive dashboards
- Ad-hoc investigations

**Configuration:**
- Cluster: 5+ nodes (3 master, data nodes)
- Sharding: 5-10 shards per index
- Replicas: 1-2 for high availability
- Index lifecycle management (ILM)

### 2. ClickHouse (Warm Storage)
**Purpose**: Historical analytics and warm data (30-365 days)

**Why ClickHouse:**
- Column-oriented storage
- Excellent compression (10-20x)
- Fast analytical queries
- Cost-effective for large datasets
- OLAP optimized

**Use Cases:**
- Historical trend analysis
- Compliance reporting
- Long-term investigations
- Statistical analysis

**Configuration:**
- Cluster: 3+ nodes with replication
- Partitioning: By date (monthly)
- Compression: ZSTD
- MergeTree engine

### 3. PostgreSQL (Metadata & Configuration)
**Purpose**: Transactional data and metadata storage

**Why PostgreSQL:**
- ACID compliance
- Complex queries and joins
- Data integrity
- JSON support (JSONB)
- Mature ecosystem

**Use Cases:**
- User accounts and permissions
- System configuration
- Asset inventory
- Case management
- Rule definitions
- Dashboards and saved searches

**Configuration:**
- Master-replica setup
- Connection pooling (PgBouncer)
- Partitioning for large tables
- Regular vacuuming

### 4. Redis Cluster (Cache & Real-time Data)
**Purpose**: High-speed cache and temporary data storage

**Why Redis:**
- In-memory performance
- Pub/Sub for real-time updates
- TTL support
- Data structures (lists, sets, hashes)
- Atomic operations

**Use Cases:**
- Session storage
- Real-time alerts cache
- Rate limiting counters
- Temporary correlation state
- API response caching

**Configuration:**
- Cluster mode (6+ nodes)
- Persistence: RDB + AOF
- Eviction policy: allkeys-lru
- Replication for HA

### 5. S3/MinIO (Cold Storage Archive)
**Purpose**: Long-term archival (1-7 years)

**Why Object Storage:**
- Cost-effective at scale
- Unlimited scalability
- Data durability (11 9's)
- Lifecycle policies
- Compliance features

**Use Cases:**
- Compliance archival
- Raw log backup
- Forensic evidence
- Historical data lake

**Configuration:**
- Versioning enabled
- Encryption at rest
- Lifecycle policies (transition to Glacier)
- Cross-region replication

### 6. InfluxDB/Prometheus (Time-Series Metrics)
**Purpose**: System and infrastructure metrics

**Why Time-Series DB:**
- Optimized for time-series data
- Built-in downsampling
- Fast metric queries
- Retention policies

**Use Cases:**
- System performance metrics
- Agent health monitoring
- Service level indicators
- Capacity planning

---

## Database Schemas

### 1. Elasticsearch Schema

#### Index Pattern: `siem-events-{YYYY.MM.DD}`

**Event Document Structure:**
```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "event": {
    "id": "evt_1234567890",
    "type": "authentication",
    "category": "security",
    "severity": "high",
    "outcome": "failure"
  },
  "source": {
    "ip": "192.168.1.100",
    "port": 54321,
    "hostname": "workstation-01",
    "user": {
      "name": "john.doe",
      "id": "u_12345"
    },
    "geo": {
      "country": "US",
      "city": "New York",
      "location": {"lat": 40.7128, "lon": -74.0060}
    }
  },
  "destination": {
    "ip": "10.0.1.50",
    "port": 22,
    "hostname": "server-prod-01"
  },
  "network": {
    "protocol": "tcp",
    "bytes": 1024,
    "packets": 5
  },
  "process": {
    "name": "sshd",
    "pid": 1234,
    "executable": "/usr/sbin/sshd"
  },
  "file": {
    "path": "/etc/passwd",
    "hash": {
      "md5": "5d41402abc4b2a76b9719d911017c592",
      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
  },
  "alert": {
    "id": "alert_9876543210",
    "name": "Multiple Failed SSH Login Attempts",
    "severity": "high",
    "confidence": 0.95,
    "mitre": {
      "tactic": "Initial Access",
      "technique": "T1078"
    }
  },
  "enrichment": {
    "threat_intel": {
      "is_malicious": true,
      "sources": ["abuse.ch", "alienvault"],
      "threat_type": "botnet"
    },
    "asset": {
      "criticality": "high",
      "owner": "IT Department",
      "tags": ["production", "web-server"]
    }
  },
  "metadata": {
    "ingestion_time": "2024-01-15T10:30:46.000Z",
    "pipeline": "linux-auth",
    "agent": {
      "id": "agent_001",
      "version": "1.0.0"
    }
  }
}
```

**Index Settings:**
```json
{
  "settings": {
    "number_of_shards": 5,
    "number_of_replicas": 1,
    "refresh_interval": "5s",
    "index.lifecycle.name": "siem-events-policy",
    "codec": "best_compression"
  },
  "mappings": {
    "properties": {
      "@timestamp": {"type": "date"},
      "event": {
        "properties": {
          "type": {"type": "keyword"},
          "category": {"type": "keyword"},
          "severity": {"type": "keyword"}
        }
      },
      "source.ip": {"type": "ip"},
      "destination.ip": {"type": "ip"},
      "source.geo.location": {"type": "geo_point"}
    }
  }
}
```

### 2. ClickHouse Schema

**Table: events**
```sql
CREATE TABLE events (
    timestamp DateTime64(3),
    event_id String,
    event_type LowCardinality(String),
    event_category LowCardinality(String),
    severity LowCardinality(String),
    source_ip IPv4,
    source_port UInt16,
    source_hostname String,
    source_user String,
    dest_ip IPv4,
    dest_port UInt16,
    dest_hostname String,
    protocol LowCardinality(String),
    bytes_sent UInt64,
    bytes_received UInt64,
    alert_id String,
    alert_name String,
    mitre_tactic LowCardinality(String),
    mitre_technique String,
    raw_event String CODEC(ZSTD(3)),
    metadata String,
    date Date MATERIALIZED toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, event_type, timestamp)
TTL date + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;

-- Materialized view for aggregations
CREATE MATERIALIZED VIEW events_hourly_agg
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, hour, event_type)
AS SELECT
    toDate(timestamp) as date,
    toHour(timestamp) as hour,
    event_type,
    severity,
    countState() as event_count,
    uniqState(source_ip) as unique_sources,
    sumState(bytes_sent) as total_bytes
FROM events
GROUP BY date, hour, event_type, severity;
```

### 3. PostgreSQL Schema

**Users and Authentication:**
```sql
-- Users table
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    is_admin BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    mfa_enabled BOOLEAN DEFAULT false,
    mfa_secret VARCHAR(255)
);

-- Roles table
CREATE TABLE roles (
    role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User roles mapping
CREATE TABLE user_roles (
    user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    role_id UUID REFERENCES roles(role_id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

-- Permissions table
CREATE TABLE permissions (
    permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_name VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT
);

-- Role permissions mapping
CREATE TABLE role_permissions (
    role_id UUID REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id UUID REFERENCES permissions(permission_id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(is_active);
```

**Alert Rules:**
```sql
-- Detection rules
CREATE TABLE detection_rules (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(50) NOT NULL, -- 'sigma', 'yara', 'correlation', 'ml'
    severity VARCHAR(20) NOT NULL, -- 'low', 'medium', 'high', 'critical'
    enabled BOOLEAN DEFAULT true,
    rule_content JSONB NOT NULL,
    mitre_tactics TEXT[],
    mitre_techniques TEXT[],
    tags TEXT[],
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_triggered TIMESTAMP,
    trigger_count INTEGER DEFAULT 0
);

CREATE INDEX idx_rules_enabled ON detection_rules(enabled);
CREATE INDEX idx_rules_severity ON detection_rules(severity);
CREATE INDEX idx_rules_type ON detection_rules(rule_type);
CREATE INDEX idx_rules_tags ON detection_rules USING GIN(tags);
```

**Alerts and Cases:**
```sql
-- Alerts table
CREATE TABLE alerts (
    alert_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID REFERENCES detection_rules(rule_id),
    alert_name VARCHAR(255) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(50) DEFAULT 'new', -- 'new', 'investigating', 'confirmed', 'false_positive', 'resolved'
    confidence DECIMAL(3,2),
    event_count INTEGER DEFAULT 1,
    first_seen TIMESTAMP NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    source_ips INET[],
    dest_ips INET[],
    affected_users TEXT[],
    affected_hosts TEXT[],
    alert_data JSONB,
    assigned_to UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cases (incidents)
CREATE TABLE cases (
    case_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_number VARCHAR(50) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(50) DEFAULT 'open', -- 'open', 'investigating', 'contained', 'resolved', 'closed'
    priority VARCHAR(20), -- 'low', 'medium', 'high', 'critical'
    assigned_to UUID REFERENCES users(user_id),
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP
);

-- Case alerts mapping
CREATE TABLE case_alerts (
    case_id UUID REFERENCES cases(case_id) ON DELETE CASCADE,
    alert_id UUID REFERENCES alerts(alert_id) ON DELETE CASCADE,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (case_id, alert_id)
);

CREATE INDEX idx_alerts_severity ON alerts(severity);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_created ON alerts(created_at DESC);
CREATE INDEX idx_cases_status ON cases(status);
CREATE INDEX idx_cases_assigned ON cases(assigned_to);
```

**Asset Management:**
```sql
-- Assets table
CREATE TABLE assets (
    asset_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_name VARCHAR(255) NOT NULL,
    asset_type VARCHAR(50) NOT NULL, -- 'server', 'workstation', 'network_device', 'application'
    ip_addresses INET[],
    hostnames TEXT[],
    mac_addresses MACADDR[],
    operating_system VARCHAR(100),
    criticality VARCHAR(20), -- 'low', 'medium', 'high', 'critical'
    owner_department VARCHAR(100),
    owner_contact VARCHAR(255),
    location VARCHAR(255),
    tags TEXT[],
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP
);

CREATE INDEX idx_assets_type ON assets(asset_type);
CREATE INDEX idx_assets_criticality ON assets(criticality);
CREATE INDEX idx_assets_ips ON assets USING GIN(ip_addresses);
```

**Dashboards and Saved Searches:**
```sql
-- Dashboards
CREATE TABLE dashboards (
    dashboard_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dashboard_name VARCHAR(255) NOT NULL,
    description TEXT,
    layout JSONB NOT NULL,
    is_public BOOLEAN DEFAULT false,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Saved searches
CREATE TABLE saved_searches (
    search_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    search_name VARCHAR(255) NOT NULL,
    query_string TEXT NOT NULL,
    filters JSONB,
    time_range JSONB,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_favorite BOOLEAN DEFAULT false
);
```

---

## Data Tier Architecture

### Hot-Warm-Cold Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                          HOT TIER                            │
│  Elasticsearch (0-30 days) - SSD, High Performance          │
│  - Real-time indexing and search                            │
│  - Active investigations                                     │
│  - Dashboard queries                                         │
│  Cost: $$$, Performance: Fastest                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
                    (Automated Rollover)
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                         WARM TIER                            │
│  ClickHouse (30-365 days) - HDD/SSD, Medium Performance     │
│  - Historical analytics                                      │
│  - Compliance reporting                                      │
│  - Trend analysis                                            │
│  Cost: $$, Performance: Fast                                │
└─────────────────────────────────────────────────────────────┘
                              ↓
                    (Automated Archival)
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                         COLD TIER                            │
│  S3/MinIO (1-7 years) - Object Storage, Low Performance     │
│  - Compliance archival                                       │
│  - Forensic investigations                                   │
│  - Long-term retention                                       │
│  Cost: $, Performance: Slow (restore required)              │
└─────────────────────────────────────────────────────────────┘
```

### Data Lifecycle Management

```sql
-- Elasticsearch ILM Policy
PUT _ilm/policy/siem-events-policy
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_size": "50GB",
            "max_age": "1d"
          },
          "set_priority": {
            "priority": 100
          }
        }
      },
      "warm": {
        "min_age": "7d",
        "actions": {
          "shrink": {
            "number_of_shards": 1
          },
          "forcemerge": {
            "max_num_segments": 1
          },
          "set_priority": {
            "priority": 50
          }
        }
      },
      "cold": {
        "min_age": "30d",
        "actions": {
          "freeze": {},
          "set_priority": {
            "priority": 0
          }
        }
      },
      "delete": {
        "min_age": "90d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```

---

## Database Diagrams

### Entity Relationship Diagram (PostgreSQL)

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│    Users    │────────→│  User_Roles │←────────│    Roles    │
└─────────────┘         └─────────────┘         └─────────────┘
      │                                                 │
      │                                                 │
      │                                                 ↓
      │                                         ┌─────────────────┐
      │                                         │Role_Permissions │
      │                                         └─────────────────┘
      │                                                 │
      │                                                 ↓
      │                                          ┌─────────────┐
      │                                          │Permissions  │
      ↓                                          └─────────────┘
┌─────────────┐
│   Cases     │
└─────────────┘
      │
      │
      ↓
┌─────────────┐         ┌─────────────┐         ┌──────────────┐
│Case_Alerts  │────────→│   Alerts    │←────────│Detection     │
└─────────────┘         └─────────────┘         │Rules         │
                              │                  └──────────────┘
                              │
                              ↓
                        ┌─────────────┐
                        │   Assets    │
                        └─────────────┘
```

### Data Flow Diagram

```
┌──────────┐
│ Raw Event│
└────┬─────┘
     │
     ↓
┌──────────────┐     Write      ┌──────────────┐
│  Kafka Topic │ ──────────────→│ Elasticsearch│
│              │                 │   (Hot 30d)  │
└──────────────┘                 └──────┬───────┘
     │                                  │
     │                                  │ ILM Rollover (30d)
     │                                  ↓
     │                           ┌──────────────┐
     │                           │  ClickHouse  │
     │                           │  (Warm 365d) │
     │                           └──────┬───────┘
     │                                  │
     │                                  │ Archive (365d)
     │                                  ↓
     │                           ┌──────────────┐
     │                           │  S3/MinIO    │
     │                           │ (Cold 1-7y)  │
     │                           └──────────────┘
     │
     │ Metadata Extraction
     ↓
┌──────────────┐
│  PostgreSQL  │
│  (Metadata)  │
└──────────────┘
     │
     │ Cache frequently accessed
     ↓
┌──────────────┐
│    Redis     │
│   (Cache)    │
└──────────────┘
```

---

## Scalability & Performance

### 1. Sharding Strategy

**Elasticsearch:**
- Index per day pattern
- 5-10 primary shards per index
- Shard size: 20-40GB
- Automatic shard rebalancing

**ClickHouse:**
- Partition by month
- Distributed table across cluster
- Replication factor: 2
- Automatic data distribution

**PostgreSQL:**
- Table partitioning by date for large tables
- Read replicas for read scaling
- Connection pooling (500-1000 connections)

### 2. Indexing Strategy

**Elasticsearch:**
```json
{
  "source.ip": "ip",
  "destination.ip": "ip",
  "event.type": "keyword",
  "alert.severity": "keyword",
  "@timestamp": "date"
}
```

**PostgreSQL:**
```sql
-- Composite indexes for common queries
CREATE INDEX idx_alerts_status_severity ON alerts(status, severity);
CREATE INDEX idx_alerts_assigned_status ON alerts(assigned_to, status) 
  WHERE status != 'resolved';
  
-- Partial indexes for active records
CREATE INDEX idx_active_cases ON cases(created_at DESC) 
  WHERE status IN ('open', 'investigating');
```

### 3. Query Optimization

**Elasticsearch:**
- Use filters instead of queries when possible
- Limit aggregation buckets
- Use scroll API for large result sets
- Disable scoring when not needed

**ClickHouse:**
- Use PREWHERE for filtering
- Leverage materialized views for aggregations
- Optimize ORDER BY with primary key
- Use sampling for approximate queries

### 4. Caching Strategy

**Redis Cache Layers:**
- L1: Frequently accessed alerts (TTL: 5 min)
- L2: User sessions (TTL: 30 min)
- L3: Dashboard data (TTL: 1 min)
- L4: API rate limiting (TTL: 1 hour)

---

## Backup & Recovery

### 1. Elasticsearch
- Snapshot to S3 every 6 hours
- Retention: 7 days of snapshots
- Point-in-time recovery capability
- Cross-cluster replication for DR

### 2. ClickHouse
- Incremental backups to S3 daily
- Full backup weekly
- Replication for HA
- Restore time: <2 hours for 1TB

### 3. PostgreSQL
- Continuous archiving with WAL
- Point-in-time recovery (PITR)
- Daily full backups
- Streaming replication to standby
- Automated failover with Patroni

### 4. Redis
- RDB snapshots every hour
- AOF for durability
- Replication for HA
- Restore time: <10 minutes

### Recovery Time Objectives (RTO)
- PostgreSQL: <5 minutes (automatic failover)
- Elasticsearch: <15 minutes (replica promotion)
- ClickHouse: <30 minutes (manual intervention)
- Redis: <5 minutes (automatic failover)

### Recovery Point Objectives (RPO)
- PostgreSQL: <1 minute
- Elasticsearch: <5 minutes
- ClickHouse: <1 hour
- Redis: <5 minutes

---

## Database Sizing

### Initial Deployment (10K EPS)

| Database      | Size      | IOPS   | Memory | CPU Cores |
|--------------|-----------|--------|--------|-----------|
| Elasticsearch| 500GB     | 5000   | 64GB   | 16        |
| ClickHouse   | 2TB       | 2000   | 32GB   | 8         |
| PostgreSQL   | 100GB     | 3000   | 16GB   | 8         |
| Redis        | 32GB      | 10000  | 32GB   | 4         |

### Growth Projection (100K EPS)

| Database      | Size      | IOPS   | Memory | CPU Cores |
|--------------|-----------|--------|--------|-----------|
| Elasticsearch| 5TB       | 20000  | 256GB  | 64        |
| ClickHouse   | 20TB      | 10000  | 128GB  | 32        |
| PostgreSQL   | 500GB     | 5000   | 64GB   | 16        |
| Redis        | 128GB     | 30000  | 128GB  | 16        |

---

## Conclusion

This database architecture provides:
- **Scalability**: Horizontal scaling for all components
- **Performance**: Optimized for different access patterns
- **Cost-effectiveness**: Hot-warm-cold tiering
- **Reliability**: Multi-replica setup with automatic failover
- **Compliance**: Long-term retention and audit trails
- **Flexibility**: Polyglot persistence for optimal use cases

The architecture is designed to grow from 10K EPS to 1M+ EPS while maintaining performance and cost efficiency.
