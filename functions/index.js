/**
 * Firebase Cloud Functions – Carvia Push Notification
 *
 * Khi ChatNotificationHelper thêm doc vào "fcm_queue",
 * function này tự động trigger và gọi FCM API để đẩy
 * heads-up notification đến điện thoại người nhận
 * (kể cả khi app đang tắt hoặc đang dùng điện thoại mà không mở app).
 *
 * Setup:
 *   1. cd functions && npm install
 *   2. firebase deploy --only functions
 */

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp }      = require("firebase-admin/app");
const { getMessaging }       = require("firebase-admin/messaging");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const crypto      = require("crypto");
const querystring = require("querystring");

initializeApp();

/**
 * Trigger: mỗi khi có doc mới trong collection "fcm_queue"
 * Doc có dạng:
 * {
 *   token:      "FCM_TOKEN_CUA_NGUOI_NHAN",
 *   title:      "Tin nhắn từ Nguyễn Văn A",
 *   body:       "Nguyễn Văn A muốn mua xe Toyota Camry",
 *   senderName: "Nguyễn Văn A",
 *   carName:    "Toyota Camry",
 *   carType:    "sale" | "rental",
 *   roomId:     "ROOM_ID",
 *   senderId:   "SENDER_UID",
 *   createdAt:  Timestamp,
 *   sent:       false   ← Cloud Function set true sau khi gửi
 * }
 */
exports.sendChatPushNotification = onDocumentCreated(
  "fcm_queue/{docId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const data  = snap.data();
    const docId = event.params.docId;

    // Bỏ qua nếu đã gửi rồi (tránh gửi 2 lần)
    if (data.sent === true) return;

    const token = data.token;
    if (!token) {
      console.warn(`[${docId}] Không có FCM token, bỏ qua.`);
      await snap.ref.update({ sent: true, error: "no_token" });
      return;
    }

    // ── Tạo payload FCM ──────────────────────────────────────────────────────
    // Dùng "data" message (không dùng "notification" field) để
    // CarviaMessagingService xử lý và hiển thị heads-up đúng cách.
    const message = {
      token: token,
      data: {
        title:      data.title      || "Tin nhắn mới",
        body:       data.body       || "Bạn có tin nhắn mới",
        senderName: data.senderName || "",
        carName:    data.carName    || "",
        carType:    data.carType    || "sale",
        roomId:     data.roomId     || "",
        senderId:   data.senderId   || "",
      },
      android: {
        priority: "high",   // Đảm bảo heads-up hoạt động kể cả Doze mode
      },
      apns: {               // iOS (nếu sau này mở rộng)
        headers: {
          "apns-priority": "10",
        },
      },
    };

    // ── Gửi FCM ──────────────────────────────────────────────────────────────
    try {
      const response = await getMessaging().send(message);
      console.log(`[${docId}] FCM gửi thành công:`, response);

      // Đánh dấu đã gửi
      await snap.ref.update({ sent: true, sentAt: new Date() });
    } catch (err) {
      console.error(`[${docId}] FCM gửi thất bại:`, err.message);

      // Token hết hạn → xoá khỏi user document
      if (
        err.code === "messaging/registration-token-not-registered" ||
        err.code === "messaging/invalid-registration-token"
      ) {
        await cleanupInvalidToken(data.token);
      }

      await snap.ref.update({ sent: true, error: err.message });
    }
  }
);

/**
 * Xoá FCM token hết hạn khỏi Firestore để tránh spam lần sau.
 */
