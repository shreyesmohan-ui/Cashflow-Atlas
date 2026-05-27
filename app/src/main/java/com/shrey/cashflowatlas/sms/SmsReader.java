package com.shrey.cashflowatlas.sms;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.List;

public class SmsReader {
    public List<String> readRecentInbox(Context context, int limit) {
        List<String> messages = new ArrayList<>();
        String[] projection = new String[]{Telephony.Sms.BODY, Telephony.Sms.DATE};
        String sort = Telephony.Sms.DATE + " DESC";

        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                sort
        )) {
            if (cursor == null) return messages;
            int bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
            while (cursor.moveToNext() && messages.size() < limit) {
                String body = cursor.getString(bodyIndex);
                if (body != null && !body.trim().isEmpty()) {
                    messages.add(body);
                }
            }
        } catch (Exception ignored) {
            // Some OEM SMS providers can expose unusual rows. Keep the app open.
        }
        return messages;
    }
}
