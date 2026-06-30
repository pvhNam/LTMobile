# CarVIA — Ứng dụng thuê xe & tài xế

> Đồ án môn **Lập trình thiết bị di động** — Nhóm 18, Khoa Công nghệ thông tin, Trường Đại học Nông Lâm TP.HCM.
>
> CarVIA là một **sàn (marketplace) trên Android** kết nối nhu cầu **thuê xe tự lái** và **thuê xe có tài xế**, gồm ba vai trò: **Khách hàng – Tài xế – Quản trị viên**.

| Hạng mục | Thông tin |
|---|---|
| Nền tảng | Android (Java) |
| `applicationId` | `com.example.doanmb` |
| compileSdk / targetSdk / minSdk | 36 / 35 / 29 (Android 10+) |
| Kiến trúc | MVVM + Repository + ViewBinding/LiveData |
| Backend | Firebase (Auth, Firestore, Storage, Cloud Messaging) + Cloud Functions |
| Dịch vụ ngoài | Cloudinary (ảnh), Google Maps/Location, VNPay (sandbox) |

---

## Mục lục

- [1. Tính năng chính](#1-tính-năng-chính)
- [2. Kiến trúc hệ thống](#2-kiến-trúc-hệ-thống)
- [3. Công nghệ & thư viện](#3-công-nghệ--thư-viện)
- [4. Cấu trúc thư mục](#4-cấu-trúc-thư-mục)
- [5. Cơ sở dữ liệu (Firestore)](#5-cơ-sở-dữ-liệu-firestore)
- [6. Luồng nghiệp vụ cốt lõi](#6-luồng-nghiệp-vụ-cốt-lõi)
- [7. Ví nội bộ & thanh toán VNPay](#7-ví-nội-bộ--thanh-toán-vnpay)
- [8. Cài đặt & chạy dự án](#8-cài-đặt--chạy-dự-án)
- [9. Kiểm thử](#9-kiểm-thử)
- [10. Hạn chế & hướng phát triển](#10-hạn-chế--hướng-phát-triển)
- [11. Phân công nhóm](#11-phân-công-nhóm)
- [12. Tài liệu & sơ đồ](#12-tài-liệu--sơ-đồ)

---

## 1. Tính năng chính

### Khách hàng (Customer)
- Đăng ký / đăng nhập / quên mật khẩu (Firebase Authentication).
- Tìm và xem **danh mục xe** cho thuê/bán theo loại, xem chi tiết kèm nhiều ảnh.
- **Gửi yêu cầu thuê** theo **ngày** hoặc theo **chuyến** (chọn điểm đón – đến trên Google Maps để ước tính quãng đường).
- **Đăng tin** cho thuê / bán xe (ảnh lưu trên Cloudinary).
- **Quản lý & duyệt yêu cầu** đến bài đăng của mình (xác nhận / từ chối).
- **Chat thời gian thực** (văn bản, ảnh, video) và gọi điện cho chủ xe.
- **Xe yêu thích**, **đánh giá** xe/tài xế.
- **Ví nội bộ**: nạp tiền qua **VNPay**, xem số dư – lịch sử giao dịch, rút tiền.
- Đăng ký trở thành tài xế.

### Tài xế (Driver)
- Bật/tắt nhận chuyến, **đăng bài cho thuê** (xe có tài xế / lái thuê).
- **Nhận / bỏ qua** chuyến; **bắt đầu / hoàn thành** chuyến.
- Xem **bản đồ nhu cầu**, xem **thu nhập / ví tài xế**, rút tiền.
- Chuyển nhanh về chế độ khách hàng.

### Quản trị viên (Admin)
- Tổng quan & **thống kê doanh thu** (biểu đồ theo ngày/tháng/năm).
- **Duyệt / từ chối** đăng ký tài xế.
- Quản lý **bài đăng xe**, **đơn hàng**, **người dùng** (nạp ví, đổi vai trò).
- Xử lý **khiếu nại / báo cáo**.

---

## 2. Kiến trúc hệ thống

Ứng dụng theo mô hình **MVVM + Repository**, tách 3 tầng rõ ràng:

```
┌──────────────────────────────────────────────────────────────┐
│  UI Layer  (ui/…)                                             │
│  Activity / Fragment + Adapter  ⇄  ViewModel (LiveData)       │
├──────────────────────────────────────────────────────────────┤
│  Domain / Service  (core/service, core/helper, core/util)     │
│  OrderReminderService · CarviaMessagingService (FCM)          │
│  ChatNotificationHelper · CloudinaryHelper · VnpayHelper      │
│  ImageLoader · EdgeToEdgeUtil                                 │
├──────────────────────────────────────────────────────────────┤
│  Data Layer  (data/…)                                         │
│  Repository (Wallet, Car, Order, Favorite)                   │
│  Model (User, Car, Trip, Order, ChatMessage, Transaction…)   │
│  Remote (VietnamLocationApi – Retrofit)                       │
└──────────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┴────────────────┐
          ▼                                 ▼
  Firebase (Auth, Firestore,        Dịch vụ ngoài
  Storage, Messaging) +             (Cloudinary, Google Maps,
  Cloud Functions                    VNPay sandbox)
```

- **View** chỉ hiển thị và quan sát (`observe`) LiveData; **không** gọi Firestore trực tiếp.
- **ViewModel** giữ trạng thái màn hình và điều phối nghiệp vụ.
- **Repository** gom toàn bộ truy cập dữ liệu (Firestore) về một nơi để dễ kiểm soát và đối soát.

> Sơ đồ chi tiết: [`docs/Architecture_Layers.png`](docs/Architecture_Layers.png), [`docs/Class_Diagram.png`](docs/Class_Diagram.png).

---

## 3. Công nghệ & thư viện

| Hạng mục | Công nghệ / Thư viện | Phiên bản |
|---|---|---|
| Ngôn ngữ | Java | 11 |
| Backend | Firebase BoM (Auth, Firestore, Storage, Messaging) | 34.13.0 |
| Cloud Functions | Node.js (firebase-functions v2) | — |
| Lưu ảnh | Cloudinary Android SDK | 2.5.0 |
| Tải ảnh | Glide (qua `util/ImageLoader`, cache đĩa) | 4.16.0 |
| Bản đồ / định vị | Google Maps SDK · Play Services Location | 19.0.0 · 21.3.0 |
| Biểu đồ | MPAndroidChart | 3.1.0 |
| Video | ExoPlayer (+ HLS, OkHttp) | 2.19.1 |
| Xem ảnh | PhotoView | 2.3.0 |
| Hiệu ứng nền | BlurView (Liquid Glass / BottomNav) | 2.0.6 |
| Gọi API | Retrofit + Gson + OkHttp | 2.11.0 |
| Kiến trúc | AndroidX Lifecycle (ViewModel/LiveData) | 2.8.7 |
| Giao diện | Material Components · CircleImageView · SwipeRefreshLayout | 1.12.0 |
| Thanh toán | VNPay (sandbox) + WebView + HmacSHA512 | API 2.1.0 |

---

## 4. Cấu trúc thư mục

```
LTMobile/
├── app/
│   └── src/main/
│       ├── java/com/example/doanmb/
│       │   ├── core/            # helper, service, util dùng chung
│       │   │   ├── helper/      # VnpayHelper, CloudinaryHelper, ChatNotificationHelper
│       │   │   ├── service/     # CarviaMessagingService (FCM), OrderReminderService
│       │   │   └── util/        # ImageLoader, EdgeToEdgeUtil
│       │   ├── data/            # tầng dữ liệu
│       │   │   ├── model/       # User, Car, Order, Trip, ChatMessage, Transaction…
│       │   │   ├── remote/      # VietnamLocationApi (Retrofit)
│       │   │   └── repository/  # WalletRepository, CarRepository, OrderRepository…
│       │   └── ui/              # tầng giao diện (MVVM)
│       │       ├── auth/        # đăng nhập, đăng ký, splash
│       │       ├── home/        # MainActivity, danh mục, banner
│       │       ├── car/         # chi tiết & đặt thuê, quản lý yêu cầu, hóa đơn
│       │       ├── chat/        # nhắn tin thời gian thực
│       │       ├── driver/      # module tài xế + bản đồ
│       │       ├── admin/       # bảng điều khiển quản trị
│       │       ├── profile/     # hồ sơ, ví, giao dịch
│       │       └── media/       # xem ảnh/video toàn màn hình
│       ├── res/                 # màn auth + tài nguyên dùng chung
│       ├── res-customer/        # layout của khách (home, thuê/mua, ví, chat, profile)
│       ├── res-driver/          # layout của tài xế
│       └── res-admin/           # layout của admin
├── functions/                   # Cloud Functions (FCM push qua hàng đợi fcm_queue)
├── docs/                        # sơ đồ UseCase / Architecture / Sequence / Class / ERD
└── build.gradle.kts
```

> **Lưu ý tổ chức layout:** layout được tách theo vai trò vào `res-customer` / `res-driver` / `res-admin` (cấu hình trong `app/build.gradle.kts → sourceSets`). Tất cả vẫn dùng chung **một** namespace `R` nên không phải sửa `R.layout` / `findViewById`.

---

## 5. Cơ sở dữ liệu (Firestore)

| Collection | Vai trò |
|---|---|
| `users` | Hồ sơ người dùng, vai trò, trạng thái tài xế, **`balance`** (ví) |
| `cars` | Bài đăng xe cho thuê/bán (`sellerId`, `status`…) |
| `orders` | Đơn thuê xe (`buyerId`, `sellerId`, `carId`, `totalAmount`, `depositAmount`, `status`, `invoiceStatus`, `lateDays`) |
| `trips` | Chuyến đi của tài xế (`customerId`, `driverId`, `rentMode`, `status`) |
| `chat_rooms` / `messages` | Phòng chat & tin nhắn (văn bản/ảnh/video, trạng thái gửi-đã đọc, thu hồi) |
| `transactions` | Nhật ký mọi lần dịch chuyển tiền để admin đối soát |
| `app_wallet` | Ví hệ thống (`main`) — cộng dồn **hoa hồng 15%** |
| `favorites` | Xe yêu thích của người dùng |
| `fcm_queue` | Hàng đợi để Cloud Function đẩy thông báo FCM |

> ERD đầy đủ: [`docs/ERD_Carvia.png`](docs/ERD_Carvia.png).

---

## 6. Luồng nghiệp vụ cốt lõi

**Gửi yêu cầu thuê → chủ xe/tài xế xác nhận → admin theo dõi:**

1. Khách mở **CarDetailActivity**, nhập thông tin thuê (theo ngày / theo chuyến) → hệ thống kiểm tra hợp lệ, tính tổng tiền và tạo document `orders` ở trạng thái `pending`.
2. Với đơn cần cọc (thuê ≥ 2 ngày) → **giữ cọc 50%** khỏi ví khách (xem mục 7), đồng thời lên lịch nhắc (`OrderReminderService`) và gửi thông báo FCM cho chủ xe.
3. **ManageFragment** lắng nghe `orders` theo thời gian thực (`addSnapshotListener`); chủ xe/tài xế **xác nhận** (`status=confirmed`, `cars.status=sold`) hoặc **từ chối** (`status=rejected`, hoàn cọc, `cars.status=active`).
4. Khi **trả xe**: lập **hóa đơn** (tiền thuê + phạt trễ nếu có) → thanh toán **85/15** về chủ xe (xem mục 7).
5. **Admin** theo dõi và cập nhật trạng thái đơn (`completed` / `cancelled`), từ đó kích hoạt chia tiền / hoàn cọc.

> Sequence chi tiết: [`docs/Sequence_DatThueXe.png`](docs/Sequence_DatThueXe.png) · Use Case: [`docs/usecase_customer_driver_admin.png`](docs/usecase_customer_driver_admin.png).

---

## 7. Ví nội bộ & thanh toán VNPay

Toàn bộ thao tác tiền được gom vào **`WalletRepository`** và chạy trong **Firestore transaction** để bảo đảm nhất quán. Mỗi lần tiền dịch chuyển đều ghi một document `transactions`.

**Quy tắc chia tiền**

| Tham số | Giá trị |
|---|---|
| Tỷ lệ đặt cọc (`DEPOSIT_RATE`) | **50%** tổng đơn |
| Hoa hồng nền tảng (`COMMISSION_RATE`) | **15%** |
| Phần trả chủ xe/tài xế | **85%** |
| Bắt buộc cọc khi thuê | từ **2 ngày** trở lên |
| Huỷ trước khi hoàn thành | hoàn **100%** cọc |

**Nạp ví qua VNPay (sandbox):** `VnpayHelper` sinh URL thanh toán đã ký **HMAC-SHA512** → mở trong `VnpayPaymentActivity` (WebView) → bắt URL trả về, **xác minh chữ ký** và mã `vnp_ResponseCode = "00"` → cộng tiền vào ví bằng `WalletRepository.userTopUp()`.

> ⚠️ Trong phạm vi đồ án, việc ký giao dịch và cộng ví được xử lý **phía client (sandbox)**. Sản phẩm thật cần chuyển phần ký/đối soát sang backend (Cloud Functions). Xem chi tiết phần này tại **[`docs/PHAN_NAM.md`](docs/PHAN_NAM.md)**.

---

## 8. Cài đặt & chạy dự án

### Yêu cầu
- Android Studio (Giraffe trở lên), JDK 11.
- Tài khoản Firebase + một project Firebase.
- (Tuỳ chọn) Node.js để deploy Cloud Functions.

### Các bước

1. **Clone & mở** dự án bằng Android Studio.

2. **Firebase:** tải `google-services.json` của project Firebase và đặt vào thư mục `app/`. Bật **Authentication (Email/Password)**, **Cloud Firestore**, **Storage**, **Cloud Messaging**.

3. **Google Maps key:** thêm vào `local.properties` (file này **không** commit lên git):
   ```properties
   MAPS_API_KEY=AIza...your_key...
   ```

4. **Build & chạy:**
   ```bash
   ./gradlew assembleDebug      # build APK debug
   # hoặc nhấn Run ▶ trong Android Studio
   ```

5. **Cloud Functions (tuỳ chọn — để nhận thông báo đẩy khi tắt app):**
   ```bash
   cd functions
   npm install
   firebase deploy --only functions
   ```

> **Cấu hình VNPay sandbox** nằm trong `core/helper/VnpayHelper.java` (`TMN_CODE`, `HASH_SECRET`). Đây là khóa **sandbox dùng cho demo**; khi triển khai thật phải thay bằng khóa thật và đưa việc ký giao dịch về phía backend.

---

## 9. Kiểm thử

Nhóm áp dụng **kiểm thử thủ công theo kịch bản (hộp đen)** trên Android Emulator và thiết bị thật, đối chiếu kết quả thực tế với kỳ vọng và kiểm tra dữ liệu trên Firestore Console. Các ca cốt lõi (đăng ký/đăng nhập, đăng tin, thuê theo ngày/chuyến, xác nhận/từ chối, chat, thông báo đẩy, duyệt tài xế, cập nhật đơn) đều **đạt**. Một số ca biên (mất mạng giữa chừng, tải lớn đồng thời) chưa được kiểm thử đầy đủ.

---

## 10. Hạn chế & hướng phát triển

**Hạn chế**
- Việc chuyển toàn bộ mã nguồn sang MVVM **chưa hoàn tất**; vài màn còn xử lý logic trong Activity/Fragment.
- VNPay chạy **sandbox**, chữ ký giao dịch xử lý **phía client**.
- Định vị tài xế thời gian thực mới ở mức cơ bản.

**Hướng phát triển**
- Kết nối VNPay/thẻ ngân hàng ở môi trường **production**; chuyển ký & xác thực giao dịch sang **Cloud Functions**.
- Đối soát tự động, rút tiền thật về tài khoản ngân hàng; bổ sung eKYC & hợp đồng điện tử.
- Định vị tài xế thời gian thực + gợi ý chuyến theo vị trí.
- Hoàn tất chuyển toàn bộ sang MVVM + ViewModel.

---

## 11. Phân công nhóm

Nhóm 18 — Lớp Lập trình thiết bị di động. Bảng tổng hợp từ lịch sử commit Git (không tính merge):

| MSSV | Họ và tên | Nội dung phụ trách |
|---|---|---|
| 23130200 | **Phạm Văn Hoài Nam** | **Nhóm trưởng.** Hệ thiết kế Liquid Glass; luồng thuê xe cốt lõi (MainActivity, CarDetailActivity, ManageFragment); đăng tin bán xe; **ví nội bộ & VNPay, hóa đơn**; hồ sơ, yêu thích, đánh giá. → [`docs/PHAN_NAM.md`](docs/PHAN_NAM.md) |
| 23130307 | Nguyễn Quang Thành | Xác thực & tài khoản; đăng tin cho thuê & danh mục; module tài xế và bản đồ (thuê theo chuyến); tái cấu trúc tầng dữ liệu. |
| 23130152 | Ngô Quang Khánh | Chat thời gian thực; thông báo đẩy FCM; tải & lưu ảnh (Cloudinary, ImageLoader). |
| 23130125 | Trần Xuân Hùng | Module quản trị: bảng điều khiển, thống kê, quản lý người dùng/xe/đơn, duyệt tài xế. |
| 23130365 | Trần Nguyễn Thanh Tú | Màn hình hồ sơ cá nhân và các màn phụ trợ (thông báo, quà tặng, giới thiệu, hỗ trợ, chính sách). |

---

## 12. Tài liệu & sơ đồ

Thư mục [`docs/`](docs/) chứa nguồn PlantUML (`.puml`) và ảnh xuất (`.png`):

| Sơ đồ | File |
|---|---|
| Use Case tổng | `usecase_customer_driver_admin.png` |
| Kiến trúc phân tầng | `Architecture_Layers.png` |
| Sequence — gửi yêu cầu thuê | `Sequence_DatThueXe.png` |
| Lược đồ lớp | `Class_Diagram.png` |
| ERD (Firestore) | `ERD_Carvia.png` |

Báo cáo đầy đủ: `BaoCao_Nhom18_Carvia_ThueXe.docx`. Phần đóng góp chi tiết của nhóm trưởng: [`docs/PHAN_NAM.md`](docs/PHAN_NAM.md).

---

<sub>Đồ án học tập — Trường Đại học Nông Lâm TP.HCM, 2026. Mọi khóa VNPay trong mã nguồn là khóa sandbox dùng cho mục đích học tập.</sub>
