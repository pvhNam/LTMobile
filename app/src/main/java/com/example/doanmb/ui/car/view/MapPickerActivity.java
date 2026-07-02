package com.example.doanmb.ui.car.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;

import com.example.doanmb.R;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Chọn ĐIỂM ĐÓN và ĐIỂM ĐẾN trên bản đồ (OpenStreetMap qua osmdroid — miễn phí,
 * không cần API key/billing như Google Maps) để đặt chuyến "theo quãng đường".
 *
 * Cách dùng: chấm bản đồ lần 1 → điểm đón, lần 2 → điểm đến. Vị trí hiện tại của
 * người đặt xe được hiện bằng chấm xanh (osmdroid MyLocationOverlay). Khoảng cách
 * ưu tiên lấy theo ĐƯỜNG THỰC TẾ qua dịch vụ định tuyến công cộng OSRM (miễn phí,
 * không cần API key); nếu không có mạng/hết hạn mức thì lùi về đường chim bay
 * (Haversine, vẽ nét đứt để phân biệt). Bấm "Xác nhận" trả về:
 *  - {@link #RESULT_PICKUP}      (String) tên/địa chỉ điểm đón
 *  - {@link #RESULT_DEST}        (String) tên/địa chỉ điểm đến
 *  - {@link #RESULT_DISTANCE_KM} (double) quãng đường (km)
 *  - {@link #RESULT_PICKUP_LAT}/{@link #RESULT_PICKUP_LNG}, {@link #RESULT_DEST_LAT}/{@link #RESULT_DEST_LNG}
 *    (double) toạ độ thô của 2 điểm — lưu lại để sau này xem lại lộ trình (vd. tài xế xem sau khi nhận đơn).
 *
 * CHẾ ĐỘ XEM LẠI (view-only): truyền {@link #EXTRA_VIEW_ONLY}=true kèm
 * {@link #EXTRA_PICKUP_LAT}/{@link #EXTRA_PICKUP_LNG}/{@link #EXTRA_DEST_LAT}/{@link #EXTRA_DEST_LNG}
 * (+ tên/quãng đường tuỳ chọn) để mở màn ở chế độ chỉ xem lộ trình đã chốt — không cho chạm chọn lại điểm,
 * dùng cho tài xế xem lại lộ trình của đơn đã nhận.
 */
public class MapPickerActivity extends AppCompatActivity {

    public static final String RESULT_PICKUP      = "result_pickup";
    public static final String RESULT_DEST        = "result_dest";
    public static final String RESULT_DISTANCE_KM = "result_distance_km";
    public static final String RESULT_PICKUP_LAT  = "result_pickup_lat";
    public static final String RESULT_PICKUP_LNG  = "result_pickup_lng";
    public static final String RESULT_DEST_LAT    = "result_dest_lat";
    public static final String RESULT_DEST_LNG    = "result_dest_lng";

    public static final String EXTRA_VIEW_ONLY    = "extra_view_only";
    public static final String EXTRA_PICKUP_LAT   = "extra_pickup_lat";
    public static final String EXTRA_PICKUP_LNG   = "extra_pickup_lng";
    public static final String EXTRA_DEST_LAT     = "extra_dest_lat";
    public static final String EXTRA_DEST_LNG     = "extra_dest_lng";
    public static final String EXTRA_PICKUP_NAME  = "extra_pickup_name";
    public static final String EXTRA_DEST_NAME    = "extra_dest_name";
    public static final String EXTRA_DISTANCE_KM  = "extra_distance_km";

    private static final int REQ_LOCATION = 101;
    // Mặc định ngắm về TP.HCM khi chưa có vị trí người dùng
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(10.776530, 106.700981);
    private static final String OSRM_URL =
            "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson";

    private final OkHttpClient httpClient = new OkHttpClient();

    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;

    private boolean viewOnly = false;
    private double fixedDistanceKm = 0; // quãng đường đã chốt lúc đặt — ưu tiên hiện cái này ở chế độ xem lại

    private GeoPoint pickup, dest;
    private String pickupName = "", destName = "";
    private Marker pickupMarker, destMarker;
    private Polyline line;
    private double lastDistanceKm = 0;
    private int routeRequestId = 0;

    private TextView tvHint, tvPickup, tvDest, tvDistance;
    private MaterialButton btnConfirm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        tvHint     = findViewById(R.id.tv_map_hint);
        tvPickup   = findViewById(R.id.tv_map_pickup);
        tvDest     = findViewById(R.id.tv_map_dest);
        tvDistance = findViewById(R.id.tv_map_distance);
        btnConfirm = findViewById(R.id.btn_map_confirm);
        ImageView btnBack = findViewById(R.id.btn_map_back);
        MaterialButton btnReset = findViewById(R.id.btn_map_reset);

        viewOnly = getIntent().getBooleanExtra(EXTRA_VIEW_ONLY, false);

        btnBack.setOnClickListener(v -> finish());
        btnReset.setOnClickListener(v -> resetPoints());
        btnConfirm.setOnClickListener(v -> confirm());

        map = findViewById(R.id.map_view);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(12.0);
        map.getController().setCenter(DEFAULT_CENTER);

        MapEventsReceiver receiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (!viewOnly) onPick(p);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };
        map.getOverlays().add(0, new MapEventsOverlay(receiver));

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocationOverlay.setDrawAccuracyEnabled(true);
        map.getOverlays().add(myLocationOverlay);
        myLocationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            if (map == null || myLocationOverlay.getMyLocation() == null || viewOnly) return;
            map.getController().animateTo(myLocationOverlay.getMyLocation());
            map.getController().setZoom(15.0);
        }));

        enableMyLocation();

        if (viewOnly) setupViewOnlyMode();
    }

    /** Mở sẵn 2 điểm đón/đến đã chốt (vd. tài xế xem lại lộ trình của đơn đã nhận) — không cho chỉnh sửa. */
    private void setupViewOnlyMode() {
        Intent i = getIntent();
        double pLat = i.getDoubleExtra(EXTRA_PICKUP_LAT, 0);
        double pLng = i.getDoubleExtra(EXTRA_PICKUP_LNG, 0);
        double dLat = i.getDoubleExtra(EXTRA_DEST_LAT, 0);
        double dLng = i.getDoubleExtra(EXTRA_DEST_LNG, 0);
        if ((pLat == 0 && pLng == 0) || (dLat == 0 && dLng == 0)) {
            Toast.makeText(this, "Không có dữ liệu lộ trình để hiển thị", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        pickupName = i.getStringExtra(EXTRA_PICKUP_NAME) != null ? i.getStringExtra(EXTRA_PICKUP_NAME) : "";
        destName   = i.getStringExtra(EXTRA_DEST_NAME)   != null ? i.getStringExtra(EXTRA_DEST_NAME)   : "";
        fixedDistanceKm = i.getDoubleExtra(EXTRA_DISTANCE_KM, 0);

        pickup = new GeoPoint(pLat, pLng);
        dest = new GeoPoint(dLat, dLng);
        pickupMarker = addMarker(pickup, "Điểm đón", Color.parseColor("#2E7D32"), null);
        destMarker = addMarker(dest, "Điểm đến", Color.parseColor("#D32F2F"), null);
        tvPickup.setText("Điểm đón: " + (!pickupName.isEmpty() ? pickupName : latLngText(pickup)));
        tvDest.setText("Điểm đến: " + (!destName.isEmpty() ? destName : latLngText(dest)));
        tvHint.setText("Lộ trình chuyến đi");
        tvDistance.setText(fixedDistanceKm > 0
                ? String.format(Locale.US, "Quãng đường: %.1f km", fixedDistanceKm)
                : "Đang tính quãng đường...");

        findViewById(R.id.btn_map_reset).setVisibility(android.view.View.GONE);
        btnConfirm.setText("Đóng");
        btnConfirm.setEnabled(true);

        fetchRoute(pickup, dest, ++routeRequestId);
    }

    /** Chạm bản đồ: lần 1 đặt điểm đón, lần 2 đặt điểm đến, lần 3 trở đi đổi lại điểm đến. */
    private void onPick(GeoPoint point) {
        if (pickup == null) {
            pickup = point;
            pickupMarker = addMarker(point, "Điểm đón", Color.parseColor("#2E7D32"), pickupMarker);
            geocode(point, true);
            tvHint.setText("Chạm bản đồ để chọn ĐIỂM ĐẾN");
        } else {
            dest = point;
            destMarker = addMarker(point, "Điểm đến", Color.parseColor("#D32F2F"), destMarker);
            geocode(point, false);
            tvHint.setText("Chạm lại để đổi điểm đến, hoặc bấm Xác nhận");
        }
        updateDistance();
    }

    private Marker addMarker(GeoPoint point, String title, int tintColor, Marker old) {
        if (old != null) map.getOverlays().remove(old);
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setTitle(title);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(this, R.drawable.ic_pin);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(icon, tintColor);
        }
        marker.setIcon(icon);
        map.getOverlays().add(marker);
        map.invalidate();
        return marker;
    }

    private void resetPoints() {
        routeRequestId++; // huỷ mọi kết quả định tuyến đang chờ
        pickup = dest = null;
        pickupName = destName = "";
        lastDistanceKm = 0;
        if (pickupMarker != null) { map.getOverlays().remove(pickupMarker); pickupMarker = null; }
        if (destMarker != null) { map.getOverlays().remove(destMarker); destMarker = null; }
        if (line != null) { map.getOverlays().remove(line); line = null; }
        map.invalidate();
        tvHint.setText("Chạm bản đồ để chọn ĐIỂM ĐÓN");
        tvPickup.setText("Điểm đón: chưa chọn");
        tvDest.setText("Điểm đến: chưa chọn");
        tvDistance.setText("Quãng đường: --");
        btnConfirm.setEnabled(false);
    }

    /** Khi đủ 2 điểm: thử lấy quãng đường THỰC TẾ qua OSRM, lùi về đường chim bay nếu lỗi. */
    private void updateDistance() {
        if (line != null) { map.getOverlays().remove(line); line = null; }
        if (pickup == null || dest == null) {
            btnConfirm.setEnabled(false);
            tvDistance.setText("Quãng đường: --");
            return;
        }
        btnConfirm.setEnabled(false);
        tvDistance.setText("Đang tính quãng đường...");
        fetchRoute(pickup, dest, ++routeRequestId);
    }

    private void fetchRoute(GeoPoint from, GeoPoint to, int requestId) {
        String url = String.format(Locale.US, OSRM_URL,
                from.getLongitude(), from.getLatitude(), to.getLongitude(), to.getLatitude());
        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> applyStraightLineFallback(from, to, requestId));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        runOnUiThread(() -> applyStraightLineFallback(from, to, requestId));
                        return;
                    }
                    JSONObject json = new JSONObject(r.body().string());
                    JSONArray routes = json.optJSONArray("routes");
                    if (routes == null || routes.length() == 0) {
                        runOnUiThread(() -> applyStraightLineFallback(from, to, requestId));
                        return;
                    }
                    JSONObject route = routes.getJSONObject(0);
                    double km = Math.round(route.getDouble("distance") / 100.0) / 10.0;
                    JSONArray coords = route.getJSONObject("geometry").getJSONArray("coordinates");
                    List<GeoPoint> points = new ArrayList<>();
                    for (int i = 0; i < coords.length(); i++) {
                        JSONArray c = coords.getJSONArray(i);
                        points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                    }
                    runOnUiThread(() -> applyRoute(points, km, requestId));
                } catch (Exception e) {
                    runOnUiThread(() -> applyStraightLineFallback(from, to, requestId));
                }
            }
        });
    }

    private void applyRoute(List<GeoPoint> points, double km, int requestId) {
        if (requestId != routeRequestId || pickup == null || dest == null) return;
        drawLine(points, false);
        lastDistanceKm = km;
        // Ở chế độ xem lại: hiện đúng quãng đường đã chốt lúc đặt (đã tính tiền theo số này),
        // đường vẽ vẫn dùng tuyến OSRM mới lấy được để minh hoạ hình dạng lộ trình.
        double shownKm = (viewOnly && fixedDistanceKm > 0) ? fixedDistanceKm : km;
        tvDistance.setText(String.format(Locale.US, "Quãng đường: %.1f km", shownKm));
        btnConfirm.setEnabled(true);
        BoundingBox bounds = BoundingBox.fromGeoPoints(points);
        map.post(() -> map.zoomToBoundingBox(bounds, true, 100));
    }

    private void applyStraightLineFallback(GeoPoint from, GeoPoint to, int requestId) {
        if (requestId != routeRequestId || pickup == null || dest == null) return;
        drawLine(Arrays.asList(from, to), true);
        lastDistanceKm = haversineKm(from, to);
        double shownKm = (viewOnly && fixedDistanceKm > 0) ? fixedDistanceKm : lastDistanceKm;
        tvDistance.setText(viewOnly
                ? String.format(Locale.US, "Quãng đường: %.1f km", shownKm)
                : String.format(Locale.US, "Quãng đường (ước tính): %.1f km", shownKm));
        btnConfirm.setEnabled(true);
        BoundingBox bounds = BoundingBox.fromGeoPoints(Arrays.asList(from, to));
        map.post(() -> map.zoomToBoundingBox(bounds, true, 160));
    }

    private void drawLine(List<GeoPoint> points, boolean dashed) {
        if (line != null) map.getOverlays().remove(line);
        line = new Polyline();
        line.setPoints(points);
        line.getOutlinePaint().setColor(0xFF2E6BF0);
        line.getOutlinePaint().setStrokeWidth(8f);
        if (dashed) line.getOutlinePaint().setPathEffect(new DashPathEffect(new float[]{20f, 15f}, 0));
        map.getOverlays().add(line);
        map.invalidate();
    }

    private void confirm() {
        if (viewOnly) { finish(); return; }
        if (pickup == null || dest == null) {
            Toast.makeText(this, "Hãy chọn cả điểm đón và điểm đến", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent data = new Intent();
        data.putExtra(RESULT_PICKUP, !pickupName.isEmpty() ? pickupName : latLngText(pickup));
        data.putExtra(RESULT_DEST, !destName.isEmpty() ? destName : latLngText(dest));
        data.putExtra(RESULT_DISTANCE_KM, lastDistanceKm);
        data.putExtra(RESULT_PICKUP_LAT, pickup.getLatitude());
        data.putExtra(RESULT_PICKUP_LNG, pickup.getLongitude());
        data.putExtra(RESULT_DEST_LAT, dest.getLatitude());
        data.putExtra(RESULT_DEST_LNG, dest.getLongitude());
        setResult(RESULT_OK, data);
        finish();
    }

    // ── Vị trí hiện tại (chấm xanh của người đặt xe) ─────────────────────────────

    private void enableMyLocation() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            // Xin cả 2: nếu người dùng chỉ chọn "Vị trí gần đúng" (Android 12+) thì
            // ACCESS_FINE_LOCATION sẽ bị từ chối nhưng ACCESS_COARSE_LOCATION vẫn được cấp —
            // trước đây chỉ xin FINE nên rơi vào trường hợp này là coi như "không cấp quyền".
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        if (!isLocationServiceEnabled()) {
            // Có quyền nhưng người dùng đang TẮT định vị (GPS/Network) trong Cài đặt máy —
            // trường hợp này osmdroid sẽ không bao giờ có fix, cần báo rõ thay vì im lặng.
            promptEnableLocationServices();
            return;
        }
        myLocationOverlay.enableMyLocation();
        seedLastKnownLocation();
    }

    /** Bản đồ chỉ có vị trí khi máy đang BẬT ít nhất 1 trong 2 nguồn định vị. */
    private boolean isLocationServiceEnabled() {
        LocationManager lm = ContextCompat.getSystemService(this, LocationManager.class);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void promptEnableLocationServices() {
        new AlertDialog.Builder(this)
                .setTitle("Bật định vị")
                .setMessage("Máy đang tắt Dịch vụ vị trí (GPS) nên ứng dụng không lấy được vị trí của bạn. Mở Cài đặt để bật?")
                .setPositiveButton("Mở cài đặt", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Để sau", null)
                .show();
    }

    /**
     * osmdroid chỉ vẽ chấm xanh sau khi có fix GPS ĐẦU TIÊN, có thể mất vài chục giây
     * hoặc không bao giờ có nếu trong nhà. Lấy tạm vị trí cũ (last known) của hệ thống
     * để hiện ngay lập tức, đỡ cảm giác "không hoạt động" trong lúc chờ fix mới.
     */
    private void seedLastKnownLocation() {
        LocationManager lm = ContextCompat.getSystemService(this, LocationManager.class);
        if (lm == null) return;
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location loc = lm.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) best = loc;
            }
        } catch (SecurityException ignored) {}
        if (best != null && map != null) {
            map.getController().animateTo(new GeoPoint(best.getLatitude(), best.getLongitude()));
            map.getController().setZoom(15.0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        boolean granted = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) granted = true;
        }
        if (granted) {
            enableMyLocation();
        } else {
            Toast.makeText(this, "Cần quyền vị trí để hiện vị trí của bạn trên bản đồ", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (myLocationOverlay != null) enableMyLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────────

    /** Lấy tên địa chỉ từ toạ độ (chạy nền để không chặn UI). */
    private void geocode(GeoPoint point, boolean isPickup) {
        new Thread(() -> {
            String name = latLngText(point);
            try {
                Geocoder geocoder = new Geocoder(this, new Locale("vi"));
                List<Address> list = geocoder.getFromLocation(point.getLatitude(), point.getLongitude(), 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    String line0 = a.getMaxAddressLineIndex() >= 0 ? a.getAddressLine(0) : null;
                    if (line0 != null && !line0.isEmpty()) name = line0;
                }
            } catch (Exception ignored) {}
            final String finalName = name;
            runOnUiThread(() -> {
                if (isPickup) {
                    pickupName = finalName;
                    tvPickup.setText("Điểm đón: " + finalName);
                } else {
                    destName = finalName;
                    tvDest.setText("Điểm đến: " + finalName);
                }
            });
        }).start();
    }

    private static String latLngText(GeoPoint p) {
        return String.format(Locale.US, "%.5f, %.5f", p.getLatitude(), p.getLongitude());
    }

    /** Khoảng cách đường chim bay giữa 2 toạ độ (km) — chỉ dùng khi không lấy được đường thực tế. */
    private static double haversineKm(GeoPoint a, GeoPoint b) {
        double r = 6371.0; // bán kính Trái Đất (km)
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLng = Math.toRadians(b.getLongitude() - a.getLongitude());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLatitude())) * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
    }
}
