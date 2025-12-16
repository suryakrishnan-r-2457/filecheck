-- ============================================================================
-- Log360Cloud and DataSecurity Plus Integration Database Schema
-- ============================================================================
-- This schema supports unified logging, DLP event tracking, and correlation
-- Compatible with PostgreSQL 12+ and SQL Server 2016+
-- ============================================================================

-- Drop existing tables (for clean installation)
DROP TABLE IF EXISTS alerts CASCADE;
DROP TABLE IF EXISTS dlp_events CASCADE;
DROP TABLE IF EXISTS security_events CASCADE;
DROP TABLE IF EXISTS correlated_incidents CASCADE;
DROP TABLE IF EXISTS correlation_rules CASCADE;
DROP TABLE IF EXISTS dlp_rules CASCADE;
DROP TABLE IF EXISTS dlp_policies CASCADE;
DROP TABLE IF EXISTS siem_integrations CASCADE;
DROP TABLE IF EXISTS agents CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS departments CASCADE;
DROP TABLE IF EXISTS tenants CASCADE;
DROP TABLE IF EXISTS organizations CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS log_sources CASCADE;
DROP TABLE IF EXISTS device_tracking CASCADE;
DROP TABLE IF EXISTS file_metadata CASCADE;

-- ============================================================================
-- CORE ENTITIES
-- ============================================================================

-- Organizations (Root entity for multi-tenant/MSSP deployments)
CREATE TABLE organizations (
    org_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_name VARCHAR(255) NOT NULL UNIQUE,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    license_type VARCHAR(50) NOT NULL,
    license_expiry DATE,
    max_tenants INTEGER DEFAULT 1,
    max_agents INTEGER DEFAULT 100,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    CONSTRAINT chk_org_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
);

-- Tenants (Individual customers or business units)
CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    tenant_name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    max_users INTEGER DEFAULT 100,
    max_agents INTEGER DEFAULT 50,
    storage_quota_gb INTEGER DEFAULT 100,
    data_retention_days INTEGER DEFAULT 365,
    CONSTRAINT chk_tenant_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TRIAL', 'INACTIVE')),
    UNIQUE (org_id, tenant_name)
);

-- Departments
CREATE TABLE departments (
    dept_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    dept_name VARCHAR(255) NOT NULL,
    manager_id UUID,
    parent_dept_id UUID REFERENCES departments(dept_id),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, dept_name)
);

-- Roles and Permissions
CREATE TABLE roles (
    role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system_role BOOLEAN DEFAULT FALSE,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, role_name)
);

CREATE TABLE permissions (
    permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    resource_type VARCHAR(50) NOT NULL, -- 'AGENT', 'POLICY', 'ALERT', 'REPORT', 'SETTINGS'
    action VARCHAR(50) NOT NULL, -- 'CREATE', 'READ', 'UPDATE', 'DELETE', 'EXECUTE'
    granted BOOLEAN DEFAULT TRUE,
    UNIQUE (role_id, resource_type, action)
);

-- Users
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255), -- Store bcrypt hash
    dept_id UUID REFERENCES departments(dept_id),
    role_id UUID REFERENCES roles(role_id),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    mfa_enabled BOOLEAN DEFAULT FALSE,
    failed_login_attempts INTEGER DEFAULT 0,
    account_locked_until TIMESTAMP,
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'PENDING')),
    UNIQUE (tenant_id, username),
    UNIQUE (tenant_id, email)
);

-- ============================================================================
-- AGENT MANAGEMENT
-- ============================================================================

-- Agents (Both Log360Cloud and DLP agents)
CREATE TABLE agents (
    agent_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    agent_type VARCHAR(20) NOT NULL, -- 'LOG360', 'DLP'
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL, -- Supports IPv6
    os_type VARCHAR(50) NOT NULL, -- 'Windows', 'Linux', 'MacOS'
    os_version VARCHAR(100),
    agent_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'OFFLINE',
    installed_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat TIMESTAMP,
    last_config_update TIMESTAMP,
    cpu_usage_percent DECIMAL(5,2),
    memory_usage_mb INTEGER,
    disk_usage_gb DECIMAL(10,2),
    buffer_size_mb INTEGER DEFAULT 2048,
    config_json TEXT, -- JSON configuration for the agent
    CONSTRAINT chk_agent_type CHECK (agent_type IN ('LOG360', 'DLP')),
    CONSTRAINT chk_agent_status CHECK (status IN ('ONLINE', 'OFFLINE', 'DEGRADED', 'UPDATING', 'ERROR')),
    UNIQUE (tenant_id, hostname, agent_type)
);

