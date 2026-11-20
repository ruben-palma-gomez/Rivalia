package com.example.rivalia

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Clase Application: crea una sola vez las dependencias de la app
 * (inyección de dependencias manual, sin librerías).
 */
class RivaliaApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var local: LocalDatabase
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var repository: RivaliaRepository
        private set
    lateinit var sync: SyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        val firebaseAuth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance() // caché sin conexión activada por defecto
        local = LocalDatabase(this)
        auth = AuthRepository(firebaseAuth, firestore)
        repository = RivaliaRepository(firestore, firebaseAuth, local)
        sync = SyncManager(this, repository, appScope)

        // Cada vez que alguien inicia sesión, se suben las partidas pendientes
        appScope.launch {
            auth.user.map { it?.uid }.distinctUntilChanged().filterNotNull().collect { sync.requestSync() }
        }
    }
}
