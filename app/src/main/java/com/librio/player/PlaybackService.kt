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
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.librio.MainActivity
import com.librio.R

/**
 * Foreground service for playback with media notification
 * Provides 5 controls: Shuffle, Previous, Play/Pause, Next, Repeat
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
        const val ACTION_SHUFFLE = "com.librio.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.librio.ACTION_REPEAT"
        const val ACTION_STOP = "com.librio.ACTION_STOP"

        // Broadcast actions for chapter navigation (within current audiobook)
        const val BROADCAST_PREVIOUS_CHAPTER = "com.librio.BROADCAST_PREVIOUS_CHAPTER"
        const val BROADCAST_NEXT_CHAPTER = "com.librio.BROADCAST_NEXT_CHAPTER"

        // Broadcast actions for audiobook playlist navigation (to next/previous audiobook)
        const val BROADCAST_PREVIOUS_AUDIOBOOK = "com.librio.BROADCAST_PREVIOUS_AUDIOBOOK"
        const val BROADCAST_NEXT_AUDIOBOOK = "com.librio.BROADCAST_NEXT_AUDIOBOOK"

        // Callbacks for playlist navigation (set by MainActivity)
        var onNextAudiobook: (() -> Unit)? = null
        var onPreviousAudiobook: (() -> Unit)? = null

        // Callbacks for music playlist navigation
        var onNextMusic: (() -> Unit)? = null
        var onPreviousMusic: (() -> Unit)? = null

        // Shuffle/repeat callbacks and state (set by MainActivity)
        var onShuffleToggle: (() -> Unit)? = null
        var onRepeatToggle: (() -> Unit)? = null
        var isShuffleOn: () -> Boolean = { false }
        var isRepeatOn: () -> Boolean = { false }

        // Track what type of content is currently active
        var currentActiveType: String? = null // "AUDIOBOOK" or "MUSIC"

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

        // Get the shared player and wrap it with ForwardingPlayer to handle skip commands
        val actualPlayer = SharedMusicPlayer.acquire(applicationContext)

        // ForwardingPlayer intercepts skip commands from headphones/Bluetooth and handles them
        val forwardingPlayer = object : ForwardingPlayer(actualPlayer) {
            override fun seekToNext() { handleNextAction(wrappedPlayer) }
            override fun seekToPrevious() { handlePreviousAction(wrappedPlayer) }
            override fun seekToNextMediaItem() { handleNextAction(wrappedPlayer) }
            override fun seekToPreviousMediaItem() { handlePreviousAction(wrappedPlayer) }
            // Advertise skip commands so Bluetooth/media buttons know they're available
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT).add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS).add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
        }

        // Create media session with the forwarding player
        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()

        // Start as foreground service
        startForeground(notificationId, createNotification(actualPlayer))

        // Listen for playback state changes to update notification
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val currentPlayer = mediaSession?.player ?: return
                updateNotification(currentPlayer)
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                val currentPlayer = mediaSession?.player ?: return
                updateNotification(currentPlayer)
            }
        }
        actualPlayer.addListener(listener)
        playerListener = listener
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            val player = mediaSession?.player ?: run {
                try {
                    SharedMusicPlayer.acquire(applicationContext)
                } catch (_: Exception) {
                    return START_STICKY
                }
            }

            try {
                when (action) {
                    ACTION_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    ACTION_PREVIOUS -> handlePreviousAction(player)
                    ACTION_NEXT -> handleNextAction(player)
                    ACTION_SHUFFLE -> onShuffleToggle?.invoke()
                    ACTION_REPEAT -> onRepeatToggle?.invoke()
                    ACTION_STOP -> {
                        player.pause()
                        stopSelf()
                    }
                }
                updateNotification(player)
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
        } catch (_: Exception) {}
        playerListener = null

        try {
            mediaSession?.release()
        } catch (_: Exception) {}
        mediaSession = null

        try {
            SharedMusicPlayer.release()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Librio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createNotification(player: Player): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val shuffleIcon = if (isShuffleOn()) android.R.drawable.ic_menu_rotate else android.R.drawable.ic_menu_sort_by_size
        val repeatIcon = if (isRepeatOn()) android.R.drawable.ic_menu_revert else android.R.drawable.ic_menu_recent_history

        val mediaMetadata = player.currentMediaItem?.mediaMetadata
        val title = mediaMetadata?.title?.toString() ?: "Librio"
        val artist = mediaMetadata?.artist?.toString() ?: "Ready to play"

        // 5 actions: Shuffle, Previous, Play/Pause, Next, Repeat
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(player.isPlaying)
            .setShowWhen(false)
            .addAction(shuffleIcon, if (isShuffleOn()) "Shuffle On" else "Shuffle Off", createServicePendingIntent(ACTION_SHUFFLE))
            .addAction(android.R.drawable.ic_media_previous, "Previous", createServicePendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseIcon, if (player.isPlaying) "Pause" else "Play", createServicePendingIntent(ACTION_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, "Next", createServicePendingIntent(ACTION_NEXT))
            .addAction(repeatIcon, if (isRepeatOn()) "Repeat On" else "Repeat Off", createServicePendingIntent(ACTION_REPEAT))
            .setDeleteIntent(createServicePendingIntent(ACTION_STOP))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val session = mediaSession
        if (session != null) {
            builder.setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(1, 2, 3) // Previous, Play/Pause, Next in compact
            )
        }

        return builder.build()
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
        val requestCode = when (action) {
            ACTION_PLAY_PAUSE -> 1
            ACTION_PREVIOUS -> 2
            ACTION_NEXT -> 3
            ACTION_STOP -> 4
            ACTION_SHUFFLE -> 5
            ACTION_REPEAT -> 6
            else -> 0
        }
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

    /**
     * Handle next action from notification buttons or headphone media buttons
     */
    private fun handleNextAction(player: Player) {
        try {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                when (currentActiveType) {
                    "MUSIC" -> onNextMusic?.invoke()
                    "AUDIOBOOK" -> {
                        onNextAudiobook?.invoke()
                        sendBroadcast(Intent(BROADCAST_NEXT_CHAPTER).apply {
                            setPackage(packageName)
                        })
                    }
                    else -> onNextAudiobook?.invoke()
                }
            }
            updateNotification(player)
        } catch (_: Exception) {}
    }

    /**
     * Handle previous action from notification buttons or headphone media buttons
     */
    private fun handlePreviousAction(player: Player) {
        try {
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                when (currentActiveType) {
                    "MUSIC" -> onPreviousMusic?.invoke()
                    "AUDIOBOOK" -> {
                        onPreviousAudiobook?.invoke()
                        sendBroadcast(Intent(BROADCAST_PREVIOUS_CHAPTER).apply {
                            setPackage(packageName)
                        })
                    }
                    else -> onPreviousAudiobook?.invoke()
                }
            }
            updateNotification(player)
        } catch (_: Exception) {}
    }
}
