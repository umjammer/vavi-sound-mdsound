
package mdsound.fmvgen.effect;

public class ReversePhase {

    public static int[][][] ssg;
    public static int[][][] fm;
    public static int[][] rhythm;
    public static int[][] adpcmA;
    public static int[][] adpcm;

    static {
        ssg = new int[][][] {
                new int[][] {new int[2], new int[2], new int[2]},
                new int[][] {new int[2], new int[2], new int[2]},
                new int[][] {new int[2], new int[2], new int[2]},
                new int[][] {new int[2], new int[2], new int[2]}
        };
        fm = new int[][][] {
                new int[][] {new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]},
                new int[][] {new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]}
        };
        rhythm = new int[][] {new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]};
        adpcmA = new int[][] {new int[2], new int[2], new int[2], new int[2], new int[2], new int[2]};
        adpcm = new int[][] {new int[2], new int[2], new int[2]};

        for (int i = 0; i < 6; i++) {
            ssg[0][i / 2][i % 2] = 1;
            ssg[1][i / 2][i % 2] = 1;
            ssg[2][i / 2][i % 2] = 1;
            ssg[3][i / 2][i % 2] = 1;

            fm[0][i / 2][i % 2] = 1;
            fm[0][i / 2 + 3][i % 2] = 1;
            fm[1][i / 2][i % 2] = 1;
            fm[1][i / 2 + 3][i % 2] = 1;

            rhythm[i / 2][i % 2] = 1;
            rhythm[i / 2 + 3][i % 2] = 1;

            adpcmA[i / 2][i % 2] = 1;
            adpcmA[i / 2 + 3][i % 2] = 1;

            adpcm[i / 2][i % 2] = 1;
        }
    }

    public void setReg(int adr, byte data) {
        switch (adr) {
        case 0:// $CC
            for (int i = 0; i < 6; i++)
                ssg[0][i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        case 1:// $CD
            for (int i = 0; i < 6; i++)
                ssg[1][i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        case 2:// $CE
            for (int i = 0; i < 6; i++)
                ssg[2][i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        case 3:// $CF
            for (int i = 0; i < 6; i++)
                ssg[3][i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;

        case 4:// $D0
            for (int i = 0; i < 6; i++)
                fm[0][i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        case 5:// $D1
            for (int i = 0; i < 6; i++)
                fm[0][i / 2 + 3][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        case 6:// $D2
            for (int i = 0; i < 6; i++)
                fm[1][i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        case 7:// $D3
            for (int i = 0; i < 6; i++)
                fm[1][i / 2 + 3][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;

        case 8:// $D4
            for (int i = 0; i < 6; i++)
                rhythm[i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        case 9:// $D5
            for (int i = 0; i < 6; i++)
                rhythm[i / 2 + 3][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;

        case 10:// $D6
            for (int i = 0; i < 6; i++)
                adpcmA[i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        case 11:// $D7
            for (int i = 0; i < 6; i++)
                adpcmA[i / 2 + 3][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;

        case 12:// $D8
            for (int i = 0; i < 6; i++)
                adpcm[i / 2][(i + 1) & 1] = (data & (1 << i)) != 0 ? -1 : 1;
            break;
        }
    }
}
