
package test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import dotnet4j.io.File;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileStream;
import dotnet4j.io.Path;
import dotnet4j.io.StreamWriter;


public class Log {
//#if DEBUG
//        public static boolean debug = true;
//#else
    public static boolean debug = false;

//#endif
    public static String path = "";

    public static boolean consoleEchoBack = false;

    private static Charset encoding = StandardCharsets.UTF_8;

    public static void ForcedWrite(String msg) {
        try {
            if (path == "") {
                String fullPath = Common.settingFilePath;
                path = Path.combine(fullPath, System.getProperty("cntLogFilename"));
                if (File.exists(path))
                    File.delete(path);
            }
            String timefmt = LocalDateTime.now().toString();

            try (StreamWriter writer = new StreamWriter(new FileStream(path, FileMode.Append), encoding)) {
                writer.writeLine(timefmt + msg);
                if (consoleEchoBack)
                    System.err.println(timefmt + msg);
            }
        } catch (Exception e) {
        }
    }

    public static void ForcedWrite(Exception e) {
        try {
            if (path == "") {
                String fullPath = Common.settingFilePath;
                path = Path.combine(fullPath, System.getProperty("cntLogFilename"));
                if (File.exists(path))
                    File.delete(path);
            }
            String timefmt = LocalDateTime.now().toString();

            try (StreamWriter writer = new StreamWriter(new FileStream(path, FileMode.Append), encoding)) {
                String msg = String.format("%s %s %s %s",
                                           e.getClass().getName(),
                                           e.getMessage(),
                                           e.getCause(),
                                           e.getStackTrace());
                Throwable ie = e;
                while (ie.getCause() != null) {
                    ie = ie.getCause();
                    msg += String.format("%s %s %s %s",
                                         ie.getClass().getName(),
                                         ie.getMessage(),
                                         ie.getCause(),
                                         ie.getStackTrace());
                }

                writer.writeLine(timefmt + msg);
                if (consoleEchoBack)
                    System.err.println(timefmt + msg);
            }
        } catch (Exception ex) {
        }
    }

    public static void Write(String msg) {
        if (!debug)
            return;

        try {
            if (path == "") {
                String fullPath = Common.settingFilePath;
                path = Path.combine(fullPath, System.getProperty("cntLogFilename"));
                if (File.exists(path))
                    File.delete(path);
            }
            String timefmt = LocalDateTime.now().toString();

            try (StreamWriter writer = new StreamWriter(new FileStream(path, FileMode.Append), encoding)) {
                // writer.WriteLine(timefmt + msg);
                writer.writeLine(msg);
                if (consoleEchoBack)
                    System.err.println(timefmt + msg);
            }
        } catch (Exception e) {
        }
    }

}
