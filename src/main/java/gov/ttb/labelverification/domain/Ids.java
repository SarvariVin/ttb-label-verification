package gov.ttb.labelverification.domain;

import java.security.SecureRandom;

/** Generates 21-character URL-safe random IDs (126 bits of entropy). */
public final class Ids {

    private static final char[] ALPHABET =
            "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict".toCharArray();
    private static final int SIZE = 21;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String newId() {
        char[] id = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            id[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(id);
    }
}
