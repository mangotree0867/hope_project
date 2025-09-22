package com.android.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatSessionAdapter extends RecyclerView.Adapter<ChatSessionAdapter.VH> {

    private final List<ChatSession> sessions = new ArrayList<>();
    private final OnItemClick onItemClick;
    private final OnEditClick onEditClick;
    private final Context context;

    private static final String PREF_SESSION_META = "session_meta";
    private final SimpleDateFormat fmt = new SimpleDateFormat("MM/dd a hh:mm", Locale.KOREA);

    public interface OnItemClick { void onClick(ChatSession session); }
    public interface OnEditClick { void onEditTitle(ChatSession session); }

    public ChatSessionAdapter(Context context, OnItemClick onItemClick, OnEditClick onEditClick) {
        this.context = context;
        this.onItemClick = onItemClick;
        this.onEditClick = onEditClick;
    }

    public void setSessions(List<ChatSession> list) {
        sessions.clear();
        if (list != null) sessions.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_session, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ChatSession session = sessions.get(pos);

        // Title - use custom title if available, otherwise default
        String title = session.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = "수어 번역 세션 #" + session.getId();
        }
        h.tvTitle.setText(title);

        // Time formatting - more user friendly
        long ts = parseSessionCreatedAtMillis(session.getCreatedAt());
        String timeAgo = getTimeAgoString(ts);
        h.tvTime.setText(timeAgo);

        // Location
        String addr = session.getAddress();
        if (addr == null || addr.isEmpty()) {
            h.tvLocation.setText("위치 정보 없음");
        } else {
            h.tvLocation.setText(addr);
        }

        // Message count
        int messageCount = session.getMessageCount();
        if (messageCount > 0) {
            h.tvMessageCount.setText(messageCount + "개 메시지");
        } else {
            h.tvMessageCount.setText("새 세션");
        }

        // Last message preview (placeholder for now)
        h.tvPreview.setText("AI 수어 번역 대화를 시작하거나 계속할 수 있습니다.");

        // Click listeners
        h.itemView.setOnClickListener(v -> {
            if (onItemClick != null) {
                onItemClick.onClick(session);
            }
        });

        h.btnEditTitle.setOnClickListener(v -> {
            if (onEditClick != null) {
                onEditClick.onEditTitle(session);
            }
        });
    }

    @Override public int getItemCount() { return sessions.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime, tvLocation, tvMessageCount, tvPreview;
        android.widget.ImageButton btnEditTitle;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvLocation = itemView.findViewById(R.id.tv_location);
            tvMessageCount = itemView.findViewById(R.id.tv_message_count);
            tvPreview = itemView.findViewById(R.id.tv_preview);
            btnEditTitle = itemView.findViewById(R.id.btn_edit_title);
        }
    }

    private static long parseSessionCreatedAtMillis(String s) {
        if (s == null || s.isEmpty()) return System.currentTimeMillis();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
            }
        } catch (Throwable ignore) {}
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String p : patterns) {
            try {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(p, java.util.Locale.US);
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return f.parse(s).getTime();
            } catch (Throwable ignore) {}
        }
        return System.currentTimeMillis();
    }

    private String getTimeAgoString(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 60 * 1000) { // Less than 1 minute
            return "방금 전";
        } else if (diff < 60 * 60 * 1000) { // Less than 1 hour
            long minutes = diff / (60 * 1000);
            return minutes + "분 전";
        } else if (diff < 24 * 60 * 60 * 1000) { // Less than 1 day
            long hours = diff / (60 * 60 * 1000);
            return hours + "시간 전";
        } else if (diff < 7 * 24 * 60 * 60 * 1000) { // Less than 1 week
            long days = diff / (24 * 60 * 60 * 1000);
            return days + "일 전";
        } else {
            // Show actual date for older sessions
            return new SimpleDateFormat("MM/dd", Locale.KOREA).format(new Date(timestamp));
        }
    }
}
