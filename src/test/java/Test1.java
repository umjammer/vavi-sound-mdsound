/*
 * Copyright (c) 2022 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import mdsound.x68sound.Global;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import test.Program;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;


/**
 * Test1.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-05-26 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
public class Test1 {

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
    }

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

        for (short[] shorts : d) {
            for (short aShort : shorts) {
                System.out.printf("%d, ", aShort);
            }
            System.out.println();
        }
    }
}
