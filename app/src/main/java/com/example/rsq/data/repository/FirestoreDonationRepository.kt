package com.example.rsq.data.repository

import android.util.Log
import com.example.rsq.data.model.Donation
import com.example.rsq.data.model.DonationSummary
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreDonationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : DonationRepository {

    private val TAG = "FirestoreDonationRepository"
    private val donationsCollection = firestore.collection("donations")

    override fun getRecentDonations(): Flow<List<Donation>> = callbackFlow {
        val subscription = donationsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "SNAPSHOT_LISTENER_ERROR: getRecentDonations failed", error)
                    // We close the channel with the error. 
                    // Collectors MUST handle this using .catch {} to prevent crashes.
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val donations = snapshot.toObjects(Donation::class.java)
                    trySend(donations).isSuccess
                }
            }
        awaitClose { subscription.remove() }
    }

    override fun getDonationSummary(): Flow<DonationSummary> = callbackFlow {
        val subscription = donationsCollection
            .whereEqualTo("status", "Completed")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "SNAPSHOT_LISTENER_ERROR: getDonationSummary failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val total = snapshot.toObjects(Donation::class.java).sumOf { it.amount }
                    trySend(DonationSummary(totalAmount = total)).isSuccess
                }
            }
        awaitClose { subscription.remove() }
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
