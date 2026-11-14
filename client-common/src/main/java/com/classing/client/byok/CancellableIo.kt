package com.classing.client.byok

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Closing resources interrupts blocking Bluetooth and HTTP operations on cancellation. */
internal suspend fun <T> cancellableIo(block: (registerClose: (() -> Unit) -> Unit) -> T): T = suspendCancellableCoroutine { cont ->
    val lock=Any();var closed=false;val closures=mutableListOf<() -> Unit>()
    fun closeAll() { val pending=synchronized(lock) { closed=true;closures.toList().also {closures.clear()} };pending.forEach {runCatching(it)} }
    val worker = Thread({
        try {
            val result=block { close -> synchronized(lock) { if(closed) { runCatching(close);throw java.io.IOException("Operation cancelled") };closures+=close } }
            if(cont.isActive) cont.resume(result)
        } catch(e: Exception) { if(cont.isActive) cont.resumeWithException(e) }
        finally { closeAll() }
    },"classing-private-io")
    cont.invokeOnCancellation { closeAll();worker.interrupt() }
    worker.start()
}
