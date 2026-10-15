package com.forward.hrms.commons.reports;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads Jasper templates from the classpath, fills them with parameters
 * + a data source, and exports to PDF bytes.
 *
 * <p>Path resolution:
 * <ul>
 *   <li>Bare name (e.g. {@code "payslip"}) → {@code reports/payslip.jasper},
 *       precompiled.</li>
 *   <li>Path ending in {@code .jasper} → loaded as precompiled bytes.</li>
 *   <li>Path ending in {@code .jrxml} → compiled at first use, cached
 *       for the JVM's life. Useful while a service is still on the
 *       runtime-compile pattern; build-time compile is preferred long
 *       term.</li>
 * </ul>
 *
 * <p>Compiled templates are cached forever — they're tied to the running
 * JVM's classpath, so restart-on-deploy is enough invalidation.
 */
public final class JasperRenderer {

    private final Map<String, JasperReport> cache = new ConcurrentHashMap<>();

    /**
     * Render the named template to PDF bytes. {@code name} is the
     * template name without the {@code reports/} prefix or the
     * {@code .jasper} suffix (e.g. {@code "payslip"} resolves to
     * {@code reports/payslip.jasper}).
     */
    public byte[] renderPdf(String name, Map<String, Object> params, JRDataSource dataSource) {
        JasperReport report = load(resolvePath(name));
        Map<String, Object> p = params == null ? new HashMap<>() : new HashMap<>(params);
        JRDataSource ds = dataSource == null ? new JREmptyDataSource() : dataSource;
        try {
            JasperPrint print = JasperFillManager.fillReport(report, p, ds);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            exporter.exportReport();
            return out.toByteArray();
        } catch (JRException e) {
            throw new ReportException("Failed to render " + name, e);
        }
    }

    private static String resolvePath(String name) {
        if (name.startsWith("/") || name.endsWith(".jasper")) return name;
        return "reports/" + name + ".jasper";
    }

    private JasperReport load(String classpathPath) {
        return cache.computeIfAbsent(classpathPath, key -> {
            Resource resource = new ClassPathResource(key);
            if (!resource.exists()) {
                throw new ReportException("Jasper template not found on classpath: " + key, null);
            }
            try (InputStream in = resource.getInputStream()) {
                if (key.endsWith(".jrxml")) {
                    return JasperCompileManager.compileReport(in);
                }
                return (JasperReport) JRLoader.loadObject(in);
            } catch (JRException | IOException e) {
                throw new ReportException("Failed to load Jasper template " + key, e);
            }
        });
    }

    public static class ReportException extends RuntimeException {
        public ReportException(String message, Throwable cause) { super(message, cause); }
    }
}
