package com.example.doanmb.ui.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.adapter.ProfileCarAdapter;
import com.example.doanmb.adapter.RequestAdapter;
import com.example.doanmb.model.Car;
import com.example.doanmb.ui.activity.CarDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.firebase.firestore.Query;

public class ManageFragment extends Fragment {

    // Tab pill (giống trang Danh mục)
    private CardView cardTabPosts, cardTabRequests;
    private LinearLayout tabPostsContent, tabRequestsContent;
    private TextView tvTabPosts, tvTabRequests;
    private LinearLayout layoutMyPosts, layoutRequests;

    // Tab 1: Xe đã đăng
    private RecyclerView rvMyPosts;
    private TextView tvMyPostCount, tvEmptyPosts;
    private ProfileCarAdapter myPostsAdapter;
    private List<Car> myCarList = new ArrayList<>();

    // Tab 2: Yêu cầu nhận được
    private RecyclerView rvRequests;
    private TextView tvRequestCount, tvEmptyRequests;
    private RequestAdapter requestAdapter;
    private List<Map<String, Object>> orderList = new ArrayList<>();
    private List<String> orderIds = new ArrayList<>();

    private FirebaseFirestore db;
    private String currentUserId;
    private ListenerRegistration requestsListener; // lưu lại để hủy khi fragment destroy
    private boolean usingCarIdFallback = false;    // tránh gọi loadRequestsByCarId() nhiều lần

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage, container, false);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) currentUserId = user.getUid();

        initViews(view);
        setupTabs();
        setupRecyclerViews();

        if (currentUserId != null) {
            loadMyPosts();
            loadRequests();
        }

        // Nếu được điều hướng đến với yêu cầu mở tab Yêu cầu (từ notification)
        Bundle args = getArguments();
        if (args != null && args.getBoolean("showRequests", false)) {
            showTab(false); // false = tab Yêu cầu nhận được
        }

        return view;
    }

    private void initViews(View view) {
        cardTabPosts = view.findViewById(R.id.card_tab_posts);
        cardTabRequests = view.findViewById(R.id.card_tab_requests);
        tabPostsContent = view.findViewById(R.id.tab_posts_content);
        tabRequestsContent = view.findViewById(R.id.tab_requests_content);
        tvTabPosts = view.findViewById(R.id.tv_tab_posts);
        tvTabRequests = view.findViewById(R.id.tv_tab_requests);
        layoutMyPosts = view.findViewById(R.id.layoutMyPosts);
        layoutRequests = view.findViewById(R.id.layoutRequests);
        rvMyPosts = view.findViewById(R.id.rvMyPosts);
        rvRequests = view.findViewById(R.id.rvRequests);
        tvMyPostCount = view.findViewById(R.id.tvMyPostCount);
        tvEmptyPosts = view.findViewById(R.id.tvEmptyPosts);
        tvRequestCount = view.findViewById(R.id.tvRequestCount);
        tvEmptyRequests = view.findViewById(R.id.tvEmptyRequests);
    }

    private void setupTabs() {
        cardTabPosts.setOnClickListener(v -> showTab(true));
        cardTabRequests.setOnClickListener(v -> showTab(false));
        showTab(true); // trạng thái ban đầu: đang xem "Xe đã đăng"
    }

    private void showTab(boolean showPosts) {
        layoutMyPosts.setVisibility(showPosts ? View.VISIBLE : View.GONE);
        layoutRequests.setVisibility(showPosts ? View.GONE : View.VISIBLE);
        setTabSelected(tabPostsContent, tvTabPosts, showPosts);
        setTabSelected(tabRequestsContent, tvTabRequests, !showPosts);
    }

    // Tab đang chọn = pill trắng + chữ xanh; tab còn lại = trong suốt + chữ trắng (giống trang Danh mục)
    private void setTabSelected(LinearLayout tabContent, TextView tabLabel, boolean selected) {
        if (selected) {
            tabContent.setBackgroundResource(R.drawable.bg_tab_active_pill);
            tabLabel.setTextColor(Color.parseColor("#2F54D4"));
        } else {
            tabContent.setBackground(null);
            tabLabel.setTextColor(Color.WHITE);
        }
    }

    private void setupRecyclerViews() {
        // Tab 1: Xe đã đăng — bấm vào item để xem chi tiết xe
        rvMyPosts.setLayoutManager(new LinearLayoutManager(getContext()));
        myPostsAdapter = new ProfileCarAdapter(myCarList, this::openCarDetail);
        rvMyPosts.setAdapter(myPostsAdapter);

        // Tab 2: Yêu cầu
        rvRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        requestAdapter = new RequestAdapter(orderList, orderIds, new RequestAdapter.OnActionListener() {
            @Override
            public void onConfirm(String orderId, String carId, Map<String, Object> order) {
                confirmRequest(orderId, carId);
            }

            @Override
            public void onReject(String orderId, String carId) {
                rejectRequest(orderId, carId);
            }
        });
        rvRequests.setAdapter(requestAdapter);
    }

    // Mở màn hình chi tiết xe khi bấm vào tin đã đăng
    private void openCarDetail(Car car) {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), CarDetailActivity.class);
        intent.putExtra("CAR_DATA", car);
        intent.putExtra("CAR_ID", car.getId());
        intent.putExtra("SELLER_ID", car.getSellerId());
        intent.putExtra("CAR_TYPE", car.getType());
        startActivity(intent);
    }

    // Load xe mình đã đăng
    private void loadMyPosts() {
        db.collection("cars")
                .whereEqualTo("userId", currentUserId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    // PHÒNG HỘ LUỒNG
                    if (!isAdded() || getActivity() == null) return;

                    myCarList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String name = doc.getString("name");
                        String price = doc.getString("price");
                        String info = doc.getString("info");
                        String type = doc.getString("type");
                        String status = doc.getString("status");
                        String imageUrl = doc.getString("imageUrl");
                        String sellerId = doc.getString("sellerId");
                        if (sellerId == null) sellerId = doc.getString("userId");
                        if (name == null) continue;

                        Car car = new Car(name, price != null ? price : "", info != null ? info : "", android.R.drawable.ic_menu_gallery);
                        if ("holding".equals(status)) {
                            car = new Car("⏳ " + name, price != null ? price : "", "Đang có người đặt cọc • " + (info != null ? info : ""), android.R.drawable.ic_menu_gallery);
                        }
                        car.setId(doc.getId());
                        car.setType(type != null ? type : "");
                        car.setImageUrl(imageUrl != null ? imageUrl : "");
                        car.setSellerId(sellerId != null ? sellerId : "");
                        myCarList.add(car);
                    }
                    myPostsAdapter.updateList(myCarList);
                    tvMyPostCount.setText("Tin đã đăng: " + myCarList.size());
                    tvEmptyPosts.setVisibility(myCarList.isEmpty() ? View.VISIBLE : View.GONE);
                    rvMyPosts.setVisibility(myCarList.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    // Load yêu cầu mua/thuê xe của mình — real-time
    private void loadRequests() {
        // Hủy listener cũ nếu có
        if (requestsListener != null) {
            requestsListener.remove();
        }
        usingCarIdFallback = false;

        // Thử query theo sellerId trước (KHÔNG dùng orderBy để tránh cần composite index)
        requestsListener = db.collection("orders")
                .whereEqualTo("sellerId", currentUserId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("ManageFragment", "loadRequests sellerId error: " + error.getMessage());
                        if (!usingCarIdFallback) {
                            usingCarIdFallback = true;
                            loadRequestsByCarId();
                        }
                        return;
                    }
                    if (snapshots == null || !isAdded() || getActivity() == null) return;

                    if (snapshots.isEmpty()) {
                        // Không có sellerId → fallback real-time theo carId (chỉ 1 lần)
                        if (!usingCarIdFallback) {
                            usingCarIdFallback = true;
                            loadRequestsByCarId();
                        }
                        return;
                    }

                    // Có data theo sellerId → dùng kết quả này
                    usingCarIdFallback = false;
                    orderList.clear();
                    orderIds.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        orderList.add(doc.getData());
                        orderIds.add(doc.getId());
                    }
                    sortOrdersByCreatedAt(); // sort trong bộ nhớ thay vì orderBy Firestore
                    updateRequestsUI();
                });
    }

    // Load yêu cầu dựa trên các carId của mình — real-time
    private void loadRequestsByCarId() {
        db.collection("cars")
                .whereEqualTo("userId", currentUserId)
                .get()
                .addOnSuccessListener(carSnapshots -> {
                    List<String> myCarIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : carSnapshots) {
                        myCarIds.add(doc.getId());
                    }

                    if (myCarIds.isEmpty()) {
                        updateRequestsUI();
                        return;
                    }

                    // Hủy listener sellerId và thay bằng listener real-time theo carId
                    // KHÔNG dùng orderBy để tránh cần composite index
                    if (requestsListener != null) {
                        requestsListener.remove();
                    }

                    requestsListener = db.collection("orders")
                            .whereIn("carId", myCarIds)
                            .addSnapshotListener((orderSnapshots, error) -> {
                                if (error != null) {
                                    android.util.Log.e("ManageFragment", "loadRequestsByCarId error: " + error.getMessage());
                                    return;
                                }
                                if (orderSnapshots == null || !isAdded() || getActivity() == null) return;

                                orderList.clear();
                                orderIds.clear();
                                for (QueryDocumentSnapshot doc : orderSnapshots) {
                                    orderList.add(doc.getData());
                                    orderIds.add(doc.getId());
                                }
                                sortOrdersByCreatedAt(); // sort trong bộ nhớ thay vì orderBy Firestore
                                updateRequestsUI();
                            });
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("ManageFragment", "loadRequestsByCarId cars query error: " + e.getMessage())
                );
    }

    // Sắp xếp orderList + orderIds theo createdAt giảm dần (mới nhất lên đầu)
    // Dùng sort trong bộ nhớ thay vì orderBy Firestore để không cần composite index
    private void sortOrdersByCreatedAt() {
        List<AbstractMap.SimpleEntry<String, Map<String, Object>>> paired = new ArrayList<>();
        for (int i = 0; i < orderList.size(); i++) {
            paired.add(new AbstractMap.SimpleEntry<>(orderIds.get(i), orderList.get(i)));
        }
        paired.sort((a, b) -> {
            com.google.firebase.Timestamp ta = (com.google.firebase.Timestamp) a.getValue().get("createdAt");
            com.google.firebase.Timestamp tb = (com.google.firebase.Timestamp) b.getValue().get("createdAt");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        orderList.clear();
        orderIds.clear();
        for (AbstractMap.SimpleEntry<String, Map<String, Object>> entry : paired) {
            orderIds.add(entry.getKey());
            orderList.add(entry.getValue());
        }
    }

    private void updateRequestsUI() {
        requestAdapter.updateList(orderList, orderIds);
        tvRequestCount.setText("Yêu cầu: " + orderList.size());
        tvEmptyRequests.setVisibility(orderList.isEmpty() ? View.VISIBLE : View.GONE);
        rvRequests.setVisibility(orderList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // Xác nhận yêu cầu → xe chuyển sang "sold/rented", order → confirmed
    private void confirmRequest(String orderId, String carId) {
        Map<String, Object> orderUpdate = new HashMap<>();
        orderUpdate.put("status", "confirmed");
        db.collection("orders").document(orderId).update(orderUpdate);

        if (!carId.isEmpty()) {
            Map<String, Object> carUpdate = new HashMap<>();
            carUpdate.put("status", "sold");
            db.collection("cars").document(carId).update(carUpdate)
                    .addOnSuccessListener(v -> {
                        // PHÒNG HỘ CONTEXT
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "✅ Đã xác nhận! Xe sẽ được ẩn khỏi danh sách.", Toast.LENGTH_SHORT).show();
                            loadMyPosts();
                            loadRequests();
                        }
                    });
        } else {
            if (getContext() != null) {
                Toast.makeText(getContext(), "✅ Đã xác nhận yêu cầu!", Toast.LENGTH_SHORT).show();
                loadRequests();
            }
        }

        // ── THÊM BƯỚC 4: thông báo cho người mua/thuê ─────────────────────────
        notifyBuyerOrderStatus(orderId, "order_confirmed");
        // ───────────────────────────────────────────────────────────────────────
    }

    // Từ chối yêu cầu → xe về trạng thái bình thường
    private void rejectRequest(String orderId, String carId) {
        Map<String, Object> orderUpdate = new HashMap<>();
        orderUpdate.put("status", "rejected");
        db.collection("orders").document(orderId).update(orderUpdate);

        if (!carId.isEmpty()) {
            Map<String, Object> carUpdate = new HashMap<>();
            carUpdate.put("status", "active");
            db.collection("cars").document(carId).update(carUpdate)
                    .addOnSuccessListener(v -> {
                        // PHÒNG HỘ CONTEXT
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Đã từ chối yêu cầu. Xe tiếp tục hiển thị.", Toast.LENGTH_SHORT).show();
                            loadMyPosts();
                            loadRequests();
                        }
                    });
        } else {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Đã từ chối yêu cầu.", Toast.LENGTH_SHORT).show();
                loadRequests();
            }
        }

        // ── THÊM BƯỚC 4: thông báo cho người mua/thuê ─────────────────────────
        notifyBuyerOrderStatus(orderId, "order_rejected");
        // ───────────────────────────────────────────────────────────────────────
    }

    private void notifyBuyerOrderStatus(String orderId, String type) {
        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) return;

                    String buyerId  = snap.getString("buyerId");
                    String carName  = snap.getString("carName");
                    String carId    = snap.getString("carId");

                    if (buyerId == null || buyerId.isEmpty()) return;

                    FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
                    String myUid = me != null ? me.getUid() : "";

                    db.collection("users").document(myUid).get()
                            .addOnSuccessListener(userSnap -> {
                                String sellerName = userSnap.getString("name");
                                if (sellerName == null || sellerName.isEmpty()) sellerName = "Chủ xe";

                                com.example.doanmb.util.ChatNotificationHelper.sendOrderNotification(
                                        requireContext(),
                                        buyerId,
                                        myUid,
                                        sellerName,
                                        carName != null ? carName : "",
                                        carId   != null ? carId   : "",  // ← thêm carId
                                        type,
                                        orderId
                                );
                            });
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentUserId != null) {
            loadMyPosts();
            loadRequests();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Quan trọng: hủy listener khi fragment bị destroy, tránh memory leak
        if (requestsListener != null) {
            requestsListener.remove();
            requestsListener = null;
        }
    }
}