/**
 * MobileCLI Production - Terminal Service
 *
 * Background service that manages terminal sessions. This service:
 * - Keeps sessions alive when the app is backgrounded
 * - Manages wake lock for long-running tasks
 * - Provides the session list to activities
 *
 * @since v162 (Production rebuild)
 */
package com.termux.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.termux.MainActivity
import com.termux.R
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * Background service for terminal session management.
 *
 * This service runs as a foreground service to keep terminal sessions
 * alive even when the app is in the background. It manages:
 * - Active terminal sessions
 * - Wake lock for keeping CPU awake during long operations
 * - Notification showing active session count
 */
class TermuxService : Service() {

    companion object {
        private const val TAG = "TermuxService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "termux_service"
        private const val NOTIFICATION_CHANNEL_NAME = "Terminal Service"
        private const val SESSION_TRANSCRIPT_ROWS = 2000

        // Action constants for service commands
        const val ACTION_STOP_SERVICE = "com.termux.service.STOP"
        const val ACTION_ACQUIRE_WAKE_LOCK = "com.termux.service.WAKE_LOCK"
        const val ACTION_RELEASE_WAKE_LOCK = "com.termux.service.WAKE_UNLOCK"
    }

    // Session management
    private val sessions = mutableListOf<TerminalSession>()
    private var sessionClient: TerminalSessionClient? = null

    // Wake lock for keeping CPU awake
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquired = false

    // Binder for activity connection
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TermuxService = this@TermuxService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "TermuxService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "TermuxService onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ACQUIRE_WAKE_LOCK -> {
                acquireWakeLock()
            }
            ACTION_RELEASE_WAKE_LOCK -> {
                releaseWakeLock()
            }
        }

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "TermuxService bound")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "TermuxService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "TermuxService destroyed")
        releaseWakeLock()

        // Kill all sessions
        sessions.forEach { session ->
            try {
                session.finishIfRunning()
            } catch (e: Exception) {
                Log.e(TAG, "Error finishing session", e)
            }
        }
        sessions.clear()

        super.onDestroy()
    }

    // ==========================================
    // Session Management
    // ==========================================

    /**
     * Sets the session client to receive session callbacks.
     */
    fun setSessionClient(client: TerminalSessionClient?) {
        this.sessionClient = client
        // Update existing sessions with the new client
        sessions.forEach { session ->
            session.updateTerminalSessionClient(client)
        }
    }

    /**
     * Gets the list of active sessions.
     */
    fun getSessions(): List<TerminalSession> = sessions.toList()

    /**
     * Gets the number of active sessions.
     */
    fun getSessionCount(): Int = sessions.size

    /**
     * Adds a session to the service.
     */
    fun addSession(session: TerminalSession) {
        if (!sessions.contains(session)) {
            sessions.add(session)
            Log.i(TAG, "Session added, count: ${sessions.size}")
            updateNotification()
        }
    }

    /**
     * Removes a session from the service.
     */
    fun removeSession(session: TerminalSession) {
        if (sessions.remove(session)) {
            Log.i(TAG, "Session removed, count: ${sessions.size}")
            updateNotification()
        }
    }

    /**
     * Creates a new terminal session.
     *
     * @param executablePath Path to the executable (e.g., /data/data/com.termux/files/usr/bin/bash)
     * @param cwd Working directory
     * @param args Arguments to pass to the executable
     * @param env Environment variables
     * @param client Session client for callbacks
     * @return The created TerminalSession
     */
    fun createSession(
        executablePath: String,
        cwd: String,
        args: Array<String>,
        env: Array<String>,
        client: TerminalSessionClient?
    ): TerminalSession {
        val effectiveClient = client ?: sessionClient

        val session = TerminalSession(
            executablePath,
            cwd,
            args,
            env,
            SESSION_TRANSCRIPT_ROWS,
            effectiveClient
        )

        addSession(session)
        return session
    }

    // ==========================================
    // Wake Lock Management
    // ==========================================

    /**
     * Acquires a wake lock to keep the CPU awake.
     * Useful for long-running tasks like AI conversations.
     */
    fun acquireWakeLock() {
        if (wakeLockAcquired) {
            Log.d(TAG, "Wake lock already acquired")
            return
        }

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MobileCLI::TerminalWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes max
            }
            wakeLockAcquired = true
            Log.i(TAG, "Wake lock acquired")
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    /**
     * Releases the wake lock.
     */
    fun releaseWakeLock() {
        if (!wakeLockAcquired) {
            return
        }

        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            wakeLockAcquired = false
            Log.i(TAG, "Wake lock released")
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    /**
     * Returns whether the wake lock is currently held.
     */
    fun isWakeLockAcquired(): Boolean = wakeLockAcquired

    // ==========================================
    // Notification Management
    // ==========================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MobileCLI terminal service notification"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Intent to open the app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop the service
        val stopIntent = Intent(this, TermuxService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification content
        val sessionCount = sessions.size
        val contentText = buildString {
            append("$sessionCount session")
            if (sessionCount != 1) append("s")
            append(" active")
            if (wakeLockAcquired) append(" (wake lock)")
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MobileCLI")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification())
    }
}