-- Log Sources (for Log360 agents)
CREATE TABLE log_sources (
    log_source_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID NOT NULL REFERENCES agents(agent_id) ON DELETE CASCADE,
    source_type VARCHAR(50) NOT NULL, -- 'Windows Event Log', 'Syslog', 'IIS', 'Apache', etc.
    source_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    auto_discovered BOOLEAN DEFAULT FALSE,
    last_collection_time TIMESTAMP,
    events_collected_count BIGINT DEFAULT 0,
    UNIQUE (agent_id, source_type, source_name)
);

-- ============================================================================
-- EVENT MANAGEMENT
-- ============================================================================

-- Security Events (from Log360Cloud)
CREATE TABLE security_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- 'LOGIN', 'LOGOUT', 'FILE_ACCESS', 'NETWORK', 'PROCESS', etc.
    source_ip VARCHAR(45),
    dest_ip VARCHAR(45),
    source_port INTEGER,
    dest_port INTEGER,
    protocol VARCHAR(20),
    user_id UUID REFERENCES users(user_id),
    username VARCHAR(255), -- Denormalized for performance
    agent_id UUID REFERENCES agents(agent_id),
    log_source VARCHAR(255),
    timestamp TIMESTAMP NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT,
    raw_log TEXT,
    indexed BOOLEAN DEFAULT FALSE,
    correlation_id UUID, -- Links to correlated_incidents
    CONSTRAINT chk_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'))
);

-- Create indexes for performance
CREATE INDEX idx_security_events_timestamp ON security_events(timestamp DESC);
CREATE INDEX idx_security_events_user ON security_events(user_id);
CREATE INDEX idx_security_events_severity ON security_events(severity);
CREATE INDEX idx_security_events_type ON security_events(event_type);
CREATE INDEX idx_security_events_tenant ON security_events(tenant_id, timestamp DESC);

-- DLP Events (from DataSecurity Plus)
CREATE TABLE dlp_events (
    dlp_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- 'FILE_COPY', 'FILE_DELETE', 'USB_TRANSFER', 'EMAIL_SEND', 'PRINT', etc.
    user_id UUID REFERENCES users(user_id),
    username VARCHAR(255), -- Denormalized
    agent_id UUID REFERENCES agents(agent_id),
    file_path TEXT NOT NULL,
    file_name VARCHAR(255),
    file_size_bytes BIGINT,
    file_hash VARCHAR(128), -- SHA-256 hash
    action VARCHAR(50) NOT NULL, -- 'CREATE', 'MODIFY', 'DELETE', 'COPY', 'MOVE', 'PRINT', 'EMAIL', 'USB_TRANSFER'
    policy_id UUID, -- References dlp_policies
    policy_name VARCHAR(255),
    rule_violated VARCHAR(255),
    device_type VARCHAR(50), -- 'USB', 'CD', 'BLUETOOTH', 'WIFI', 'EMAIL', 'PRINTER'
    device_id VARCHAR(255),
    destination_path TEXT,
    timestamp TIMESTAMP NOT NULL,
    severity VARCHAR(20) NOT NULL,
    action_taken VARCHAR(50), -- 'ALLOWED', 'BLOCKED', 'QUARANTINED', 'ALERTED'
    quarantine BOOLEAN DEFAULT FALSE,
    quarantine_path TEXT,
    alert_sent BOOLEAN DEFAULT FALSE,
    compliance_tags TEXT[], -- Array of compliance labels: 'PII', 'PHI', 'PCI', 'CONFIDENTIAL'
    correlation_id UUID,
    CONSTRAINT chk_dlp_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    CONSTRAINT chk_dlp_action CHECK (action IN ('CREATE', 'MODIFY', 'DELETE', 'COPY', 'MOVE', 'PRINT', 'EMAIL', 'USB_TRANSFER', 'CLOUD_UPLOAD')),
    CONSTRAINT chk_dlp_action_taken CHECK (action_taken IN ('ALLOWED', 'BLOCKED', 'QUARANTINED', 'ALERTED'))
);

-- Create indexes
CREATE INDEX idx_dlp_events_timestamp ON dlp_events(timestamp DESC);
CREATE INDEX idx_dlp_events_user ON dlp_events(user_id);
CREATE INDEX idx_dlp_events_severity ON dlp_events(severity);
CREATE INDEX idx_dlp_events_action ON dlp_events(action);
CREATE INDEX idx_dlp_events_tenant ON dlp_events(tenant_id, timestamp DESC);
CREATE INDEX idx_dlp_events_file_hash ON dlp_events(file_hash);

