package com.example.doanmb.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.Map;

/**
 * Gom toàn bộ thao tác Firestore của Chat + Thông báo trong app:
 * collection "chat_rooms" (+ sub "messages"), "notifications", "blocks", "reports",
 * "message_reports". View/ViewModel không truy vấn Firestore trực tiếp nữa.
 */
public final class ChatRepository {

    private static final String COL_ROOMS   = "chat_rooms";
    private static final String SUB_MESSAGES= "messages";
    private static final String COL_NOTIFS  = "notifications";
    private static final String COL_USERS   = "users";
    private static final String COL_BLOCKS  = "blocks";
    private static final String COL_REPORTS = "reports";
    private static final String COL_MSG_REPORTS = "message_reports";

    private ChatRepository() {}

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    // ── Callbacks ────────────────────────────────────────────────────────────

    public interface OnCreated  { void onCreated(String id); void onError(String message); }
    public interface OnResult   { void onSuccess(); void onError(String message); }
    public interface OnDoc      { void onLoaded(DocumentSnapshot doc); void onError(String message); }
    public interface OnSnapshot { void onLoaded(QuerySnapshot snap); void onError(String message); }
    public interface OnUserBrief{ void onLoaded(String name, String avatar); }

    // ── Hội thoại + thông báo (màn Tin nhắn) ──────────────────────────────────

    public static ListenerRegistration listenConversations(
            @NonNull String uid, @NonNull EventListener<QuerySnapshot> listener) {
        return db().collection(COL_ROOMS)
                .whereArrayContains("participants", uid)
                .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener(listener);
    }

    public static void loadUserBrief(@Nullable String uid, @NonNull OnUserBrief cb) {
        if (uid == null || uid.isEmpty()) { cb.onLoaded(null, null); return; }
        db().collection(COL_USERS).document(uid).get()
                .addOnSuccessListener(doc -> cb.onLoaded(
                        doc != null ? doc.getString("name") : null,
                        doc != null ? doc.getString("avatarUrl") : null))
                .addOnFailureListener(e -> cb.onLoaded(null, null));
    }

