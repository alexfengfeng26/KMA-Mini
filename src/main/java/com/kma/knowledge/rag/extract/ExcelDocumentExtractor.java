package com.kma.knowledge.rag.extract;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class ExcelDocumentExtractor implements DocumentExtractor {
    @Override
    public boolean supports(String mimeType) {
        return "application/vnd.ms-excel".equals(mimeType)
            || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(mimeType);
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        DataFormatter formatter = new DataFormatter();
        StringBuilder text = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            for (Sheet sheet : workbook) {
                text.append("# 工作表：").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    boolean first = true;
                    for (var cell : row) {
                        if (!first) text.append(" | ");
                        text.append(formatter.formatCellValue(cell));
                        first = false;
                    }
                    text.append('\n');
                }
            }
            return text.toString();
        } catch (Exception ex) {
            throw new NonRetryableIngestionException("Excel 文档解析失败", ex);
        }
    }
}
