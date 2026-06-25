package com.example.doanmb.ui.car.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.car.adapter.ProfileCarAdapter;
import com.example.doanmb.data.model.Car;
import com.example.doanmb.core.helper.FavoriteHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/** Danh sách xe yêu thích. Xe đã bán/ẩn/xóa sẽ tự bị loại (và dọn khỏi mục yêu thích). */
public class FavoriteCarsActivity extends AppCompatActivity {

    private RecyclerView rvFavorites;
    private View tvEmpty;
    private ProfileCarAdapter adapter;
    private final List<Car> favCars = new ArrayList<>();
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_cars);
        db = FirebaseFirestore.getInstance();

        findViewById(R.id.btn_back_favorites).setOnClickListener(v -> finish());
        rvFavorites = findViewById(R.id.rv_favorites);
        tvEmpty = findViewById(R.id.tv_favorites_empty);

        rvFavorites.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProfileCarAdapter(favCars, this::openCarDetail);
        rvFavorites.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void openCarDetail(Car car) {
        Intent intent = new Intent(this, CarDetailActivity.class);
        intent.putExtra("CAR_DATA", car);
        intent.putExtra("CAR_ID", car.getId());
        intent.putExtra("SELLER_ID", car.getSellerId());
        intent.putExtra("CAR_TYPE", car.getType());
        startActivity(intent);
    }

    private void loadFavorites() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            favCars.clear();
            adapter.updateList(favCars);
            showEmpty(true);
            return;
        }
        String uid = user.getUid();
        FavoriteHelper.loadIds(uid, ids -> {
            if (isFinishing()) return;
            if (ids.isEmpty()) {
                favCars.clear();
                adapter.updateList(favCars);
                showEmpty(true);
                return;
            }
            favCars.clear();
            final int[] remaining = { ids.size() };
            for (String id : ids) {
                db.collection("cars").document(id).get().addOnCompleteListener(task -> {
                    if (isFinishing()) return;
                    DocumentSnapshot doc = task.isSuccessful() ? task.getResult() : null;
                    if (doc != null && doc.exists()) {
                        String status = doc.getString("status");
                        // Xe đã bán/ẩn → bỏ khỏi danh sách yêu thích
                        if ("sold".equals(status) || "hidden".equals(status)) {
                            FavoriteHelper.remove(uid, id);
                        } else {
                            favCars.add(buildCar(doc));
                        }
                    } else {
                        // Xe đã bị xóa → dọn khỏi yêu thích
                        FavoriteHelper.remove(uid, id);
                    }
                    if (--remaining[0] == 0) {
                        adapter.updateList(favCars);
                        showEmpty(favCars.isEmpty());
                    }
                });
            }
        });
    }

    private Car buildCar(DocumentSnapshot doc) {
        String name = doc.getString("name");
        String price = doc.getString("price");
        String info = doc.getString("info");
        String type = doc.getString("type");
        String imageUrl = doc.getString("imageUrl");
        String sellerId = doc.getString("sellerId");
        if (sellerId == null) sellerId = doc.getString("userId");
        Car car = new Car(name != null ? name : "", price != null ? price : "",
                info != null ? info : "", android.R.drawable.ic_menu_gallery);
        car.setId(doc.getId());
        car.setType(type != null ? type : "");
        car.setImageUrl(imageUrl != null ? imageUrl : "");
        car.setSellerId(sellerId != null ? sellerId : "");
        return car;
    }

    private void showEmpty(boolean empty) {
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvFavorites.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
