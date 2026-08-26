package com.example.doanmb.ui.chat.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Build;
import android.view.ViewGroup;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.BlurAlgorithm;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.ui.chat.adapter.ChatAdapter;
import com.example.doanmb.ui.chat.adapter.MediaPickerAdapter;
import com.example.doanmb.ui.chat.viewmodel.ChatDetailViewModel;
import com.example.doanmb.data.model.Car;
import com.example.doanmb.data.model.ChatMessage;
import com.example.doanmb.core.helper.ChatNotificationHelper;
import com.example.doanmb.core.helper.CloudinaryHelper;
import com.example.doanmb.ui.media.view.FullscreenMediaActivity;
import com.example.doanmb.ui.car.view.CarDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.example.doanmb.core.service.CarviaMessagingService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatDetailActivity extends AppCompatActivity {

    private String roomId, currentUserId, partnerId, partnerName;
    private Car carData;

    private ChatDetailViewModel viewModel;

    // ── Views cơ bản ──────────────────────────────────────────────────────────
    private RecyclerView rvMessages;
    private ChatAdapter  chatAdapter;
    private EditText     etMessage;
    private ImageButton  btnSend, btnBack, btnAddMedia;
    private View         layoutLoading, rootLayout;
    private ImageView    ivCar;
    private TextView     tvPartnerName, tvCarName, tvCarPrice;
    private TextView     tvBlockedBanner;
    private Button       btnViewPost;

    // ── Search views ──────────────────────────────────────────────────────────
    private ImageButton  btnSearchToggle, btnSearchClose, btnSearchPrev, btnSearchNext;
    private EditText     etSearchMessages;
    private LinearLayout layoutSearchBar, layoutSearchNav;
    private TextView     tvSearchResultInfo;

    // ── Search state ──────────────────────────────────────────────────────────
    private final List<ChatMessage> allMessages       = new ArrayList<>();
    private final List<ChatMessage> firestoreMessages = new ArrayList<>();
    private final List<ChatMessage> tempMessages      = new ArrayList<>();
    private final List<Integer>     searchPositions   = new ArrayList<>();
    private int currentSearchIdx = -1;

    // ── Media picker ──────────────────────────────────────────────────────────
    private LinearLayout layoutMediaPicker;
    private ImageView    tvPickerClose;
    private RecyclerView rvMediaPicker;
    private Button       btnPickerSend;
    private MediaPickerAdapter mediaPickerAdapter;
    private boolean isPickerOpen = false;

    private LinearLayout btnPickerImage, btnPickerVideo;
    private ImageView    ivTabImage, ivTabVideo;
    private View         indicatorImage, indicatorVideo;
    private boolean      isVideoMode = false;

    private final List<MediaPickerAdapter.MediaItem> pendingMedia = new ArrayList<>();

    // ── Media overlay ─────────────────────────────────────────────────────────
    private View layoutMediaOverlay;
    private com.github.chrisbanes.photoview.PhotoView photoViewOverlay;
    private android.widget.VideoView videoViewOverlay;
    private android.widget.ProgressBar progressVideoOverlay;

    // Chuyển tiếp: tin nhắn đang chờ chọn đích.
    private ChatMessage pendingForwardMessage;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = false;
                for (Boolean v : result.values()) if (v) { granted = true; break; }
                if (granted) openMediaPicker(isVideoMode);
                else Toast.makeText(this, "Cần quyền truy cập thư viện!", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_chat_detail);
        EdgeToEdgeUtil.applyHeaderAndScroll(null, findViewById(R.id.layout_header));

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        roomId      = getIntent().getStringExtra("ROOM_ID");
        partnerId   = getIntent().getStringExtra("PARTNER_ID");
        partnerName = getIntent().getStringExtra("PARTNER_NAME");
        carData     = (Car) getIntent().getSerializableExtra("CAR_DATA");

        if (roomId == null || partnerId == null) { finish(); return; }

        viewModel = new ViewModelProvider(this).get(ChatDetailViewModel.class);
        viewModel.init(roomId, currentUserId, partnerId,
                carData != null ? carData.getName() : "",
                carData != null ? carData.getType() : "sale");

        initViews();
        setupChat();
        setupMediaPicker();
        setupSearch();
        observeViewModel();

        refreshFcmToken();
        requestNotificationPermission();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Observe ViewModel
    // ══════════════════════════════════════════════════════════════════════════
    private void observeViewModel() {
        viewModel.getMessages().observe(this, list -> {
            firestoreMessages.clear();
            if (list != null) firestoreMessages.addAll(list);
            rebuildAllMessages(true);
        });

        viewModel.getBlockState().observe(this, state -> updateBlockUI(state));

        viewModel.getMessageEvent().observe(this, msg -> {
            if (msg == null) return;
            if ("__BLOCKED__".equals(msg)) {
                Toast.makeText(this, "Đã chặn " + partnerName, Toast.LENGTH_SHORT).show();
            } else if ("__UNBLOCKED__".equals(msg)) {
                Toast.makeText(this, "Đã bỏ chặn " + partnerName, Toast.LENGTH_SHORT).show();
            } else if (!msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Gửi push điện thoại (cần Context) khi ViewModel báo đã gửi tin xong.
        viewModel.getPushEvent().observe(this, ev -> {
            if (ev == null) return;
            ChatNotificationHelper.sendChatNotification(
                    this, ev.receiverId, ev.senderId, ev.senderName,
                    ev.carName, ev.carType, ev.preview, ev.roomId);
        });

        viewModel.getForwardTargets().observe(this, targets -> {
            if (targets == null || pendingForwardMessage == null) return;
            if (targets.isEmpty()) {
                Toast.makeText(this, "Không có cuộc trò chuyện nào khác", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] labels = new String[targets.size()];
            for (int i = 0; i < targets.size(); i++) labels[i] = targets.get(i).label;
            final ChatMessage source = pendingForwardMessage;
            new AlertDialog.Builder(this)
                    .setTitle("Chuyển tiếp đến...")
                    .setItems(labels, (d, which) -> viewModel.forward(targets.get(which).roomId, source))
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        viewModel.getReportSuccess().observe(this, ok -> {
            if (!Boolean.TRUE.equals(ok)) return;
            new AlertDialog.Builder(this)
                    .setTitle("Đã gửi báo cáo")
                    .setMessage("Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xem xét và xử lý sớm nhất có thể.\n\nNếu ai đó đang gặp nguy hiểm, hãy liên hệ dịch vụ khẩn cấp tại địa phương.")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    /** Gộp tin Firestore + tin tạm (đang upload) thành 1 danh sách hiển thị. */
    private void rebuildAllMessages(boolean scroll) {
        allMessages.clear();
        allMessages.addAll(firestoreMessages);
        allMessages.addAll(tempMessages);
        if (scroll) chatAdapter.submitList(new ArrayList<>(allMessages), this::scrollToBottom);
        else        chatAdapter.submitList(new ArrayList<>(allMessages));

        if (layoutSearchBar != null && layoutSearchBar.getVisibility() == View.VISIBLE
                && etSearchMessages != null) {
            String q = etSearchMessages.getText().toString().trim();
            if (!q.isEmpty()) performSearch(q);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FCM token + notification permission
    // ══════════════════════════════════════════════════════════════════════════
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (!granted)
                    Toast.makeText(this, "Cần bật thông báo để nhận tin nhắn mới", Toast.LENGTH_LONG).show();
            });

    private void refreshFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    android.util.Log.d("ChatDetail", "FCM token: " + token);
                    CarviaMessagingService.saveFcmToken(this, token);
                })
                .addOnFailureListener(e -> android.util.Log.w("ChatDetail", "Lấy FCM token thất bại", e));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private BlurAlgorithm newBlurAlgorithm() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new RenderEffectBlur() : new RenderScriptBlur(this);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Init Views
    // ══════════════════════════════════════════════════════════════════════════
    private void initViews() {
        rootLayout        = findViewById(R.id.root_layout);
        rvMessages        = findViewById(R.id.rv_messages);
        etMessage         = findViewById(R.id.et_message);
        btnSend           = findViewById(R.id.btn_send);
        btnBack           = findViewById(R.id.btn_back);
        btnAddMedia       = findViewById(R.id.btn_add_image);
        layoutLoading     = findViewById(R.id.layout_loading);
        tvPartnerName     = findViewById(R.id.tv_partner_name);
        ivCar             = findViewById(R.id.iv_car);
        tvCarName         = findViewById(R.id.tv_car_name);
        tvCarPrice        = findViewById(R.id.tv_car_price);
        btnViewPost       = findViewById(R.id.btn_view_post);
        tvBlockedBanner   = findViewById(R.id.tv_blocked_banner);
        layoutMediaPicker = findViewById(R.id.layout_media_picker);
        tvPickerClose     = findViewById(R.id.tv_picker_close);
        rvMediaPicker     = findViewById(R.id.rv_media_picker);
        btnPickerSend     = findViewById(R.id.btn_picker_send);

        ViewGroup rootVg = (rootLayout instanceof ViewGroup) ? (ViewGroup) rootLayout : null;
        BlurView blurInput  = findViewById(R.id.blur_input);
        BlurView blurHeader = findViewById(R.id.blur_header);
        if (rootVg != null) {
            if (blurInput != null) {
                blurInput.setupWith(rootVg, newBlurAlgorithm())
                        .setFrameClearDrawable(rootLayout.getBackground())
                        .setBlurRadius(22f);
            }
            if (blurHeader != null) {
                blurHeader.setupWith(rootVg, newBlurAlgorithm())
                        .setFrameClearDrawable(rootLayout.getBackground())
                        .setBlurRadius(22f);
                blurHeader.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                    int h = b - t;
                    if (rvMessages != null && h > 0 && rvMessages.getPaddingTop() != h) {
                        rvMessages.setPadding(rvMessages.getPaddingLeft(), h,
                                rvMessages.getPaddingRight(), rvMessages.getPaddingBottom());
                    }
                });
            }
        }

        btnSearchToggle   = findViewById(R.id.btn_search_toggle);
        btnSearchClose    = findViewById(R.id.btn_search_close);
        btnSearchPrev     = findViewById(R.id.btn_search_prev);
        btnSearchNext     = findViewById(R.id.btn_search_next);
        etSearchMessages  = findViewById(R.id.et_search_messages);
        layoutSearchBar   = findViewById(R.id.layout_search_bar);
        layoutSearchNav   = findViewById(R.id.layout_search_nav);
        tvSearchResultInfo = findViewById(R.id.tv_search_result_info);

        btnPickerImage = findViewById(R.id.btn_picker_image);
        btnPickerVideo = findViewById(R.id.btn_picker_video);
        ivTabImage     = findViewById(R.id.iv_tab_image);
        ivTabVideo     = findViewById(R.id.iv_tab_video);
        indicatorImage = findViewById(R.id.indicator_image);
        indicatorVideo = findViewById(R.id.indicator_video);

        tvPartnerName.setText(partnerName != null ? partnerName : "Người dùng");
        if (carData != null) {
            tvCarName.setText(carData.getName());
            tvCarPrice.setText(carData.getPrice());
            Glide.with(this).load(carData.getImageUrl())
                    .placeholder(R.drawable.ic_buy_car).into(ivCar);
        }

        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> handleSendMessage());

        btnAddMedia.setOnClickListener(v -> {
            if (viewModel.isAnyoneBlocked()) return;
            if (isPickerOpen) closeMediaPicker();
            else { isVideoMode = false; checkPermissionAndOpen(); }
        });

        btnPickerImage.setOnClickListener(v -> {
            if (isVideoMode) { isVideoMode = false; applyTabStyle(); reloadPickerContent(); }
        });
        btnPickerVideo.setOnClickListener(v -> {
            if (!isVideoMode) { isVideoMode = true; applyTabStyle(); reloadPickerContent(); }
        });

        if (tvPickerClose != null) {
            tvPickerClose.setOnClickListener(v -> {
                pendingMedia.clear();
                if (mediaPickerAdapter != null) mediaPickerAdapter.clearSelection();
                updatePickerSendButton();
                updateSendButtonState();
                closeMediaPicker();
            });
        }

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) { updateSendButtonState(); }
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        rootLayout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b < ob) scrollToBottom();
        });

        if (btnViewPost != null) {
            btnViewPost.setOnClickListener(v -> {
                if (carData != null && carData.getId() != null) {
                    Intent intent = new Intent(this, CarDetailActivity.class);
                    intent.putExtra("CAR_DATA", carData);
                    intent.putExtra("CAR_ID", carData.getId());
                    intent.putExtra("SELLER_ID", carData.getSellerId());
                    intent.putExtra("CAR_TYPE", carData.getType());
                    startActivity(intent);
                }
            });
        }

        findViewById(R.id.btn_menu_more).setOnClickListener(this::showPopupMenu);

        layoutMediaOverlay   = findViewById(R.id.layout_media_overlay);
        photoViewOverlay     = findViewById(R.id.photo_view_overlay);
        videoViewOverlay     = findViewById(R.id.video_view_overlay);
        progressVideoOverlay = findViewById(R.id.progress_video_overlay);
        View btnCloseOverlay = findViewById(R.id.btn_close_overlay);
        if (btnCloseOverlay != null) btnCloseOverlay.setOnClickListener(v -> closeMediaOverlay());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Tìm kiếm tin nhắn + tên (thuần UI trên allMessages)
    // ══════════════════════════════════════════════════════════════════════════
    private void setupSearch() {
        btnSearchToggle.setOnClickListener(v -> {
            if (layoutSearchBar.getVisibility() == View.VISIBLE) closeSearch();
            else { layoutSearchBar.setVisibility(View.VISIBLE); etSearchMessages.requestFocus(); }
        });
        btnSearchClose.setOnClickListener(v -> closeSearch());
        etSearchMessages.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) { performSearch(s.toString().trim()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        btnSearchPrev.setOnClickListener(v -> navigateSearch(-1));
        btnSearchNext.setOnClickListener(v -> navigateSearch(+1));
    }

    private void performSearch(String query) {
        searchPositions.clear();
        currentSearchIdx = -1;

        if (query.isEmpty()) {
            layoutSearchNav.setVisibility(View.GONE);
            chatAdapter.setSearchQuery("");
            chatAdapter.notifyDataSetChanged();
            return;
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        boolean nameMatch = partnerName != null
                && partnerName.toLowerCase(Locale.ROOT).contains(lowerQuery);

        for (int i = 0; i < allMessages.size(); i++) {
            ChatMessage msg = allMessages.get(i);
            if (msg.isRecalled()) continue;
            String content = msg.getContent();
            if (content != null && content.toLowerCase(Locale.ROOT).contains(lowerQuery))
                searchPositions.add(i);
        }

        chatAdapter.setSearchQuery(query);
        chatAdapter.notifyDataSetChanged();

        int total = searchPositions.size();
        if (nameMatch && total == 0) {
            layoutSearchNav.setVisibility(View.VISIBLE);
            tvSearchResultInfo.setText("Tên: " + partnerName);
            tvPartnerName.setBackgroundColor(0x55FFFF00);
        } else if (total > 0) {
            tvPartnerName.setBackgroundColor(0x00000000);
            layoutSearchNav.setVisibility(View.VISIBLE);
            currentSearchIdx = total - 1;
            updateSearchNavUI(nameMatch ? total + " tin  |  tên: " + partnerName
                    : String.valueOf(total) + " kết quả");
            scrollToSearchResult(currentSearchIdx);
        } else if (nameMatch) {
            layoutSearchNav.setVisibility(View.VISIBLE);
            tvSearchResultInfo.setText("Tên: " + partnerName);
            tvPartnerName.setBackgroundColor(0x55FFFF00);
        } else {
            layoutSearchNav.setVisibility(View.VISIBLE);
            tvSearchResultInfo.setText("Không tìm thấy");
        }
    }

    private void navigateSearch(int direction) {
        if (searchPositions.isEmpty()) return;
        currentSearchIdx += direction;
        if (currentSearchIdx < 0) currentSearchIdx = searchPositions.size() - 1;
        if (currentSearchIdx >= searchPositions.size()) currentSearchIdx = 0;
        updateSearchNavUI((currentSearchIdx + 1) + " / " + searchPositions.size());
        scrollToSearchResult(currentSearchIdx);
    }

    private void updateSearchNavUI(String text) { tvSearchResultInfo.setText(text); }

    private void scrollToSearchResult(int idx) {
        if (idx < 0 || idx >= searchPositions.size()) return;
        rvMessages.scrollToPosition(searchPositions.get(idx));
    }

    private void closeSearch() {
        layoutSearchBar.setVisibility(View.GONE);
        layoutSearchNav.setVisibility(View.GONE);
        etSearchMessages.setText("");
        searchPositions.clear();
        currentSearchIdx = -1;
        tvPartnerName.setBackgroundColor(0x00000000);
        chatAdapter.setSearchQuery("");
        chatAdapter.notifyDataSetChanged();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Trạng thái chặn (UI)
    // ══════════════════════════════════════════════════════════════════════════
    private void updateBlockUI(ChatDetailViewModel.BlockState state) {
        boolean blocked = state.anyoneBlocked();
        if (tvBlockedBanner != null) {
            if (state.partnerBlockedMe && !state.iBlockedPartner) {
                tvBlockedBanner.setText("🚫 Bạn đã bị người này chặn. Không thể gửi tin nhắn.");
                tvBlockedBanner.setVisibility(View.VISIBLE);
            } else if (state.iBlockedPartner) {
                tvBlockedBanner.setText("🚫 Bạn đang chặn người này. Hãy bỏ chặn để nhắn tin.");
                tvBlockedBanner.setVisibility(View.VISIBLE);
            } else {
                tvBlockedBanner.setVisibility(View.GONE);
            }
        }
        if (etMessage != null) {
            etMessage.setEnabled(!blocked);
            etMessage.setHint(blocked ? "Không thể gửi tin nhắn" : "Nhập tin nhắn...");
        }
        updateSendButtonState();
        if (btnAddMedia != null) btnAddMedia.setEnabled(!blocked);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Thu hồi tin nhắn
    // ══════════════════════════════════════════════════════════════════════════
    private void recallMessage(String messageId, ChatMessage msg) {
        if (!msg.getSenderId().equals(currentUserId)) return; // chỉ thu hồi tin của mình
        new AlertDialog.Builder(this)
                .setTitle("Thu hồi tin nhắn")
                .setMessage("Tin nhắn sẽ bị thu hồi với cả hai phía và không thể hoàn tác.")
                .setPositiveButton("Thu hồi", (d, w) -> viewModel.recallMessage(messageId))
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Setup Chat
    // ══════════════════════════════════════════════════════════════════════════
    private void setupChat() {
        chatAdapter = new ChatAdapter();
        chatAdapter.setOnMediaClickListener(this::openFullscreenMedia);

        chatAdapter.setOnRetryUploadListener(failedMsg -> {
            String localId = failedMsg.getMessageId();
            RetryData data = retryDataMap.remove(localId);
            if (data == null) return;
            for (ChatMessage m : tempMessages) {
                if (localId.equals(m.getMessageId())) {
                    m.setUploading(true);
                    m.setUploadFailed(false);
                    break;
                }
            }
            rebuildAllMessages(false);
            retryUpload(localId, data.item, data.text);
        });

        chatAdapter.setOnMessageActionListener(new ChatAdapter.OnMessageActionListener() {
            @Override public void onRecall(String messageId, ChatMessage message) { recallMessage(messageId, message); }
            @Override public void onForward(ChatMessage message) { forwardMessage(message); }
            @Override public void onReportMessage(ChatMessage message) { reportMessage(message); }
        });

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(chatAdapter);
        rvMessages.setHasFixedSize(true);
    }

    private void openFullscreenMedia(ChatMessage clickedMsg) {
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<Boolean> isVideos = new ArrayList<>();
        int startPosition = 0;

        for (int i = 0; i < allMessages.size(); i++) {
            ChatMessage msg = allMessages.get(i);
            if (msg.isRecalled()) continue;
            if (msg.isVideo() && msg.getVideoUrl() != null && !msg.getVideoUrl().isEmpty()) {
                urls.add(msg.getVideoUrl());
                isVideos.add(true);
                if (msg.getMessageId() != null && msg.getMessageId().equals(clickedMsg.getMessageId()))
                    startPosition = urls.size() - 1;
            } else if (msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                urls.add(msg.getImageUrl());
                isVideos.add(false);
                if (msg.getMessageId() != null && msg.getMessageId().equals(clickedMsg.getMessageId()))
                    startPosition = urls.size() - 1;
            }
        }
        if (urls.isEmpty()) return;

        Intent intent = new Intent(this, FullscreenMediaActivity.class);
        intent.putStringArrayListExtra(FullscreenMediaActivity.EXTRA_URLS, urls);
        intent.putExtra(FullscreenMediaActivity.EXTRA_IS_VIDEOS, isVideos);
        intent.putExtra(FullscreenMediaActivity.EXTRA_START_POS, startPosition);
        startActivity(intent);
    }

    private void closeMediaOverlay() {
        if (layoutMediaOverlay == null) return;
        if (videoViewOverlay != null) videoViewOverlay.stopPlayback();
        layoutMediaOverlay.setVisibility(View.GONE);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Media Picker
    // ══════════════════════════════════════════════════════════════════════════
    private void applyTabStyle() {
        if (isVideoMode) {
            ivTabImage.setAlpha(0.45f); indicatorImage.setVisibility(View.INVISIBLE);
            ivTabVideo.setAlpha(1.0f);  indicatorVideo.setVisibility(View.VISIBLE);
        } else {
            ivTabImage.setAlpha(1.0f);  indicatorImage.setVisibility(View.VISIBLE);
            ivTabVideo.setAlpha(0.45f); indicatorVideo.setVisibility(View.INVISIBLE);
        }
    }

    private void reloadPickerContent() {
        if (mediaPickerAdapter == null) return;
        new Thread(() -> {
            mediaPickerAdapter.loadFromDevice(this, isVideoMode);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) mediaPickerAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void setupMediaPicker() {
        if (rvMediaPicker == null) return;
        mediaPickerAdapter = new MediaPickerAdapter();
        rvMediaPicker.setLayoutManager(new GridLayoutManager(this, 3));
        rvMediaPicker.setAdapter(mediaPickerAdapter);

        mediaPickerAdapter.setOnMediaSelectedListener(selected -> {
            List<MediaPickerAdapter.MediaItem> toKeep = new ArrayList<>();
            for (MediaPickerAdapter.MediaItem item : pendingMedia)
                if (item.isVideo != isVideoMode) toKeep.add(item);
            pendingMedia.clear();
            pendingMedia.addAll(toKeep);
            pendingMedia.addAll(selected);
            updatePickerSendButton();
            updateSendButtonState();
        });

        if (btnPickerSend != null) {
            btnPickerSend.setOnClickListener(v -> {
                if (!pendingMedia.isEmpty()) handleSendMessage();
                closeMediaPicker();
            });
        }
    }

    private void updatePickerSendButton() {
        if (btnPickerSend == null) return;
        int total = pendingMedia.size();
        btnPickerSend.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        if (total > 0) btnPickerSend.setText("Gửi " + total);
    }

    private void checkPermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hi = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
            boolean hv = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)  == PackageManager.PERMISSION_GRANTED;
            if (!hi || !hv) { permissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}); return; }
        } else {
            boolean hs = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (!hs) { permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}); return; }
        }
        openMediaPicker(isVideoMode);
    }

    private void openMediaPicker(boolean videoMode) {
        if (layoutMediaPicker == null || mediaPickerAdapter == null) return;
        isPickerOpen = true;
        isVideoMode  = videoMode;
        applyTabStyle();
        layoutMediaPicker.setVisibility(View.VISIBLE);
        new Thread(() -> {
            mediaPickerAdapter.loadFromDevice(this, isVideoMode);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    mediaPickerAdapter.notifyDataSetChanged();
                    scrollToBottom();
                }
            });
        }).start();
    }

    private void closeMediaPicker() {
        isPickerOpen = false;
        if (layoutMediaPicker != null) layoutMediaPicker.setVisibility(View.GONE);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Gửi tin nhắn
    // ══════════════════════════════════════════════════════════════════════════
    private void handleSendMessage() {
        if (viewModel.isAnyoneBlocked()) {
            Toast.makeText(this, "Không thể gửi tin nhắn", Toast.LENGTH_SHORT).show();
            return;
        }
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty() && pendingMedia.isEmpty()) return;

        if (!pendingMedia.isEmpty()) {
            sendMediaSequentially(new ArrayList<>(pendingMedia), content);
            pendingMedia.clear();
            if (mediaPickerAdapter != null) mediaPickerAdapter.clearSelection();
            updatePickerSendButton();
            closeMediaPicker();
        } else {
            viewModel.sendMessage(content, null, null);
            etMessage.setText("");
        }
    }

    private void sendMediaSequentially(List<MediaPickerAdapter.MediaItem> items, String textContent) {
        btnSend.setEnabled(false);
        final int   total     = items.size();
        final int[] doneCount = {0};

        for (int i = 0; i < total; i++) {
            final int    index   = i;
            final MediaPickerAdapter.MediaItem item = items.get(index);
            final String msgText = (index == 0) ? textContent : "";

            final String localId = "local_" + System.currentTimeMillis() + "_" + index;
            addTempMessage(buildTempMessage(localId, item, msgText));

            if (item.isVideo) {
                CloudinaryHelper.uploadVideo(getApplicationContext(), item.uri,
                        new CloudinaryHelper.OnUploadCallback() {
                            @Override public void onSuccess(String url) {
                                runOnUiThread(() -> {
                                    removeTempMessage(localId);
                                    viewModel.sendMessage(msgText, null, url);
                                    finishOneUpload(doneCount, total);
                                });
                            }
                            @Override public void onFailure(String error) {
                                runOnUiThread(() -> {
                                    markTempMessageFailed(localId, item, msgText);
                                    Toast.makeText(ChatDetailActivity.this, "Lỗi video: " + error, Toast.LENGTH_SHORT).show();
                                    finishOneUpload(doneCount, total);
                                });
                            }
                        });
            } else {
                CloudinaryHelper.uploadImage(getApplicationContext(), item.uri,
                        new CloudinaryHelper.OnUploadCallback() {
                            @Override public void onSuccess(String url) {
                                runOnUiThread(() -> {
                                    removeTempMessage(localId);
                                    viewModel.sendMessage(msgText, url, null);
                                    finishOneUpload(doneCount, total);
                                });
                            }
                            @Override public void onFailure(String error) {
                                runOnUiThread(() -> {
                                    markTempMessageFailed(localId, item, msgText);
                                    Toast.makeText(ChatDetailActivity.this, "Lỗi ảnh: " + error, Toast.LENGTH_SHORT).show();
                                    finishOneUpload(doneCount, total);
                                });
                            }
                        });
            }
        }
    }

    private void finishOneUpload(int[] doneCount, int total) {
        doneCount[0]++;
        if (doneCount[0] >= total) {
            etMessage.setText("");
            updateSendButtonState();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════
    private void updateSendButtonState() {
        if (btnSend == null) return;
        if (viewModel.isAnyoneBlocked()) { btnSend.setEnabled(false); return; }
        btnSend.setEnabled(!pendingMedia.isEmpty()
                || (etMessage != null && !etMessage.getText().toString().trim().isEmpty()));
    }

    private void scrollToBottom() {
        if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
            rvMessages.postDelayed(
                    () -> rvMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1), 100);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Menu (Chặn)
    // ══════════════════════════════════════════════════════════════════════════
    private void showPopupMenu(View v) {
        boolean iBlocked = viewModel.isIBlockedPartner();
        String blockLabel = iBlocked ? "Bỏ chặn " + partnerName : "Chặn " + partnerName;

        new AlertDialog.Builder(this)
                .setItems(new String[]{blockLabel}, (dialog, which) -> {
                    if (iBlocked) {
                        viewModel.unblock();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Chặn " + partnerName + "?")
                                .setMessage("Người này sẽ không thể gửi tin nhắn cho bạn. Bạn có thể bỏ chặn bất cứ lúc nào.")
                                .setPositiveButton("Chặn", (d, w) -> viewModel.block())
                                .setNegativeButton("Hủy", null)
                                .show();
                    }
                })
                .show();
    }

    private void showReportDialog(ChatMessage message) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_report_fb, null);
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.ReportBottomSheetTheme);
        bottomSheet.setContentView(dialogView);

        dialogView.findViewById(R.id.btn_report_close).setOnClickListener(close -> bottomSheet.dismiss());
        dialogView.findViewById(R.id.option_impersonation).setOnClickListener(opt -> {
            bottomSheet.dismiss();
            viewModel.submitMessageReport(message, "Giả mạo người khác");
        });
        dialogView.findViewById(R.id.option_fraud).setOnClickListener(opt -> {
            bottomSheet.dismiss();
            viewModel.submitMessageReport(message, "Lừa đảo hoặc gian lận");
        });
        bottomSheet.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Chuyển tiếp / Báo cáo tin nhắn
    // ══════════════════════════════════════════════════════════════════════════
    private void forwardMessage(ChatMessage message) {
        pendingForwardMessage = message;
        viewModel.loadForwardTargets();
    }

    private void reportMessage(ChatMessage message) { showReportDialog(message); }

    @Override
    public void onBackPressed() {
        if (layoutSearchBar != null && layoutSearchBar.getVisibility() == View.VISIBLE) { closeSearch(); return; }
        if (isPickerOpen) { closeMediaPicker(); return; }
        super.onBackPressed();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Tin nhắn tạm (preview khi upload)
    // ══════════════════════════════════════════════════════════════════════════
    private final Map<String, RetryData> retryDataMap = new HashMap<>();

    private static class RetryData {
        final MediaPickerAdapter.MediaItem item;
        final String text;
        RetryData(MediaPickerAdapter.MediaItem item, String text) { this.item = item; this.text = text; }
    }

    private ChatMessage buildTempMessage(String localId, MediaPickerAdapter.MediaItem item, String text) {
        ChatMessage msg = new ChatMessage();
        msg.setMessageId(localId);
        msg.setSenderId(currentUserId);
        msg.setContent(text != null ? text : "");
        msg.setLocalUri(item.uri.toString());
        msg.setUploading(true);
        msg.setUploadFailed(false);
        msg.setMessageType(item.isVideo ? ChatMessage.TYPE_VIDEO : ChatMessage.TYPE_IMAGE);
        return msg;
    }

    private void addTempMessage(ChatMessage msg) {
        tempMessages.add(msg);
        rebuildAllMessages(true);
    }

    private void removeTempMessage(String localId) {
        tempMessages.removeIf(m -> localId.equals(m.getMessageId()));
        rebuildAllMessages(false);
    }

    private void markTempMessageFailed(String localId, MediaPickerAdapter.MediaItem item, String msgText) {
        for (ChatMessage m : tempMessages) {
            if (localId.equals(m.getMessageId())) {
                m.setUploading(false);
                m.setUploadFailed(true);
                break;
            }
        }
        rebuildAllMessages(false);
        retryDataMap.put(localId, new RetryData(item, msgText));
    }

    private void retryUpload(String localId, MediaPickerAdapter.MediaItem item, String msgText) {
        if (item.isVideo) {
            CloudinaryHelper.uploadVideo(getApplicationContext(), item.uri,
                    new CloudinaryHelper.OnUploadCallback() {
                        @Override public void onSuccess(String url) {
                            runOnUiThread(() -> { removeTempMessage(localId); viewModel.sendMessage(msgText, null, url); });
                        }
                        @Override public void onFailure(String error) {
                            runOnUiThread(() -> {
                                markTempMessageFailed(localId, item, msgText);
                                Toast.makeText(ChatDetailActivity.this, "Thử lại thất bại: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
        } else {
            CloudinaryHelper.uploadImage(getApplicationContext(), item.uri,
                    new CloudinaryHelper.OnUploadCallback() {
                        @Override public void onSuccess(String url) {
                            runOnUiThread(() -> { removeTempMessage(localId); viewModel.sendMessage(msgText, url, null); });
                        }
                        @Override public void onFailure(String error) {
                            runOnUiThread(() -> {
                                markTempMessageFailed(localId, item, msgText);
                                Toast.makeText(ChatDetailActivity.this, "Thử lại thất bại: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
        }
    }
}
