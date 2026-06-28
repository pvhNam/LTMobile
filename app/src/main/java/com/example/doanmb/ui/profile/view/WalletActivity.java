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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Transaction;
import com.example.doanmb.ui.profile.adapter.TransactionAdapter;
import com.example.doanmb.ui.profile.viewmodel.WalletViewModel;

import java.text.NumberFormat;
import java.util.ArrayList;
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

    private boolean balanceHidden = false;

    private final List<Transaction> transactions = new ArrayList<>();
    private TransactionAdapter adapter;

    private WalletViewModel viewModel;

    // Nhận kết quả thanh toán từ VnpayPaymentActivity
    private final ActivityResultLauncher<Intent> vnpayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null
                        && data.getBooleanExtra(VnpayPaymentActivity.EXTRA_SUCCESS, false)) {
                    viewModel.creditWallet(data.getLongExtra(VnpayPaymentActivity.EXTRA_AMOUNT, 0L));
                } else {
                    Toast.makeText(this, "Thanh toán chưa hoàn tất hoặc đã huỷ", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        viewModel = new ViewModelProvider(this).get(WalletViewModel.class);
        if (!viewModel.isLoggedIn()) {
            Toast.makeText(this, "Vui lòng đăng nhập để dùng ví", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvBalance      = findViewById(R.id.tv_wallet_balance_big);
        tvEmpty        = findViewById(R.id.tv_wallet_empty);
        rvTransactions = findViewById(R.id.rv_transactions);
        btnTopUp       = findViewById(R.id.btn_wallet_topup);
        btnWithdraw    = findViewById(R.id.btn_wallet_withdraw);
        btnEye         = findViewById(R.id.btn_wallet_eye);
        ImageView btnBack = findViewById(R.id.btn_wallet_back);

        adapter = new TransactionAdapter(transactions, viewModel.getUid());
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnTopUp.setOnClickListener(v -> showTopUpDialog());
        btnWithdraw.setOnClickListener(v -> showWithdrawDialog());
        btnEye.setOnClickListener(v -> {
            balanceHidden = !balanceHidden;
            renderBalance();
        });

        observeViewModel();
        renderBalance();
    }

    private void observeViewModel() {
        viewModel.getBalance().observe(this, b -> renderBalance());
        viewModel.getTransactions().observe(this, list -> {
            transactions.clear();
            transactions.addAll(list);
            adapter.notifyDataSetChanged();
            boolean empty = transactions.isEmpty();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            rvTransactions.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void renderBalance() {
        tvBalance.setText(balanceHidden ? "••••••• đ" : MONEY.format(viewModel.currentBalance()) + " đ");
        btnEye.setImageResource(balanceHidden ? R.drawable.ic_eye_off : R.drawable.ic_eye);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sau khi thanh toán VNPay xong và quay lại app, làm mới số dư + lịch sử.
        if (viewModel != null && viewModel.isLoggedIn()) viewModel.refresh();
    }

    private void showTopUpDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Số tiền cần nạp (VNĐ)");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Nạp tiền vào ví")
                .setMessage("Số dư hiện tại: " + MONEY.format(viewModel.currentBalance()) + " đ")
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
                .setMessage("Số dư hiện tại: " + MONEY.format(viewModel.currentBalance()) + " đ")
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
                    if (amount > viewModel.currentBalance()) {
                        Toast.makeText(this, "Số dư không đủ để rút", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.withdraw(amount);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** Mở màn hình thanh toán VNPay (WebView) cho số tiền đã nhập. */
    private void topUp(long amount) {
        Intent i = new Intent(this, VnpayPaymentActivity.class);
        i.putExtra(VnpayPaymentActivity.EXTRA_AMOUNT, amount);
        vnpayLauncher.launch(i);
    }
}
