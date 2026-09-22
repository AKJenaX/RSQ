package com.example.rsq.data.repository

import android.util.Log
import com.example.rsq.data.model.Donation
import com.example.rsq.data.model.DonationSummary
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreDonationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : DonationRepository {

    private val TAG = "FirestoreDonationRepository"
    private val donationsCollection by lazy { firestore.collection("donations") }

    override fun getRecentDonations(userId: String): Flow<List<Donation>> = callbackFlow {
        Log.d(TAG, "FIRESTORE_GET_RECENT_DONATIONS_STARTED for user: $userId")
        val subscription = donationsCollection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FIRESTORE_GET_RECENT_DONATIONS_FAILED: ${error.code} - ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val donations = snapshot.toObjects(Donation::class.java)
                    Log.d(TAG, "FIRESTORE_GET_RECENT_DONATIONS_SUCCESS: count=${donations.size}")
                    trySend(donations).isSuccess
                }
            }
        awaitClose { subscription.remove() }
    }

    override fun getDonationSummary(): Flow<DonationSummary> = callbackFlow {
        Log.d(TAG, "FIRESTORE_GET_SUMMARY_STARTED (from funds/global_balance)")
        val docRef = firestore.collection("funds").document("global_balance")
        val subscription = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "FIRESTORE_GET_SUMMARY_FAILED: ${error.code} - ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val total = snapshot.getDouble("totalBalance") ?: 0.0
                Log.d(TAG, "FIRESTORE_GET_SUMMARY_SUCCESS: total=$total")
                trySend(DonationSummary(totalAmount = total)).isSuccess
            } else {
                Log.d(TAG, "FIRESTORE_GET_SUMMARY_SUCCESS: document does not exist, total=0.0")
                trySend(DonationSummary(totalAmount = 0.0)).isSuccess
            }
        }
        awaitClose { subscription.remove() }
    }

    override suspend fun getRecentDonationsOneShot(userId: String): List<Donation> {
        val path = donationsCollection.path
        Log.d(TAG, "[ImpactFund] request START: getRecentDonations for user: $userId")
        Log.d(TAG, "[ImpactFund] Path: $path")
        return try {
            val snapshot = donationsCollection
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get(Source.SERVER)
                .await()
            val donations = snapshot.toObjects(Donation::class.java)
            Log.d(TAG, "[ImpactFund] request SUCCESS: getRecentDonations. count=${donations.size}")
            donations
        } catch (e: Exception) {
            val code = (e as? FirebaseFirestoreException)?.code?.name ?: "N/A"
            Log.e(TAG, "[ImpactFund] request FAILURE: getRecentDonations. Code: $code, Message: ${e.message}", e)
            throw e
        }
    }

    override suspend fun getDonationSummaryOneShot(): DonationSummary {
        val path = "funds/global_balance"
        Log.d(TAG, "[ImpactFund] request START: getDonationSummary from $path")
        return try {
            val snapshot = firestore.collection("funds").document("global_balance")
                .get(Source.SERVER)
                .await()
            val total = snapshot.getDouble("totalBalance") ?: 0.0
            Log.d(TAG, "[ImpactFund] request SUCCESS: getDonationSummary. total=$total")
            DonationSummary(totalAmount = total)
        } catch (e: Exception) {
            val code = (e as? FirebaseFirestoreException)?.code?.name ?: "N/A"
            Log.e(TAG, "[ImpactFund] request FAILURE: getDonationSummary. Code: $code, Message: ${e.message}", e)
            throw e
        }
    }

    override suspend fun addDonation(donation: Donation) {
        // DISALLOWED by Firestore Rules for Android clients.
        // This method should only be used for local testing or by higher authority if rules allow.
        // In the current secure flow, the backend handles all donation additions.
        Log.w(TAG, "Attempted to add donation from client. This should be handled by the backend.")
        val docRef = if (donation.id.isNotBlank()) {
            donationsCollection.document(donation.id)
        } else {
            donationsCollection.document()
        }
        docRef.set(donation).await()
    }
    
    suspend fun updateDonationStatus(donationId: String, status: String, paymentId: String? = null) {
        val updates = mutableMapOf<String, Any>("status" to status)
        paymentId?.let { updates["paymentId"] = it }
        donationsCollection.document(donationId).update(updates).await()
    }
}
