package com.example.doanmb.ui.profile.view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.core.helper.VnpayHelper;

/**
 * Mở trang thanh toán VNPay trong WebView và bắt URL trả về để xác định
 * giao dịch thành công hay thất bại. Trả kết quả về cho WalletActivity.
 */
public class VnpayPaymentActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT  = "amount";
    public static final String EXTRA_SUCCESS = "success";

    private long amount;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_vnpay_payment);
        EdgeToEdgeUtil.applyHeaderAndScroll(null, findViewById(R.id.header_bar));
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        amount = getIntent().getLongExtra(EXTRA_AMOUNT, 0L);
        if (amount <= 0) { finish(); return; }

        ImageView btnBack = findViewById(R.id.btn_vnpay_back);
        ProgressBar progress = findViewById(R.id.vnpay_progress);
        WebView web = findViewById(R.id.vnpay_webview);

        btnBack.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });

        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });

        String txnRef = VnpayHelper.newTxnRef();
        web.loadUrl(VnpayHelper.buildPaymentUrl(amount, txnRef));
    }

    /** Bắt URL trả về của VNPay. Trả true nếu đã xử lý xong (đóng màn). */
    private boolean handleUrl(String url) {
        if (url == null || !url.startsWith(VnpayHelper.RETURN_URL)) return false;

        Uri uri = Uri.parse(url);
        boolean valid   = VnpayHelper.isValidReturn(uri);
        boolean success = valid && "00".equals(uri.getQueryParameter("vnp_ResponseCode"));

        Intent data = new Intent();
        data.putExtra(EXTRA_SUCCESS, success);
        data.putExtra(EXTRA_AMOUNT, amount);
        setResult(success ? RESULT_OK : RESULT_CANCELED, data);
        finish();
        return true;
    }
}
