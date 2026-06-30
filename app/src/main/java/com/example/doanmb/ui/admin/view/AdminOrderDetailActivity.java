package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.viewmodel.AdminOrderDetailViewModel;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Màn admin xem chi tiết 1 đơn hàng + xác nhận / hoàn thành / hủy. */
public class AdminOrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "ORDER_ID";

    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

    private TextView tvCarName, tvPrice, tvType, tvStatus, tvDate, tvId;
    private TextView tvBuyer, tvBuyerPhone, tvBuyerCccd, tvNote, tvSeller;
    private TextView tvDays, tvStartDate, tvPickup, tvDestination, tvDistance;
    private TextView tvTotal, tvDeposit, tvPayment, tvDepositStatus;
    private View cardRent, cardTrip, layoutActions;
    private Button btnCancel, btnPrimary;

    private AdminOrderDetailViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_order_detail);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        viewModel = new ViewModelProvider(this).get(AdminOrderDetailViewModel.class);
        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);

        tvCarName       = findViewById(R.id.tv_admin_order_car_name);
        tvPrice         = findViewById(R.id.tv_admin_order_price);
        tvType          = findViewById(R.id.tv_admin_order_type);
        tvStatus        = findViewById(R.id.tv_admin_order_status);
        tvDate          = findViewById(R.id.tv_admin_order_date);
        tvId            = findViewById(R.id.tv_admin_order_id);
        tvBuyer         = findViewById(R.id.tv_admin_order_buyer);
        tvBuyerPhone    = findViewById(R.id.tv_admin_order_buyer_phone);
        tvBuyerCccd     = findViewById(R.id.tv_admin_order_buyer_cccd);
        tvNote          = findViewById(R.id.tv_admin_order_note);
        tvSeller        = findViewById(R.id.tv_admin_order_seller);
        tvDays          = findViewById(R.id.tv_admin_order_days);
        tvStartDate     = findViewById(R.id.tv_admin_order_start_date);
        tvPickup        = findViewById(R.id.tv_admin_order_pickup);
        tvDestination   = findViewById(R.id.tv_admin_order_destination);
        tvDistance      = findViewById(R.id.tv_admin_order_distance);
        tvTotal         = findViewById(R.id.tv_admin_order_total);
        tvDeposit       = findViewById(R.id.tv_admin_order_deposit);
        tvPayment       = findViewById(R.id.tv_admin_order_payment);
        tvDepositStatus = findViewById(R.id.tv_admin_order_deposit_status);
        cardRent        = findViewById(R.id.card_admin_order_rent);
        cardTrip        = findViewById(R.id.card_admin_order_trip);
        layoutActions   = findViewById(R.id.layout_admin_order_actions);
        btnCancel       = findViewById(R.id.btn_admin_order_cancel);
        btnPrimary      = findViewById(R.id.btn_admin_order_primary);

        findViewById(R.id.btn_admin_order_back).setOnClickListener(v -> finish());

        if (orderId == null || orderId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy đơn hàng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvId.setText("🆔  Mã đơn: " + orderId);

        viewModel.getOrder().observe(this, this::bindOrder);
        viewModel.getBuyerName().observe(this, name -> tvBuyer.setText("👤  " + name));
        viewModel.getSellerName().observe(this, name -> tvSeller.setText("👤  " + name));
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getFinishEvent().observe(this, f -> {
            if (Boolean.TRUE.equals(f)) finish();
        });

        viewModel.start(orderId);
    }

    private void bindOrder(DocumentSnapshot doc) {
        if (doc == null) return;

        String carName = str(doc.getString("carName"), "Xe không xác định");
        String carPrice = str(doc.getString("carPrice"), "--");
        String type = str(doc.getString("type"), "Mua xe");
        String status = str(doc.getString("status"), "pending");

        tvCarName.setText(carName);
        tvPrice.setText(carPrice);
        tvType.setText(type);
        applyStatusStyle(status);

        Timestamp createdAt = doc.getTimestamp("createdAt");
        tvDate.setText("Ngày đặt: " + (createdAt != null
                ? new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(createdAt.toDate())
                : "--"));

        // Khách hàng & chủ xe (tên) cập nhật qua observer buyerName/sellerName.
        showOrHide(tvBuyerPhone, "📞  ", doc.getString("renterPhone"));
        showOrHide(tvBuyerCccd, "🪪  CCCD: ", doc.getString("renterCccd"));
        showOrHide(tvNote, "📝  Ghi chú: ", doc.getString("note"));

        // Thuê theo ngày vs chuyến đi theo km
        boolean isTrip = "distance".equals(doc.getString("rentMode"));
        boolean hasRentInfo = doc.getString("days") != null || doc.getString("startDate") != null;

        if (isTrip) {
            cardTrip.setVisibility(View.VISIBLE);
            tvPickup.setText("📍  Điểm đón: " + str(doc.getString("pickup"), "--"));
            tvDestination.setText("🏁  Điểm đến: " + str(doc.getString("destination"), "--"));
            Object dist = doc.get("distanceKm");
            tvDistance.setText("Khoảng cách: " + (dist != null ? dist + " km" : "--"));
        } else {
            cardTrip.setVisibility(View.GONE);
        }

        if (!isTrip && hasRentInfo) {
            cardRent.setVisibility(View.VISIBLE);
            tvDays.setText("Số ngày: " + str(doc.getString("days"), "--"));
            tvStartDate.setText("Ngày bắt đầu: " + str(doc.getString("startDate"), "--"));
        } else {
            cardRent.setVisibility(View.GONE);
        }

        // Thanh toán
        tvTotal.setText(money(doc.get("totalAmount"), carPrice));
        tvDeposit.setText(money(doc.get("depositAmount"), "0 đ"));
        tvPayment.setText(paymentLabel(doc.getString("paymentMethod")));
        tvDepositStatus.setText(depositStatusLabel(doc.getString("depositStatus")));

        bindActions(status);
    }

    /** Hiện nút theo trạng thái đơn: pending → Xác nhận, confirmed → Hoàn thành. */
    private void bindActions(String status) {
        boolean isPending = "pending".equals(status);
        boolean isConfirmed = "confirmed".equals(status);

        if (!isPending && !isConfirmed) {
            layoutActions.setVisibility(View.GONE);
            return;
        }

        layoutActions.setVisibility(View.VISIBLE);
        btnCancel.setOnClickListener(v -> askCancelOrder());
        if (isPending) {
            btnPrimary.setText("Xác nhận");
            btnPrimary.setOnClickListener(v -> viewModel.confirmOrder());
        } else {
            btnPrimary.setText("Hoàn thành");
            btnPrimary.setOnClickListener(v -> askCompleteOrder());
        }
    }

    private void askCompleteOrder() {
        new AlertDialog.Builder(this)
                .setTitle("Hoàn thành đơn")
                .setMessage("Xác nhận đơn đã hoàn thành?\nApp sẽ trừ 15% hoa hồng từ tiền cọc và trả 85% còn lại về ví chủ xe/tài xế.")
                .setPositiveButton("Hoàn thành", (d, w) -> viewModel.completeOrder())
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void askCancelOrder() {
        new AlertDialog.Builder(this)
                .setTitle("Hủy đơn hàng")
                .setMessage("Bạn có chắc muốn hủy đơn hàng này không?\nHành động này sẽ trả xe về trạng thái đang bán.")
                .setPositiveButton("Hủy đơn", (d, w) -> viewModel.cancelOrder())
                .setNegativeButton("Không", null)
                .show();
    }

    private void applyStatusStyle(String status) {
        switch (status) {
            case "pending":
                tvStatus.setText("Chờ xác nhận");
                tvStatus.setBackgroundColor(0xFFFFF3E0);
                tvStatus.setTextColor(0xFFE65100);
                break;
            case "confirmed":
                tvStatus.setText("Đã xác nhận");
                tvStatus.setBackgroundColor(0xFFE8F5E9);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
            case "completed":
                tvStatus.setText("Hoàn thành");
                tvStatus.setBackgroundColor(0xFFE3F2FD);
                tvStatus.setTextColor(0xFF1565C0);
                break;
            case "rejected":
            case "cancelled":
                tvStatus.setText("Đã hủy");
                tvStatus.setBackgroundColor(0xFFFFCDD2);
                tvStatus.setTextColor(0xFFC62828);
                break;
            default:
                tvStatus.setText(status);
                tvStatus.setBackgroundColor(0xFFEEEEEE);
                tvStatus.setTextColor(0xFF757575);
        }
    }

    private void showOrHide(TextView tv, String prefix, String value) {
        if (value == null || value.trim().isEmpty()) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(prefix + value);
        }
    }

    private static String paymentLabel(String method) {
        if (method == null) return "--";
        switch (method) {
            case "cash":   return "Tiền mặt";
            case "wallet": return "Ví trong app";
            default:        return method;
        }
    }

    private static String depositStatusLabel(String status) {
        if (status == null) return "--";
        switch (status) {
            case "none":     return "Không cọc";
            case "held":     return "Đang giữ cọc";
            case "settled":  return "Đã chia tiền";
            case "refunded": return "Đã hoàn cọc";
            default:          return status;
        }
    }

    /** Format số tiền (Number) thành "x đ", fallback nếu thiếu. */
    private static String money(Object value, String def) {
        if (value instanceof Number) return MONEY.format(((Number) value).longValue()) + " đ";
        return def;
    }

    private static String str(String value, String def) {
        return (value != null && !value.isEmpty()) ? value : def;
    }
}
