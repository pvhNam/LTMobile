package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Voucher;
import com.example.doanmb.ui.admin.adapter.VoucherAdminAdapter;
import com.example.doanmb.ui.admin.viewmodel.AdminVouchersViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Màn quản lý catalog voucher cho admin: xem, thêm, sửa, bật/tắt, xoá. */
public class AdminVouchersFragment extends Fragment {

    private RecyclerView rvVouchers;
    private TextView tvCount, tvEmpty;
    private VoucherAdminAdapter adapter;
    private final List<Voucher> voucherList = new ArrayList<>();
    private AdminVouchersViewModel viewModel;
    /** Dialog thêm/sửa đang mở, để đóng khi lưu thành công. */
    private androidx.appcompat.app.AlertDialog editDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_vouchers, container, false);
        viewModel = new ViewModelProvider(this).get(AdminVouchersViewModel.class);

        view.findViewById(R.id.btn_vouchers_back).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        rvVouchers = view.findViewById(R.id.rv_vouchers);
        tvCount = view.findViewById(R.id.tv_voucher_count);
        tvEmpty = view.findViewById(R.id.tv_empty_vouchers);

        adapter = new VoucherAdminAdapter(voucherList, new VoucherAdminAdapter.OnVoucherActionListener() {
            @Override public void onEdit(Voucher voucher) { showEditDialog(voucher); }
            @Override public void onToggleActive(Voucher voucher) { viewModel.toggleActive(voucher); }
            @Override public void onDelete(Voucher voucher) { confirmDelete(voucher); }
        });
        rvVouchers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchers.setAdapter(adapter);

        view.findViewById(R.id.btn_add_voucher).setOnClickListener(v -> showEditDialog(null));

        viewModel.getVouchers().observe(getViewLifecycleOwner(), this::render);
        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getSaveSuccess().observe(getViewLifecycleOwner(), ok -> {
            if (Boolean.TRUE.equals(ok) && editDialog != null) {
                editDialog.dismiss();
                editDialog = null;
            }
        });

        viewModel.load();
        return view;
    }

    private void render(List<Voucher> list) {
        voucherList.clear();
        voucherList.addAll(list);
        adapter.updateList(voucherList);
        tvCount.setText(voucherList.size() + " voucher");
        tvEmpty.setVisibility(voucherList.isEmpty() ? View.VISIBLE : View.GONE);
        rvVouchers.setVisibility(voucherList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmDelete(Voucher voucher) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xoá voucher")
                .setMessage("Xoá hẳn voucher \"" + voucher.getTitle() + "\" khỏi danh mục? Voucher khách đã đổi vẫn dùng được bình thường.")
                .setPositiveButton("Xoá", (d, w) -> viewModel.delete(voucher))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    /** Mở dialog thêm (voucher == null) hoặc sửa voucher đã có. */
    private void showEditDialog(@Nullable Voucher existing) {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_voucher, null);

        EditText etTitle       = form.findViewById(R.id.et_voucher_title);
        EditText etDescription = form.findViewById(R.id.et_voucher_description);
        RadioGroup rgType      = form.findViewById(R.id.rg_voucher_type);
        EditText etValue       = form.findViewById(R.id.et_voucher_value);
        EditText etMaxDiscount = form.findViewById(R.id.et_voucher_max_discount);
        EditText etMinOrder    = form.findViewById(R.id.et_voucher_min_order);
        EditText etPoints      = form.findViewById(R.id.et_voucher_points);
        EditText etQuantity    = form.findViewById(R.id.et_voucher_quantity);
        EditText etValidDays   = form.findViewById(R.id.et_voucher_valid_days);
        CheckBox cbActive      = form.findViewById(R.id.cb_voucher_active);

        if (existing != null) {
            etTitle.setText(existing.getTitle());
            etDescription.setText(existing.getDescription());
            rgType.check(Voucher.TYPE_FIXED.equals(existing.getDiscountType())
                    ? R.id.rb_voucher_fixed : R.id.rb_voucher_percent);
            etValue.setText(numText(existing.getDiscountValue()));
            etMaxDiscount.setText(String.valueOf(existing.getMaxDiscount()));
            etMinOrder.setText(String.valueOf(existing.getMinOrderAmount()));
            etPoints.setText(String.valueOf(existing.getPointsCost()));
            etQuantity.setText(String.valueOf(existing.getQuantity()));
            etValidDays.setText(String.valueOf(existing.getValidDays()));
            cbActive.setChecked(existing.isActive());
        } else {
            rgType.check(R.id.rb_voucher_percent);
            cbActive.setChecked(true);
        }

        editDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? "Thêm voucher" : "Sửa voucher")
                .setView(form)
                .setPositiveButton("Lưu", null) // gắn sau để chặn đóng dialog khi nhập sai
                .setNegativeButton("Huỷ", null)
                .create();
        editDialog.show();
        editDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(btn -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) { etTitle.setError("Nhập tên voucher"); return; }

                    double value = parseDouble(etValue.getText().toString());
                    if (value <= 0) { etValue.setError("Giá trị giảm phải > 0"); return; }
                    int points = (int) parseLong(etPoints.getText().toString());
                    if (points < 0) { etPoints.setError("Điểm không hợp lệ"); return; }

                    boolean isPercent = rgType.getCheckedRadioButtonId() == R.id.rb_voucher_percent;
                    if (isPercent && value > 100) { etValue.setError("Phần trăm phải ≤ 100"); return; }

                    Voucher v = new Voucher();
                    if (existing != null) v.setId(existing.getId());
                    v.setTitle(title);
                    v.setDescription(etDescription.getText().toString().trim());
                    v.setDiscountType(isPercent ? Voucher.TYPE_PERCENT : Voucher.TYPE_FIXED);
                    v.setDiscountValue(value);
                    v.setMaxDiscount(parseLong(etMaxDiscount.getText().toString()));
                    v.setMinOrderAmount(parseLong(etMinOrder.getText().toString()));
                    v.setPointsCost(points);
                    v.setQuantity(parseQuantity(etQuantity.getText().toString()));
                    v.setValidDays((int) parseLong(etValidDays.getText().toString()));
                    v.setActive(cbActive.isChecked());

                    viewModel.save(v); // dialog tự đóng qua observer saveSuccess
                });
    }

    private static String numText(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    /** Số lượng kho: để trống => -1 (không giới hạn). */
    private static int parseQuantity(String s) {
        if (s == null || s.trim().isEmpty()) return -1;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.load();
    }
}
