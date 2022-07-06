
package mdsound;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.IntStream;

import dotnet4j.io.FileMode;
import dotnet4j.io.FileStream;
import dotnet4j.io.MemoryStream;
import dotnet4j.io.Stream;
import dotnet4j.io.StreamWriter;


public class Common {

    public static byte[] toByteArray(List<Byte> o) {
        byte[] a = new byte[o.size()];
        IntStream.range(0, o.size()).forEach(i -> a[i] = o.get(i));
        return a;
    }

    public static int[] toIntArray(List<Integer> o) {
        int[] a = new int[o.size()];
        IntStream.range(0, o.size()).forEach(i -> a[i] = o.get(i));
        return a;
    }

    public static short[][] readArrays(String name) {
        Scanner s = new Scanner(Common.class.getResourceAsStream(name));
        List<String> ls = new ArrayList<>();
        while (s.hasNextLine()) ls.add(s.nextLine());
        s.close();
        short[][] d = new short[ls.size()][];
        IntStream.range(0, ls.size()).forEach(i -> {
            String[] ps = ls.get(i).trim().split("[\\s,]+");
            d[i] = new short[ps.length];
            IntStream.range(0, ps.length).forEach(j -> d[i][j] = Short.parseShort(ps[j]));
        });
        return d;
    }

    public static final int SampleRate = 44100;
    public static final int NsfClock = 1789773;

    @Deprecated
    public static void write(String fmt, Object... arg) {
        String msg = String.format(fmt, arg);
        try (StreamWriter writer = new StreamWriter(new FileStream("log.txt", FileMode.Append))) {
            writer.writeLine(msg);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * ストリームから一括でバイナリを読み込む
     */
    public static byte[] readAllBytes(Stream stream) {
        if (stream == null)
            return null;

        byte[] buf = new byte[8192];
        try (MemoryStream ms = new MemoryStream()) {
            while (true) {
                int r = stream.read(buf, 0, buf.length);
                if (r < 1) {
                    break;
                }
                ms.write(buf, 0, r);
            }
            return ms.toArray();
        }
    }
}
