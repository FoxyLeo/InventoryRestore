package com.foxy.inventoryRestore.util;

import java.time.format.DateTimeFormatter;

public final class DateFormats {

    private DateFormats() {
    }

    public static final DateTimeFormatter RECORD_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss");
    public static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
}
