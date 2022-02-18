package MDSound;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;

import dotnet4j.io.FileMode;
import dotnet4j.io.FileStream;
import dotnet4j.io.MemoryStream;
import dotnet4j.io.Stream;
import dotnet4j.io.StreamWriter;

public class common
    {
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

        public static int SampleRate = 44100;
        public static int NsfClock = 1789773;

        public static void write(String fmt,Object... arg)
        {
// #if DEBUG
            String msg = String.format(fmt, arg);
            try(StreamWriter writer=new StreamWriter(new FileStream("log.txt", FileMode.Append))) // 
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
        public static byte[] ReadAllBytes(Stream stream) throws IOException
        {
            if (stream == null) return null;

            byte[] buf = new byte[8192];
            try (MemoryStream ms = new MemoryStream())
            {
                while (true)
                {
                    int r = stream.read(buf, 0, buf.length);
                    if (r < 1)
                    {
                        break;
                    }
                    ms.write(buf, 0, r);
                }
                return ms.toArray();
            }
        }

    }
