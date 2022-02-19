
package MDSound;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;

import MDSound.X68Sound.Global;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileStream;
import dotnet4j.io.MemoryStream;
import dotnet4j.io.Stream;
import dotnet4j.io.StreamWriter;


public class Common {
    @FunctionalInterface
    public interface QuadConsumer<T, U, V, W> {
        void accept(T t, U u, V v, W w);
    }

    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    @FunctionalInterface
    public interface QuadFunction<T, U, V, W, R> {
        R apply(T t, U u, V v, W w);
    }

    public static byte[] toByteArray(List<Byte> o) {
        byte[] a = new byte[o.size()];
        IntStream.range(0, o.size()).forEach(i -> a[i] = o.get(i).byteValue());
        return a;
    }
    public static short[][] readArrays(String name) throws Exception {
        Path path = Paths.get(Global.class.getResource(name).toURI());
        List<String> ls = Files.readAllLines(path);
        short[][] d = new short[ls.size()][];
        IntStream.range(0, ls.size()).forEach(i -> {
            String[] ps = ls.get(i).trim().split("[\\s,]+");
            d[i] = new short[ps.length];
            IntStream.range(0, ps.length).forEach(j -> d[i][j] = Short.parseShort(ps[j]));
        });
        return d;
    }

    public static int SampleRate = 44100;
    public static int NsfClock = 1789773;

    public static void write(String fmt, Object... arg) {
// #if DEBUG
        String msg = String.format(fmt, arg);
        try (StreamWriter writer = new StreamWriter(new FileStream("log.txt", FileMode.Append))) //
        {
            writer.writeLine(msg);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
// #endif
    }

    /// <summary>
    /// ストリームから一括でバイナリを読み込む
    /// </summary>
    public static byte[] ReadAllBytes(Stream stream) throws IOException {
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
