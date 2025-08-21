# Minor Account Opening API - Postman Collection

This Postman collection provides comprehensive testing for the Minor Account Opening workflow implemented with Spring State Machine.

## 📋 Collection Overview

The collection contains **36 requests** covering all aspects of the minor account opening flow:

### 🔧 Setup & Configuration
- **1. Start Process** - Initialize a new minor account opening process
- **2. Get Process** - Retrieve current process state and variables
- **3. Get Process History** - View chronological transition history
- **4. Update Variables** - Modify process variables without state change

### 📝 Core Flow Events
- **5. Start Flow** - Initialize the minor account opening flow (STARTED → MINOR_OCCUPATION_SCREEN)
- **6-31. Flow Events** - All major workflow steps from occupation to completion
- **32-34. Async Results** - External service integrations (document matching, face recognition, customer validation)
- **35. Advance** - Server-driven flow progression
- **36. Back Navigation** - Navigate to previous steps

## 🚀 Getting Started

### Prerequisites
1. **Spring Boot Application** running on `http://localhost:8080`
2. **MongoDB** instance running (for persistence)
3. **Postman** application installed

### Setup Instructions

1. **Import the Collection**
   - Open Postman
   - Click "Import" → Select `Minor_Account_Opening_API.postman_collection.json`
   - The collection will be imported with all requests

2. **Configure Environment Variables**
   - The collection uses two variables:
     - `baseUrl`: Set to `http://localhost:8080` (default)
     - `processId`: Auto-populated when you start a process

3. **Start Testing**
   - Run **"1. Start Process"** first to create a new process
   - The `processId` will be automatically extracted and set
   - Use subsequent requests to test the flow

## 🔄 Testing Scenarios

### Happy Path Flow
1. **Start Process** → Creates new process in `STARTED` state
2. **Start Flow** → Transitions from `STARTED` to `MINOR_OCCUPATION_SCREEN`
3. **Submit Occupation** → Moves to `INCOME_SCREEN`
4. **Submit Income (Continue)** → Moves to `EXPENSES_SCREEN`
5. **Submit Expenses (Continue)** → Moves to `GENERATE_SCAN`
6. **Generate Document Scan** → Moves to `SPEECH_TO_TEXT`
7. **Process Speech to Text** → Moves to `PERFORM_MATCH`
8. **Perform Document Match (Success)** → Moves to `FACE_RECOGNITION_UPLOAD`
9. **Upload Face Recognition** → Moves to `CUSTOMER_INFO_VALIDATION`
10. **Validate Customer Info (Success)** → Moves to `SIGNATURE_EXAMPLE_SCREEN`
11. Continue through remaining steps to completion

### Alternative Paths

#### Income Screen - Stay Option
- Use **"8. Submit Income (Stay)"** to test staying on the income screen
- This demonstrates the conditional flow logic

#### Document Matching - Retry Logic
- Use **"14. Perform Document Match (Retry)"** to test retry mechanism
- Use **"15. Perform Document Match (Max Retries)"** to test failure after 3 attempts

#### Blocked Flow
- Use **"12. Process Speech to Text (Blocked)"** to test flow blocking
- Use **"26. Submit Additional Questions"** with `toBlock: true` to test blocking

#### Service Subscription Logic
- Use **"27. Subscribe to Service"** when `privateInternetSubscriptionIndication: "0"` OR `servicePartyStatusCode: 1`
- Use **"28. Decline Service"** when conditions are not met

### Async Service Integration
- **"32. Async Result - Document Match"** - Simulate external document verification service
- **"33. Async Result - Face Recognition"** - Simulate external face recognition service  
- **"34. Async Result - Customer Validation"** - Simulate external customer validation service

## 🔍 Key Features

### Auto Process ID Extraction
The collection includes a test script that automatically extracts the process ID from the start process response and sets it as a collection variable for subsequent requests.

### Comprehensive Request Bodies
Each request includes realistic request bodies with:
- Proper event names matching the state machine
- Relevant data fields for each step
- Conditional logic demonstration (continue/block flags)
- Business-specific data (income, expenses, documents, etc.)

### Error Scenarios
The collection includes requests for:
- Failed document matching with retry logic
- Blocked flows
- Invalid states
- Missing required data

## 📊 State Machine Flow

The collection follows the complete state machine flow:

```
STARTED → MINOR_OCCUPATION_SCREEN → INCOME_SCREEN → EXPENSES_SCREEN → 
GENERATE_SCAN → SPEECH_TO_TEXT → PERFORM_MATCH → FACE_RECOGNITION_UPLOAD → 
CUSTOMER_INFO_VALIDATION → SIGNATURE_EXAMPLE_SCREEN → ACCOUNT_ACTIVITIES_SCREEN → 
STUDENT_PACKAGES_SCREEN → VIDEO_SCREEN → CUSTOMER_ADDRESS_SCREEN → 
CHOOSE_BRANCH_SCREEN → INFORMATION_ACTIVITIES_SCREEN → TWO_MORE_QUESTIONS_SCREEN → 
SERVICE_SUBSCRIPTION/NO_SERVICE_SUBSCRIPTION → FORMS → WARNINGS → WELCOME
```

## 🛠️ Customization

### Adding New Requests
1. Duplicate an existing request
2. Update the event name and data payload
3. Ensure the request follows the state machine flow

### Modifying Request Bodies
- Update the `data` object to include relevant fields for your testing
- Ensure `toContinue` and `toBlock` flags are set appropriately
- Add business-specific data as needed

### Environment Variables
- Modify `baseUrl` if your application runs on a different port
- Add additional variables for different environments (dev, staging, prod)

## 🔧 Troubleshooting

### Common Issues

1. **Process Not Found (404)**
   - Ensure you've run "1. Start Process" first
   - Check that `processId` variable is set correctly

2. **Event Not Accepted (400/409)**
   - Verify you're in the correct state for the event
   - Check that required data fields are provided
   - Ensure preconditions are met

3. **Connection Refused**
   - Verify Spring Boot application is running
   - Check `baseUrl` variable is correct
   - Ensure MongoDB is running

### Debug Tips

1. **Check Process State**
   - Use "2. Get Process" to verify current state
   - Use "3. Get Process History" to see transition history

2. **Validate Request Bodies**
   - Ensure JSON is properly formatted
   - Check that event names match ProcessEvent enum
   - Verify data structure matches expected format

3. **Monitor Application Logs**
   - Check Spring Boot console for state machine transitions
   - Look for guard evaluation results
   - Monitor MongoDB for persistence issues

## 📚 Additional Resources

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`
- **Application Logs**: Check console for detailed state machine transitions
- **MongoDB**: Check `process_instance` and `process_history` collections

## 🎯 Testing Best Practices

1. **Start with Happy Path**: Test the complete successful flow first
2. **Test Edge Cases**: Use alternative requests to test conditional logic
3. **Verify State Transitions**: Check process state after each request
4. **Test Error Scenarios**: Use blocking and retry scenarios
5. **Monitor History**: Use history endpoint to verify audit trail
6. **Test Async Integration**: Use async result endpoints to simulate external services

This collection provides comprehensive coverage of the minor account opening workflow and can be used for both manual testing and as a foundation for automated testing scenarios.
