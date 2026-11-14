package com.fraugz.filemanager;

import android.content.Context;

import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/**
 * Fast-scroll labels for a list ordered by name. A row normally gets its initial, but when more
 * rows than fit on a couple of screens start the same way, that shared start is skipped and the
 * label becomes what changes next: the date written in the name ("IMG_20261114_…"), or else the
 * first differing characters ("Factura_Enero" → "E", "informe_137" → "13").
 */
final class NameLabels {

    /** Longest run of rows that may share one label before it is refined. */
    private static final int MAX_RUN = 30;
    private static final int MAX_CHARS = 4;

    private NameLabels() {}

    static String[] compute(Context context, List<String> names) {
        String[] out = new String[names.size()];
        split(context, names, out, 0, names.size(), 0, 0);
        return out;
    }

    /** Rows [start, end) share their first {@code p} characters: group them by the one at {@code p}. */
    private static void split(Context context, List<String> names, String[] out,
                              int start, int end, int p, int from) {
        for (int i = start; i < end; ) {
            int key = charAt(names.get(i), p);
            int j = i + 1;
            while (j < end && charAt(names.get(j), p) == key) j++;
            if (key >= 0 && j - i > MAX_RUN) {
                refine(context, names, out, i, j, p + 1);
            } else {
                for (int k = i; k < j; k++) out[k] = text(names.get(k), from, p);
            }
            i = j;
        }
    }

    /** A run too long for a single label, sharing at least {@code shared} characters. */
    private static void refine(Context context, List<String> names, String[] out,
                               int start, int end, int shared) {
        String first = names.get(start);
        int p = first.length();
        for (int i = start + 1; i < end; i++) {
            String name = names.get(i);
            int c = shared;
            while (c < p && charAt(name, c) == charAt(first, c)) c++;
            p = c;
        }

        // The shared start may end in the middle of a number or date: label it from its beginning.
        int from = p;
        if (p < first.length() && isDigit(first.charAt(p))) {
            while (from > 0 && isDigit(first.charAt(from - 1))) from--;
        }
        // A very short shared start is kept as context ("IM", "IN").
        int textFrom = from <= 2 ? 0 : from;

        int dateAt = dateStart(first, from);
        if (dateAt >= 0 && labelDates(context, names, out, start, end, dateAt, textFrom, p)) return;
        split(context, names, out, start, end, p, textFrom);
    }

    /** Labels the run with the dates in its names. False, labelling nothing, if they all carry the same one. */
    private static boolean labelDates(Context context, List<String> names, String[] out,
                                      int start, int end, int at, int textFrom, int p) {
        int firstDate = dateAt(names.get(start), at);
        boolean varies = false;
        for (int i = start + 1; i < end && !varies; i++) {
            int date = dateAt(names.get(i), at);
            varies = date >= 0 && date != firstDate;
        }
        if (!varies) return false;

        int labelled = -1;
        String label = null;
        for (int i = start; i < end; i++) {
            int date = dateAt(names.get(i), at);
            if (date < 0) {
                out[i] = text(names.get(i), textFrom, p);
                continue;
            }
            if (date != labelled) {
                labelled = date;
                label = FastScrollRecyclerView.dateLabel(context, new GregorianCalendar(
                        date / 10000, date / 100 % 100 - 1, date % 100).getTimeInMillis());
            }
            out[i] = label;
        }
        return true;
    }

    /** Where a date begins, at {@code from} or shortly before it (the year of "2026-11-14"), or -1. */
    private static int dateStart(String name, int from) {
        for (int at = Math.max(0, from - 8); at <= from; at++) {
            if ((at == 0 || !isDigit(name.charAt(at - 1))) && dateAt(name, at) >= 0) return at;
        }
        return -1;
    }

    /** yyyyMMdd, yyyy-MM-dd, yyyy_MM_dd or yyyy.MM.dd at {@code at}, as the number yyyymmdd; -1 if none. */
    private static int dateAt(String name, int at) {
        int year = number(name, at, 4);
        if (year < 1980 || year > 2099) return -1;
        int i = at + 4;
        char separator = i < name.length() ? name.charAt(i) : 0;
        boolean separated = separator == '-' || separator == '_' || separator == '.';
        if (separated) i++;
        int month = number(name, i, 2);
        i += 2;
        if (separated) {
            if (i >= name.length() || name.charAt(i) != separator) return -1;
            i++;
        }
        int day = number(name, i, 2);
        if (month < 1 || month > 12 || day < 1 || day > 31) return -1;
        return year * 10000 + month * 100 + day;
    }

    private static int number(String name, int at, int digits) {
        if (at + digits > name.length()) return -1;
        int value = 0;
        for (int i = at; i < at + digits; i++) {
            if (!isDigit(name.charAt(i))) return -1;
            value = value * 10 + (name.charAt(i) - '0');
        }
        return value;
    }

    /** The name from {@code from} up to and including the character at {@code p}; the initial if that is blank. */
    private static String text(String name, int from, int p) {
        int end = p < name.length() ? name.offsetByCodePoints(p, 1) : name.length();
        String label = name.substring(Math.max(from, end - MAX_CHARS), end).trim();
        if (label.isEmpty() && !name.isEmpty()) {
            label = name.substring(0, name.offsetByCodePoints(0, 1));
        }
        return label.toUpperCase(Locale.getDefault());
    }

    /** Lower-cased, so the grouping matches the case-insensitive sort; -1 past the end of the name. */
    private static int charAt(String name, int index) {
        return index < name.length() ? Character.toLowerCase(name.charAt(index)) : -1;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
