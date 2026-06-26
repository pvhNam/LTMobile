package com.example.doanmb.ui.profile.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Transaction;
import com.example.doanmb.data.repository.WalletRepository;
import com.example.doanmb.ui.profile.adapter.TransactionAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Màn hình Ví của người dùng: xem số dư, nạp tiền và lịch sử giao dịch. */
public class WalletActivity extends AppCompatActivity {

    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

    private TextView tvBalance;
    private View tvEmpty;
    private RecyclerView rvTransactions;
    private Button btnTopUp, btnWithdraw;
    private ImageView btnEye;

    private FirebaseFirestore db;
    private String uid;
    private long balance = 0L;
    private boolean balanceHidden = false;

    private final List<Transaction> transactions = new ArrayList<>();
    private TransactionAdapter adapter;

    // Nhận kết quả thanh toán từ VnpayPaymentActivity
    private final ActivityResultLauncher<Intent> vnpayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null
                        && data.getBooleanExtra(VnpayPaymentActivity.EXTRA_SUCCESS, false)) {
                    creditWallet(data.getLongExtra(VnpayPaymentActivity.EXTRA_AMOUNT, 0L));
                } else {
                    Toast.makeText(this, "Thanh toán chưa hoàn tất hoặc đã huỷ", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để dùng ví", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        uid = user.getUid();

        tvBalance      = findViewById(R.id.tv_wallet_balance_big);
        tvEmpty        = findViewById(R.id.tv_wallet_empty);
        rvTransactions = findViewById(R.id.rv_transactions);
        btnTopUp       = findViewById(R.id.btn_wallet_topup);
        btnWithdraw    = findViewById(R.id.btn_wallet_withdraw);
        btnEye         = findViewById(R.id.btn_wallet_eye);
        ImageView btnBack = findViewById(R.id.btn_wallet_back);

        adapter = new TransactionAdapter(transactions, uid);
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnTopUp.setOnClickListener(v -> showTopUpDialog());
        btnWithdraw.setOnClickListener(v -> showWithdrawDialog());
        btnEye.setOnClickListener(v -> {
            balanceHidden = !balanceHidden;
            renderBalance();
        });

        renderBalance();
        loadBalance();
        loadTransactions();
    }

    private void renderBalance() {
        tvBalance.setText(balanceHidden ? "••••••• đ" : MONEY.format(balance) + " đ");
        btnEye.setImageResource(balanceHidden ? R.drawable.ic_eye_off : R.drawable.ic_eye);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sau khi thanh toán VNPay xong và quay lại app, làm mới số dư + lịch sử.
        if (uid != null) {
            loadBalance();
            loadTransactions();
        }
    }

    private void loadBalance() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    Double b = doc.getDouble("balance");
                    balance = b != null ? Math.round(b) : 0L;
                    renderBalance();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi tải số dư: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    /**
     * Lấy các giao dịch liên quan tới người dùng: tiền vào (toUserId == uid)
     * và tiền ra (fromUserId == uid), gộp lại rồi sắp xếp mới nhất lên đầu.
     * Dùng 2 query đơn trường nên không cần composite index.
     */
    private void loadTransactions() {
        transactions.clear();
        db.collection("transactions").whereEqualTo("toUserId", uid).get()
                .addOnSuccessListener(in -> {
                    addAll(in);
                    db.collection("transactions").whereEqualTo("fromUserId", uid).get()
                            .addOnSuccessListener(out -> {
                                addAll(out);
                                sortAndShow();
                            })
                            .addOnFailureListener(e -> sortAndShow());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi tải giao dịch: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void addAll(@NonNull Iterable<QueryDocumentSnapshot> snaps) {
        for (QueryDocumentSnapshot d : snaps) {
            Transaction t = d.toObject(Transaction.class);
            transactions.add(t);
        }
    }

    private void sortAndShow() {
        Collections.sort(transactions, (a, b) -> {
            long ta = a.getCreatedAt() != null ? a.getCreatedAt().toDate().getTime() : 0L;
            long tb = b.getCreatedAt() != null ? b.getCreatedAt().toDate().getTime() : 0L;
            return Long.compare(tb, ta); // mới nhất trước
        });
        adapter.notifyDataSetChanged();
        boolean empty = transactions.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvTransactions.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showTopUpDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Số tiền cần nạp (VNĐ)");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Nạp tiền vào ví")
                .setMessage("Số dư hiện tại: " + MONEY.format(balance) + " đ")
                .setView(input)
                .setPositiveButton("Nạp", (dialog, which) -> {
                    String raw = input.getText().toString().trim().replaceAll("[^0-9]", "");
                    if (raw.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long amount = Long.parseLong(raw);
                    if (amount <= 0) {
                        Toast.makeText(this, "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    topUp(amount);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showWithdrawDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Số tiền cần rút (VNĐ)");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Rút tiền về tài khoản")
                .setMessage("Số dư hiện tại: " + MONEY.format(balance) + " đ")
                .setView(input)
                .setPositiveButton("Rút", (dialog, which) -> {
                    String raw = input.getText().toString().trim().replaceAll("[^0-9]", "");
                    if (raw.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long amount = Long.parseLong(raw);
                    if (amount <= 0) {
                        Toast.makeText(this, "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (amount > balance) {
                        Toast.makeText(this, "Số dư không đủ để rút", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    withdraw(amount);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void withdraw(long amount) {
        btnWithdraw.setEnabled(false);
        WalletRepository.withdraw(uid, amount, new WalletRepository.Callback() {
            @Override public void onSuccess() {
                btnWithdraw.setEnabled(true);
                Toast.makeText(WalletActivity.this,
                        "✅ Đã rút " + MONEY.format(amount) + " đ về tài khoản", Toast.LENGTH_SHORT).show();
                loadBalance();
                loadTransactions();
            }
            @Override public void onError(String message) {
                btnWithdraw.setEnabled(true);
                Toast.makeText(WalletActivity.this,
                        "Lỗi rút tiền: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Mở màn hình thanh toán VNPay (WebView) cho số tiền đã nhập. */
    private void topUp(long amount) {
        Intent i = new Intent(this, VnpayPaymentActivity.class);
        i.putExtra(VnpayPaymentActivity.EXTRA_AMOUNT, amount);
        vnpayLauncher.launch(i);
    }

    /** Sau khi VNPay báo thanh toán thành công → cộng tiền vào ví và ghi giao dịch. */
    private void creditWallet(long amount) {
        if (amount <= 0) return;
        WalletRepository.userTopUp(uid, amount, new WalletRepository.Callback() {
            @Override public void onSuccess() {
                Toast.makeText(WalletActivity.this,
                        "✅ Nạp " + MONEY.format(amount) + " đ thành công", Toast.LENGTH_SHORT).show();
                loadBalance();
                loadTransactions();
            }
            @Override public void onError(String message) {
                Toast.makeText(WalletActivity.this,
                        "Lỗi cập nhật ví: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
