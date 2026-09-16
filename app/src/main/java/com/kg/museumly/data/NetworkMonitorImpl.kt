package com.kg.museumly.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.kg.museumly.domain.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitorImpl @Inject constructor(
    // plain paramater, not a val. it is not stored.
    @ApplicationContext context: Context
) : NetworkMonitor {

    /**
     * ConnectivityManager is your app's handle to Android's system
     * connectivity service, which runs in a separate system process.
     * getSystemService takes a string key and returns Any, which is why the cast is needed.
     */
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    // context.getSystemService(ConnectivityManager::class.java)
    /**
     * Lives as long as the process. A @Singleton has no screen whose
     * death should cancel it. SupervisorJob so one failure doesn't
     * cancel the whole scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * INTERNET: the network claims it can reach the internet.
     * VALIDATED: Android checked, and it actually does.
     * Captive-portal Wi-Fi has the first but not the second.
     */
    private fun isValidated(caps: NetworkCapabilities?) : Boolean
    {
        if(caps == null)
        {
            return false
        }
        // does network reach the internet
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        // testing if internet works.
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return hasInternet && validated
    }

    private fun currentlyOnline() : Boolean
    {
        // activeNetwork is the default network, or null if there isn't one (airplane mode, or Wi-Fi and data both off).
        val network: Network? = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
        return isValidated(caps)
    }

    override val isOnline: StateFlow<Boolean> = callbackFlow {
        /**
         * So the full lifetime is: collection starts, the callback is registered,
         * values flow, collection is cancelled,
         * the callback is unregistered. Registration and cleanup are tied together automatically.
         */
        val callback = object : ConnectivityManager.NetworkCallback() {
            /**
             * This is called when the default network's capabilities change. The important case is VALIDATED
             * being added once Android's test succeeds, or removed if it fails.
             *
             * The sequence when a network connects is:
             * onAvailable: "a network is now the default." Validation usually hasn't finished yet.
             * onCapabilitiesChanged: often called several times as flags update, eventually including VALIDATED.
             *
             * That's why  onAvailable is not overriden. It would say "online" too early.
             * onCapabilitiesChanged has the actual flags.
             */
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val online: Boolean = isValidated(caps = networkCapabilities)
                Log.d("NET", "capabilitiesChanged online=$online")
                trySend(online)
            }

            /**
             * This is called when the default network goes away.
             * Sending false directly is correct here because I registered with registerDefaultNetworkCallback.
             */
            override fun onLost(network: Network) {
                Log.d("NET" , "LOST")
                trySend(false)
            }
        }
        /**
         * This means "only tell me about whichever network is the default at the moment."
         */
        connectivityManager.registerDefaultNetworkCallback(callback)

        // With no network at all, no callback ever fires.
        // This covers that case.
        // thread safe.
        trySend(currentlyOnline())
        /**
         * It suspends the producer, keeping it alive, for as long as the collector is still collecting.
         * When the collector is cancelled, it runs your block. Here, that unregisters the callback.
         * scope wont be cancelled because this block wont fire it is needed becasue if it
         * is not written, flow will throw
         */
        awaitClose {
            Log.d("NET", "unregister")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
        /**
         * onCapabilitiesChange is fired often. So i can send same values again and again.
         * distinctUntilChanged prevents it by checking previous value. if equal, it dont emit.
         */
        .distinctUntilChanged()
        /**
         * change cold flow to hot. eagerly because i need to start collecting immediately.
         */
        .stateIn(scope, SharingStarted.Eagerly, currentlyOnline())
}