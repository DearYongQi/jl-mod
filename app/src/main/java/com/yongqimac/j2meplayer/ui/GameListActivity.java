package com.yongqimac.j2meplayer.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.yongqimac.j2meplayer.api.ApiClient;
import com.yongqimac.j2meplayer.model.Game;
import ru.playsoftware.j2meloader.R;
import java.util.List;

public class GameListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView errorText;
    private GameListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.game_list);
        progressBar = findViewById(R.id.progress_bar);
        errorText = findViewById(R.id.error_text);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new GameListAdapter(this, java.util.Collections.emptyList());
        recyclerView.setAdapter(adapter);

        loadGames();
    }

    private void loadGames() {
        new AsyncTask<Void, Void, List<Game>>() {
            @Override
            protected List<Game> doInBackground(Void... voids) {
                try {
                    return ApiClient.getGames();
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(List<Game> games) {
                progressBar.setVisibility(android.view.View.GONE);
                if (games == null) {
                    errorText.setText("加载失败，请检查网络");
                    errorText.setVisibility(android.view.View.VISIBLE);
                    return;
                }
                if (games.isEmpty()) {
                    errorText.setText("暂无游戏");
                    errorText.setVisibility(android.view.View.VISIBLE);
                    return;
                }
                adapter.updateGames(games);
            }
        }.execute();
    }
}
