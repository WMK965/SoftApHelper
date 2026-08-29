package com.xhy.xp.softaphelper;

public final class CidrUtils {
    private CidrUtils() {
    }

    public static boolean isValidIpv4Cidr(String value) {
        if (value == null) {
            return false;
        }

        String[] parts = value.trim().split("/", -1);
        if (parts.length != 2) {
            return false;
        }

        if (!isValidIpv4(parts[0])) {
            return false;
        }

        try {
            int prefixLength = Integer.parseInt(parts[1]);
            return prefixLength >= 1 && prefixLength <= 30;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String normalizeIpv4Cidr(String value) {
        if (!isValidIpv4Cidr(value)) {
            return AppSettings.DEFAULT_WIFI_CIDR;
        }
        return value.trim();
    }

    public static String getHostAddress(String cidr) {
        String normalized = normalizeIpv4Cidr(cidr);
        return normalized.substring(0, normalized.indexOf('/'));
    }

    private static boolean isValidIpv4(String value) {
        String[] segments = value.split("\\.", -1);
        if (segments.length != 4) {
            return false;
        }

        for (String segment : segments) {
            if (segment.length() == 0 || segment.length() > 3) {
                return false;
            }
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            int number = Integer.parseInt(segment);
            if (number < 0 || number > 255) {
                return false;
            }
        }
        return true;
    }
}
