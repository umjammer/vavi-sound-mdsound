package mdsound.np;


public class LoopDetector implements Device {
    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean write(int adr, int val, int id/* = 0*/) {
        throw new UnsupportedOperationException();
    }

    public boolean isLooped(int time_in_ms, int match_second, int match_interval) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean read(int adr, int[] val, int id/* = 0*/) {
        throw new UnsupportedOperationException();
    }

    public int getLoopStart() {
        throw new UnsupportedOperationException();
    }

    public int getLoopEnd() {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOption(int id, int val) {
        throw new UnsupportedOperationException();
    }

    public static class BasicDetector extends LoopDetector {
        protected int bufSize, bufMask;
        protected int[] streamBuf;
        protected int[] timeBuf;
        protected int bIdx;
        // 前回チェック時の bIdx;
        protected int bLast;
        protected int wspeed;
        protected int currentTime;
        protected int loopStart, loopEnd;
        protected boolean empty;

        public BasicDetector(int bufbits/* = 16*/) {
            bufSize = 1 << bufbits;
            bufMask = bufSize - 1;
            streamBuf = new int[bufSize];
            timeBuf = new int[bufSize];
        }

        @Override
        public void reset() {
            for (int i = 0; i < bufSize; i++) {
                streamBuf[i] = -i;
                timeBuf[i] = 0;
            }

            currentTime = 0;
            wspeed = 0;

            bIdx = 0;
            bLast = 0;
            loopStart = -1;
            loopEnd = -1;
            empty = true;
        }

        @Override
        public boolean write(int adr, int val, int id /*= 0*/) {
            empty = false;
            timeBuf[bIdx] = currentTime;
            streamBuf[bIdx] = ((adr & 0xffff) << 8) | (val & 0xff);
            bIdx = (bIdx + 1) & bufMask;
            return false;
        }

        @Override
        public boolean read(int a, int[] b, int id/* = 0*/) {
            return false;
        }

        public boolean isLooped(int time_in_ms, int match_second, int match_interval) {
            int i, j;
            int match_size, match_length;

            if (time_in_ms - currentTime < match_interval)
                return false;

            currentTime = time_in_ms;

            if (bIdx <= bLast)
                return false;
            if (wspeed != 0)
                wspeed = (wspeed + bIdx - bLast) / 2;
            else
                wspeed = bIdx - bLast; // 初回
            bLast = bIdx;

            match_size = wspeed * match_second / match_interval;
            match_length = bufSize - match_size;

            if (match_length < 0)
                return false;

            //Debug.printf("match_length:%d", match_length);
            //Debug.printf("match_size  :%d", match_size);
            for (i = 0; i < match_length; i++) {
                for (j = 0; j < match_size; j++) {
                    if (streamBuf[(bIdx + j + match_length) & bufMask] !=
                            streamBuf[(bIdx + i + j) & bufMask]) {
                        //Debug.printf("j  :%d", j);
                        break;
                    }
                }
                if (j == match_size) {
                    loopStart = timeBuf[(bIdx + i) & bufMask];
                    loopEnd = timeBuf[(bIdx + match_length) & bufMask];
                    return true;
                }
            }
            return false;
        }

        public int getLoopStart() {
            return loopStart;
        }

        public int getLoopEnd() {
            return loopEnd;
        }

        public boolean isEmpty() {
            return empty;
        }
    }

    public static class NESDetector extends BasicDetector {
        public NESDetector(int bufbits) {
            super(bufbits);
        }

        public static final byte[] maskAPU = new byte[] {
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, 0x00, (byte) 0xff, (byte) 0xff,
                0x3f, 0x00, (byte) 0x8f, (byte) 0xf8
        };

        @Override
        public boolean write(int adr, int val, int id) {
            if ((0x4000 <= adr && adr <= 0x4013) // APU / DMC
                            || (0x4015 == adr)
                            || (0x4017 == adr)
                            || (0x9000 <= adr && adr <= 0x9002) // Vrc6Inst
                            || (0xA000 <= adr && adr <= 0xA002)
                            || (0xB000 <= adr && adr <= 0xB002)
                            || (0x9010 == adr) // VRC7
                            || (0x9030 == adr)
                            || (0x4040 <= adr && adr <= 0x4092) // FDS
                            || (0x4800 == adr) // N163
                            || (0xF800 == adr)
                            || (0x5000 <= adr && adr <= 0x5007) // MMC5
                            || (0x5010 == adr)
                            || (0x5011 == adr)
                            || (0xC000 == adr) // 5B
                            || (0xE000 == adr)
            ) {
                return super.write(adr, val, id);
            }

            return false;
        }
    }

    public static class NESDetectorEx extends LoopDetector {
        public static final byte[] maskAPU = new byte[] {
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, 0x00, (byte) 0xff, (byte) 0xff,
                0x3f, 0x00, (byte) 0x8f, (byte) 0xf8
        };

        protected enum Ins {
            SQR_0, SQR_1, TRI, NOIZ, DPCM,
            N106_0, N106_1, N106_2, N106_3,
            N106_4, N106_5, N106_6, N106_7,
            MAX_CH
        }

        protected BasicDetector[] ld = new BasicDetector[13];
        protected boolean[] looped = new boolean[13];
        protected int n106Addr;
        protected int loopStart, m_loop_end;

        public NESDetectorEx() {
            int[] bufsize_table = new int[] {
                    15, 15, 15, 15, 15, // SQR0, SQR1, TRI, NOIZ, DPCM
                    14, 14, 14, 14,// N106[0-3]
                    14, 14, 14, 14 // N106[4-7]
            };
            for (int i = 0; i < 13; i++)
                ld[i] = new BasicDetector(bufsize_table[i]);
        }

        @Override
        public void reset() {
            for (int i = 0; i < 13; i++) {
                ld[i].reset();
                looped[i] = false;
            }
        }

        public boolean isLooped(int time_in_ms, int match_second, int match_interval) {
            boolean all_empty = true, all_looped = true;
            for (int i = 0; i < 13; i++) {
                if (!looped[i]) {
                    looped[i] = ld[i].isLooped(time_in_ms, match_second, match_interval);
                    if (looped[i]) {
                        loopStart = ld[i].getLoopStart();
                        m_loop_end = ld[i].getLoopEnd();
                    }
                }
                all_looped &= looped[i] | ld[i].isEmpty();
                all_empty &= ld[i].isEmpty();
            }

            return !all_empty & all_looped;
        }

        @Override
        public boolean write(int adr, int val, int id) {
            if (0x4000 <= adr && adr < 0x4004)
                ld[Ins.SQR_0.ordinal()].write(adr, val & maskAPU[adr - 0x4000]);
            else if (0x4004 <= adr && adr < 0x4008)
                ld[Ins.SQR_1.ordinal()].write(adr, val & maskAPU[adr - 0x4000]);
            else if (0x4008 <= adr && adr < 0x400C)
                ld[Ins.TRI.ordinal()].write(adr, val & maskAPU[adr - 0x4000]);
            else if (0x400C <= adr && adr < 0x4010)
                ld[Ins.NOIZ.ordinal()].write(adr, val & maskAPU[adr - 0x4000]);
            else if (adr == 0x4012 || adr == 0x4013)
                ld[Ins.DPCM.ordinal()].write(adr, val);
            else if (0xF800 == adr)
                n106Addr = val;
            else if (0x4800 == adr) {
                if (0x40 <= n106Addr) {
                    ld[Ins.N106_0.ordinal() + ((n106Addr >> 3) & 7)].write(n106Addr, val);

                }
                if ((n106Addr & 0x80) != 0) n106Addr++;
            }
            return false;
        }

        @Override
        public boolean read(int a, int[] b, int id/* = 0*/) {
            return false;
        }

        public int getLoopStart() {
            return loopStart;
        }

        public int getLoopEnd() {
            return m_loop_end;
        }

        public boolean isEmpty() {
            boolean ret = true;
            for (int i = 0; i < 13; i++)
                ret &= ld[i].isEmpty();
            return ret;
        }
    }
}
