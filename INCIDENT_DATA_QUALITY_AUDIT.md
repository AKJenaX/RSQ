# Incident Data Quality Audit

## 1. Overview
- **Total Incident Documents**: 95
- **Actual Duplicate Documents**: 20 (Documents representing the same logical incident)
- **Likely Test Records**: 37 (E.g., Android offline-sync test records, or gibberish like 'guuuuu', 'rsgwbb')
- **Likely Real Records**: 55
- **Records with Suspicious Titles**: 3 (e.g. 'jf', 'uc')

## 2. Missing/Invalid Data Analysis
- **Missing Location (Null/Undefined)**: 7 (IDs: 07d83159..., 0e78b239..., 40b6782b..., 57093067..., a7813392...)
- **Invalid Coordinates (Out of bounds/NaN)**: 0
- **Missing Severity**: 0
- **Missing Status**: 0
- **Missing Timestamps**: 0

## 3. Duplicate Document Analysis
We found 20 logical duplicates (same user, title, and coordinates).
These indicate the same event was submitted multiple times, likely due to sync retries or rapid button pressing on the Android app.

## 4. Test Records Analysis
Test records found:
- ID: 069ea80c-426b-4d49-bb85-97493687e930 | Title: "guuuuu"
- ID: 0d34f2e8-9c6f-4f3a-85e3-3fa6f384cd01 | Title: "rsgwbb"
- ID: 0ec55d3e-7d3a-4c37-bee7-082f82fd5ba3 | Title: "jf"
- ID: 0f2eea5e-1257-4b4d-a439-b5f9a351f905 | Title: "fkkk"
- ID: 2a5a4698-92a6-45a0-b6b4-46f3bf8ff1d3 | Title: "345"
- ID: 38bfc0c6-6b5a-47ec-90ce-f964df0698a2 | Title: "ttttttttttttttttttttttt"
- ID: 3d1ff627-47ab-44cf-9f12-b202737f0e16 | Title: "rsgwbb"
- ID: 3e0011dc-2929-4398-8306-795ff8c626cf | Title: "rsgwbb"
- ID: 3e1476d3-89ee-4a92-8e44-dcebfb93a591 | Title: "kgf"
- ID: 483c86d2-1db6-4cab-84d2-301caaab7087 | Title: "jaaaaaaaaaaaaaa"
*(showing up to 10)*

## 5. Conclusion
These records are genuine Firebase documents stored in the `reports` collection. Many are clearly from testing (Android offline-sync tests, Authority dashboard tests, or accidental submissions), containing gibberish titles like "345", "098", "jf", "uc", or "popppooop". They are not hardcoded mock arrays, but actual Firestore data. No automated deletion was performed.
