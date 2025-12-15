# Agent Architecture for SIEM Solution

## Table of Contents
1. [Overview](#overview)
2. [Agent Requirements Analysis](#agent-requirements-analysis)
3. [Agent Architecture](#agent-architecture)
4. [Agent Types](#agent-types)
5. [Agent Design](#agent-design)
6. [Deployment Strategies](#deployment-strategies)
7. [Communication Protocols](#communication-protocols)
8. [Security Considerations](#security-considerations)

---

## Overview

This document analyzes whether agents are required for the SIEM solution and, if so, details their architecture, design patterns, and deployment strategies.

---

## Agent Requirements Analysis

### Do We Need Agents? YES ✅

**Rationale:**

Agents are **essential** for comprehensive security monitoring because:

1. **Endpoint Visibility**
   - Deep visibility into host-level activities
   - Process execution monitoring
   - File integrity monitoring
   - Registry changes (Windows)
   - User activity tracking

2. **Real-time Collection**
   - Immediate event capture at source
   - Low-latency data transmission
   - Real-time threat detection

3. **Local Processing**
   - Pre-filtering at source reduces network bandwidth
   - Local enrichment (process tree, user context)
   - Reduced load on central systems

4. **Agentless Limitations**
   - Cannot monitor disconnected systems
   - Limited visibility into endpoint activities
   - Higher network overhead
   - Delayed detection

5. **Cloud & Container Environments**
   - Dynamic infrastructure requires lightweight agents
   - Container-aware monitoring
   - Cloud instance monitoring

### When Agents Are NOT Required

**Agentless collection is suitable for:**

1. **Network Devices**
   - Firewalls, routers, switches
   - Use syslog, SNMP, APIs
   - Cannot install agents

2. **Legacy Systems**
   - Unsupported operating systems
   - Critical systems with no changes allowed
   - Use network traffic analysis

3. **Cloud Services**
   - SaaS applications (Office 365, Salesforce)
   - Cloud platform logs (AWS CloudTrail, Azure Activity)
   - API-based collection

4. **Security Appliances**
   - IDS/IPS, WAF, DLP
   - API or syslog integration

---

## Agent Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Monitored Host                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              SIEM Agent (Process)                     │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │                                                        │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │
│  │  │  Collector  │  │  Processor  │  │  Forwarder  │  │  │
│  │  │   Modules   │  │   Engine    │  │   Module    │  │  │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  │  │
│  │         │                │                │          │  │
│  │  ┌──────┴────────────────┴────────────────┴──────┐  │  │
│  │  │           Configuration Manager               │  │  │
│  │  └───────────────────────────────────────────────┘  │  │
│  │                                                        │  │
│  │  ┌───────────────────────────────────────────────┐  │  │
│  │  │            Local Buffer/Queue                  │  │  │
│  │  └───────────────────────────────────────────────┘  │  │
│  │                                                        │  │
│  └────────────────────────┬───────────────────────────────┘  │
│                           │                                   │
│                           ↓                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         OS APIs & Data Sources                        │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │  • File System Events    • Process Creation          │  │
│  │  • Network Connections   • Registry Changes          │  │
│  │  • Log Files             • Authentication Events     │  │
│  │  • Performance Metrics   • Kernel Events             │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                               │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            │ TLS 1.3 (gRPC/HTTP)
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   SIEM Platform                              │
├─────────────────────────────────────────────────────────────┤
│  Load Balancer → Agent Manager → Message Queue (Kafka)      │
└─────────────────────────────────────────────────────────────┘
```

---

## Agent Types

### 1. Universal Agent (Recommended for Most Deployments)

**Capabilities:**
- Log collection (file tailing, Windows Event Logs, syslog)
- Process monitoring
- Network connection tracking
- File integrity monitoring
- Registry monitoring (Windows)
- Performance metrics

**Supported Platforms:**
- Windows (7, 8, 10, 11, Server 2012+)
- Linux (RHEL, CentOS, Ubuntu, Debian, SUSE)
- macOS (10.14+)

**Resource Footprint:**
- CPU: <2% average
- Memory: 50-100 MB
- Disk: 100 MB (agent + buffer)
- Network: ~10 KB/s average

### 2. Lightweight Log Forwarder

**Capabilities:**
- Log file collection only
- Minimal processing
- Ultra-low resource usage

**Use Cases:**
- Resource-constrained systems
- Container/pod sidecar
- IoT devices

**Resource Footprint:**
- CPU: <1%
- Memory: 10-20 MB
- Disk: 10 MB

### 3. Network Traffic Agent

**Capabilities:**
- Packet capture and analysis
- Flow data collection (NetFlow, sFlow)
- Protocol analysis
- Deep packet inspection

**Deployment:**
- Network TAP points
- SPAN/mirror ports
- Dedicated capture hosts

### 4. Cloud-Native Agent

**Capabilities:**
- Container-aware monitoring
- Kubernetes/Docker integration
- Cloud metadata enrichment
- Serverless function monitoring

**Supported Platforms:**
- Docker containers
- Kubernetes pods
- AWS ECS/Fargate
- Azure Container Instances
- Google Cloud Run

---

## Agent Design

### Component Architecture

```go
// Agent Core Structure
package agent

type Agent struct {
    config      *Config
    collectors  []Collector
    processor   *Processor
    forwarder   *Forwarder
    buffer      *LocalBuffer
    healthCheck *HealthCheck
    
    ctx         context.Context
    cancelFunc  context.CancelFunc
}

// Agent initialization
func NewAgent(configPath string) (*Agent, error) {
    config, err := LoadConfig(configPath)
    if err != nil {
        return nil, err
    }
    
    agent := &Agent{
        config:      config,
        collectors:  make([]Collector, 0),
        processor:   NewProcessor(),
        forwarder:   NewForwarder(config.ServerURL, config.APIKey),
        buffer:      NewLocalBuffer(config.BufferSize),
        healthCheck: NewHealthCheck(),
    }
    
    // Initialize collectors based on config
    agent.initializeCollectors()
    
    return agent, nil
}

// Start agent
func (a *Agent) Start() error {
    a.ctx, a.cancelFunc = context.WithCancel(context.Background())
    
    // Start health check endpoint
    go a.healthCheck.Start(a.ctx)
    
    // Start collectors
    for _, collector := range a.collectors {
        go collector.Start(a.ctx)
    }
    
    // Start processor
    go a.processor.Start(a.ctx)
    
    // Start forwarder
    go a.forwarder.Start(a.ctx)
    
    // Monitor and orchestrate
    return a.run()
}

// Main run loop
func (a *Agent) run() error {
    ticker := time.NewTicker(10 * time.Second)
    defer ticker.Stop()
    
    for {
        select {
        case <-a.ctx.Done():
            return a.shutdown()
            
        case <-ticker.C:
            // Periodic health check
            a.performHealthCheck()
            
            // Check for configuration updates
            a.checkConfigUpdate()
        }
    }
}

// Graceful shutdown
func (a *Agent) shutdown() error {
    log.Info("Shutting down agent...")
    
    // Stop collectors
    for _, collector := range a.collectors {
        collector.Stop()
    }
    
    // Flush buffer
    a.buffer.Flush()
    
    // Stop forwarder
    a.forwarder.Stop()
    
    log.Info("Agent shutdown complete")
    return nil
}
```

### Collector Interface

```go
// Collector interface for different data sources
type Collector interface {
    Start(ctx context.Context) error
    Stop() error
    Collect() (<-chan Event, error)
    GetMetrics() CollectorMetrics
}

// File Collector (log tailing)
type FileCollector struct {
    paths       []string
    tailers     []*tail.Tail
    eventChan   chan Event
    
    config      *FileCollectorConfig
}

func (fc *FileCollector) Start(ctx context.Context) error {
    for _, path := range fc.paths {
        t, err := tail.TailFile(path, tail.Config{
            Follow:    true,
            ReOpen:    true,
            MustExist: false,
            Location:  &tail.SeekInfo{Offset: 0, Whence: 2}, // End of file
        })
        
        if err != nil {
            return err
        }
        
        fc.tailers = append(fc.tailers, t)
        
        go func(tailer *tail.Tail) {
            for line := range tailer.Lines {
                event := Event{
                    Timestamp: time.Now(),
                    Type:      "log",
                    Source:    path,
                    Data:      line.Text,
                }
                
                select {
                case fc.eventChan <- event:
                case <-ctx.Done():
                    return
                }
            }
        }(t)
    }
    
    return nil
}

// Process Collector (process monitoring)
type ProcessCollector struct {
    eventChan   chan Event
    interval    time.Duration
}

func (pc *ProcessCollector) Start(ctx context.Context) error {
    ticker := time.NewTicker(pc.interval)
    defer ticker.Stop()
    
    for {
        select {
        case <-ctx.Done():
            return nil
            
        case <-ticker.C:
            processes, err := process.Processes()
            if err != nil {
                log.Error("Failed to get processes: %v", err)
                continue
            }
            
            for _, p := range processes {
                event := pc.createProcessEvent(p)
                select {
                case pc.eventChan <- event:
                case <-ctx.Done():
                    return nil
                }
            }
        }
    }
}

// Network Collector (connection monitoring)
type NetworkCollector struct {
    eventChan   chan Event
}

func (nc *NetworkCollector) Start(ctx context.Context) error {
    // TODO: Monitor network connections
    // Platform-specific implementation
    // Linux: /proc/net/tcp, netlink sockets
    // Windows: GetTcpTable, GetUdpTable APIs
    // macOS: lsof, netstat commands
    
    // Implementation would:
    // 1. Subscribe to network events
    // 2. Track connection state changes
    // 3. Detect port scans and anomalies
    // 4. Send events to eventChan
    
    return nil
}

// Windows Event Log Collector
type WindowsEventCollector struct {
    eventChan   chan Event
    channels    []string // Security, System, Application
}

func (wec *WindowsEventCollector) Start(ctx context.Context) error {
    // Subscribe to Windows Event Log channels
    // Use Windows Event Log API
    
    return nil
}
```

### Processor Engine

```go
// Processor handles event transformation and enrichment
type Processor struct {
    inputChan   <-chan Event
    outputChan  chan ProcessedEvent
    
    filters     []Filter
    enrichers   []Enricher
}

func (p *Processor) Start(ctx context.Context) error {
    for {
        select {
        case <-ctx.Done():
            return nil
            
        case event := <-p.inputChan:
            // Apply filters
            if p.shouldFilter(event) {
                continue
            }
            
            // Normalize event
            normalized := p.normalize(event)
            
            // Enrich event
            enriched := p.enrich(normalized)
            
            // Send to output
            select {
            case p.outputChan <- enriched:
            case <-ctx.Done():
                return nil
            }
        }
    }
}

func (p *Processor) normalize(event Event) ProcessedEvent {
    // Convert to standard format
    return ProcessedEvent{
        Timestamp: event.Timestamp.UTC(),
        Type:      event.Type,
        Source:    event.Source,
        Data:      event.Data,
        Metadata: map[string]interface{}{
            "agent_id":      getAgentID(),
            "hostname":      getHostname(),
            "os":            runtime.GOOS,
            "agent_version": getVersion(),
        },
    }
}

func (p *Processor) enrich(event ProcessedEvent) ProcessedEvent {
    // Add local context
    for _, enricher := range p.enrichers {
        event = enricher.Enrich(event)
    }
    
    return event
}

// Local enrichers
type ProcessEnricher struct{}

func (pe *ProcessEnricher) Enrich(event ProcessedEvent) ProcessedEvent {
    // Add process tree information
    // Add user context
    // Add parent process details
    return event
}

type FileEnricher struct{}

func (fe *FileEnricher) Enrich(event ProcessedEvent) ProcessedEvent {
    // Add file hash
    // Add file metadata
    return event
}
```

### Forwarder Module

```go
// Forwarder sends events to SIEM platform
type Forwarder struct {
    serverURL   string
    apiKey      string
    client      *http.Client
    
    batchSize   int
    batchTime   time.Duration
    
    buffer      *LocalBuffer
    inputChan   <-chan ProcessedEvent
}

func NewForwarder(serverURL, apiKey string) *Forwarder {
    return &Forwarder{
        serverURL: serverURL,
        apiKey:    apiKey,
        client: &http.Client{
            Timeout: 30 * time.Second,
            Transport: &http.Transport{
                TLSClientConfig: &tls.Config{
                    MinVersion: tls.VersionTLS13,
                },
            },
        },
        batchSize: 100,
        batchTime: 5 * time.Second,
    }
}

func (f *Forwarder) Start(ctx context.Context) error {
    batch := make([]ProcessedEvent, 0, f.batchSize)
    ticker := time.NewTicker(f.batchTime)
    defer ticker.Stop()
    
    for {
        select {
        case <-ctx.Done():
            // Flush remaining events
            if len(batch) > 0 {
                f.sendBatch(batch)
            }
            return nil
            
        case event := <-f.inputChan:
            batch = append(batch, event)
            
            // Send when batch is full
            if len(batch) >= f.batchSize {
                if err := f.sendBatch(batch); err != nil {
                    // Buffer for retry
                    f.buffer.Store(batch)
                }
                batch = make([]ProcessedEvent, 0, f.batchSize)
            }
            
        case <-ticker.C:
            // Send batch on timer
            if len(batch) > 0 {
                if err := f.sendBatch(batch); err != nil {
                    f.buffer.Store(batch)
                }
                batch = make([]ProcessedEvent, 0, f.batchSize)
            }
            
            // Retry buffered events
            f.retryBuffered()
        }
    }
}

func (f *Forwarder) sendBatch(events []ProcessedEvent) error {
    payload, err := json.Marshal(events)
    if err != nil {
        return err
    }
    
    // Compress payload
    compressed := compress(payload)
    
    req, err := http.NewRequest("POST", f.serverURL+"/api/v1/events", bytes.NewReader(compressed))
    if err != nil {
        return err
    }
    
    req.Header.Set("Content-Type", "application/json")
    req.Header.Set("Content-Encoding", "gzip")
    req.Header.Set("Authorization", "Bearer "+f.apiKey)
    req.Header.Set("X-Agent-ID", getAgentID())
    
    resp, err := f.client.Do(req)
    if err != nil {
        return err
    }
    defer resp.Body.Close()
    
    if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusAccepted {
        return fmt.Errorf("server returned %d", resp.StatusCode)
    }
    
    return nil
}

func (f *Forwarder) retryBuffered() {
    buffered := f.buffer.GetAll()
    for _, batch := range buffered {
        if err := f.sendBatch(batch); err == nil {
            f.buffer.Remove(batch)
        }
    }
}
```

### Local Buffer (Disk-backed Queue)

```go
// LocalBuffer provides persistent buffering
type LocalBuffer struct {
    path      string
    maxSize   int64
    db        *bbolt.DB
}

func NewLocalBuffer(maxSize int64) *LocalBuffer {
    db, err := bbolt.Open("agent-buffer.db", 0600, nil)
    if err != nil {
        log.Fatal(err)
    }
    
    db.Update(func(tx *bbolt.Tx) error {
        _, err := tx.CreateBucketIfNotExists([]byte("events"))
        return err
    })
    
    return &LocalBuffer{
        maxSize: maxSize,
        db:      db,
    }
}

func (lb *LocalBuffer) Store(events []ProcessedEvent) error {
    return lb.db.Update(func(tx *bbolt.Tx) error {
        bucket := tx.Bucket([]byte("events"))
        
        for _, event := range events {
            id := generateID()
            data, err := json.Marshal(event)
            if err != nil {
                return err
            }
            
            if err := bucket.Put([]byte(id), data); err != nil {
                return err
            }
        }
        
        return nil
    })
}

func (lb *LocalBuffer) GetAll() [][]ProcessedEvent {
    var batches [][]ProcessedEvent
    
    lb.db.View(func(tx *bbolt.Tx) error {
        bucket := tx.Bucket([]byte("events"))
        
        batch := make([]ProcessedEvent, 0)
        
        bucket.ForEach(func(k, v []byte) error {
            var event ProcessedEvent
            if err := json.Unmarshal(v, &event); err != nil {
                return err
            }
            
            batch = append(batch, event)
            
            if len(batch) >= 100 {
                batches = append(batches, batch)
                batch = make([]ProcessedEvent, 0)
            }
            
            return nil
        })
        
        if len(batch) > 0 {
            batches = append(batches, batch)
        }
        
        return nil
    })
    
    return batches
}

func (lb *LocalBuffer) Flush() error {
    // Send all buffered events before shutdown
    return nil
}
```

---

## Deployment Strategies

### 1. Traditional Server/Workstation Deployment

**Installation Methods:**

**Windows:**
```powershell
# MSI installer
msiexec /i siem-agent-1.0.0.msi /qn SERVERURL="https://siem.company.com" APIKEY="xxx"

# Or via PowerShell script
Invoke-WebRequest -Uri "https://siem.company.com/agent/install.ps1" | Invoke-Expression
```

**Linux:**
```bash
# RPM (RHEL/CentOS)
rpm -i siem-agent-1.0.0.rpm

# DEB (Ubuntu/Debian)
dpkg -i siem-agent-1.0.0.deb

# Or via install script
curl -sSL https://siem.company.com/agent/install.sh | bash
```

**Service Management:**
```bash
# Linux systemd
systemctl start siem-agent
systemctl enable siem-agent
systemctl status siem-agent

# Windows Service
sc start SIEMAgent
sc config SIEMAgent start= auto
```

### 2. Container Deployment

**Docker Sidecar:**
```yaml
# docker-compose.yml
version: '3'
services:
  app:
    image: myapp:latest
    
  siem-agent:
    image: siem/agent:latest
    environment:
      - SIEM_SERVER_URL=https://siem.company.com
      - SIEM_API_KEY=${SIEM_API_KEY}
    volumes:
      - /var/log:/var/log:ro
      - app-logs:/app/logs:ro
    network_mode: "service:app"
```

**Kubernetes DaemonSet:**
```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: siem-agent
  namespace: kube-system
spec:
  selector:
    matchLabels:
      app: siem-agent
  template:
    metadata:
      labels:
        app: siem-agent
    spec:
      hostNetwork: true
      hostPID: true
      containers:
      - name: siem-agent
        image: siem/agent:latest
        env:
        - name: SIEM_SERVER_URL
          value: "https://siem.company.com"
        - name: SIEM_API_KEY
          valueFrom:
            secretKeyRef:
              name: siem-agent-secret
              key: api-key
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName
        volumeMounts:
        - name: var-log
          mountPath: /var/log
          readOnly: true
        - name: container-logs
          mountPath: /var/lib/docker/containers
          readOnly: true
        securityContext:
          privileged: true
      volumes:
      - name: var-log
        hostPath:
          path: /var/log
      - name: container-logs
        hostPath:
          path: /var/lib/docker/containers
```

### 3. Cloud Instance Deployment

**AWS EC2:**
```bash
# User data script
#!/bin/bash
curl -sSL https://siem.company.com/agent/install.sh | bash
/opt/siem-agent/bin/configure \
  --server-url https://siem.company.com \
  --api-key ${SIEM_API_KEY} \
  --cloud-provider aws \
  --instance-id $(ec2-metadata --instance-id | cut -d " " -f 2)
systemctl start siem-agent
```

**Azure VM:**
```bash
# Custom Script Extension
az vm extension set \
  --resource-group myResourceGroup \
  --vm-name myVM \
  --name CustomScript \
  --publisher Microsoft.Azure.Extensions \
  --settings '{"fileUris": ["https://siem.company.com/agent/install.sh"]}' \
  --protected-settings '{"commandToExecute": "./install.sh --api-key APIKEY"}'
```

### 4. Centralized Deployment (Enterprise)

**SCCM (Windows):**
- Package agent as MSI
- Deploy via SCCM collections
- Automated updates

**Ansible:**
```yaml
# playbook.yml
---
- name: Deploy SIEM Agent
  hosts: all
  become: yes
  tasks:
    - name: Download agent package
      get_url:
        url: "https://siem.company.com/agent/{{ ansible_distribution }}/siem-agent-latest.{{ 'rpm' if ansible_os_family == 'RedHat' else 'deb' }}"
        dest: /tmp/siem-agent.pkg
        
    - name: Install agent
      package:
        name: /tmp/siem-agent.pkg
        state: present
        
    - name: Configure agent
      template:
        src: agent.conf.j2
        dest: /etc/siem-agent/agent.conf
        
    - name: Start agent service
      service:
        name: siem-agent
        state: started
        enabled: yes
```

---

## Communication Protocols

### 1. gRPC (Recommended)

**Advantages:**
- Binary protocol (efficient)
- HTTP/2 multiplexing
- Built-in TLS support
- Bi-directional streaming

**Usage:**
```protobuf
// events.proto
syntax = "proto3";

service AgentService {
  // Upload events in batch
  rpc UploadEvents(stream EventBatch) returns (UploadResponse);
  
  // Get agent configuration
  rpc GetConfig(ConfigRequest) returns (AgentConfig);
  
  // Health check
  rpc HealthCheck(HealthRequest) returns (HealthResponse);
}

message Event {
  string id = 1;
  int64 timestamp = 2;
  string type = 3;
  bytes data = 4;
  map<string, string> metadata = 5;
}

message EventBatch {
  repeated Event events = 1;
  string agent_id = 2;
}
```

### 2. HTTPS/REST (Alternative)

**Endpoints:**
```
POST   /api/v1/events          # Upload events
GET    /api/v1/config          # Get configuration
POST   /api/v1/heartbeat       # Send heartbeat
GET    /api/v1/health          # Health check
```

### 3. WebSocket (Real-time Commands)

**Use Case:** Remote agent control
```javascript
// Agent connects to server
ws://siem.company.com/agent/ws

// Server can send commands:
{
  "command": "update_config",
  "data": {...}
}

{
  "command": "collect_diagnostics",
  "data": {...}
}
```

---

## Security Considerations

### 1. Agent Authentication

**Methods:**
- API key (pre-shared secret)
- Certificate-based (mutual TLS)
- Token-based (JWT with rotation)

```go
// Certificate-based authentication
tlsConfig := &tls.Config{
    Certificates: []tls.Certificate{agentCert},
    RootCAs:      serverCertPool,
    MinVersion:   tls.VersionTLS13,
}
```

### 2. Data Encryption

- **In Transit**: TLS 1.3
- **In Buffer**: AES-256 encryption of local buffer
- **Sensitive Fields**: Field-level encryption for passwords, tokens

### 3. Agent Hardening

**Security Measures:**
- Run as non-privileged user (where possible)
- Minimal file system permissions
- No remote code execution
- Signed binaries (code signing)
- Regular security updates

### 4. Configuration Security

```yaml
# agent.conf - secure configuration
server:
  url: https://siem.company.com
  api_key: ${SIEM_API_KEY}  # Environment variable
  
  tls:
    min_version: 1.3
    verify_server: true
    ca_cert: /etc/siem-agent/ca.crt
    
security:
  encrypt_buffer: true
  mask_sensitive_data: true
  
  rate_limits:
    max_events_per_second: 1000
    max_buffer_size: 1GB
```

### 5. Update Mechanism

**Secure Auto-Update:**
1. Server publishes new version
2. Agent checks for updates (signed manifest)
3. Download new binary (TLS)
4. Verify signature
5. Backup current version
6. Install and restart
7. Rollback if health check fails

---

## Performance & Resource Management

### Resource Limits

```yaml
# agent.conf
resources:
  cpu_limit: 2%           # Maximum CPU usage
  memory_limit: 100MB     # Maximum memory
  disk_buffer: 1GB        # Maximum buffer size
  network_bandwidth: 1Mbps # Max network usage
  
collection:
  max_events_per_second: 1000
  batch_size: 100
  batch_interval: 5s
```

### Adaptive Collection

```go
// Throttle collection based on system load
func (a *Agent) adaptiveCollection() {
    cpuUsage := getCPUUsage()
    
    if cpuUsage > 80 {
        a.collector.SetRate(500)  // Reduce rate
    } else if cpuUsage < 50 {
        a.collector.SetRate(1000) // Normal rate
    }
}
```

---

## Monitoring & Health

### Agent Metrics

**Exposed Metrics:**
- Events collected per second
- Events forwarded per second
- Buffer size
- CPU/Memory usage
- Network errors
- Last successful send timestamp

**Health Check Endpoint:**
```
GET http://localhost:8080/health

Response:
{
  "status": "healthy",
  "version": "1.0.0",
  "uptime": "2d 4h 30m",
  "last_send": "2024-01-15T10:30:00Z",
  "buffer_size": 1024,
  "collectors": {
    "file": "running",
    "process": "running",
    "network": "running"
  }
}
```

---

## Conclusion

### Agent Strategy Summary

✅ **Agents are REQUIRED** for:
- Endpoint visibility (servers, workstations)
- Real-time event collection
- Local processing and enrichment
- Container and cloud monitoring

✅ **Recommended Agent Approach**:
- Universal agent for most deployments
- Lightweight forwarder for containers
- DaemonSet for Kubernetes
- gRPC for communication
- Certificate-based authentication
- Auto-update capability

✅ **Agentless Collection** for:
- Network devices
- Cloud service logs
- Security appliances
- Legacy systems

This hybrid approach (agents + agentless) provides comprehensive coverage while optimizing for performance, security, and maintainability.
