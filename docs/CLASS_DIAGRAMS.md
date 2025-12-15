# Class Diagrams and Object-Oriented Design

## Table of Contents
1. [Overview](#overview)
2. [Core Domain Models](#core-domain-models)
3. [Service Layer Architecture](#service-layer-architecture)
4. [Design Patterns](#design-patterns)
5. [Class Diagrams](#class-diagrams)
6. [Interface Definitions](#interface-definitions)

---

## Overview

This document outlines the object-oriented design and class structure for the SIEM solution. The design follows SOLID principles and employs common design patterns for maintainability and extensibility.

### Design Principles
- **Single Responsibility**: Each class has one reason to change
- **Open/Closed**: Open for extension, closed for modification
- **Liskov Substitution**: Subtypes must be substitutable for base types
- **Interface Segregation**: Many specific interfaces over one general
- **Dependency Inversion**: Depend on abstractions, not concretions

---

## Core Domain Models

### 1. Event Model

```typescript
// Base Event Class
abstract class BaseEvent {
  protected id: string;
  protected timestamp: Date;
  protected eventType: EventType;
  protected severity: Severity;
  protected rawData: string;
  
  constructor(id: string, timestamp: Date, type: EventType) {
    this.id = id;
    this.timestamp = timestamp;
    this.eventType = type;
  }
  
  abstract normalize(): NormalizedEvent;
  abstract validate(): boolean;
  
  getId(): string { return this.id; }
  getTimestamp(): Date { return this.timestamp; }
  getEventType(): EventType { return this.eventType; }
}

// Security Event
class SecurityEvent extends BaseEvent {
  private source: EventSource;
  private destination: EventDestination;
  private action: string;
  private outcome: EventOutcome;
  
  normalize(): NormalizedEvent {
    return new NormalizedEvent({
      id: this.id,
      timestamp: this.timestamp,
      type: this.eventType,
      source: this.source,
      destination: this.destination,
      action: this.action,
      outcome: this.outcome,
      severity: this.severity
    });
  }
  
  validate(): boolean {
    return this.id && this.timestamp && this.source && this.destination;
  }
}

// Authentication Event
class AuthenticationEvent extends SecurityEvent {
  private username: string;
  private authMethod: AuthMethod;
  private success: boolean;
  private failureReason?: string;
  
  constructor(data: AuthEventData) {
    super(data.id, data.timestamp, EventType.AUTHENTICATION);
    this.username = data.username;
    this.authMethod = data.authMethod;
    this.success = data.success;
    this.failureReason = data.failureReason;
  }
  
  isSuccessful(): boolean { return this.success; }
  getUsername(): string { return this.username; }
}

// Network Event
class NetworkEvent extends SecurityEvent {
  private protocol: NetworkProtocol;
  private bytesIn: number;
  private bytesOut: number;
  private packetsIn: number;
  private packetsOut: number;
  
  getTotalBytes(): number {
    return this.bytesIn + this.bytesOut;
  }
  
  getTrafficDirection(): TrafficDirection {
    if (this.bytesIn > this.bytesOut) return TrafficDirection.INBOUND;
    if (this.bytesOut > this.bytesIn) return TrafficDirection.OUTBOUND;
    return TrafficDirection.BIDIRECTIONAL;
  }
}

// File Event
class FileEvent extends SecurityEvent {
  private filePath: string;
  private fileHash: FileHash;
  private fileAction: FileAction;
  private fileSize: number;
  
  constructor(data: FileEventData) {
    super(data.id, data.timestamp, EventType.FILE);
    this.filePath = data.filePath;
    this.fileHash = data.fileHash;
    this.fileAction = data.fileAction;
    this.fileSize = data.fileSize;
  }
  
  getFileHash(): FileHash { return this.fileHash; }
  isExecutable(): boolean {
    const exeExtensions = ['.exe', '.dll', '.bat', '.sh', '.ps1'];
    return exeExtensions.some(ext => this.filePath.endsWith(ext));
  }
}
```

### 2. Alert Model

```typescript
// Alert Class
class Alert {
  private id: string;
  private ruleId: string;
  private name: string;
  private description: string;
  private severity: Severity;
  private confidence: number;
  private status: AlertStatus;
  private events: SecurityEvent[];
  private createdAt: Date;
  private updatedAt: Date;
  private assignedTo?: string;
  private mitreTactics: string[];
  private mitreTechniques: string[];
  
  constructor(data: AlertData) {
    this.id = data.id;
    this.ruleId = data.ruleId;
    this.name = data.name;
    this.severity = data.severity;
    this.confidence = data.confidence;
    this.status = AlertStatus.NEW;
    this.events = [];
    this.createdAt = new Date();
    this.updatedAt = new Date();
  }
  
  addEvent(event: SecurityEvent): void {
    this.events.push(event);
    this.updatedAt = new Date();
  }
  
  assignTo(userId: string): void {
    this.assignedTo = userId;
    this.status = AlertStatus.INVESTIGATING;
    this.updatedAt = new Date();
  }
  
  markAsFalsePositive(): void {
    this.status = AlertStatus.FALSE_POSITIVE;
    this.updatedAt = new Date();
  }
  
  resolve(resolution: string): void {
    this.status = AlertStatus.RESOLVED;
    this.updatedAt = new Date();
  }
  
  escalateToCase(): Case {
    return new Case({
      alerts: [this],
      severity: this.severity,
      title: this.name
    });
  }
  
  getRiskScore(): number {
    const severityWeight = this.getSeverityWeight();
    const confidenceWeight = this.confidence;
    const eventCountWeight = Math.min(this.events.length / 10, 1);
    
    return severityWeight * 0.5 + confidenceWeight * 0.3 + eventCountWeight * 0.2;
  }
  
  private getSeverityWeight(): number {
    const weights = {
      [Severity.LOW]: 0.25,
      [Severity.MEDIUM]: 0.5,
      [Severity.HIGH]: 0.75,
      [Severity.CRITICAL]: 1.0
    };
    return weights[this.severity];
  }
}

// Alert Rule
class DetectionRule {
  private id: string;
  private name: string;
  private description: string;
  private ruleType: RuleType;
  private severity: Severity;
  private enabled: boolean;
  private query: string | object;
  private conditions: RuleCondition[];
  private actions: RuleAction[];
  
  constructor(data: RuleData) {
    this.id = data.id;
    this.name = data.name;
    this.ruleType = data.ruleType;
    this.severity = data.severity;
    this.enabled = true;
    this.conditions = data.conditions;
    this.actions = data.actions;
  }
  
  evaluate(events: SecurityEvent[]): boolean {
    return this.conditions.every(condition => 
      condition.evaluate(events)
    );
  }
  
  execute(events: SecurityEvent[]): Alert[] {
    if (!this.enabled) return [];
    
    if (this.evaluate(events)) {
      const alert = new Alert({
        id: generateId(),
        ruleId: this.id,
        name: this.name,
        severity: this.severity,
        confidence: this.calculateConfidence(events)
      });
      
      events.forEach(event => alert.addEvent(event));
      
      this.actions.forEach(action => action.execute(alert));
      
      return [alert];
    }
    
    return [];
  }
  
  private calculateConfidence(events: SecurityEvent[]): number {
    // Implement confidence scoring logic
    return 0.95;
  }
}
```

### 3. Case Model

```typescript
// Incident Case
class Case {
  private id: string;
  private caseNumber: string;
  private title: string;
  private description: string;
  private severity: Severity;
  private priority: Priority;
  private status: CaseStatus;
  private alerts: Alert[];
  private timeline: CaseTimeline;
  private assignedTo?: string;
  private createdBy: string;
  private createdAt: Date;
  private updatedAt: Date;
  private closedAt?: Date;
  
  constructor(data: CaseData) {
    this.id = generateId();
    this.caseNumber = this.generateCaseNumber();
    this.title = data.title;
    this.severity = data.severity;
    this.status = CaseStatus.OPEN;
    this.alerts = data.alerts || [];
    this.timeline = new CaseTimeline();
    this.createdAt = new Date();
    this.updatedAt = new Date();
  }
  
  addAlert(alert: Alert): void {
    this.alerts.push(alert);
    this.timeline.addEntry({
      action: 'alert_added',
      alertId: alert.getId(),
      timestamp: new Date()
    });
    this.updatedAt = new Date();
  }
  
  assignInvestigator(userId: string): void {
    this.assignedTo = userId;
    this.status = CaseStatus.INVESTIGATING;
    this.timeline.addEntry({
      action: 'assigned',
      userId: userId,
      timestamp: new Date()
    });
  }
  
  escalate(): void {
    this.priority = Priority.CRITICAL;
    this.timeline.addEntry({
      action: 'escalated',
      timestamp: new Date()
    });
  }
  
  resolve(resolution: CaseResolution): void {
    this.status = CaseStatus.RESOLVED;
    this.closedAt = new Date();
    this.timeline.addEntry({
      action: 'resolved',
      resolution: resolution,
      timestamp: new Date()
    });
  }
  
  private generateCaseNumber(): string {
    const year = new Date().getFullYear();
    const random = Math.floor(Math.random() * 10000).toString().padStart(4, '0');
    return `CASE-${year}-${random}`;
  }
  
  getTimeToResolve(): number | null {
    if (!this.closedAt) return null;
    return this.closedAt.getTime() - this.createdAt.getTime();
  }
}

// Case Timeline
class CaseTimeline {
  private entries: TimelineEntry[];
  
  constructor() {
    this.entries = [];
  }
  
  addEntry(entry: TimelineEntry): void {
    this.entries.push(entry);
    this.entries.sort((a, b) => 
      a.timestamp.getTime() - b.timestamp.getTime()
    );
  }
  
  getEntries(): TimelineEntry[] {
    return [...this.entries];
  }
  
  getEntriesByAction(action: string): TimelineEntry[] {
    return this.entries.filter(entry => entry.action === action);
  }
}
```

### 4. User and Permission Models

```typescript
// User Class
class User {
  private id: string;
  private username: string;
  private email: string;
  private passwordHash: string;
  private firstName: string;
  private lastName: string;
  private roles: Role[];
  private isActive: boolean;
  private mfaEnabled: boolean;
  private lastLogin?: Date;
  
  constructor(data: UserData) {
    this.id = data.id;
    this.username = data.username;
    this.email = data.email;
    this.firstName = data.firstName;
    this.lastName = data.lastName;
    this.roles = [];
    this.isActive = true;
    this.mfaEnabled = false;
  }
  
  hasPermission(permission: string): boolean {
    return this.roles.some(role => 
      role.hasPermission(permission)
    );
  }
  
  assignRole(role: Role): void {
    if (!this.roles.includes(role)) {
      this.roles.push(role);
    }
  }
  
  revokeRole(role: Role): void {
    this.roles = this.roles.filter(r => r !== role);
  }
  
  getFullName(): string {
    return `${this.firstName} ${this.lastName}`;
  }
  
  enableMFA(secret: string): void {
    this.mfaEnabled = true;
  }
}

// Role Class
class Role {
  private id: string;
  private name: string;
  private description: string;
  private permissions: Permission[];
  
  constructor(name: string, description: string) {
    this.id = generateId();
    this.name = name;
    this.description = description;
    this.permissions = [];
  }
  
  addPermission(permission: Permission): void {
    if (!this.permissions.includes(permission)) {
      this.permissions.push(permission);
    }
  }
  
  hasPermission(permissionName: string): boolean {
    return this.permissions.some(p => 
      p.getName() === permissionName
    );
  }
  
  getPermissions(): Permission[] {
    return [...this.permissions];
  }
}

// Permission Class
class Permission {
  private id: string;
  private name: string;
  private resource: string;
  private action: string;
  
  constructor(name: string, resource: string, action: string) {
    this.id = generateId();
    this.name = name;
    this.resource = resource;
    this.action = action;
  }
  
  getName(): string { return this.name; }
  getResource(): string { return this.resource; }
  getAction(): string { return this.action; }
  
  matches(resource: string, action: string): boolean {
    return this.resource === resource && this.action === action;
  }
}
```

---

## Service Layer Architecture

### 1. Collection Services

```typescript
// Agent Interface
interface IAgent {
  start(): Promise<void>;
  stop(): Promise<void>;
  collect(): Promise<SecurityEvent[]>;
  getHealth(): AgentHealth;
  configure(config: AgentConfig): void;
}

// Base Agent Class
abstract class BaseAgent implements IAgent {
  protected id: string;
  protected config: AgentConfig;
  protected isRunning: boolean;
  protected lastHeartbeat: Date;
  
  constructor(id: string, config: AgentConfig) {
    this.id = id;
    this.config = config;
    this.isRunning = false;
  }
  
  async start(): Promise<void> {
    this.isRunning = true;
    await this.initialize();
    this.startHeartbeat();
  }
  
  async stop(): Promise<void> {
    this.isRunning = false;
    await this.cleanup();
  }
  
  getHealth(): AgentHealth {
    return {
      agentId: this.id,
      status: this.isRunning ? 'healthy' : 'stopped',
      lastHeartbeat: this.lastHeartbeat,
      uptime: this.calculateUptime()
    };
  }
  
  configure(config: AgentConfig): void {
    this.config = { ...this.config, ...config };
  }
  
  protected abstract initialize(): Promise<void>;
  protected abstract cleanup(): Promise<void>;
  abstract collect(): Promise<SecurityEvent[]>;
  
  private startHeartbeat(): void {
    setInterval(() => {
      this.lastHeartbeat = new Date();
    }, 30000); // Every 30 seconds
  }
  
  private calculateUptime(): number {
    // Implementation
    return 0;
  }
}

// Log Agent
class LogAgent extends BaseAgent {
  private logSources: LogSource[];
  
  protected async initialize(): Promise<void> {
    // Initialize log sources
    this.logSources = this.config.sources.map(s => 
      LogSourceFactory.create(s)
    );
  }
  
  protected async cleanup(): Promise<void> {
    // Cleanup resources
    await Promise.all(this.logSources.map(s => s.close()));
  }
  
  async collect(): Promise<SecurityEvent[]> {
    const events: SecurityEvent[] = [];
    
    for (const source of this.logSources) {
      const logs = await source.read();
      const parsedEvents = logs.map(log => this.parseLog(log));
      events.push(...parsedEvents);
    }
    
    return events;
  }
  
  private parseLog(log: string): SecurityEvent {
    // Parse log to SecurityEvent
    return new SecurityEvent(/* ... */);
  }
}

// Network Agent
class NetworkAgent extends BaseAgent {
  private packetCapture: PacketCapture;
  
  protected async initialize(): Promise<void> {
    this.packetCapture = new PacketCapture(this.config.interface);
    await this.packetCapture.start();
  }
  
  protected async cleanup(): Promise<void> {
    await this.packetCapture.stop();
  }
  
  async collect(): Promise<SecurityEvent[]> {
    const packets = await this.packetCapture.getPackets();
    return packets.map(packet => this.analyzePacket(packet));
  }
  
  private analyzePacket(packet: Packet): NetworkEvent {
    // Deep packet inspection
    return new NetworkEvent(/* ... */);
  }
}
```

### 2. Processing Services

```typescript
// Event Processor Interface
interface IEventProcessor {
  process(event: SecurityEvent): Promise<ProcessedEvent>;
  canProcess(event: SecurityEvent): boolean;
}

// Normalizer
class EventNormalizer implements IEventProcessor {
  private schemas: Map<string, Schema>;
  
  canProcess(event: SecurityEvent): boolean {
    return this.schemas.has(event.getEventType().toString());
  }
  
  async process(event: SecurityEvent): Promise<ProcessedEvent> {
    const schema = this.schemas.get(event.getEventType().toString());
    const normalized = event.normalize();
    
    return new ProcessedEvent({
      original: event,
      normalized: normalized,
      schema: schema
    });
  }
}

// Enricher
class EventEnricher implements IEventProcessor {
  private geoIpService: GeoIPService;
  private threatIntelService: ThreatIntelService;
  private assetService: AssetService;
  
  canProcess(event: SecurityEvent): boolean {
    return true; // Can enrich any event
  }
  
  async process(event: SecurityEvent): Promise<ProcessedEvent> {
    const enrichments: Enrichment[] = [];
    
    // GeoIP enrichment
    if (event.getSource()?.ip) {
      const geoData = await this.geoIpService.lookup(event.getSource().ip);
      enrichments.push(new GeoIPEnrichment(geoData));
    }
    
    // Threat intelligence
    const threatData = await this.threatIntelService.check(event);
    if (threatData.isThreat) {
      enrichments.push(new ThreatEnrichment(threatData));
    }
    
    // Asset context
    const asset = await this.assetService.findByEvent(event);
    if (asset) {
      enrichments.push(new AssetEnrichment(asset));
    }
    
    return new ProcessedEvent({
      original: event,
      enrichments: enrichments
    });
  }
}

// Correlation Engine
class CorrelationEngine {
  private rules: DetectionRule[];
  private eventWindow: EventWindow;
  
  constructor() {
    this.rules = [];
    this.eventWindow = new EventWindow(300000); // 5 minutes
  }
  
  addRule(rule: DetectionRule): void {
    this.rules.push(rule);
  }
  
  correlate(event: SecurityEvent): Alert[] {
    this.eventWindow.add(event);
    const alerts: Alert[] = [];
    
    for (const rule of this.rules) {
      const relatedEvents = this.eventWindow.getRelatedEvents(event);
      const ruleAlerts = rule.execute([event, ...relatedEvents]);
      alerts.push(...ruleAlerts);
    }
    
    return alerts;
  }
  
  cleanup(): void {
    this.eventWindow.removeExpired();
  }
}

// ML Detection Service
class MLDetectionService {
  private models: Map<string, MLModel>;
  private featureExtractor: FeatureExtractor;
  
  async detectAnomalies(events: SecurityEvent[]): Promise<Anomaly[]> {
    const anomalies: Anomaly[] = [];
    
    for (const event of events) {
      const features = this.featureExtractor.extract(event);
      
      for (const [modelName, model] of this.models) {
        const prediction = await model.predict(features);
        
        if (prediction.isAnomaly) {
          anomalies.push(new Anomaly({
            event: event,
            model: modelName,
            score: prediction.score,
            confidence: prediction.confidence
          }));
        }
      }
    }
    
    return anomalies;
  }
  
  async trainModel(modelName: string, data: TrainingData): Promise<void> {
    const model = this.models.get(modelName);
    if (model) {
      await model.train(data);
    }
  }
}
```

### 3. Storage Services

```typescript
// Repository Pattern
interface IRepository<T> {
  findById(id: string): Promise<T | null>;
  findAll(filter?: FilterCriteria): Promise<T[]>;
  save(entity: T): Promise<T>;
  update(id: string, entity: T): Promise<T>;
  delete(id: string): Promise<boolean>;
}

// Event Repository
class EventRepository implements IRepository<SecurityEvent> {
  private elasticsearchClient: ElasticsearchClient;
  
  async findById(id: string): Promise<SecurityEvent | null> {
    const result = await this.elasticsearchClient.get({
      index: 'siem-events-*',
      id: id
    });
    
    return result ? this.mapToEvent(result) : null;
  }
  
  async findAll(filter: FilterCriteria): Promise<SecurityEvent[]> {
    const query = this.buildQuery(filter);
    const results = await this.elasticsearchClient.search({
      index: 'siem-events-*',
      body: query
    });
    
    return results.hits.hits.map(hit => this.mapToEvent(hit));
  }
  
  async save(event: SecurityEvent): Promise<SecurityEvent> {
    await this.elasticsearchClient.index({
      index: `siem-events-${this.getIndexSuffix()}`,
      id: event.getId(),
      body: event.toJSON()
    });
    
    return event;
  }
  
  async update(id: string, event: SecurityEvent): Promise<SecurityEvent> {
    await this.elasticsearchClient.update({
      index: 'siem-events-*',
      id: id,
      body: {
        doc: event.toJSON()
      }
    });
    
    return event;
  }
  
  async delete(id: string): Promise<boolean> {
    const result = await this.elasticsearchClient.delete({
      index: 'siem-events-*',
      id: id
    });
    
    return result.result === 'deleted';
  }
  
  private buildQuery(filter: FilterCriteria): object {
    // Build Elasticsearch query
    return {};
  }
  
  private mapToEvent(doc: any): SecurityEvent {
    // Map document to SecurityEvent
    return new SecurityEvent(/* ... */);
  }
  
  private getIndexSuffix(): string {
    return new Date().toISOString().split('T')[0].replace(/-/g, '.');
  }
}

// Alert Repository
class AlertRepository implements IRepository<Alert> {
  private postgresClient: PostgresClient;
  private cacheClient: RedisClient;
  
  async findById(id: string): Promise<Alert | null> {
    // Check cache first
    const cached = await this.cacheClient.get(`alert:${id}`);
    if (cached) {
      return JSON.parse(cached);
    }
    
    // Query database
    const result = await this.postgresClient.query(
      'SELECT * FROM alerts WHERE alert_id = $1',
      [id]
    );
    
    if (result.rows.length === 0) return null;
    
    const alert = this.mapToAlert(result.rows[0]);
    
    // Cache for 5 minutes
    await this.cacheClient.setex(`alert:${id}`, 300, JSON.stringify(alert));
    
    return alert;
  }
  
  async save(alert: Alert): Promise<Alert> {
    const query = `
      INSERT INTO alerts (
        alert_id, rule_id, alert_name, severity, 
        status, confidence, created_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7)
      RETURNING *
    `;
    
    const values = [
      alert.getId(),
      alert.getRuleId(),
      alert.getName(),
      alert.getSeverity(),
      alert.getStatus(),
      alert.getConfidence(),
      alert.getCreatedAt()
    ];
    
    await this.postgresClient.query(query, values);
    
    // Invalidate cache
    await this.cacheClient.del(`alert:${alert.getId()}`);
    
    return alert;
  }
  
  private mapToAlert(row: any): Alert {
    // Map database row to Alert
    return new Alert(/* ... */);
  }
}
```

---

## Design Patterns

### 1. Factory Pattern

```typescript
// Event Factory
class EventFactory {
  static create(eventType: EventType, data: any): SecurityEvent {
    switch (eventType) {
      case EventType.AUTHENTICATION:
        return new AuthenticationEvent(data);
      case EventType.NETWORK:
        return new NetworkEvent(data);
      case EventType.FILE:
        return new FileEvent(data);
      case EventType.PROCESS:
        return new ProcessEvent(data);
      default:
        return new GenericSecurityEvent(data);
    }
  }
}

// Agent Factory
class AgentFactory {
  static create(agentType: AgentType, config: AgentConfig): IAgent {
    switch (agentType) {
      case AgentType.LOG:
        return new LogAgent(generateId(), config);
      case AgentType.NETWORK:
        return new NetworkAgent(generateId(), config);
      case AgentType.FILE:
        return new FileAgent(generateId(), config);
      default:
        throw new Error(`Unknown agent type: ${agentType}`);
    }
  }
}
```

### 2. Observer Pattern

```typescript
// Event Emitter for Alert Notifications
class AlertNotifier {
  private observers: IAlertObserver[];
  
  constructor() {
    this.observers = [];
  }
  
  subscribe(observer: IAlertObserver): void {
    this.observers.push(observer);
  }
  
  unsubscribe(observer: IAlertObserver): void {
    this.observers = this.observers.filter(o => o !== observer);
  }
  
  notify(alert: Alert): void {
    this.observers.forEach(observer => observer.onAlert(alert));
  }
}

// Alert Observer Interface
interface IAlertObserver {
  onAlert(alert: Alert): void;
}

// Email Notifier
class EmailNotifier implements IAlertObserver {
  private emailService: EmailService;
  
  onAlert(alert: Alert): void {
    if (alert.getSeverity() >= Severity.HIGH) {
      this.emailService.send({
        to: this.getRecipients(alert),
        subject: `[SIEM Alert] ${alert.getName()}`,
        body: this.formatAlertEmail(alert)
      });
    }
  }
  
  private getRecipients(alert: Alert): string[] {
    // Get email recipients based on alert
    return [];
  }
  
  private formatAlertEmail(alert: Alert): string {
    // Format alert as email
    return '';
  }
}

// Slack Notifier
class SlackNotifier implements IAlertObserver {
  private slackClient: SlackClient;
  
  onAlert(alert: Alert): void {
    this.slackClient.postMessage({
      channel: '#security-alerts',
      text: this.formatAlertMessage(alert)
    });
  }
  
  private formatAlertMessage(alert: Alert): string {
    return `🚨 *${alert.getName()}*\nSeverity: ${alert.getSeverity()}\nConfidence: ${alert.getConfidence()}`;
  }
}
```

### 3. Strategy Pattern

```typescript
// Query Strategy Interface
interface IQueryStrategy {
  buildQuery(criteria: SearchCriteria): Query;
  execute(query: Query): Promise<SearchResult[]>;
}

// Elasticsearch Query Strategy
class ElasticsearchQueryStrategy implements IQueryStrategy {
  private client: ElasticsearchClient;
  
  buildQuery(criteria: SearchCriteria): Query {
    return {
      index: 'siem-events-*',
      body: {
        query: {
          bool: {
            must: this.buildMustClauses(criteria),
            filter: this.buildFilterClauses(criteria)
          }
        },
        size: criteria.limit,
        from: criteria.offset
      }
    };
  }
  
  async execute(query: Query): Promise<SearchResult[]> {
    const response = await this.client.search(query);
    return response.hits.hits.map(hit => ({
      id: hit._id,
      source: hit._source,
      score: hit._score
    }));
  }
  
  private buildMustClauses(criteria: SearchCriteria): any[] {
    // Build must clauses
    return [];
  }
  
  private buildFilterClauses(criteria: SearchCriteria): any[] {
    // Build filter clauses
    return [];
  }
}

// ClickHouse Query Strategy
class ClickHouseQueryStrategy implements IQueryStrategy {
  private client: ClickHouseClient;
  
  buildQuery(criteria: SearchCriteria): Query {
    let sql = 'SELECT * FROM events WHERE 1=1';
    
    if (criteria.timeRange) {
      sql += ` AND timestamp BETWEEN '${criteria.timeRange.start}' AND '${criteria.timeRange.end}'`;
    }
    
    if (criteria.filters) {
      criteria.filters.forEach(filter => {
        sql += ` AND ${filter.field} ${filter.operator} '${filter.value}'`;
      });
    }
    
    sql += ` LIMIT ${criteria.limit} OFFSET ${criteria.offset}`;
    
    return { sql };
  }
  
  async execute(query: Query): Promise<SearchResult[]> {
    const response = await this.client.query(query.sql);
    return response.data.map(row => ({
      id: row.event_id,
      source: row,
      score: 1.0
    }));
  }
}

// Search Service using Strategy
class SearchService {
  private strategy: IQueryStrategy;
  
  setStrategy(strategy: IQueryStrategy): void {
    this.strategy = strategy;
  }
  
  async search(criteria: SearchCriteria): Promise<SearchResult[]> {
    const query = this.strategy.buildQuery(criteria);
    return await this.strategy.execute(query);
  }
}
```

### 4. Chain of Responsibility

```typescript
// Event Processing Chain
abstract class EventProcessorChain {
  protected next: EventProcessorChain | null = null;
  
  setNext(processor: EventProcessorChain): EventProcessorChain {
    this.next = processor;
    return processor;
  }
  
  async handle(event: SecurityEvent): Promise<SecurityEvent> {
    const processed = await this.process(event);
    
    if (this.next) {
      return await this.next.handle(processed);
    }
    
    return processed;
  }
  
  protected abstract process(event: SecurityEvent): Promise<SecurityEvent>;
}

// Validation Processor
class ValidationProcessor extends EventProcessorChain {
  protected async process(event: SecurityEvent): Promise<SecurityEvent> {
    if (!event.validate()) {
      throw new Error(`Invalid event: ${event.getId()}`);
    }
    return event;
  }
}

// Normalization Processor
class NormalizationProcessor extends EventProcessorChain {
  protected async process(event: SecurityEvent): Promise<SecurityEvent> {
    const normalized = event.normalize();
    return normalized as SecurityEvent;
  }
}

// Enrichment Processor
class EnrichmentProcessor extends EventProcessorChain {
  private enricher: EventEnricher;
  
  protected async process(event: SecurityEvent): Promise<SecurityEvent> {
    const processed = await this.enricher.process(event);
    return processed.original;
  }
}

// Usage
const pipeline = new ValidationProcessor();
pipeline
  .setNext(new NormalizationProcessor())
  .setNext(new EnrichmentProcessor());

const processedEvent = await pipeline.handle(rawEvent);
```

---

## Class Diagrams

### High-Level Class Diagram

```
┌──────────────────┐
│   BaseEvent      │
│ <<abstract>>     │
└────────┬─────────┘
         │
         ├─────────────┬─────────────┬──────────────┐
         │             │             │              │
┌────────┴────────┐ ┌─┴──────────┐ ┌┴─────────────┐ ┌┴────────────┐
│SecurityEvent    │ │NetworkEvent│ │AuthEvent     │ │FileEvent    │
└─────────────────┘ └────────────┘ └──────────────┘ └─────────────┘


┌──────────────────┐      ┌──────────────────┐
│   Alert          │      │ DetectionRule    │
│                  │◄─────│                  │
└────────┬─────────┘      └──────────────────┘
         │
         │ *
         │
┌────────┴─────────┐
│   Case           │
│                  │
└──────────────────┘


┌──────────────────┐      ┌──────────────────┐
│   User           │      │   Role           │
│                  │◄────►│                  │
└──────────────────┘      └────────┬─────────┘
                                   │
                                   │ *
                                   │
                          ┌────────┴─────────┐
                          │  Permission      │
                          │                  │
                          └──────────────────┘


┌──────────────────┐
│   IAgent         │
│  <<interface>>   │
└────────┬─────────┘
         │
         ├─────────────┬─────────────┬──────────────┐
         │             │             │              │
┌────────┴────────┐ ┌─┴──────────┐ ┌┴─────────────┐ 
│  LogAgent       │ │NetworkAgent│ │FileAgent     │
└─────────────────┘ └────────────┘ └──────────────┘
```

---

## Interface Definitions

```typescript
// Core Interfaces
interface EventSource {
  ip: string;
  port?: number;
  hostname?: string;
  user?: UserInfo;
  geo?: GeoLocation;
}

interface EventDestination {
  ip: string;
  port?: number;
  hostname?: string;
}

interface UserInfo {
  name: string;
  id: string;
  domain?: string;
}

interface GeoLocation {
  country: string;
  city: string;
  latitude: number;
  longitude: number;
}

// Enums
enum EventType {
  AUTHENTICATION = 'authentication',
  NETWORK = 'network',
  FILE = 'file',
  PROCESS = 'process',
  REGISTRY = 'registry',
  DNS = 'dns',
  HTTP = 'http'
}

enum Severity {
  LOW = 'low',
  MEDIUM = 'medium',
  HIGH = 'high',
  CRITICAL = 'critical'
}

enum AlertStatus {
  NEW = 'new',
  INVESTIGATING = 'investigating',
  CONFIRMED = 'confirmed',
  FALSE_POSITIVE = 'false_positive',
  RESOLVED = 'resolved'
}

enum CaseStatus {
  OPEN = 'open',
  INVESTIGATING = 'investigating',
  CONTAINED = 'contained',
  RESOLVED = 'resolved',
  CLOSED = 'closed'
}
```

---

## Conclusion

This object-oriented design provides:
- **Modularity**: Clear separation of concerns
- **Extensibility**: Easy to add new event types, agents, and processors
- **Maintainability**: SOLID principles and design patterns
- **Testability**: Interface-based design for easy mocking
- **Scalability**: Repository pattern for data access abstraction

The class structure supports the SIEM architecture while maintaining clean code principles and best practices.
