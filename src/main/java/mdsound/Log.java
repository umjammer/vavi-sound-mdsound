
package mdsound;

import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class Log {
    public static BiConsumer<LogLevel, String> writeLine = null;
    public static LogLevel level = LogLevel.INFO;
    public static int off = LogLevel.WARNING.v;
    public static Consumer<String> writeMethod;

    public static void writeLine(LogLevel level, String msg) {
        // if ((off & (int)level) != 0) return;
        // return;
        if (level.v <= Log.level.v) {
            if (writeMethod != null)
                writeMethod.accept(String.format("[%-7s] %s}", level, msg));
            else
                writeLine.accept(level, msg);
        }
    }
}
