package com.example.doanmb.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.doanmb.data.model.AiChatMessage;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.ThinkingConfig;
import com.google.firebase.ai.type.ThinkingLevel;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AiAssistantRepository {

    private static final String MODEL_NAME = "gemini-3.6-flash";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final String SELLING_POLICY =
            "Chính sách đăng bán xe CarVIA: tài khoản phải xác thực; người đăng là chủ xe " +
            "hoặc được ủy quyền; thông tin và ít nhất 3 ảnh phải đúng thực tế. Đăng tin miễn phí. " +
            "Khi người mua gửi yêu cầu, hệ thống giữ cọc 50% giá xe từ ví người mua. Nếu người bán " +
            "từ chối hoặc không giao dịch thì hoàn 100% cọc. Người bán chịu trách nhiệm về giấy tờ " +
            "và tình trạng xe; người mua phải kiểm tra xe, giấy tờ trực tiếp trước khi thanh toán.";

    public interface OnAiReply {
        void onSuccess(String reply, List<AiChatMessage.AiSuggestedCar> cars);
        void onError(String message);
    }

    private AiAssistantRepository() {}

    public static void ask(@NonNull String message,
                           @NonNull List<AiChatMessage> history,
                           @NonNull OnAiReply callback) {
        FirebaseFirestore.getInstance()
                .collection("cars")
                .limit(80)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<CarContext> cars = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        cars.add(CarContext.from(document));
                    }
                    requestAi(message, history, cars, callback);
                })
                .addOnFailureListener(error -> requestAi(
                        message, history, new ArrayList<>(), callback));
    }

    private static void requestAi(String message,
                                  List<AiChatMessage> history,
                                  List<CarContext> cars,
                                  OnAiReply callback) {
        GenerationConfig generationConfig = new GenerationConfig.Builder()
                .setResponseMimeType("application/json")
                .setTemperature(0.35f)
                .setMaxOutputTokens(1200)
                .setThinkingConfig(new ThinkingConfig.Builder()
                        .setThinkingLevel(ThinkingLevel.LOW)
                        .build())
                .build();
        GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel(MODEL_NAME, generationConfig);
        GenerativeModelFutures model = GenerativeModelFutures.from(ai);
        Content prompt = new Content.Builder().addText(buildPrompt(message, history, cars)).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    ParsedReply parsed = parseReply(result.getText(), cars);
                    MAIN_HANDLER.post(() -> callback.onSuccess(parsed.reply, parsed.cars));
                } catch (Exception error) {
                    postError(callback, "Trợ lý AI trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
                }
            }

            @Override
            public void onFailure(@NonNull Throwable error) {
                android.util.Log.e("AiAssistant", "Firebase AI request failed", error);
                String detail = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
                String message;
                if (detail.contains("app check") || detail.contains("appcheck")) {
                    message = "Thiết bị chưa được xác thực App Check. Hãy đăng ký debug token trong Firebase Console.";
                } else if (detail.contains("permission") || detail.contains("403")
                        || detail.contains("api has not been used") || detail.contains("disabled")) {
                    message = "Firebase AI Logic chưa được bật cho dự án. Hãy bật Gemini Developer API trong Firebase Console.";
                } else if (detail.contains("network") || detail.contains("unavailable")
                        || detail.contains("unable to resolve host")) {
                    message = "Không có kết nối mạng. Vui lòng kiểm tra Internet rồi thử lại.";
                } else {
                    message = "Không kết nối được với trợ lý AI. Vui lòng thử lại.";
                }
                postError(callback, message);
            }
        }, MoreExecutors.directExecutor());
    }

    private static String buildPrompt(String message,
                                      List<AiChatMessage> history,
                                      List<CarContext> cars) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là Trợ lý CarVIA, tư vấn mua, thuê và đăng bán ô tô bằng tiếng Việt.\n")
                .append("Trả lời ngắn gọn, thân thiện, không bịa thông tin xe. ")
                .append("Chỉ chọn id trong danh sách xe. Nếu không phù hợp, để carIds rỗng.\n")
                .append("Nếu hỏi về bán/đăng xe, phải dựa trên chính sách sau: ")
                .append(SELLING_POLICY).append("\n")
                .append("Chỉ trả về JSON đúng schema: {\"reply\":\"...\",\"carIds\":[\"id\"]}.\n")
                .append("Danh sách xe: ").append(carsToJson(cars)).append("\n")
                .append("Lịch sử hội thoại:\n");

        int start = Math.max(0, history.size() - 8);
        for (int index = start; index < history.size(); index++) {
            AiChatMessage item = history.get(index);
            if (item.getRole() == AiChatMessage.ROLE_USER) {
                prompt.append("Người dùng: ").append(item.getText()).append("\n");
            } else if (item.getRole() == AiChatMessage.ROLE_AI) {
                prompt.append("Trợ lý: ").append(item.getText()).append("\n");
            }
        }
        prompt.append("Câu hỏi mới: ").append(message);
        return prompt.toString();
    }

    private static JSONArray carsToJson(List<CarContext> cars) {
        JSONArray array = new JSONArray();
        for (CarContext car : cars) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", car.id);
                object.put("name", car.name);
                object.put("brand", car.brand);
                object.put("type", car.type);
                object.put("price", car.price);
                object.put("info", car.info);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        return array;
    }

    private static ParsedReply parseReply(String rawText, List<CarContext> cars) throws Exception {
        String raw = rawText == null ? "" : rawText.trim();
        raw = raw.replace("```json", "").replace("```", "").trim();
        int objectStart = raw.indexOf('{');
        int objectEnd = raw.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            raw = raw.substring(objectStart, objectEnd + 1);
        }
        JSONObject object = new JSONObject(raw);
        String reply = object.optString("reply", "").trim();
        if (reply.isEmpty()) throw new IllegalArgumentException("Missing reply");

        Map<String, CarContext> carsById = new HashMap<>();
        for (CarContext car : cars) carsById.put(car.id, car);
        List<AiChatMessage.AiSuggestedCar> suggestions = new ArrayList<>();
        JSONArray ids = object.optJSONArray("carIds");
        if (ids != null) {
            for (int index = 0; index < ids.length() && suggestions.size() < 5; index++) {
                CarContext car = carsById.get(ids.optString(index));
                if (car != null) suggestions.add(car.toSuggestedCar());
            }
        }
        return new ParsedReply(reply, suggestions);
    }

    private static void postError(OnAiReply callback, String message) {
        MAIN_HANDLER.post(() -> callback.onError(message));
    }

    private static String value(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value == null ? "" : String.valueOf(value);
    }

    private static final class CarContext {
        final String id;
        final String name;
        final String brand;
        final String type;
        final String price;
        final String info;

        CarContext(String id, String name, String brand, String type, String price, String info) {
            this.id = id;
            this.name = name;
            this.brand = brand;
            this.type = type;
            this.price = price;
            this.info = info;
        }

        static CarContext from(DocumentSnapshot document) {
            String info = value(document, "info");
            if (info.length() > 120) info = info.substring(0, 120);
            return new CarContext(document.getId(), value(document, "name"),
                    value(document, "brand"), value(document, "type"),
                    value(document, "price"), info);
        }

        AiChatMessage.AiSuggestedCar toSuggestedCar() {
            return new AiChatMessage.AiSuggestedCar(id, name, brand, type, price);
        }
    }

    private static final class ParsedReply {
        final String reply;
        final List<AiChatMessage.AiSuggestedCar> cars;

        ParsedReply(String reply, List<AiChatMessage.AiSuggestedCar> cars) {
            this.reply = reply;
            this.cars = cars;
        }
    }
}