async function cleanupInvalidToken(token) {
  try {
    const db      = getFirestore();
    const usersRef = db.collection("users");
    const snapshot = await usersRef.where("fcmToken", "==", token).get();
    for (const doc of snapshot.docs) {
      await doc.ref.update({ fcmToken: "" });
      console.log(`Đã xoá token hết hạn của user: ${doc.id}`);
    }
  } catch (e) {
    console.error("cleanupInvalidToken error:", e.message);
  }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  NẠP VÍ QUA VNPAY (SANDBOX)
 *
 *  Luồng:
 *   1. App gọi callable createVnpayTopup(amount) → tạo topup_orders/{txnRef}
 *      (status="pending") và trả về payUrl của VNPay.
 *   2. App mở payUrl, người dùng thanh toán bằng thẻ test VNPay.
 *   3. VNPay gọi server-to-server tới vnpayIpn → xác thực chữ ký, cộng tiền
 *      vào ví user và ghi 1 transaction (idempotent).
 *   4. VNPay chuyển trình duyệt về vnpayReturn (chỉ hiển thị kết quả).
 *
 *  ⚠️ Cấu hình: đặt các biến môi trường trong functions/.env (xem .env.example):
 *      VNP_TMN_CODE, VNP_HASH_SECRET, VNP_RETURN_URL
 *  Khai báo IPN URL (…/vnpayIpn) trong trang quản trị merchant sandbox VNPay.
 * ════════════════════════════════════════════════════════════════════════════ */

const VNP_TMN_CODE    = process.env.VNP_TMN_CODE    || "TMNCODE_SANDBOX";
const VNP_HASH_SECRET = process.env.VNP_HASH_SECRET || "HASHSECRET_SANDBOX";
const VNP_PAY_URL     = process.env.VNP_PAY_URL     ||
  "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
const VNP_RETURN_URL  = process.env.VNP_RETURN_URL  ||
  "https://example.com/vnpayReturn"; // thay bằng URL function vnpayReturn sau khi deploy

// Sắp xếp + encode tham số đúng đặc tả VNPay (giống sample Node.js chính thức).
function sortObject(obj) {
  const sorted = {};
  const keys = Object.keys(obj).map((k) => encodeURIComponent(k)).sort();
  for (const key of keys) {
    sorted[key] = encodeURIComponent(obj[key]).replace(/%20/g, "+");
  }
  return sorted;
}

function signParams(params) {
  const sorted   = sortObject(params);
  const signData = querystring.stringify(sorted, { encode: false });
  const hmac     = crypto.createHmac("sha512", VNP_HASH_SECRET);
  return { sorted, hash: hmac.update(Buffer.from(signData, "utf-8")).digest("hex") };
}

// yyyyMMddHHmmss theo giờ Việt Nam (GMT+7)
function vnpDate(d) {
  const t = new Date(d.getTime() + 7 * 3600 * 1000);
  return t.toISOString().slice(0, 19).replace(/[-T:]/g, "");
}

exports.createVnpayTopup = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Bạn cần đăng nhập");

  const amount = Math.round(Number(request.data && request.data.amount));
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new HttpsError("invalid-argument", "Số tiền không hợp lệ");
  }

  const db     = getFirestore();
  const txnRef = vnpDate(new Date()) + "_" + Math.floor(Math.random() * 1e6);

  await db.collection("topup_orders").doc(txnRef).set({
    userId: uid,
    amount: amount,
    status: "pending",
    createdAt: FieldValue.serverTimestamp(),
  });

  const ipAddr = (request.rawRequest && (request.rawRequest.ip ||
    request.rawRequest.headers["x-forwarded-for"])) || "127.0.0.1";

  const vnpParams = {
    vnp_Version:   "2.1.0",
    vnp_Command:   "pay",
    vnp_TmnCode:   VNP_TMN_CODE,
    vnp_Amount:    amount * 100,            // VNPay tính theo đơn vị xu
    vnp_CurrCode:  "VND",
    vnp_TxnRef:    txnRef,
    vnp_OrderInfo: "Nap vi Carvia " + txnRef,
    vnp_OrderType: "other",
    vnp_Locale:    "vn",
    vnp_ReturnUrl: VNP_RETURN_URL,
    vnp_IpAddr:    String(ipAddr),
    vnp_CreateDate: vnpDate(new Date()),
  };

  const { sorted, hash } = signParams(vnpParams);
  sorted["vnp_SecureHash"] = hash;
  const payUrl = VNP_PAY_URL + "?" + querystring.stringify(sorted, { encode: false });

  return { payUrl, txnRef };
});

