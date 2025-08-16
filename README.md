# Minor Account Opening State Machine (Spring Boot 3.5, Java 17)

This project implements a detailed minor account opening workflow using Spring Boot 3.5, Java 17, and Spring State Machine 3.2.
It provides a comprehensive onboarding flow for minors opening bank accounts, with conditional transitions, retry mechanisms, and precondition-based validations.

---

## ✨ Key Features

1. Detailed Minor Account Opening Flow
   - 20+ distinct states covering the complete onboarding process
   - Conditional transitions and guard-based business logic
   - Retry mechanisms for document matching (up to 3 attempts)
   - Service subscription logic driven by customer preferences

2. State-Driven Workflow
   - State changes are triggered by domain events (ProcessEvent)
   - State machine configuration defines allowed transitions and guards
   - Precondition layer validates request payload and can enrich variables

3. Persistent State Machine
   - Process state is stored in MongoDB via ProcessInstanceRepository
   - Variables are merged and updated on each event
   - Complete audit trail with transition history

4. Extensible Architecture
   - Clean separation between service logic, state machine config, and persistence
   - Easy to add/remove states, events, or modify business rules
   - Comprehensive precondition validation system with detailed DEBUG logs

---

## 🏗️ Flow Overview

The minor account opening flow includes the following key stages:

1. Initial Information Collection
   - Occupation screen
   - Income screen  
   - Expenses screen

2. Document Processing
   - Document scan generation
   - Speech-to-text processing (decision to block is based on voice score threshold)
   - Document matching with retry logic

3. Identity Verification
   - Face recognition upload
   - Customer information validation

4. Account Setup
   - Signature example collection
   - Account activities selection
   - Student packages selection
   - Video verification

5. Location & Preferences
   - Customer address collection
   - Branch selection
   - Information activities setup
   - Additional questions

6. Service Configuration
   - Conditional service subscription based on preferences
   - Forms submission to proceed to warnings
   - Warnings acknowledgment leading to Welcome

---

## 🚀 API Endpoints

### Start Process
```http
POST /process/start
{
  "clientId": "client-123",
  "type": "MINOR",
  "initialData": {
    "customerName": "John Doe",
    "age": 16
  }
}
```

### Send Event (Client-Driven)
```http
POST /process/{id}/event
{
  "event": "SUBMIT_OCCUPATION",
  "data": {
    "occupation": "Student",
    "school": "High School"
  }
}
```

### Advance (Server-Driven)
Event can be omitted — it will be resolved by the server from the current state.
```http
POST /process/{id}/advance
{
  "event": "PROCESS_SPEECH_TO_TEXT", // optional
  "data": {
    "transcription": "I confirm the information is correct",
    "voiceScore": 0.96
  }
}
```

### Async Results
```http
POST /process/{id}/async-result
{
  "type": "document_match",
  "result": {
    "scanMatch": "OK",
    "confidence": 0.95
  }
}
```

---

## 🔧 Configuration

### Workflow Settings (application.yaml)
```yaml
workflow:
  strict-guards: true            # enable additional guard checks
  auto-advance-on-start: true    # advance from STARTED to the first screen via StepPlan
  voice-score-threshold: 0.95    # threshold for speech-to-text voice score
```

### Guards (selected)
The system uses guards to control flow transitions:

- `type(ProcessType.MINOR|MINOR_TO_REGULAR)` – route-specific execution
- `toContinue()` – controls whether to proceed to next screen
- `sttBlocked()` – derived by preconditions from `voiceScore` vs threshold; controls STT outcome (BLOCKED vs PERFORM_MATCH)
- `scanMatchOk()` – validates document matching results
- `oneToManyStatusOk()` – checks customer validation status after signature
- `bankBranchAccountExists()` – for MINOR_TO_REGULAR document match
- `needsServiceSubscription()` – determines service subscription requirement

### Variables (selected)
Key variables used throughout the flow:

- `toContinue` – Boolean flag for flow continuation
- `voiceScore` – numeric score used by STT decision
- `sttBlocked` – Boolean decision computed by preconditions for STT
- `scanMatch` – document match result ("OK"/other)
- `numOfScanMatchTries` – retry counter for document matching
- `oneToManyStatus` – customer validation status
- `privateInternetSubscriptionIndication`, `servicePartyStatusCode` – service preference inputs
- `accountDetails` – map with `bankId`, `branchCode`, `accountNumber` (MINOR_TO_REGULAR)

---

## 🛠️ Technology Stack

| Component         | Technology                |
| ----------------- | ------------------------- |
| Framework         | Spring Boot 3.5.x         |
| Language          | Java 17                   |
| State Machine     | Spring State Machine 3.2  |
| Database          | MongoDB                   |
| Documentation     | OpenAPI 3.0 (Swagger)     |
| Build Tool        | Maven                     |

---

## 📊 State Machine States

| State                           | Screen Code | Description                   |
|---------------------------------|-------------|-------------------------------|
| `STARTED`                       | s500.1      | Initial state                 |
| `MINOR_OCCUPATION_SCREEN`       | s530.1      | Occupation information        |
| `INCOME_SCREEN`                 | s530.2      | Income details                |
| `EXPENSES_SCREEN`               | s530.3      | Expense information           |
| `GENERATE_SCAN`                 | s530.4      | Document scan generation      |
| `SPEECH_TO_TEXT`                | s530.5      | Speech processing             |
| `PERFORM_MATCH`                 | s530.6      | Document matching             |
| `FACE_RECOGNITION_UPLOAD`       | s530.7      | Face recognition              |
| `CUSTOMER_INFO_VALIDATION`      | s530.8      | Customer validation           |
| `SIGNATURE_EXAMPLE_SCREEN`      | s530.9      | Signature collection          |
| `ACCOUNT_ACTIVITIES_SCREEN`     | s530.10     | Account activities            |
| `STUDENT_PACKAGES_SCREEN`       | s530.11     | Student packages              |
| `VIDEO_SCREEN`                  | s530.12     | Video verification            |
| `CUSTOMER_ADDRESS_SCREEN`       | s530.13     | Address collection            |
| `CHOOSE_BRANCH_SCREEN`          | s530.14     | Branch selection              |
| `INFORMATION_ACTIVITIES_SCREEN` | s530.15     | Information activities        |
| `TWO_MORE_QUESTIONS_SCREEN`     | s530.16     | Additional questions          |
| `SERVICE_SUBSCRIPTION`          | s530.17     | Service subscription          |
| `NO_SERVICE_SUBSCRIPTION`       | s530.18     | No service option             |
| `WARNINGS`                      | s530.20     | Warnings acknowledgment       |
| `WELCOME`                       | s530.21     | Welcome completion            |

---

## 🚀 Getting Started

1. Prerequisites
   - Java 17
   - Maven 3.8+
   - MongoDB (local or remote)

2. Run the Application
```bash
mvn spring-boot:run
```

3. Access API Documentation
   - Swagger UI: http://localhost:8080/swagger-ui
   - OpenAPI JSON: http://localhost:8080/v3/api-docs

4. Quick Test
```bash
# Start a new process
curl -X POST http://localhost:8080/process/start \
  -H "Content-Type: application/json" \
  -d '{"clientId":"test-123","type":"MINOR"}'
```

---

## 📝 License

This project is licensed under the MIT License.
