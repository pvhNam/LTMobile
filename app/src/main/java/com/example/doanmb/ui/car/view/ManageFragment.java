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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.service.OrderReminderService;
import com.example.doanmb.ui.car.adapter.ProfileCarAdapter;
import com.example.doanmb.ui.car.adapter.RequestAdapter;
import com.example.doanmb.ui.car.viewmodel.ManageViewModel;
import com.example.doanmb.core.helper.ChatNotificationHelper;
import com.example.doanmb.data.model.Car;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManageFragment extends Fragment {

    private CardView cardTabPosts, cardTabRequests;
    private LinearLayout tabPostsContent, tabRequestsContent;
    private TextView tvTabPosts, tvTabRequests;
    private LinearLayout layoutMyPosts, layoutRequests;

    private RecyclerView rvMyPosts;
    private TextView tvMyPostCount, tvEmptyPosts;
    private ProfileCarAdapter myPostsAdapter;
    private final List<Car> myCarList = new ArrayList<>();

    private RecyclerView rvRequests;
    private TextView tvRequestCount, tvEmptyRequests;
    private RequestAdapter requestAdapter;
    private final List<Map<String, Object>> orderList = new ArrayList<>();
    private final List<String> orderIds = new ArrayList<>();

    private FirebaseFirestore db;
    private String currentUserId;
    private ManageViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage, container, false);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) currentUserId = user.getUid();

        viewModel = new ViewModelProvider(this).get(ManageViewModel.class);

        initViews(view);
        setupTabs();
        setupRecyclerViews();
        observeViewModel();

        viewModel.start();

        Bundle args = getArguments();
        if (args != null && args.getBoolean("showRequests", false)) {
            showTab(false); // false = tab Yêu cầu nhận được
        }

        return view;
    }

    private void observeViewModel() {
        viewModel.getMyPosts().observe(getViewLifecycleOwner(), cars -> {
            myCarList.clear();
            myCarList.addAll(cars);
            myPostsAdapter.updateList(myCarList);
            tvMyPostCount.setText("Tin đã đăng: " + myCarList.size());
            tvEmptyPosts.setVisibility(myCarList.isEmpty() ? View.VISIBLE : View.GONE);
            rvMyPosts.setVisibility(myCarList.isEmpty() ? View.GONE : View.VISIBLE);
        });

        viewModel.getRequests().observe(getViewLifecycleOwner(), items -> {
            orderList.clear();
            orderIds.clear();
            for (ManageViewModel.OrderItem it : items) {
                orderIds.add(it.id);
                orderList.add(it.data);
            }
            requestAdapter.updateList(orderList, orderIds);
            tvRequestCount.setText("Yêu cầu: " + orderList.size());
            tvEmptyRequests.setVisibility(orderList.isEmpty() ? View.VISIBLE : View.GONE);
            rvRequests.setVisibility(orderList.isEmpty() ? View.GONE : View.VISIBLE);
        });

        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.getCancelReminderEvent().observe(getViewLifecycleOwner(), orderId -> {
            if (orderId != null && getContext() != null) OrderReminderService.cancel(requireContext(), orderId);
        });

        viewModel.getNotifyBuyerEvent().observe(getViewLifecycleOwner(), e -> {
            if (e != null) notifyBuyerOrderStatus(e.orderId, e.type);
        });
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
        requestAdapter = new RequestAdapter(orderList, orderIds, currentUserId,
                new RequestAdapter.OnActionListener() {
            @Override
            public void onConfirm(String orderId, String carId, Map<String, Object> order) {
                viewModel.confirmRequest(orderId, carId);
            }
            @Override
            public void onReject(String orderId, String carId) {
                viewModel.rejectRequest(orderId, carId);
            }
            @Override
            public void onMarkReturned(String orderId, Map<String, Object> order) {
                showMarkReturnedDialog(orderId, order);
            }
            @Override
            public void onCancelOwn(String orderId, Map<String, Object> order) {
                showCancelDialog(orderId, order);
            }
            @Override
            public void onExtend(String orderId, Map<String, Object> order) {
                showExtendDialog(orderId, order);
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

    private void openCarDetail(Car car) {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), CarDetailActivity.class);
        intent.putExtra("CAR_DATA", car);
        intent.putExtra("CAR_ID", car.getId());
        intent.putExtra("SELLER_ID", car.getSellerId());
        intent.putExtra("CAR_TYPE", car.getType());
        startActivity(intent);
    }

    private void showMarkReturnedDialog(String orderId, Map<String, Object> order) {
        ManageViewModel.ReturnInvoice inv = viewModel.computeReturnInvoice(order);
        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận đã trả xe")
                .setMessage((inv.lateDays > 0
                        ? "⚠️ Khách trả TRỄ " + inv.lateDays + " ngày.\nPhí phạt: " + money(inv.penalty) + "\n"
                        : "Khách trả đúng hạn.\n")
                        + "Tiền thuê: " + money(inv.total) + "\nTổng hóa đơn: " + money(inv.invoiceTotal))
                .setPositiveButton("Gửi hóa đơn", (d, w) -> viewModel.sendInvoice(orderId, order, inv))
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void showCancelDialog(String orderId, Map<String, Object> order) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Hủy yêu cầu thuê")
                .setMessage("Bạn chắc chắn muốn hủy yêu cầu thuê xe này?")
                .setPositiveButton("Hủy yêu cầu", (d, w) -> viewModel.cancelOwnOrder(orderId, order))
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void showExtendDialog(String orderId, Map<String, Object> order) {
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Số ngày muốn thuê thêm");
        new AlertDialog.Builder(requireContext())
                .setTitle("Gia hạn thuê xe")
                .setMessage("Nhập số ngày muốn thuê thêm. Yêu cầu sẽ được gửi đến chủ xe.")
                .setView(input)
                .setPositiveButton("Gửi", (d, w) ->
                        viewModel.extendOrder(orderId, order,
                                ManageViewModel.parseIntSafe(input.getText().toString())))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private String money(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.') + " đ";
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
                                if (getContext() == null) return;
                                String sellerName = userSnap.getString("name");
                                if (sellerName == null || sellerName.isEmpty()) sellerName = "Chủ xe";

                                ChatNotificationHelper.sendOrderNotification(
                                        requireContext(),
                                        buyerId,
                                        myUid,
                                        sellerName,
                                        carName != null ? carName : "",
                                        carId   != null ? carId   : "",
                                        type,
                                        orderId
                                );
                            });
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null && viewModel.isLoggedIn()) viewModel.loadMyPosts();
    }
}