exports.vnpayIpn = onRequest(async (req, res) => {
  const params = { ...req.query };
  const secureHash = params["vnp_SecureHash"];
  delete params["vnp_SecureHash"];
  delete params["vnp_SecureHashType"];

  const { hash } = signParams(params);
  if (secureHash !== hash) {
    return res.json({ RspCode: "97", Message: "Invalid checksum" });
  }

  const txnRef   = params["vnp_TxnRef"];
  const rspCode  = params["vnp_ResponseCode"];
  const txStatus = params["vnp_TransactionStatus"];
  const amount   = Number(params["vnp_Amount"]) / 100;

  const db  = getFirestore();
  const ref = db.collection("topup_orders").doc(txnRef);

  try {
    const result = await db.runTransaction(async (tr) => {
      const snap = await tr.get(ref);
      if (!snap.exists) return { RspCode: "01", Message: "Order not found" };
      const order = snap.data();
      if (Math.round(order.amount) !== Math.round(amount)) {
        return { RspCode: "04", Message: "Invalid amount" };
      }
      if (order.status === "paid") {
        return { RspCode: "02", Message: "Order already confirmed" };
      }
      if (rspCode === "00" && txStatus === "00") {
        const userRef = db.collection("users").doc(order.userId);
        tr.update(userRef, { balance: FieldValue.increment(order.amount) });
        tr.set(db.collection("transactions").doc(), {
          type: "topup",
          amount: order.amount,
          fromUserId: null,
          toUserId: order.userId,
          orderId: txnRef,
          note: "Nạp tiền qua VNPay",
          createdAt: FieldValue.serverTimestamp(),
        });
        tr.update(ref, { status: "paid", paidAt: FieldValue.serverTimestamp() });
      } else {
        tr.update(ref, { status: "failed", responseCode: rspCode });
      }
      return { RspCode: "00", Message: "Confirm Success" };
    });
    return res.json(result);
  } catch (e) {
    console.error("vnpayIpn error:", e.message);
    return res.json({ RspCode: "99", Message: "Unknown error" });
  }
});

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  TRỢ LÝ AI TÌM/GỢI Ý XE — aiCarAssistant
 * ══════════════════════════════════════════════════════════════════════════
 * Client gọi qua Firebase Functions callable SDK:
 *   httpsCallable("aiCarAssistant").call({ message, history })
 *
 * - Lấy danh sách xe đang có trên hệ thống (Firestore) làm ngữ cảnh (RAG đơn giản).
 * - Gọi Gemini API (Google) để trả lời bằng tiếng Việt + gợi ý carIds phù hợp.
 * - Khi người dùng hỏi về việc ĐĂNG BÁN xe, model được yêu cầu trích dẫn
 *   chính sách bán xe (SELLING_POLICY_VI) để trả lời đúng, không bịa đặt.
 * - API key Gemini được giữ ở server (biến môi trường GEMINI_API_KEY),
 *   không bao giờ lộ ra app.
 */

const GEMINI_API_KEY = process.env.GEMINI_API_KEY || "";
const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-3.6-flash";
const GEMINI_URL =
  `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;

// Tóm tắt chính sách bán xe (bản đầy đủ: docs/CHINH_SACH_BAN_XE.md)
const SELLING_POLICY_VI = `
CHÍNH SÁCH ĐĂNG BÁN XE TRÊN CARVIA (tóm tắt):
1. Điều kiện đăng: tài khoản đã xác thực, là chủ xe hoặc được ủy quyền hợp pháp;
   thông tin/ảnh phải đúng thực tế, tối thiểu 3 ảnh thật.
2. Cấm: xe không rõ nguồn gốc/giấy tờ, đang tranh chấp/thế chấp; giá ảo; đăng trùng lặp;
   dẫn giao dịch ra ngoài app để né phí/xác minh.
