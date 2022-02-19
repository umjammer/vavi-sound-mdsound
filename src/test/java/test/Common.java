
package test;

public class Common {

    public static String settingFilePath = "";

    public enum EnmRealChipType {
        YM2608(1),
        YM2151(2),
        YM2610(3),
        YM2203(4),
        YM2612(5),
        SN76489(7),
        SPPCM(42),
        C140(43),
        SEGAPCM(44);
        int v;
        EnmRealChipType(int v) {
            this.v = v;
        }
    }

    @FunctionalInterface
    public interface HexaFunction<T, U, V, W, X, Y, R> {
        R apply(T t, U u, V v, W w, X x, Y y);
    }

    @FunctionalInterface
    public interface HexaConsumer<T, U, V, W, X, Y> {
        void accept(T t, U u, V v, W w, X x, Y y);
    }
}
