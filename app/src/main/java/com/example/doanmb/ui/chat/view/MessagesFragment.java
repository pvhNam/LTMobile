package com.example.doanmb.ui.chat.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.ui.home.view.MainActivity;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.R;
import com.example.doanmb.ui.home.adapter.ShortcutAdapter;
import com.example.doanmb.data.model.Car;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessagesFragment extends Fragment {

    private LinearLayout layoutNotLoggedIn, layoutEmpty, layoutContent;
    private RecyclerView rvConversations, rvShortcuts;
    private EditText etSearch;
    private TextView tvGreeting;
    private ImageView imgAvatar;

    private FirebaseFirestore db;
    private ListenerRegistration listener;
    private ListenerRegistration notifListener; // Lắng nghe thông báo real-time
    private ConversationAdapter adapter;
    private ShortcutAdapter shortcutAdapter;

    // Toàn bộ danh sách hội thoại gốc (chưa lọc)
    private final List<Map<String, Object>> convList     = new ArrayList<>();
    // Danh sách hiện lên RecyclerView (đã lọc)
    private final List<Map<String, Object>> filteredList = new ArrayList<>();
    private final List<Map<String, Object>> shortcutList = new ArrayList<>();

    // Đang tìm kiếm trong tin nhắn hay không
    private boolean isSearchingMessages = false;
    // Kết quả tìm kiếm trong nội dung tin nhắn:
    // mỗi item = map hội thoại + thêm key "matchedMessage" (nội dung tin nhắn khớp)
    private final List<Map<String, Object>> messageSearchResults = new ArrayList<>();

    private android.widget.FrameLayout frameMsgContent;
    private LinearLayout layoutChatTabContent;
    private View layoutNotificationTabContent;
    private androidx.cardview.widget.CardView tabChat, tabNotification;
    private LinearLayout contentTabChat, contentTabNotification;
    private TextView tvTabChat, tvTabNotification;
    private boolean isChatTabActive = true;
    private String selectedShortcutPartnerId = null;

    // Notification tab
    private RecyclerView rvNotifications;
    private TextView tvNotifEmpty;
    private NotifAdapter notifAdapter;
    private final List<Map<String, Object>> notifList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_messages, container, false);
        db = FirebaseFirestore.getInstance();

        layoutNotLoggedIn = view.findViewById(R.id.layout_msg_not_logged_in);
        layoutEmpty       = view.findViewById(R.id.layout_msg_empty);
        layoutContent     = view.findViewById(R.id.layout_msg_content);
        etSearch          = view.findViewById(R.id.et_search_chat);
        tvGreeting        = view.findViewById(R.id.tv_msg_greeting);
        imgAvatar         = view.findViewById(R.id.img_msg_avatar);

        rvConversations = view.findViewById(R.id.rv_conversations);
        rvConversations.setLayoutManager(new LinearLayoutManager(getContext()));
        rvConversations.setNestedScrollingEnabled(false);
        adapter = new ConversationAdapter(filteredList, db);
        rvConversations.setAdapter(adapter);

        rvShortcuts = view.findViewById(R.id.rv_shortcuts);
        rvShortcuts.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvShortcuts.setNestedScrollingEnabled(false);
        shortcutAdapter = new ShortcutAdapter(shortcutList, partnerId -> {
            // Khi người dùng bấm vào Avatar, Adapter sẽ trả partnerId về đây
            onShortcutClicked(partnerId);
        });
        rvShortcuts.setAdapter(shortcutAdapter);

        setupSearch();

        frameMsgContent              = view.findViewById(R.id.frame_msg_content);
        layoutChatTabContent         = view.findViewById(R.id.layout_chat_tab_content);
        layoutNotificationTabContent = view.findViewById(R.id.layout_notification_tab_content);
        tabChat                       = view.findViewById(R.id.tab_chat);
        tabNotification               = view.findViewById(R.id.tab_notification);
        contentTabChat                = view.findViewById(R.id.content_tab_chat);
        contentTabNotification        = view.findViewById(R.id.content_tab_notification);
        tvTabChat                      = view.findViewById(R.id.tv_tab_chat);
        tvTabNotification              = view.findViewById(R.id.tv_tab_notification);

        // Setup notification RecyclerView
        rvNotifications = view.findViewById(R.id.rv_notifications);
        tvNotifEmpty    = view.findViewById(R.id.tv_notif_empty);
        if (rvNotifications != null) {
            notifAdapter = new NotifAdapter();
            rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
            rvNotifications.setAdapter(notifAdapter);
        }

        tabChat.setOnClickListener(v -> selectTab(true));
        tabNotification.setOnClickListener(v -> selectTab(false));
        return view;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Search  —  tìm theo tên cuộc trò chuyện  VÀ  nội dung tin nhắn
    // ══════════════════════════════════════════════════════════════════════════
    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (selectedShortcutPartnerId != null) {
                    selectedShortcutPartnerId = null;
                    shortcutAdapter.setSelectedPartnerId(null);
                }
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    // Quay lại hiển thị tất cả hội thoại
                    isSearchingMessages = false;
                    messageSearchResults.clear();
                    showAllConversations();
                } else {
                    searchEverything(query);
                }
            }
        });
    }

    private void searchEverything(String query) {
        String lower = query.toLowerCase();

        // ── Bước 1: lọc local theo tên ────────────────────────────────────
        List<Map<String, Object>> nameMatches = new ArrayList<>();
        for (Map<String, Object> item : convList) {
            String carName     = String.valueOf(item.getOrDefault("carName", "")).toLowerCase();
            String partnerName = String.valueOf(item.getOrDefault("partnerName", "")).toLowerCase();
            if (carName.contains(lower) || partnerName.contains(lower)) {
                nameMatches.add(item);
            }
        }

        // ── Bước 2: tìm trong nội dung tin nhắn Firestore ─────────────────
        messageSearchResults.clear();
        isSearchingMessages = true;

        if (convList.isEmpty()) {
            mergeAndShow(nameMatches);
            return;
        }

        final int[] remaining = {convList.size()};
        final List<Map<String, Object>> msgMatches = new ArrayList<>();

        for (Map<String, Object> conv : convList) {
            String roomId = String.valueOf(conv.getOrDefault("roomId", ""));
            if (roomId.isEmpty()) {
                decrementAndMerge(remaining, nameMatches, msgMatches);
                continue;
            }

            db.collection("chat_rooms").document(roomId)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(200)
                    .get()
                    .addOnSuccessListener(snapshots -> {
                        if (!isAdded()) return;
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Boolean recalled = doc.getBoolean("recalled");
                            if (Boolean.TRUE.equals(recalled)) continue;

                            String content = doc.getString("content");
                            if (content != null && content.toLowerCase().contains(lower)) {
                                Map<String, Object> resultItem = new HashMap<>(conv);
                                resultItem.put("matchedMessage", content);
                                synchronized (msgMatches) {
                                    boolean alreadyAdded = false;
                                    for (Map<String, Object> m : msgMatches) {
                                        if (roomId.equals(m.get("roomId"))) {
                                            alreadyAdded = true;
                                            break;
                                        }
                                    }
                                    if (!alreadyAdded) msgMatches.add(resultItem);
                                }
                                break;
                            }
                        }
                        decrementAndMerge(remaining, nameMatches, msgMatches);
                    })
                    .addOnFailureListener(e -> decrementAndMerge(remaining, nameMatches, msgMatches));
        }
    }

    private void decrementAndMerge(int[] remaining,
                                   List<Map<String, Object>> nameMatches,
                                   List<Map<String, Object>> msgMatches) {
        synchronized (remaining) {
            remaining[0]--;
            if (remaining[0] <= 0) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> mergeAndShow(nameMatches, msgMatches));
                }
            }
        }
    }

    private void mergeAndShow(List<Map<String, Object>> nameMatches,
                              List<Map<String, Object>> msgMatches) {
        List<Map<String, Object>> merged = new ArrayList<>(nameMatches);
        for (Map<String, Object> m : msgMatches) {
            String roomId = String.valueOf(m.getOrDefault("roomId", ""));
            boolean exists = false;
            for (Map<String, Object> n : nameMatches) {
                if (roomId.equals(n.getOrDefault("roomId", ""))) { exists = true; break; }
            }
            if (!exists) merged.add(m);
        }
        showFiltered(merged, etSearch.getText().toString().trim());
    }

    private void mergeAndShow(List<Map<String, Object>> nameMatches) {
        showFiltered(nameMatches, etSearch.getText().toString().trim());
    }

    private void showAllConversations() {
        filteredList.clear();
        filteredList.addAll(convList);
        adapter.setSearchQuery("");
        adapter.notifyDataSetChanged();
        updateEmptyState(false, "");
    }

    private void showFiltered(List<Map<String, Object>> results, String query) {
        filteredList.clear();
        filteredList.addAll(results);
        adapter.setSearchQuery(query);
        adapter.notifyDataSetChanged();
        updateEmptyState(filteredList.isEmpty(), query);
    }

    private void updateEmptyState(boolean empty, String query) {
        if (layoutEmpty == null) return;
        if (empty && !query.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (layoutNotLoggedIn != null) layoutNotLoggedIn.setVisibility(View.VISIBLE);
            if (layoutContent != null)     layoutContent.setVisibility(View.GONE);
            if (layoutEmpty != null)       layoutEmpty.setVisibility(View.GONE);
            return;
        }
        if (layoutNotLoggedIn != null) layoutNotLoggedIn.setVisibility(View.GONE);
        if (layoutContent != null)     layoutContent.setVisibility(View.VISIBLE);

        loadUserProfile(user.getUid());
        loadConversations(user.getUid());
    }

    private void loadUserProfile(String uid) {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists() && isAdded()) {
                String name      = doc.getString("name");
                String avatarUrl = doc.getString("avatarUrl");
                if (tvGreeting != null)
                    tvGreeting.setText("Hi, " + (name != null ? name : "User"));
                if (avatarUrl != null && !avatarUrl.isEmpty() && imgAvatar != null) {
                    ImageLoader.loadAvatar(imgAvatar, avatarUrl);
                }
            }
        });
    }

    private void loadConversations(String uid) {
        if (listener != null) listener.remove();

        listener = db.collection("chat_rooms")
                .whereArrayContains("participants", uid)
                .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || !isAdded()) return;

                    convList.clear();
                    shortcutList.clear();
                    Map<String, Boolean> addedPartners = new HashMap<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        Map<String, Object> data = new HashMap<>(doc.getData());
                        data.put("roomId", doc.getId());
                        android.util.Log.d("SNAPSHOT_DEBUG",
                                "RAW doc=" + doc.getId() +
                                        " unreadBy='" + doc.getString("unreadBy") + "'" +
                                        " lastMsg='" + doc.getString("lastMessage") + "'");

                        String buyerId   = (String) data.get("buyerId");
                        String sellerId  = (String) data.get("sellerId");
                        String partnerId = uid.equals(buyerId) ? sellerId : buyerId;
                        data.put("partnerId", partnerId);

                        if (partnerId != null) {
                            db.collection("users").document(partnerId)
                                    .get().addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists() && isAdded()) {
                                            String name   = userDoc.getString("name");
                                            String avatar = userDoc.getString("avatarUrl");
                                            data.put("partnerName",   name);
                                            data.put("partnerAvatar", avatar);

                                            if (!addedPartners.containsKey(partnerId)
                                                    && shortcutList.size() < 10) {
                                                shortcutList.add(new HashMap<>(data));
                                                addedPartners.put(partnerId, true);
                                                shortcutAdapter.notifyDataSetChanged();
                                            }

                                            String q = etSearch != null
                                                    ? etSearch.getText().toString().trim() : "";
                                            if (!q.isEmpty()) {
                                                searchEverything(q);
                                            } else {
                                                adapter.notifyDataSetChanged();
                                                showAllConversations();
                                            }
                                        }
                                    });
                        }
                        convList.add(data);
                    }

                    String q = etSearch != null ? etSearch.getText().toString().trim() : "";
                    if (q.isEmpty()) showAllConversations();
                    else searchEverything(q);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) listener.remove();
        if (notifListener != null) {
            notifListener.remove();
            notifListener = null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ConversationAdapter
    // ══════════════════════════════════════════════════════════════════════════
    static class ConversationAdapter
            extends RecyclerView.Adapter<ConversationAdapter.VH> {

        private final List<Map<String, Object>> list;
        private final FirebaseFirestore db;
        private String searchQuery = "";

        ConversationAdapter(List<Map<String, Object>> list, FirebaseFirestore db) {
            this.list = list;
            this.db   = db;
        }

        private String getCurrentUid() {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            return u != null ? u.getUid() : "";
        }

        void setSearchQuery(String q) { this.searchQuery = q != null ? q : ""; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_conversation, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = list.get(position);

            String carName      = String.valueOf(item.getOrDefault("carName",      ""));
            String partnerName  = String.valueOf(item.getOrDefault("partnerName",  "Đang tải..."));
            String partnerAvatar= String.valueOf(item.getOrDefault("partnerAvatar",""));
            String matchedMsg   = (String) item.get("matchedMessage");
            String lastMsg      = matchedMsg != null ? matchedMsg
                    : String.valueOf(item.getOrDefault("lastMessage", ""));

            h.tvCarName.setText(carName);
            h.tvName.setText(partnerName);

            if (lastMsg.isEmpty()) lastMsg = "Bắt đầu trò chuyện...";
            h.tvLastMsg.setText(lastMsg);

            // ── Hiển thị chấm xanh nếu currentUser chưa đọc tin nhắn cuối ──
            String unreadBy = String.valueOf(item.getOrDefault("unreadBy", ""));
            boolean hasUnread = getCurrentUid().equals(unreadBy);
            android.util.Log.d("UNREAD_DEBUG",
                    "roomId=" + item.getOrDefault("roomId","?") +
                            " unreadBy='" + unreadBy + "'"+
                            " currentUid='" + getCurrentUid() + "'"+
                            " hasUnread=" + hasUnread);
            if (h.viewUnreadDot != null)
                h.viewUnreadDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
            h.tvLastMsg.setTypeface(null,
                    hasUnread ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            h.tvLastMsg.setTextColor(hasUnread ? 0xFF1A1A2E : 0xFF6B7280);

            if (!partnerAvatar.isEmpty() && !"null".equals(partnerAvatar)) {
                h.tvAvatar.setVisibility(View.GONE);
                h.ivAvatar.setVisibility(View.VISIBLE);
                ImageLoader.loadAvatar(h.ivAvatar, partnerAvatar);
            } else if (!partnerName.isEmpty() && !"Đang tải...".equals(partnerName)) {
                h.tvAvatar.setVisibility(View.VISIBLE);
                h.ivAvatar.setVisibility(View.GONE);
                h.tvAvatar.setText(String.valueOf(partnerName.charAt(0)).toUpperCase());
            } else {
                h.tvAvatar.setVisibility(View.VISIBLE);
                h.ivAvatar.setVisibility(View.GONE);
                h.tvAvatar.setText("?");
            }

            h.itemView.setOnClickListener(v -> {
                String roomId    = String.valueOf(item.getOrDefault("roomId",   ""));
                String buyerId   = String.valueOf(item.getOrDefault("buyerId",  ""));
                String sellerId  = String.valueOf(item.getOrDefault("sellerId", ""));
                String partnerId = getCurrentUid().equals(buyerId) ? sellerId : buyerId;
                String carPrice  = String.valueOf(item.getOrDefault("carPrice", ""));
                String carImage  = String.valueOf(item.getOrDefault("carImage", ""));
                String carId     = String.valueOf(item.getOrDefault("carId",    ""));
                String carType   = String.valueOf(item.getOrDefault("carType",  "sale"));

                // Ẩn chấm xanh ngay lập tức + cập nhật Firestore
                String unreadByNow = String.valueOf(item.getOrDefault("unreadBy", ""));
                if (getCurrentUid().equals(unreadByNow)) {
                    item.put("unreadBy", "");
                    if (h.viewUnreadDot != null) h.viewUnreadDot.setVisibility(View.GONE);
                    h.tvLastMsg.setTypeface(null, android.graphics.Typeface.NORMAL);
                    h.tvLastMsg.setTextColor(0xFF6B7280);
                    if (!roomId.isEmpty()) {
                        db.collection("chat_rooms").document(roomId).update("unreadBy", "");
                    }
                }

                Intent intent = new Intent(v.getContext(), ChatDetailActivity.class);
                intent.putExtra("ROOM_ID",      roomId);
                intent.putExtra("PARTNER_ID",   partnerId);
                intent.putExtra("PARTNER_NAME", partnerName);

                Car car = new Car(carName, carPrice, "", 0);
                car.setId(carId);
                car.setImageUrl(carImage);
                car.setSellerId(sellerId);
                car.setType(carType);
                intent.putExtra("CAR_DATA", car);
                v.getContext().startActivity(intent);
            });
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView  tvAvatar, tvName, tvCarName, tvLastMsg;
            ImageView ivAvatar;
            View      viewUnreadDot;

            VH(@NonNull View v) {
                super(v);
                tvAvatar      = v.findViewById(R.id.tvConvAvatar);
                ivAvatar      = v.findViewById(R.id.ivConvAvatar);
                tvName        = v.findViewById(R.id.tvConvName);
                tvCarName     = v.findViewById(R.id.tvConvCarName);
                tvLastMsg     = v.findViewById(R.id.tvConvLastMsg);
                viewUnreadDot = v.findViewById(R.id.viewConvUnreadDot);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Xử lý chuyển Tab: Trò chuyện / Thông báo
    // ══════════════════════════════════════════════════════════════════════════
    private void selectTab(boolean chatSelected) {
        if (chatSelected == isChatTabActive) return;
        isChatTabActive = chatSelected;

        if (!chatSelected) loadNotifications();

        View incoming = chatSelected
                ? layoutChatTabContent
                : (View) layoutNotificationTabContent;
        View outgoing = chatSelected
                ? (View) layoutNotificationTabContent
                : layoutChatTabContent;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int slideIn  = chatSelected ? -screenWidth :  screenWidth;
        int slideOut = chatSelected ?  screenWidth : -screenWidth;

        long DURATION = 300L;
        android.view.animation.DecelerateInterpolator interpolator =
                new android.view.animation.DecelerateInterpolator(1.5f);

        incoming.setTranslationX(slideIn);
        incoming.setVisibility(View.VISIBLE);

        outgoing.animate()
                .translationX(slideOut)
                .setDuration(DURATION)
                .setInterpolator(interpolator)
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setTranslationX(0f);
                })
                .start();

        incoming.animate()
                .translationX(0f)
                .setDuration(DURATION)
                .setInterpolator(interpolator)
                .start();

        int activeColor   = android.graphics.Color.parseColor("#2F54D4");
        int inactiveColor = android.graphics.Color.WHITE;

        if (chatSelected) {
            contentTabChat.setBackgroundResource(R.drawable.bg_tab_active_pill);
            contentTabNotification.setBackground(null);
        } else {
            contentTabNotification.setBackgroundResource(R.drawable.bg_tab_active_pill);
            contentTabChat.setBackground(null);
        }

        ValueAnimator colorAnimChat = ValueAnimator.ofObject(new ArgbEvaluator(),
                chatSelected ? inactiveColor : activeColor,
                chatSelected ? activeColor   : inactiveColor);
        colorAnimChat.setDuration(DURATION);
        colorAnimChat.addUpdateListener(a ->
                tvTabChat.setTextColor((int) a.getAnimatedValue()));
        colorAnimChat.start();

        ValueAnimator colorAnimNotif = ValueAnimator.ofObject(new ArgbEvaluator(),
                chatSelected ? activeColor   : inactiveColor,
                chatSelected ? inactiveColor : activeColor);
        colorAnimNotif.setDuration(DURATION);
        colorAnimNotif.addUpdateListener(a ->
                tvTabNotification.setTextColor((int) a.getAnimatedValue()));
        colorAnimNotif.start();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  loadNotifications() — THAY THẾ
    // ══════════════════════════════════════════════════════════════════════════════
    private void loadNotifications() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || rvNotifications == null) return;

        if (notifListener != null) {
            notifListener.remove();
            notifListener = null;
        }

        notifListener = FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("userId", user.getUid())
                // ✅ FIX: Sắp xếp mới nhất lên đầu trực tiếp từ Firestore
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || !isAdded()) return;

                    notifList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Map<String, Object> data = new HashMap<>(doc.getData());
                        data.put("docId", doc.getId());
                        notifList.add(data);
                        // ✅ FIX: KHÔNG tự động đánh dấu read ở đây
                        // → chỉ đánh dấu khi user BẤM vào từng thông báo (xem onBindViewHolder)
                    }

                    notifAdapter.notifyDataSetChanged();
                    boolean empty = notifList.isEmpty();
                    if (tvNotifEmpty != null)
                        tvNotifEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    if (rvNotifications != null)
                        rvNotifications.setVisibility(empty ? View.GONE : View.VISIBLE);
                });
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  NotifAdapter — THAY THẾ
    // ══════════════════════════════════════════════════════════════════════════════
    private class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {

        private final SimpleDateFormat SDF =
                new SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.getDefault());

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_notification, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = notifList.get(position);

            String title      = str(item, "title");
            String body       = str(item, "body");
            String type       = str(item, "type");
            String senderId   = str(item, "senderId");
            String senderName = str(item, "senderName");
            String roomId     = str(item, "roomId");
            String docId      = str(item, "docId");
            String orderId    = str(item, "orderId");

            h.tvTitle.setText(senderName.isEmpty() ? (title.isEmpty() ? "Thông báo" : title) : senderName);
            h.tvBody.setText(body.isEmpty() ? "Đã gửi một tin nhắn" : body);

            // ── THÊM BƯỚC 5: Phân biệt loại icon bằng Emoji cho tvTypeIcon ──
            switch (type) {
                case "order_confirmed":
                    h.ivIcon.setImageResource(R.drawable.ic_verified_check); // icon tick xanh
                    break;
                case "order_rejected":
                    h.ivIcon.setImageResource(R.drawable.ic_warning);        // icon cảnh báo
                    break;
                case "order_sent":
                    h.ivIcon.setImageResource(R.drawable.ic_admin_orders);   // icon đơn hàng
                    break;
                case "invoice":
                case "invoice_paid":
                    h.ivIcon.setImageResource(R.drawable.ic_admin_orders);   // hóa đơn
                    break;
                default: // chat
                    h.ivIcon.setImageResource(R.drawable.ic_nav_message);
                    break;
            }
            // ────────────────────────────────────────────────────────────────

            Object createdAt = item.get("createdAt");
            if (createdAt instanceof com.google.firebase.Timestamp) {
                h.tvTime.setText(SDF.format(
                        ((com.google.firebase.Timestamp) createdAt).toDate()));
            } else {
                h.tvTime.setText("");
            }

            Object read = item.get("read");
            h.viewUnreadDot.setVisibility(
                    Boolean.FALSE.equals(read) ? View.VISIBLE : View.GONE);

            String initial = (!senderName.isEmpty())
                    ? String.valueOf(senderName.charAt(0)).toUpperCase()
                    : "?";
            h.tvAvatar.setText(initial);
            h.tvAvatar.setVisibility(View.VISIBLE);
            h.ivAvatar.setVisibility(View.GONE);

            if (!senderId.isEmpty()) {
                FirebaseFirestore.getInstance()
                        .collection("users").document(senderId).get()
                        .addOnSuccessListener(doc -> {
                            if (doc == null || !doc.exists() || !isAdded()) return;
                            String avatarUrl = doc.getString("avatarUrl");
                            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                h.ivAvatar.setVisibility(View.VISIBLE);
                                h.tvAvatar.setVisibility(View.GONE);
                                ImageLoader.loadAvatar(h.ivAvatar, avatarUrl);
                            } else {
                                String name = doc.getString("name");
                                if (name != null && !name.isEmpty()) {
                                    h.tvAvatar.setText(
                                            String.valueOf(name.charAt(0)).toUpperCase());
                                }
                            }
                        });
            }

            // ── THÊM BƯỚC 5: Xử lý Click tùy theo loại thông báo ───────────
            h.itemView.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos == RecyclerView.NO_ID) return;

                // Đánh dấu đã đọc (ẩn chấm xanh trên UI & update Firestore)
                notifList.get(pos).put("read", true);
                notifyItemChanged(pos);
                if (!docId.isEmpty()) {
                    FirebaseFirestore.getInstance()
                            .collection("notifications")
                            .document(docId)
                            .update("read", true);
                }

                // Hóa đơn → mở màn thanh toán hóa đơn
                if ("invoice".equals(type) && !orderId.isEmpty()) {
                    Intent invoice = new Intent(v.getContext(),
                            com.example.doanmb.ui.car.view.InvoiceActivity.class);
                    invoice.putExtra("ORDER_ID", orderId);
                    v.getContext().startActivity(invoice);
                    return;
                }

                // Điều hướng dựa vào type
                if ("chat".equals(type) && !roomId.isEmpty()) {
                    // Mở màn hình chat như cũ
                    FirebaseFirestore.getInstance()
                            .collection("chat_rooms")
                            .document(roomId)
                            .get()
                            .addOnSuccessListener(roomDoc -> {
                                Intent intent = new Intent(v.getContext(), ChatDetailActivity.class);
                                intent.putExtra("ROOM_ID",      roomId);
                                intent.putExtra("PARTNER_ID",   senderId);
                                intent.putExtra("PARTNER_NAME",
                                        senderName.isEmpty() ? "Người dùng" : senderName);

                                if (roomDoc.exists()) {
                                    String carName2  = roomDoc.getString("carName");
                                    String carPrice2 = roomDoc.getString("carPrice");
                                    String carImage2 = roomDoc.getString("carImage");
                                    String carId2    = roomDoc.getString("carId");
                                    String carType2  = roomDoc.getString("carType");
                                    String sellerId2 = roomDoc.getString("sellerId");

                                    Car car =
                                            new Car(
                                                    carName2  != null ? carName2  : "",
                                                    carPrice2 != null ? carPrice2 : "",
                                                    "", 0);
                                    car.setId(carId2         != null ? carId2    : "");
                                    car.setImageUrl(carImage2 != null ? carImage2 : "");
                                    car.setSellerId(sellerId2 != null ? sellerId2 : "");
                                    car.setType(carType2     != null ? carType2  : "sale");
                                    intent.putExtra("CAR_DATA", car);
                                }
                                v.getContext().startActivity(intent);
                            })
                            .addOnFailureListener(e -> {
                                Intent intent = new Intent(v.getContext(), ChatDetailActivity.class);
                                intent.putExtra("ROOM_ID",      roomId);
                                intent.putExtra("PARTNER_ID",   senderId);
                                intent.putExtra("PARTNER_NAME",
                                        senderName.isEmpty() ? "Người dùng" : senderName);
                                v.getContext().startActivity(intent);
                            });
                } else {
                    // Thông báo đơn hàng → mở tab Quản lí
                    if (getActivity() instanceof MainActivity) {
                        MainActivity main = (MainActivity) getActivity();
                        main.openManageRequestsTab();
                    }
                }
            });
            // ────────────────────────────────────────────────────────────────
        }

        @Override
        public int getItemCount() { return notifList.size(); }

        private String str(Map<String, Object> m, String key) {
            Object v = m.get(key);
            return v != null ? v.toString() : "";
        }

        class VH extends RecyclerView.ViewHolder {
            TextView  tvTitle, tvBody, tvTime, tvAvatar;
            ImageView ivAvatar, ivIcon; // Khai báo ivIcon thay vì tvTypeIcon
            View      viewUnreadDot;

            VH(@NonNull View v) {
                super(v);
                tvTitle      = v.findViewById(R.id.tv_notif_title);
                tvBody       = v.findViewById(R.id.tv_notif_body);
                tvTime       = v.findViewById(R.id.tv_notif_time);
                tvAvatar     = v.findViewById(R.id.tv_notif_avatar);
                ivAvatar     = v.findViewById(R.id.iv_notif_avatar);
                viewUnreadDot= v.findViewById(R.id.view_unread_dot);

                // Nhớ đổi ID này cho khớp với ImageView trong file layout XML của bạn
                ivIcon       = v.findViewById(R.id.tv_notif_type_icon);
            }
        }
    }

    public void onShortcutClicked(String partnerId) {
        if (partnerId.equals(selectedShortcutPartnerId)) {
            selectedShortcutPartnerId = null;
            shortcutAdapter.setSelectedPartnerId(null);
            String currentSearch = etSearch.getText().toString().trim();
            if (currentSearch.isEmpty()) {
                showAllConversations();
            } else {
                searchEverything(currentSearch);
            }
        } else {
            selectedShortcutPartnerId = partnerId;
            shortcutAdapter.setSelectedPartnerId(partnerId);
            List<Map<String, Object>> userSpecificList = new ArrayList<>();
            for (Map<String, Object> conv : convList) {
                String pId = (String) conv.get("partnerId");
                if (partnerId.equals(pId)) {
                    userSpecificList.add(conv);
                }
            }
            showFiltered(userSpecificList, "");
        }
    }
}