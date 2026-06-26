package com.example.doanmb.data.model;

import com.google.firebase.Timestamp;

/**
 * Một "chuyến" thuê xe có tài xế.
 * Khách hàng tạo chuyến (chọn loại xe + hình thức thuê), tài xế lái loại xe đó
 * sẽ thấy chuyến ở trạng thái "waiting" và quẹt để nhận.
 *
 * Vòng đời status: waiting -> running -> completed (hoặc cancelled).
 */
public class Trip {

    // Trạng thái chuẩn hoá dùng chung toàn app
    public static final String STATUS_WAITING   = "waiting";   // Đang chờ tài xế nhận
    public static final String STATUS_RUNNING   = "running";   // Tài xế đang chạy
    public static final String STATUS_COMPLETED = "completed"; // Đã hoàn thành
    public static final String STATUS_CANCELLED = "cancelled"; // Đã huỷ

    // Hình thức thuê
    public static final String MODE_DISTANCE = "distance"; // Theo quãng đường
    public static final String MODE_DAY      = "day";      // Theo ngày
    public static final String MODE_MONTH    = "month";    // Theo tháng

    private String id;            // doc id Firestore (không lưu trong doc)

    private String customerId;
    private String customerName;
    private String customerPhone;

    private String carType;       // Loại xe yêu cầu, vd "4 chỗ", "7 chỗ", "16 chỗ"
    private String rentMode;      // distance | day | month

    private String pickup;        // Điểm đón
    private String destination;   // Điểm đến (cho thuê theo quãng đường)
    private double distanceKm;    // Quãng đường (km) khi thuê theo quãng đường
    private int duration;         // Số ngày/tháng khi thuê theo ngày/tháng

    private double price;         // Giá cước = doanh thu của tài xế
    private String note;

    private String status;        // waiting | running | completed | cancelled
    private String driverId;      // tài xế nhận chuyến
    private String driverName;

    private Timestamp createdAt;
    private Timestamp acceptedAt;
    private Timestamp completedAt;

    public Trip() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCarType() { return carType; }
    public void setCarType(String carType) { this.carType = carType; }

    public String getRentMode() { return rentMode; }
    public void setRentMode(String rentMode) { this.rentMode = rentMode; }

    public String getPickup() { return pickup; }
    public void setPickup(String pickup) { this.pickup = pickup; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Timestamp acceptedAt) { this.acceptedAt = acceptedAt; }

    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }

    /** Mô tả hình thức thuê để hiển thị. */
    public String rentModeLabel() {
        if (MODE_DISTANCE.equals(rentMode)) return "Theo quãng đường";
        if (MODE_DAY.equals(rentMode))      return "Theo ngày";
        if (MODE_MONTH.equals(rentMode))    return "Theo tháng";
        return rentMode != null ? rentMode : "--";
    }
}