-- File Metadata (for DLP tracking)
CREATE TABLE file_metadata (
    file_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(128) UNIQUE, -- SHA-256
    file_size_bytes BIGINT,
    file_type VARCHAR(50),
    created_by UUID REFERENCES users(user_id),
    created_date TIMESTAMP,
    modified_by UUID REFERENCES users(user_id),
    modified_date TIMESTAMP,
    owner_user_id UUID REFERENCES users(user_id),
    classification VARCHAR(50), -- 'PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'
    sensitivity_level INTEGER, -- 1-5 scale
    contains_pii BOOLEAN DEFAULT FALSE,
    contains_phi BOOLEAN DEFAULT FALSE,
    contains_pci BOOLEAN DEFAULT FALSE,
    last_scan_date TIMESTAMP,
    CONSTRAINT chk_classification CHECK (classification IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'))
);

-- Device Tracking (for DLP)
CREATE TABLE device_tracking (
    device_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    device_type VARCHAR(50) NOT NULL,
    device_serial VARCHAR(255),
    device_vendor VARCHAR(255),
    device_model VARCHAR(255),
    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP,
    user_id UUID REFERENCES users(user_id),
    agent_id UUID REFERENCES agents(agent_id),
    status VARCHAR(20) DEFAULT 'ALLOWED',
    CONSTRAINT chk_device_status CHECK (status IN ('ALLOWED', 'BLOCKED', 'QUARANTINED'))
);

-- ============================================================================
-- DLP POLICY MANAGEMENT
-- ============================================================================

-- DLP Policies
CREATE TABLE dlp_policies (
    policy_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    policy_name VARCHAR(255) NOT NULL,
    description TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100, -- Lower number = higher priority
    created_by UUID REFERENCES users(user_id),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID REFERENCES users(user_id),
    modified_date TIMESTAMP,
    UNIQUE (tenant_id, policy_name)
);

-- DLP Rules
CREATE TABLE dlp_rules (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id UUID NOT NULL REFERENCES dlp_policies(policy_id) ON DELETE CASCADE,
    rule_name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(50) NOT NULL, -- 'CONTENT_PATTERN', 'FILE_TYPE', 'FILE_SIZE', 'DEVICE_TYPE', 'USER_GROUP'
    condition_json TEXT NOT NULL, -- JSON representation of rule conditions
    action VARCHAR(50) NOT NULL, -- 'ALLOW', 'BLOCK', 'QUARANTINE', 'ALERT'
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    CONSTRAINT chk_rule_type CHECK (rule_type IN ('CONTENT_PATTERN', 'FILE_TYPE', 'FILE_SIZE', 'DEVICE_TYPE', 'USER_GROUP', 'TIME_BASED')),
    CONSTRAINT chk_rule_action CHECK (action IN ('ALLOW', 'BLOCK', 'QUARANTINE', 'ALERT', 'ENCRYPT'))
);

-- ============================================================================
-- ALERT MANAGEMENT
-- ============================================================================

-- Alerts
CREATE TABLE alerts (
    alert_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    event_id UUID, -- Can reference security_events or dlp_events (polymorphic)
    event_type VARCHAR(20) NOT NULL, -- 'SECURITY' or 'DLP'
    rule_name VARCHAR(255),
    alert_type VARCHAR(50) NOT NULL, -- 'THREAT', 'COMPLIANCE', 'POLICY_VIOLATION', 'ANOMALY'
    title VARCHAR(255) NOT NULL,
    description TEXT,
    priority INTEGER DEFAULT 3, -- 1=Critical, 2=High, 3=Medium, 4=Low
    status VARCHAR(20) DEFAULT 'NEW',
    assigned_to UUID REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    false_positive BOOLEAN DEFAULT FALSE,
    CONSTRAINT chk_alert_event_type CHECK (event_type IN ('SECURITY', 'DLP')),
    CONSTRAINT chk_alert_status CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'ESCALATED'))
);

CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_priority ON alerts(priority);
CREATE INDEX idx_alerts_created ON alerts(created_at DESC);
CREATE INDEX idx_alerts_tenant ON alerts(tenant_id, created_at DESC);

-- ============================================================================
-- CORRELATION & INTEGRATION
-- ============================================================================

