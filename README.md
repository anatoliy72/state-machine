This project implements a **state-driven account onboarding system** using **Spring Boot 3.3**, **Java 21**, and **Spring State Machine**.  
It supports multiple onboarding flows, each with its own business logic and transitions, and persists process state in a MongoDB-backed repository.

---

## ✨ Key Features

1. **Multiple Process Types**
    - **SINGLE_OWNER** – Single account holder onboarding
    - **MULTI_OWNER** – Multiple owners with coordination step
    - **MINOR** – Minor account creation with parent consent
    - **MINOR_TO_REGULAR** – Conversion from minor to regular account

2. **Event-Driven State Transitions**
    - State changes are triggered by domain events (`ProcessEvent`).
    - State machine configuration defines allowed transitions and guards.
    - Supports validation and additional variable storage during events.

3. **Persistent State Machine**
    - Process state is stored in MongoDB via `ProcessInstanceRepository`.
    - Variables are merged and updated on each event.
    - Allows pausing and resuming flows.

4. **Extensible**
    - Adding new process types or states requires minimal configuration changes.
    - Designed for clean separation between service logic, state machine config, and persistence.

---

## 🛠️ Technology Stack

| Component           | Technology |
|---------------------|------------|
| Language            | Java 21    |
| Framework           | Spring Boot 3.3 |
| State Management    | Spring State Machine |
| Database            | MongoDB (Spring Data MongoDB) |
| Build Tool          | Maven |
| Testing             | JUnit 5, Mockito |
| API Documentation   | Springdoc OpenAPI (Swagger) |



<img width="881" height="745" alt="image" src="https://github.com/user-attachments/assets/03ecf341-dd18-46cc-80cf-e764ad4b445e" />
