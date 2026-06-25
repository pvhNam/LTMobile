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

import com.example.doanmb.core.helper.ImageLoader;
import com.example.doanmb.R;
import com.example.doanmb.ui.home.adapter.ShortcutAdapter;
import com.example.doanmb.data.model.Car;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import androidx.transition.Slide;
import android.view.Gravity;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessagesFragment extends Fragment {

    private LinearLayout layoutNotLoggedIn, layoutEmpty, layoutContent;
    private RecyclerView rvConversations, rvShortcuts;
    private EditText etSearch;
    private TextView tvGreeting;
    private ImageView imgAvatar;

    private FirebaseFirestore db;
    private ListenerRegistration listener;
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

    /**
     * Tìm kiếm đa năng:
     * 1. Lọc hội thoại theo tên xe hoặc tên đối phương (instant, local)
     * 2. Tìm trong nội dung tin nhắn của từng phòng chat (Firestore query)
     * Kết quả 2 loại gộp lại không trùng, hiển thị cùng một danh sách.
     */
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
        // Chạy song song cho tất cả roomId
        messageSearchResults.clear();
        isSearchingMessages = true;

        if (convList.isEmpty()) {
            // Chưa có hội thoại nào → chỉ hiện kết quả tên
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
                    .limit(200) // giới hạn để không quá nặng
                    .get()
                    .addOnSuccessListener(snapshots -> {
                        if (!isAdded()) return;
                        for (QueryDocumentSnapshot doc : snapshots) {
                            // Bỏ qua tin bị thu hồi
                            Boolean recalled = doc.getBoolean("recalled");
                            if (Boolean.TRUE.equals(recalled)) continue;

                            String content = doc.getString("content");
                            if (content != null && content.toLowerCase().contains(lower)) {
                                // Tìm thấy tin nhắn khớp trong phòng này
                                // Thêm conv vào kết quả nếu chưa có (tránh trùng)
                                Map<String, Object> resultItem = new HashMap<>(conv);
                                // Gắn đoạn tin nhắn khớp để hiển thị preview
                                resultItem.put("matchedMessage", content);
                                synchronized (msgMatches) {
                                    // Kiểm tra trùng roomId
                                    boolean alreadyAdded = false;
                                    for (Map<String, Object> m : msgMatches) {
                                        if (roomId.equals(m.get("roomId"))) {
                                            alreadyAdded = true;
                                            break;
                                        }
                                    }
                                    if (!alreadyAdded) msgMatches.add(resultItem);
                                }
                                break; // chỉ cần 1 tin khớp mỗi phòng
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

    /** Gộp kết quả tên + kết quả tin nhắn (không trùng roomId) rồi hiển thị */
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

    /** Overload cho khi chỉ có nameMatches (convList rỗng) */
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

                        String buyerId   = (String) data.get("buyerId");
                        String sellerId  = (String) data.get("sellerId");
                        String partnerId = uid.equals(buyerId) ? sellerId : buyerId;
                        data.put("partnerId", partnerId);
                        // Load tên + avatar đối phương
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

                                            // Nếu đang tìm kiếm thì re-run search
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

                    // Hiển thị ngay với dữ liệu hiện có
                    String q = etSearch != null ? etSearch.getText().toString().trim() : "";
                    if (q.isEmpty()) showAllConversations();
                    else searchEverything(q);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) listener.remove();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ConversationAdapter  —  hỗ trợ highlight từ khoá + preview tin nhắn khớp
    // ══════════════════════════════════════════════════════════════════════════
    static class ConversationAdapter
            extends RecyclerView.Adapter<ConversationAdapter.VH> {

        private final List<Map<String, Object>> list;
        private final FirebaseFirestore db;
        private final String currentUid;
        private String searchQuery = "";

        ConversationAdapter(List<Map<String, Object>> list, FirebaseFirestore db) {
            this.list       = list;
            this.db         = db;
            this.currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
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
            String matchedMsg   = (String) item.get("matchedMessage"); // null nếu tìm theo tên
            String lastMsg      = matchedMsg != null ? matchedMsg
                    : String.valueOf(item.getOrDefault("lastMessage", ""));

            h.tvCarName.setText(carName);
            h.tvName.setText(partnerName);

            // Preview: nếu có tin nhắn khớp → hiện nó, ngược lại hiện lastMessage
            if (lastMsg.isEmpty()) lastMsg = "Bắt đầu trò chuyện...";
            h.tvLastMsg.setText(lastMsg);

            // Avatar
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

            // Click → mở ChatDetailActivity
            h.itemView.setOnClickListener(v -> {
                String roomId    = String.valueOf(item.getOrDefault("roomId",   ""));
                String buyerId   = String.valueOf(item.getOrDefault("buyerId",  ""));
                String sellerId  = String.valueOf(item.getOrDefault("sellerId", ""));
                String partnerId = currentUid.equals(buyerId) ? sellerId : buyerId;
                String carPrice  = String.valueOf(item.getOrDefault("carPrice", ""));
                String carImage  = String.valueOf(item.getOrDefault("carImage", ""));
                String carId     = String.valueOf(item.getOrDefault("carId",    ""));
                String carType   = String.valueOf(item.getOrDefault("carType",  "sale"));

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

            VH(@NonNull View v) {
                super(v);
                tvAvatar  = v.findViewById(R.id.tvConvAvatar);
                ivAvatar  = v.findViewById(R.id.ivConvAvatar);
                tvName    = v.findViewById(R.id.tvConvName);
                tvCarName = v.findViewById(R.id.tvConvCarName);
                tvLastMsg = v.findViewById(R.id.tvConvLastMsg);
            }
        }
    }
    // ══════════════════════════════════════════════════════════════════════════
    //  Xử lý chuyển Tab: Trò chuyện / Thông báo
    // ══════════════════════════════════════════════════════════════════════════
    private void selectTab(boolean chatSelected) {
        if (chatSelected == isChatTabActive) return;
        isChatTabActive = chatSelected;

        // ── 1. ANIMATION TRƯỢT NGANG (NỘI DUNG) ──
        TransitionSet transitionSet = new TransitionSet();
        transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transitionSet.setDuration(250);

        Slide slideChat = new Slide();
        slideChat.addTarget(layoutChatTabContent);

        Slide slideNotif = new Slide();
        slideNotif.addTarget(layoutNotificationTabContent);

        if (chatSelected) {
            slideChat.setSlideEdge(Gravity.START);
            slideNotif.setSlideEdge(Gravity.END);
        } else {
            slideChat.setSlideEdge(Gravity.START);
            slideNotif.setSlideEdge(Gravity.END);
        }

        transitionSet.addTransition(slideChat);
        transitionSet.addTransition(slideNotif);
        TransitionManager.beginDelayedTransition(frameMsgContent, transitionSet);

        // ── 2. ĐỔI TRẠNG THÁI HIỂN THỊ NỘI DUNG ──
        layoutChatTabContent.setVisibility(chatSelected ? View.VISIBLE : View.GONE);
        layoutNotificationTabContent.setVisibility(chatSelected ? View.GONE : View.VISIBLE);
        if (!chatSelected) loadNotifications();

        // ── 3. ANIMATION CHUYỂN MÀU (TAB HEADER) ──
        int activeColor   = android.graphics.Color.parseColor("#2F54D4"); // Xanh
        int inactiveColor = android.graphics.Color.WHITE; // Trắng (sẽ tiệp màu với background xung quanh, tạo cảm giác "mất chữ")

        if (chatSelected) {
            // Tab Chat được chọn
            contentTabChat.setBackgroundResource(R.drawable.bg_tab_active_pill);
            tvTabChat.setTextColor(activeColor);

            // Tab Thông báo bị bỏ chọn
            contentTabNotification.setBackground(null);
            tvTabNotification.setTextColor(inactiveColor); // Chữ lập tức thành trắng (biến mất)
        } else {
            // Tab Thông báo được chọn
            contentTabNotification.setBackgroundResource(R.drawable.bg_tab_active_pill);
            tvTabNotification.setTextColor(activeColor);

            // Tab Chat bị bỏ chọn
            contentTabChat.setBackground(null);
            tvTabChat.setTextColor(inactiveColor); // Chữ lập tức thành trắng (biến mất)
        }

        // Animator cho chữ Tab Trò chuyện
        ValueAnimator colorAnimChat = ValueAnimator.ofObject(new ArgbEvaluator(),
                chatSelected ? inactiveColor : activeColor,
                chatSelected ? activeColor : inactiveColor);
        colorAnimChat.setDuration(250);
        colorAnimChat.addUpdateListener(animator ->
                tvTabChat.setTextColor((int) animator.getAnimatedValue()));
        colorAnimChat.start();

        // Animator cho chữ Tab Thông báo
        ValueAnimator colorAnimNotif = ValueAnimator.ofObject(new ArgbEvaluator(),
                chatSelected ? activeColor : inactiveColor,
                chatSelected ? inactiveColor : activeColor);
        colorAnimNotif.setDuration(250);
        colorAnimNotif.addUpdateListener(animator ->
                tvTabNotification.setTextColor((int) animator.getAnimatedValue()));
        colorAnimNotif.start();

        // (Lưu ý: Không đổi màu icon của 2 tab ở đây để chúng luôn giữ màu xanh mặc định)
    }

    private void loadNotifications() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || rvNotifications == null) return;
        // Không dùng orderBy chung với whereEqualTo để tránh phải tạo composite index
        // trên Firestore (nếu thiếu index, query sẽ lỗi âm thầm và customer không thấy
        // thông báo duyệt/từ chối đăng ký tài xế). Sắp xếp ở phía client.
        FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        docs.add(doc);
                    }
                    docs.sort((a, b) -> {
                        Timestamp ta = a.getTimestamp("createdAt");
                        Timestamp tb = b.getTimestamp("createdAt");
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.compareTo(ta);
                    });

                    notifList.clear();
                    for (QueryDocumentSnapshot doc : docs) {
                        notifList.add(doc.getData());
                        // đánh dấu đã đọc
                        if (!Boolean.TRUE.equals(doc.getBoolean("read"))) {
                            doc.getReference().update("read", true);
                        }
                    }
                    notifAdapter.notifyDataSetChanged();
                    boolean empty = notifList.isEmpty();
                    if (tvNotifEmpty != null) tvNotifEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    rvNotifications.setVisibility(empty ? View.GONE : View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    boolean empty = notifList.isEmpty();
                    if (tvNotifEmpty != null) tvNotifEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    rvNotifications.setVisibility(empty ? View.GONE : View.VISIBLE);
                });
    }

    // ── Adapter thông báo ──────────────────────────────────────────────────────
    private class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {
        private final java.text.SimpleDateFormat SDF =
                new java.text.SimpleDateFormat("HH:mm  dd/MM/yyyy", java.util.Locale.getDefault());

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = notifList.get(position);
            h.tvTitle.setText(str(item, "title"));
            h.tvBody.setText(str(item, "body"));
            Object createdAt = item.get("createdAt");
            if (createdAt instanceof Timestamp) {
                h.tvTime.setText(SDF.format(((Timestamp) createdAt).toDate()));
            } else {
                h.tvTime.setText("");
            }
            // chấm đỏ nếu chưa đọc (khi vừa load xong chưa kịp update)
            Object read = item.get("read");
            h.tvDot.setVisibility(Boolean.FALSE.equals(read) ? View.VISIBLE : View.GONE);
        }

        @Override public int getItemCount() { return notifList.size(); }

        private String str(Map<String, Object> m, String key) {
            Object v = m.get(key); return v != null ? v.toString() : "";
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvBody, tvTime, tvDot;
            VH(@NonNull View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_notif_title);
                tvBody  = v.findViewById(R.id.tv_notif_body);
                tvTime  = v.findViewById(R.id.tv_notif_time);
                tvDot   = v.findViewById(R.id.tv_notif_dot);
            }
        }
    }

    public void onShortcutClicked(String partnerId) {
        if (partnerId.equals(selectedShortcutPartnerId)) {
            // Trạng thái: Bấm lại lần 2 vào cùng 1 người -> TẮT LỌC
            selectedShortcutPartnerId = null;

            // Cập nhật lại UI Adapter của phần "Gần đây" để tắt viền sáng (nếu có)
            shortcutAdapter.setSelectedPartnerId(null);

            // Hiển thị lại toàn bộ danh sách (có tính đến việc thanh Search có đang gõ chữ không)
            String currentSearch = etSearch.getText().toString().trim();
            if (currentSearch.isEmpty()) {
                showAllConversations();
            } else {
                searchEverything(currentSearch);
            }

        } else {
            // Trạng thái: Bấm vào 1 người mới -> BẬT LỌC
            selectedShortcutPartnerId = partnerId;

            // Cập nhật UI cho ShortcutAdapter biết ai đang được chọn
            shortcutAdapter.setSelectedPartnerId(partnerId);

            // Tiến hành lọc convList
            List<Map<String, Object>> userSpecificList = new ArrayList<>();
            for (Map<String, Object> conv : convList) {
                String pId = (String) conv.get("partnerId");
                if (partnerId.equals(pId)) {
                    userSpecificList.add(conv);
                }
            }

            // Ép danh sách dưới hiển thị kết quả đã lọc
            showFiltered(userSpecificList, "");
        }
    }

}