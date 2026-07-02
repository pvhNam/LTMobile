package com.example.doanmb.ui.driver.view;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.data.repository.DriverRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

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

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tab "Bản đồ" của tài xế: công tắc nhận chuyến + bản đồ THẬT (osmdroid, không cần
 * API key) hiện vị trí hiện tại của tài xế. Nếu tài xế đang có đơn "theo quãng đường"
 * đã nhận (accepted/in_progress), vẽ luôn lộ trình điểm đón → điểm đến lên bản đồ và
 * hiện thẻ tóm tắt quãng đường; nút "Điều hướng" mở Google Maps chỉ đường thẳng tới
 * điểm đón của đơn đó (không có đơn thì mở Google Maps trống).
 */
public class DriverMapFragment extends Fragment {

    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(10.776530, 106.700981);
    private static final String OSRM_URL =
            "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson";

    private final OkHttpClient httpClient = new OkHttpClient();

    private SwitchMaterial switchReceive;
    private MaterialButton btnToggle;
    private TextView tvReceiveState, tvName, tvNavigateHint;
    private CircleImageView ivAvatar;

    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;
    private Marker pickupMarker, destMarker;
    private Polyline routeLine;

    private View cardActiveTrip;
    private TextView tvActivePickup, tvActiveDest, tvActiveDistance;

    private FirebaseFirestore db;
    private String uid;
    private boolean online = true;

    // Điểm đón của đơn đang nhận (nếu có) — dùng cho nút "Điều hướng".
    private GeoPoint navigateTarget;
    private boolean locationPromptShown = false;

