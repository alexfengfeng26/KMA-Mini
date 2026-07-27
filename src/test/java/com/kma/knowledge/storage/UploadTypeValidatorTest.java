package com.kma.knowledge.storage;

import com.kma.common.exception.KmaException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadTypeValidatorTest {
    private final UploadTypeValidator validator = new UploadTypeValidator();

    @Test
    void acceptsMatchingSignatureAndCanonicalizesMimeType() {
        MockMultipartFile pdf = new MockMultipartFile("file", "policy.pdf", "application/pdf",
            "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile markdown = new MockMultipartFile("file", "readme.md", "text/markdown",
            "# KMA".getBytes(StandardCharsets.UTF_8));

        assertThat(validator.validate(pdf, pdf.getOriginalFilename())).isEqualTo("application/pdf");
        assertThat(validator.validate(markdown, markdown.getOriginalFilename())).isEqualTo("text/plain");
    }

    @Test
    void rejectsUnsupportedExtensionSignatureMismatchAndMimeMismatch() {
        MockMultipartFile executable = new MockMultipartFile("file", "run.exe", "application/octet-stream",
            new byte[]{0x4d, 0x5a});
        MockMultipartFile fakePdf = new MockMultipartFile("file", "fake.pdf", "application/pdf",
            "not-pdf".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile wrongMime = new MockMultipartFile("file", "note.txt", "application/pdf",
            "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(executable, executable.getOriginalFilename()))
            .isInstanceOf(KmaException.class).hasMessageContaining("不支持");
        assertThatThrownBy(() -> validator.validate(fakePdf, fakePdf.getOriginalFilename()))
            .isInstanceOf(KmaException.class).hasMessageContaining("不匹配");
        assertThatThrownBy(() -> validator.validate(wrongMime, wrongMime.getOriginalFilename()))
            .isInstanceOf(KmaException.class).hasMessageContaining("MIME");
    }

    @Test
    void rejectsOfficeArchiveThatExceedsExpansionLimit() throws Exception {
        var properties = new com.kma.knowledge.config.KnowledgeProperties();
        properties.getDocument().setMaxUncompressedBytes(32);
        UploadTypeValidator strictValidator = new UploadTypeValidator(properties);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("x".repeat(128).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MockMultipartFile bomb = new MockMultipartFile("file", "bomb.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes.toByteArray());

        assertThatThrownBy(() -> strictValidator.validate(bomb, bomb.getOriginalFilename()))
            .isInstanceOf(KmaException.class).hasMessageContaining("解压后大小");
    }
}
