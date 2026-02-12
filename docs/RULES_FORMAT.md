# Detection Rules Format

This document describes the format and structure of detection rules used by the File Check scanner.

## Table of Contents
- [Rule Bundle Structure](#rule-bundle-structure)
- [Rule Properties](#rule-properties)
- [Pattern Writing Guidelines](#pattern-writing-guidelines)
- [Validator Types](#validator-types)
- [Confidence Scoring](#confidence-scoring)
- [Testing Rules](#testing-rules)
- [Rule Bundle Versioning](#rule-bundle-versioning)
- [Best Practices](#best-practices)

## Rule Bundle Structure

Rules are organized in JSON bundles with the following structure:

```json
{
  "version": "1.0",
  "signatureTimestamp": "2024-01-15T10:30:00Z",
  "rules": [
    {
      "id": "SSN_FULL",
      "name": "Social Security Number (Full)",
      "category": "PII",
      "severity": "HIGH",
      "pattern": "\\b\\d{3}-\\d{2}-\\d{4}\\b",
      "validator": "ssn",
      "confidence": 0.9,
      "contextRequired": false
    }
  ]
}
```

### Top-Level Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `version` | String | Yes | Version identifier for the rule bundle (e.g., "1.0") |
| `signatureTimestamp` | ISO 8601 Timestamp | No | Optional timestamp for rule bundle signing/verification |
| `rules` | Array | Yes | Array of detection rules |

## Rule Properties

Each rule object in the `rules` array contains the following properties:

### Core Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `id` | String | Yes | Unique identifier for the rule (e.g., "SSN_FULL", "CCN_VISA") |
| `name` | String | Yes | Human-readable name for the rule |
| `category` | String | Yes | Classification category (e.g., "PII", "PCI", "PHI") |
| `severity` | String | Yes | Alert severity level (see below) |
| `pattern` | String | Yes | Regular expression pattern using RE2J syntax |
| `validator` | String | No | Validation algorithm to apply (see [Validator Types](#validator-types)) |
| `confidence` | Float | Yes | Confidence score between 0.0 and 1.0 |
| `contextRequired` | Boolean | No | If true, requires context validation to reduce false positives (default: false) |

### Severity Levels

The scanner supports four severity levels:

| Severity | Use Case | Example |
|----------|----------|---------|
| `LOW` | Low-risk information | Phone numbers, zip codes |
| `MEDIUM` | Moderate-risk information | Email addresses, dates of birth |
| `HIGH` | High-risk information | Social Security Numbers, passport numbers |
| `CRITICAL` | Critical information requiring immediate attention | Credit card numbers, API keys, passwords |

### Example Rules

#### High-Severity Rule with Validation
```json
{
  "id": "SSN_FULL",
  "name": "Social Security Number (Full)",
  "category": "PII",
  "severity": "HIGH",
  "pattern": "\\b\\d{3}-\\d{2}-\\d{4}\\b",
  "validator": "ssn",
  "confidence": 0.9
}
```

#### Critical-Severity Rule with Luhn Validation
```json
{
  "id": "CCN_VISA",
  "name": "Credit Card Number (Visa)",
  "category": "PCI",
  "severity": "CRITICAL",
  "pattern": "\\b4\\d{15}\\b",
  "validator": "luhn",
  "confidence": 0.95
}
```

#### Context-Required Rule
```json
{
  "id": "SSN_COMPACT",
  "name": "Social Security Number (Compact)",
  "category": "PII",
  "severity": "HIGH",
  "pattern": "\\b\\d{9}\\b",
  "validator": "ssn",
  "confidence": 0.7,
  "contextRequired": true
}
```

## Pattern Writing Guidelines

Patterns use the **RE2J** regular expression syntax, which is a safe, deterministic subset of PCRE.

### RE2J Syntax Basics

- **Word boundaries**: Use `\\b` to match word boundaries
- **Digit classes**: `\\d` matches any digit (0-9)
- **Quantifiers**: `{n}` (exactly n), `{n,m}` (between n and m), `+` (one or more), `*` (zero or more)
- **Character classes**: `[A-Za-z]`, `[0-9]`, etc.
- **Groups**: `(...)` for capturing, `(?:...)` for non-capturing
- **Alternation**: `|` for OR conditions

### Pattern Best Practices

1. **Always use word boundaries** (`\\b`) to avoid partial matches:
   ```json
   "pattern": "\\b\\d{3}-\\d{2}-\\d{4}\\b"
   ```

2. **Be specific but flexible** for separators:
   ```json
   "pattern": "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"
   ```

3. **Escape backslashes** in JSON (use `\\` instead of `\`):
   ```json
   "pattern": "\\b4\\d{15}\\b"
   ```

4. **Use non-capturing groups** when grouping is needed but capture isn't:
   ```json
   "pattern": "\\b(?:visa|mastercard|amex):\\s*\\d{16}\\b"
   ```

5. **Optimize for performance** - avoid excessive backtracking:
   - Good: `\\b\\d{3}-\\d{2}-\\d{4}\\b`
   - Bad: `.*\\d+.*-.*\\d+.*-.*\\d+.*`

### Pattern Examples by Data Type

#### Social Security Numbers
```json
// Full format with dashes
"pattern": "\\b\\d{3}-\\d{2}-\\d{4}\\b"

// Compact format (9 digits)
"pattern": "\\b\\d{9}\\b"
```

#### Credit Cards
```json
// Visa (starts with 4, 16 digits)
"pattern": "\\b4\\d{15}\\b"

// MasterCard (starts with 51-55, 16 digits)
"pattern": "\\b5[1-5]\\d{14}\\b"

// American Express (starts with 34/37, 15 digits)
"pattern": "\\b3[47]\\d{13}\\b"

// Discover (starts with 6011 or 65, 16 digits)
"pattern": "\\b6(?:011|5\\d{2})\\d{12}\\b"
```

#### Email Addresses
```json
"pattern": "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
```

#### Phone Numbers
```json
// US format with optional separators
"pattern": "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"

// International format
"pattern": "\\b\\+?1?[-.]?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4}\\b"
```

## Validator Types

Validators provide additional verification beyond pattern matching to reduce false positives.

### Available Validators

#### 1. `luhn` - Luhn Algorithm (Mod-10 Checksum)

Validates credit card numbers using the Luhn algorithm.

**Use for**: Credit card numbers, some identification numbers

**Example**:
```json
{
  "id": "CCN_VISA",
  "pattern": "\\b4\\d{15}\\b",
  "validator": "luhn",
  "confidence": 0.95
}
```

**Validation logic**:
- Extracts digits from the matched string
- Applies Luhn algorithm: doubles every second digit from right to left
- Subtracts 9 from doubled digits > 9
- Sums all digits and checks if divisible by 10

#### 2. `ssn` - Social Security Number Validation

Validates SSN area codes (first three digits).

**Use for**: Social Security Numbers

**Example**:
```json
{
  "id": "SSN_FULL",
  "pattern": "\\b\\d{3}-\\d{2}-\\d{4}\\b",
  "validator": "ssn",
  "confidence": 0.9
}
```

**Validation logic**:
- Extracts 9 digits from the matched string
- Checks area code (first 3 digits) is between 001-899
- Rejects area code 666 (never issued)
- Rejects 000, 900-999 (invalid ranges)

#### 3. No Validator (Optional)

Omit the `validator` property or set to `null` for pattern-only matching.

**Use for**: Patterns with high specificity (emails, structured IDs)

**Example**:
```json
{
  "id": "EMAIL",
  "pattern": "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b",
  "confidence": 0.8
}
```

### Context Validation

When `contextRequired` is set to `true`, the scanner checks the surrounding text for false positive indicators:

**False positive indicators**:
- Keywords: "example", "test", "sample", "dummy", "fake", "mock", "placeholder"
- Common test values: "xxx", "000-00-0000", "123-45-6789", "0000-0000-0000-0000"

**Context window**: ±20 characters around the match

**Example**:
```json
{
  "id": "SSN_COMPACT",
  "pattern": "\\b\\d{9}\\b",
  "validator": "ssn",
  "confidence": 0.7,
  "contextRequired": true
}
```

This rule would **reject** matches in text like:
- "This is an example SSN: 123456789"
- "Use test SSN 987654321 for testing"

## Confidence Scoring

The `confidence` value represents the likelihood that a match is a true positive.

### Confidence Scale

| Range | Interpretation | Example Use Cases |
|-------|---------------|-------------------|
| 0.9 - 1.0 | Very High | SSN with dashes + validation, Credit cards with Luhn |
| 0.8 - 0.89 | High | Email addresses, phone numbers with formatting |
| 0.7 - 0.79 | Medium | Compact SSNs with context required |
| 0.6 - 0.69 | Moderate | Phone numbers without formatting |
| < 0.6 | Low | Generic patterns, high false positive risk |

### Factors Affecting Confidence

1. **Pattern specificity**: More specific patterns → higher confidence
2. **Validator presence**: Rules with validators → higher confidence
3. **Context requirements**: Context validation → adjusted confidence
4. **Data format**: Structured formats (with delimiters) → higher confidence

### Confidence Guidelines

```json
// High confidence: specific pattern + validator
{
  "pattern": "\\b\\d{3}-\\d{2}-\\d{4}\\b",
  "validator": "ssn",
  "confidence": 0.9
}

// Medium confidence: broader pattern + validator + context
{
  "pattern": "\\b\\d{9}\\b",
  "validator": "ssn",
  "confidence": 0.7,
  "contextRequired": true
}

// Lower confidence: pattern without validation
{
  "pattern": "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b",
  "confidence": 0.6
}
```

## Testing Rules

### Unit Testing

Test rules using the Java API:

```java
import com.dlp.discovery.policy.RuleBundle;
import com.dlp.discovery.detection.RegexDetectionEngine;

// Load rules
RuleBundle bundle = RuleBundle.fromJsonFile(Paths.get("rules.json"));

// Create detection engine
RegexDetectionEngine engine = new RegexDetectionEngine(bundle);

// Test detection
String testContent = "SSN: 123-45-6789";
List<Finding> findings = engine.scan(testContent);

// Assertions
assertEquals(1, findings.size());
assertEquals("SSN_FULL", findings.get(0).getRuleId());
```

### Test Cases to Include

1. **True Positives**: Verify the rule matches valid sensitive data
   ```java
   testDetection("123-45-6789", true, "SSN_FULL");
   ```

2. **True Negatives**: Verify the rule doesn't match non-sensitive data
   ```java
   testDetection("123-456-7890", false, "SSN_FULL"); // Phone, not SSN
   ```

3. **False Positive Prevention**: Test with common false positive triggers
   ```java
   testDetection("Example SSN: 123-45-6789", false, "SSN_FULL");
   ```

4. **Validator Effectiveness**: Test with invalid checksums
   ```java
   testDetection("4111111111111112", false, "CCN_VISA"); // Invalid Luhn
   ```

### Manual Testing

Test rules with sample files:

```bash
# Run scanner with custom rules
java -jar scanner-app.jar \
  --rules custom_rules.json \
  --input test_data.txt \
  --output results.json

# Review results
cat results.json | jq '.findings[] | {ruleId, confidence}'
```

## Rule Bundle Versioning

### Version Format

Use semantic versioning: `MAJOR.MINOR`

- **MAJOR**: Increment for breaking changes (rule schema changes, removed rules)
- **MINOR**: Increment for additions (new rules, non-breaking enhancements)

**Examples**:
- `1.0` - Initial release
- `1.1` - Added new credit card rules
- `2.0` - Changed pattern syntax or removed deprecated rules

### Signature Timestamp

The optional `signatureTimestamp` field records when the bundle was created or last signed:

```json
{
  "version": "1.0",
  "signatureTimestamp": "2024-01-15T10:30:00Z",
  "rules": [...]
}
```

**Format**: ISO 8601 timestamp (RFC 3339)

**Use cases**:
- Rule bundle integrity verification
- Tracking rule freshness
- Audit logging

### Loading Rules

Rules can be loaded from multiple sources:

```java
// From JSON string
RuleBundle bundle = RuleBundle.fromJson(jsonString);

// From input stream
RuleBundle bundle = RuleBundle.fromJson(inputStream);

// From file
RuleBundle bundle = RuleBundle.fromJsonFile(Paths.get("rules.json"));
```

## Best Practices

### 1. Rule Design

- **Start specific, then broaden**: Begin with strict patterns and relax as needed
- **Use validators**: Always include validators for numeric patterns (SSN, credit cards)
- **Consider context**: Enable `contextRequired` for patterns prone to false positives
- **Test thoroughly**: Include both positive and negative test cases

### 2. Pattern Writing

- **Anchor patterns**: Use `\\b` word boundaries to prevent partial matches
- **Avoid greedy quantifiers**: Prefer `\\d{9}` over `\\d+`
- **Keep patterns simple**: Complex patterns are harder to maintain and debug
- **Document intent**: Use descriptive rule names and IDs

### 3. Performance

- **Optimize patterns**: Avoid backtracking-heavy patterns
- **Be specific**: More specific patterns match faster
- **Group related rules**: Organize by category for easier management
- **Limit rule count**: Too many rules can slow scanning

### 4. Maintenance

- **Version bundles**: Track changes with semantic versioning
- **Document changes**: Maintain a changelog for rule updates
- **Review regularly**: Update rules based on false positive/negative feedback
- **Test before deployment**: Validate all rules before production use

### 5. Security

- **Avoid sensitive data in rules**: Don't include real SSNs/credit cards in patterns
- **Validate JSON**: Ensure rule bundles are well-formed
- **Control access**: Restrict who can modify rule bundles
- **Sign bundles**: Use `signatureTimestamp` for integrity verification

### 6. Categories

Use consistent category naming:
- **PII**: Personally Identifiable Information (SSN, email, phone)
- **PCI**: Payment Card Industry data (credit cards)
- **PHI**: Protected Health Information (medical records)
- **SECRETS**: API keys, passwords, tokens
- **FINANCIAL**: Bank accounts, routing numbers

### Example Complete Rule Bundle

```json
{
  "version": "1.0",
  "signatureTimestamp": "2024-01-15T10:30:00Z",
  "rules": [
    {
      "id": "SSN_FULL",
      "name": "Social Security Number (Full)",
      "category": "PII",
      "severity": "HIGH",
      "pattern": "\\b\\d{3}-\\d{2}-\\d{4}\\b",
      "validator": "ssn",
      "confidence": 0.9
    },
    {
      "id": "SSN_COMPACT",
      "name": "Social Security Number (Compact)",
      "category": "PII",
      "severity": "HIGH",
      "pattern": "\\b\\d{9}\\b",
      "validator": "ssn",
      "confidence": 0.7,
      "contextRequired": true
    },
    {
      "id": "CCN_VISA",
      "name": "Credit Card Number (Visa)",
      "category": "PCI",
      "severity": "CRITICAL",
      "pattern": "\\b4\\d{15}\\b",
      "validator": "luhn",
      "confidence": 0.95
    },
    {
      "id": "EMAIL",
      "name": "Email Address",
      "category": "PII",
      "severity": "MEDIUM",
      "pattern": "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b",
      "confidence": 0.8
    },
    {
      "id": "PHONE_US",
      "name": "US Phone Number",
      "category": "PII",
      "severity": "LOW",
      "pattern": "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b",
      "confidence": 0.6
    }
  ]
}
```

## Related Documentation

- [README.md](../README.md) - Project overview and usage
- [default_rules.json](../scanner-app/src/main/resources/default_rules.json) - Default rule bundle

## Support

For questions or issues with rule creation, please open an issue on the project repository.
