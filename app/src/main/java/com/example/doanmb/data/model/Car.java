package com.example.doanmb.data.model;
import java.io.Serializable;
public class Car implements Serializable {
    private String id;
    private String name;
    private String price;
    private String info;
    private String type;
    private String brand;
    private String sellerId;
    private String imageUrl;
    private int imageResId;

    public Car(String name, String price, String info, int imageResId) {
        this.name = name;
        this.price = price;
        this.info = info;
        this.brand = "";
        this.imageUrl = "";
        this.imageResId = imageResId;
    }

    public Car(String name, String price, String info, String type, int imageResId) {
        this(name, price, info, type, "", imageResId);
    }

    public Car(String name, String price, String info, String type, String brand, int imageResId) {
        this.name = name;
        this.price = price;
        this.info = info;
        this.type = type;
        this.brand = brand;
        this.imageUrl = "";
        this.imageResId = imageResId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPrice() { return price; }
    public String getInfo() { return info; }
    public String getType() { return type; }
    public String getBrand() { return brand; }
    public String getSellerId() { return sellerId; }
    public String getImageUrl() { return imageUrl; }
    public int getImageResId() { return imageResId; }

    public void setId(String id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}