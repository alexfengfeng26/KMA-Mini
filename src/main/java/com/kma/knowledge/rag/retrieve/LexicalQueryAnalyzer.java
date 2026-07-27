package com.kma.knowledge.rag.retrieve;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Portable application-side tokenizer for PostgreSQL simple text search. */
@Component
public class LexicalQueryAnalyzer {

    public String analyzeDocument(String text) {
        return String.join(" ", tokenizeValue(text));
    }

    public String toTsQuery(String query) {
        Set<String> tokens = new LinkedHashSet<>(tokenizeValue(query));
        return String.join(" | ", tokens);
    }

    List<String> tokenize(String value) {
        return tokenizeValue(value);
    }

    public static List<String> tokenizeValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        StringBuilder han = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushLatin(latin, result);
                han.appendCodePoint(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                flushHan(han, result);
                latin.appendCodePoint(Character.toLowerCase(codePoint));
            } else {
                flushLatin(latin, result);
                flushHan(han, result);
            }
        });
        flushLatin(latin, result);
        flushHan(han, result);
        return result;
    }

    private static void flushLatin(StringBuilder value, List<String> output) {
        if (!value.isEmpty()) {
            output.add(value.toString().toLowerCase(Locale.ROOT));
            value.setLength(0);
        }
    }

    private static void flushHan(StringBuilder value, List<String> output) {
        if (value.isEmpty()) {
            return;
        }
        int[] points = value.codePoints().toArray();
        if (points.length == 1) {
            output.add(new String(points, 0, 1));
        } else {
            for (int i = 0; i < points.length - 1; i++) {
                output.add(new String(points, i, 2));
            }
        }
        value.setLength(0);
    }
}
