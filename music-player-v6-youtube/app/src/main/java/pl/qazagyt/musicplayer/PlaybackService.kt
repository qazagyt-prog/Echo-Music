package pl.qazagyt.musicplayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
class PlaybackService: MediaSessionService() {
 private lateinit var player: ExoPlayer
 private var session: MediaSession?=null
 override fun onCreate(){super.onCreate();player=ExoPlayer.Builder(this).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true).setHandleAudioBecomingNoisy(true).build();session=MediaSession.Builder(this,player).build()}
 override fun onGetSession(controllerInfo:MediaSession.ControllerInfo)=session
 override fun onDestroy(){session?.release();player.release();super.onDestroy()}
}