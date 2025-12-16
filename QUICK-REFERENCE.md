# Quick Reference Guide - Log360Cloud & DataSecurity Plus Integration

## 🚀 5-Minute Overview

### What is This Integration?
Combines **Log360Cloud** (cloud SIEM) with **DataSecurity Plus** (DLP) to provide:
- Unified security monitoring
- Data loss prevention
- Advanced threat detection through event correlation

### Key Benefits
✅ Single dashboard for security + DLP  
✅ Detect insider threats and data exfiltration  
✅ Automated compliance reporting (GDPR, HIPAA, PCI-DSS)  
✅ Cloud-based, infinitely scalable  

---

## 📋 Installation Checklist

### Prerequisites
- [ ] Log360Cloud license activated
- [ ] DataSecurity Plus license activated
- [ ] Network ports opened (443, 514, 8443)
- [ ] Admin credentials ready

### Installation Steps
1. [ ] Install DataSecurity Plus Server
2. [ ] Deploy DLP agents to critical endpoints
3. [ ] Deploy Log360Cloud agents to all endpoints
4. [ ] Configure SIEM integration (DSP → Log360)
5. [ ] Create DLP policies
6. [ ] Set up correlation rules
7. [ ] Test end-to-end flow

**Time Estimate**: 4-6 hours for pilot (50-100 endpoints)

---

## 🔧 Configuration Quick Starts

### DLP Policy Example (PII Protection)
```yaml
Policy Name: SSN Detection
Pattern: \b\d{3}-\d{2}-\d{4}\b
Action: BLOCK + ALERT
Devices: USB, Email, Cloud
Priority: HIGH
```

### Syslog Integration
```yaml
Source: DataSecurity Plus Server
Target: Log360 Agent IP
Port: 514 (UDP) or 6514 (TCP)
Format: JSON
Standard: RFC 5424
```

### Correlation Rule (Insider Threat)
```yaml
Trigger: 3+ DLP violations + 10+ file accesses + After-hours login
Time Window: 1 hour
Action: Create HIGH priority alert
Notify: SOC team
```

---

## 📊 Key Metrics to Monitor

### Agent Health
- **Online Agents**: > 95%
- **Heartbeat Frequency**: < 5 minutes
- **Buffer Usage**: < 80%

### DLP Events
- **Critical Violations**: 0 (target)
- **False Positives**: < 5%
- **Policy Coverage**: 100% of critical data

### SIEM Performance
- **Event Processing Time**: < 2 minutes
- **Correlation Accuracy**: > 90%
- **Alert Response Time**: < 15 minutes

---

## 🔍 Common Queries

### Top DLP Violators (SQL)
```sql
SELECT username, COUNT(*) as violations
FROM dlp_events
WHERE severity IN ('HIGH', 'CRITICAL')
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY username
ORDER BY violations DESC
LIMIT 10;
```

### Log360Cloud Search (Lucene)
```
source:"DataSecurityPlus" AND severity:HIGH AND action:BLOCKED
```

---

## 🚨 Troubleshooting Quick Fixes

### Issue: DLP events not in Log360Cloud
**Fix**:
1. Check Syslog config in DSP
2. Test network: `telnet <log360-ip> 514`
3. Verify Log360 custom parser

### Issue: Agent shows offline
**Fix**:
1. Restart service: `net start DSPAgent`
2. Check firewall rules
3. Verify server IP in agent config

### Issue: High false positives
**Fix**:
1. Tune regex patterns
2. Add file type exclusions
3. Whitelist known-good users/paths

---

## 📞 Emergency Contacts

| Issue Type | Contact | Response Time |
|------------|---------|---------------|
| Critical Security Alert | SOC Team | Immediate |
| SIEM Integration Down | IT Operations | 1 hour |
| DLP Policy Questions | Security Team | 4 hours |
| License Issues | ManageEngine Support | 24 hours |

---

## 🎯 Success Criteria

### Week 1 (Pilot)
- [ ] 50+ endpoints with both agents
- [ ] SIEM integration working
- [ ] 1+ DLP policy deployed
- [ ] 0 critical issues

### Month 1 (Production)
- [ ] All endpoints covered
- [ ] 5+ DLP policies active
- [ ] 3+ correlation rules
- [ ] < 5% false positive rate

### Month 3 (Optimization)
- [ ] Compliance reports automated
- [ ] Alert fatigue eliminated
- [ ] Insider threat detection proven
- [ ] ROI documented

---

## 📚 Documentation Index

| Document | Purpose | Audience |
|----------|---------|----------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System design, DB schema | Architects, Developers |
| [IMPLEMENTATION-GUIDE.md](IMPLEMENTATION-GUIDE.md) | Step-by-step setup | IT Admins |
| [database-schema.sql](database-schema.sql) | Database DDL | DBAs |
| [class-diagram.puml](class-diagram.puml) | Object model | Developers |
| [sequence-diagram-*.puml](sequence-diagram-dlp-siem.puml) | Workflows | Architects |
| This file | Quick reference | Everyone |

---

## 🔑 Key Takeaways

1. **Dual Agents**: Log360 + DLP agents work independently, no conflicts
2. **Syslog is Key**: All integration happens via Syslog forwarding
3. **Correlation = Value**: The real power is correlating DLP + security events
4. **Start Small**: Pilot with 50-100 endpoints before full rollout
5. **Tune Continuously**: Adjust policies based on real-world feedback

---

**Pro Tip**: Bookmark this page for quick reference during implementation!
