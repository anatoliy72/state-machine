# State-Driven Account Onboarding (Spring Boot 3.3, Java 21)

This project implements a **state-driven account onboarding system** using **Spring Boot 3.3**, **Java 21**, and **Spring State Machine**.
It supports multiple onboarding flows, each with its own business logic and transitions, and persists process state in a MongoDB-backed repository.

---

## ✨ Key Features

1. **Multiple Process Types**

   * **SINGLE\_OWNER** – Single account holder onboarding
   * **MULTI\_OWNER** – Multiple owners with coordination step
   * **MINOR** – Minor account creation with parent consent
   * **MINOR\_TO\_REGULAR** – Conversion from minor to regular account

2. **Event-Driven State Transitions**

   * State changes are triggered by domain events (`ProcessEvent`).
   * State machine configuration defines allowed transitions and guards.
   * Supports validation and additional variable storage during events.

3. **Persistent State Machine**

   * Process state is stored in MongoDB via `ProcessInstanceRepository`.
   * Variables are merged and updated on each event.
   * Allows pausing and resuming flows.

4. **Extensible**

   * Adding new process types or states requires minimal configuration changes.
   * Designed for clean separation between service logic, state machine config, and persistence.

---

# Choosing Between `/event` and `/advance`

## TL;DR

* Use **`/process/{id}/event`** when the **client knows the exact event** to fire (fine-grained control).
* Use **`/process/{id}/advance`** when the **server should decide the next event** from the current state & type (simpler client, server-driven flow).

---

## When to use which

| Scenario                                                 | Use        | Why                                                                   |
| -------------------------------------------------------- | ---------- | --------------------------------------------------------------------- |
| Front-end wizard is tightly coupled to domain events     | `/event`   | Client explicitly picks the event and payload for each step           |
| “Just go to the next step” UX                            | `/advance` | Server maps `(type,state) → event`, checks preconditions, transitions |
| You need strict API compatibility with legacy clients    | `/event`   | No hidden server logic; clients send explicit events                  |
| You want to minimize front-end knowledge of the workflow | `/advance` | Server owns the progression rules, less client logic                  |

---

## Contracts

### `/process/{id}/event` (explicit)

**Request**

```http
POST /process/{id}/event
Content-Type: application/json

{
  "event": "KYC_VERIFIED",
  "data": {
    "status": "APPROVED",
    "verificationId": "kyc123"
  }
}
```

**Response**

```json
{
  "id": "123",
  "clientId": "client-001",
  "type": "SINGLE_OWNER",
  "state": "WAITING_FOR_BIOMETRY",
  "screenCode": "s510.2",
  "variables": { "status": "APPROVED", "verificationId": "kyc123" },
  "createdAt": "2025-08-12T10:00:00Z",
  "updatedAt": "2025-08-12T10:01:12Z"
}
```

---

### `/process/{id}/advance` (server-driven)

**Request**

```http
POST /process/{id}/advance
Content-Type: application/json

{
  "data": {
    "status": "APPROVED",
    "verificationId": "kyc123"
  }
}
```

**Response**

```json
{
  "id": "123",
  "clientId": "client-001",
  "type": "SINGLE_OWNER",
  "state": "WAITING_FOR_BIOMETRY",
  "screenCode": "s510.2",
  "variables": { "status": "APPROVED", "verificationId": "kyc123" },
  "createdAt": "2025-08-12T10:00:00Z",
  "updatedAt": "2025-08-12T10:01:12Z"
}
```

---

## 🛠️ Technology Stack

| Component        | Technology                    |
| ---------------- | ----------------------------- |
| Language         | Java 21                       |
| Framework        | Spring Boot 3.3               |
| State Management | Spring State Machine          |
| Database         | MongoDB (Spring Data MongoDB) |
| Build Tool       | Maven                         |
| Testing          | JUnit 5, Mockito              |
| API Docs         | Springdoc OpenAPI (Swagger)   |

---

<img width="881" height="745" alt="state-machine-diagram" src="https://github.com/user-attachments/assets/03ecf341-dd18-46cc-80cf-e764ad4b445e" />
::contentReference[oaicite:0]{index=0}
