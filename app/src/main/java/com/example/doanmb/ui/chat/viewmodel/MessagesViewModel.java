package com.example.doanmb.ui.chat.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.ChatRepository;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel màn Tin nhắn: hội thoại realtime + shortcut + tìm kiếm 2 lớp (tên + nội dung),
 * và thông báo realtime. View chỉ observe và mở Activity/Dialog (cần Context).
 */
public class MessagesViewModel extends ViewModel {

    private final MutableLiveData<String> greetingName = new MutableLiveData<>();
    private final MutableLiveData<String> avatarUrl    = new MutableLiveData<>();
    private final MutableLiveData<List<Map<String, Object>>> conversations = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Map<String, Object>>> shortcuts     = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Map<String, Object>>> notifications = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> showEmpty       = new MutableLiveData<>(false);
    private final MutableLiveData<String>  selectedShortcut = new MutableLiveData<>();

    public LiveData<String> getGreetingName() { return greetingName; }
    public LiveData<String> getAvatarUrl()    { return avatarUrl; }
    public LiveData<List<Map<String, Object>>> getConversations() { return conversations; }
    public LiveData<List<Map<String, Object>>> getShortcuts()     { return shortcuts; }
    public LiveData<List<Map<String, Object>>> getNotifications() { return notifications; }
    public LiveData<Boolean> getShowEmpty()        { return showEmpty; }
    public LiveData<String>  getSelectedShortcut() { return selectedShortcut; }

    private final List<Map<String, Object>> convList     = new ArrayList<>();
    private final List<Map<String, Object>> shortcutList = new ArrayList<>();

    private ListenerRegistration convListener;
    private ListenerRegistration notifListener;

    private String currentUid = "";
    private String query = "";
    private String selectedShortcutPartnerId = null;

    // ── Hồ sơ người dùng ──────────────────────────────────────────────────────

    public void loadUserProfile(String uid) {
        ChatRepository.loadUserBrief(uid, (name, avatar) -> {
            greetingName.setValue("Hi, " + (name != null ? name : "User"));
            if (avatar != null && !avatar.isEmpty()) avatarUrl.setValue(avatar);
        });
    }

    // ── Hội thoại realtime ──────────────────────────────────────────────────────

    public void startConversations(String uid) {
        currentUid = uid != null ? uid : "";
        if (convListener != null) convListener.remove();
        convListener = ChatRepository.listenConversations(currentUid, (snapshots, error) -> {
            if (error != null || snapshots == null) return;

            convList.clear();
            shortcutList.clear();
            Map<String, Boolean> addedPartners = new HashMap<>();

            for (QueryDocumentSnapshot doc : snapshots) {
                Map<String, Object> data = new HashMap<>(doc.getData());
                data.put("roomId", doc.getId());

                String buyerId   = (String) data.get("buyerId");
                String sellerId  = (String) data.get("sellerId");
                String partnerId = currentUid.equals(buyerId) ? sellerId : buyerId;
                data.put("partnerId", partnerId);

                if (partnerId != null) {
                    ChatRepository.loadUserBrief(partnerId, (name, avatar) -> {
                        data.put("partnerName",   name);
                        data.put("partnerAvatar", avatar);

                        if (!addedPartners.containsKey(partnerId) && shortcutList.size() < 10) {
                            shortcutList.add(new HashMap<>(data));
                            addedPartners.put(partnerId, true);
                            shortcuts.setValue(new ArrayList<>(shortcutList));
                        }

                        if (!query.isEmpty()) searchEverything(query);
                        else showAllConversations();
                    });
                }
                convList.add(data);
            }

            if (query.isEmpty()) showAllConversations();
            else searchEverything(query);
        });
    }

    // ── Tìm kiếm 2 lớp ───────────────────────────────────────────────────────────

    /** Người dùng đổi nội dung ô tìm kiếm. */
    public void setSearchQuery(String q) {
        if (selectedShortcutPartnerId != null) {
            selectedShortcutPartnerId = null;
            selectedShortcut.setValue(null);
        }
        query = q != null ? q.trim() : "";
        if (query.isEmpty()) showAllConversations();
        else searchEverything(query);
    }

