package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** ViewModel màn danh sách người dùng (admin): tải toàn bộ users từ Firestore. */
public class AdminUsersViewModel extends ViewModel {

    private final MutableLiveData<AdminDocList> users = new MutableLiveData<>(AdminDocList.empty());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<AdminDocList> getUsers() { return users; }
    public LiveData<String> getError() { return error; }

    public void load() {
        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> data = new ArrayList<>();
                    List<String> ids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        data.add(doc.getData());
                        ids.add(doc.getId());
                    }
                    users.setValue(new AdminDocList(data, ids));
                })
                .addOnFailureListener(e -> error.setValue(e.getMessage()));
    }
}
