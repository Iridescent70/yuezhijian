package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvTableParserTest {
    @Test
    void parsesBomQuotedCommasAndEscapedQuotes() {
        byte[] content = "\ufeff\"编号\",\"说明\"\r\n\"SVC-1\",\"含,逗号和\"\"引号\"\"\"\r\n"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(CsvTableParser.parse(content, 10))
                .containsExactly(
                        java.util.List.of("编号", "说明"),
                        java.util.List.of("SVC-1", "含,逗号和\"引号\""));
    }

    @Test
    void rejectsUnclosedQuotesAndExcessRows() {
        assertThatThrownBy(() -> CsvTableParser.parse("\"未闭合".getBytes(StandardCharsets.UTF_8), 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("未闭合");
        assertThatThrownBy(() -> CsvTableParser.parse("a\r\nb\r\nc\r\n".getBytes(StandardCharsets.UTF_8), 2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能超过");
    }
}
