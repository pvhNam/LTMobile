package com.example.doanmb.ui.car.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.CarRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel màn Đăng xe: lưu xe vào Firestore (đọc thông tin người bán + thêm document)
 * qua {@link CarRepository}. Phần chọn/upload ảnh (cần Context) vẫn nằm ở Fragment.
 */
public class PostCarViewModel extends ViewModel {

    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveResult = new MutableLiveData<>(); // true = thành công

    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getSaveResult() { return saveResult; }

    /** Lưu xe sau khi (nếu có) đã upload ảnh xong. */
    public void saveCar(String name, String price, String fullInfo, String type, String brand,
                        String year, String km, String location, List<String> imageUrls) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            message.setValue("Vui lòng đăng nhập trước!");
            saveResult.setValue(false);
            return;
        }
        final String uid = user.getUid();

        CarRepository.loadUserContact(uid, new CarRepository.OnContact() {
            @Override public void onLoaded(String sellerName, String sellerPhone) {
                addCar(uid, sellerName, sellerPhone, name, price, fullInfo, type, brand,
                        year, km, location, imageUrls);
            }
            @Override public void onError(String msg) {
                // Vẫn cho đăng dù thiếu thông tin liên hệ (như bản gốc khi doc tồn tại nhưng trống).
                addCar(uid, null, null, name, price, fullInfo, type, brand,
                        year, km, location, imageUrls);
            }
        });
    }

    private void addCar(String uid, String sellerName, String sellerPhone,
                        String name, String price, String fullInfo, String type, String brand,
                        String year, String km, String location, List<String> imageUrls) {
        Map<String, Object> car = new HashMap<>();
        car.put("name", name);
        car.put("price", price + (type.equals("rental") ? " đ/ngày" : " VNĐ"));
        car.put("info", fullInfo);
        car.put("type", type);
        car.put("brand", brand);
        car.put("fuel", "");
        car.put("condition", "");
        car.put("transmission", "");
        car.put("year", year);
        car.put("km", km);
        car.put("location", location);
        car.put("status", "pending");
        car.put("imageUrl", imageUrls.isEmpty() ? "" : imageUrls.get(0));
        car.put("imageUrls", imageUrls);
        car.put("userId", uid);
        car.put("sellerId", uid);
        car.put("sellerName", sellerName != null ? sellerName : "");
        car.put("sellerPhone", sellerPhone != null ? sellerPhone : "");
        car.put("createdAt", Timestamp.now());

        CarRepository.addCar(car, new CarRepository.OnCreated() {
            @Override public void onCreated(String carId) {
                message.setValue("Đăng tin thành công! Tin đang chờ admin duyệt.");
                saveResult.setValue(true);
            }
            @Override public void onError(String msg) {
                message.setValue("Lỗi: " + msg);
                saveResult.setValue(false);
            }
        });
    }
}