    /** 200 tin gần nhất của 1 phòng (tìm kiếm theo nội dung — lọc recalled ở ViewModel). */
    public static void searchMessagesInRoom(@NonNull String roomId, @NonNull OnSnapshot cb) {
        db().collection(COL_ROOMS).document(roomId).collection(SUB_MESSAGES)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .addOnSuccessListener(cb::onLoaded)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static ListenerRegistration listenNotifications(
            @NonNull String uid, @NonNull EventListener<QuerySnapshot> listener) {
        return db().collection(COL_NOTIFS)
                .whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(listener);
    }

    public static void markNotificationRead(@Nullable String docId) {
        if (docId == null || docId.isEmpty()) return;
        db().collection(COL_NOTIFS).document(docId).update("read", true);
    }

    public static void loadRoom(@Nullable String roomId, @NonNull OnDoc cb) {
        if (roomId == null || roomId.isEmpty()) { cb.onError("Thiếu mã phòng"); return; }
        db().collection(COL_ROOMS).document(roomId).get()
                .addOnSuccessListener(cb::onLoaded)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Màn chat chi tiết ──────────────────────────────────────────────────────

    public static ListenerRegistration listenMessages(
            @NonNull String roomId, @NonNull EventListener<QuerySnapshot> listener) {
        return db().collection(COL_ROOMS).document(roomId).collection(SUB_MESSAGES)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    public static void sendMessage(@NonNull String roomId, @NonNull Map<String, Object> msg,
                                   @NonNull OnCreated cb) {
        db().collection(COL_ROOMS).document(roomId).collection(SUB_MESSAGES).add(msg)
                .addOnSuccessListener(ref -> cb.onCreated(ref.getId()))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Cập nhật lastMessage/lastTimestamp/lastSenderId/unreadBy (set + merge). */
    public static void updateRoomLast(@NonNull String roomId, @NonNull Map<String, Object> fields) {
        db().collection(COL_ROOMS).document(roomId).set(fields, SetOptions.merge());
    }

    /** Đánh dấu đã đọc: clear unreadBy nếu chính uid là người chưa đọc. */
    public static void markRoomRead(@Nullable String roomId, @Nullable String uid) {
        if (roomId == null || uid == null) return;
        db().collection(COL_ROOMS).document(roomId).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    if (uid.equals(doc.getString("unreadBy"))) {
                        db().collection(COL_ROOMS).document(roomId).update("unreadBy", "");
                    }
                });
    }

    /** Clear unreadBy ngay (optimistic ở danh sách hội thoại). */
    public static void clearUnread(@Nullable String roomId) {
        if (roomId == null || roomId.isEmpty()) return;
        db().collection(COL_ROOMS).document(roomId).update("unreadBy", "");
    }

    /** Đánh dấu tin của đối phương là đã đọc (status=2). */
    public static void markPartnerMessagesRead(@NonNull String roomId, @Nullable String partnerId) {
        db().collection(COL_ROOMS).document(roomId).collection(SUB_MESSAGES)
                .whereEqualTo("senderId", partnerId)
                .whereLessThan("status", 2)
                .get().addOnSuccessListener(snaps -> {
                    if (snaps.isEmpty()) return;
                    WriteBatch batch = db().batch();
                    for (DocumentSnapshot doc : snaps) batch.update(doc.getReference(), "status", 2);
                    batch.commit();
                });
    }

    public static void recallMessage(@NonNull String roomId, @NonNull String messageId,
                                     @NonNull OnResult cb) {
        db().collection(COL_ROOMS).document(roomId).collection(SUB_MESSAGES).document(messageId)
                .update("recalled", true, "content", "", "imageUrl", "",
                        "videoUrl", "", "thumbnailUrl", "")
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Danh sách phòng để chuyển tiếp. */
    public static void loadForwardTargets(@NonNull String uid, @NonNull OnSnapshot cb) {
        db().collection(COL_ROOMS)
                .whereArrayContains("participants", uid)
                .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(cb::onLoaded)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void forwardMessage(@NonNull String targetRoomId, @NonNull Map<String, Object> msg,
                                      @NonNull String preview, @NonNull OnResult cb) {
        db().collection(COL_ROOMS).document(targetRoomId).collection(SUB_MESSAGES).add(msg)
                .addOnSuccessListener(ref -> {
                    db().collection(COL_ROOMS).document(targetRoomId)
                            .update("lastMessage", preview,
                                    "lastTimestamp", FieldValue.serverTimestamp());
                    cb.onSuccess();
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void submitReport(@NonNull Map<String, Object> report, @NonNull OnResult cb) {
        db().collection(COL_REPORTS).add(report)
                .addOnSuccessListener(ref -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void submitMessageReport(@NonNull Map<String, Object> report, @NonNull OnResult cb) {
        db().collection(COL_MSG_REPORTS).add(report)
                .addOnSuccessListener(ref -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Chặn 2 chiều ───────────────────────────────────────────────────────────

    public static ListenerRegistration listenBlock(
            @NonNull String blockerId, @NonNull String blockedId,
            @NonNull EventListener<DocumentSnapshot> listener) {
        return db().collection(COL_BLOCKS).document(blockerId + "_" + blockedId)
                .addSnapshotListener(listener);
    }

    public static void block(@NonNull String blockerId, @NonNull String blockedId,
                             @NonNull OnResult cb) {
        Map<String, Object> b = new java.util.HashMap<>();
        b.put("blockerId", blockerId);
        b.put("blockedId", blockedId);
        b.put("timestamp", FieldValue.serverTimestamp());
        db().collection(COL_BLOCKS).document(blockerId + "_" + blockedId).set(b)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void unblock(@NonNull String blockerId, @NonNull String blockedId,
                               @NonNull OnResult cb) {
        db().collection(COL_BLOCKS).document(blockerId + "_" + blockedId).delete()
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
