
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import mdsound.x68sound.Global;


public class Test1 {

    @Test
    void test1() throws Exception {
System.err.println("44: " + Global.OPMLOWPASS_44.length);
System.err.println("48: " + Global.OPMLOWPASS_44.length);
        assertNotNull(Global.OPMLOWPASS_44);
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        Path path = Paths.get(Test1.class.getResource("/test_data").toURI());
        short[][] d;
        List<String> ls = Files.readAllLines(path);
        d = new short[ls.size()][];
        IntStream.range(0, ls.size()).forEach(i -> {
            String[] ps = ls.get(i).trim().split("[\\s,]+");
//Arrays.stream(ps).forEach(System.err::println);
            d[i] = new short[ps.length];
            IntStream.range(0, ps.length).forEach(j -> d[i][j] = Short.parseShort(ps[j]));
        });

        for (int i = 0; i < d.length; i++) {
            for (int j = 0; j < d[i].length; j++) {
                System.out.printf("%d, ", d[i][j]);
            }
            System.out.println();
        }
    }
}

/* */
