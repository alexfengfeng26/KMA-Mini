package com.kma.knowledge.rag.extract;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class HtmlDocumentExtractor implements DocumentExtractor {
    @Override
    public boolean supports(String mimeType) {
        return "text/html".equals(mimeType) || "application/xhtml+xml".equals(mimeType);
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        StringBuilder text = new StringBuilder();
        new ParserDelegator().parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8),
            new HTMLEditorKit.ParserCallback() {
                private boolean ignored;

                @Override
                public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) ignored = true;
                    if (tag == HTML.Tag.P || tag == HTML.Tag.DIV || tag == HTML.Tag.H1
                        || tag == HTML.Tag.H2 || tag == HTML.Tag.H3 || tag == HTML.Tag.LI) text.append('\n');
                }

                @Override
                public void handleEndTag(HTML.Tag tag, int position) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) ignored = false;
                }

                @Override
                public void handleText(char[] data, int position) {
                    if (!ignored) text.append(data).append(' ');
                }
            }, true);
        return text.toString().trim();
    }
}
