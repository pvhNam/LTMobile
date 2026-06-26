package com.example.doanmb.data.remote;
import com.example.doanmb.data.dto.PlaceDto;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface VietnamLocationService {
    // GET /p/  -> mảng tỉnh/thành
    @GET("p/")
    Call<List<PlaceDto>> getProvinces();
    // GET /p/{code}?depth=2 -> object 1 tỉnh, chứa "wards"
    @GET("p/{code}")
    Call<PlaceDto> getProvinceDetail(
            @Path("code") int provinceCode,
            @Query("depth") int depth
    );
}