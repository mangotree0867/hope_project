package com.android.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
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
                userHolder.videoContainer.setVisibility(View.VISIBLE);
                
                // Generate and set video thumbnail
                try {
                    Bitmap thumbnail = null;
                    String videoPath = message.getVideoPath();
                    
                    if (videoPath.startsWith("content://")) {
                        // Handle content URI (from gallery)
                        Uri videoUri = Uri.parse(videoPath);
                        thumbnail = ThumbnailUtils.createVideoThumbnail(
                            getRealPathFromURI(videoUri), MediaStore.Images.Thumbnails.MINI_KIND);
                    } else {
                        // Handle file path (from camera)
                        thumbnail = ThumbnailUtils.createVideoThumbnail(
                            videoPath, MediaStore.Images.Thumbnails.MINI_KIND);
                    }
                    
                    if (thumbnail != null) {
                        userHolder.videoThumbnail.setImageBitmap(thumbnail);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Set a placeholder if thumbnail generation fails
                    userHolder.videoThumbnail.setImageResource(R.drawable.ic_launcher_foreground);
                }
                
                // Set click listener to open video player
                userHolder.videoContainer.setOnClickListener(v -> {
                    Intent intent = new Intent(context, VideoPlayerActivity.class);
                    intent.putExtra("videoPath", message.getVideoPath());
                    context.startActivity(intent);
                });
            } else {
                userHolder.videoContainer.setVisibility(View.GONE);
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
        CardView videoContainer;
        ImageView videoThumbnail;

        UserViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
            videoContainer = itemView.findViewById(R.id.videoContainer);
            videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
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