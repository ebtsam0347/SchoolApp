package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;
import com.forward.hrms.payroll.enums.ReportColumnType;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRLineBox;
import net.sf.jasperreports.engine.JRStyle;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignTextElement;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import org.springframework.core.io.ClassPathResource;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PDF of the F-7 allowances calculation sheet. The allowance columns come
 * from the salary-component catalog, so {@code jasper/allowances-calculation.jrxml}
 * holds the letterhead, footer and sign-off blocks and this class lays out
 * the table into its columnHeader / detail / summary bands, one column per
 * report column, widths fitted to the Legal-landscape page. Totals are known before the
 * fill, so they go in as static text.
 */
final class AllowancesCalculationPdf {

    private static final String TEMPLATE = "jasper/allowances-calculation.jrxml";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    private static final Color TEXT = Color.decode("#1A1714");
    private static final Color ACCENT = Color.decode("#9A2D2A");
    private static final Color RULE = Color.decode("#E5DDD0");
    private static final Color TOTAL_TINT = Color.decode("#FFF4CC");

    /** Band geometry — must match the jrxml. */
    private static final int GROUP_ROW = 14;
    private static final int LABEL_ROW = 26;
    private static final int DETAIL_HEIGHT = 22;
    private static final int TOTAL_HEIGHT = 20;

    /** Widths of the employee columns; the allowance columns share what's left. */
    private static final Map<String, Integer> FIXED_WIDTH = Map.of(
            AccountsReportColumnDto.SR_NO, 22,
            AccountsReportColumnDto.CARD_NO, 32,
            AccountsReportColumnDto.EMPLOYEE_NAME, 90,
            AccountsReportColumnDto.DEPARTMENT, 66,
            AccountsReportColumnDto.SECTION, 60,
            AccountsReportColumnDto.DESIGNATION, 72,
            AccountsReportColumnDto.GRADE, 30,
            AccountsReportColumnDto.DATE_OF_JOINING, 48,
            "signature", 62);
    /** How far each employee column may give way when many allowance columns need the room. */
    private static final Map<String, Integer> MIN_WIDTH = Map.of(
            AccountsReportColumnDto.EMPLOYEE_NAME, 72,
            AccountsReportColumnDto.DEPARTMENT, 54,
            AccountsReportColumnDto.SECTION, 48,
            AccountsReportColumnDto.DESIGNATION, 58);
    /** Amount width wanted before the employee columns give way; below it amounts use a smaller font. */
    private static final int COMFORT_AMOUNT_WIDTH = 52;
    private static final int MIN_AMOUNT_WIDTH = 46;
    /** Rough average glyph width of bold SansSerif, in ems — for fitting a header's longest word. */
    private static final float BOLD_EM = 0.6f;

    private AllowancesCalculationPdf() {}

    /** One laid-out column: its report column, x, width and the data-source field it reads. */
    private record Slot(AccountsReportColumnDto column, int x, int width, String field) {

        boolean amount() {
            return column.type() == ReportColumnType.AMOUNT;
        }

        boolean total() {
            return column.key().equals(AccountsReportColumnDto.TOTAL);
        }
    }

