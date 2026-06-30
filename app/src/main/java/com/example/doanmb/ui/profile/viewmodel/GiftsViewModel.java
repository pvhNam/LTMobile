package com.example.doanmb.ui.profile.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.UserVoucher;
import com.example.doanmb.data.model.Voucher;
import com.example.doanmb.data.repository.VoucherRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel cho màn hình Quà tặng: giữ điểm thưởng, danh mục voucher có thể
 * đổi và ví voucher của người dùng. Mọi truy vấn Firestore đi qua
 * {@link VoucherRepository}; View chỉ observe và render.
 */
public class GiftsViewModel extends ViewModel {

    private final String uid;

    private final MutableLiveData<Long> points = new MutableLiveData<>(0L);
    private final MutableLiveData<List<Voucher>> catalog = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<UserVoucher>> myVouchers = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public GiftsViewModel() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() { return uid != null; }

    public LiveData<Long> getPoints() { return points; }
    public LiveData<List<Voucher>> getCatalog() { return catalog; }
    public LiveData<List<UserVoucher>> getMyVouchers() { return myVouchers; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getLoading() { return loading; }

    /** Điểm hiện có (giá trị mới nhất đã tải, dùng để kiểm tra trước khi đổi). */
    public long currentPoints() {
        Long p = points.getValue();
        return p != null ? p : 0L;
    }

    /** Tải lại điểm + danh mục voucher + ví voucher. */
    public void refresh() {
        if (uid == null) return;
        loadPoints();
        loadCatalog();
        loadMyVouchers();
    }

    private void loadPoints() {
        VoucherRepository.loadPoints(uid, new VoucherRepository.PointsCallback() {
            @Override public void onLoaded(long value) { points.setValue(value); }
            @Override public void onError(String msg) { message.setValue("Lỗi tải điểm thưởng: " + msg); }
        });
    }

    private void loadCatalog() {
        VoucherRepository.loadCatalog(new VoucherRepository.VoucherListCallback() {
            @Override public void onLoaded(List<Voucher> list) { catalog.setValue(list); }
            @Override public void onError(String msg) { message.setValue("Lỗi tải danh sách quà tặng: " + msg); }
        });
    }

    private void loadMyVouchers() {
        VoucherRepository.loadMyVouchers(uid, new VoucherRepository.UserVoucherListCallback() {
            @Override public void onLoaded(List<UserVoucher> list) { myVouchers.setValue(list); }
            @Override public void onError(String msg) { message.setValue("Lỗi tải ví voucher: " + msg); }
        });
    }

    /** Đổi một voucher trong danh mục bằng điểm thưởng. */
    public void redeem(Voucher voucher) {
        if (uid == null || voucher == null) return;
        if (currentPoints() < voucher.getPointsCost()) {
            message.setValue("Bạn chưa đủ điểm để đổi quà này");
            return;
        }
        loading.setValue(true);
        VoucherRepository.redeem(uid, voucher, new VoucherRepository.Callback() {
            @Override public void onSuccess() {
                loading.setValue(false);
                message.setValue("🎉 Đổi quà thành công! Voucher đã có trong ví của bạn.");
                refresh();
            }
            @Override public void onError(String msg) {
                loading.setValue(false);
                message.setValue("❌ " + (msg != null ? msg : "Đổi quà thất bại"));
            }
        });
    }
}