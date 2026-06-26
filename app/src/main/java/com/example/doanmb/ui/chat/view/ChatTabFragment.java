package com.example.doanmb.ui.chat.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatTabFragment extends Fragment {

    private RecyclerView rvConversations, rvShortcuts;
    private LinearLayout layoutEmpty;
    private FirebaseFirestore db;
    private ListenerRegistration listener;
    private ConversationAdapter adapter;
    private ShortcutAdapter shortcutAdapter;
    private String selectedShortcutPartnerId = null;

    private final List<Map<String, Object>> convList     = new ArrayList<>();
    private final List<Map<String, Object>> shortcutList = new ArrayList<>();
    private final List<Map<String, Object>> filteredList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_tab, container, false);
        db = FirebaseFirestore.getInstance();

        rvConversations = view.findViewById(R.id.rv_conversations);
        rvShortcuts     = view.findViewById(R.id.rv_shortcuts);
        layoutEmpty     = view.findViewById(R.id.layout_msg_empty);

        rvConversations.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationAdapter(filteredList, db);
        rvConversations.setAdapter(adapter);

        rvShortcuts.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        shortcutAdapter = new ShortcutAdapter(shortcutList, this::onShortcutClicked);
        rvShortcuts.setAdapter(shortcutAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) loadConversations(user.getUid());
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
                    filteredList.clear();
                    Map<String, Boolean> addedPartners = new HashMap<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        Map<String, Object> data = new HashMap<>(doc.getData());
                        data.put("roomId", doc.getId());
                        convList.add(data);
                        filteredList.add(data);

                        String buyerId  = (String) data.get("buyerId");
                        String sellerId = (String) data.get("sellerId");
                        String partnerId = uid.equals(buyerId) ? sellerId : buyerId;

                        if (partnerId != null
                                && !addedPartners.containsKey(partnerId)
                                && shortcutList.size() < 10) {
                            Map<String, Object> shortcut = new HashMap<>();
                            shortcut.put("partnerId", partnerId);
                            shortcut.put("roomId",    doc.getId());
                            shortcut.put("carId",     data.get("carId"));
                            shortcut.put("carName",   data.get("carName"));
                            shortcut.put("carImage",  data.get("carImage"));

                            db.collection("users").document(partnerId).get()
                                    .addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists() && isAdded()) {
                                            shortcut.put("partnerName",   userDoc.getString("name"));
                                            shortcut.put("partnerAvatar", userDoc.getString("avatarUrl"));
                                            shortcutAdapter.notifyDataSetChanged();
                                        }
                                    });

                            shortcutList.add(shortcut);
                            addedPartners.put(partnerId, true);
                        }
                    }

                    adapter.notifyDataSetChanged();
                    shortcutAdapter.notifyDataSetChanged();
                    layoutEmpty.setVisibility(convList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) listener.remove();
    }

    public void onShortcutClicked(String partnerId) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        if (partnerId.equals(selectedShortcutPartnerId)) {
            selectedShortcutPartnerId = null;
            shortcutAdapter.setSelectedPartnerId(null);
            filteredList.clear();
            filteredList.addAll(convList);
        } else {
            selectedShortcutPartnerId = partnerId;
            shortcutAdapter.setSelectedPartnerId(partnerId);
            filteredList.clear();
            for (Map<String, Object> conv : convList) {
                String buyerId  = (String) conv.get("buyerId");
                String sellerId = (String) conv.get("sellerId");
                String pId = uid.equals(buyerId) ? sellerId : buyerId;
                if (partnerId.equals(pId)) filteredList.add(conv);
            }
        }
        adapter.notifyDataSetChanged();
        layoutEmpty.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  ConversationAdapter — hiện chấm xanh nếu currentUser là unreadBy
    // ──────────────────────────────────────────────────────────────────────────
    static class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {
        private final List<Map<String, Object>> list;
        private final FirebaseFirestore db;
        private final String currentUid;

        ConversationAdapter(List<Map<String, Object>> list, FirebaseFirestore db) {
            this.list       = list;
            this.db         = db;
            this.currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        }

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

            String roomId   = (String) item.get("roomId");
            String carName  = (String) item.get("carName");
            String carPrice = (String) item.get("carPrice");
            String lastMsg  = (String) item.get("lastMessage");
            String buyerId  = (String) item.get("buyerId");
            String sellerId = (String) item.get("sellerId");
            String carType  = (String) item.get("carType");
            String carId    = (String) item.get("carId");
            String carImage = (String) item.get("carImage");
            // Field mới: uid của người chưa đọc tin nhắn cuối
            String unreadBy = (String) item.get("unreadBy");

            String partnerId = currentUid.equals(buyerId) ? sellerId : buyerId;

            h.tvCarName.setText(carName);
            h.tvLastMsg.setText(lastMsg != null && !lastMsg.isEmpty()
                    ? lastMsg : "Bắt đầu cuộc trò chuyện...");
            h.tvName.setText("Đang tải...");

            // ── Hiển thị chấm xanh nếu currentUser chưa đọc tin nhắn cuối ──
            boolean hasUnread = currentUid.equals(unreadBy);
            h.viewUnreadDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);

            // Tin nhắn chưa đọc → in đậm preview
            h.tvLastMsg.setTypeface(null,
                    hasUnread
                            ? android.graphics.Typeface.BOLD
                            : android.graphics.Typeface.NORMAL);
            h.tvLastMsg.setTextColor(hasUnread ? 0xFF1A1A2E : 0xFF6B7280);

            // ── Load thông tin partner ──────────────────────────────────────
            if (partnerId != null && !partnerId.isEmpty()) {
                db.collection("users").document(partnerId).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String name      = doc.getString("name");
                                String avatarUrl = doc.getString("avatarUrl");
                                h.tvName.setText(name != null ? name : "Người dùng");
                                String initial = String.valueOf(
                                        (name != null && !name.isEmpty() ? name : "U")
                                                .charAt(0)).toUpperCase();
                                h.tvAvatar.setText(initial);

                                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                    h.tvAvatar.setVisibility(View.GONE);
                                    h.ivAvatar.setVisibility(View.VISIBLE);
                                    ImageLoader.loadAvatar(h.ivAvatar, avatarUrl);
                                } else {
                                    h.tvAvatar.setVisibility(View.VISIBLE);
                                    h.ivAvatar.setVisibility(View.GONE);
                                }
                            }
                        });
            }

            // ── Click: mở chat + đánh dấu đã đọc ngay trên UI ─────────────
            h.itemView.setOnClickListener(v -> {
                // Ẩn chấm xanh ngay lập tức (không đợi Firestore)
                item.put("unreadBy", "");
                h.viewUnreadDot.setVisibility(View.GONE);
                h.tvLastMsg.setTypeface(null, android.graphics.Typeface.NORMAL);
                h.tvLastMsg.setTextColor(0xFF6B7280);

                // Cập nhật Firestore
                if (roomId != null && currentUid.equals(unreadBy)) {
                    db.collection("chat_rooms").document(roomId)
                            .update("unreadBy", "");
                }

                Intent intent = new Intent(v.getContext(), ChatDetailActivity.class);
                intent.putExtra("ROOM_ID",       roomId);
                intent.putExtra("PARTNER_ID",    partnerId);
                intent.putExtra("PARTNER_NAME",  h.tvName.getText().toString());

                Car car = new Car(carName, carPrice, "", 0);
                car.setId(carId);
                car.setSellerId(sellerId);
                car.setImageUrl(carImage);
                car.setType(carType);
                intent.putExtra("CAR_DATA", car);
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

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
}