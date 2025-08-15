# Minor Account Opening State Machine (Spring Boot 3.3, Java 17)

This project implements a **detailed minor account opening workflow** using **Spring Boot 3.3**, **Java 21**, and **Spring State Machine**.
It provides a comprehensive onboarding flow for minors opening bank accounts, with complex branching logic, parallel processing, and retry mechanisms.

---

## ✨ Key Features

1. **Detailed Minor Account Opening Flow**
   - **21 distinct states** covering the complete onboarding process
   - **Complex branching logic** with conditional transitions
   - **Parallel processing** for face recognition and customer validation
   - **Retry mechanisms** for document matching (up to 3 attempts)
   - **Service subscription logic** based on customer preferences

2. **State-Driven Workflow**
   - State changes are triggered by domain events (`ProcessEvent`)
   - State machine configuration defines allowed transitions and guards
   - Supports validation and additional variable storage during events
   - Implements complex business logic through guards

3. **Persistent State Machine**
   - Process state is stored in MongoDB via `ProcessInstanceRepository`
   - Variables are merged and updated on each event
   - Allows pausing and resuming flows
   - Complete audit trail with transition history

4. **Extensible Architecture**
   - Clean separation between service logic, state machine config, and persistence
   - Easy to add new states, events, or modify business logic
   - Comprehensive precondition validation system

---

## 🏗️ Flow Overview

The minor account opening flow includes the following key stages:

1. **Initial Information Collection**
   - Occupation screen
   - Income screen  
   - Expenses screen

2. **Document Processing**
   - Document scan generation
   - Speech-to-text processing
   - Document matching with retry logic

3. **Identity Verification**
   - Face recognition upload (parallel)
   - Customer information validation (parallel)

4. **Account Setup**
   - Signature example collection
   - Account activities selection
   - Student packages selection
   - Video verification

5. **Location & Preferences**
   - Customer address collection
   - Branch selection
   - Information activities setup
   - Additional questions

6. **Service Configuration**
   - Conditional service subscription based on preferences
   - Forms completion
   - Warnings acknowledgment
   - Welcome completion

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
```http
POST /process/{id}/advance
{
  "data": {
    "toContinue": true,
    "income": 5000
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

### Guards
The system uses various guards to control flow transitions:

- **`toContinue()`** - Controls whether to proceed to next screen
- **`toBlock()`** - Determines if flow should be blocked
- **`scanMatchOk()`** - Validates document matching results
- **`oneToManyStatusOk()`** - Checks customer validation status
- **`serviceNeeded()`** - Determines service subscription requirements

### Variables
Key variables used throughout the flow:

- `toContinue` - Boolean flag for flow continuation
- `toBlock` - Boolean flag for flow blocking
- `scanMatch` - Document matching result ("OK"/"FAIL")
- `numOfScanMatchTries` - Retry counter for document matching
- `oneToManyStatus` - Customer validation status
- `privateInternetSubscriptionIndication` - Service preference
- `servicePartyStatusCode` - Service party status

---

## 🛠️ Technology Stack

| Component        | Technology                    |
| ---------------- | ----------------------------- |
| **Framework**    | Spring Boot 3.3               |
| **Language**     | Java 21                       |
| **State Machine**| Spring State Machine 3.2      |
| **Database**     | MongoDB                       |
| **Documentation**| OpenAPI 3.0 (Swagger)         |
| **Build Tool**   | Maven                         |

---

## 📊 State Machine States

| State | Screen Code | Description |
|-------|-------------|-------------|
| `STARTED` | s500.1 | Initial state |
| `MINOR_OCCUPATION_SCREEN` | s530.1 | Occupation information |
| `INCOME_SCREEN` | s530.2 | Income details |
| `EXPENSES_SCREEN` | s530.3 | Expense information |
| `GENERATE_SCAN` | s530.4 | Document scan generation |
| `SPEECH_TO_TEXT` | s530.5 | Speech processing |
| `PERFORM_MATCH` | s530.6 | Document matching |
| `FACE_RECOGNITION_UPLOAD` | s530.7 | Face recognition |
| `CUSTOMER_INFO_VALIDATION` | s530.8 | Customer validation |
| `SIGNATURE_EXAMPLE_SCREEN` | s530.9 | Signature collection |
| `ACCOUNT_ACTIVITIES_SCREEN` | s530.10 | Account activities |
| `STUDENT_PACKAGES_SCREEN` | s530.11 | Student packages |
| `VIDEO_SCREEN` | s530.12 | Video verification |
| `CUSTOMER_ADDRESS_SCREEN` | s530.13 | Address collection |
| `CHOOSE_BRANCH_SCREEN` | s530.14 | Branch selection |
| `INFORMATION_ACTIVITIES_SCREEN` | s530.15 | Information activities |
| `TWO_MORE_QUESTIONS_SCREEN` | s530.16 | Additional questions |
| `SERVICE_SUBSCRIPTION` | s530.17 | Service subscription |
| `NO_SERVICE_SUBSCRIPTION` | s530.18 | No service option |
| `FORMS` | s530.19 | Forms completion |
| `WARNINGS` | s530.20 | Warnings acknowledgment |
| `WELCOME` | s530.21 | Welcome completion |

---

## 🚀 Getting Started

1. **Prerequisites**
   - Java 21
   - Maven 3.8+
   - MongoDB

2. **Run the Application**
   ```bash
   mvn spring-boot:run
   ```

3. **Access API Documentation**
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - OpenAPI JSON: http://localhost:8080/v3/api-docs

4. **Test the Flow**
   ```bash
   # Start a new process
   curl -X POST http://localhost:8080/process/start \
     -H "Content-Type: application/json" \
     -d '{"clientId":"test-123","type":"MINOR"}'
   ```

---

## 📝 License

This project is licensed under the MIT License.
