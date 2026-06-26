package com.example.doanmb.data.model;

import com.google.firebase.Timestamp;

/**
 * Một dòng giao dịch ví, ánh xạ từ document trong collection "transactions".
 *
 * Quy ước hướng tiền theo người dùng đang xem:
 *  - toUserId   == uid  -> tiền vào ví (nạp tiền, hoàn cọc, nhận thanh toán)  => dấu (+)
 *  - fromUserId == uid  -> tiền ra khỏi ví (giữ cọc)                          => dấu (-)
 */
public class Transaction {

    private String type;        // topup | hold | payout | commission | refund
    private long   amount;      // VNĐ
    private String fromUserId;
    private String toUserId;
    private String orderId;
    private String note;
    private Timestamp createdAt;

    public Transaction() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /** true nếu giao dịch làm tăng số dư của user có uid truyền vào. */
    public boolean isIncoming(String uid) {
        return uid != null && uid.equals(toUserId);
    }
}
