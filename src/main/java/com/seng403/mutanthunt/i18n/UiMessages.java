package com.seng403.mutanthunt.i18n;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exposes every key of {@code messages.properties} (resolved for the request locale) to the
 * browser, so that the vanilla-JS pages and the server share one message file.
 * Adding {@code messages_tr.properties} is enough to get a Turkish UI for Turkish browsers.
 */
@Component
public class UiMessages {

    private final MessageSource messageSource;
    private final Map<Locale, Map<String, String>> cache = new ConcurrentHashMap<>();

    public UiMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public Map<String, String> all(Locale locale) {
        return cache.computeIfAbsent(locale == null ? Locale.ENGLISH : locale, this::load);
    }

    public String get(String key, Locale locale) {
        return all(locale).getOrDefault(key, key);
    }

    private Map<String, String> load(Locale locale) {
        ResourceBundle base = ResourceBundle.getBundle("messages", Locale.ROOT);
        Map<String, String> result = new TreeMap<>();
        for (String key : base.keySet()) {
            // no args → Spring returns the raw pattern; {0} placeholders are filled in by JS
            result.put(key, messageSource.getMessage(key, null, base.getString(key), locale));
        }
        return Collections.unmodifiableMap(result);
    }
}
