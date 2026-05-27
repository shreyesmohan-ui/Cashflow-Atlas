package com.shrey.cashflowatlas.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.shrey.cashflowatlas.model.FinanceState;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CashflowStore {
    private static final String PREFS = "cashflow_atlas_store";
    private static final String STATE = "state_json";
    private static final String KEY_ALIAS = "cashflow_atlas_state_key";

    private final SharedPreferences preferences;

    public CashflowStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public FinanceState load() {
        String stored = preferences.getString(STATE, null);
        if (stored == null) {
            return FinanceState.demo();
        }
        try {
            String json = decrypt(stored);
            return FinanceState.fromJson(new JSONObject(json));
        } catch (Exception ignored) {
            return FinanceState.demo();
        }
    }

    public void save(FinanceState state) {
        try {
            preferences.edit().putString(STATE, encrypt(state.toJson().toString())).apply();
        } catch (Exception ignored) {
            // Local persistence should never crash the UI.
        }
    }

    public void reset() {
        preferences.edit().clear().apply();
    }

    private String encrypt(String plainText) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String decrypt(String stored) throws Exception {
        if (!stored.contains(":")) {
            return stored;
        }
        String[] parts = stored.split(":", 2);
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(
                    new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setRandomizedEncryptionRequired(true)
                            .build()
            );
            keyGenerator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }
}
