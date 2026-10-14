package com.taha1337.btclottery;

import java.util.ArrayList;
import java.util.List;

final class NonceInput {
    static List<Long> parse(String input) {
        String value = input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter at least one nonce guess.");
        String[] parts = value.split("[\\s,;]+", -1);
        List<Long> values = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!part.matches("[0-9]{1,10}")) throw new IllegalArgumentException("Use whole numbers from 0 to 4294967295.");
            long nonce = Long.parseLong(part);
            if (nonce > 4294967295L) throw new IllegalArgumentException("A nonce cannot be greater than 4294967295.");
            values.add(nonce);
            if (values.size() > 128) throw new IllegalArgumentException("Check up to 128 guesses at a time.");
        }
        if (values.isEmpty()) throw new IllegalArgumentException("Enter at least one nonce guess.");
        return values;
    }
}