    private void searchEverything(String q) {
        String lower = q.toLowerCase();

        List<Map<String, Object>> nameMatches = new ArrayList<>();
        for (Map<String, Object> item : convList) {
            String carName     = String.valueOf(item.getOrDefault("carName", "")).toLowerCase();
            String partnerName = String.valueOf(item.getOrDefault("partnerName", "")).toLowerCase();
            if (carName.contains(lower) || partnerName.contains(lower)) nameMatches.add(item);
        }

        if (convList.isEmpty()) { mergeAndShow(nameMatches, new ArrayList<>()); return; }

        final int[] remaining = {convList.size()};
        final List<Map<String, Object>> msgMatches = new ArrayList<>();

        for (Map<String, Object> conv : convList) {
            String roomId = String.valueOf(conv.getOrDefault("roomId", ""));
            if (roomId.isEmpty()) { decrementAndMerge(remaining, nameMatches, msgMatches); continue; }

            ChatRepository.searchMessagesInRoom(roomId, new ChatRepository.OnSnapshot() {
                @Override public void onLoaded(com.google.firebase.firestore.QuerySnapshot snapshots) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        if (Boolean.TRUE.equals(doc.getBoolean("recalled"))) continue;
                        String content = doc.getString("content");
                        if (content != null && content.toLowerCase().contains(lower)) {
                            Map<String, Object> resultItem = new HashMap<>(conv);
                            resultItem.put("matchedMessage", content);
                            synchronized (msgMatches) {
                                boolean already = false;
                                for (Map<String, Object> m : msgMatches)
                                    if (roomId.equals(m.get("roomId"))) { already = true; break; }
                                if (!already) msgMatches.add(resultItem);
                            }
                            break;
                        }
                    }
                    decrementAndMerge(remaining, nameMatches, msgMatches);
                }
                @Override public void onError(String message) {
                    decrementAndMerge(remaining, nameMatches, msgMatches);
                }
            });
        }
    }

    private void decrementAndMerge(int[] remaining, List<Map<String, Object>> nameMatches,
                                   List<Map<String, Object>> msgMatches) {
        synchronized (remaining) {
            remaining[0]--;
            if (remaining[0] <= 0) mergeAndShow(nameMatches, msgMatches);
        }
    }

    private void mergeAndShow(List<Map<String, Object>> nameMatches,
                              List<Map<String, Object>> msgMatches) {
        List<Map<String, Object>> merged = new ArrayList<>(nameMatches);
        for (Map<String, Object> m : msgMatches) {
            String roomId = String.valueOf(m.getOrDefault("roomId", ""));
            boolean exists = false;
            for (Map<String, Object> n : nameMatches)
                if (roomId.equals(n.getOrDefault("roomId", ""))) { exists = true; break; }
            if (!exists) merged.add(m);
        }
        publishFiltered(merged, true);
    }

    private void showAllConversations() {
        publishFiltered(new ArrayList<>(convList), false);
    }

    private void publishFiltered(List<Map<String, Object>> results, boolean searching) {
        conversations.postValue(results);
        showEmpty.postValue(searching && results.isEmpty() && !query.isEmpty());
    }

    // ── Shortcut ─────────────────────────────────────────────────────────────────

    public void onShortcutClicked(String partnerId) {
        if (partnerId.equals(selectedShortcutPartnerId)) {
            selectedShortcutPartnerId = null;
            selectedShortcut.setValue(null);
            if (query.isEmpty()) showAllConversations(); else searchEverything(query);
        } else {
            selectedShortcutPartnerId = partnerId;
            selectedShortcut.setValue(partnerId);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> conv : convList)
                if (partnerId.equals(conv.get("partnerId"))) list.add(conv);
            conversations.setValue(list);
            showEmpty.setValue(false);
        }
    }

    // ── Thông báo realtime ─────────────────────────────────────────────────────

    public void startNotifications(String uid) {
        if (uid == null || uid.isEmpty()) return;
        if (notifListener != null) { notifListener.remove(); notifListener = null; }
        notifListener = ChatRepository.listenNotifications(uid, (snapshots, error) -> {
            if (error != null || snapshots == null) return;
            List<Map<String, Object>> list = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshots) {
                Map<String, Object> data = new HashMap<>(doc.getData());
                data.put("docId", doc.getId());
                list.add(data);
            }
            notifications.setValue(list);
        });
    }

    public void markNotificationRead(String docId) { ChatRepository.markNotificationRead(docId); }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (convListener != null) { convListener.remove(); convListener = null; }
        if (notifListener != null) { notifListener.remove(); notifListener = null; }
    }
}
