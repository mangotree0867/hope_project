package com.android.example.myapplication;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<ChatMessage> messages;

    // 썸네일 생성용 스레드풀
    private final ExecutorService thumbPool = Executors.newFixedThreadPool(2);

    // 보낸 시간 표시 포맷터
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("a h:mm", Locale.getDefault());

    public ChatAdapter(Context context, List<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == ChatMessage.TYPE_USER) {
            view = LayoutInflater.from(context).inflate(R.layout.item_message_user, parent, false);
            return new UserViewHolder(view);
        } else if (viewType == ChatMessage.TYPE_TYPING) {
            view = LayoutInflater.from(context).inflate(R.layout.item_typing_indicator, parent, false);
            return new TypingViewHolder(view);
        } else if (viewType == ChatMessage.TYPE_DATE_HEADER) {
            view = LayoutInflater.from(context).inflate(R.layout.item_date_header, parent, false);
            return new DateHeaderViewHolder(view);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_message_bot, parent, false);
            return new BotViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage item = messages.get(position);

        // 1) 사용자 메시지
        if (holder instanceof UserViewHolder) {
            UserViewHolder h = (UserViewHolder) holder;

            if (item.isVideo()) {
                h.textMessage.setVisibility(View.GONE);
                h.videoContainer.setVisibility(View.VISIBLE);

                Bitmap cached = item.getVideoThumbnail();
                if (cached != null) {
                    h.videoThumbnail.setImageBitmap(cached);
                } else {
                    h.videoThumbnail.setImageDrawable(null); // 자리표시자
                    final String local = item.getLocalVideoPath();
                    final String remote = item.getRemoteVideoUrl();
                    final int bindPos = holder.getAdapterPosition();
                    if (bindPos == RecyclerView.NO_POSITION) return;

                    thumbPool.execute(() -> {
                        Bitmap bmp = null;
                        try {
                            if (local != null) {
                                if (android.os.Build.VERSION.SDK_INT >= 29) {
                                    bmp = android.media.ThumbnailUtils.createVideoThumbnail(
                                            new java.io.File(local),
                                            new android.util.Size(320, 180),
                                            null
                                    );
                                } else {
                                    bmp = android.media.ThumbnailUtils.createVideoThumbnail(
                                            local,
                                            android.provider.MediaStore.Images.Thumbnails.MINI_KIND
                                    );
                                }
                            } else if (remote != null) {
                                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                                mmr.setDataSource(remote, new java.util.HashMap<>());
                                bmp = mmr.getFrameAtTime(0);
                                mmr.release();
                            }
                        } catch (Throwable ignored) {}

                        Bitmap result = bmp;
                        if (result != null && bindPos == holder.getAdapterPosition()) {
                            item.setVideoThumbnail(result);
                            h.videoThumbnail.post(() -> h.videoThumbnail.setImageBitmap(result));
                        }
                    });
                }

                // 썸네일 탭 → 재생
                h.videoContainer.setOnClickListener(v -> {
                    Context ctx = v.getContext();
                    Intent i = new Intent(ctx, VideoPlayerActivity.class);
                    if (item.getLocalVideoPath() != null) {
                        i.putExtra("localPath", item.getLocalVideoPath());
                    } else if (item.getRemoteVideoUrl() != null) {
                        i.putExtra("remoteUrl", item.getRemoteVideoUrl());
                    } else if (item.getVideoPath() != null) { // 하위호환
                        String p = item.getVideoPath();
                        if (p.startsWith("http")) i.putExtra("remoteUrl", p);
                        else i.putExtra("localPath", p);
                    }
                    ctx.startActivity(i);
                });
            } else {
                // 텍스트 사용자 메시지(혹시 남아있을 수 있음)
                h.videoContainer.setVisibility(View.GONE);
                h.textMessage.setVisibility(View.VISIBLE);
                h.textMessage.setText(item.getMessage() != null ? item.getMessage() : "");
            }

            // [ADD] 사용자 메시지 보낸 시간 표시
            if (h.tvTime != null) {
                String formatted = timeFormat.format(new Date(item.getCreatedAt()));
                h.tvTime.setText(formatted);
                h.tvTime.setVisibility(View.VISIBLE);
            }
            return;
        }

        // 2) 봇 메시지
        if (holder instanceof BotViewHolder) {
            BotViewHolder h = (BotViewHolder) holder;
            h.textMessage.setVisibility(View.VISIBLE);
            h.textMessage.setText(item.getMessage() != null ? item.getMessage() : "");

            // [ADD] 봇 메시지 보낸 시간 표시
            if (h.tvTime != null) {
                String formatted = timeFormat.format(new Date(item.getCreatedAt()));
                h.tvTime.setText(formatted);
                h.tvTime.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (holder instanceof DateHeaderViewHolder) {        // [ADD]
            DateHeaderViewHolder h = (DateHeaderViewHolder) holder;
            h.tvDateHeader.setText(item.getMessage());
            return;
        }

        // 3) 타이핑(생각중…) 메시지
        if (holder instanceof TypingViewHolder) {
            TypingViewHolder h = (TypingViewHolder) holder;

            // 재활용 대비: 기존 애니메이션 정지
            h.dot1.clearAnimation();
            h.dot2.clearAnimation();
            h.dot3.clearAnimation();

            long d = 400L;
            ObjectAnimator a1 = ObjectAnimator.ofFloat(h.dot1, "alpha", 0.2f, 1f, 0.2f);
            a1.setDuration(d);
            a1.setRepeatCount(ObjectAnimator.INFINITE);
            a1.setInterpolator(new LinearInterpolator());

            ObjectAnimator a2 = ObjectAnimator.ofFloat(h.dot2, "alpha", 0.2f, 1f, 0.2f);
            a2.setDuration(d);
            a2.setStartDelay(120);
            a2.setRepeatCount(ObjectAnimator.INFINITE);
            a2.setInterpolator(new LinearInterpolator());

            ObjectAnimator a3 = ObjectAnimator.ofFloat(h.dot3, "alpha", 0.2f, 1f, 0.2f);
            a3.setDuration(d);
            a3.setStartDelay(240);
            a3.setRepeatCount(ObjectAnimator.INFINITE);
            a3.setInterpolator(new LinearInterpolator());

            AnimatorSet set = new AnimatorSet();
            set.playTogether(a1, a2, a3);
            set.start();
            return;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage, tvTime;
        CardView videoContainer;
        ImageView videoThumbnail;

        UserViewHolder(View itemView) {
            super(itemView);
            textMessage    = itemView.findViewById(R.id.textMessage);
            tvTime         = itemView.findViewById(R.id.tv_time);
            videoContainer = itemView.findViewById(R.id.videoContainer);
            videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
        }
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage, tvTime;

        BotViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
            tvTime      = itemView.findViewById(R.id.tv_time);
        }
    }

    static class TypingViewHolder extends RecyclerView.ViewHolder {
        View dot1, dot2, dot3;

        TypingViewHolder(View itemView) {
            super(itemView);
            dot1 = itemView.findViewById(R.id.dot1);
            dot2 = itemView.findViewById(R.id.dot2);
            dot3 = itemView.findViewById(R.id.dot3);
        }
    }

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateHeader;
        DateHeaderViewHolder(View itemView) {
            super(itemView);
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
        }
    }

    private void animateTypingDots(TypingViewHolder holder) {
        Handler handler = new Handler();

        holder.dot1.setAlpha(0.3f);
        holder.dot2.setAlpha(0.3f);
        holder.dot3.setAlpha(0.3f);

        handler.postDelayed(() -> holder.dot1.animate().alpha(1f).setDuration(300).start(), 0);
        handler.postDelayed(() -> holder.dot2.animate().alpha(1f).setDuration(300).start(), 200);
        handler.postDelayed(() -> holder.dot3.animate().alpha(1f).setDuration(300).start(), 400);

        handler.postDelayed(() -> animateTypingDots(holder), 1200);
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        android.database.Cursor cursor = context.getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) {
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }
}
