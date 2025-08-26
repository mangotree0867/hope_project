package com.android.example.myapplication;
import android.content.Context;               // 어댑터에서 리소스/인텐트 등을 쓰기 위한 앱 컨텍스트
import android.content.Intent;                // 썸네일 터치 시 VideoPlayerActivity로 이동할 때 사용
import android.graphics.Bitmap;               // 비디오 썸네일 비트맵 객체 타입
import android.media.ThumbnailUtils;          // 비디오 썸네일 생성 유틸 (경로 기반)
import android.net.Uri;                       // content://, file:// 형태의 비디오 경로 표현
import android.provider.MediaStore;           // 미디어 스토어(썸네일 상수/데이터 컬럼 등)
import android.view.LayoutInflater;           // XML 레이아웃을 View로 inflate
import android.view.View;                     // 뷰 기본 타입
import android.view.ViewGroup;                // RecyclerView가 각 아이템을 담는 부모 컨테이너
import android.widget.ImageView;              // 썸네일 이미지 표시
import android.widget.TextView;               // 메시지 텍스트/타임스탬프 표시
import androidx.annotation.NonNull;           // 널 아님 어노테이션(안전성/가독성)
import androidx.cardview.widget.CardView;     // 동영상 썸네일 카드 UI
import androidx.recyclerview.widget.RecyclerView; // 리스트 어댑터 베이스 클래스
import android.os.Handler;                    // 타이핑 점(.) 애니메이션 지연 실행용
import java.util.List;                        // 메시지 목록 타입
import java.text.SimpleDateFormat;            // 타임스탬프 포맷터
import java.util.Date;                        // 에폭 ms → Date
import java.util.Locale;                      // 현지화된 포맷(오전/오후 등)


