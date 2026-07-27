package com.kma.knowledge.service;

import com.kma.common.exception.KmaException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative CSS parser for portal themes. At-rules and nested rules are rejected; every selector
 * is compiled under the immutable site scope before publication.
 */
@Component
public class PortalCssScopeService {
    public String scope(String siteKey, String css) {
        if (!StringUtils.hasText(css)) return "";
        if (css.contains("@")) throw new KmaException(400, "PORTAL_CSS_AT_RULE_FORBIDDEN");
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (cursor < css.length()) {
            int open = css.indexOf('{', cursor);
            if (open < 0) {
                if (!css.substring(cursor).isBlank())
                    throw new KmaException(400, "PORTAL_CSS_SYNTAX_INVALID");
                break;
            }
            int close = css.indexOf('}', open + 1);
            if (close < 0 || css.indexOf('{', open + 1) >= 0 && css.indexOf('{', open + 1) < close)
                throw new KmaException(400, "PORTAL_CSS_NESTING_FORBIDDEN");
            String selectorText = css.substring(cursor, open).trim();
            String declarations = css.substring(open + 1, close).trim();
            if (selectorText.isBlank() || declarations.isBlank())
                throw new KmaException(400, "PORTAL_CSS_EMPTY_RULE");
            List<String> selectors = new ArrayList<>();
            for (String selector : selectorText.split(",")) {
                String normalized = selector.trim();
                if (normalized.isBlank() || normalized.contains(":has(")
                    || normalized.contains("[data-kma-site"))
                    throw new KmaException(400, "PORTAL_CSS_SELECTOR_FORBIDDEN");
                selectors.add("[data-kma-site=\"" + siteKey + "\"] " + normalized);
            }
            result.append(String.join(",\n", selectors)).append(" {\n")
                .append(declarations).append("\n}\n");
            cursor = close + 1;
        }
        return result.toString();
    }
}
