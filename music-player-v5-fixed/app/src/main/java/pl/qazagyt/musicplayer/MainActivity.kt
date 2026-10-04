package pl.qazagyt.musicplayer

import android.Manifest
import android.app.Dialog
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

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
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ load() }

 override fun onCreate(b:Bundle?){ super.onCreate(b); buildUi(); connect(); requestPermission() }

 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,20,20,10);setBackgroundColor(Color.rgb(8,9,11))}
  val title=TextView(this).apply{text="MUSIC PLAYER V5";textSize=28f;setTextColor(Color.WHITE);setPadding(0,0,0,12)}
  search=EditText(this).apply{hint="Szukaj utworu, wykonawcy lub albumu…";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);singleLine=true}
  now=TextView(this).apply{text="Brak utworu";textSize=17f;setTextColor(Color.WHITE);setPadding(0,18,0,8)}
  val controls=LinearLayout(this).apply{gravity=Gravity.CENTER}
  fun button(t:String,a:()->Unit)=Button(this).apply{text=t;setOnClickListener{a()}}
  controls.addView(button("⏮"){controller?.seekToPrevious()})
  play=button("▶"){controller?.let{if(it.isPlaying)it.pause()else it.play()}}; controls.addView(play)
  controls.addView(button("⏭"){controller?.seekToNext()})
  controls.addView(button("🔀"){controller?.shuffleModeEnabled=!(controller?.shuffleModeEnabled?:false)})
  controls.addView(button("🔁"){controller?.let{it.repeatMode=if(it.repeatMode==Player.REPEAT_MODE_OFF)Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF}})
  val tabs=LinearLayout(this).apply{gravity=Gravity.CENTER}
  tabs.addView(button("UTWORY"){filter("")})
  tabs.addView(button("♥ ULUBIONE"){filter("fav")})
  tabs.addView(button("ODŚWIEŻ"){load()})
  tabs.addView(button("▶ PEŁNY"){showPlayer()})
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
  c.setMediaItems(songs.map{MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build()).build()})
  c.prepare()
  filter("")
 }

 private fun filter(q:String){
  visible.clear()
  val l=if(q=="fav")songs.filter{prefs.getBoolean("fav_"+it.id,false)} else songs.filter{q.isBlank()||it.title.contains(q,true)||it.artist.contains(q,true)||it.album.contains(q,true)}
  visible.addAll(l)
  adapter.clear()
  adapter.addAll(l.map{it.title+"\n"+it.artist+" • "+it.album})
  adapter.notifyDataSetChanged()
 }

 private fun playSong(s:Song){
  val i=songs.indexOfFirst{it.id==s.id}
  if(i>=0){controller?.seekToDefaultPosition(i);controller?.play()}
 }

 private fun showPlayer(){
  val d=Dialog(this)
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(30,30,30,30);setBackgroundColor(Color.rgb(8,9,11))}
  val t=TextView(this).apply{text=now.text;textSize=25f;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
  val b=Button(this).apply{text="▶ / ⏸";setOnClickListener{controller?.let{if(it.isPlaying)it.pause()else it.play()}}}
  val close=Button(this).apply{text="ZAMKNIJ";setOnClickListener{d.dismiss()}}
  box.addView(t);box.addView(b);box.addView(close);d.setContentView(box);d.show()
 }

 override fun onDestroy(){controller?.release();super.onDestroy()}
}
