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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageFragment extends Fragment {

    private CardView cardTabPosts, cardTabRequests;
    private LinearLayout tabPostsContent, tabRequestsContent;
    private TextView tvTabPosts, tvTabRequests;
    private LinearLayout layoutMyPosts, layoutRequests;

    private RecyclerView rvMyPosts;
    private TextView tvMyPostCount, tvEmptyPosts;
    private ProfileCarAdapter myPostsAdapter;
    private List<Car> myCarList = new ArrayList<>();

    private RecyclerView rvRequests;
    private TextView tvRequestCount, tvEmptyRequests;
    private RequestAdapter requestAdapter;
    private List<Map<String, Object>> orderList = new ArrayList<>();
    private List<String> orderIds = new ArrayList<>();

    private FirebaseFirestore db;
    private String currentUserId;
    private ListenerRegistration requestsListener;

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

        // KIỂM TRA ĐỐI SỐ TỪ NOTIFICATION ĐỂ MỞ TAB YÊU CẦU
        Bundle args = getArguments();
        boolean openRequests = args != null && args.getBoolean("OPEN_REQUESTS", false);
        showTab(!openRequests);
    }

    private void showTab(boolean showPosts) {
        layoutMyPosts.setVisibility(showPosts ? View.VISIBLE : View.GONE);
        layoutRequests.setVisibility(showPosts ? View.GONE : View.VISIBLE);
        setTabSelected(tabPostsContent, tvTabPosts, showPosts);
        setTabSelected(tabRequestsContent, tvTabRequests, !showPosts);
    }

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
        rvMyPosts.setLayoutManager(new LinearLayoutManager(getContext()));
        myPostsAdapter = new ProfileCarAdapter(myCarList, this::openCarDetail);
        rvMyPosts.setAdapter(myPostsAdapter);

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

    private void openCarDetail(Car car) {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), CarDetailActivity.class);
        intent.putExtra("CAR_DATA", car);
        intent.putExtra("CAR_ID", car.getId());
        intent.putExtra("SELLER_ID", car.getSellerId());
        intent.putExtra("CAR_TYPE", car.getType());
        startActivity(intent);
    }

    private void loadMyPosts() {
        db.collection("cars")
                .whereEqualTo("userId", currentUserId)
                .get()
                .addOnSuccessListener(snapshots -> {
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

    private void loadRequests() {
        if (requestsListener != null) {
            requestsListener.remove();
        }

        // CẬP NHẬT SORT DESCENDING (YÊU CẦU COMPOSITE INDEX)
        requestsListener = db.collection("orders")
                .whereEqualTo("sellerId", currentUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        loadRequestsByCarId();
                        return;
                    }
                    if (!isAdded() || getActivity() == null) return;

                    if (snapshots.isEmpty()) {
                        loadRequestsByCarId();
                        return;
                    }

                    orderList.clear();
                    orderIds.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        orderList.add(doc.getData());
                        orderIds.add(doc.getId());
                    }
                    updateRequestsUI();
                });
    }

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

                    db.collection("orders").get()
                            .addOnSuccessListener(orderSnapshots -> {
                                List<Map<String, Object>> tempOrders = new ArrayList<>();

                                for (QueryDocumentSnapshot doc : orderSnapshots) {
                                    String carId = doc.getString("carId");
                                    if (carId != null && myCarIds.contains(carId)) {
                                        Map<String, Object> data = doc.getData();
                                        // Tạm nhúng ID vào Map để tránh mất đồng bộ khi sort
                                        data.put("_tempDocId", doc.getId());
                                        tempOrders.add(data);
                                    }
                                }

                                // CẬP NHẬT SORT AN TOÀN
                                tempOrders.sort((a, b) -> {
                                    com.google.firebase.Timestamp ta = (com.google.firebase.Timestamp) a.get("createdAt");
                                    com.google.firebase.Timestamp tb = (com.google.firebase.Timestamp) b.get("createdAt");
                                    if (ta == null && tb == null) return 0;
                                    if (ta == null) return 1;  // Đẩy null xuống cuối
                                    if (tb == null) return -1;
                                    return tb.compareTo(ta); // Mới nhất lên đầu
                                });

                                orderList.clear();
                                orderIds.clear();
                                for (Map<String, Object> data : tempOrders) {
                                    orderIds.add((String) data.remove("_tempDocId")); // Tách ID ra lại
                                    orderList.add(data);
                                }

                                updateRequestsUI();
                            });
                });
    }

    private void updateRequestsUI() {
        requestAdapter.updateList(orderList, orderIds);
        tvRequestCount.setText("Yêu cầu: " + orderList.size());
        tvEmptyRequests.setVisibility(orderList.isEmpty() ? View.VISIBLE : View.GONE);
        rvRequests.setVisibility(orderList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmRequest(String orderId, String carId) {
        Map<String, Object> orderUpdate = new HashMap<>();
        orderUpdate.put("status", "confirmed");
        db.collection("orders").document(orderId).update(orderUpdate);

        if (!carId.isEmpty()) {
            Map<String, Object> carUpdate = new HashMap<>();
            carUpdate.put("status", "sold");
            db.collection("cars").document(carId).update(carUpdate)
                    .addOnSuccessListener(v -> {
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

        notifyBuyerOrderStatus(orderId, "order_confirmed");
    }

    private void rejectRequest(String orderId, String carId) {
        Map<String, Object> orderUpdate = new HashMap<>();
        orderUpdate.put("status", "rejected");
        db.collection("orders").document(orderId).update(orderUpdate);

        if (!carId.isEmpty()) {
            Map<String, Object> carUpdate = new HashMap<>();
            carUpdate.put("status", "active");
            db.collection("cars").document(carId).update(carUpdate)
                    .addOnSuccessListener(v -> {
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

        notifyBuyerOrderStatus(orderId, "order_rejected");
    }

    private void notifyBuyerOrderStatus(String orderId, String type) {
        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) return;

                    String buyerId  = snap.getString("buyerId");
                    String carName  = snap.getString("carName");

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
        if (requestsListener != null) {
            requestsListener.remove();
            requestsListener = null;
        }
    }
}