    static byte[] render(AccountsReportService.Computed c, Map<String, Object> params) {
        try {
            JasperDesign design = load();
            List<Slot> slots = layout(c.shownColumns(), design.getColumnWidth());
            for (Slot s : slots) {
                JRDesignField f = new JRDesignField();
                f.setName(s.field());
                f.setValueClass(String.class);
                design.addField(f);
            }
            header((JRDesignBand) design.getColumnHeader(), slots);
            detail((JRDesignBand) ((JRDesignSection) design.getDetailSection()).getBands()[0], slots,
                    design.getStylesMap().get("row.zebra"), design.getStylesMap().get("cell.total"));
            totals((JRDesignBand) design.getSummary(), slots, c);

            JasperReport report = JasperCompileManager.compileReport(design);
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(params),
                    new JRMapCollectionDataSource(rows(c, slots)));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException | IOException e) {
            throw new JasperRenderer.ReportException("Failed to render " + TEMPLATE, e);
        }
    }

    private static JasperDesign load() throws IOException, JRException {
        try (InputStream in = new ClassPathResource(TEMPLATE).getInputStream()) {
            return JRXmlLoader.load(in);
        }
    }

    /**
     * Fixed widths for employee columns; amount columns split the rest,
     * remainder to the name. With many allowances the name / department /
     * section / designation columns give way (down to {@link #MIN_WIDTH})
     * so amounts keep a readable width.
     */
    private static List<Slot> layout(List<AccountsReportColumnDto> columns, int pageWidth) {
        Map<String, Integer> widths = new HashMap<>();
        int fixed = 0;
        int amounts = 0;
        for (AccountsReportColumnDto col : columns) {
            if (col.type() == ReportColumnType.AMOUNT) {
                amounts++;
            } else {
                int w = FIXED_WIDTH.getOrDefault(col.key(), 50);
                widths.put(col.key(), w);
                fixed += w;
            }
        }
        int deficit = amounts * COMFORT_AMOUNT_WIDTH - (pageWidth - fixed);
        for (Map.Entry<String, Integer> e : MIN_WIDTH.entrySet()) {
            Integer w = widths.get(e.getKey());
            if (deficit <= 0 || w == null) continue;
            int give = Math.min(deficit, w - e.getValue());
            widths.put(e.getKey(), w - give);
            fixed -= give;
            deficit -= give;
        }
        int amountWidth = amounts == 0 ? 0 : (pageWidth - fixed) / amounts;
        int spare = pageWidth - fixed - amountWidth * amounts;

        List<Slot> out = new ArrayList<>(columns.size());
        int x = 0;
        int a = 0;
        for (AccountsReportColumnDto col : columns) {
            boolean amount = col.type() == ReportColumnType.AMOUNT;
            int w = amount ? amountWidth : widths.get(col.key());
            if (col.key().equals(AccountsReportColumnDto.EMPLOYEE_NAME)) w += spare;
            String field = amount && !col.key().equals(AccountsReportColumnDto.TOTAL) ? "a" + a++ : col.key();
            out.add(new Slot(col, x, w, field));
            x += w;
        }
        return out;
    }

    /** Group row (Basic Information / Payables / Signature) over the column labels. */
    private static void header(JRDesignBand band, List<Slot> slots) {
        int basicEnd = 0;
        int payStart = -1;
        int payEnd = 0;
        for (Slot s : slots) {
            if (s.amount()) {
                if (payStart < 0) payStart = s.x();
                payEnd = s.x() + s.width();
            } else if (payStart < 0) {
                basicEnd = s.x() + s.width();
            }
        }
        group(band, 0, basicEnd, "Basic Information");
        if (payStart >= 0) group(band, payStart, payEnd - payStart, "Payables");
        Slot last = slots.getLast();
        if (!last.amount()) group(band, last.x(), last.width(), last.column().key().equals("signature")
                ? "Signature" : "");

        for (Slot s : slots) {
            JRDesignStaticText t = new JRDesignStaticText();
            place(t, s.x(), GROUP_ROW, s.width(), LABEL_ROW);
            t.setText(s.column().key().equals("signature") ? "Employee Signature" : s.column().label());
            font(t, labelSize(t.getText(), s.width() - 4), true);
            t.setHorizontalTextAlign(s.amount() ? HorizontalTextAlignEnum.RIGHT : HorizontalTextAlignEnum.LEFT);
            if (s.column().key().equals(AccountsReportColumnDto.SR_NO)) {
                t.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            }
            t.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            grid(t.getLineBox());
            t.getLineBox().setLeftPadding(2);
            t.getLineBox().setRightPadding(2);
            t.getLineBox().getBottomPen().setLineWidth(1.5f);
            t.getLineBox().getBottomPen().setLineColor(ACCENT);
            if (s.total()) {
                t.setMode(ModeEnum.OPAQUE);
                t.setBackcolor(TOTAL_TINT);
            }
            band.addElement(t);
        }
    }

    /** 6.5pt, or smaller (not below 5pt) so the label's longest word fits the column without breaking. */
    private static float labelSize(String label, int width) {
        int longest = 1;
        for (String word : label.split("\\s+")) longest = Math.max(longest, word.length());
        return Math.max(5f, Math.min(6.5f, width / (longest * BOLD_EM)));
    }

    private static void group(JRDesignBand band, int x, int width, String label) {
        JRDesignStaticText t = new JRDesignStaticText();
        place(t, x, 0, width, GROUP_ROW);
        t.setText(label);
        font(t, 8, true);
        t.setForecolor(Color.WHITE);
        t.setMode(ModeEnum.OPAQUE);
        t.setBackcolor(ACCENT);
        t.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
        t.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
        t.getLineBox().getPen().setLineWidth(0.5f);
        t.getLineBox().getPen().setLineColor(Color.WHITE);
        band.addElement(t);
    }

    private static void detail(JRDesignBand band, List<Slot> slots, JRStyle zebra, JRStyle totalStyle) {
        for (Slot s : slots) {
            JRDesignTextField t = new JRDesignTextField();
            place(t, s.x(), 0, s.width(), DETAIL_HEIGHT);
            t.setExpression(new JRDesignExpression("$F{" + s.field() + "}"));
            t.setBlankWhenNull(true);
            t.setStyle(s.total() ? totalStyle : zebra);
            boolean bold = s.total() || s.column().key().equals(AccountsReportColumnDto.CARD_NO);
            font(t, s.amount() && s.width() < MIN_AMOUNT_WIDTH ? 6 : 7, bold);
            t.setHorizontalTextAlign(s.amount() ? HorizontalTextAlignEnum.RIGHT
                    : s.column().key().equals(AccountsReportColumnDto.SR_NO) ? HorizontalTextAlignEnum.CENTER
                    : HorizontalTextAlignEnum.LEFT);
            t.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            grid(t.getLineBox());
            band.addElement(t);
        }
    }

    /** "TOTAL — N employee(s)" under the employee columns, the grand total under each amount column. */
    private static void totals(JRDesignBand band, List<Slot> slots, AccountsReportService.Computed c) {
        int labelEnd = slots.stream().filter(Slot::amount).mapToInt(Slot::x).findFirst().orElse(0);
        JRDesignStaticText label = new JRDesignStaticText();
        place(label, 0, 0, labelEnd, TOTAL_HEIGHT);
        label.setText("TOTAL — " + c.totalEmployees() + " employee" + (c.totalEmployees() == 1 ? "" : "s"));
        font(label, 8, true);
        label.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        label.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
        totalBox(label.getLineBox());
        band.addElement(label);

        for (Slot s : slots) {
            if (!s.amount()) continue;
            JRDesignStaticText t = new JRDesignStaticText();
            place(t, s.x(), 0, s.width(), TOTAL_HEIGHT);
            t.setText(money(c.totals().get(s.column().key())));
            font(t, s.width() < MIN_AMOUNT_WIDTH ? 6 : 7, true);
            t.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
            t.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            totalBox(t.getLineBox());
            if (s.total()) {
                t.setMode(ModeEnum.OPAQUE);
                t.setBackcolor(TOTAL_TINT);
                t.setForecolor(ACCENT);
            }
            band.addElement(t);
        }
    }

    /** The report's rows as pre-formatted strings keyed by slot field. */
    private static List<Map<String, ?>> rows(AccountsReportService.Computed c, List<Slot> slots) {
        List<Map<String, ?>> out = new ArrayList<>(c.rows().size());
        for (Map<String, Object> row : c.rows()) {
            Map<String, Object> m = new HashMap<>();
            for (Slot s : slots) {
                Object v = row.get(s.column().key());
                m.put(s.field(), switch (v) {
                    case null -> null;
                    case BigDecimal b -> money(b);
                    case LocalDate d -> DATE.format(d);
                    default -> v.toString();
                });
            }
            out.add(m);
        }
        return out;
    }

    /** Whole rupees without decimals (the common case), otherwise two. */
    static String money(BigDecimal v) {
        if (v == null) return "0";
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        return new DecimalFormat(whole ? "#,##0" : "#,##0.00").format(v);
    }

    private static void place(JRDesignElement e, int x, int y, int w, int h) {
        e.setX(x);
        e.setY(y);
        e.setWidth(w);
        e.setHeight(h);
        e.setForecolor(TEXT);
    }

    private static void font(JRDesignTextElement t, float size, boolean bold) {
        t.setFontName("SansSerif");
        t.setFontSize(size);
        t.setBold(bold);
    }

    private static void grid(JRLineBox box) {
        box.getPen().setLineWidth(0.5f);
        box.getPen().setLineColor(RULE);
        box.setLeftPadding(3);
        box.setRightPadding(3);
    }

    private static void totalBox(JRLineBox box) {
        box.getTopPen().setLineWidth(1.5f);
        box.getTopPen().setLineColor(ACCENT);
        box.getBottomPen().setLineWidth(1.5f);
        box.getBottomPen().setLineColor(ACCENT);
        box.setRightPadding(3);
    }
}
