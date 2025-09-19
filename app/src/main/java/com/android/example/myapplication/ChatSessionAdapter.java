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
    private final Context context;

    private static final String PREF_SESSION_META = "session_meta";
    private final SimpleDateFormat fmt = new SimpleDateFormat("MM/dd a hh:mm", Locale.KOREA);

    public interface OnItemClick { void onClick(ChatSession session); }

    public ChatSessionAdapter(Context context, OnItemClick onItemClick) {
        this.context = context;
        this.onItemClick = onItemClick;
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

        // 1) 제목: "요약내용" 고정
        h.tvTitle.setText("요약내용");

        // 시간 포맷
        long ts = parseSessionCreatedAtMillis(session.getCreatedAt());
        String when = new java.text.SimpleDateFormat("MM/dd a hh:mm", java.util.Locale.KOREA)
                .format(new java.util.Date(ts));

        // 주소(시·구·동까지) – ChatActivity에서 저장한 값
        String addr = session.getAddress();
        if (addr == null || addr.isEmpty()) addr = "위치정보 없음";

        h.tvMeta.setText(when + " 📍" + addr);
        // ✅ 클릭 리스너 연결
        h.itemView.setOnClickListener(v -> {
            if (onItemClick != null) {
                onItemClick.onClick(session);
            }
        });
    }

    @Override public int getItemCount() { return sessions.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMeta;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvMeta  = itemView.findViewById(R.id.tv_meta);
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
}