    private final ActivityResultLauncher<String[]> requestLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) enableMyLocation();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().load(requireContext(),
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        View v = inflater.inflate(R.layout.fragment_driver_map, container, false);
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        switchReceive = v.findViewById(R.id.switch_receive);
        btnToggle = v.findViewById(R.id.btn_toggle_receive);
        tvReceiveState = v.findViewById(R.id.tv_receive_state);
        tvName = v.findViewById(R.id.tv_dh_name);
        ivAvatar = v.findViewById(R.id.iv_dh_avatar);
        tvNavigateHint = v.findViewById(R.id.tv_navigate_hint);

        cardActiveTrip   = v.findViewById(R.id.card_active_trip);
        tvActivePickup   = v.findViewById(R.id.tv_active_pickup);
        tvActiveDest     = v.findViewById(R.id.tv_active_dest);
        tvActiveDistance = v.findViewById(R.id.tv_active_distance);

        switchReceive.setOnClickListener(x -> setOnline(switchReceive.isChecked()));
        btnToggle.setOnClickListener(x -> setOnline(!online));

        v.findViewById(R.id.card_navigate).setOnClickListener(x -> openMaps());
        v.findViewById(R.id.card_move_history).setOnClickListener(x ->
                Toast.makeText(getContext(), "Lịch sử di chuyển đang được phát triển", Toast.LENGTH_SHORT).show());

        setupMap(v);
        return v;
    }

    private void setupMap(View root) {
        map = root.findViewById(R.id.map_view_driver);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(13.0);
        map.getController().setCenter(DEFAULT_CENTER);
        // Chỉ xem, không cho chạm chọn điểm trên bản đồ này.
        map.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { return false; }
            @Override public boolean longPressHelper(GeoPoint p) { return false; }
        }));

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
        myLocationOverlay.setDrawAccuracyEnabled(true);
        map.getOverlays().add(myLocationOverlay);
        myLocationOverlay.runOnFirstFix(() -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (map == null || myLocationOverlay.getMyLocation() == null || navigateTarget != null) return;
                map.getController().animateTo(myLocationOverlay.getMyLocation());
                map.getController().setZoom(15.0);
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        enableMyLocation();
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
        loadActiveTrip();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        map = null;
        myLocationOverlay = null;
        pickupMarker = destMarker = null;
        routeLine = null;
        locationPromptShown = false;
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

    // ── Chuyến đang nhận (accepted/in_progress, kiểu "theo quãng đường") ─────────

    private void loadActiveTrip() {
        if (uid.isEmpty()) return;
        DriverRepository.loadAllDriverOrders(uid, new DriverRepository.OnSnapshot() {
            @Override
            public void onLoaded(QuerySnapshot snap) {
                if (!isAdded()) return;
                QueryDocumentSnapshot best = null;
                for (QueryDocumentSnapshot d : snap) {
                    String status = d.getString("status");
                    if (!"accepted".equals(status) && !"in_progress".equals(status)) continue;
                    if (numberOrZero(d.get("distanceKm")) <= 0) continue;
                    if (numberOrZero(d.get("pickupLat")) == 0 && numberOrZero(d.get("pickupLng")) == 0) continue;
                    if (numberOrZero(d.get("destLat")) == 0 && numberOrZero(d.get("destLng")) == 0) continue;
                    // Ưu tiên chuyến đang chạy hơn chuyến mới nhận.
                    if (best == null || "in_progress".equals(status)) best = d;
                }
                if (best != null) showActiveTrip(best); else clearActiveTrip();
            }
            @Override public void onError(String msg) { }
        });
    }

    private static double numberOrZero(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0d;
    }

    private void showActiveTrip(QueryDocumentSnapshot d) {
        String pickupName = d.getString("pickup");
        String destName   = d.getString("destination");
        double km   = numberOrZero(d.get("distanceKm"));
        GeoPoint pickup = new GeoPoint(numberOrZero(d.get("pickupLat")), numberOrZero(d.get("pickupLng")));
        GeoPoint dest   = new GeoPoint(numberOrZero(d.get("destLat")), numberOrZero(d.get("destLng")));

        navigateTarget = pickup;
        if (tvNavigateHint != null) tvNavigateHint.setText("Chỉ đường tới điểm đón");

        cardActiveTrip.setVisibility(View.VISIBLE);
        tvActivePickup.setText("Đón: " + (pickupName != null && !pickupName.isEmpty() ? pickupName : latLngText(pickup)));
        tvActiveDest.setText("Đến: " + (destName != null && !destName.isEmpty() ? destName : latLngText(dest)));
        tvActiveDistance.setText(String.format(Locale.US, "Quãng đường: %.1f km", km));

        if (map == null) return;
        if (pickupMarker != null) map.getOverlays().remove(pickupMarker);
        if (destMarker != null) map.getOverlays().remove(destMarker);
        pickupMarker = addMarker(pickup, "Điểm đón", Color.parseColor("#2E7D32"));
        destMarker = addMarker(dest, "Điểm đến", Color.parseColor("#D32F2F"));
        map.invalidate();

        fetchRoute(pickup, dest);
    }

    private void clearActiveTrip() {
        navigateTarget = null;
        if (tvNavigateHint != null) tvNavigateHint.setText("Mở Google Maps");
        cardActiveTrip.setVisibility(View.GONE);
        if (map == null) return;
        if (pickupMarker != null) { map.getOverlays().remove(pickupMarker); pickupMarker = null; }
        if (destMarker   != null) { map.getOverlays().remove(destMarker);   destMarker = null; }
        if (routeLine    != null) { map.getOverlays().remove(routeLine);    routeLine = null; }
        map.invalidate();
    }

    private Marker addMarker(GeoPoint point, String title, int tintColor) {
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setTitle(title);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_pin);
        if (icon != null) {
            icon = androidx.core.graphics.drawable.DrawableCompat.wrap(icon.mutate());
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, tintColor);
        }
        marker.setIcon(icon);
        map.getOverlays().add(marker);
        return marker;
    }

    /** Vẽ hình dạng tuyến đường thật qua OSRM (chỉ để minh hoạ — số km hiện theo đơn đã lưu). */
    private void fetchRoute(GeoPoint from, GeoPoint to) {
        String url = String.format(Locale.US, OSRM_URL,
                from.getLongitude(), from.getLatitude(), to.getLongitude(), to.getLatitude());
        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postToUi(() -> drawRouteLine(Arrays.asList(from, to), true));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        postToUi(() -> drawRouteLine(Arrays.asList(from, to), true));
                        return;
                    }
                    JSONObject json = new JSONObject(r.body().string());
                    JSONArray routes = json.optJSONArray("routes");
                    if (routes == null || routes.length() == 0) {
                        postToUi(() -> drawRouteLine(Arrays.asList(from, to), true));
                        return;
                    }
                    JSONArray coords = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                    List<GeoPoint> points = new ArrayList<>();
                    for (int i = 0; i < coords.length(); i++) {
                        JSONArray c = coords.getJSONArray(i);
                        points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                    }
                    postToUi(() -> drawRouteLine(points, false));
                } catch (Exception e) {
                    postToUi(() -> drawRouteLine(Arrays.asList(from, to), true));
                }
            }
        });
    }

    private void postToUi(Runnable r) {
        if (isAdded()) requireActivity().runOnUiThread(r);
    }

    private void drawRouteLine(List<GeoPoint> points, boolean dashed) {
        if (map == null || navigateTarget == null) return; // đã bị clear trong lúc chờ mạng
        if (routeLine != null) map.getOverlays().remove(routeLine);
        routeLine = new Polyline();
        routeLine.setPoints(points);
        routeLine.getOutlinePaint().setColor(0xFF2E6BF0);
        routeLine.getOutlinePaint().setStrokeWidth(8f);
        if (dashed) routeLine.getOutlinePaint().setPathEffect(new android.graphics.DashPathEffect(new float[]{20f, 15f}, 0));
        map.getOverlays().add(routeLine);
        BoundingBox bounds = BoundingBox.fromGeoPoints(points);
        map.post(() -> map.zoomToBoundingBox(bounds, true, 100));
        map.invalidate();
    }

    private static String latLngText(GeoPoint p) {
        return String.format(Locale.US, "%.5f, %.5f", p.getLatitude(), p.getLongitude());
    }

    // ── Vị trí hiện tại (chấm xanh của tài xế) ────────────────────────────────────

    private void enableMyLocation() {
        if (!isAdded() || myLocationOverlay == null) return;
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            requestLocationLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        if (!isLocationServiceEnabled()) {
            if (!locationPromptShown) { locationPromptShown = true; promptEnableLocationServices(); }
            return;
        }
        myLocationOverlay.enableMyLocation();
        seedLastKnownLocation();
    }

    private boolean isLocationServiceEnabled() {
        LocationManager lm = ContextCompat.getSystemService(requireContext(), LocationManager.class);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void seedLastKnownLocation() {
        if (navigateTarget != null) return; // đã có lộ trình để hiện, khỏi nhảy về vị trí tài xế
        LocationManager lm = ContextCompat.getSystemService(requireContext(), LocationManager.class);
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

    /** Mở Google Maps chỉ đường tới điểm đón của đơn đang nhận (nếu có), không thì mở trống. */
    private void openMaps() {
        try {
            Uri uri = navigateTarget != null
                    ? Uri.parse(String.format(Locale.US, "google.navigation:q=%f,%f&mode=d",
                            navigateTarget.getLatitude(), navigateTarget.getLongitude()))
                    : Uri.parse("geo:0,0?q=trạm xăng");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            String url = navigateTarget != null
                    ? String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%f,%f",
                            navigateTarget.getLatitude(), navigateTarget.getLongitude())
                    : "https://www.google.com/maps";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    private void promptEnableLocationServices() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Bật định vị")
                .setMessage("Máy đang tắt Dịch vụ vị trí (GPS) nên bản đồ không hiện được vị trí của bạn. Mở Cài đặt để bật?")
                .setPositiveButton("Mở cài đặt", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Để sau", null)
                .show();
    }
}
