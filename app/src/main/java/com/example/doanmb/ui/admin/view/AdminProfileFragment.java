package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.doanmb.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AdminProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_profile, container, false);

        TextView tvName = view.findViewById(R.id.tv_admin_profile_name);
        TextView tvEmail = view.findViewById(R.id.tv_admin_profile_email);
        TextView tvInfoName = view.findViewById(R.id.tv_info_name);
        TextView tvInfoEmail = view.findViewById(R.id.tv_info_email);
        TextView tvAppWallet = view.findViewById(R.id.tv_app_wallet_balance);

        FirebaseFirestore.getInstance().collection("app_wallet").document("main")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || tvAppWallet == null) return;
                    Double bal = doc.getDouble("balance");
                    long b = bal != null ? Math.round(bal) : 0L;
                    tvAppWallet.setText(
                            java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(b) + " đ");
                });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String email = user.getEmail();
            if (email != null) {
                tvEmail.setText(email);
                tvInfoEmail.setText(email);
            }

            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (!isAdded() || doc == null) return;
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) {
                            tvName.setText(name);
                            tvInfoName.setText(name);
                        }
                    });
        }

        return view;
    }
}
