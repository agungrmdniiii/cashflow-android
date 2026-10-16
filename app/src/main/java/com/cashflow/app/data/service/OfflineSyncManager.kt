package com.cashflow.app.data.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object OfflineSyncManager {

    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    private var isInitialized = false

    fun initialize(context: Context, onNetworkRestored: (suspend () -> Unit)? = null) {
        if (isInitialized) return
        isInitialized = true

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        // Initial check
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val initialConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        _isOnline.value = initialConnected

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    if (onNetworkRestored != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                onNetworkRestored()
                            } catch (_: Exception) {}
                        }
                    }
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                }
            })
        } catch (_: Exception) {
            // Fallback for restricted sandboxes/emulators
        }
    }
}
