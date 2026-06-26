package com.example.doanmb.ui.driver.view;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Tab "Bản đồ" (thiết kế driver3): công tắc nhận chuyến, bản đồ minh hoạ kèm
 * các vùng nhu cầu cao và lối tắt điều hướng. Bản đồ là ảnh tĩnh (không cần API key).
 */
public class DriverMapFragment extends Fragment {

    private SwitchMaterial switchReceive;
    private MaterialButton btnToggle;
    private TextView tvReceiveState, tvName;
    private CircleImageView ivAvatar;

    private FirebaseFirestore db;
    private String uid;
    private boolean online = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_driver_map, container, false);
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        switchReceive = v.findViewById(R.id.switch_receive);
        btnToggle = v.findViewById(R.id.btn_toggle_receive);
        tvReceiveState = v.findViewById(R.id.tv_receive_state);
        tvName = v.findViewById(R.id.tv_dh_name);
        ivAvatar = v.findViewById(R.id.iv_dh_avatar);

        switchReceive.setOnClickListener(x -> setOnline(switchReceive.isChecked()));
        btnToggle.setOnClickListener(x -> setOnline(!online));

        v.findViewById(R.id.card_navigate).setOnClickListener(x -> openMaps());
        v.findViewById(R.id.card_move_history).setOnClickListener(x ->
                Toast.makeText(getContext(), "Lịch sử di chuyển đang được phát triển", Toast.LENGTH_SHORT).show());

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            tvName.setText(doc.getString("name") != null ? doc.getString("name") : "Tài xế");
            String avatar = doc.getString("avatarUrl");
            if (avatar != null && !avatar.isEmpty()) Glide.with(this).load(avatar).into(ivAvatar);
            Boolean on = doc.getBoolean("driverOnline");
            online = on == null || on;
            applyOnlineUi();
        });
    }

    private void setOnline(boolean value) {
        online = value;
        applyOnlineUi();
        if (!uid.isEmpty()) db.collection("users").document(uid).update("driverOnline", value);
    }

    private void applyOnlineUi() {
        switchReceive.setChecked(online);
        tvReceiveState.setText(online ? "Đang nhận chuyến" : "Đã tắt nhận chuyến");
        btnToggle.setText(online ? "Tắt nhận chuyến" : "Bật nhận chuyến");
    }

    /** Mở ứng dụng bản đồ (Google Maps nếu có, không thì trình duyệt). */
    private void openMaps() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=trạm xăng"));
            intent.setPackage("com.google.android.apps.maps");
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps")));
        }
    }
}
