/*
 * Copyright (c) 2022 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import mdsound.x68sound.Global;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import test.Program;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-05-26 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
public class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @Property(name = "filename")
    String filename;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    @Test
    @DisplayName("run gui")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void testP1() throws Exception {
        Program.main(new String[0]);
        Thread.sleep(10000000);
    }

    @Test
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void testP2() throws Exception {
        Program app = new Program();
Debug.println(filename);
        app.prePlay(filename);
        CountDownLatch cdl = new CountDownLatch(1);
        cdl.await();
    }

    @Test
    void test1() throws Exception {
System.err.println("44: " + Global.OPMLOWPASS_44.length);
System.err.println("48: " + Global.OPMLOWPASS_48.length);
        assertEquals(441, Global.OPMLOWPASS_44.length);
        assertEquals(64, Global.OPMLOWPASS_44[0].length);
        assertEquals(-22, Global.OPMLOWPASS_44[1][3]);
        assertEquals(-29, Global.OPMLOWPASS_44[439][60]);
        assertEquals(96, Global.OPMLOWPASS_48.length);
        assertEquals(64, Global.OPMLOWPASS_48[0].length);
        assertEquals(-15, Global.OPMLOWPASS_48[3][3]);
        assertEquals(-17, Global.OPMLOWPASS_48[93][60]);
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        Path path = Paths.get(TestCase.class.getResource("/test_data").toURI());
        short[][] d;
        List<String> ls = Files.readAllLines(path);
        d = new short[ls.size()][];
        IntStream.range(0, ls.size()).forEach(i -> {
            String[] ps = ls.get(i).trim().split("[\\s,]+");
//Arrays.stream(ps).forEach(System.err::println);
            d[i] = new short[ps.length];
            IntStream.range(0, ps.length).forEach(j -> d[i][j] = Short.parseShort(ps[j]));
        });

        for (short[] shorts : d) {
            for (short aShort : shorts) {
                System.out.printf("%d, ", aShort);
            }
            System.out.println();
        }
    }
}
