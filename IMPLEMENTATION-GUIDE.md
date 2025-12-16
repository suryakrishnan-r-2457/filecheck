# Log360Cloud and DataSecurity Plus Integration - Implementation Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Installation Steps](#installation-steps)
3. [Configuration](#configuration)
4. [Testing & Validation](#testing--validation)
5. [Troubleshooting](#troubleshooting)
6. [Best Practices](#best-practices)

---

## 1. Prerequisites

### 1.1 System Requirements

#### Log360Cloud Agent
- **Operating Systems**: Windows Server 2012 R2+, Windows 10+, Linux (RHEL/CentOS 7+, Ubuntu 18.04+)
- **CPU**: 2 vCPU minimum (4 vCPU recommended)
- **RAM**: 4 GB minimum (8 GB recommended)
- **Disk Space**: 50 GB minimum for log buffering
- **Network**: Internet connectivity (HTTPS/443) to Zoho Logs cloud

#### DataSecurity Plus Server
- **Operating Systems**: Windows Server 2012 R2+, Linux (RHEL/CentOS 7+)
- **CPU**: 8 vCPU minimum (16 vCPU for > 1000 agents)
- **RAM**: 16 GB minimum (32 GB for > 1000 agents)
- **Disk Space**: 500 GB minimum (SSD recommended)
- **Database**: PostgreSQL 12+ or SQL Server 2016+

#### DLP Agent (Endpoint)
- **Operating Systems**: Windows 7+, Windows Server 2008 R2+
- **RAM**: 2 GB minimum
- **Disk Space**: 10 GB minimum (for offline buffering)
- **Kernel Driver**: Windows minifilter driver support

### 1.2 Network Requirements

| Source | Destination | Port | Protocol | Purpose |
|--------|-------------|------|----------|---------|
| Log360 Agent | Zoho Logs Cloud | 443 | HTTPS | Log upload |
| DLP Agent | DSP Server | 8443 | HTTPS | Event reporting |
| DSP Server | Log360 Agent | 514, 6514 | Syslog (UDP/TCP) | Event forwarding |
| Admin Browser | DSP Server | 8443 | HTTPS | Web console |
| Admin Browser | Log360Cloud | 443 | HTTPS | Web console |

### 1.3 Licensing
- **Log360Cloud**: Cloud SIEM license (per device)
- **DataSecurity Plus**: DLP license (per agent)
- Ensure licenses are valid and activated before deployment

---

## 2. Installation Steps

### 2.1 Step 1: Install DataSecurity Plus Server

```bash
# Download DataSecurity Plus installer
wget https://www.manageengine.com/products/data-security/download.html

# Extract and run installer (Windows)
.\ManageEngine_DataSecurityPlus_64bit.exe

# Or for Linux
sh ManageEngine_DataSecurityPlus_64bit.bin
```

**Installation Wizard Steps**:
1. Accept license agreement
2. Choose installation directory: `C:\ManageEngine\DataSecurityPlus`
3. Configure database:
   - Type: PostgreSQL (bundled) or External SQL Server
   - For PostgreSQL: Accept default settings
4. Set admin password
5. Configure service to start automatically
6. Complete installation

**Post-Installation**:
```bash
# Verify service is running (Windows)
sc query "ManageEngine DataSecurity Plus"

# Verify service (Linux)
systemctl status datasecurityplus

# Access web console
https://<server-ip>:8443
```

### 2.2 Step 2: Deploy DLP Agents

#### Option A: Agent Deployment via Web Console

1. Login to DSP Web Console: `https://<dsp-server>:8443`
2. Navigate to: **Admin** → **Agent Management** → **Deploy Agents**
3. Select deployment method:
   - **Active Directory**: Deploy via GPO
   - **Manual**: Download installer and deploy manually
   - **Remote**: Use DSP's remote deployment tool

4. Configure agent settings:
   ```json
   {
     "server_ip": "192.168.1.100",
     "server_port": 8443,
     "buffer_size_mb": 2048,
     "modules": {
       "file_audit": true,
       "dlp": true,
       "device_control": true
     }
   }
   ```

5. Deploy to target endpoints

#### Option B: Manual Agent Installation

```powershell
# Download agent installer
Invoke-WebRequest -Uri "https://<dsp-server>:8443/agent/download" -OutFile "DSPAgent.exe"

# Install silently with configuration
.\DSPAgent.exe /S /SERVER=192.168.1.100 /PORT=8443 /KEY=<agent-key>

# Verify installation
Get-Service "DSPAgent"
```

### 2.3 Step 3: Deploy Log360Cloud Agents

1. Login to Log360Cloud portal: `https://log360cloud.manageengine.com`
2. Navigate to: **Admin** → **Agents** → **Download Agent**
3. Select OS type and download installer

**Windows Deployment**:
```powershell
# Run installer
.\Log360CloudAgent.exe /SILENT /CLOUDKEY=<your-cloud-key>

# Verify
Get-Service "Log360CloudAgent"
```

**Linux Deployment**:
```bash
# Extract and install
tar -xzf log360cloud-agent-linux.tar.gz
cd log360cloud-agent
sudo ./install.sh --cloud-key <your-cloud-key>

# Verify
sudo systemctl status log360cloudagent
```

### 2.4 Step 4: Configure SIEM Integration

#### A. Configure Syslog in DataSecurity Plus

1. Login to DSP Web Console
2. Navigate to: **Configuration** → **Administration** → **SIEM Integration**
3. Click **+ Add Configuration**
4. Configure Syslog settings:

```
Integration Name: Log360Cloud SIEM
Protocol: Syslog
Server IP: <Log360-Agent-IP>
Port: 514 (UDP) or 6514 (TCP)
Standard: RFC 5424
Format: JSON
Severity Mapping:
  - Critical → Critical
  - High → High
  - Medium → Medium
  - Low → Low
  - Info → Info

Event Filters:
  ☑ File Access Violations
  ☑ DLP Policy Violations
  ☑ Device Control Events
  ☐ Informational Events (optional)
```

5. Click **Test Connection**
6. Save configuration

#### B. Configure Custom Log Source in Log360Cloud

1. Login to Log360Cloud portal
2. Navigate to: **Admin** → **Log Sources** → **Add Log Source**
3. Select **Syslog Server**
4. Configure:

```
Name: DataSecurity Plus DLP
Protocol: Syslog
Listening Port: 514 (or 6514 for TCP)
Format: Auto-detect (or Custom)
```

5. Create custom parser:

```json
{
  "parser_name": "DataSecurityPlus_DLP",
  "patterns": [
    {
      "pattern": "\\[DLP\\] User: (?<username>\\S+), File: (?<file_path>.+), Action: (?<action>\\S+), Policy: (?<policy_name>.+)",
      "fields": {
        "username": "string",
        "file_path": "string",
        "action": "string",
        "policy_name": "string"
      }
    }
  ],
  "field_mapping": {
    "timestamp": "event_time",
    "severity": "severity_level",
    "source": "dsp_server"
  }
}
```

6. Save and enable log source

---

## 3. Configuration

### 3.1 DLP Policy Configuration

#### Create Sample DLP Policy for PII Protection

1. In DSP Web Console: **Policies** → **DLP Policies** → **Create Policy**

```yaml
Policy Name: PII Protection - SSN Detection
Description: Detect and block Social Security Numbers in file transfers

Rules:
  - Rule 1: Content Pattern Matching
    Type: Regular Expression
    Pattern: \b\d{3}-\d{2}-\d{4}\b
    File Types: .txt, .doc, .docx, .pdf, .xls, .xlsx
    Action: BLOCK
    Alert: Yes
    Quarantine: Yes

  - Rule 2: Device Control
    Device Type: USB Mass Storage
    Action: BLOCK (if Rule 1 matches)
    Notification: "Transfer blocked: File contains sensitive data (SSN)"

Scope:
  - All Users
  - All Departments
  - All Workstations with DLP Agent

Priority: 10 (High)
Enabled: Yes
```

2. Save and deploy policy to agents

### 3.2 Correlation Rule Configuration

#### Create Insider Threat Correlation Rule

1. In Log360Cloud: **Configuration** → **Correlation Rules** → **Create Rule**

```yaml
Rule Name: Potential Insider Threat - Data Exfiltration
Description: Detect users attempting to exfiltrate data via multiple channels

Conditions:
  - Event Type: DLP_EVENT
    Field: action_taken
    Value: BLOCKED
    Severity: HIGH, CRITICAL
    Count: >= 3
    Time Window: 1 hour

  AND
  
  - Event Type: SECURITY_EVENT
    Field: event_type
    Value: FILE_ACCESS
    User: same_as_dlp_event.username
    Count: >= 10
    Time Window: 1 hour

  AND (Optional)
  
  - Event Type: SECURITY_EVENT
    Field: event_type
    Value: VPN_LOGIN
    User: same_as_dlp_event.username
    Time: After_hours (6pm - 6am)

Action:
  - Create Alert: Priority HIGH
  - Send Email: security@company.com
  - Create Incident: Type = "Insider Threat"
  - Notify: SOC Analyst

Enabled: Yes
```

### 3.3 Dashboard Configuration

#### Create Unified DLP & SIEM Dashboard

1. In Log360Cloud: **Dashboards** → **Create Dashboard**

```yaml
Dashboard Name: DLP & Security Monitoring

Widgets:
  1. DLP Violations (Last 24 Hours)
     Type: Bar Chart
     Data Source: DLP Events
     Groupby: Policy Name
     Filter: severity IN (HIGH, CRITICAL)

  2. Top Violators
     Type: Table
     Columns: Username, Violation Count, Last Violation Time
     Data Source: DLP Events
     Sort: Violation Count DESC
     Limit: 10

  3. File Access Patterns
     Type: Heat Map
     X-Axis: Hour of Day
     Y-Axis: User
     Data Source: Security Events + DLP Events
     Filter: event_type = FILE_ACCESS

  4. Device Control Events
     Type: Pie Chart
     Data Source: DLP Events
     Groupby: Device Type
     Filter: action_taken = BLOCKED

  5. Correlated Incidents
     Type: Timeline
     Data Source: Correlated Incidents
     Sort: Created Date DESC

  6. Agent Health
     Type: Gauge
     Metric: Online Agents / Total Agents
     Threshold: < 95% = Warning

Refresh Interval: 5 minutes
```

---

## 4. Testing & Validation

### 4.1 Test DLP Policy Enforcement

```powershell
# Test 1: Create file with SSN pattern
$content = "Test SSN: 123-45-6789"
Set-Content -Path "C:\test-ssn.txt" -Value $content

# Test 2: Attempt to copy to USB
# Expected: File transfer blocked by DLP agent

# Test 3: Verify event in DSP
# Check: Configuration → Reports → DLP Violations
```

### 4.2 Test SIEM Integration

```bash
# Test 1: Generate DLP event (from Test 4.1)
# Expected: Event appears in Log360Cloud within 5 minutes

# Test 2: Query Log360Cloud
# Search: source:"DataSecurity Plus" AND severity:HIGH
# Expected: DLP event with all fields populated

# Test 3: Verify correlation
# Create multiple DLP violations by same user
# Expected: Correlated incident created
```

### 4.3 Test Agent Health Monitoring

```powershell
# Test 1: Stop DLP agent
Stop-Service "DSPAgent"

# Expected: Agent status changes to OFFLINE in DSP console within 5 minutes

# Test 2: Start agent
Start-Service "DSPAgent"

# Expected: Agent status changes to ONLINE, buffered events sent

# Test 3: Check Log360 agent
Get-Service "Log360CloudAgent"
# Expected: Running
```

---

## 5. Troubleshooting

### 5.1 Issue: DLP Events Not Appearing in Log360Cloud

**Symptoms**: DLP violations logged in DSP but not visible in Log360Cloud

**Resolution Steps**:

1. **Check SIEM Integration Status**
   ```sql
   -- In DSP database
   SELECT * FROM siem_integrations WHERE enabled = true;
   -- Verify last_successful_send timestamp
   ```

2. **Test Network Connectivity**
   ```powershell
   # From DSP server
   Test-NetConnection -ComputerName <log360-agent-ip> -Port 514
   ```

3. **Check Syslog Configuration**
   - Verify DSP is sending to correct IP:Port
   - Verify Log360 agent is listening on port 514
   ```bash
   # On Log360 agent server
   netstat -an | grep 514
   ```

4. **Check Firewall Rules**
   ```powershell
   # Allow Syslog traffic
   New-NetFirewallRule -DisplayName "Syslog Inbound" -Direction Inbound -LocalPort 514 -Protocol UDP -Action Allow
   ```

5. **Verify Parser Configuration**
   - Test custom parser in Log360Cloud console
   - Check for parsing errors in logs

### 5.2 Issue: High Latency in Event Processing

**Symptoms**: 10+ minute delay between DLP event and SIEM visibility

**Resolution**:

1. **Check Agent Buffer Size**
   ```json
   // Increase buffer size in agent config
   {
     "buffer_size_mb": 4096  // Increase from 2048
   }
   ```

2. **Optimize Network**
   - Enable compression on Log360 agent
   - Use TCP instead of UDP for Syslog (more reliable)

3. **Check Cloud SIEM Status**
   - Verify Log360Cloud service health
   - Contact support if cloud-side delays persist

### 5.3 Issue: Agent Port Conflicts

**Symptoms**: DLP agent fails to start after Log360 agent installation

**Resolution**:

```powershell
# Option 1: Change DLP agent port
# Edit: C:\ManageEngine\DSPAgent\conf\agent.conf
port=8801  # Changed from 8800

# Restart DLP agent
Restart-Service "DSPAgent"

# Option 2: Use port mapping
# Configure firewall to map external 8800 to internal 8801
```

---

## 6. Best Practices

### 6.1 Agent Deployment

1. **Phased Rollout**
   - Pilot: 50-100 endpoints
   - Production: Roll out in batches of 500
   - Monitor for issues after each batch

2. **Agent Placement**
   - Deploy Log360 agents on all endpoints for visibility
   - Deploy DLP agents only on:
     - File servers
     - High-risk workstations (Finance, HR, Legal)
     - Executive laptops

3. **Resource Management**
   - Monitor CPU/memory usage during pilot
   - Adjust buffer sizes based on event volume
   - Schedule agent updates during maintenance windows

### 6.2 Policy Configuration

1. **Start with Audit-Only Mode**
   - Enable DLP policies in "Alert Only" mode initially
   - Analyze violations for false positives
   - Transition to "Block" mode after tuning

2. **Prioritize Policies**
   - High priority: PII, PHI, PCI data
   - Medium priority: Intellectual property
   - Low priority: General data classification

3. **Regular Review**
   - Review DLP policies quarterly
   - Update content patterns based on new threats
   - Remove outdated rules

### 6.3 Correlation & Alerting

1. **Tune Alert Thresholds**
   - Reduce noise by increasing event count thresholds
   - Extend time windows for slow-moving attacks
   - Use severity filters to focus on critical events

2. **Define Escalation Paths**
   - Tier 1: SOC Analyst (NEW, ASSIGNED alerts)
   - Tier 2: Security Engineer (IN_PROGRESS, complex incidents)
   - Tier 3: CISO (CRITICAL severity, confirmed breaches)

3. **Document Response Procedures**
   - Create playbooks for common scenarios:
     - Insider threat
     - Ransomware
     - Data exfiltration
   - Include investigation steps and remediation actions

### 6.4 Compliance & Audit

1. **Enable Comprehensive Logging**
   - Log all configuration changes
   - Track user access to sensitive data
   - Archive logs per retention policy (e.g., 7 years for SOX)

2. **Regular Compliance Reports**
   - Schedule monthly compliance reports
   - Export to PDF and archive securely
   - Review with compliance team quarterly

3. **Data Retention**
   - Configure retention based on regulations:
     - GDPR: 30 days minimum, up to 7 years
     - HIPAA: 6 years
     - PCI-DSS: 1 year minimum
   - Implement automated archival and deletion

### 6.5 Performance Optimization

1. **Database Maintenance**
   ```sql
   -- Run weekly vacuum (PostgreSQL)
   VACUUM ANALYZE security_events;
   VACUUM ANALYZE dlp_events;
   
   -- Rebuild indexes monthly
   REINDEX TABLE security_events;
   REINDEX TABLE dlp_events;
   ```

2. **Partition Large Tables**
   ```sql
   -- Create monthly partitions for events
   CREATE TABLE security_events_2024_12 PARTITION OF security_events
       FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');
   ```

3. **Monitor Storage**
   - Set up alerts for 80% disk usage
   - Archive old data to cold storage
   - Consider cloud storage for long-term retention

---

## Appendix A: Sample SQL Queries

### Query 1: Top DLP Violators (Last 30 Days)
```sql
SELECT 
    username,
    COUNT(*) as violation_count,
    SUM(CASE WHEN severity = 'CRITICAL' THEN 1 ELSE 0 END) as critical_count,
    MAX(timestamp) as last_violation
FROM dlp_events
WHERE timestamp > CURRENT_TIMESTAMP - INTERVAL '30 days'
  AND action_taken IN ('BLOCKED', 'QUARANTINED')
GROUP BY username
ORDER BY violation_count DESC
LIMIT 10;
```

### Query 2: Correlation - File Access + DLP Events
```sql
SELECT 
    se.username,
    se.timestamp as access_time,
    de.timestamp as dlp_event_time,
    de.file_path,
    de.action,
    de.policy_name
FROM security_events se
JOIN dlp_events de ON se.user_id = de.user_id
WHERE se.event_type = 'FILE_ACCESS'
  AND de.severity IN ('HIGH', 'CRITICAL')
  AND se.timestamp BETWEEN de.timestamp - INTERVAL '1 hour' AND de.timestamp
ORDER BY se.timestamp DESC;
```

---

## Appendix B: API Integration Examples

### Python: Forward DLP Events to Custom SIEM
```python
import requests
from datetime import datetime, timedelta

# Fetch DLP events from DataSecurity Plus
dsp_url = "https://dsp-server:8443/api/v1/dlp-events"
headers = {"Authorization": "Bearer YOUR_API_KEY"}
params = {
    "from": (datetime.now() - timedelta(hours=1)).isoformat(),
    "severity": "HIGH,CRITICAL"
}

response = requests.get(dsp_url, headers=headers, params=params, verify=False)
dlp_events = response.json()

# Forward to Log360Cloud
log360_url = "https://api.zoho.com/log360cloud/v1/logs/ingest"
headers = {"Authorization": "Bearer YOUR_LOG360_TOKEN", "Content-Type": "application/json"}

for event in dlp_events:
    payload = {
        "source": "DataSecurityPlus",
        "event_type": "DLP_VIOLATION",
        "timestamp": event["timestamp"],
        "user": event["username"],
        "file": event["file_path"],
        "action": event["action"],
        "severity": event["severity"]
    }
    requests.post(log360_url, json=payload, headers=headers)
```

---

**Document Version**: 1.0  
**Last Updated**: December 2024
