package com.freelapp.components.biller.android.impl

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.freelapp.components.biller.android.impl.ktx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

@ExperimentalCoroutinesApi
internal class ConnectionManagerImpl(
    private val billingClient: BillingClient
) : DefaultLifecycleObserver {

    val connectionStatus = MutableSharedFlow<Int>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var connectionManagerJob: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        connectionManagerJob = createConnectionManagerJob(owner)
    }

    override fun onStop(owner: LifecycleOwner) {
        connectionManagerJob?.cancel()
        connectionManagerJob = null
    }

    private fun createConnectionManagerJob(owner: LifecycleOwner): Job =
        owner.lifecycleScope.launch {
            while (true) {
                runCatching {
                    billingClient
                        .connectionAsFlow()
                        .onEach { connectionStatus.tryEmit(it) }
                        .onCompletion { delay(RETRY_MILLIS) }
                        .collect()
                }
            }
        }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private companion object {
        private const val RETRY_MILLIS = 3000L
    }
}