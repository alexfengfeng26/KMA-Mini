package com.kma.knowledge.rag.extract;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentExtractionQualityTest {

    @Test
    void extractsChineseContentFromOfficeAndHtmlDocuments() throws Exception {
        assertThat(new ExcelDocumentExtractor().extract(new ByteArrayInputStream(excel())))
            .contains("工作表：政策清单", "事项名称", "党员教育");
        assertThat(new WordDocumentExtractor().extract(new ByteArrayInputStream(word())))
            .contains("知识库建设质量基线");
        assertThat(new PowerPointDocumentExtractor().extract(new ByteArrayInputStream(powerPoint())))
            .contains("幻灯片 1", "党建知识服务");

        String html = "<html><style>hidden</style><body><h1>办事指南</h1><script>secret</script><p>材料齐全</p></body></html>";
        assertThat(new HtmlDocumentExtractor().extract(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))))
            .contains("办事指南", "材料齐全").doesNotContain("hidden", "secret");
    }

    @Test
    void imageWithoutConfiguredOcrHasExplicitNonRetryableState() {
        ImageDocumentExtractor extractor = new ImageDocumentExtractor(List.of());

        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(new byte[]{1, 2, 3})))
            .isInstanceOf(OcrRequiredException.class).hasMessageContaining("需要配置 OCR");
    }

    private byte[] excel() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("政策清单");
            sheet.createRow(0).createCell(0).setCellValue("事项名称");
            sheet.createRow(1).createCell(0).setCellValue("党员教育");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] word() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("知识库建设质量基线");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] powerPoint() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            show.createSlide().createTextBox().setText("党建知识服务");
            show.write(output);
            return output.toByteArray();
        }
    }
}
