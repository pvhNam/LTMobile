package com.example.doanmb.ui.aiassistant.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.AiChatMessage;

import java.util.List;

public class AiMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnCarClickListener {
        void onCarClick(AiChatMessage.AiSuggestedCar car);
    }

    private final List<AiChatMessage> messages;
    private final OnCarClickListener carClickListener;

    public AiMessageAdapter(List<AiChatMessage> messages, OnCarClickListener carClickListener) {
        this.messages = messages;
        this.carClickListener = carClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getRole();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == AiChatMessage.ROLE_USER) {
            return new MessageHolder(inflater.inflate(R.layout.item_ai_message_user, parent, false));
        }
        if (viewType == AiChatMessage.ROLE_AI_TYPING) {
            return new TypingHolder(inflater.inflate(R.layout.item_ai_message_typing, parent, false));
        }
        return new BotMessageHolder(inflater.inflate(R.layout.item_ai_message_bot, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AiChatMessage message = messages.get(position);
        if (holder instanceof BotMessageHolder) {
            ((BotMessageHolder) holder).bind(message, carClickListener);
        } else if (holder instanceof MessageHolder) {
            ((MessageHolder) holder).text.setText(message.getText());
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageHolder extends RecyclerView.ViewHolder {
        final TextView text;

        MessageHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tv_message);
        }
    }

    static class TypingHolder extends RecyclerView.ViewHolder {
        TypingHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class BotMessageHolder extends MessageHolder {
        final LinearLayout suggestions;

        BotMessageHolder(@NonNull View itemView) {
            super(itemView);
            suggestions = itemView.findViewById(R.id.layout_suggested_cars);
        }

        void bind(AiChatMessage message, OnCarClickListener listener) {
            text.setText(message.getText());
            text.setAlpha(message.isError() ? 0.72f : 1f);
            suggestions.removeAllViews();
            suggestions.setVisibility(message.hasSuggestions() ? View.VISIBLE : View.GONE);

            Context context = itemView.getContext();
            for (AiChatMessage.AiSuggestedCar car : message.getSuggestedCars()) {
                View carView = LayoutInflater.from(context)
                        .inflate(R.layout.item_ai_suggested_car, suggestions, false);
                TextView name = carView.findViewById(R.id.tv_car_name);
                TextView price = carView.findViewById(R.id.tv_car_price);
                String displayName = car.getName().isEmpty() ? car.getBrand() : car.getName();
                name.setText(displayName.isEmpty() ? "Xe được gợi ý" : displayName);
                price.setText(car.getPrice().isEmpty() ? "Xem thông tin xe" : car.getPrice());
                carView.setOnClickListener(v -> listener.onCarClick(car));
                suggestions.addView(carView);
            }
        }
    }
}
