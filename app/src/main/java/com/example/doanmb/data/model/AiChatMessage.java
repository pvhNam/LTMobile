package com.example.doanmb.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Một tin nhắn trong đoạn chat với Trợ lý AI (không lưu Firestore — chỉ giữ
 * trong bộ nhớ phiên làm việc hiện tại của màn hình).
 */
public class AiChatMessage implements Serializable {

    public static final int ROLE_USER = 1;
    public static final int ROLE_AI = 2;
    /** Trạng thái "đang gõ..." hiển thị tạm trong lúc chờ server trả lời. */
    public static final int ROLE_AI_TYPING = 3;

    private final int role;
    private final String text;
    private final List<AiSuggestedCar> suggestedCars;
    private final boolean isError;

    public AiChatMessage(int role, String text) {
        this(role, text, new ArrayList<>(), false);
    }

    public AiChatMessage(int role, String text, List<AiSuggestedCar> suggestedCars, boolean isError) {
        this.role = role;
        this.text = text;
        this.suggestedCars = suggestedCars != null ? suggestedCars : new ArrayList<>();
        this.isError = isError;
    }

    public int getRole() { return role; }
    public String getText() { return text; }
    public List<AiSuggestedCar> getSuggestedCars() { return suggestedCars; }
    public boolean isError() { return isError; }
    public boolean hasSuggestions() { return suggestedCars != null && !suggestedCars.isEmpty(); }

    /** Xe được AI gợi ý — đủ dữ liệu để hiển thị mini-card + mở link bài đăng (CarDetailActivity). */
    public static class AiSuggestedCar implements Serializable {
        private final String id;
        private final String name;
        private final String brand;
        private final String type; // "sale" | "rental"
        private final String price;

        public AiSuggestedCar(String id, String name, String brand, String type, String price) {
            this.id = id;
            this.name = name;
            this.brand = brand;
            this.type = type;
            this.price = price;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getBrand() { return brand; }
        public String getType() { return type; }
        public String getPrice() { return price; }
    }
}