3. Phí: đăng tin MIỄN PHÍ. Khi có người mua gửi yêu cầu, hệ thống giữ CỌC 50% GIÁ XE
   từ ví nội bộ người mua. Người bán từ chối/không giao dịch → hoàn 100% cọc cho người mua.
   Hiện tại CarVIA KHÔNG thu hoa hồng trên giá trị xe (giai đoạn thử nghiệm).
4. Trách nhiệm: người bán chịu trách nhiệm về thông tin/giấy tờ/tình trạng xe;
   người mua nên kiểm tra xe & giấy tờ trực tiếp trước khi thanh toán phần còn lại;
   CarVIA chỉ kết nối & giữ cọc, không phải một bên trong hợp đồng mua bán.
5. Kiểm duyệt: admin có thể ẩn/gỡ tin vi phạm; người dùng có thể báo cáo tin đăng nghi vấn.
6. Trợ lý AI chỉ gợi ý dựa trên dữ liệu hệ thống, không thay việc kiểm tra xe thực tế,
   và không có quyền xác nhận/giữ chỗ hay đổi trạng thái đơn hàng.
`.trim();

function buildSystemPrompt(carsContext) {
  return `
Bạn là "Trợ lý CarVIA" — trợ lý AI trong ứng dụng CarVIA (sàn thuê/mua bán xe ô tô).
Nhiệm vụ: giúp người dùng (1) TÌM xe phù hợp để MUA hoặc THUÊ, hoặc (2) tư vấn khi người dùng
muốn ĐĂNG BÁN/CHO THUÊ xe của họ.

QUY TẮC BẮT BUỘC:
- Trả lời bằng tiếng Việt, ngắn gọn, thân thiện, đúng trọng tâm.
- CHỈ được gợi ý xe (carIds) lấy từ DANH SÁCH XE HIỆN CÓ bên dưới — tuyệt đối không bịa
  tên xe, giá, hay id không có trong danh sách. Nếu không có xe phù hợp, nói rõ là chưa
  tìm thấy và gợi ý người dùng thử tiêu chí khác.
- Khi câu hỏi liên quan đến việc BÁN hoặc ĐĂNG TIN bán xe, BẮT BUỘC nêu rõ các điểm liên
  quan trong CHÍNH SÁCH BÁN XE bên dưới (đặc biệt: điều kiện đăng, cọc 50%, trách nhiệm
  các bên) trước khi tư vấn thêm.
- Không tư vấn pháp lý chắc chắn tuyệt đối; luôn nhắc người dùng tự kiểm tra giấy tờ xe
  thực tế trước khi giao dịch.
- CHỈ trả lời bằng đúng một object JSON, KHÔNG thêm text/markdown nào khác, theo schema:
  {"reply": "<câu trả lời tiếng Việt>", "carIds": ["<id xe phù hợp, tối đa 5, lấy từ danh sách>"]}
  Nếu không có xe nào phù hợp để gợi ý, để "carIds": [].

${SELLING_POLICY_VI}

