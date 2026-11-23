package com.cameron.lumix;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The runtimes' safety guard (core/js/lux-camera.js denyReason), applied again to every camera request native code
 * sends, since the URLs come from drivers. Same rules: a request is refused when a word of its path or query (split on
 * anything but letters, digits and _) starts with "del" or contains "delete", or starts with "format", "setsetting" or
 * "setinfo". Anything but GET/HEAD must be a SOAP Browse or Search with no write action in the body.
 */
final class Guard {
    private Guard() { }

    static final class Refused extends IOException {
        Refused(String why) { super("Refused to send: " + why); }
    }

    private static final Pattern SPLIT = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern SOAP_ALLOWED = Pattern.compile("#(Browse|Search)\"?$");
    private static final Pattern SOAP_DENIED_BODY = Pattern.compile("<\\s*(?:[\\w-]+:)?(Destroy|Delete|Update|Create|Import|Export|Move)\\w*[\\s/>]", Pattern.CASE_INSENSITIVE);

    static String denyReason(String url, String method, String body, String soapAction) {
        String target = String.valueOf(url).replaceFirst("(?i)^[a-z]+://[^/]*", "");
        try { target = URLDecoder.decode(target, StandardCharsets.UTF_8.name()); } catch (Exception ignored) { }
        for (String w : SPLIT.split(target.toLowerCase(Locale.ROOT))) {
            if (w.isEmpty()) continue;
            if (w.startsWith("del") || w.contains("delete") || w.startsWith("format") || w.startsWith("setsetting") || w.startsWith("setinfo")) {
                return "\"" + w + "\" is on the denylist";
            }
        }
        if (method != null && !method.equals("GET") && !method.equals("HEAD")) {
            if (soapAction == null || !SOAP_ALLOWED.matcher(soapAction).find()) return method + " is only allowed for a SOAP Browse or Search";
            if (body != null && SOAP_DENIED_BODY.matcher(body).find()) return "the SOAP body has a write action";
        }
        return null;
    }

    static void check(String url) throws Refused { check(url, "GET", null, null); }

    static void check(String url, String method, String body, String soapAction) throws Refused {
        String why = denyReason(url, method, body, soapAction);
        if (why != null) throw new Refused(why);
    }
}
