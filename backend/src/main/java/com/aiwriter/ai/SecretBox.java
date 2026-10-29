package com.aiwriter.ai;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 用户自带 API Key 的加解密（AES-256-GCM）。
 *
 * <p>为什么不能明文存：数据库、备份、日志任何一处泄露，用户的 key 就被人拿去刷了。
 * 这里用一台服务器的 {@code ai.key-secret} 派生主密钥，每条记录随机 IV，
 * 密文带前缀 {@code v1:}，将来换算法时可以按前缀兼容旧数据。
 */
public final class SecretBox {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretBox() {
    }

    public static String encrypt(String plain, String secret) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(secret), new GCMParameterSpec(TAG_BITS, iv));
            byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + body.length).put(iv).put(body).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    public static String decrypt(String encoded, String secret) {
        if (encoded == null || encoded.isBlank()) return null;
        if (!encoded.startsWith(PREFIX)) {
            throw new IllegalArgumentException("不认识的密文格式");
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded.substring(PREFIX.length()));
            ByteBuffer buf = ByteBuffer.wrap(all);
            byte[] iv = new byte[IV_BYTES];
            buf.get(iv);
            byte[] body = new byte[buf.remaining()];
            buf.get(body);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(secret), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败（密钥可能已更换）", e);
        }
    }

    /** 只给界面看的掩码：sk-abcd…wxyz */
    public static String mask(String plain) {
        if (plain == null || plain.isBlank()) return null;
        String t = plain.trim();
        if (t.length() <= 8) return "****";
        return t.substring(0, 4) + "…" + t.substring(t.length() - 4);
    }

    private static SecretKeySpec key(String secret) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
