package com.forward.hrms.commons.reports;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    @Test
    void writesPlainAndQuotedCellsAndEscapesQuotes() throws Exception {
        StringWriter sw = new StringWriter();
        CsvWriter w = new CsvWriter(sw);
        w.writeRow("code", "name", "email");
        w.writeRow("E1", "Saqib", "s@example.com");
        w.writeRow("E2", "Last, First", "x\"quote@example.com");
        w.writeRow("E3", "newline\nhere", null);
        w.flush();

        String expected = String.join("\r\n",
                "code,name,email",
                "E1,Saqib,s@example.com",
                "E2,\"Last, First\",\"x\"\"quote@example.com\"",
                "E3,\"newline\nhere\",") + "\r\n";
        assertEquals(expected, sw.toString());
    }

    @Test
    void writeRowAcceptsList() throws Exception {
        StringWriter sw = new StringWriter();
        CsvWriter w = new CsvWriter(sw);
        w.writeRow(Arrays.asList(1, 2, 3));
        w.flush();
        assertEquals("1,2,3\r\n", sw.toString());
    }
}
