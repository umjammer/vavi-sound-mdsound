package mdsound.chips;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NesTest {

    private final Consumer<int[]> listener = ds -> {
        if (ds[0] != -1) np_nes_apu_volume = ds[0];
        if (ds[1] != -1) np_nes_dmc_volume = ds[1];
        if (ds[2] != -1) np_nes_fds_volume = ds[2];
    };

    public int np_nes_apu_volume;
    public int np_nes_dmc_volume;
    public int np_nes_fds_volume;

    @Test
    public void testOutput() {
        Nes nes = new Nes();
        int clock = 1789772;
        int rate = 44100;
        nes.start(clock, rate);
        nes.setListener(listener);
        nes.reset();
        nes.setVolumeAPU(0); // 0dB

        // Enable Square 1
        // 4000: Duty/Volume/Env: 1011 1111 (Duty 50%, Loop off, Vol 15) -> 0xBF
        nes.write(0x00, 0xBF);
        // 4001: Sweep: 0000 1000 (Disabled) -> 0x08
        nes.write(0x01, 0x08);
        // 4002: Timer Low: 0xFD
        nes.write(0x02, 0xFD);
        // 4003: Timer High: 0000 0000 -> 0x00
        nes.write(0x03, 0x00);
        // 4015: Status: Enable Square 1 (bit 0) -> 0x01
        nes.write(0x15, 0x01);
        
        // Frame Counter: 4017 -> 0x40 (Inhibit IRQ)
        nes.write(0x17, 0x40);

        int[][] outputs = new int[2][100];
        
        // Run for some samples
        nes.update(outputs, 100);

        // Check for non-zero output
        boolean hasOutput = false;
        for (int i = 0; i < 100; i++) {
            if (outputs[0][i] != 0 || outputs[1][i] != 0) {
                hasOutput = true;
                break;
            }
        }
        
        assertTrue(hasOutput, "Nes should produce sound output");
    }
}
