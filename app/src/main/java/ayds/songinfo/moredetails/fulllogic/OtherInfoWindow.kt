package ayds.songinfo.moredetails.fulllogic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.room.Room.databaseBuilder
import ayds.songinfo.R
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.squareup.picasso.Picasso
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.IOException
import java.util.Locale
import androidx.core.net.toUri

var LASTFM_BASE_URL = "https://ws.audioscrobbler.com/2.0/"
var DATABASE_MARKUP = "[*]"
var ARTICLE_DB_NAME = "database-name-thename"
var LASTFM_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Lastfm_logo.svg/320px-Lastfm_logo.svg.png"

data class ArtistBiography(val artistName: String, val biography: String, val articleUrl: String)

class OtherInfoWindow : Activity() {

    private lateinit var articleTextView: TextView
    private lateinit var openUrlButton: Button
    private lateinit var lastFMImageView: ImageView

    private lateinit var articleDatabase: ArticleDatabase
    private lateinit var lastFMAPI: LastFMAPI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_info)

        initProperties()
        initArticleDatabase()
        initApi()
        getArtistInfoAsync()
    }

    private fun initProperties(){
        articleTextView = findViewById<TextView>(R.id.textPane1)
        openUrlButton = findViewById<Button>(R.id.openUrlButton1)
        lastFMImageView = findViewById<ImageView>(R.id.imageView1)
    }
    private fun initArticleDatabase(){
        articleDatabase = databaseBuilder(this, ArticleDatabase::class.java, ARTICLE_DB_NAME).build()
    }
    private fun initApi(){
        val retrofit = Retrofit.Builder()
            .baseUrl(LASTFM_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        lastFMAPI = retrofit.create<LastFMAPI>(LastFMAPI::class.java)
    }

    fun getArtistInfoAsync() {
        Thread {
            getArtistInfo()
        }.start()
    }
    private fun getArtistInfo(){
        val artistBiography = getArtistInfoFromRepository()
        updateUi(artistBiography)
    }
    private fun getArtistInfoFromRepository(): ArtistBiography{
        val artistName = getArtistName()
        val dbArticle = getArticleFromDb(artistName)
        val artistBiography: ArtistBiography

        if(dbArticle != null) artistBiography = dbArticle.markItAsLocal()
        else{
            artistBiography = getArticleFromService(artistName)
            if(artistBiography.biography.isEmpty()) insertArtistIntoDB(artistBiography)
        }
        return artistBiography
    }
    private fun getArtistName():String{
        return intent.getStringExtra(ARTIST_NAME_EXTRA)?:throw Exception("Mission artist name")
    }

    private fun getArticleFromDb(artistName:String): ArtistBiography?{
        val artistEntity = articleDatabase.ArticleDao().getArticleByArtistName(artistName)
        return artistEntity?.let {
            ArtistBiography(artistName,artistEntity.biography, artistEntity.articleUrl)
        }
    }
    private fun getArticleFromService(artistName:String): ArtistBiography{
        var artistBiography = ArtistBiography(artistName,"","")
        try{
            val callResponse = getSongFromService(artistName)
            artistBiography = getArtistBioFromExternalData(callResponse.body(),artistName)
        }catch (io: IOException){
            io.printStackTrace()
        }

        return artistBiography
    }
    private fun getSongFromService(artistName:String) = lastFMAPI.getArtistInfo(artistName).execute()

    private fun getArtistBioFromExternalData(serviceData:String?, artistName:String): ArtistBiography{
        val gson = Gson()
        val jobj = gson.fromJson(serviceData, JsonObject::class.java)

        val artist = jobj["artist"].getAsJsonObject()
        val bio = artist["bio"].getAsJsonObject()
        val extract = bio["content"]
        val url = artist["url"]
        val text = extract?.asString ?: "No results"

        return  ArtistBiography(artistName,text,url.asString)
    }

    private fun insertArtistIntoDB(artistBiography: ArtistBiography){
        articleDatabase.ArticleDao().insertArticle(
            ArticleEntity(artistBiography.artistName,artistBiography.biography,artistBiography.articleUrl)
        )
    }
    private fun ArtistBiography.markItAsLocal() =copy(biography = DATABASE_MARKUP+biography)

    private fun updateUi(artistBiography: ArtistBiography){
        runOnUiThread {
            updateArticleText(artistBiography)
            updateOpenUrlButton(artistBiography)
            updateLastFMLogo()
        }
    }
    private fun updateArticleText(artistBiography: ArtistBiography){
        var text = artistBiography.biography.toString().replace("\\n","\n")
        articleTextView.text = textToHtml(text, artistBiography.artistName)
    }
    private fun updateOpenUrlButton(artistBiography: ArtistBiography){
        openUrlButton.setOnClickListener{
            var intent = Intent(Intent.ACTION_VIEW)
            intent.data = artistBiography.articleUrl.toUri()
            startActivity(intent)
        }
    }
    private fun updateLastFMLogo(){
        Picasso.get().load(LASTFM_IMAGE_URL).into(lastFMImageView)
    }
    companion object {
        const val ARTIST_NAME_EXTRA: String = "artistName"

        fun textToHtml(text: String, term: String): String {
            val builder = StringBuilder()

            builder.append("<html><div width=400>")
            builder.append("<font face=\"arial\">")

            val textWithBold = text
                .replace("'", " ")
                .replace("\n", "<br>")
                .replace(
                    ("(?i)" + term).toRegex(),
                    "<b>" + term.uppercase(Locale.getDefault()) + "</b>"
                )

            builder.append(textWithBold)
            builder.append("</font></div></html>")

            return builder.toString()
        }
    }
}
