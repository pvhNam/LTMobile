package com.example.doanmb.ui.driver.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.doanmb.R;
import com.example.doanmb.core.util.ImageLoader;
import de.hdodenhof.circleimageview.CircleImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class DriverHomeFragment extends Fragment {

    private static final String COL_ORDERS  = "orders";
    private static final String COL_DRIVERS = "drivers";

    private SwitchMaterial switchAvailable;
    private TextView  tvAvailableLabel;

    private TextView     tvLatestCarName, tvLatestRenter,
            tvLatestDate,    tvLatestNote;
    private View         layoutNoteBox;
    private Button       btnAcceptLatest, btnRejectLatest;

    private FirebaseFirestore db;
    private String uid;
    private String latestOrderId;
    private String driverName;
    private ListenerRegistration pendingListener;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_driver_home, container, false);

        db  = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        CircleImageView ivAvatar = v.findViewById(R.id.iv_home_avatar);
        switchAvailable  = v.findViewById(R.id.switch_available);
        tvAvailableLabel = v.findViewById(R.id.tv_available_label);
        tvLatestCarName  = v.findViewById(R.id.tv_latest_car_name);
        tvLatestRenter   = v.findViewById(R.id.tv_latest_renter);
        tvLatestDate     = v.findViewById(R.id.tv_latest_date);
        tvLatestNote     = v.findViewById(R.id.tv_latest_note);
        layoutNoteBox    = v.findViewById(R.id.layout_note_box);
        btnAcceptLatest  = v.findViewById(R.id.btn_accept_latest);
        btnRejectLatest  = v.findViewById(R.id.btn_reject_latest);

        if (btnAcceptLatest != null)
            btnAcceptLatest.setOnClickListener(x -> acceptLatest());
        if (btnRejectLatest != null)
            btnRejectLatest.setOnClickListener(x -> rejectLatest());

        loadDriverInfo();
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        startPendingListener();
    }

    @Override public void onPause() {
        super.onPause();
        stopPendingListener();
    }

    private void loadDriverInfo() {
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            driverName = doc.getString("name");
            // Load avatar thật của user
            String avatarUrl = doc.getString("avatarUrl");
            CircleImageView av = getView() != null ? getView().findViewById(R.id.iv_home_avatar) : null;
            if (av != null && avatarUrl != null && !avatarUrl.isEmpty()) {
                ImageLoader.loadAvatar(av, avatarUrl, R.drawable.ic_driver_avatar_placeholder);
            }
        });

        db.collection(COL_DRIVERS).document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded() || switchAvailable == null) return;
            boolean available = !doc.exists() || !doc.contains("isAvailable")
                    || Boolean.TRUE.equals(doc.getBoolean("isAvailable"));
            applyAvailableUi(available);

            switchAvailable.setOnCheckedChangeListener((btn, isChecked) -> {
                applyAvailableUi(isChecked);
                saveAvailability(isChecked);
                if (isChecked) {
                    startPendingListener();
                } else {
                    stopPendingListener();
                    showNoTrip();
                }
            });
        });
    }

    private void applyAvailableUi(boolean available) {
        if (switchAvailable  != null) switchAvailable.setChecked(available);
        if (tvAvailableLabel != null)
            tvAvailableLabel.setText(available ? "🟢 Đang nhận chuyến" : "🔴 Tắt nhận chuyến");
    }

    private void saveAvailability(boolean available) {
        if (uid.isEmpty()) return;
        db.collection(COL_DRIVERS).document(uid)
                .set(new java.util.HashMap<String, Object>() {{
                    put("isAvailable", available);
                }}, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi cập nhật trạng thái: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void startPendingListener() {
        if (uid.isEmpty()) return;
        stopPendingListener();
        pendingListener = db.collection(COL_ORDERS)
                .whereEqualTo("sellerId", uid)
                .whereEqualTo("status", "pending")
                .limit(1)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded()) return;
                    if (e != null || snap == null || snap.isEmpty()) {
                        showNoTrip();
                        return;
                    }
                    QueryDocumentSnapshot d = (QueryDocumentSnapshot) snap.getDocuments().get(0);
                    latestOrderId = d.getId();
                    showLatestOrder(d);
                });
    }

    private void stopPendingListener() {
        if (pendingListener != null) {
            pendingListener.remove();
            pendingListener = null;
        }
    }

    private void showLatestOrder(QueryDocumentSnapshot d) {
        View cardNearest = getView() != null ? getView().findViewById(R.id.card_nearest) : null;
        View tvNoNearest = getView() != null ? getView().findViewById(R.id.tv_no_nearest) : null;
        if (cardNearest != null) cardNearest.setVisibility(View.VISIBLE);
        if (tvNoNearest != null) tvNoNearest.setVisibility(View.GONE);

        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault());

        String carName = d.getString("carName");
        String price   = d.getString("carPrice");

        setText(tvLatestCarName, carName);
        TextView tvNCartype = getView() != null ? getView().findViewById(R.id.tv_n_cartype) : null;
        String type = d.getString("type");
        if (tvNCartype != null)
            tvNCartype.setText(type != null && !type.isEmpty() ? type : "Có tài xế");

        TextView tvNPrice = getView() != null ? getView().findViewById(R.id.tv_n_price) : null;
        if (tvNPrice != null) setText(tvNPrice, price);

        String rn = d.getString("renterName");
        String rp = d.getString("renterPhone");
        setText(tvLatestRenter, (rn != null ? rn : "") +
                (rp != null && !rp.isEmpty() ? "  |  " + rp : ""));

        String note = d.getString("note");
        boolean hasNote = note != null && !note.isEmpty();
        if (layoutNoteBox != null) layoutNoteBox.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) setText(tvLatestNote, note);

        Timestamp ts = d.getTimestamp("createdAt");
        if (tvLatestDate != null && ts != null)
            tvLatestDate.setText("📅 " + fmt.format(ts.toDate()));
    }

    private void showNoTrip() {
        latestOrderId = null;
        View cardNearest = getView() != null ? getView().findViewById(R.id.card_nearest) : null;
        View tvNoNearest = getView() != null ? getView().findViewById(R.id.tv_no_nearest) : null;
        if (cardNearest != null) cardNearest.setVisibility(View.GONE);
        if (tvNoNearest != null) tvNoNearest.setVisibility(View.VISIBLE);
    }

    private void acceptLatest() {
        if (latestOrderId == null) return;
        String savedOrderId = latestOrderId;
        DocumentReference ref = db.collection(COL_ORDERS).document(savedOrderId);
        db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            if (!"pending".equals(snap.getString("status"))) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                        "Đơn đã được xử lý",
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED);
            }
            tr.update(ref,
                    "status",     "accepted",
                    "driverName", driverName != null ? driverName : "",
                    "acceptedAt", com.google.firebase.Timestamp.now());
            return null;
        }).addOnSuccessListener(x -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "✅ Đã nhận chuyến!", Toast.LENGTH_SHORT).show();
            notifyCustomerAccepted(savedOrderId);
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void notifyCustomerAccepted(String orderId) {
        db.collection(COL_ORDERS).document(orderId).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) return;
                    String buyerId = snap.getString("buyerId");
                    String carName = snap.getString("carName");
                    String carId   = snap.getString("carId");
                    if (buyerId == null || buyerId.isEmpty()) return;

                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(userSnap -> {
                                String myName = userSnap.getString("name");
                                if (myName == null || myName.isEmpty()) myName = "Tài xế";
                                com.example.doanmb.core.helper.ChatNotificationHelper
                                        .sendOrderNotification(
                                                requireContext(),
                                                buyerId,
                                                uid,
                                                myName,
                                                carName != null ? carName : "",
                                                carId   != null ? carId   : "",
                                                "order_confirmed",
                                                orderId
                                        );
                            });
                });
    }

    private void rejectLatest() {
        if (latestOrderId == null) return;
        String savedOrderId = latestOrderId;
        db.collection(COL_ORDERS).document(savedOrderId)
                .update("status", "rejected", "rejectedAt", com.google.firebase.Timestamp.now())
                .addOnSuccessListener(x -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Đã từ chối chuyến.", Toast.LENGTH_SHORT).show();
                    notifyCustomerRejected(savedOrderId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void notifyCustomerRejected(String orderId) {
        db.collection(COL_ORDERS).document(orderId).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) return;
                    String buyerId  = snap.getString("buyerId");
                    String carName  = snap.getString("carName");
                    String carId    = snap.getString("carId");
                    if (buyerId == null || buyerId.isEmpty()) return;

                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(userSnap -> {
                                String myName = userSnap.getString("name");
                                if (myName == null || myName.isEmpty()) myName = "Tài xế";
                                com.example.doanmb.core.helper.ChatNotificationHelper
                                        .sendOrderNotification(
                                                requireContext(),
                                                buyerId,
                                                uid,
                                                myName,
                                                carName != null ? carName : "",
                                                carId   != null ? carId   : "",
                                                "order_rejected",
                                                orderId
                                        );
                            });
                });
    }

    private static void setText(TextView tv, String text) {
        if (tv != null) tv.setText(text != null ? text : "");
    }
}