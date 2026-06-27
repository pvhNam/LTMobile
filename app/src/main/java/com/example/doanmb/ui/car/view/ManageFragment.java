package com.example.doanmb.ui.car.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.example.doanmb.core.service.OrderReminderService;
import com.example.doanmb.ui.car.adapter.ProfileCarAdapter;
import com.example.doanmb.ui.car.adapter.RequestAdapter;
import com.example.doanmb.core.helper.ChatNotificationHelper;
import com.example.doanmb.data.model.Car;
import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.Timestamp;
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

    // Hai nguồn: đơn NHẬN ĐƯỢC (mình là chủ xe) và đơn MÌNH GỬI ĐI (mình đi thuê) — gộp chung 1 danh sách
    private final Map<String, Map<String, Object>> incomingOrders = new java.util.LinkedHashMap<>();
    private final Map<String, Map<String, Object>> outgoingOrders = new java.util.LinkedHashMap<>();

    private FirebaseFirestore db;
    private String currentUserId;
    private ListenerRegistration requestsListener; // lưu lại để hủy khi fragment destroy
    private ListenerRegistration outgoingListener; // listener đơn mình gửi đi
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

        // Tab 2: Yêu cầu (gồm đơn nhận được + đơn mình gửi đi)
        rvRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        requestAdapter = new RequestAdapter(orderList, orderIds, currentUserId,
                new RequestAdapter.OnActionListener() {
            @Override
            public void onConfirm(String orderId, String carId, Map<String, Object> order) {
                confirmRequest(orderId, carId);
            }
            @Override
            public void onReject(String orderId, String carId) {
                rejectRequest(orderId, carId);
            }
            @Override
            public void onMarkReturned(String orderId, Map<String, Object> order) {
                markReturned(orderId, order);
            }
            @Override
            public void onCancelOwn(String orderId, Map<String, Object> order) {
                cancelOwnOrder(orderId, order);
            }
            @Override
            public void onExtend(String orderId, Map<String, Object> order) {
                extendOrder(orderId, order);
            }
            @Override
            public void onViewInvoice(String orderId, Map<String, Object> order) {
                if (getActivity() == null) return;
                Intent i = new Intent(getActivity(), InvoiceActivity.class);
                i.putExtra("ORDER_ID", orderId);
                startActivity(i);
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

    // Load yêu cầu — gồm đơn NHẬN ĐƯỢC (sellerId==mình) và đơn MÌNH GỬI ĐI (buyerId==mình), real-time
    private void loadRequests() {
        if (requestsListener != null) requestsListener.remove();
        if (outgoingListener != null) outgoingListener.remove();
        usingCarIdFallback = false;
        incomingOrders.clear();
        outgoingOrders.clear();

        // (A) Đơn NHẬN ĐƯỢC — theo sellerId (fallback theo carId nếu trống)
        requestsListener = db.collection("orders")
                .whereEqualTo("sellerId", currentUserId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("ManageFragment", "loadRequests sellerId error: " + error.getMessage());
                        if (!usingCarIdFallback) { usingCarIdFallback = true; loadRequestsByCarId(); }
                        return;
                    }
                    if (snapshots == null || !isAdded() || getActivity() == null) return;

                    if (snapshots.isEmpty() && !usingCarIdFallback) {
                        usingCarIdFallback = true;
                        loadRequestsByCarId();
                        return;
                    }
                    if (!usingCarIdFallback) {
                        incomingOrders.clear();
                        for (QueryDocumentSnapshot doc : snapshots) incomingOrders.put(doc.getId(), doc.getData());
                        rebuildRequestList();
                    }
                });

        // (B) Đơn MÌNH GỬI ĐI — theo buyerId
        outgoingListener = db.collection("orders")
                .whereEqualTo("buyerId", currentUserId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("ManageFragment", "loadRequests buyerId error: " + error.getMessage());
                        return;
                    }
                    if (snapshots == null || !isAdded() || getActivity() == null) return;
                    outgoingOrders.clear();
                    for (QueryDocumentSnapshot doc : snapshots) outgoingOrders.put(doc.getId(), doc.getData());
                    rebuildRequestList();
                });
    }

    // Fallback: đơn nhận được dựa trên các carId của mình — real-time
    private void loadRequestsByCarId() {
        db.collection("cars")
                .whereEqualTo("userId", currentUserId)
                .get()
                .addOnSuccessListener(carSnapshots -> {
                    List<String> myCarIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : carSnapshots) myCarIds.add(doc.getId());

                    if (myCarIds.isEmpty()) { rebuildRequestList(); return; }

                    if (requestsListener != null) requestsListener.remove();
                    requestsListener = db.collection("orders")
                            .whereIn("carId", myCarIds)
                            .addSnapshotListener((orderSnapshots, error) -> {
                                if (error != null) {
                                    android.util.Log.e("ManageFragment", "loadRequestsByCarId error: " + error.getMessage());
                                    return;
                                }
                                if (orderSnapshots == null || !isAdded() || getActivity() == null) return;
                                incomingOrders.clear();
                                for (QueryDocumentSnapshot doc : orderSnapshots) incomingOrders.put(doc.getId(), doc.getData());
                                rebuildRequestList();
                            });
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("ManageFragment", "loadRequestsByCarId cars query error: " + e.getMessage())
                );
    }

    // Gộp đơn nhận + đơn gửi (loại trùng theo orderId), sort theo createdAt giảm dần
    private void rebuildRequestList() {
        Map<String, Map<String, Object>> merged = new java.util.LinkedHashMap<>();
        merged.putAll(incomingOrders);
        merged.putAll(outgoingOrders);

        List<AbstractMap.SimpleEntry<String, Map<String, Object>>> paired = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : merged.entrySet()) {
            paired.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
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
        updateRequestsUI();
    }

    private void updateRequestsUI() {
        requestAdapter.updateList(orderList, orderIds);
        tvRequestCount.setText("Yêu cầu: " + orderList.size());
        tvEmptyRequests.setVisibility(orderList.isEmpty() ? View.VISIBLE : View.GONE);
        rvRequests.setVisibility(orderList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // Xác nhận yêu cầu → xe chuyển sang "sold/rented", order → confirmed
    private void confirmRequest(String orderId, String carId) {
        OrderReminderService.cancel(requireContext(), orderId);
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
        OrderReminderService.cancel(requireContext(), orderId);
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

    // ═══════════════════════════════════════════════════════════════════════════
    //  CHỦ XE: xác nhận khách đã trả xe → tính phạt trễ (nếu có) → gửi hóa đơn
    // ═══════════════════════════════════════════════════════════════════════════
    private void markReturned(String orderId, Map<String, Object> order) {
        long days        = parseIntSafe((String) order.get("days"));
        long total       = toLong(order.get("totalAmount"));
        long deposit     = toLong(order.get("depositAmount"));
        long rentalDue   = Math.max(0, total - deposit);   // tiền thuê còn lại sau khi đã giữ cọc
        long pricePerDay = days > 0 ? total / days : total;
        long lateDays    = computeLateDays(order);
        long penalty     = 0;
        if (lateDays >= 1) {
            penalty = Math.round(1.5 * pricePerDay) + (lateDays - 1) * Math.round(2.0 * pricePerDay);
        }
        long invoiceTotal = rentalDue + penalty;
        final String reason = lateDays > 0
                ? "Trả xe trễ " + lateDays + " ngày. Hóa đơn gồm tiền thuê còn lại (đã trừ cọc) và phí phạt (150% ngày đầu, 200% các ngày sau)."
                : "Thanh toán tiền thuê còn lại (đã trừ cọc) khi kết thúc chuyến.";

        final long fPenalty = penalty, fLate = lateDays, fInvoice = invoiceTotal,
                fTotal = total, fRentalDue = rentalDue, fDeposit = deposit;
        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận đã trả xe")
                .setMessage((lateDays > 0
                        ? "⚠️ Khách trả TRỄ " + lateDays + " ngày.\nPhí phạt: " + money(fPenalty) + "\n"
                        : "Khách trả đúng hạn.\n")
                        + "Tiền thuê: " + money(fTotal)
                        + "\nĐã giữ cọc: " + money(fDeposit)
                        + "\nCòn lại phải trả: " + money(fRentalDue)
                        + "\nTổng hóa đơn: " + money(fInvoice))
                .setPositiveButton("Gửi hóa đơn", (d, w) -> {
                    Map<String, Object> up = new HashMap<>();
                    up.put("status", "awaiting_payment");
                    up.put("returnedAt", Timestamp.now());
                    up.put("lateDays", fLate);
                    up.put("penaltyAmount", fPenalty);
                    up.put("rentalDue", fRentalDue);
                    up.put("invoiceTotal", fInvoice);
                    up.put("invoiceStatus", "unpaid");
                    up.put("invoiceReason", reason);
                    db.collection("orders").document(orderId).update(up);

                    String carId = (String) order.get("carId");
                    if (carId != null && !carId.isEmpty()) {
                        db.collection("cars").document(carId).update("status", "active");
                    }
                    String buyerId = (String) order.get("buyerId");
                    writeNotification(buyerId, "invoice", "Hóa đơn thuê xe",
                            "Vui lòng thanh toán " + money(fInvoice)
                                    + (fLate > 0 ? (" (trễ " + fLate + " ngày)") : ""), orderId);
                    if (getContext() != null)
                        Toast.makeText(getContext(), "✅ Đã gửi hóa đơn cho khách.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  KHÁCH: hủy yêu cầu thuê của chính mình
    // ═══════════════════════════════════════════════════════════════════════════
    private void cancelOwnOrder(String orderId, Map<String, Object> order) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Hủy yêu cầu thuê")
                .setMessage("Bạn chắc chắn muốn hủy yêu cầu thuê xe này?")
                .setPositiveButton("Hủy yêu cầu", (d, w) -> {
                    db.collection("orders").document(orderId).update("status", "rejected");

                    // Hoàn cọc nếu đã giữ
                    String depositStatus = (String) order.get("depositStatus");
                    long deposit  = toLong(order.get("depositAmount"));
                    String buyerId = (String) order.get("buyerId");
                    if ("held".equals(depositStatus) && deposit > 0 && buyerId != null) {
                        WalletRepository.refund(buyerId, deposit, orderId, null);
                    }
                    // Trả xe về active nếu đang giữ chỗ
                    String carId = (String) order.get("carId");
                    if (carId != null && !carId.isEmpty()) {
                        db.collection("cars").document(carId).update("status", "active");
                    }
                    String sellerId = (String) order.get("sellerId");
                    Object carName = order.get("carName");
                    writeNotification(sellerId, "order_rejected", "Khách đã hủy",
                            "Khách đã hủy yêu cầu thuê " + (carName != null ? carName : "xe"), orderId);
                    if (getContext() != null)
                        Toast.makeText(getContext(), "Đã hủy yêu cầu.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  KHÁCH: xin gia hạn thuê thêm ngày → cộng ngày + báo chủ xe
    // ═══════════════════════════════════════════════════════════════════════════
    private void extendOrder(String orderId, Map<String, Object> order) {
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Số ngày muốn thuê thêm");
        new AlertDialog.Builder(requireContext())
                .setTitle("Gia hạn thuê xe")
                .setMessage("Nhập số ngày muốn thuê thêm. Yêu cầu sẽ được gửi đến chủ xe.")
                .setView(input)
                .setPositiveButton("Gửi", (d, w) -> {
                    int extra = parseIntSafe(input.getText().toString());
                    if (extra <= 0) {
                        if (getContext() != null)
                            Toast.makeText(getContext(), "Số ngày không hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int oldDays = parseIntSafe((String) order.get("days"));
                    int newDays = oldDays + extra;
                    Map<String, Object> up = new HashMap<>();
                    up.put("days", String.valueOf(newDays));
                    up.put("extendRequested", true);
                    db.collection("orders").document(orderId).update(up);

                    String sellerId = (String) order.get("sellerId");
                    Object carName = order.get("carName");
                    writeNotification(sellerId, "order_sent", "Yêu cầu gia hạn",
                            "Khách xin gia hạn thêm " + extra + " ngày (tổng " + newDays + " ngày) cho "
                                    + (carName != null ? carName : "xe"), orderId);
                    if (getContext() != null)
                        Toast.makeText(getContext(), "✅ Đã gửi yêu cầu gia hạn cho chủ xe.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Số ngày trễ = (hôm nay) - (ngày bắt đầu + số ngày thuê), làm tròn lên; 0 nếu chưa trễ
    private long computeLateDays(Map<String, Object> order) {
        int days = parseIntSafe((String) order.get("days"));
        long dueMillis = parseStartMillis(order) + (long) days * 86_400_000L;
        long now = System.currentTimeMillis();
        if (now <= dueMillis) return 0;
        return (now - dueMillis + 86_400_000L - 1) / 86_400_000L;
    }

    private long parseStartMillis(Map<String, Object> order) {
        String s = (String) order.get("startDate");
        if (s != null && !s.trim().isEmpty()) {
            String[] fmts = {"dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy"};
            for (String f : fmts) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(f, java.util.Locale.US);
                    sdf.setLenient(false);
                    java.util.Date dt = sdf.parse(s.trim());
                    if (dt != null) return dt.getTime();
                } catch (Exception ignore) { }
            }
        }
        Object c = order.get("createdAt");
        if (c instanceof Timestamp) return ((Timestamp) c).toDate().getTime();
        return System.currentTimeMillis();
    }

    private void writeNotification(String userId, String type, String title, String body, String orderId) {
        if (userId == null || userId.isEmpty()) return;
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        Map<String, Object> n = new HashMap<>();
        n.put("userId", userId);
        n.put("senderId", me != null ? me.getUid() : "");
        n.put("type", type);
        n.put("title", title);
        n.put("body", body);
        n.put("orderId", orderId);
        n.put("read", false);
        n.put("createdAt", Timestamp.now());
        db.collection("notifications").add(n);
    }

    private int parseIntSafe(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim().replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong(((String) o).replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    private String money(long v) {
        return String.format(java.util.Locale.US, "%,d", v).replace(',', '.') + " đ";
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

                                ChatNotificationHelper.sendOrderNotification(
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
        if (outgoingListener != null) {
            outgoingListener.remove();
            outgoingListener = null;
        }
    }
}