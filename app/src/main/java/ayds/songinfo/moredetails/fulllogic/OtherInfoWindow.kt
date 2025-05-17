package ayds.songinfo.moredetails.fulllogic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.room.Room.databaseBuilder
import ayds.songinfo.R
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.squareup.picasso.Picasso
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.IOException
import java.util.Locale

class OtherInfoWindow : Activity() {
    private var textPane1: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_other_info)

        textPane1 = findViewById<TextView>(R.id.textPane1)


        open(getIntent().getStringExtra("artistName")!!)
    }

    fun getARtistInfo(artistName: String) {
        // create

        val retrofit = Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/2.0/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        val lastFMAPI = retrofit.create<LastFMAPI>(LastFMAPI::class.java)

        Log.e("TAG", "artistName " + artistName)

        Thread(object : Runnable {
            override fun run() {
                val article = dataBase!!.ArticleDao().getArticleByArtistName(artistName)


                var text = ""


                if (article != null) { // exists in db

                    text = "[*]" + article.biography

                    val urlString = article.articleUrl
                    findViewById<View?>(R.id.openUrlButton1).setOnClickListener(object :
                        View.OnClickListener {
                        override fun onClick(v: View?) {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setData(Uri.parse(urlString))
                            startActivity(intent)
                        }
                    })
                } else { // get from service
                    val callResponse: Response<String?>?
                    try {
                        callResponse = lastFMAPI.getArtistInfo(artistName).execute()

                        Log.e("TAG", "JSON " + callResponse.body())

                        val gson = Gson()
                        val jobj =
                            gson.fromJson<JsonObject>(callResponse.body(), JsonObject::class.java)
                        val artist = jobj.get("artist").getAsJsonObject()
                        val bio = artist.get("bio").getAsJsonObject()
                        val extract = bio.get("content")
                        val url = artist.get("url")


                        if (extract == null) {
                            text = "No Results"
                        } else {
                            text = extract.getAsString().replace("\\n", "\n")

                            text = textToHtml(text, artistName)


                            // save to DB  <o/
                            val text2 = text
                            Thread(object : Runnable {
                                override fun run() {
                                    dataBase!!.ArticleDao().insertArticle(
                                        ArticleEntity(
                                            artistName,
                                            text2,
                                            url.getAsString()
                                        )
                                    )
                                }
                            }).start()
                        }


                        val urlString = url.getAsString()
                        findViewById<View?>(R.id.openUrlButton1).setOnClickListener(object :
                            View.OnClickListener {
                            override fun onClick(v: View?) {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setData(Uri.parse(urlString))
                                startActivity(intent)
                            }
                        })
                    } catch (e1: IOException) {
                        Log.e("TAG", "Error " + e1)
                        e1.printStackTrace()
                    }
                }


                val imageUrl =
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Lastfm_logo.svg/320px-Lastfm_logo.svg.png"

                Log.e("TAG", "Get Image from " + imageUrl)


                val finalText = text

                runOnUiThread(Runnable {
                    Picasso.get().load(imageUrl)
                        .into(findViewById<View?>(R.id.imageView1) as ImageView?)
                    textPane1!!.setText(Html.fromHtml(finalText))
                })
            }
        }).start()
    }

    private var dataBase: ArticleDatabase? = null

    private fun open(artist: String) {
        dataBase = databaseBuilder<ArticleDatabase>(
            this,
            ArticleDatabase::class.java,
            "database-name-thename"
        ).build()

        Thread(object : Runnable {
            override fun run() {
                dataBase!!.ArticleDao().insertArticle(ArticleEntity("test", "sarasa", ""))
                Log.e("TAG", "" + dataBase!!.ArticleDao().getArticleByArtistName("test"))
                Log.e("TAG", "" + dataBase!!.ArticleDao().getArticleByArtistName("nada"))
            }
        }).start()


        getARtistInfo(artist)
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
