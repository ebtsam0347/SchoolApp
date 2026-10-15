package com.forward.hrms.commons.reports;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

import java.io.IOException;
import java.io.OutputStream;

/**
 * One styled sheet in the FS-HRMS print identity: company name, report
 * title, filter caption and generated-by line on top; ink (#1A1714)
 * header row with white bold text; zebra rows (#F8F4ED) with hairline
 * borders (#E5DDD0); frozen header and autofilter. Streaming (SXSSF), so
 * the whole company fits in a few MB of heap. Shared by the employee and
 * payroll list reports so every Excel export looks the same.
 *
 * <pre>
 * try (XlsxReport wb = new XlsxReport("Employees")) {
 *     wb.header(company, "Employee List", filters, generated, columns, widths);
 *     wb.row(values...);
 *     wb.write(out);
 * }
 * </pre>
 */
public final class XlsxReport implements AutoCloseable {

    private static final byte[] INK = rgb(0x1A1714);
    private static final byte[] ACCENT = rgb(0x9A2D2A);
    private static final byte[] ZEBRA = rgb(0xF8F4ED);
    private static final byte[] RULE = rgb(0xE5DDD0);
    private static final byte[] MUTED = rgb(0x8C8175);

    private final SXSSFWorkbook wb = new SXSSFWorkbook(200);
    private final SXSSFSheet sheet;
    private final XSSFCellStyle company, title, caption, head, text, textZebra, number, numberZebra, total;
    private int rowIdx;
    private int columns;
    private int headerRow;
    private int dataRows;

    public XlsxReport(String sheetName) {
        wb.setCompressTempFiles(true);
        sheet = wb.createSheet(sheetName);
        company = style(font(14, true, ACCENT), null, false);
        title = style(font(12, true, INK), null, false);
        caption = style(font(9, false, MUTED), null, false);
        head = style(font(10, true, new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}), INK, true);
        head.setVerticalAlignment(VerticalAlignment.CENTER);
        head.setWrapText(true);
        text = style(font(10, false, INK), null, true);
        textZebra = style(font(10, false, INK), ZEBRA, true);
        number = style(font(10, false, INK), null, true);
        number.setAlignment(HorizontalAlignment.RIGHT);
        numberZebra = style(font(10, false, INK), ZEBRA, true);
        numberZebra.setAlignment(HorizontalAlignment.RIGHT);
        total = style(font(10, true, INK), ZEBRA, true);
    }

    /** Title block + column header row; widths are in characters. */
    public void header(String companyName, String reportTitle, String filters, String generated,
                String[] cols, int[] widths) {
        columns = cols.length;
        cell(sheet.createRow(rowIdx++), 0, companyName, company);
        cell(sheet.createRow(rowIdx++), 0, reportTitle, title);
        if (filters != null && !filters.isBlank()) cell(sheet.createRow(rowIdx++), 0, filters, caption);
        cell(sheet.createRow(rowIdx++), 0, generated, caption);
        rowIdx++;
        headerRow = rowIdx;
        Row h = sheet.createRow(rowIdx++);
        h.setHeightInPoints(20);
        for (int c = 0; c < cols.length; c++) {
            cell(h, c, cols[c], head);
            sheet.setColumnWidth(c, Math.min(255, widths[c]) * 256);
        }
        sheet.createFreezePane(0, rowIdx);
    }

    /** One data row; Numbers are right-aligned numeric cells, everything else text. */
    public void row(Object... values) {
        boolean zebra = dataRows++ % 2 == 1;
        Row r = sheet.createRow(rowIdx++);
        for (int c = 0; c < values.length; c++) {
            Object v = values[c];
            if (v instanceof Number n) {
                Cell cell = r.createCell(c);
                cell.setCellValue(n.doubleValue());
                cell.setCellStyle(zebra ? numberZebra : number);
            } else {
                cell(r, c, v == null ? "" : v.toString(), zebra ? textZebra : text);
            }
        }
    }

    /** Bold footer row (e.g. "Total: 42"). */
    public void totalRow(Object... values) {
        Row r = sheet.createRow(rowIdx++);
        for (int c = 0; c < columns; c++) {
            Object v = c < values.length ? values[c] : null;
            if (v instanceof Number n) {
                Cell cell = r.createCell(c);
                cell.setCellValue(n.doubleValue());
                cell.setCellStyle(total);
            } else {
                cell(r, c, v == null ? "" : v.toString(), total);
            }
        }
    }

    public int dataRows() {
        return dataRows;
    }

    public void write(OutputStream out) throws IOException {
        if (dataRows > 0) {
            sheet.setAutoFilter(new CellRangeAddress(headerRow, headerRow + dataRows, 0, columns - 1));
        }
        wb.write(out);
    }

    @Override
    public void close() throws IOException {
        wb.dispose();
        wb.close();
    }

    // ---------- styling ----------

    private XSSFFont font(int size, boolean bold, byte[] color) {
        XSSFFont f = (XSSFFont) wb.createFont();
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) size);
        f.setBold(bold);
        f.setColor(new XSSFColor(color, null));
        return f;
    }

    private XSSFCellStyle style(Font font, byte[] fill, boolean bordered) {
        XSSFCellStyle s = (XSSFCellStyle) wb.createCellStyle();
        s.setFont(font);
        if (fill != null) {
            s.setFillForegroundColor(new XSSFColor(fill, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (bordered) {
            XSSFColor rule = new XSSFColor(RULE, null);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBottomBorderColor(rule);
            s.setBorderTop(BorderStyle.THIN);
            s.setTopBorderColor(rule);
            s.setBorderLeft(BorderStyle.THIN);
            s.setLeftBorderColor(rule);
            s.setBorderRight(BorderStyle.THIN);
            s.setRightBorderColor(rule);
            s.setVerticalAlignment(VerticalAlignment.TOP);
        }
        return s;
    }

    private static void cell(Row r, int idx, String value, CellStyle style) {
        Cell c = r.createCell(idx);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static byte[] rgb(int hex) {
        return new byte[] {(byte) (hex >> 16), (byte) (hex >> 8), (byte) hex};
    }
}
