package com.example.tomatomall.retrieval;
import java.util.Locale;
import java.util.regex.Pattern;
/** Literal matching only. Chinese terms are substrings; Latin terms respect word boundaries. */
public final class ProductText {
    private ProductText() {}
    public static boolean contains(String text,String term) {
        text=text.toLowerCase(Locale.ROOT);term=term.toLowerCase(Locale.ROOT);
        if(term.matches("[a-z][a-z0-9+#.-]*"))
            return Pattern.compile("(?<![a-z0-9_])"+Pattern.quote(term)+"(?![a-z0-9_])").matcher(text).find();
        return text.contains(term);
    }
    public static String title(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replace("（合成测试）","");
    }
}
