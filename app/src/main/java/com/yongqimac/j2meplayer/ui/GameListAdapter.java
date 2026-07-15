package com.yongqimac.j2meplayer.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ru.playsoftware.j2meloader.R;
import com.yongqimac.j2meplayer.api.ApiClient;
import com.yongqimac.j2meplayer.model.Game;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameListAdapter extends RecyclerView.Adapter<GameListAdapter.ViewHolder> {

    private final Context ctx;
    private List<Game> games;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public GameListAdapter(Context ctx, List<Game> games) {
        this.ctx = ctx;
        this.games = games;
    }

    public void updateGames(List<Game> newGames) {
        this.games = newGames;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_game_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
        Game game = games.get(pos);
        holder.nameText.setText(game.name);

        if (game.cover != null && !game.cover.isEmpty()) {
            String coverUrl = "https://yongqimac.vip" + game.cover;
            holder.coverImage.setTag(coverUrl);
            executor.execute(() -> loadCover(holder.coverImage, coverUrl));
        } else {
            holder.coverImage.setImageResource(R.drawable.ic_game_default);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(ctx, GameDetailActivity.class);
            intent.putExtra("game_name", game.name);
            intent.putExtra("game_file", game.file);
            intent.putExtra("game_cover", game.cover);
            ctx.startActivity(intent);
        });
    }

    @Override public int getItemCount() { return games.size(); }

    private void loadCover(ImageView iv, String url) {
        try {
            InputStream is = new URL(url).openStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp != null && url.equals(iv.getTag())) {
                iv.post(() -> iv.setImageBitmap(bmp));
            }
        } catch (Exception ignored) {}
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView coverImage;
        TextView nameText;
        ViewHolder(View v) {
            super(v);
            coverImage = v.findViewById(R.id.game_cover);
            nameText = v.findViewById(R.id.game_name);
        }
    }
}
