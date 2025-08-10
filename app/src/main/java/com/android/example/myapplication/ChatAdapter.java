package com.android.example.myapplication;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.os.Handler;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<ChatMessage> messages;

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
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_message_bot, parent, false);
            return new BotViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.textMessage.setText(message.getMessage());
            
            if (message.getVideoPath() != null) {
                userHolder.videoView.setVisibility(View.VISIBLE);
                Uri videoUri = Uri.parse(message.getVideoPath());
                userHolder.videoView.setVideoURI(videoUri);
                
                // Add media controller for video playback
                MediaController mediaController = new MediaController(context);
                mediaController.setAnchorView(userHolder.videoView);
                userHolder.videoView.setMediaController(mediaController);
                
                // Start video playback on click
                userHolder.videoView.setOnClickListener(v -> {
                    if (userHolder.videoView.isPlaying()) {
                        userHolder.videoView.pause();
                    } else {
                        userHolder.videoView.start();
                    }
                });
            } else {
                userHolder.videoView.setVisibility(View.GONE);
            }
        } else if (holder instanceof BotViewHolder) {
            BotViewHolder botHolder = (BotViewHolder) holder;
            botHolder.textMessage.setText(message.getMessage());
        } else if (holder instanceof TypingViewHolder) {
            TypingViewHolder typingHolder = (TypingViewHolder) holder;
            animateTypingDots(typingHolder);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        VideoView videoView;

        UserViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
            videoView = itemView.findViewById(R.id.videoView);
        }
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;

        BotViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
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

    private void animateTypingDots(TypingViewHolder holder) {
        Handler handler = new Handler();
        
        // Animate dots with delay
        holder.dot1.setAlpha(0.3f);
        holder.dot2.setAlpha(0.3f);
        holder.dot3.setAlpha(0.3f);
        
        handler.postDelayed(() -> {
            holder.dot1.animate().alpha(1f).setDuration(300).start();
        }, 0);
        
        handler.postDelayed(() -> {
            holder.dot2.animate().alpha(1f).setDuration(300).start();
        }, 200);
        
        handler.postDelayed(() -> {
            holder.dot3.animate().alpha(1f).setDuration(300).start();
        }, 400);
        
        // Loop the animation
        handler.postDelayed(() -> {
            animateTypingDots(holder);
        }, 1200);
    }
}