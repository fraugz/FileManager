package com.fraugz.filemanager;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class NameLabelsTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private String[] labels(List<String> names) {
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return NameLabels.compute(context, names);
    }

    private String day(int month, int day) {
        return FastScrollRecyclerView.dateLabel(context,
                new GregorianCalendar(2021, month - 1, day).getTimeInMillis());
    }

    @Test
    public void longRunsOfDatedNamesAreLabelledByDate() {
        String[] patterns = {"IMG_2021%02d%02d_093000.jpg", "PXL_2021%02d%02d_093000123.jpg",
                "Screenshot_2021-%02d-%02d-10-00-00.png", "2021%02d%02d-acta.pdf"};
        for (String pattern : patterns) {
            List<String> names = new ArrayList<>();
            for (int d = 1; d <= 20; d++) {
                names.add(String.format(Locale.US, pattern, 10, d));
                names.add(String.format(Locale.US, pattern, 11, d));
            }
            String[] labels = labels(names);
            assertEquals(pattern, day(10, 1), labels[0]);
            assertEquals(pattern, day(10, 20), labels[19]);
            assertEquals(pattern, day(11, 20), labels[39]);
        }
    }

    @Test
    public void onlyTheLongRunIsRefinedInAMixedFolder() {
        List<String> names = new ArrayList<>(Arrays.asList("zeta.txt", "apunte.txt", "invoice.pdf"));
        for (int s = 0; s < 20; s++) {
            names.add(String.format(Locale.US, "IMG_20211114_0930%02d.jpg", s));
            names.add(String.format(Locale.US, "IMG_20211115_0930%02d.jpg", s));
        }
        String[] labels = labels(names);
        assertEquals("A", labels[0]);
        assertEquals(day(11, 14), labels[1]);
        assertEquals(day(11, 15), labels[40]);
        assertEquals("IN", labels[41]);
        assertEquals("Z", labels[42]);

        // The same names in a list short enough to scan keep their initial.
        assertEquals("I", labels(new ArrayList<>(names.subList(3, 33)))[0]);
    }

    @Test
    public void withoutVaryingDatesTheLabelIsWhatChanges() {
        List<String> invoices = new ArrayList<>();
        for (String month : new String[]{"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"}) {
            for (int n = 1; n <= 3; n++) invoices.add("Factura_" + month + "_" + n + ".pdf");
        }
        String[] labels = labels(invoices);
        assertEquals("E", labels[invoices.indexOf("Factura_Enero_1.pdf")]);
        assertEquals("M", labels[invoices.indexOf("Factura_Mayo_3.pdf")]);

        List<String> reports = new ArrayList<>();
        for (int n = 1; n <= 250; n++) reports.add(String.format(Locale.US, "informe_%03d.txt", n));
        labels = labels(reports);
        assertEquals("00", labels[4]);
        assertEquals("13", labels[136]);
        assertEquals("25", labels[249]);

        // Every photo is from the same day, so the date says nothing: the time is what changes.
        List<String> sameDay = new ArrayList<>();
        for (int h = 10; h < 50; h++) sameDay.add("IMG_20211114_" + h + "3000.jpg");
        labels = labels(sameDay);
        assertEquals("1", labels[0]);
        assertEquals("4", labels[39]);
    }
}
