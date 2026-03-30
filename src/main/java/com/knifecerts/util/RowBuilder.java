package com.knifecerts.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class RowBuilder {

    public static void addRow(List<InlineKeyboardButton> row, String text, String callbackData){
        row.add(InlineKeyboardButton
                .builder()
                .text(text)
                .callbackData(callbackData)
                .build());
    }

    public static List<InlineKeyboardButton> getRow(String text, String callbackData){
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton
                .builder()
                .text(text)
                .callbackData(callbackData)
                .build());
        return row;
    }
}
