package com.example.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Serializes and debounces non-blocking IO writes for Citra notes (.md files)
 * and device snapshots so UI navigation/typing never stalls on disk IO and
 * concurrent writes to the same note cannot race or lose data.
 */
object CitraPersistenceQueue {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keyMutexes = ConcurrentHashMap<String, Mutex>()
    private val pendingMarkdownJobs = ConcurrentHashMap<String, Job>()
    private val snapshotMutex = Mutex()
    private val pendingSnapshotJob = AtomicReference<Job?>(null)

    private fun resolveKey(uuid: String?, title: String?): String {
        val cleanUuid = uuid?.trim().orEmpty()
        if (cleanUuid.isNotEmpty()) return "uuid:$cleanUuid"
        return "title:${title.orEmpty().trim().lowercase()}"
    }

    private fun mutexForKey(key: String): Mutex {
        return keyMutexes.getOrPut(key) { Mutex() }
    }

    /**
     * Schedules a debounced background write to Documents/Citra Docs/.
     * Any earlier pending write for the same note is cancelled and replaced.
     */
    fun scheduleMarkdownSave(
        context: Context,
        title: String,
        content: String,
        uuid: String? = null,
        oldTitle: String? = null,
        debounceMs: Long = 850L
    ) {
        val appContext = context.applicationContext
        val key = resolveKey(uuid, title)
        val mutex = mutexForKey(key)

        val job = ioScope.launch {
            delay(debounceMs)
            withContext(NonCancellable) {
                mutex.withLock {
                    CitraDocsFileManager.saveNoteMarkdown(
                        context = appContext,
                        title = title,
                        content = content,
                        uuid = uuid,
                        oldTitle = oldTitle
                    )
                }
            }
        }
        pendingMarkdownJobs.put(key, job)?.cancel()
    }

    /**
     * Cancels any pending debounced write for a note (e.g., before archiving or deleting).
     */
    fun cancelPendingMarkdownSave(uuid: String?, title: String?) {
        val key = resolveKey(uuid, title)
        pendingMarkdownJobs.remove(key)?.cancel()
    }

    /**
     * Immediately flushes the latest markdown content to disk on Dispatchers.IO
     * inside NonCancellable context, cancelling any older debounced job for the same note.
     */
    suspend fun flushMarkdownSaveNow(
        context: Context,
        title: String,
        content: String,
        uuid: String? = null,
        oldTitle: String? = null
    ): File? = withContext(NonCancellable + Dispatchers.IO) {
        val appContext = context.applicationContext
        val key = resolveKey(uuid, title)
        pendingMarkdownJobs.remove(key)?.cancel()
        val mutex = mutexForKey(key)
        mutex.withLock {
            CitraDocsFileManager.saveNoteMarkdown(
                context = appContext,
                title = title,
                content = content,
                uuid = uuid,
                oldTitle = oldTitle
            )
        }
    }

    /**
     * Non-blocking fire-and-forget immediate flush on the queue's NonCancellable IO scope.
     * Ideal for lifecycle callbacks (ON_PAUSE / ON_STOP / BackHandler) where UI must close immediately.
     */
    fun flushMarkdownSaveAsync(
        context: Context,
        title: String,
        content: String,
        uuid: String? = null,
        oldTitle: String? = null
    ) {
        val appContext = context.applicationContext
        val key = resolveKey(uuid, title)
        pendingMarkdownJobs.remove(key)?.cancel()
        val mutex = mutexForKey(key)
        ioScope.launch {
            withContext(NonCancellable) {
                mutex.withLock {
                    CitraDocsFileManager.saveNoteMarkdown(
                        context = appContext,
                        title = title,
                        content = content,
                        uuid = uuid,
                        oldTitle = oldTitle
                    )
                }
            }
        }
    }

    /**
     * Schedules a serialized, debounced snapshot task so rapid bursts of edits coalesce cleanly.
     */
    fun scheduleSnapshotSave(
        debounceMs: Long = 1500L,
        action: suspend () -> Unit
    ) {
        val newJob = ioScope.launch {
            delay(debounceMs)
            withContext(NonCancellable) {
                snapshotMutex.withLock {
                    try {
                        action()
                    } catch (_: Exception) {}
                }
            }
        }
        pendingSnapshotJob.getAndSet(newJob)?.cancel()
    }

    /**
     * Flushes a snapshot task immediately on Dispatchers.IO without debouncing.
     */
    fun flushSnapshotAsync(action: suspend () -> Unit) {
        pendingSnapshotJob.getAndSet(null)?.cancel()
        ioScope.launch {
            withContext(NonCancellable) {
                snapshotMutex.withLock {
                    try {
                        action()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
