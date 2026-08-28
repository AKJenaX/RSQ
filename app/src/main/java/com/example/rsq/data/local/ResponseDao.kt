package com.example.rsq.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments ORDER BY updatedAt DESC")
    fun getAllAssignments(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE id = :id")
    fun getAssignmentById(id: String): Flow<AssignmentEntity?>

    @Query("SELECT * FROM assignments WHERE id = :id")
    suspend fun getAssignmentByIdOneShot(id: String): AssignmentEntity?

    @Query("SELECT * FROM assignments WHERE volunteerId = :volunteerId")
    fun getAssignmentsForVolunteer(volunteerId: String): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE reportId = :reportId")
    fun getAssignmentsForReport(reportId: String): Flow<List<AssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: AssignmentEntity)

    @Update
    suspend fun updateAssignment(assignment: AssignmentEntity)

    @Query("UPDATE assignments SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAssignmentStatus(id: String, status: String, updatedAt: Long)
}

@Dao
interface VolunteerDao {
    @Query("SELECT * FROM volunteers WHERE id = :id")
    fun getVolunteerById(id: String): Flow<VolunteerEntity?>

    @Query("SELECT * FROM volunteers WHERE firebaseUid = :uid")
    fun getVolunteerByFirebaseUid(uid: String): Flow<VolunteerEntity?>

    @Query("SELECT * FROM volunteers WHERE firebaseUid = :uid")
    suspend fun getVolunteerByFirebaseUidOneShot(uid: String): VolunteerEntity?

    @Query("SELECT * FROM volunteers")
    fun getAllVolunteers(): Flow<List<VolunteerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVolunteer(volunteer: VolunteerEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE recipientId = :recipientId ORDER BY timestamp DESC")
    fun getNotificationsForRecipient(recipientId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE recipientId = :recipientId AND isRead = 0")
    fun getUnreadCount(recipientId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE recipientId = :recipientId")
    suspend fun markAllAsRead(recipientId: String)
}
