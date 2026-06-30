package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.Voucher;
import com.example.doanmb.data.repository.VoucherRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel màn quản lý catalog voucher (admin). Mọi truy vấn đi qua
 * {@link VoucherRepository}; View chỉ observe và render.
 */
public class AdminVouchersViewModel extends ViewModel {

    private final MutableLiveData<List<Voucher>> vouchers = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    /** Bật true khi lưu voucher thành công để View đóng dialog đang mở. */
    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>();

    public LiveData<List<Voucher>> getVouchers() { return vouchers; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getSaveSuccess() { return saveSuccess; }

    public void load() {
        VoucherRepository.loadAllVouchers(new VoucherRepository.VoucherListCallback() {
            @Override public void onLoaded(List<Voucher> list) { vouchers.setValue(list); }
            @Override public void onError(String msg) { message.setValue("Lỗi tải voucher: " + msg); }
        });
    }

    public void save(Voucher voucher) {
        VoucherRepository.saveVoucher(voucher, new VoucherRepository.Callback() {
            @Override public void onSuccess() {
                message.setValue("✅ Đã lưu voucher");
                saveSuccess.setValue(true);
                load();
            }
            @Override public void onError(String msg) { message.setValue("Lỗi lưu: " + msg); }
        });
    }

    public void toggleActive(Voucher voucher) {
        if (voucher.getId() == null) return;
        boolean newActive = !voucher.isActive();
        VoucherRepository.setVoucherActive(voucher.getId(), newActive, new VoucherRepository.Callback() {
            @Override public void onSuccess() {
                message.setValue(newActive ? "Đã mở lại voucher" : "Đã tạm tắt voucher");
                load();
            }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    public void delete(Voucher voucher) {
        if (voucher.getId() == null) return;
        VoucherRepository.deleteVoucher(voucher.getId(), new VoucherRepository.Callback() {
            @Override public void onSuccess() {
                message.setValue("🗑 Đã xoá voucher");
                load();
            }
            @Override public void onError(String msg) { message.setValue("Lỗi xoá: " + msg); }
        });
    }
}
