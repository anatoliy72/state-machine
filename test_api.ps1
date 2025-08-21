# Test script for API calls
$baseUrl = "http://localhost:8080"

# Start process
Write-Host "=== Starting new process ===" -ForegroundColor Green
$startResponse = Invoke-RestMethod -Uri "$baseUrl/process/start" -Method POST -ContentType "application/json" -Body @"
{
  "clientId": "קטין-001",
  "type": "MINOR",
  "data": {
    "customerName": "יוסף כהן",
    "age": 16
  }
}
"@

Write-Host "Start Response:" -ForegroundColor Yellow
$startResponse | ConvertTo-Json -Depth 5
$processId = $startResponse.id

# Submit occupation
Write-Host "`n=== Submit Occupation ===" -ForegroundColor Green
$occupationResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_OCCUPATION",
  "data": {
    "occupation": "תלמיד",
    "school": "תיכון הרצל", 
    "grade": "יא"
  }
}
"@

Write-Host "Occupation Response:" -ForegroundColor Yellow
$occupationResponse | ConvertTo-Json -Depth 5

# Submit income with toContinue=true
Write-Host "`n=== Submit Income (toContinue=true) ===" -ForegroundColor Green
$incomeResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "CONTINUE_FLOW",
  "data": {
    "monthlyIncome": 500,
    "incomeSource": "דמי כיס מההורים",
    "toContinue": true
  }
}
"@

Write-Host "Income Response:" -ForegroundColor Yellow
$incomeResponse | ConvertTo-Json -Depth 5

# Submit monthly expenses
Write-Host "`n=== Submit Monthly Expenses ===" -ForegroundColor Green
$expensesResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_MONTHLY_EXPENSES",
  "data": {
    "monthlyExpenses": 300,
    "expenseCategories": ["בגדים", "בידור", "אוכל"]
  }
}
"@

Write-Host "Expenses Response:" -ForegroundColor Yellow
$expensesResponse | ConvertTo-Json -Depth 5

# Submit activities selection
Write-Host "`n=== Submit Activities Selection ===" -ForegroundColor Green
$activitiesResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_ACTIVITIES_SELECTION",
  "data": {
    "selectedActivities": ["העברות בנקאיות", "משיכת מזומן", "קניות אונליין"],
    "digitalBanking": true
  }
}
"@

Write-Host "Activities Response:" -ForegroundColor Yellow
$activitiesResponse | ConvertTo-Json -Depth 5

# Submit document type
Write-Host "`n=== Submit Document Type ===" -ForegroundColor Green
$docTypeResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_DOCUMENT_TYPE",
  "data": {
    "documentType": "תעודת זהות"
  }
}
"@

Write-Host "Document Type Response:" -ForegroundColor Yellow
$docTypeResponse | ConvertTo-Json -Depth 5

# Submit document scan
Write-Host "`n=== Submit Document Scan ===" -ForegroundColor Green
$scanResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_DOCUMENT_SCAN",
  "data": {
    "documentNumber": "123456789",
    "scanQuality": "גבוהה"
  }
}
"@

Write-Host "Scan Response:" -ForegroundColor Yellow
$scanResponse | ConvertTo-Json -Depth 5

# Submit scan match
Write-Host "`n=== Submit Scan Match ===" -ForegroundColor Green
$scanMatchResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_SCAN_MATCH",
  "data": {
    "scanMatch": "OK",
    "confidence": 0.95,
    "matchDetails": {
      "idNumber": "123456789",
      "name": "יוסף כהן",
      "matchScore": 0.98
    },
    "numOfScanMatchTries": 1
  }
}
"@

Write-Host "Scan Match Response:" -ForegroundColor Yellow
$scanMatchResponse | ConvertTo-Json -Depth 5

# Submit one to many check
Write-Host "`n=== Submit One to Many Check ===" -ForegroundColor Green
$oneToManyResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_ONE_TO_MANY",
  "data": {
    "oneToManyStatus": "OK"
  }
}
"@

Write-Host "One to Many Response:" -ForegroundColor Yellow
$oneToManyResponse | ConvertTo-Json -Depth 5

# Submit signature
Write-Host "`n=== Submit Signature ===" -ForegroundColor Green
$signatureResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_SIGNATURE",
  "data": {
    "signatureImage": "data:image/png;base64,iVBORw0KGgoAAAANS...",
    "signatureQuality": "גבוהה"
  }
}
"@

Write-Host "Signature Response:" -ForegroundColor Yellow
$signatureResponse | ConvertTo-Json -Depth 5

# Submit audio recording
Write-Host "`n=== Submit Audio Recording ===" -ForegroundColor Green
$audioResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_AUDIO_RECORDING",
  "data": {
    "audioFile": "הקלטה.wav",
    "transcription": "אני מאשר שכל המידע שמסרתי נכון ומדויק"
  }
}
"@

Write-Host "Audio Response:" -ForegroundColor Yellow
$audioResponse | ConvertTo-Json -Depth 5

# Continue from confirmation screen
Write-Host "`n=== Continue from Confirmation Screen ===" -ForegroundColor Green
$confirmationResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "CONTINUE_FLOW",
  "data": {
    "toBlock": false,
    "toContinue": true
  }
}
"@

Write-Host "Confirmation Response:" -ForegroundColor Yellow
$confirmationResponse | ConvertTo-Json -Depth 5

# Submit student packages
Write-Host "`n=== Submit Student Package ===" -ForegroundColor Green
$studentPackageResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "SUBMIT_STUDENT_PACKAGES",
  "data": {
    "selectedPackage": "חבילת תלמידים בסיסית",
    "benefits": ["פטור מדמי ניהול", "הנחות בחנויות"],
    "monthlyFee": 0
  }
}
"@

Write-Host "Student Package Response:" -ForegroundColor Yellow
$studentPackageResponse | ConvertTo-Json -Depth 5

# Continue from video screen
Write-Host "`n=== Continue from Video Screen ===" -ForegroundColor Green
$videoResponse = Invoke-RestMethod -Uri "$baseUrl/process/$processId/advance" -Method POST -ContentType "application/json" -Body @"
{
  "event": "CONTINUE_FLOW",
  "data": {
    "videoWatched": true,
    "duration": 120,
    "toContinue": true
  }
}
"@

Write-Host "Video Response:" -ForegroundColor Yellow
$videoResponse | ConvertTo-Json -Depth 5

Write-Host "`nFull test completed!" -ForegroundColor Green
