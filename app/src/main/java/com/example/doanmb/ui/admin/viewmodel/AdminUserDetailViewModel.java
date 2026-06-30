package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * ViewModel màn admin xem chi tiết 1 người dùng: tải hồ sơ, thống kê (số xe đăng,
 * đơn mua/bán), nạp tiền và đổi quyền.
 */
public class AdminUserDetailViewModel extends ViewModel {

    private final MutableLiveData<DocumentSnapshot> user = new MutableLiveData<>();
    private final MutableLiveData<Integer> carsCount = new MutableLiveData<>();
    private final MutableLiveData<Integer> buyCount  = new MutableLiveData<>();
    private final MutableLiveData<Integer> sellCount = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishEvent = new MutableLiveData<>();

    private String userId;
    private String userName = "";
    private String currentRole = "CUSTOMER";
    private long balance = 0L;

    public LiveData<DocumentSnapshot> getUser() { return user; }
    public LiveData<Integer> getCarsCount() { return carsCount; }
    public LiveData<Integer> getBuyCount() { return buyCount; }
    public LiveData<Integer> getSellCount() { return sellCount; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getFinishEvent() { return finishEvent; }

    public String getUserName() { return userName; }
    public String getCurrentRole() { return currentRole; }
    public long getBalance() { return balance; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void start(String userId) {
        if (this.userId != null) return; // đã tải
        this.userId = userId;
        loadUser();
        loadStats();
    }

    private void loadUser() {
        db().collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        message.setValue("Người dùng không còn tồn tại");
                        finishEvent.setValue(true);
                        return;
                    }
                    userName    = str(doc.getString("name"), "Không có tên");
                    currentRole = str(doc.getString("role"), "CUSTOMER");
                    balance     = getLong(doc, "balance");
                    user.setValue(doc);
                })
                .addOnFailureListener(e -> {
                    message.setValue("Lỗi tải người dùng: " + e.getMessage());
                    finishEvent.setValue(true);
                });
    }

    /** Đếm số xe đã đăng, số đơn đã mua / đã bán của user. */
    private void loadStats() {
        db().collection("cars").whereEqualTo("sellerId", userId).get()
                .addOnSuccessListener(s -> carsCount.setValue(s.size()))
                .addOnFailureListener(e -> carsCount.setValue(0));
        db().collection("orders").whereEqualTo("buyerId", userId).get()
                .addOnSuccessListener(s -> buyCount.setValue(s.size()))
                .addOnFailureListener(e -> buyCount.setValue(0));
        db().collection("orders").whereEqualTo("sellerId", userId).get()
                .addOnSuccessListener(s -> sellCount.setValue(s.size()))
                .addOnFailureListener(e -> sellCount.setValue(0));
    }

    public void topUp(long amount) {
        WalletRepository.topUp(userId, amount, new WalletRepository.Callback() {
            @Override public void onSuccess() {
                message.setValue("✅ Đã nạp tiền cho " + userName);
                loadUser();
            }
            @Override public void onError(String msg) {
                message.setValue("Lỗi nạp tiền: " + msg);
            }
        });
    }

    public void changeRole(String newRole) {
        db().collection("users").document(userId).update("role", newRole)
                .addOnSuccessListener(v -> {
                    message.setValue("Đã đổi quyền thành " + newRole);
                    loadUser();
                })
                .addOnFailureListener(e -> message.setValue("Lỗi: " + e.getMessage()));
    }

    private static long getLong(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    private static String str(String value, String def) {
        return (value != null && !value.isEmpty()) ? value : def;
    }
}