-- Correlation Rules
CREATE TABLE correlation_rules (
    correlation_rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    rule_name VARCHAR(255) NOT NULL,
    description TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    rule_definition_json TEXT NOT NULL, -- JSON representation of correlation logic
    time_window_minutes INTEGER DEFAULT 60,
    min_event_count INTEGER DEFAULT 2,
    severity_threshold VARCHAR(20) DEFAULT 'MEDIUM',
    action VARCHAR(50) DEFAULT 'CREATE_ALERT', -- 'CREATE_ALERT', 'SEND_EMAIL', 'EXECUTE_SCRIPT'
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, rule_name)
);

-- Correlated Incidents
CREATE TABLE correlated_incidents (
    incident_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    correlation_rule_id UUID REFERENCES correlation_rules(correlation_rule_id),
    incident_type VARCHAR(50) NOT NULL, -- 'INSIDER_THREAT', 'RANSOMWARE', 'DATA_EXFILTRATION', 'BRUTE_FORCE'
    title VARCHAR(255) NOT NULL,
    description TEXT,
    confidence_score DECIMAL(5,2), -- 0-100 scale
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    related_event_count INTEGER DEFAULT 0,
    CONSTRAINT chk_incident_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'CONTAINED', 'RESOLVED', 'CLOSED'))
);

-- Link table for incidents and events
CREATE TABLE incident_events (
    incident_id UUID REFERENCES correlated_incidents(incident_id) ON DELETE CASCADE,
    event_id UUID NOT NULL, -- Can be security_event_id or dlp_event_id
    event_type VARCHAR(20) NOT NULL, -- 'SECURITY' or 'DLP'
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (incident_id, event_id, event_type)
);

-- SIEM Integrations
CREATE TABLE siem_integrations (
    integration_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    integration_name VARCHAR(255) NOT NULL,
    source_system VARCHAR(100) NOT NULL, -- 'DataSecurityPlus'
    target_system VARCHAR(100) NOT NULL, -- 'Log360Cloud'
    protocol VARCHAR(50) NOT NULL, -- 'SYSLOG', 'SPLUNK', 'API', 'WEBHOOK'
    endpoint VARCHAR(255), -- IP:Port or URL
    port INTEGER,
    format VARCHAR(50), -- 'JSON', 'CEF', 'LEEF', 'XML'
    enabled BOOLEAN DEFAULT TRUE,
    last_successful_send TIMESTAMP,
    last_error TIMESTAMP,
    error_message TEXT,
    total_events_sent BIGINT DEFAULT 0,
    total_errors BIGINT DEFAULT 0,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_protocol CHECK (protocol IN ('SYSLOG', 'SPLUNK', 'API', 'WEBHOOK', 'HTTP', 'HTTPS')),
    UNIQUE (tenant_id, integration_name)
);

-- ============================================================================
-- AUDIT & COMPLIANCE
-- ============================================================================

-- Audit Log (tracks all administrative actions)
CREATE TABLE audit_log (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(user_id),
    action VARCHAR(100) NOT NULL, -- 'CREATE_USER', 'UPDATE_POLICY', 'DELETE_AGENT', etc.
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    changes_json TEXT, -- Before/after values in JSON
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT
);

CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp DESC);
CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id, timestamp DESC);

-- ============================================================================
-- FUNCTIONS & TRIGGERS
-- ============================================================================

-- Function to update modified timestamp
CREATE OR REPLACE FUNCTION update_modified_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.modified_date = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for users table
CREATE TRIGGER trg_users_modified
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_modified_timestamp();

-- Function to increment event count when correlating
CREATE OR REPLACE FUNCTION increment_incident_event_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE correlated_incidents 
    SET related_event_count = related_event_count + 1
    WHERE incident_id = NEW.incident_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_incident_events_count
AFTER INSERT ON incident_events
FOR EACH ROW
EXECUTE FUNCTION increment_incident_event_count();

-- ============================================================================
-- VIEWS FOR REPORTING
-- ============================================================================

-- View: Active DLP Violations
CREATE VIEW v_active_dlp_violations AS
SELECT 
    de.dlp_event_id,
    de.timestamp,
    de.username,
    de.file_name,
    de.file_path,
    de.action,
    de.policy_name,
    de.severity,
    de.action_taken,
    a.hostname,
    t.tenant_name
