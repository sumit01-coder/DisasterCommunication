package com.example.disastercomm.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class SecurityUtil {

    private static final String TAG = "SecurityUtil";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "DisasterCommKey";
    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String KEY_PUBLIC_KEY = "my_public_key";

    // --- RSA Key Management ---

    public static KeyPair getOrGenerateKeyPair(Context context) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                // Generate new KeyPair
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER);
                keyPairGenerator.initialize(
                        new KeyGenParameterSpec.Builder(
                                KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                                .setKeySize(2048)
                                .build());
                KeyPair keyPair = keyPairGenerator.generateKeyPair();

                // Save Public Key String for easy sharing
                savePublicKey(context, keyPair.getPublic());
                return keyPair;
            } else {
                // Load existing
                KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS,
                        null);
                PublicKey publicKey = keyStore.getCertificate(KEY_ALIAS).getPublicKey();
                return new KeyPair(publicKey, privateKeyEntry.getPrivateKey());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error managing keys", e);
            return null;
        }
    }

    private static void savePublicKey(Context context, PublicKey publicKey) {
        String pubKeyStr = Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_PUBLIC_KEY, pubKeyStr).apply();
    }

    public static String getMyPublicKeyString(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PUBLIC_KEY, null);
    }

    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT);
    }

    public static PublicKey decodePublicKey(String keyStr) {
        try {
            byte[] keyBytes = Base64.decode(keyStr, Base64.DEFAULT);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA);
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            Log.e(TAG, "Error decoding public key", e);
            return null;
        }
    }

    // --- AES Key Management ---

    public static SecretKey generateAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "Error generating AES key", e);
            return null;
        }
    }

    public static SecretKey decodeAesKey(String keyStr) {
        byte[] decodedKey = Base64.decode(keyStr, Base64.DEFAULT);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    public static String encodeAesKey(SecretKey key) {
        return Base64.encodeToString(key.getEncoded(), Base64.DEFAULT);
    }

    // --- Encryption / Decryption ---

    public static String encryptAes(String content, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);
            javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            byte[] encryptedBytes = cipher.doFinal(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);
            return Base64.encodeToString(byteBuffer.array(), Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "AES Encrypt failed", e);
            return null;
        }
    }

    public static String decryptAes(String encryptedContent, SecretKey key) {
        try {
            byte[] decodedBytes = Base64.decode(encryptedContent, Base64.DEFAULT);
            if (decodedBytes.length < 12) return null;
            byte[] iv = java.util.Arrays.copyOfRange(decodedBytes, 0, 12);
            byte[] encryptedBytes = java.util.Arrays.copyOfRange(decodedBytes, 12, decodedBytes.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "AES Decrypt failed", e);
            return null;
        }
    }

    public static String encryptRsa(SecretKey aesKey, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(aesKey.getEncoded());
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "RSA Encrypt failed", e);
            return null;
        }
    }

    public static SecretKey decryptRsa(String encryptedAesKeyStr, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decodedBytes = Base64.decode(encryptedAesKeyStr, Base64.DEFAULT);
            byte[] decryptedKeyBytes = cipher.doFinal(decodedBytes);
            return new SecretKeySpec(decryptedKeyBytes, 0, decryptedKeyBytes.length, "AES");
        } catch (Exception e) {
            Log.e(TAG, "RSA Decrypt failed", e);
            return null;
        }
    }
}
