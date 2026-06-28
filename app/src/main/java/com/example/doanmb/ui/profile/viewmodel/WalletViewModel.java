package com.example.doanmb.ui.profile.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.Transaction;
import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel cho màn hình Ví: giữ số dư + lịch sử giao dịch và xử lý nạp/rút tiền.
 * Mọi truy vấn Firestore đều đi qua {@link WalletRepository}; View chỉ observe và render.
 */
public class WalletViewModel extends ViewModel {

    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

    private final String uid;

    private final MutableLiveData<Long> balance = new MutableLiveData<>(0L);
    private final MutableLiveData<List<Transaction>> transactions = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public WalletViewModel() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() { return uid != null; }

    public String getUid() { return uid; }

    public LiveData<Long> getBalance() { return balance; }

    public LiveData<List<Transaction>> getTransactions() { return transactions; }

    public LiveData<String> getMessage() { return message; }

    /** Số dư mới nhất đã tải về (dùng cho hộp thoại nạp/rút). */
    public long currentBalance() {
        Long b = balance.getValue();
        return b != null ? b : 0L;
    }

    /** Tải lại số dư + lịch sử giao dịch. */
    public void refresh() {
        loadBalance();
        loadTransactions();
    }

    private void loadBalance() {
        WalletRepository.loadBalance(uid, new WalletRepository.BalanceCallback() {
            @Override public void onLoaded(long value) { balance.setValue(value); }
            @Override public void onError(String msg) { message.setValue("Lỗi tải số dư: " + msg); }
        });
    }

    private void loadTransactions() {
        WalletRepository.loadTransactions(uid, new WalletRepository.TransactionsCallback() {
            @Override public void onLoaded(List<Transaction> list) { transactions.setValue(list); }
            @Override public void onError(String msg) { message.setValue("Lỗi tải giao dịch: " + msg); }
        });
    }

    /** Rút tiền khỏi ví về tài khoản; số dư & lịch sử sẽ được làm mới khi thành công. */
    public void withdraw(long amount) {
        WalletRepository.withdraw(uid, amount, new WalletRepository.Callback() {
            @Override public void onSuccess() {
                message.setValue("✅ Đã rút " + MONEY.format(amount) + " đ về tài khoản");
                refresh();
            }
            @Override public void onError(String msg) {
                message.setValue("Lỗi rút tiền: " + msg);
            }
        });
    }

    /** Cộng tiền vào ví sau khi VNPay báo thanh toán thành công. */
    public void creditWallet(long amount) {
        if (amount <= 0) return;
        WalletRepository.userTopUp(uid, amount, new WalletRepository.Callback() {
            @Override public void onSuccess() {
                message.setValue("✅ Nạp " + MONEY.format(amount) + " đ thành công");
                refresh();
            }
            @Override public void onError(String msg) {
                message.setValue("Lỗi cập nhật ví: " + msg);
            }
        });
    }
}
