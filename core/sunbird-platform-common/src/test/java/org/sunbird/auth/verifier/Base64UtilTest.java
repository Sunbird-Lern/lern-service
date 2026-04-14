package org.sunbird.auth.verifier;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class Base64UtilTest {

    @Test
    public void testEncodeDecodeDefault() {
        String original = "Hello World";
        byte[] input = original.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Util.encodeToString(input, Base64Util.DEFAULT);
        // "Hello World" in Base64 is "SGVsbG8gV29ybGQ=" (with newline depending on implementation, Android Base64 might add one)
        // Base64Util implementation seems to add newline if not NO_WRAP

        byte[] decoded = Base64Util.decode(encoded, Base64Util.DEFAULT);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void testEncodeDecodeNoPadding() {
        String original = "Hello World"; // "SGVsbG8gV29ybGQ="
        byte[] input = original.getBytes(StandardCharsets.UTF_8);

        // NO_PADDING should remove '='
        String encoded = Base64Util.encodeToString(input, Base64Util.NO_PADDING | Base64Util.NO_WRAP);
        assertEquals("SGVsbG8gV29ybGQ", encoded);

        byte[] decoded = Base64Util.decode(encoded, Base64Util.NO_PADDING);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void testEncodeDecodeUrlSafe() {
        // Need a string that produces + or /
        // "Subject?" -> "U3ViamVjdD8="
        // "Subject>" -> "U3ViamVjdD4="
        // "Subjects?" -> "U3ViamVjdHM/""

        byte[] input = new byte[] {-5, -10}; // 11111011 11110110 -> bits... should produce + /

        // Let's use a known input that produces + and /
        // standard: +/
        // url safe: -_

        byte[] bytes = new byte[] {(byte)0xFB, (byte)0xF0}; // 11111011 11110000 -> 111110 111111 000000 000000 -> + 8 A A (roughly)

        String encoded = Base64Util.encodeToString(bytes, Base64Util.URL_SAFE | Base64Util.NO_WRAP | Base64Util.NO_PADDING);
        // Expect - and _ if applicable, mostly just testing the flag is accepted and works round trip

        byte[] decoded = Base64Util.decode(encoded, Base64Util.URL_SAFE);
        assertArrayEquals(bytes, decoded);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeInvalid() {
        Base64Util.decode("Invalid@@String", Base64Util.DEFAULT);
    }

    @Test
    public void testEncodeToStringWithOffset() {
        String original = "Hello World";
        byte[] input = original.getBytes(StandardCharsets.UTF_8);
        // Encode only "World" (offset 6, len 5)
        String encoded = Base64Util.encodeToString(input, 6, 5, Base64Util.NO_WRAP);
        assertEquals("V29ybGQ=", encoded);
    }

    @Test
    public void testDecodeWithOffset() {
        String original = "SGVsbG8gV29ybGQ=";
        byte[] input = original.getBytes(StandardCharsets.UTF_8);
        // Decode only "V29ybGQ=" (offset 8, len 8)
        byte[] decoded = Base64Util.decode(input, 8, 8, Base64Util.DEFAULT);
        assertArrayEquals("World".getBytes(StandardCharsets.UTF_8), decoded);
    }
}