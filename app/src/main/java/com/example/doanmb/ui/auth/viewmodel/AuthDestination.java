package com.example.doanmb.ui.auth.viewmodel;

/**
 * Đích điều hướng sau khi xác thực, quyết định theo vai trò người dùng:
 * ADMIN → màn quản trị, DRIVER → dashboard tài xế, MAIN → màn chính (khách hàng).
 */
public enum AuthDestination { ADMIN, DRIVER, MAIN }