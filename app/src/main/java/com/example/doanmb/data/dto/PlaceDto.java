package com.example.doanmb.data.dto;

import com.example.doanmb.data.model.Place;
import com.google.gson.annotations.SerializedName;
import java.util.List;

// Map trực tiếp từ JSON của API provinces.open-api.vn
public class PlaceDto {
    @SerializedName("name") public String name;
    @SerializedName("code") public int code;
    // chỉ có khi gọi ?depth=2
    @SerializedName("wards") public List<PlaceDto> wards;

    public Place toPlace() {
        return new Place(name != null ? name : "", code);
    }
}
