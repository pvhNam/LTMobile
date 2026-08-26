package com.example.doanmb.ui.aiassistant.view;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.data.model.AiChatMessage;
import com.example.doanmb.data.repository.AiAssistantRepository;
import com.example.doanmb.ui.aiassistant.adapter.AiMessageAdapter;
import com.example.doanmb.ui.car.view.CarDetailActivity;

import java.util.ArrayList;
import java.util.List;

import eightbitlab.com.blurview.BlurAlgorithm;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public class AiAssistantActivity extends AppCompatActivity {

    private static final String STATE_MESSAGES = "ai_messages";

    private final ArrayList<AiChatMessage> messages = new ArrayList<>();
    private RecyclerView messageList;
    private EditText messageInput;
    private ImageButton sendButton;
    private AiMessageAdapter adapter;
    private boolean waitingForReply;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_ai_assistant);
        EdgeToEdgeUtil.applyHeaderAndScroll(null, findViewById(R.id.page_header));

        messageList = findViewById(R.id.rv_messages);
        messageInput = findViewById(R.id.et_message);
        sendButton = findViewById(R.id.btn_send);
        setupBlur();

        if (savedInstanceState != null) {
            ArrayList<AiChatMessage> restored =
                    (ArrayList<AiChatMessage>) savedInstanceState.getSerializable(STATE_MESSAGES);
            if (restored != null) messages.addAll(restored);
        }
        removeTypingMessage();
        if (messages.isEmpty()) {
            messages.add(new AiChatMessage(
                    AiChatMessage.ROLE_AI,
                    "Xin chào! Tôi có thể tư vấn tìm xe mua, thuê xe hoặc hướng dẫn đăng bán xe. Bạn đang cần gì?"));
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messageList.setLayoutManager(layoutManager);
        adapter = new AiMessageAdapter(messages, this::openCarDetail);
        messageList.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        sendButton.setOnClickListener(v -> sendMessage());
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
        scrollToLastMessage();
    }

    private void setupBlur() {
        ViewGroup root = findViewById(R.id.root_layout);
        setupBlurView(findViewById(R.id.blur_header), root, 18f);
        setupBlurView(findViewById(R.id.blur_input), root, 18f);
    }

    private void setupBlurView(BlurView blurView, ViewGroup root, float radius) {
        if (blurView == null || root == null) return;
        BlurAlgorithm algorithm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new RenderEffectBlur()
                : new RenderScriptBlur(this);
        blurView.setupWith(root, algorithm)
                .setFrameClearDrawable(root.getBackground())
                .setBlurRadius(radius);
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        ArrayList<AiChatMessage> savedMessages = new ArrayList<>();
        for (AiChatMessage message : messages) {
            if (message.getRole() != AiChatMessage.ROLE_AI_TYPING) savedMessages.add(message);
        }
        outState.putSerializable(STATE_MESSAGES, savedMessages);
        super.onSaveInstanceState(outState);
    }

    private void sendMessage() {
        String question = messageInput.getText().toString().trim();
        if (question.isEmpty() || waitingForReply) return;

        List<AiChatMessage> history = new ArrayList<>(messages);
        messages.add(new AiChatMessage(AiChatMessage.ROLE_USER, question));
        messages.add(new AiChatMessage(AiChatMessage.ROLE_AI_TYPING, ""));
        waitingForReply = true;
        setInputEnabled(false);
        messageInput.setText("");
        adapter.notifyItemRangeInserted(messages.size() - 2, 2);
        scrollToLastMessage();

        AiAssistantRepository.ask(question, history, new AiAssistantRepository.OnAiReply() {
            @Override
            public void onSuccess(String reply, List<AiChatMessage.AiSuggestedCar> cars) {
                if (isFinishing() || isDestroyed()) return;
                removeTypingMessage();
                String safeReply = reply == null || reply.trim().isEmpty()
                        ? "Tôi chưa nhận được câu trả lời phù hợp. Bạn hãy thử diễn đạt câu hỏi khác nhé."
                        : reply.trim();
                messages.add(new AiChatMessage(AiChatMessage.ROLE_AI, safeReply, cars, false));
                finishRequest();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                removeTypingMessage();
                messages.add(new AiChatMessage(AiChatMessage.ROLE_AI, message, null, true));
                finishRequest();
            }
        });
    }

    private void finishRequest() {
        waitingForReply = false;
        setInputEnabled(true);
        adapter.notifyDataSetChanged();
        scrollToLastMessage();
        messageInput.requestFocus();
    }

    private void setInputEnabled(boolean enabled) {
        messageInput.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        sendButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private void removeTypingMessage() {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index).getRole() == AiChatMessage.ROLE_AI_TYPING) {
                messages.remove(index);
            }
        }
    }

    private void scrollToLastMessage() {
        if (!messages.isEmpty()) {
            messageList.post(() -> messageList.smoothScrollToPosition(messages.size() - 1));
        }
    }

    private void openCarDetail(AiChatMessage.AiSuggestedCar car) {
        if (car.getId().isEmpty()) return;
        Intent intent = new Intent(this, CarDetailActivity.class);
        intent.putExtra("CAR_ID", car.getId());
        intent.putExtra("CAR_TYPE", car.getType());
        startActivity(intent);
    }
}
