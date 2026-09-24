/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.instrument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mdsound.Instrument.BaseInstrument;
import mdsound.chips.Gigatron;


/**
 * GigatronInst.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-02 nsano initial version <br>
 */
public class GigatronInst extends BaseInstrument {

    private final Gigatron[] gig = {new Gigatron(), new Gigatron()};

    /** bit n: channel n muted */
    private final int[] muteMask = {0, 0};

    @Override
    public String getName() {
        return "Gigatron";
    }

    @Override
    public String getShortName() {
        return "Gigatron";
   }

    @Override
    public void reset(int chipId) {
        gig[chipId].reset();
    }

    @Override
    public int start(int chipId, int sampleRate, int clock, Object... option) {
        gig[chipId].start(sampleRate, clock);
        return sampleRate;
    }

    @Override
    public int read(int chipId, int adr) {
        return 0;
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return gig[chipId].write(port, adr, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        gig[chipId].update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        gig[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
        muteMask[chipId] |= 1 << ch;
        gig[chipId].setMuteMask(muteMask[chipId]);
    }

    @Override
    public void resetMask(int chipId, int ch) {
        muteMask[chipId] &= ~(1 << ch);
        gig[chipId].setMuteMask(muteMask[chipId]);
    }

    /**
     * {@code clock} (scanlines/s), {@code channelMask}, and {@code channels}, a list of four maps
     * with {@code key}, {@code wavX}, {@code wavA}, {@code servings} (slots per tick, 0 when the
     * mask leaves the channel out), {@code level} (peak to peak, 0..63) and {@code mute}.
     */
    private Map<String, Object> getInfo(int chipId) {
        Gigatron chip = gig[chipId];

        Map<String, Object> info = new HashMap<>();
        info.put("clock", chip.getClock());
        info.put("channelMask", chip.getChannelMask());
        List<Map<String, Object>> channels = new ArrayList<>();
        for (int c = 0; c < Gigatron.CHANNELS; c++) {
            Gigatron.Channel ch = chip.getChannel(c);
            Map<String, Object> channel = new HashMap<>();
            channel.put("key", ch.getKey());
            channel.put("wavX", ch.getWavX());
            channel.put("wavA", ch.getWavA());
            channel.put("servings", chip.getServings(c));
            channel.put("level", ch.getLevel());
            channel.put("mute", (muteMask[chipId] & (1 << c)) != 0);
            channels.add(channel);
        }
        info.put("channels", channels);
        return info;
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "NAME" -> result.put(getName(), "Gigatron");
            case "FAMILY" -> result.put(getName(), "Gigatron TTL microcomputer");
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
