
package test;

public class Common {

    public static String settingFilePath = "";

    public enum RealChipType {
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
        RealChipType(int v) {
            this.v = v;
        }
    }
}
