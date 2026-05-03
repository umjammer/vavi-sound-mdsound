/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.util.compat;


import java.nio.file.Path;


/**
 * Util.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-05-01 nsano initial version <br>
 */
public class Util {

    /** */
    public static String changeExtension(String path, String extension) {
        return Path.of(path).getParent().resolve(getFileNameWithoutExtension(Path.of(path).getFileName().toString()) + extension).toString();
    }

    /** */
    public static String getFileNameWithoutExtension(String path) {
        String filename = Path.of(path).getFileName().toString();
        int p = filename.lastIndexOf('.');
        return filename.substring(0, p > 0 ? p : filename.length());
    }

    /** */
    public static String getExtension(String filename) {
        int p = filename.lastIndexOf('.');
        return p >= 0 ? filename.substring(p) : "";
    }

    /** */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
