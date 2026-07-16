package com.yongqimac.j2meplayer.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.yongqimac.j2meplayer.api.ApiClient;
import com.yongqimac.j2meplayer.model.SaveSlot;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.util.FileUtils;
import java.io.*;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class GameDetailActivity extends AppCompatActivity {

    private String gameName, gameFile, gameCover;
    private ImageView coverView;
    private TextView nameView;
    private Button btnPlay;
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

        loadSaves();
        buildSkinSelector();
    }

    private void logError(String tag, String msg) {
        android.util.Log.e(tag, msg);
        new Thread(() -> {
            try { ApiClient.logError(tag, msg, gameName); }
            catch (Exception ignored) {}
        }).start();
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
                    logError("DownloadJar", e.toString());
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
                if (jarFile == null) {
                    btnPlay.setEnabled(true);
                    btnPlay.setText("下载并启动");
                    Toast.makeText(GameDetailActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                btnPlay.setText("安装中...");
                new InstallTask(jarFile).execute();
            }
        }.execute();
    }

    private class InstallTask extends AsyncTask<Void, Integer, String> {
        private final File jarFile;
        private final StringBuilder installLog = new StringBuilder();

        InstallTask(File jar) { this.jarFile = jar; }

        private void log(String step) {
            installLog.append(step).append("\n");
            logError("InstallTask", step);
        }

        @Override
        protected String doInBackground(Void... v) {
            log("=== Install start: " + jarFile.getAbsolutePath() + " ===");

            try {
                String baseName = jarFile.getName();
                int dot = baseName.lastIndexOf('.');
                if (dot > 0) baseName = baseName.substring(0, dot);
                String appDirName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
                if (appDirName.isEmpty()) appDirName = "app_" + System.currentTimeMillis();

                log("appDirName=" + appDirName);
                log("Config.getEmulatorDir()=" + Config.getEmulatorDir());
                log("getExternalFilesDir=" + getExternalFilesDir(null));

                String emuDir = Config.getEmulatorDir();
                if (emuDir == null || emuDir.isEmpty()) {
                    emuDir = new File(getExternalFilesDir(null), "emulator").getAbsolutePath();
                    log("emuDir null, fallback=" + emuDir);
                }

                File convertedDir = new File(emuDir, "converted");
                if (!convertedDir.exists()) {
                    log("mkdir convertedDir=" + convertedDir);
                    if (!convertedDir.mkdirs()) {
                        log("FAIL: mkdir convertedDir, try fallback");
                        // Scoped storage blocked external dir, fallback to app-private
                        emuDir = new File(getExternalFilesDir(null), "emulator").getAbsolutePath();
                        convertedDir = new File(emuDir, "converted");
                        log("emuDir fallback=" + emuDir);
                        if (!convertedDir.exists() && !convertedDir.mkdirs()) {
                            log("FAIL: mkdir convertedDir fallback also failed");
                            return null;
                        }
                        log("fallback convertedDir OK" + (convertedDir.exists() ? " (exists)" : ""));
                    }
                }

                File appDir = new File(convertedDir, appDirName);
                File tmpDir = new File(convertedDir, ".tmp_" + appDirName);

                if (tmpDir.exists()) {
                    log("delete old tmpDir");
                    FileUtils.deleteDirectory(tmpDir);
                }
                if (!tmpDir.mkdirs()) {
                    log("FAIL: mkdir tmpDir");
                    return null;
                }

                if (appDir.exists()) {
                    log("delete old appDir");
                    FileUtils.deleteDirectory(appDir);
                }

                // Extract MANIFEST.MF
                log("extract manifest");
                try (JarFile jf = new JarFile(jarFile)) {
                    Manifest mf = jf.getManifest();
                    if (mf != null) {
                        try (FileOutputStream mfOut = new FileOutputStream(
                                new File(tmpDir, Config.MIDLET_MANIFEST_FILE))) {
                            mf.write(mfOut);
                        }
                        log("manifest OK");
                    } else {
                        new File(tmpDir, Config.MIDLET_MANIFEST_FILE).createNewFile();
                        log("manifest empty, created blank");
                    }
                } catch (Exception e) {
                    log("FAIL extract manifest: " + e);
                }

                // Copy JAR as res.jar
                log("copy res.jar");
                File resJar = new File(tmpDir, Config.MIDLET_RES_FILE);
                FileUtils.copyFileUsingChannel(jarFile, resJar);
                log("res.jar size=" + resJar.length());

                // DEX conversion - needed for FULL_EMULATOR class loading
                // Main.main() calls System.exit() only on failure, not on success
                log("run dex converter");
                File dexFile = new File(tmpDir, Config.MIDLET_DEX_FILE);
                try {
                    com.android.dx.command.dexer.Main.main(new String[]{
                            "--dex", "--output=" + dexFile.getAbsolutePath(),
                            jarFile.getAbsolutePath()
                    });
                    if (dexFile.exists()) {
                        log("dex conversion OK, size=" + dexFile.length());
                    } else {
                        log("WARN: dex file not created");
                    }
                } catch (Exception e) {
                    log("FAIL dex conversion: " + e);
                }

                // Create config
                File configDir = new File(emuDir, "configs" + File.separator + appDirName);
                if (!configDir.exists()) {
                    if (!configDir.mkdirs()) {
                        log("FAIL: mkdir configDir=" + configDir);
                        FileUtils.deleteDirectory(tmpDir);
                        return null;
                    }
                    log("configDir created");
                }
                File cfgFile = new File(configDir, Config.MIDLET_CONFIG_FILE);
                if (!cfgFile.exists()) {
                    ProfileModel p = new ProfileModel();
                    p.dir = configDir;
                    ProfilesManager.saveConfig(p);
                    log("config.json saved");
                }

                // Move tmpDir to appDir
                log("rename tmpDir to appDir");
                if (!tmpDir.renameTo(appDir)) {
                    FileUtils.deleteDirectory(appDir);
                    if (!tmpDir.renameTo(appDir)) {
                        log("FAIL: rename failed after retry");
                        FileUtils.deleteDirectory(tmpDir);
                        return null;
                    }
                }
                log("install SUCCESS: " + appDir.getAbsolutePath());
                return appDir.getAbsolutePath();
            } catch (Exception e) {
                log("FAIL exception: " + e.toString());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log(sw.toString());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String appPath) {
            btnPlay.setEnabled(true);
            btnPlay.setText("下载并启动");
            if (appPath == null) {
                Toast.makeText(GameDetailActivity.this, "安装失败", Toast.LENGTH_SHORT).show();
                logError("InstallFail", installLog.toString());
                return;
            }
            // Use Config.startApp for proper launch
            Config.startApp(GameDetailActivity.this, gameName, appPath);
        }
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
        btn.setBackgroundTintList(ColorStateList.valueOf(slot.empty ? 0xFF424242 : 0xFF2E7D32));
        btn.setTextColor(0xFFFFFFFF);
        btn.setOnClickListener(v -> {
            if (slot.empty) {
                Toast.makeText(this, "请在游戏中暂停后保存", Toast.LENGTH_SHORT).show();
            } else {
                loadSaveAndLaunch(slot.slot);
            }
        });
        row.addView(btn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!slot.empty) {
            Button delBtn = new Button(this);
            delBtn.setText("删");
            delBtn.setBackgroundTintList(ColorStateList.valueOf(0xFFD32F2F));
            delBtn.setTextColor(0xFFFFFFFF);
            delBtn.setOnClickListener(v -> deleteSave(slot.slot));
            int delWidth = (int) (100 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    delWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
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
        skinContainer.removeAllViews();
        String currentId = SkinManager.getCurrentSkin(this).id;
        java.util.List<SkinManager.Skin> skins = java.util.Arrays.asList(SkinManager.SKINS);
        float density = getResources().getDisplayMetrics().density;
        int cols = 3;
        int btnPadH = (int)(6 * density);
        int btnPadV = (int)(8 * density);
        int margin = (int)(4 * density);

        int count = skins.size();
        for (int i = 0; i < count; i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, margin, 0, 0);
            row.setLayoutParams(rowLp);

            for (int j = i; j < i + cols && j < count; j++) {
                SkinManager.Skin skin = skins.get(j);
                Button btn = new Button(this);
                btn.setText(skin.name);
                btn.setTextSize(12);
                btn.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        skin.id.equals(currentId) ? 0xFFE0551F : 0xFF424242));
                btn.setTextColor(0xFFFFFFFF);
                btn.setOnClickListener(v -> {
                    SkinManager.saveSkin(this, skin.id);
                    Toast.makeText(this, "皮肤已切换为: " + skin.name, Toast.LENGTH_SHORT).show();
                    buildSkinSelector();
                });

                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (j < i + cols - 1) {
                    blp.setMarginEnd(margin);
                }
                row.addView(btn, blp);
            }
            skinContainer.addView(row);
        }
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