// 클래스/필드/시간 초기화
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context; // inflate, 리소스 접근, Activity 시작 등에 사용
    private List<ChatMessage> messages; // 표시할 메시지들의 데이터 소스
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("a hh:mm", Locale.getDefault()); // "오전/오후 시:분" 형태로 현지화된 시각 문자열 생성 (예: 오후 09:41)

    // 생성자
    public ChatAdapter(Context context, List<ChatMessage> messages) {
        this.context = context; // 전달받은 컨텍스트 보관
        this.messages = messages; // 바인딩할 데이터 보관
    }

    // 뷰 타입 결정
    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType(); // 각 메시지의 타입(USER/BOT/TYPING) 반환
    }

    // 뷰홀더 생성
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == ChatMessage.TYPE_USER) {
            view = LayoutInflater.from(context).inflate(R.layout.item_message_user, parent, false);
            return new UserViewHolder(view); // 사용자 메시지 레이아웃 inflate
        } else if (viewType == ChatMessage.TYPE_TYPING) {
            view = LayoutInflater.from(context).inflate(R.layout.item_typing_indicator, parent, false);
            return new TypingViewHolder(view); // "생각 중..." 인디케이터 레이아웃 inflate
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_message_bot, parent, false);
            return new BotViewHolder(view); // 봇 메시지 레이아웃 inflate
        }
    }

    // 데이터 바인딩
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position); // 바인딩할 메시지 한 개

        // 사용자 메시지
        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.textMessage.setText(message.getMessage()); // 본문 텍스트 표시
            userHolder.timeStamp.setText(formatTs(message)); // 타임스탬프 표시
            if (message.getVideoPath() != null) { // 동영상 포함 메시지면
                userHolder.videoContainer.setVisibility(View.VISIBLE); // 썸네일 카드 보이기
                try {
                    Bitmap thumbnail = null;
                    String videoPath = message.getVideoPath();
                    if (videoPath.startsWith("content://")) {
                        Uri videoUri = Uri.parse(videoPath);
                        thumbnail = ThumbnailUtils.createVideoThumbnail(
                            getRealPathFromURI(videoUri), MediaStore.Images.Thumbnails.MINI_KIND);
                    } else {
                        thumbnail = ThumbnailUtils.createVideoThumbnail(
                            videoPath, MediaStore.Images.Thumbnails.MINI_KIND);
                    }
                    if (thumbnail != null) {
                        userHolder.videoThumbnail.setImageBitmap(thumbnail); // 성공 시 썸네일 표시
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    userHolder.videoThumbnail.setImageResource(R.drawable.ic_launcher_foreground); // 실패 시 플레이스홀더 아이콘 표시
                }
                userHolder.videoContainer.setOnClickListener(v -> {
                    Intent intent = new Intent(context, VideoPlayerActivity.class);
                    intent.putExtra("videoPath", message.getVideoPath()); // 플레이어에 경로 전달
                    context.startActivity(intent); // 영상 재생 화면으로 이동
                });
            } else {
                userHolder.videoContainer.setVisibility(View.GONE); // 영상 없으면 카드 숨김
            }
        } else if (holder instanceof BotViewHolder) { // 봇 메시지
            BotViewHolder botHolder = (BotViewHolder) holder;
            botHolder.textMessage.setText(message.getMessage()); // 봇 응답 텍스트 표시
            botHolder.timeStamp.setText(formatTs(message)); // 응답 시각 표시
        } else if (holder instanceof TypingViewHolder) {
            TypingViewHolder typingHolder = (TypingViewHolder) holder;
            animateTypingDots(typingHolder); // 점3개 깜빡임 애니메이션
        }
    }

    // 아이템 개수 저장
    @Override
    public int getItemCount() {
        return messages.size(); // 리스트 크기를 RecyclerView에 전달
    }

    // 뷰홀더들
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage; // 본문
        CardView videoContainer; // 썸네일 카드영역(재생 오버레이 포함)
        ImageView videoThumbnail; // 썸네일 이미지
        TextView timeStamp; // 전송 시각
        UserViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
            videoContainer = itemView.findViewById(R.id.videoContainer);
            videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
            timeStamp = itemView.findViewById(R.id.timeStamp); // XML의 @+id/timeStamp와 연결
        }
    }
    static class BotViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage; // 봇 응답 본문
        TextView timeStamp; // 응답 시각
        BotViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
            timeStamp = itemView.findViewById(R.id.timeStamp);
        }
    }
    static class TypingViewHolder extends RecyclerView.ViewHolder {
        View dot1, dot2, dot3; // 점(.) 3개(좌→우)
        TypingViewHolder(View itemView) {
            super(itemView);
            dot1 = itemView.findViewById(R.id.dot1);
            dot2 = itemView.findViewById(R.id.dot2);
            dot3 = itemView.findViewById(R.id.dot3);
        }
    }

    // 점 애니메이션
    private void animateTypingDots(TypingViewHolder holder) {
        Handler handler = new Handler(); // 메인 스레드 핸들러 생성(지연 실행)
        holder.dot1.setAlpha(0.3f);
        holder.dot2.setAlpha(0.3f);
        holder.dot3.setAlpha(0.3f);
        handler.postDelayed(() -> {holder.dot1.animate().alpha(1f).setDuration(300).start();}, 0);
        handler.postDelayed(() -> {holder.dot2.animate().alpha(1f).setDuration(300).start();}, 200);
        handler.postDelayed(() -> {holder.dot3.animate().alpha(1f).setDuration(300).start();}, 400);
        handler.postDelayed(() -> {animateTypingDots(holder);}, 1200);
    }

    // 파일 경로 문자열을 얻어 경로 기반 API에 넣으려는 보조 함수
    private String getRealPathFromURI(Uri contentURI) {
        String result;
        android.database.Cursor cursor = context.getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) {
            result = contentURI.getPath(); // 쿼리 실패 시 대체로 contentURI의 path 사용
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA); // DATA 컬럼 인덱스
            result = cursor.getString(idx); // 실제 파일 경로 문자열 획득(단, Q+에서 종종 null)
            cursor.close();
        }
        return result;
    }

    // 타임스탬프 초기화
    private String formatTs(ChatMessage message) {
        long ts;
        try {
            ts = (Long) ChatMessage.class.getMethod("getTimestampMs").invoke(message);
        } catch (Exception ignore) {
            try {
                ts = (Long) ChatMessage.class.getMethod("getTimestamp").invoke(message);
            } catch (Exception e) {
                ts = 0L;
            }
        }
        if (ts <= 0L) return "방금 전"; // 시간값이 없으면 기본 플레이스홀더
        return TIME_FMT.format(new Date(ts)); // 유효하면 "오전/오후 hh:mm"로 출력
    }
}