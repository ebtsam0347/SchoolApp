package com.forward.hrms.commons.reports;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

/**
 * RFC 4180-ish CSV writer. Streams to a {@link Writer} so callers can
 * pair it with a {@code StreamingResponseBody} and avoid buffering the
 * whole report. Quotes a value when it contains comma, double-quote,
 * CR, or LF; escapes internal quotes by doubling.
 *
 * <p>Excel reads UTF-8 reliably only with a BOM — call {@link #writeBom}
 * first if the report will be opened in Excel directly. We keep the BOM
 * opt-in because pure-text consumers (CLI, scripts) hate it.
 */
public final class CsvWriter {

    private static final char[] BOM = { '﻿' };
    private static final String LINE_SEP = "\r\n";

    private final Writer out;

    public CsvWriter(Writer out) {
        this.out = out;
    }

    public void writeBom() throws IOException {
        out.write(BOM);
    }

    public void writeRow(List<?> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) out.write(',');
            out.write(escape(cells.get(i)));
        }
        out.write(LINE_SEP);
    }

    public void writeRow(Object... cells) throws IOException {
        writeRow(Arrays.asList(cells));
    }

    public void flush() throws IOException {
        out.flush();
    }

    private static String escape(Object cell) {
        if (cell == null) return "";
        String s = cell.toString();
        if (needsQuoting(s)) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static boolean needsQuoting(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || c == '"' || c == '\r' || c == '\n') return true;
        }
        return false;
    }
}
