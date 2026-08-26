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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.ui.chat.viewmodel.MessagesViewModel;
import com.example.doanmb.ui.home.view.MainActivity;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.data.repository.ChatRepository;
import com.example.doanmb.R;
import com.example.doanmb.ui.home.adapter.ShortcutAdapter;
import com.example.doanmb.data.model.Car;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessagesFragment extends Fragment {

    private LinearLayout layoutNotLoggedIn, layoutEmpty, layoutContent;
    private RecyclerView rvConversations, rvShortcuts;
    private EditText etSearch;
    private TextView tvGreeting;
    private ImageView imgAvatar;

    private MessagesViewModel viewModel;
    private ConversationAdapter adapter;
    private ShortcutAdapter shortcutAdapter;
    private final List<Map<String, Object>> filteredList = new ArrayList<>();
    private final List<Map<String, Object>> shortcutList = new ArrayList<>();
    private String searchQuery = "";

    private android.widget.FrameLayout frameMsgContent;
    private LinearLayout layoutChatTabContent;
    private View layoutNotificationTabContent;
    private androidx.cardview.widget.CardView tabChat, tabNotification;
    private LinearLayout contentTabChat, contentTabNotification;
    private TextView tvTabChat, tvTabNotification;
    private boolean isChatTabActive = true;

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
        EdgeToEdgeUtil.applyHeaderAndScroll(null, view.findViewById(R.id.page_header));

        viewModel = new ViewModelProvider(this).get(MessagesViewModel.class);

        layoutNotLoggedIn = view.findViewById(R.id.layout_msg_not_logged_in);
        layoutEmpty       = view.findViewById(R.id.layout_msg_empty);
        layoutContent     = view.findViewById(R.id.layout_msg_content);
        etSearch          = view.findViewById(R.id.et_search_chat);
        tvGreeting        = view.findViewById(R.id.tv_msg_greeting);
        imgAvatar         = view.findViewById(R.id.img_msg_avatar);

        rvConversations = view.findViewById(R.id.rv_conversations);
        rvConversations.setLayoutManager(new LinearLayoutManager(getContext()));
        rvConversations.setNestedScrollingEnabled(false);
        adapter = new ConversationAdapter(filteredList);
        rvConversations.setAdapter(adapter);

        rvShortcuts = view.findViewById(R.id.rv_shortcuts);
        rvShortcuts.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvShortcuts.setNestedScrollingEnabled(false);
        shortcutAdapter = new ShortcutAdapter(shortcutList, partnerId ->
                viewModel.onShortcutClicked(partnerId));
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

        rvNotifications = view.findViewById(R.id.rv_notifications);
        tvNotifEmpty    = view.findViewById(R.id.tv_notif_empty);
        if (rvNotifications != null) {
            notifAdapter = new NotifAdapter();
            rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
            rvNotifications.setAdapter(notifAdapter);
        }

        tabChat.setOnClickListener(v -> selectTab(true));
        tabNotification.setOnClickListener(v -> selectTab(false));

        observeViewModel();
        return view;
    }

    private void observeViewModel() {
        viewModel.getGreetingName().observe(getViewLifecycleOwner(), name -> {
            if (tvGreeting != null && name != null) tvGreeting.setText(name);
        });
        viewModel.getAvatarUrl().observe(getViewLifecycleOwner(), url -> {
            if (url != null && !url.isEmpty() && imgAvatar != null) ImageLoader.loadAvatar(imgAvatar, url);
        });
        viewModel.getConversations().observe(getViewLifecycleOwner(), list -> {
            filteredList.clear();
            if (list != null) filteredList.addAll(list);
            adapter.setSearchQuery(searchQuery);
            adapter.notifyDataSetChanged();
        });
        viewModel.getShortcuts().observe(getViewLifecycleOwner(), list -> {
            shortcutList.clear();
            if (list != null) shortcutList.addAll(list);
            shortcutAdapter.notifyDataSetChanged();
        });
        viewModel.getSelectedShortcut().observe(getViewLifecycleOwner(), pid ->
                shortcutAdapter.setSelectedPartnerId(pid));
        viewModel.getShowEmpty().observe(getViewLifecycleOwner(), empty -> {
            if (layoutEmpty != null)
                layoutEmpty.setVisibility(Boolean.TRUE.equals(empty) ? View.VISIBLE : View.GONE);
        });
        viewModel.getNotifications().observe(getViewLifecycleOwner(), list -> {
            notifList.clear();
            if (list != null) notifList.addAll(list);
            if (notifAdapter != null) notifAdapter.notifyDataSetChanged();
            boolean empty = notifList.isEmpty();
            if (tvNotifEmpty != null)
                tvNotifEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (rvNotifications != null)
                rvNotifications.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                viewModel.setSearchQuery(searchQuery);
            }
        });
    }

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

        viewModel.loadUserProfile(user.getUid());
        viewModel.startConversations(user.getUid());
    }

    static class ConversationAdapter
            extends RecyclerView.Adapter<ConversationAdapter.VH> {

        private final List<Map<String, Object>> list;
        private String searchQuery = "";

        ConversationAdapter(List<Map<String, Object>> list) { this.list = list; }

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

                // Optimistic: ẩn chấm xanh ngay rồi mới ghi xuống server.
                String unreadByNow = String.valueOf(item.getOrDefault("unreadBy", ""));
                if (getCurrentUid().equals(unreadByNow)) {
                    item.put("unreadBy", "");
                    if (h.viewUnreadDot != null) h.viewUnreadDot.setVisibility(View.GONE);
                    h.tvLastMsg.setTypeface(null, android.graphics.Typeface.NORMAL);
                    h.tvLastMsg.setTextColor(0xFF6B7280);
                    if (!roomId.isEmpty()) ChatRepository.clearUnread(roomId);
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

    private void selectTab(boolean chatSelected) {
        if (chatSelected == isChatTabActive) return;
        isChatTabActive = chatSelected;

        if (!chatSelected) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) viewModel.startNotifications(user.getUid());
        }

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

            switch (type) {
                case "order_confirmed": h.ivIcon.setImageResource(R.drawable.ic_verified_check); break;
                case "order_rejected":  h.ivIcon.setImageResource(R.drawable.ic_warning); break;
                case "order_sent":      h.ivIcon.setImageResource(R.drawable.ic_admin_orders); break;
                case "review_driver":   h.ivIcon.setImageResource(R.drawable.ic_star); break;
                case "invoice":
                case "invoice_paid":    h.ivIcon.setImageResource(R.drawable.ic_admin_orders); break;
                default:                h.ivIcon.setImageResource(R.drawable.ic_nav_message); break;
            }

            if ("review_driver".equals(type)) {
                Boolean actionCompleted = (Boolean) item.get("actionCompleted");
                boolean alreadyReviewed = Boolean.TRUE.equals(actionCompleted);
                if (h.btnReview   != null) h.btnReview.setVisibility(alreadyReviewed ? View.GONE    : View.VISIBLE);
                if (h.tvReviewed  != null) h.tvReviewed.setVisibility(alreadyReviewed ? View.VISIBLE : View.GONE);

                String notifOrderId  = str(item, "orderId");
                String notifDriverId = str(item, "driverId");
                String notifCarId    = str(item, "carId");

                if (h.btnReview != null && !alreadyReviewed) {
                    h.btnReview.setOnClickListener(v -> {
                        androidx.fragment.app.FragmentActivity act = MessagesFragment.this.getActivity();
                        if (act == null || act.isFinishing()) return;
                        com.example.doanmb.ui.car.view.ReviewDialogFragment.newInstance(
                                        notifOrderId, notifDriverId, notifCarId, docId)
                                .show(act.getSupportFragmentManager(), "review_dialog");
                    });
                }
            } else {
                if (h.btnReview  != null) h.btnReview.setVisibility(View.GONE);
                if (h.tvReviewed != null) h.tvReviewed.setVisibility(View.GONE);
            }

            Object createdAt = item.get("createdAt");
            if (createdAt instanceof com.google.firebase.Timestamp) {
                h.tvTime.setText(SDF.format(((com.google.firebase.Timestamp) createdAt).toDate()));
            } else {
                h.tvTime.setText("");
            }

            Object read = item.get("read");
            h.viewUnreadDot.setVisibility(Boolean.FALSE.equals(read) ? View.VISIBLE : View.GONE);

            String initial = (!senderName.isEmpty())
                    ? String.valueOf(senderName.charAt(0)).toUpperCase() : "?";
            h.tvAvatar.setText(initial);
            h.tvAvatar.setVisibility(View.VISIBLE);
            h.ivAvatar.setVisibility(View.GONE);

            if (!senderId.isEmpty()) {
                ChatRepository.loadUserBrief(senderId, (name, avatarUrl) -> {
                    if (!isAdded()) return;
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        h.ivAvatar.setVisibility(View.VISIBLE);
                        h.tvAvatar.setVisibility(View.GONE);
                        ImageLoader.loadAvatar(h.ivAvatar, avatarUrl);
                    } else if (name != null && !name.isEmpty()) {
                        h.tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
                    }
                });
            }

            h.itemView.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                notifList.get(pos).put("read", true);
                notifyItemChanged(pos);
                if (!docId.isEmpty()) viewModel.markNotificationRead(docId);

                if ("invoice".equals(type) && !orderId.isEmpty()) {
                    Intent invoice = new Intent(v.getContext(),
                            com.example.doanmb.ui.car.view.InvoiceActivity.class);
                    invoice.putExtra("ORDER_ID", orderId);
                    v.getContext().startActivity(invoice);
                    return;
                }

                if ("chat".equals(type) && !roomId.isEmpty()) {
                    ChatRepository.loadRoom(roomId, new ChatRepository.OnDoc() {
                        @Override public void onLoaded(com.google.firebase.firestore.DocumentSnapshot roomDoc) {
                            Intent intent = buildChatIntent(v.getContext(), roomId, senderId, senderName);
                            if (roomDoc != null && roomDoc.exists()) {
                                Car car = new Car(
                                        nz(roomDoc.getString("carName")),
                                        nz(roomDoc.getString("carPrice")), "", 0);
                                car.setId(nz(roomDoc.getString("carId")));
                                car.setImageUrl(nz(roomDoc.getString("carImage")));
                                car.setSellerId(nz(roomDoc.getString("sellerId")));
                                car.setType(roomDoc.getString("carType") != null ? roomDoc.getString("carType") : "sale");
                                intent.putExtra("CAR_DATA", car);
                            }
                            v.getContext().startActivity(intent);
                        }
                        @Override public void onError(String message) {
                            v.getContext().startActivity(buildChatIntent(v.getContext(), roomId, senderId, senderName));
                        }
                    });
                } else {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).openManageRequestsTab();
                    }
                }
            });
        }

        @Override public int getItemCount() { return notifList.size(); }

        private String str(Map<String, Object> m, String key) {
            Object v = m.get(key);
            return v != null ? v.toString() : "";
        }

        class VH extends RecyclerView.ViewHolder {
            TextView  tvTitle, tvBody, tvTime, tvAvatar, tvReviewed;
            ImageView ivAvatar, ivIcon;
            View      viewUnreadDot;
            android.widget.Button btnReview;

            VH(@NonNull View v) {
                super(v);
                tvTitle      = v.findViewById(R.id.tv_notif_title);
                tvBody       = v.findViewById(R.id.tv_notif_body);
                tvTime       = v.findViewById(R.id.tv_notif_time);
                tvAvatar     = v.findViewById(R.id.tv_notif_avatar);
                ivAvatar     = v.findViewById(R.id.iv_notif_avatar);
                viewUnreadDot= v.findViewById(R.id.view_unread_dot);
                btnReview    = v.findViewById(R.id.btn_notif_review);
                tvReviewed   = v.findViewById(R.id.tv_notif_reviewed);
                ivIcon       = v.findViewById(R.id.tv_notif_type_icon);
            }
        }
    }

    private static String nz(String s) { return s != null ? s : ""; }

    private static Intent buildChatIntent(android.content.Context ctx, String roomId,
                                          String senderId, String senderName) {
        Intent intent = new Intent(ctx, ChatDetailActivity.class);
        intent.putExtra("ROOM_ID",      roomId);
        intent.putExtra("PARTNER_ID",   senderId);
        intent.putExtra("PARTNER_NAME", senderName.isEmpty() ? "Người dùng" : senderName);
        return intent;
    }
}
