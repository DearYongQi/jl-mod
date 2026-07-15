package com.yongqimac.j2meplayer.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.yongqimac.j2meplayer.api.ApiClient;
import com.yongqimac.j2meplayer.model.SaveSlot;
import ru.playsoftware.j2meloader.R;
import java.io.*;
import java.net.URL;

public class GameDetailActivity extends AppCompatActivity {

    private String gameName, gameFile, gameCover;
    private ImageView coverView;
    private TextView nameView;
    private Button btnPlay, btnFavorite;
    private ProgressBar downloadProgress;
    private LinearLayout savesContainer, skinContainer;
    private ProgressBar savesProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_detail);

        gameName = getIntent().getStringExtra("game_name");
        gameFile = getIntent().getStringExtra("game_file");
        gameCover = getIntent().getStringExtra("game_cover");

        coverView = findViewById(R.id.detail_cover);
        nameView = findViewById(R.id.detail_name);
        btnPlay = findViewById(R.id.btn_play);
        btnFavorite = findViewById(R.id.btn_favorite);
        downloadProgress = findViewById(R.id.download_progress);
        savesContainer = findViewById(R.id.saves_container);
        savesProgress = findViewById(R.id.saves_progress);
        skinContainer = findViewById(R.id.skin_container);

        nameView.setText(gameName);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(gameName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (gameCover != null && !gameCover.isEmpty()) {
            new LoadCoverTask().execute("https://yongqimac.vip" + gameCover);
        }

        btnPlay.setOnClickListener(v -> downloadAndPlay());
        btnFavorite.setOnClickListener(v -> toggleFavorite());

        loadSaves();
        buildSkinSelector();
    }

    private void downloadAndPlay() {
        btnPlay.setEnabled(false);
        btnPlay.setText("下载中...");
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setProgress(0);

        new AsyncTask<Void, Integer, File>() {
            @Override
            protected File doInBackground(Void... voids) {
                try {
                    publishProgress(30);
                    byte[] jarData = ApiClient.downloadJar(gameFile);
                    publishProgress(80);

                    File dir = new File(getExternalFilesDir(null), "jars");
                    if (!dir.exists()) dir.mkdirs();
                    File jarFile = new File(dir, gameFile);
                    FileOutputStream fos = new FileOutputStream(jarFile);
                    fos.write(jarData);
                    fos.close();
                    publishProgress(100);
                    return jarFile;
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                downloadProgress.setProgress(values[0]);
            }

            @Override
            protected void onPostExecute(File jarFile) {
                downloadProgress.setVisibility(View.GONE);
                btnPlay.setEnabled(true);
                btnPlay.setText("下载并启动");

                if (jarFile == null) {
                    Toast.makeText(GameDetailActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(GameDetailActivity.this,
                        javax.microedition.shell.MicroActivity.class);
                intent.setData(Uri.fromFile(jarFile));
                intent.putExtra("ru.playsoftware.j2meloader.midletName", gameName);
                startActivity(intent);
            }
        }.execute();
    }

    private void loadSaves() {
        savesProgress.setVisibility(View.VISIBLE);
        new AsyncTask<Void, Void, java.util.List<SaveSlot>>() {
            @Override
            protected java.util.List<SaveSlot> doInBackground(Void... v) {
                try { return ApiClient.getSaves(gameName); }
                catch (Exception e) { return null; }
            }
            @Override
            protected void onPostExecute(java.util.List<SaveSlot> slots) {
                savesProgress.setVisibility(View.GONE);
                savesContainer.removeAllViews();
                if (slots == null || slots.isEmpty()) {
                    TextView tv = new TextView(GameDetailActivity.this);
                    tv.setText("暂无存档");
                    tv.setTextColor(0xFF888888);
                    tv.setPadding(0, 8, 0, 0);
                    savesContainer.addView(tv);
                    return;
                }
                for (SaveSlot s : slots) {
                    addSlotView(s);
                }
            }
        }.execute();
    }

    private void addSlotView(SaveSlot slot) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 0);

        String text = "槽位 " + slot.slot;
        if (slot.empty) {
            text += " - 空";
        } else if (slot.mtime != null) {
            text += " - " + slot.mtime.substring(0, 10);
        }

        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundTint(slot.empty ? 0xFF424242 : 0xFF2E7D32);
        btn.setTextColor(0xFFFFFFFF);
        btn.setOnClickListener(v -> {
            if (slot.empty) {
                // Save: get screenshot + RMS data from the emulator
                Toast.makeText(this, "请在游戏中暂停后保存", Toast.LENGTH_SHORT).show();
            } else {
                // Load: download save data and launch emulator
                loadSaveAndLaunch(slot.slot);
            }
        });
        row.addView(btn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!slot.empty) {
            Button delBtn = new Button(this);
            delBtn.setText("删");
            delBtn.setBackgroundTint(0xFFD32F2F);
            delBtn.setTextColor(0xFFFFFFFF);
            delBtn.setOnClickListener(v -> deleteSave(slot.slot));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    80, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(8);
            row.addView(delBtn, lp);
        }

        savesContainer.addView(row);
    }

    private void loadSaveAndLaunch(int slot) {
        new AsyncTask<Void, Void, byte[]>() {
            @Override
            protected byte[] doInBackground(Void... v) {
                try { return ApiClient.loadSave(gameName, slot); }
                catch (Exception e) { return null; }
            }
            @Override
            protected void onPostExecute(byte[] rmsData) {
                if (rmsData == null) {
                    Toast.makeText(GameDetailActivity.this, "下载存档失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Write RMS data to emulator dir
                try {
                    File rmsDir = new File(getExternalFilesDir(null),
                            "appdb/" + gameName.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    if (!rmsDir.exists()) rmsDir.mkdirs();
                    FileOutputStream fos = new FileOutputStream(
                            new File(rmsDir, "rms_data"));
                    fos.write(rmsData);
                    fos.close();
                } catch (IOException e) {
                    Toast.makeText(GameDetailActivity.this, "写入存档失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(GameDetailActivity.this, "存档已加载，请启动游戏", Toast.LENGTH_SHORT).show();
            }
        }.execute();
    }

    private void deleteSave(int slot) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("删除槽位 " + slot + " 的存档？")
                .setPositiveButton("删除", (d, w) -> {
                    new AsyncTask<Void, Void, Boolean>() {
                        @Override
                        protected Boolean doInBackground(Void... v) {
                            try { ApiClient.deleteSave(gameName, slot); return true; }
                            catch (Exception e) { return false; }
                        }
                        @Override
                        protected void onPostExecute(Boolean ok) {
                            if (ok) loadSaves();
                            else Toast.makeText(GameDetailActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    }.execute();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void buildSkinSelector() {
        String currentId = SkinManager.getCurrentSkin(this).id;
        for (SkinManager.Skin skin : SkinManager.SKINS) {
            Button btn = new Button(this);
            btn.setText(skin.name);
            btn.setBackgroundTintList(ColorStateList.valueOf(skin.id.equals(currentId) ? 0xFFE0551F : 0xFF424242));
            btn.setTextColor(0xFFFFFFFF);
            btn.setOnClickListener(v -> {
                SkinManager.saveSkin(this, skin.id);
                Toast.makeText(this, "皮肤已切换为: " + skin.name, Toast.LENGTH_SHORT).show();
                buildSkinSelector(); // refresh
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 4, 8, 0);
            skinContainer.addView(btn, lp);
        }
    }

    private void toggleFavorite() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... v) {
                try { return ApiClient.toggleFavorite(gameName); }
                catch (Exception e) { return false; }
            }
            @Override
            protected void onPostExecute(Boolean ok) {
                if (ok) {
                    btnFavorite.setText("已收藏");
                    btnFavorite.setBackgroundTint(0xFFFF9800);
                } else {
                    Toast.makeText(GameDetailActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    class LoadCoverTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... urls) {
            try {
                InputStream is = new URL(urls[0]).openStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                return bmp;
            } catch (Exception e) { return null; }
        }
        @Override
        protected void onPostExecute(Bitmap bmp) {
            if (bmp != null) coverView.setImageBitmap(bmp);
        }
    }
}
