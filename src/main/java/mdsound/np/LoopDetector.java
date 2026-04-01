/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np;


public interface LoopDetector extends Device {

    @Override
    void reset();

    @Override
    boolean write(int adr, int val, int id /* = 0 */);

    boolean isLooped(int time_in_ms, int match_second, int match_interval);

    @Override
    boolean read(int adr, int[] val, int id /* = 0 */);

    int getLoopStart();

    int getLoopEnd();

    boolean isEmpty();

    @Override
    default void setOption(int id, int val) {
        throw new UnsupportedOperationException();
    }

    class BasicDetector implements LoopDetector {
        protected final int bufSize;
        protected final int bufMask;
        protected final int[] streamBuf;
        protected final int[] timeBuf;
        protected int bIdx;
        // bIdx last time checked
        protected int bLast;
        protected int wSpeed;
        protected int currentTime;
        protected int loopStart, loopEnd;
        protected boolean empty;

        public BasicDetector(int bufBits /* = 16 */) {
            bufSize = 1 << bufBits;
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
            wSpeed = 0;

            bIdx = 0;
            bLast = 0;
            loopStart = -1;
            loopEnd = -1;
            empty = true;
        }

        @Override
        public boolean write(int adr, int val, int id /* = 0 */) {
            empty = false;
            timeBuf[bIdx] = currentTime;
            streamBuf[bIdx] = ((adr & 0xffff) << 8) | (val & 0xff);
            bIdx = (bIdx + 1) & bufMask;
            return false;
        }

        @Override
        public boolean read(int a, int[] b, int id /* = 0 */) {
            return false;
        }

        @Override
        public boolean isLooped(int time_in_ms, int match_second, int match_interval) {
            if (time_in_ms - currentTime < match_interval)
                return false;

            currentTime = time_in_ms;

            if (bIdx <= bLast)
                return false;
            if (wSpeed != 0)
                wSpeed = (wSpeed + bIdx - bLast) / 2;
            else
                wSpeed = bIdx - bLast; // first time
            bLast = bIdx;

            int match_size = wSpeed * match_second / match_interval;
            int match_length = bufSize - match_size;

            if (match_length < 0)
                return false;

            //logger.log(Level.TRACE, "match_length:%d".formatted(match_length));
            //logger.log(Level.TRACE, "match_size  :%d".formatted(match_size));
            for (int i = 0; i < match_length; i++) {
                int j;
                for (j = 0; j < match_size; j++) {
                    if (streamBuf[(bIdx + j + match_length) & bufMask] !=
                            streamBuf[(bIdx + i + j) & bufMask]) {
                        //logger.log(Level.TRACE, "j  :%d".formatted(j));
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

        @Override
        public int getLoopStart() {
            return loopStart;
        }

        @Override
        public int getLoopEnd() {
            return loopEnd;
        }

        @Override
        public boolean isEmpty() {
            return empty;
        }
    }

    class NESDetector extends BasicDetector {

        public NESDetector(int bufBits) {
            super(bufBits);
        }

        @Override
        public boolean write(int adr, int val, int id) {
            if ((0x4000 <= adr && adr <= 0x4013) // APU / DMC
                            || (0x4015 == adr)
                            || (0x4017 == adr)
                            || (0x9000 <= adr && adr <= 0x9002) // Vrc6Inst
                            || (0xa000 <= adr && adr <= 0xa002)
                            || (0xb000 <= adr && adr <= 0xb002)
                            || (0x9010 == adr) // VRC7
                            || (0x9030 == adr)
                            || (0x4040 <= adr && adr <= 0x4092) // FDS
                            || (0x4800 == adr) // N163
                            || (0xf800 == adr)
                            || (0x5000 <= adr && adr <= 0x5007) // MMC5
                            || (0x5010 == adr)
                            || (0x5011 == adr)
                            || (0xc000 == adr) // 5B
                            || (0xe000 == adr)
            ) {
                return super.write(adr, val, id);
            }

            return false;
        }
    }

    class NESDetectorEx implements LoopDetector {

        private static final int[] maskAPU = {
                0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff,
                0xff, 0x00, 0xff, 0xff,
                0x3f, 0x00, 0x8f, 0xf8
        };

        protected enum Ins {
            SQR_0, SQR_1, TRI, NOIZ, DPCM,
            N106_0, N106_1, N106_2, N106_3,
            N106_4, N106_5, N106_6, N106_7,
            MAX_CH
        }

        protected final BasicDetector[] ld = new BasicDetector[13];
        protected final boolean[] looped = new boolean[13];
        protected int n106Addr;
        protected int loopStart, m_loop_end;

        private static final int[] bufsize_table = {
                15, 15, 15, 15, 15, // SQR0, SQR1, TRI, NOIZ, DPCM
                14, 14, 14, 14,// N106[0-3]
                14, 14, 14, 14 // N106[4-7]
        };

        public NESDetectorEx() {
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

        @Override
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
        public boolean read(int a, int[] b, int id /* = 0 */) {
            return false;
        }

        @Override
        public int getLoopStart() {
            return loopStart;
        }

        @Override
        public int getLoopEnd() {
            return m_loop_end;
        }

        @Override
        public boolean isEmpty() {
            boolean ret = true;
            for (int i = 0; i < 13; i++)
                ret &= ld[i].isEmpty();
            return ret;
        }
    }
}
