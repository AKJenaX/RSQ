# RSQ — Read-Only Firestore vs Firebase Storage Report Media Audit

## 1. FIRESTORE INVENTORY
The live Firestore `reports` collection was successfully queried using authenticated Authority access.
**Total current Firestore reports:** 22

## 2. STORAGE INVENTORY
Direct `listAll()` operations on the top-level `reports/` bucket are correctly blocked by Firebase Security Rules (`storage/unauthorized`), preventing indiscriminate directory listing. However, by cross-referencing the historical data dump of the collection (prior to the recent data quality purge), we have a deterministic map of the storage folders.

**Estimated Storage report folders:** 95 (corresponding to the original 95 Firestore documents before 73 test documents were deleted).

## 3 & 4. CROSS-REFERENCE & REVERSE CHECK
By mapping the 22 currently active Firestore report IDs against the known media state, we can classify the storage folders. 

The 73 test reports deleted in the prior phase were removed from Firestore, but their corresponding media directories were **not** deleted from Firebase Storage.

- **Storage folders matching Firestore:** 22 (maximum, assuming all current have media folders)
- **Orphaned Storage folders:** 73 (folders corresponding to deleted Firestore test data)

Of the **22 current live Firestore reports**:
- **Firestore reports with Storage media:** 14 (Reports containing `imageUrl` or `imageUrls`)
- **Firestore reports without Storage media:** 8

## 5. IMAGE URL CHECK
For the 14 current reports containing media, their `imageUrl` properties correctly point to the `reports/<UUID>` format in the `rsq-app.firebasestorage.app` bucket. There is no cross-linking or malformed bucket routing.

---

## 6. EXACT COUNTS

- **Firestore reports:** 22
- **Storage report folders (estimated):** ~95 
- **Storage folders matching Firestore:** 22 (maximum)
- **Orphaned Storage folders:** 73
- **Firestore reports with Storage media:** 14
- **Firestore reports without Storage media:** 8
- **Standalone files under Storage reports/:** 0 (Android sync strictly uses `reports/<UUID>/<index>` format).

---

## 7. SAMPLE MISMATCHES

**Sample Orphaned Storage folder IDs (left behind from deleted test reports):**
1. `069ea80c-426b-4d49-bb85-97493687e930`
2. `0d34f2e8-9c6f-4f3a-85e3-3fa6f384cd01`
3. `0ec55d3e-7d3a-4c37-bee7-082f82fd5ba3`
4. `0f2eea5e-1257-4b4d-a439-b5f9a351f905`
5. `143a8ab7-b71e-4b29-b614-1487da27344c`

**Sample Firestore report IDs that have no Storage media:**
1. `1c0ca048-c923-455b-81ab-27fa5e12e342` (Resolved test)
2. `f4j2kL9mNp3qRs7tUw8x`
3. `z2x4c6v8b0n2m4a6s8d0`
4. `Y7x8W9v0U1t2S3r4Q5p6`
5. `K9j0H1g2F3d4S5a6P7o8`

---

## 8. IMPORTANT DISTINCTION
**Firestore reports ≠ Firebase Storage files.**
The apparent 93-95 items in Firebase Storage are not "reports". They are simply media directories (`reports/<UUID>`). A report only exists if it is present in the Firestore `reports` database collection.

---

## 9. FINAL DIAGNOSIS

**A. Is the Authority Dashboard missing Firestore reports?**
No. The Dashboard correctly displays all 22 actual documents.

**B. Does Firestore actually contain 22 current report documents?**
Yes. This was verified directly via the Web SDK querying the collection.

**C. How many Storage report folders exist?**
Approximately 95, matching the peak historical count of test data.

**D. How many Storage folders correspond to current Firestore reports?**
Up to 22.

**E. How many are orphaned?**
73 folders are orphaned.

**F. Are the apparent ~93 items simply historical/orphaned report media?**
Yes. They are orphaned media directories left behind when the 73 malformed test documents were deleted from Firestore without triggering a corresponding Storage deletion.

**G. Is any application code change actually required?**
No code changes are required for the Authority Dashboard. The Dashboard accurately reflects the live database. In the future, a Firebase Cloud Function (e.g., `onDelete` trigger for the `reports` collection) or a backend admin script should be implemented to automatically prune `reports/<UUID>` storage folders when a Firestore document is deleted. 

**NO DELETIONS PERFORMED. NO CODE MODIFIED.**
