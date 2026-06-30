package com.example.doanmb.ui.chat.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.ChatMessage;
import com.example.doanmb.data.repository.ChatRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel màn Chat chi tiết: lắng nghe/gửi tin nhắn, trạng thái chặn 2 chiều, thu hồi/chuyển tiếp/báo cáo.
 * Mọi truy cập Firestore đi qua {@link ChatRepository}. Việc gửi FCM (cần Context) phát ra qua {@link #getPushEvent()}.
 */
public class ChatDetailViewModel extends ViewModel {

    /** Trạng thái chặn 2 chiều. */
    public static class BlockState {
        public final boolean iBlockedPartner, partnerBlockedMe;
        public BlockState(boolean iBlocked, boolean partnerBlocked) {
            this.iBlockedPartner = iBlocked; this.partnerBlockedMe = partnerBlocked;
        }
        public boolean anyoneBlocked() { return iBlockedPartner || partnerBlockedMe; }
    }

    /** Dữ liệu cần để View gửi push điện thoại (cần Context). */
    public static class PushEvent {
        public final String receiverId, senderId, senderName, carName, carType, preview, roomId;
        public PushEvent(String receiverId, String senderId, String senderName,
                         String carName, String carType, String preview, String roomId) {
            this.receiverId = receiverId; this.senderId = senderId; this.senderName = senderName;
            this.carName = carName; this.carType = carType; this.preview = preview; this.roomId = roomId;
        }
    }

    /** 1 đích chuyển tiếp (phòng chat khác). */
    public static class ForwardTarget {
        public final String roomId, label;
        public ForwardTarget(String roomId, String label) { this.roomId = roomId; this.label = label; }
    }

    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<BlockState> blockState = new MutableLiveData<>(new BlockState(false, false));
    private final MutableLiveData<String>  message    = new MutableLiveData<>();
    private final MutableLiveData<PushEvent> pushEvent = new MutableLiveData<>();
    private final MutableLiveData<List<ForwardTarget>> forwardTargets = new MutableLiveData<>();
    private final MutableLiveData<Boolean> reportSuccess = new MutableLiveData<>();

    public LiveData<List<ChatMessage>> getMessages() { return messages; }
    public LiveData<BlockState> getBlockState() { return blockState; }
    public LiveData<String>     getMessageEvent() { return message; }
    public LiveData<PushEvent>  getPushEvent()  { return pushEvent; }
    public LiveData<List<ForwardTarget>> getForwardTargets() { return forwardTargets; }
    public LiveData<Boolean>    getReportSuccess() { return reportSuccess; }

    private String roomId, currentUserId, partnerId, carName, carType;
    private boolean iBlockedPartner = false, partnerBlockedMe = false;

    private ListenerRegistration chatListener, blockListenerMine, blockListenerPartner;
    private boolean started = false;

    public void init(String roomId, String currentUserId, String partnerId,
                     String carName, String carType) {
        this.roomId        = roomId;
        this.currentUserId = currentUserId != null ? currentUserId : "";
        this.partnerId     = partnerId;
        this.carName       = carName != null ? carName : "";
        this.carType       = carType != null ? carType : "sale";
        if (started) return;
        started = true;
        listenForMessages();
        listenForBlockStatus();
    }

    // ── Tin nhắn ─────────────────────────────────────────────────────────────────

    private void listenForMessages() {
        chatListener = ChatRepository.listenMessages(roomId, (value, error) -> {
            if (value == null) return;
            List<ChatMessage> list = new ArrayList<>();
            for (DocumentSnapshot doc : value.getDocuments()) {
                ChatMessage msg = doc.toObject(ChatMessage.class);
                if (msg != null) { msg.setMessageId(doc.getId()); list.add(msg); }
            }
            messages.setValue(list);
            markPartnerMessagesRead();
            markRoomRead();
        });
    }

    /** Gửi tin (text/ảnh/video). Cập nhật room + phát event push cho View. */
    public void sendMessage(String content, String imageUrl, String videoUrl) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId",  currentUserId);
        msg.put("content",   content != null ? content : "");
        msg.put("timestamp", FieldValue.serverTimestamp());
        msg.put("status",    0);
        msg.put("recalled",  false);

        final String preview;
        if (videoUrl != null && !videoUrl.isEmpty()) {
            msg.put("videoUrl",     videoUrl);
            msg.put("thumbnailUrl", com.example.doanmb.core.helper.CloudinaryHelper.getVideoThumbnailUrl(videoUrl));
            msg.put("messageType",  ChatMessage.TYPE_VIDEO);
            preview = "[Video]";
        } else if (imageUrl != null && !imageUrl.isEmpty()) {
            msg.put("imageUrl",    imageUrl);
            msg.put("messageType", ChatMessage.TYPE_IMAGE);
            preview = "[Hình ảnh]";
        } else {
            msg.put("messageType", ChatMessage.TYPE_TEXT);
            preview = content != null ? content : "";
        }

        ChatRepository.sendMessage(roomId, msg, new ChatRepository.OnCreated() {
            @Override public void onCreated(String id) {
                // Cập nhật last + unreadBy (partnerId luôn != null vì Activity đã chặn ở onCreate).
                Map<String, Object> roomUpdate = new HashMap<>();
                roomUpdate.put("lastMessage",   preview);
                roomUpdate.put("lastTimestamp", FieldValue.serverTimestamp());
                roomUpdate.put("lastSenderId",  currentUserId);
                roomUpdate.put("unreadBy",      partnerId != null ? partnerId : "");
                ChatRepository.updateRoomLast(roomId, roomUpdate);

                // Gửi push cho người nhận: đọc tên người gửi rồi phát event cho View.
                ChatRepository.loadUserBrief(currentUserId, (name, avatar) ->
                        pushEvent.setValue(new PushEvent(
                                partnerId, currentUserId, name != null ? name : "",
                                carName, carType, preview, roomId)));
            }
            @Override public void onError(String msg) { message.setValue("Lỗi gửi tin: " + msg); }
        });
    }

    public void markRoomRead() { ChatRepository.markRoomRead(roomId, currentUserId); }
    public void markPartnerMessagesRead() { ChatRepository.markPartnerMessagesRead(roomId, partnerId); }

    // ── Thu hồi ─────────────────────────────────────────────────────────────────

    public void recallMessage(String messageId) {
        ChatRepository.recallMessage(roomId, messageId, new ChatRepository.OnResult() {
            @Override public void onSuccess() { message.setValue("Đã thu hồi tin nhắn"); }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    // ── Chuyển tiếp ───────────────────────────────────────────────────────────────

    public void loadForwardTargets() {
        ChatRepository.loadForwardTargets(currentUserId, new ChatRepository.OnSnapshot() {
            @Override public void onLoaded(com.google.firebase.firestore.QuerySnapshot snapshots) {
                List<ForwardTarget> targets = new ArrayList<>();
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    String rid = doc.getId();
                    if (rid.equals(roomId)) continue; // bỏ phòng hiện tại
                    String cName = doc.getString("carName");
                    String lastMsg = doc.getString("lastMessage");
                    String label = (cName != null && !cName.isEmpty() ? "🚗 " + cName : "Cuộc trò chuyện");
                    if (lastMsg != null && !lastMsg.isEmpty())
                        label += "\n   " + (lastMsg.length() > 40 ? lastMsg.substring(0, 40) + "..." : lastMsg);
                    targets.add(new ForwardTarget(rid, label));
                }
                forwardTargets.setValue(targets);
            }
            @Override public void onError(String msg) { message.setValue("Lỗi tải danh sách: " + msg); }
        });
    }

    public void forward(String targetRoomId, ChatMessage source) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId",  currentUserId);
        msg.put("timestamp", FieldValue.serverTimestamp());
        msg.put("status",    0);
        msg.put("recalled",  false);

        String preview;
        if (source.isVideo() && source.getVideoUrl() != null && !source.getVideoUrl().isEmpty()) {
            msg.put("videoUrl",     source.getVideoUrl());
            msg.put("thumbnailUrl", source.getThumbnailUrl() != null ? source.getThumbnailUrl() : "");
            msg.put("messageType",  ChatMessage.TYPE_VIDEO);
            msg.put("content",      "");
            preview = "📩 [Video]";
        } else if (source.getImageUrl() != null && !source.getImageUrl().isEmpty()) {
            msg.put("imageUrl",    source.getImageUrl());
            msg.put("messageType", ChatMessage.TYPE_IMAGE);
            msg.put("content",     "");
            preview = "📩 [Hình ảnh]";
        } else {
            String content = source.getContent() != null ? source.getContent() : "";
            msg.put("content",     content);
            msg.put("messageType", ChatMessage.TYPE_TEXT);
            preview = "📩 " + content;
        }

        ChatRepository.forwardMessage(targetRoomId, msg, preview, new ChatRepository.OnResult() {
            @Override public void onSuccess() { message.setValue("✅ Đã chuyển tiếp tin nhắn!"); }
            @Override public void onError(String msg) { message.setValue("Lỗi chuyển tiếp: " + msg); }
        });
    }

    // ── Báo cáo ───────────────────────────────────────────────────────────────────

    public void submitMessageReport(ChatMessage messageToReport, String reason) {
        Map<String, Object> report = new HashMap<>();
        report.put("reporterId",      currentUserId);
        report.put("targetMessageId", messageToReport.getMessageId() != null ? messageToReport.getMessageId() : "");
        report.put("targetSenderId",  messageToReport.getSenderId());
        report.put("targetRoomId",    roomId);
        report.put("reason",          reason);
        report.put("messageContent",  messageToReport.getContent() != null ? messageToReport.getContent() : "");
        report.put("messageType",     messageToReport.getMessageType() != null ? messageToReport.getMessageType() : "text");
        if (messageToReport.getImageUrl() != null && !messageToReport.getImageUrl().isEmpty())
            report.put("imageUrl", messageToReport.getImageUrl());
        if (messageToReport.getVideoUrl() != null && !messageToReport.getVideoUrl().isEmpty())
            report.put("videoUrl", messageToReport.getVideoUrl());
        report.put("status",    "pending");
        report.put("timestamp", FieldValue.serverTimestamp());

        ChatRepository.submitMessageReport(report, new ChatRepository.OnResult() {
            @Override public void onSuccess() { reportSuccess.setValue(true); }
            @Override public void onError(String msg) { message.setValue("Lỗi gửi báo cáo: " + msg); }
        });
    }

    // ── Chặn 2 chiều ───────────────────────────────────────────────────────────────

    private void listenForBlockStatus() {
        blockListenerMine = ChatRepository.listenBlock(currentUserId, partnerId, (doc, e) -> {
            iBlockedPartner = doc != null && doc.exists();
            publishBlockState();
        });
        blockListenerPartner = ChatRepository.listenBlock(partnerId, currentUserId, (doc, e) -> {
            partnerBlockedMe = doc != null && doc.exists();
            publishBlockState();
        });
    }

    private void publishBlockState() {
        blockState.setValue(new BlockState(iBlockedPartner, partnerBlockedMe));
    }

    public boolean isAnyoneBlocked() { return iBlockedPartner || partnerBlockedMe; }
    public boolean isIBlockedPartner() { return iBlockedPartner; }

    public void block() {
        ChatRepository.block(currentUserId, partnerId, new ChatRepository.OnResult() {
            @Override public void onSuccess() { message.setValue("__BLOCKED__"); }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    public void unblock() {
        ChatRepository.unblock(currentUserId, partnerId, new ChatRepository.OnResult() {
            @Override public void onSuccess() { message.setValue("__UNBLOCKED__"); }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (chatListener != null) chatListener.remove();
        if (blockListenerMine != null) blockListenerMine.remove();
        if (blockListenerPartner != null) blockListenerPartner.remove();
    }
}
