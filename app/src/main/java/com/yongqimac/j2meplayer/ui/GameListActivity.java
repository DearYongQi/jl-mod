package com.yongqimac.j2meplayer.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new GameListAdapter(this, java.util.Collections.emptyList());
        recyclerView.setAdapter(adapter);

        requestStoragePermission();
        loadGames();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("需要存储权限")
                        .setMessage("为了将游戏文件保存到手机本地存储，需要授予「所有文件访问权限」。\n\n点击确定后将跳转到设置页面，请找到本应用并开启权限。")
                        .setPositiveButton("去设置", (d, w) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("稍后", null)
                        .show();
            }
        }
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
