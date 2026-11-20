package com.example.rivalia

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Registro, inicio y cierre de sesión con Firebase Authentication (correo y contraseña). */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    init {
        auth.addAuthStateListener { _user.value = it.currentUser }
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /** Crea la cuenta y el perfil público (alias) en users/{uid}. */
    suspend fun register(alias: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("No se pudo crear la cuenta")
        db.collection("users").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "alias" to alias.trim(),
                "email" to user.email,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        runCatching { user.sendEmailVerification().await() }
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun logout() = auth.signOut()
}
