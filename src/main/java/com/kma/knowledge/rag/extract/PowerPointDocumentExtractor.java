package com.kma.knowledge.rag.extract;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class PowerPointDocumentExtractor implements DocumentExtractor {
    @Override
    public boolean supports(String mimeType) {
        return "application/vnd.ms-powerpoint".equals(mimeType)
            || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(mimeType);
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < show.getSlides().size(); i++) {
                text.append("# 幻灯片 ").append(i + 1).append('\n');
                show.getSlides().get(i).getShapes().stream()
                    .filter(XSLFTextShape.class::isInstance).map(XSLFTextShape.class::cast)
                    .forEach(shape -> text.append(shape.getText()).append('\n'));
            }
            return text.toString();
        } catch (Exception openXmlFailure) {
            try (HSLFSlideShow show = new HSLFSlideShow(new ByteArrayInputStream(bytes))) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < show.getSlides().size(); i++) {
                    text.append("# 幻灯片 ").append(i + 1).append('\n');
                    show.getSlides().get(i).getShapes().stream()
                        .filter(HSLFTextShape.class::isInstance).map(HSLFTextShape.class::cast)
                        .forEach(shape -> text.append(shape.getText()).append('\n'));
                }
                return text.toString();
            } catch (Exception binaryFailure) {
                throw new NonRetryableIngestionException("PowerPoint 文档解析失败", binaryFailure);
            }
        }
    }
}