DANH SÁCH XE HIỆN CÓ TRÊN HỆ THỐNG (JSON, chỉ dùng để tham chiếu, không đọc lại nguyên văn cho người dùng):
${carsContext}
`.trim();
}

exports.aiCarAssistant = onCall(
  { region: "us-central1", timeoutSeconds: 30, cors: true },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Bạn cần đăng nhập để dùng trợ lý AI.");
    }

    const message = (request.data && request.data.message || "").toString().trim();
    if (!message) {
      throw new HttpsError("invalid-argument", "Thiếu nội dung câu hỏi.");
    }
    if (message.length > 1000) {
      throw new HttpsError("invalid-argument", "Câu hỏi quá dài.");
    }

    const historyRaw = Array.isArray(request.data && request.data.history)
      ? request.data.history.slice(-8)
      : [];

    if (!GEMINI_API_KEY) {
      throw new HttpsError(
        "failed-precondition",
        "Trợ lý AI chưa được cấu hình (thiếu GEMINI_API_KEY trên server)."
      );
    }

    const db = getFirestore();

    // 1) Lấy danh sách xe hiện có (giới hạn để gọn ngữ cảnh gửi cho model)
    let cars = [];
    try {
      const snap = await db.collection("cars").limit(80).get();
      cars = snap.docs.map((d) => {
        const c = d.data() || {};
        return {
          id: d.id,
          name: c.name || "",
          brand: c.brand || "",
          type: c.type || "",
          price: c.price || "",
          info: (c.info || "").toString().slice(0, 120),
        };
      });
    } catch (e) {
      console.error("aiCarAssistant: load cars failed:", e.message);
    }

    const carsContext = JSON.stringify(cars);
    const systemPrompt = buildSystemPrompt(carsContext);

    // 2) Chuẩn bị lịch sử hội thoại cho Gemini (role: user/model)
    const contents = historyRaw
      .filter((h) => h && h.text)
      .map((h) => ({
        role: h.role === "assistant" || h.role === "model" ? "model" : "user",
        parts: [{ text: String(h.text).slice(0, 1500) }],
      }));
    contents.push({ role: "user", parts: [{ text: message }] });

    // 3) Gọi Gemini API
    let aiReplyText = "";
    try {
      const resp = await fetch(`${GEMINI_URL}?key=${GEMINI_API_KEY}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          system_instruction: { parts: [{ text: systemPrompt }] },
          contents,
          generationConfig: {
            responseMimeType: "application/json",
          },
        }),
      });
      const data = await resp.json();
      if (!resp.ok) {
        console.error("Gemini error:", JSON.stringify(data).slice(0, 500));
        throw new HttpsError("internal", "Trợ lý AI đang bận, vui lòng thử lại sau.");
      }
      aiReplyText =
        data &&
        data.candidates &&
        data.candidates[0] &&
        data.candidates[0].content &&
        data.candidates[0].content.parts &&
        data.candidates[0].content.parts[0] &&
        data.candidates[0].content.parts[0].text
          ? data.candidates[0].content.parts[0].text
          : "";
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      console.error("aiCarAssistant: Gemini call failed:", e.message);
      throw new HttpsError("internal", "Không gọi được trợ lý AI, vui lòng thử lại.");
    }

    // 4) Parse JSON trả về từ model (phòng trường hợp model lỡ bọc ```json)
    let reply = "Xin lỗi, tôi chưa hiểu ý bạn. Bạn có thể mô tả rõ hơn xe cần tìm không?";
    let carIds = [];
    try {
      const cleaned = aiReplyText.replace(/```json|```/g, "").trim();
      const parsed = JSON.parse(cleaned);
      if (parsed && typeof parsed.reply === "string") reply = parsed.reply;
      if (parsed && Array.isArray(parsed.carIds)) carIds = parsed.carIds;
    } catch (e) {
      console.warn("aiCarAssistant: cannot parse model JSON, fallback to raw text.");
      if (aiReplyText) reply = aiReplyText.slice(0, 1000);
    }

    // 5) Chỉ giữ lại carIds thật sự tồn tại trong danh sách đã gửi (chống bịa đặt)
    const validIdSet = new Set(cars.map((c) => c.id));
    carIds = carIds.filter((id) => validIdSet.has(id)).slice(0, 5);
    const suggestedCars = cars.filter((c) => carIds.includes(c.id));

    return { reply, cars: suggestedCars };
  }
);

exports.vnpayReturn = onRequest((req, res) => {
  const ok = req.query["vnp_ResponseCode"] === "00";
  const msg = ok
    ? "✅ Thanh toán thành công! Vui lòng quay lại ứng dụng Carvia."
    : "❌ Thanh toán thất bại hoặc đã huỷ. Vui lòng quay lại ứng dụng.";
  res.set("Content-Type", "text/html; charset=utf-8");
  res.send(`<!doctype html><html lang="vi"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Kết quả thanh toán</title></head>
<body style="font-family:sans-serif;text-align:center;padding:48px 24px;color:#1f3a5f">
<h2>${msg}</h2></body></html>`);
});
