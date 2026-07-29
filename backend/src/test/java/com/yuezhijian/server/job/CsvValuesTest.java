package com.yuezhijian.server.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvValuesTest {
    @Test
    void quotesSpecialCharactersAndNeutralizesSpreadsheetFormulas() {
        assertThat(CsvValues.cell("普通,内容\n含换行")).isEqualTo("\"普通,内容\n含换行\"");
        assertThat(CsvValues.cell("=HYPERLINK(\"bad\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"bad\"\")\"");
        assertThat(CsvValues.cell("@SUM(A1:A2)")).startsWith("\"'@");
    }
}
