package com.forward.hrms.auth.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserAgentParser {

    public enum DeviceFamily { MOBILE, TABLET, DESKTOP, UNKNOWN }

    public record NameAndVersion(String name, String version) {}

    public record Parsed(DeviceFamily family, NameAndVersion browser, NameAndVersion os) {}

    private UserAgentParser() {}

    public static String describe(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown device";
        String browser = browserName(ua);
        String os = osName(ua);
        if (browser == null && os == null) return trim(ua);
        if (browser == null) return os;
        if (os == null) return browser;
        return browser + " on " + os;
    }

    public static Parsed parse(String ua) {
        if (ua == null || ua.isBlank()) return new Parsed(DeviceFamily.UNKNOWN, null, null);
        String bName = browserName(ua);
        String oName = osName(ua);
        return new Parsed(
                family(ua),
                bName == null ? null : new NameAndVersion(bName, browserVersion(ua, bName)),
                oName == null ? null : new NameAndVersion(oName, osVersion(ua, oName))
        );
    }

    private static String browserName(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("Chrome/") && !ua.contains("Chromium")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/") && ua.contains("Version/")) return "Safari";
        return null;
    }

    private static String osName(String ua) {
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return null;
    }

    private static DeviceFamily family(String ua) {
        // iPad before iPhone — iPad UAs contain "Mobile" too.
        if (ua.contains("iPad") || ua.contains("Tablet")) return DeviceFamily.TABLET;
        if (ua.contains("Mobile") || ua.contains("iPhone") || ua.contains("Android")) return DeviceFamily.MOBILE;
        if (ua.contains("Macintosh") || ua.contains("Windows") || ua.contains("Linux")) return DeviceFamily.DESKTOP;
        return DeviceFamily.UNKNOWN;
    }

    private static String browserVersion(String ua, String name) {
        // Match "Name/X.Y.Z" — Safari is the special case (Version/X.Y on Safari/Z).
        if ("Safari".equals(name)) return matchFirst(ua, "Version/([0-9.]+)");
        return switch (name) {
            case "Edge"    -> matchFirst(ua, "Edg/([0-9.]+)");
            case "Opera"   -> matchFirst(ua, "OPR/([0-9.]+)");
            case "Chrome"  -> matchFirst(ua, "Chrome/([0-9.]+)");
            case "Firefox" -> matchFirst(ua, "Firefox/([0-9.]+)");
            default        -> null;
        };
    }

    private static String osVersion(String ua, String name) {
        return switch (name) {
            case "Windows" -> matchFirst(ua, "Windows NT ([0-9.]+)");
            case "macOS"   -> {
                String v = matchFirst(ua, "Mac OS X ([0-9_.]+)");
                yield v == null ? null : v.replace('_', '.');
            }
            case "Android" -> matchFirst(ua, "Android ([0-9.]+)");
            case "iOS"     -> {
                String v = matchFirst(ua, "OS ([0-9_]+) like Mac OS X");
                yield v == null ? null : v.replace('_', '.');
            }
            default        -> null;
        };
    }

    private static String matchFirst(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String trim(String s) {
        return s.length() <= 128 ? s : s.substring(0, 128);
    }
}
