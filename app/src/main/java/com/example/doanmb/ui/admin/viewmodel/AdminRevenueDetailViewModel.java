package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ViewModel màn chi tiết doanh thu: tải song song hai dataset thô (đơn đã chốt +
 * bài đăng xe) rồi phát ra một lần khi cả hai sẵn sàng. Việc lọc theo khoảng
 * ngày / dựng danh sách hiển thị do View tự xử lý trên dữ liệu đã tải.
 */
public class AdminRevenueDetailViewModel extends ViewModel {

    /** Hai dataset thô để View tính tổng + dựng danh sách. */
    public static class Data {
        public final List<QueryDocumentSnapshot> orders;
        public final List<QueryDocumentSnapshot> cars;
        public Data(List<QueryDocumentSnapshot> orders, List<QueryDocumentSnapshot> cars) {
            this.orders = orders;
            this.cars = cars;
        }
    }

    private final MutableLiveData<Data> data = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private final List<QueryDocumentSnapshot> orders = new ArrayList<>();
    private final List<QueryDocumentSnapshot> cars = new ArrayList<>();
    private int loaded = 0;

    public LiveData<Data> getData() { return data; }
    public LiveData<String> getError() { return error; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void load() {
        loaded = 0;
        orders.clear();
        cars.clear();

        // Doanh thu = đơn đã chốt: gồm cả "confirmed" lẫn "completed"
        db().collection("orders").whereIn("status", Arrays.asList("confirmed", "completed")).get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) orders.add(doc);
                    onReady();
                })
                .addOnFailureListener(e -> error.setValue(e.getMessage()));

        db().collection("cars").get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) cars.add(doc);
                    onReady();
                })
                .addOnFailureListener(e -> error.setValue(e.getMessage()));
    }

    private void onReady() {
        loaded++;
        if (loaded < 2) return;
        data.setValue(new Data(new ArrayList<>(orders), new ArrayList<>(cars)));
    }
}