FROM dlp_events de
JOIN agents a ON de.agent_id = a.agent_id
JOIN tenants t ON de.tenant_id = t.tenant_id
WHERE de.severity IN ('CRITICAL', 'HIGH')
  AND de.timestamp > CURRENT_TIMESTAMP - INTERVAL '30 days'
  AND de.action_taken IN ('BLOCKED', 'QUARANTINED');

-- View: Security Event Summary
CREATE VIEW v_security_event_summary AS
SELECT 
    se.tenant_id,
    t.tenant_name,
    DATE(se.timestamp) as event_date,
    se.severity,
    COUNT(*) as event_count
FROM security_events se
JOIN tenants t ON se.tenant_id = t.tenant_id
WHERE se.timestamp > CURRENT_TIMESTAMP - INTERVAL '7 days'
GROUP BY se.tenant_id, t.tenant_name, DATE(se.timestamp), se.severity;

-- View: Agent Health Dashboard
CREATE VIEW v_agent_health AS
SELECT 
    a.tenant_id,
    t.tenant_name,
    a.agent_type,
    COUNT(*) as total_agents,
    SUM(CASE WHEN a.status = 'ONLINE' THEN 1 ELSE 0 END) as online_agents,
    SUM(CASE WHEN a.status = 'OFFLINE' THEN 1 ELSE 0 END) as offline_agents,
    SUM(CASE WHEN a.last_heartbeat < CURRENT_TIMESTAMP - INTERVAL '5 minutes' THEN 1 ELSE 0 END) as stale_agents
FROM agents a
JOIN tenants t ON a.tenant_id = t.tenant_id
GROUP BY a.tenant_id, t.tenant_name, a.agent_type;

-- View: Top Users by DLP Violations
CREATE VIEW v_top_dlp_violators AS
SELECT 
    de.tenant_id,
    de.user_id,
    de.username,
    COUNT(*) as violation_count,
    SUM(CASE WHEN de.severity = 'CRITICAL' THEN 1 ELSE 0 END) as critical_count,
    SUM(CASE WHEN de.severity = 'HIGH' THEN 1 ELSE 0 END) as high_count,
    MAX(de.timestamp) as last_violation_date
FROM dlp_events de
WHERE de.timestamp > CURRENT_TIMESTAMP - INTERVAL '30 days'
  AND de.action_taken IN ('BLOCKED', 'QUARANTINED')
GROUP BY de.tenant_id, de.user_id, de.username
ORDER BY violation_count DESC;

-- ============================================================================
-- SAMPLE DATA (for testing)
-- ============================================================================

-- Insert sample organization
INSERT INTO organizations (org_name, license_type, max_tenants, max_agents)
VALUES ('Acme Corporation', 'ENTERPRISE', 10, 10000);

-- Insert sample tenant
INSERT INTO tenants (org_id, tenant_name, domain, max_users, max_agents)
SELECT org_id, 'IT Department', 'acme.com', 500, 1000
FROM organizations WHERE org_name = 'Acme Corporation';

-- Insert sample roles
INSERT INTO roles (tenant_id, role_name, description, is_system_role)
SELECT tenant_id, 'Administrator', 'Full system access', TRUE
FROM tenants WHERE tenant_name = 'IT Department';

INSERT INTO roles (tenant_id, role_name, description, is_system_role)
SELECT tenant_id, 'Security Analyst', 'View and manage security events', TRUE
FROM tenants WHERE tenant_name = 'IT Department';

INSERT INTO roles (tenant_id, role_name, description, is_system_role)
SELECT tenant_id, 'User', 'Standard user access', TRUE
FROM tenants WHERE tenant_name = 'IT Department';

-- ============================================================================
-- PERFORMANCE OPTIMIZATION RECOMMENDATIONS
-- ============================================================================

-- 1. Partition security_events and dlp_events tables by timestamp (monthly)
-- 2. Implement table partitioning for large deployments (> 1M events/day)
-- 3. Use materialized views for dashboard queries
-- 4. Implement archival strategy for events older than retention period
-- 5. Consider TimescaleDB extension for time-series data

-- Example: Create monthly partitions (PostgreSQL 10+)
-- ALTER TABLE security_events PARTITION BY RANGE (timestamp);
-- CREATE TABLE security_events_2024_01 PARTITION OF security_events
--     FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- ============================================================================
-- END OF SCHEMA
-- ============================================================================

-- Grant permissions (adjust as needed)
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO log360_app;
-- GRANT SELECT ON ALL TABLES IN SCHEMA public TO log360_readonly;
