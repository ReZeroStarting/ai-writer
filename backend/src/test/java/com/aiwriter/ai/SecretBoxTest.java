package com.aiwriter.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户自带 API Key 的加解密。
 *
 * <p>锁定的点：密文里不能出现明文；换密钥后解密必须失败（而不是悄悄给出错的东西）；
 * 掩码只暴露头尾。
 */
class SecretBoxTest {

    private static final String SECRET = "unit-test-secret";

    @Test
    void shouldRoundTrip() {
        String key = "sk-1234567890abcdefghijklmn";
        String enc = SecretBox.encrypt(key, SECRET);
        assertNotNull(enc);
        assertTrue(enc.startsWith("v1:"));
        assertFalse(enc.contains(key), "密文里不能出现明文");
        assertEquals(key, SecretBox.decrypt(enc, SECRET));
    }

    @Test
    void shouldProduceDifferentCiphertextForSameInput() {
        // 每条记录随机 IV：同样的 key 两次加密结果不同，避免"相同密文=相同 key"的推断
        String a = SecretBox.encrypt("sk-same-key-12345678", SECRET);
        String b = SecretBox.encrypt("sk-same-key-12345678", SECRET);
        assertNotEquals(a, b);
    }

    @Test
    void shouldFailWithWrongSecret() {
        String enc = SecretBox.encrypt("sk-1234567890abcdefghijklmn", SECRET);
        assertThrows(IllegalStateException.class, () -> SecretBox.decrypt(enc, "another-secret"));
    }

    @Test
    void shouldMaskKey() {
        assertEquals("sk-1…klmn", SecretBox.mask("sk-1234567890abcdefghijklmn"));
        assertEquals("****", SecretBox.mask("short"));
        assertNull(SecretBox.mask(null));
        assertNull(SecretBox.mask("   "));
    }
}
