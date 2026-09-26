package com.toppay.org

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object NotificationBackupManager {
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
