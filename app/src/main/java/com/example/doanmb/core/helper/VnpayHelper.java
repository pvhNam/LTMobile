package com.example.doanmb.core.helper;

import android.net.Uri;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tạo URL thanh toán VNPay và kiểm tra chữ ký URL trả về — xử lý hoàn toàn phía
 * client (không cần backend). sandbox.
 *
 * ⚠️ Vì key nằm trong app và ví cộng ở client nên KHÔNG dùng cho sản phẩm thật.
 */
public final class VnpayHelper {

    // ── Cấu hình sandbox ────────────────────────────────────────────────────
    public static final String TMN_CODE   = "TZ9M4UN9";
    public static final String HASH_SECRET = "IF93SG5WPI42RT3YYVCDE20UE0LWV1KL";
    public static final String PAY_URL    = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    /** URL giả để bắt khi VNPay redirect về . */
    public static final String RETURN_URL = "https://carvia.app/vnpay_return";

    private VnpayHelper() {}

    /** Sinh mã giao dịch duy nhất. */
    public static String newTxnRef() {
        return createDate(0) + "_" + (int) (Math.random() * 1_000_000);
    }

    /** Tạo URL thanh toán đã ký (theo chuẩn sample Java chính thức của VNPay). */
    public static String buildPaymentUrl(long amount, String txnRef) {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_Version",   "2.1.0");
        p.put("vnp_Command",   "pay");
        p.put("vnp_TmnCode",   TMN_CODE);
        p.put("vnp_Amount",    String.valueOf(amount * 100)); // đơn vị xu
        p.put("vnp_CurrCode",  "VND");
        p.put("vnp_TxnRef",    txnRef);
        p.put("vnp_OrderInfo", "Nap vi Carvia " + txnRef);
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale",    "vn");
        p.put("vnp_ReturnUrl", RETURN_URL);
        p.put("vnp_IpAddr",    "127.0.0.1");
        p.put("vnp_CreateDate", createDate(0));
        p.put("vnp_ExpireDate", createDate(15)); // hết hạn sau 15 phút

        List<String> names = new ArrayList<>(p.keySet());
        Collections.sort(names);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query    = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            String name  = names.get(i);
            String value = p.get(name);
            if (value == null || value.isEmpty()) continue;
            hashData.append(name).append('=').append(enc(value));
            query.append(enc(name)).append('=').append(enc(value));
            if (i < names.size() - 1) {
                hashData.append('&');
                query.append('&');
            }
        }
        String secureHash = hmacSHA512(HASH_SECRET, hashData.toString());
        return PAY_URL + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    /** Kiểm tra chữ ký của URL trả về từ VNPay. */
    public static boolean isValidReturn(Uri uri) {
        String received = uri.getQueryParameter("vnp_SecureHash");
        if (received == null) return false;

        Map<String, String> fields = new HashMap<>();
        for (String name : uri.getQueryParameterNames()) {
            if ("vnp_SecureHash".equals(name) || "vnp_SecureHashType".equals(name)) continue;
            fields.put(name, uri.getQueryParameter(name));
        }
        List<String> names = new ArrayList<>(fields.keySet());
        Collections.sort(names);
        StringBuilder hashData = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            String name  = names.get(i);
            String value = fields.get(name);
            if (value == null || value.isEmpty()) continue;
            hashData.append(name).append('=').append(enc(value));
            if (i < names.size() - 1) hashData.append('&');
        }
        String expected = hmacSHA512(HASH_SECRET, hashData.toString());
        return expected.equalsIgnoreCase(received);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.US_ASCII.toString());
        } catch (Exception e) {
            return s;
        }
    }

    private static String createDate(int plusMinutes) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return f.format(new Date(System.currentTimeMillis() + plusMinutes * 60_000L));
    }

    private static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
