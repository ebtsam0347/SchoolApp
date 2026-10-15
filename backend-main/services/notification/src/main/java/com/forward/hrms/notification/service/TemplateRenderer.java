package com.forward.hrms.notification.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single-pass {{placeholder}} substitution. Whitespace-tolerant
 * ({@code {{ name }}}) and forgiving — missing keys render as the empty
 * string.
 *
 * <p>§7.notification — substituted values are HTML-escaped to prevent
 * stored-XSS via vars (e.g. a user's {@code fullName} containing
 * {@code <script>...</script>} renders inert in the FE bell). The
 * static template text is HR-authored and trusted; only the var
 * substitutions are escaped.
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    public String render(String template, Map<String, ?> vars) {
        if (template == null || template.isEmpty()) return template;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object v = vars == null ? null : vars.get(key);
            String replacement = v == null ? "" : escapeHtml(v.toString());
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Minimal HTML entity escaping for the five chars that matter. */
    private static String escapeHtml(String s) {
        if (s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
