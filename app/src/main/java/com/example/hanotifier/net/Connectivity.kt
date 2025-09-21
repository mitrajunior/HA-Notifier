package com.example.hanotifier.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object Connectivity {
  fun isConnected(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  enum class NetworkType {
    WIFI,
    CELLULAR,
    OTHER,
    NONE
  }

  fun observeNetworkType(ctx: Context): Flow<NetworkType> = callbackFlow {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun resolveType(): NetworkType {
      val net = cm.activeNetwork ?: return NetworkType.NONE
      val caps = cm.getNetworkCapabilities(net) ?: return NetworkType.NONE
      return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
          caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
        else -> NetworkType.OTHER
      }
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: android.net.Network) {
        trySend(resolveType())
      }

      override fun onLost(network: android.net.Network) {
        trySend(resolveType())
      }

      override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
        trySend(resolveType())
      }
    }

    trySend(resolveType())
    cm.registerDefaultNetworkCallback(callback)

    awaitClose { cm.unregisterNetworkCallback(callback) }
  }.distinctUntilChanged()
}
