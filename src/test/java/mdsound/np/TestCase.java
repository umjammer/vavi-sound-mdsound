/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.np;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-01-24 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
public class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @Property
    String nsf;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }

        System.setProperty("mdsound.volume", String.valueOf(volume));
Debug.print("volume: " + volume);
    }

    @Test
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test1() throws Exception {
Debug.println(nsf);
        NpTestProgram.main(new String[]{nsf, "0"});

        CountDownLatch cdl = new CountDownLatch(1);
        cdl.await();
    }
}
