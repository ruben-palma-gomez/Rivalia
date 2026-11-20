package com.example.rivalia

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sincroniza las partidas apuntadas sin conexión.
 * Se ejecuta al abrir la app, al apuntar una partida y cada vez que vuelve la red.
 */
class SyncManager(
    context: Context,
    private val repository: RivaliaRepository,
    private val scope: CoroutineScope
) {
    private val local = repository.local
    private val mutex = Mutex()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    init {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                requestSync()
            }
        })
    }

    fun requestSync() {
        scope.launch { sync() }
    }

    suspend fun sync() {
        if (repository.uid == null) return
        mutex.withLock {
            _syncing.value = true
            try {
                val pending = withContext(Dispatchers.IO) { local.pendingMatches() }
                for (p in pending) {
                    try {
                        repository.commitPending(p)
                        withContext(Dispatchers.IO) { local.deletePending(p.localId) }
                        _lastError.value = null
                    } catch (e: FirebaseFirestoreException) {
                        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                            break // sin conexión: se reintenta cuando vuelva la red
                        }
                        withContext(Dispatchers.IO) { local.markPendingError(p.localId, e.code.name) }
                        _lastError.value = "No se pudo subir una partida (${e.code.name})"
                    } catch (e: Exception) {
                        _lastError.value = e.message
                        break
                    }
                }
            } finally {
                _syncing.value = false
            }
        }
    }
}
