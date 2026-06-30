package com.example.doanmb.ui.driver.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.DriverRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * ViewModel màn Hồ sơ tài xế: chỉ tải tên + avatar. Điều hướng (chế độ khách, đăng xuất,
 * mở đánh giá) cần Context/Activity nên ở lại View; signOut() gọi trực tiếp trong View.
 */
public class DriverProfileViewModel extends ViewModel {

    private final MutableLiveData<String> name   = new MutableLiveData<>();
    private final MutableLiveData<String> avatar = new MutableLiveData<>();

    public LiveData<String> getName()   { return name; }
    public LiveData<String> getAvatar() { return avatar; }

    public void loadInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        DriverRepository.loadUserBrief(user.getUid(), (n, a) -> {
            name.setValue(n);
            avatar.setValue(a);
        });
    }
}
