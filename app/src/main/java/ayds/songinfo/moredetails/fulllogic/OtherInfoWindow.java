package ayds.songinfo.moredetails.fulllogic;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.room.Room;

import ayds.songinfo.R;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.squareup.picasso.Picasso;


import java.io.IOException;

import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;


public class OtherInfoWindow extends Activity {

    public final static String ARTIST_NAME_EXTRA = "artistName";

    private TextView description;
    private Button urlButton;
    private ImageView logoImage;

    private Retrofit retrofit;
    private LastFMAPI lastFMAPI;

    private ArticleDatabase dataBase = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initProperties();
        updateLogoImage();

        setContentView(R.layout.activity_other_info);
        open(getIntent().getStringExtra("artistName"));
    }

    private void initProperties() {
        description = findViewById(R.id.textPane1);
        urlButton = findViewById(R.id.openUrlButton1);
        logoImage = findViewById(R.id.imageView1);

        retrofit = new Retrofit.Builder()
                .baseUrl("https://ws.audioscrobbler.com/2.0/")
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        lastFMAPI = retrofit.create(LastFMAPI.class);
    }

    private void updateLogoImage() {
        String imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Lastfm_logo.svg/320px-Lastfm_logo.svg.png";
        runOnUiThread(() -> {
            Picasso.get().load(imageUrl).into(logoImage);
        });
    }

    private void open(String artist) {
        dataBase = Room.databaseBuilder(this, ArticleDatabase.class, "database-name-thename").build();
        getArtistInfo(artist);
    }

    public void getArtistInfo(String artistName) {

        Log.e("TAG", "artistName " + artistName);
        new Thread(() -> {
            try {

                Artist artist = getArtist(artistName);

                // save to DB  <o/
                final String text2 = textToHtml(artist.getBiography(), artistName);
                new Thread(() -> {
                    dataBase.ArticleDao().insertArticle(new ArticleEntity(artistName, text2, artist.getUrl()));
                }).start();

                //Not should be here
                urlButton.setOnClickListener((View v) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(artist.getUrl()));
                    startActivity(intent);
                });

                runOnUiThread(() -> {
                    description.setText(Html.fromHtml(artist.getBiography()));
                });
            } catch (IOException e) {
                Log.e("Error",e.toString());
            }
        }).start();

    }

    private Artist getArtist(String artistName) throws IOException {
        Artist artist = new Artist();
        final String biography;
        final String url;

        ArticleEntity article = dataBase.ArticleDao().getArticleByArtistName(artistName);

        if (article != null) { // exists in db
            biography = "[*]" + article.getBiography();
            url = article.getArticleUrl();
        } else { // get from service
            Response<String> callResponse = lastFMAPI.getArtistInfo(artistName).execute(); //Compilation error, try catch
            Log.e("TAG", "JSON " + callResponse.body());

            Gson gson = new Gson();
            JsonObject artistInformation = gson.fromJson(callResponse.body(), JsonObject.class)
                    .get("artist").getAsJsonObject()
                    .get("bio").getAsJsonObject()
                    .get("content").getAsJsonObject();

            url = artistInformation.get("url").getAsString();
            biography = artistInformation.getAsString().replace("\\n", "\n");
        }
        artist.setBiography(biography);
        artist.setUrl(url);
        return artist;
    }

    public static String textToHtml(String text, String term) {
        StringBuilder builder = new StringBuilder();

        builder.append("<html><div width=400>");
        builder.append("<font face=\"arial\">");

        String textWithBold = text
                .replace("'", " ")
                .replace("\n", "<br>")
                .replaceAll("(?i)" + term, "<b>" + term.toUpperCase() + "</b>");

        builder.append(textWithBold);

        builder.append("</font></div></html>");

        return builder.toString();
    }

    private static class Artist {
        private String biography;
        private String url;

        public void setBiography(String biography) {
            this.biography = biography;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBiography() {
            return biography;
        }

        public String getUrl() {
            return url;
        }
    }
}
