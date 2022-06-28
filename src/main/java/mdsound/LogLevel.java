
package mdsound;

public enum LogLevel {
    FATAL(1),
    ERROR(2),
    WARNING(4),
    INFO(8),
    DEBUG(16),
    TRACE(32);
    final int v;
    LogLevel(int v) {
        this.v = v;
    }
}
