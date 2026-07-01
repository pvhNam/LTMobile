package com.example.doanmb.ui.car.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.ui.car.viewmodel.ReviewDialogViewModel;
import com.google.android.material.chip.Chip;

public class ReviewDialogFragment extends DialogFragment {

    private static final String ARG_ORDER_ID        = "orderId";
    private static final String ARG_DRIVER_ID       = "driverId";
    private static final String ARG_CAR_ID          = "carId";
    private static final String ARG_NOTIFICATION_ID = "notificationId";

    private RatingBar ratingBar;
    private EditText etComment;
    private Button btnSubmit, btnCancel;

    private ReviewDialogViewModel viewModel;

    public static ReviewDialogFragment newInstance(String orderId, String driverId, String carId) {
        return newInstance(orderId, driverId, carId, "");
    }

    public static ReviewDialogFragment newInstance(String orderId, String driverId,
                                                   String carId, String notificationId) {
        ReviewDialogFragment f = new ReviewDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORDER_ID,        orderId);
        args.putString(ARG_DRIVER_ID,       driverId);
        args.putString(ARG_CAR_ID,          carId);
        args.putString(ARG_NOTIFICATION_ID, notificationId != null ? notificationId : "");
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);

        viewModel = new ViewModelProvider(this).get(ReviewDialogViewModel.class);
        if (getArguments() != null) {
            viewModel.init(
                    getArguments().getString(ARG_ORDER_ID),
                    getArguments().getString(ARG_DRIVER_ID),
                    getArguments().getString(ARG_CAR_ID),
                    getArguments().getString(ARG_NOTIFICATION_ID, ""));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        android.app.Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            );
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ratingBar  = view.findViewById(R.id.rating_bar_input);
        etComment  = view.findViewById(R.id.et_review_comment);
        btnSubmit  = view.findViewById(R.id.btn_submit_review);
        btnCancel  = view.findViewById(R.id.btn_cancel_review);

        btnCancel.setOnClickListener(v -> dismiss());
        btnSubmit.setOnClickListener(v ->
                viewModel.submitReview(ratingBar.getRating(),
                        etComment.getText() != null ? etComment.getText().toString() : ""));

        setupChip(view, R.id.chip_ontime,   "Đúng giờ, đáng tin cậy.");
        setupChip(view, R.id.chip_safe,     "Lái xe an toàn, tốc độ ổn định.");
        setupChip(view, R.id.chip_friendly, "Tài xế thân thiện, nhiệt tình.");
        setupChip(view, R.id.chip_clean,    "Xe sạch sẽ, thoải mái.");

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getSubmitting().observe(getViewLifecycleOwner(), submitting ->
                btnSubmit.setEnabled(!Boolean.TRUE.equals(submitting)));

        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.getSuccess().observe(getViewLifecycleOwner(), ok -> {
            if (!Boolean.TRUE.equals(ok) || !isAdded()) return;
            if (getActivity() != null) getActivity().setResult(android.app.Activity.RESULT_OK);
            dismiss();
        });
    }

    private void setupChip(View root, int chipId, String text) {
        Chip chip = root.findViewById(chipId);
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            String current = etComment.getText() != null ? etComment.getText().toString().trim() : "";
            if (!current.contains(text)) {
                String appended = current.isEmpty() ? text : current + " " + text;
                etComment.setText(appended);
                etComment.setSelection(etComment.getText().length());
            }
        });
    }
}
