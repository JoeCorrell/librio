package com.librio.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.librio.MainActivity
import com.librio.R

/**
 * Foreground service for audiobook playback with media notification
 * Provides playback controls: Previous, Play/Pause, Next, and Shuffle
 */
class PlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null
    private val notificationId = 1001
    private val channelId = "audiobook_playback_channel"

    companion object {
        const val ACTION_PLAY_PAUSE = "com.librio.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.librio.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.librio.ACTION_NEXT"
        const val ACTION_STOP = "com.librio.ACTION_STOP"

        // Broadcast actions for chapter navigation
        const val BROADCAST_PREVIOUS_CHAPTER = "com.librio.BROADCAST_PREVIOUS_CHAPTER"
        const val BROADCAST_NEXT_CHAPTER = "com.librio.BROADCAST_NEXT_CHAPTER"

        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Get the shared player and create media session
        val player = SharedMusicPlayer.acquire(applicationContext)
        mediaSession = MediaSession.Builder(this, player).build()

        // Start as foreground service
        startForeground(notificationId, createNotification(player))

        // Listen for playback state changes to update notification
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Always get the current player from mediaSession to avoid stale references
                val currentPlayer = mediaSession?.player ?: return
                updateNotification(currentPlayer)
                // Note: Do NOT call stopForeground() here - it causes Android to kill the service
                // The service should stay in foreground as long as it exists
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                val currentPlayer = mediaSession?.player ?: return
                updateNotification(currentPlayer)
            }
        }
        player.addListener(listener)
        playerListener = listener
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle intents from notification actions
        intent?.action?.let { action ->
            // Try mediaSession player first, fall back to SharedMusicPlayer
            val player = mediaSession?.player ?: run {
                // MediaSession may have been released, try to get player directly
                try {
                    SharedMusicPlayer.acquire(applicationContext)
                } catch (_: Exception) {
                    return START_STICKY
                }
            }

            try {
                when (action) {
                    ACTION_PLAY_PAUSE -> {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        updateNotification(player)
                    }
                    ACTION_PREVIOUS -> {
                        // Seek backward 10 seconds (or to start if less than 10s in)
                        val newPosition = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                        player.seekTo(newPosition)
                        updateNotification(player)
                        // Also send broadcast in case AudiobookPlayer is alive for chapter handling
                        sendBroadcast(Intent(BROADCAST_PREVIOUS_CHAPTER).apply {
                            setPackage(packageName)
                        })
                    }
                    ACTION_NEXT -> {
                        // Seek forward 30 seconds (or to end if near end)
                        val duration = player.duration.coerceAtLeast(0L)
                        val newPosition = (player.currentPosition + 30_000L).coerceAtMost(duration)
                        player.seekTo(newPosition)
                        updateNotification(player)
                        // Also send broadcast in case AudiobookPlayer is alive for chapter handling
                        sendBroadcast(Intent(BROADCAST_NEXT_CHAPTER).apply {
                            setPackage(packageName)
                        })
                    }
                    ACTION_STOP -> {
                        player.pause()
                        stopSelf()
                    }
                }
            } catch (_: Exception) {
                // Player may be in invalid state, ignore
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // When app is swiped from recents, stop the service if not playing
        val player = mediaSession?.player
        if (player == null || !player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            playerListener?.let { listener ->
                mediaSession?.player?.removeListener(listener)
            }
        } catch (_: Exception) {
            // Player may already be released
        }
        playerListener = null

        try {
            mediaSession?.release()
        } catch (_: Exception) {
            // MediaSession may already be released
        }
        mediaSession = null

        try {
            SharedMusicPlayer.release()
        } catch (_: Exception) {
            // Already released
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audiobook Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audiobook playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(player: Player): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (player.isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val playPauseTitle = if (player.isPlaying) "Pause" else "Play"

        val mediaMetadata = player.currentMediaItem?.mediaMetadata
        val title = mediaMetadata?.title?.toString() ?: "Audiobook"
        val artist = mediaMetadata?.artist?.toString() ?: "Unknown"

        // Create notification with Previous, Play/Pause, and Next controls
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(player.isPlaying)
            .setShowWhen(false)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                createServicePendingIntent(ACTION_PREVIOUS)
            )
            .addAction(
                playPauseIcon,
                playPauseTitle,
                createServicePendingIntent(ACTION_PLAY_PAUSE)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                createServicePendingIntent(ACTION_NEXT)
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateNotification(player: Player) {
        try {
            val notification = createNotification(player)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(notificationId, notification)
        } catch (_: Exception) {
            // Player may be in an invalid state, ignore notification update
        }
    }

    private fun createServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
        }
        // Use distinct positive request codes for each action
        val requestCode = when (action) {
            ACTION_PLAY_PAUSE -> 1
            ACTION_PREVIOUS -> 2
            ACTION_NEXT -> 3
            ACTION_STOP -> 4
            else -> 0
        }
        // Use getForegroundService on Android O+ for foreground service intents
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
