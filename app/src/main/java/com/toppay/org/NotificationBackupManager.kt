package com.toppay.org

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object NotificationBackupManager {
    private const val PREFERENCES = "notification_backup_preferences"
    private const val ENABLED = "cloud_backup_enabled"

    fun isEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, enabled)
            .apply()
    }

    fun sync(context: Context, onResult: ((Result<Int>) -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult?.invoke(Result.failure(IllegalStateException("No signed-in user")))
            return
        }

        val notifications = NotificationLogStorage.entries(context)
        FirebaseFirestore.getInstance()
            .document("users/${user.uid}/private/notification")
            .set(
                mapOf(
                    "notifications" to notifications,
                    "count" to notifications.size,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onResult?.invoke(Result.success(notifications.size)) }
            .addOnFailureListener { error -> onResult?.invoke(Result.failure(error)) }
    }
}
