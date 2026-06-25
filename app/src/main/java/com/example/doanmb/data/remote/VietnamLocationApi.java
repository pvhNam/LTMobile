package com.example.doanmb.data.remote;

import androidx.annotation.NonNull;
import android.util.Log;

import com.example.doanmb.data.dto.PlaceDto;
import com.example.doanmb.data.model.Place;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Lấy danh sách địa giới hành chính Việt Nam từ API miễn phí provinces.open-api.vn (v2).
 * Cấu trúc v2 (theo cải cách 2025): Tỉnh/Thành phố → Phường/Xã (không còn cấp quận/huyện).
 * Gọi mạng chạy trên thread nền; callback luôn được trả về main thread.
 */
public final class VietnamLocationApi {
    private static final String BASE = "https://provinces.open-api.vn/api/v2/";
    private static final String TAG = "VietnamLocationApi";

    private VietnamLocationApi() {
    }
    private static final VietnamLocationService SERVICE = build();

    public interface Callback {
        void onResult(List<Place> places);

        void onError(String message);
    }
    private static VietnamLocationService build() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC); // dùng NONE cho bản release

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VietnamLocationService.class);
    }
    /** Lấy danh sách tỉnh/thành phố (34 đơn vị). */
    public static void fetchProvinces(Callback cb) {
        SERVICE.getProvinces().enqueue(new retrofit2.Callback<List<PlaceDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<PlaceDto>> call,
                                   @NonNull Response<List<PlaceDto>> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    cb.onResult(mapList(resp.body()));
                } else {
                    cb.onError("HTTP " + resp.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<PlaceDto>> call, @NonNull Throwable t) {
                postError(cb, t);
            }
        });
    }

    /** Lấy danh sách phường/xã của một tỉnh/thành. */
    public static void fetchWards(int provinceCode, Callback cb) {
        SERVICE.getProvinceDetail(provinceCode, 2).enqueue(new retrofit2.Callback<PlaceDto>() {
            @Override
            public void onResponse(@NonNull Call<PlaceDto> call,
                                   @NonNull Response<PlaceDto> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    List<PlaceDto> wards = resp.body().wards;
                    cb.onResult(wards != null ? mapList(wards) : new ArrayList<>());
                } else {
                    cb.onError("HTTP " + resp.code());
                }
            }
            @Override
            public void onFailure(@NonNull Call<PlaceDto> call, @NonNull Throwable t) {
                postError(cb, t);
            }
        });
    }

    private static List<Place> mapList(List<PlaceDto> dtos) {
        List<Place> list = new ArrayList<>();
        for (PlaceDto d : dtos) {
            if (d != null && d.name != null && !d.name.isEmpty()) {
                list.add(d.toPlace());
            }
        }
        return list;
    }

    private static void postError(Callback cb, Throwable t) {
        Log.e(TAG, "fetch failed", t);
        String msg = (t.getMessage() != null) ? t.getMessage() : t.getClass().getSimpleName();
        cb.onError(msg);
    }
}