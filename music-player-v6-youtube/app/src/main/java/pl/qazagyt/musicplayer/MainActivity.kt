package pl.qazagyt.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executors

class MainActivity: AppCompatActivity() {
 data class Song(val id:Long,val title:String,val artist:String,val album:String,val uri:android.net.Uri,val duration:Long)
 private var controller:MediaController?=null
 private val songs=mutableListOf<Song>()
 private val visible=mutableListOf<Song>()
 private lateinit var adapter:ArrayAdapter<String>
 private lateinit var search:EditText
 private lateinit var now:TextView
 private lateinit var play:Button
 private val prefs by lazy { getSharedPreferences("player", Context.MODE_PRIVATE) }
 private val executor=Executors.newSingleThreadExecutor()
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ load() }

 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  buildUi();connect();requestPermission()
}

 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,20,20,10);setBackgroundColor(Color.rgb(8,9,11))}
  val title=TextView(this).apply{text="MUSIC PLAYER V16";textSize=28f;setTextColor(Color.WHITE);setPadding(0,0,0,12)}
  search=EditText(this).apply{hint="Szukaj utworu, wykonawcy lub albumu…";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setSingleLine(true)}
  now=TextView(this).apply{text="Brak utworu";textSize=17f;setTextColor(Color.WHITE);setPadding(0,18,0,8)}
  val controls=LinearLayout(this).apply{gravity=Gravity.CENTER}
  fun button(t:String,a:()->Unit)=Button(this).apply{text=t;setOnClickListener{a()}}
  controls.addView(button("⏮"){controller?.seekToPrevious()})
  play=button("▶"){controller?.let{if(it.isPlaying)it.pause()else it.play()}};controls.addView(play)
  controls.addView(button("⏭"){controller?.seekToNext()})
  controls.addView(button("🔀"){controller?.shuffleModeEnabled=!(controller?.shuffleModeEnabled?:false)})
  controls.addView(button("🔁"){controller?.let{it.repeatMode=if(it.repeatMode==Player.REPEAT_MODE_OFF)Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF}})
  // Two rows so the YouTube control is always visible on narrow phones.
  val tabs=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val row1=LinearLayout(this).apply{gravity=Gravity.CENTER}
  row1.addView(button("UTWORY"){filter("")})
  row1.addView(button("♥ ULUBIONE"){filter("fav")})
  row1.addView(button("ODŚWIEŻ"){load()})
  row1.addView(button("▶ PEŁNY"){showPlayer()})
  val row2=LinearLayout(this).apply{gravity=Gravity.CENTER}
  row2.addView(button("▶ YOUTUBE AUDIO"){showYouTube()})
  tabs.addView(row1)
  tabs.addView(row2)
  adapter=ArrayAdapter(this,android.R.layout.simple_list_item_2,android.R.id.text1,ArrayList<String>())
  val list=ListView(this).apply{adapter=this@MainActivity.adapter;setOnItemClickListener{_,_,p,_->playSong(visible[p])}}
  search.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}
   override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){filter(s?.toString()?:"")}
   override fun afterTextChanged(e:android.text.Editable?){}
  })
  root.addView(title);root.addView(search);root.addView(now);root.addView(controls);root.addView(tabs);root.addView(list,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
 }

 private fun connect(){
  val token=SessionToken(this,ComponentName(this,PlaybackService::class.java))
  val f=MediaController.Builder(this,token).buildAsync()
  f.addListener({
   controller=f.get()
   controller?.addListener(object:Player.Listener{
    override fun onIsPlayingChanged(v:Boolean){play.text=if(v)"⏸" else "▶"}
    override fun onMediaItemTransition(item:MediaItem?,reason:Int){
     val t=item?.mediaMetadata?.title?.toString()?:"Brak utworu"
     val a=item?.mediaMetadata?.artist?.toString()?:""
     now.text=t+" — "+a
    }
   })
   load()
  },ContextCompat.getMainExecutor(this))
 }

 private fun requestPermission(){
  val p=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
  if(ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED)load() else permission.launch(p)
 }

 private fun load(){
  val c=controller?:return
  songs.clear()
  val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  val proj=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION)
  contentResolver.query(base,proj,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" ASC")?.use{q->
   while(q.moveToNext()){
    val id=q.getLong(0)
    songs+=Song(id,q.getString(1)?:"Nieznany",q.getString(2)?:"Nieznany",q.getString(3)?:"Nieznany album",ContentUris.withAppendedId(base,id),q.getLong(4))
   }
  }
  c.setMediaItems(songs.map{MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build()).build()})
  c.prepare();filter("")
 }

 private fun filter(q:String){
  visible.clear()
  val l=if(q=="fav")songs.filter{prefs.getBoolean("fav_"+it.id,false)} else songs.filter{q.isBlank()||it.title.contains(q,true)||it.artist.contains(q,true)||it.album.contains(q,true)}
  visible.addAll(l);adapter.clear();adapter.addAll(l.map{it.title+"\n"+it.artist+" • "+it.album});adapter.notifyDataSetChanged()
 }

 private fun playSong(s:Song){val i=songs.indexOfFirst{it.id==s.id};if(i>=0){controller?.seekToDefaultPosition(i);controller?.play()}}

 private fun showPlayer(){
  val d=android.app.Dialog(this)
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(30,30,30,30);setBackgroundColor(Color.rgb(8,9,11))}
  val t=TextView(this).apply{text=now.text;textSize=25f;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
  val b=Button(this).apply{text="▶ / ⏸";setOnClickListener{controller?.let{if(it.isPlaying)it.pause()else it.play()}}}
  val close=Button(this).apply{text="ZAMKNIJ";setOnClickListener{d.dismiss()}}
  box.addView(t);box.addView(b);box.addView(close);d.setContentView(box);d.show()
 }

 private fun showYouTube(){
  val input=EditText(this).apply{hint="Wklej link YouTube…";setSingleLine(true)}
  AlertDialog.Builder(this)
   .setTitle("YouTube — AUDIO")
   .setMessage("V15 używa yt-dlp bezpośrednio na telefonie i wybiera najlepszy dostępny strumień audio.")
   .setView(input)
   .setNegativeButton("ANULUJ",null)
   .setPositiveButton("ODTWÓR"){_,_->playYouTubeDirect(input.text.toString())}
   .show()
 }

 private fun playYouTubeDirect(value:String){
  val id=extractYouTubeId(value)?:run{
   Toast.makeText(this,"Nieprawidłowy link YouTube",Toast.LENGTH_LONG).show();return
  }
  val url="https://www.youtube.com/watch?v=$id"
  Toast.makeText(this,"V16: przygotowuję audio z YouTube…",Toast.LENGTH_SHORT).show()
  executor.execute{
   try{
    dev.ffmpegkit_maintained.ytdlp.YtDlp.init(applicationContext)
    val dir=java.io.File(cacheDir,"youtube")
    dir.mkdirs()
    dir.listFiles()?.forEach{it.delete()}
    fun download(format:String, clients:String): java.io.File {
     val request=dev.ffmpegkit_maintained.ytdlp.YtDlpRequest(url)
      .setOutputTemplate(java.io.File(dir,"%(id)s_%(ext)s").absolutePath)
      .addOption("--no-playlist")
      .addOption("--no-part")
      .addOption("--no-mtime")
      .addOption("--restrict-filenames")
      .addOption("--no-warnings")
      .addOption("-f",format)
      .addOption("--extractor-args","youtube:player_client=$clients")
     val response=dev.ffmpegkit_maintained.ytdlp.YtDlp.execute(request,null)
     val files=dir.listFiles()?.filter{it.isFile && it.length()>1024}?.sortedByDescending{it.length()}?:emptyList()
     if(response.exitCode!=0 || files.isEmpty()) throw IllegalStateException("yt-dlp nie pobrał audio (kod "+response.exitCode+")")
     return files.first()
    }
    val file=try{
     download("bestaudio/best","web_embedded,android_vr")
    }catch(first:Exception){
     Log.w("MusicPlayerV16","Audio-only extraction failed; retrying with compatible A/V format",first)
     dir.listFiles()?.forEach{it.delete()}
     download("best[acodec!=none]/best","web_embedded,android_vr")
    }
    val title=file.nameWithoutExtension.replace('_',' ').ifBlank{"YouTube"}
    runOnUiThread{
     controller?.clearMediaItems()
     controller?.setMediaItem(
      MediaItem.Builder()
       .setMediaId("youtube:$id")
       .setUri(android.net.Uri.fromFile(file))
       .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("YouTube").build())
       .build()
     )
     controller?.prepare()
     controller?.play()
     now.text="$title — YouTube"
     Toast.makeText(this,"▶ YouTube — odtwarzanie",Toast.LENGTH_SHORT).show()
    }
   }catch(e:Exception){
    Log.e("MusicPlayerV16","yt-dlp failed",e)
    runOnUiThread{
     val msg=e.message?.replace("\n"," ")?.take(260)?:"nie udało się pobrać audio"
     Toast.makeText(this,"YouTube V16: $msg",Toast.LENGTH_LONG).show()
    }
   }
  }
 }

 private fun extractYouTubeId(value:String):String?{
  val v=value.trim()
  if(v.matches(Regex("^[A-Za-z0-9_-]{11}$")))return v
  val patterns=listOf(Regex("""(?:v=|youtu\.be/|youtube\.com/embed/|youtube\.com/shorts/)([A-Za-z0-9_-]{11})"""),Regex("""youtube\.com/watch/[^?]*[?&]v=([A-Za-z0-9_-]{11})"""))
  return patterns.firstNotNullOfOrNull{it.find(v)?.groupValues?.getOrNull(1)}
 }

 override fun onDestroy(){executor.shutdownNow();controller?.release();super.onDestroy()}
}
