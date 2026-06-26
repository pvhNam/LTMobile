package com.example.doanmb.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

public class User {
    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String avatarUrl;

    // ----- Hồ sơ tài xế -----
    private String driverStatus;   // null | "pending" | "approved" | "rejected"
    private String cccd;           // Số CCCD
    private String licenseNumber;  // Số bằng lái xe
    private String cccdImageUrl;   // Ảnh CCCD (Cloudinary)
    private String licenseImageUrl;// Ảnh bằng lái (Cloudinary)
    private String driverCarType;  // Loại xe tài xế có thể lái, vd "4 chỗ"
    private boolean isDriver;      // true nếu user đã được duyệt làm tài xế
    private boolean driverOnline;  // Tài xế đang online hay offline
    private Timestamp appliedAt;   // Thời điểm gửi đăng ký tài xế

    // ----- Ví trong app -----
    private double balance;        // Số dư ví (VNĐ). Mặc định 0 nếu Firestore thiếu field.

    public User() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getDriverStatus() { return driverStatus; }
    public void setDriverStatus(String driverStatus) { this.driverStatus = driverStatus; }
    public String getCccd() { return cccd; }
    public void setCccd(String cccd) { this.cccd = cccd; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getCccdImageUrl() { return cccdImageUrl; }
    public void setCccdImageUrl(String cccdImageUrl) { this.cccdImageUrl = cccdImageUrl; }
    public String getLicenseImageUrl() { return licenseImageUrl; }
    public void setLicenseImageUrl(String licenseImageUrl) { this.licenseImageUrl = licenseImageUrl; }
    public String getDriverCarType() { return driverCarType; }
    public void setDriverCarType(String driverCarType) { this.driverCarType = driverCarType; }
    @PropertyName("isDriver")
    public boolean isDriver() { return isDriver; }
    @PropertyName("isDriver")
    public void setDriver(boolean isDriver) { this.isDriver = isDriver; }
    public boolean isDriverOnline() { return driverOnline; }
    public void setDriverOnline(boolean driverOnline) { this.driverOnline = driverOnline; }
    public Timestamp getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Timestamp appliedAt) { this.appliedAt = appliedAt; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
