# Read Me First

# Read Me First

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

---

## 📂 Project Structure



# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.4/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.4/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/3.5.4/reference/web/servlet.html)
* [Spring Data MongoDB](https://docs.spring.io/spring-boot/3.5.4/reference/data/nosql.html#data.nosql.mongodb)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/3.5.4/reference/actuator/index.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with MongoDB](https://spring.io/guides/gs/accessing-data-mongodb/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

