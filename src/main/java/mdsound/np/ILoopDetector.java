package mdsound.np;

public class ILoopDetector implements IDevice
    {
        @Override public void Reset()
        {
            throw new UnsupportedOperationException();
        }

        @Override public boolean Write(int adr, int val, int id/* = 0*/)
        {
            throw new UnsupportedOperationException();
        }

        public boolean IsLooped(int time_in_ms, int match_second, int match_interval)
        {
            throw new UnsupportedOperationException();
        }

        @Override public boolean Read(int adr, int val, int id/* = 0*/)
        {
            throw new UnsupportedOperationException();
        }

        public int GetLoopStart()
        {
            throw new UnsupportedOperationException();
        }

        public int GetLoopEnd()
        {
            throw new UnsupportedOperationException();
        }

        public boolean IsEmpty()
        {
            throw new UnsupportedOperationException();
        }

        @Override public void SetOption(int id, int val)
        {
            throw new UnsupportedOperationException();
        }


    public static class BasicDetector extends ILoopDetector
    {
        protected int m_bufsize, m_bufmask;
        protected int[] m_stream_buf;
        protected int[] m_time_buf;
        protected int m_bidx;
        protected int m_blast;                    // 前回チェック時のbidx;
        protected int m_wspeed;
        protected int m_current_time;
        protected int m_loop_start, m_loop_end;
        protected boolean m_empty;

        public BasicDetector(int bufbits/* = 16*/)
        {
            m_bufsize = 1 << bufbits;
            m_bufmask = m_bufsize - 1;
            m_stream_buf = new int[m_bufsize];
            m_time_buf = new int[m_bufsize];
        }

        protected void finalinze()
        {
        }

        @Override public void Reset()
        {
            int i;

            for (i = 0; i < m_bufsize; i++)
            {
                m_stream_buf[i] = -i;
                m_time_buf[i] = 0;
            }

            m_current_time = 0;
            m_wspeed = 0;

            m_bidx = 0;
            m_blast = 0;
            m_loop_start = -1;
            m_loop_end = -1;
            m_empty = true;
        }

        @Override public boolean Write(int adr, int val, int id /*= 0*/)
        {
            m_empty = false;
            m_time_buf[m_bidx] = m_current_time;
            m_stream_buf[m_bidx] = (int)(((adr & 0xffff) << 8) | (val & 0xff));
            m_bidx = (m_bidx + 1) & m_bufmask;
            return false;
        }

        @Override public boolean Read(int a, int b, int id/* = 0*/)
        {
            return false;
        }

        public boolean IsLooped(int time_in_ms, int match_second, int match_interval)
        {
            int i, j;
            int match_size, match_length;

            if (time_in_ms - m_current_time < match_interval)
                return false;

            m_current_time = time_in_ms;

            if (m_bidx <= m_blast)
                return false;
            if (m_wspeed != 0)
                m_wspeed = (m_wspeed + m_bidx - m_blast) / 2;
            else
                m_wspeed = m_bidx - m_blast;      // 初回
            m_blast = m_bidx;

            match_size = m_wspeed * match_second / match_interval;
            match_length = m_bufsize - match_size;

            if (match_length < 0)
                return false;

            //System.err.printf("match_length:{0}", match_length);
            //System.err.printf("match_size  :{0}", match_size);
            for (i = 0; i < match_length; i++)
            {
                for (j = 0; j < match_size; j++)
                {
                    if (m_stream_buf[(m_bidx + j + match_length) & m_bufmask] !=
                        m_stream_buf[(m_bidx + i + j) & m_bufmask])
                    {
                        //System.err.printf("j  :{0}", j);
                        break;
                    }
                }
                if (j == match_size)
                {
                    m_loop_start = m_time_buf[(m_bidx + i) & m_bufmask];
                    m_loop_end = m_time_buf[(m_bidx + match_length) & m_bufmask];
                    return true;
                }
            }
            return false;
        }

        public int GetLoopStart()
        {
            return m_loop_start;
        }

        public int GetLoopEnd()
        {
            return m_loop_end;
        }

        public boolean IsEmpty()
        {
            return m_empty;
        }
    }

    static class NESDetector extends BasicDetector
    {
        public NESDetector(int bufbits) {
            super(bufbits);
        }

        public static byte[] maskAPU = new byte[] {
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, 0x00, (byte) 0xff, (byte) 0xff,
            0x3f, 0x00, (byte) 0x8f, (byte) 0xf8
        };

        @Override public boolean Write(int adr, int val, int id)
        {
            if (
                (0x4000 <= adr && adr <= 0x4013) // APU / DMC
              || (0x4015 == adr)
              || (0x4017 == adr)
              || (0x9000 <= adr && adr <= 0x9002) // VRC6
              || (0xA000 <= adr && adr <= 0xA002)
              || (0xB000 <= adr && adr <= 0xB002)
              || (0x9010 == adr)                  // VRC7
              || (0x9030 == adr)
              || (0x4040 <= adr && adr <= 0x4092) // FDS
              || (0x4800 == adr)                  // N163
              || (0xF800 == adr)
              || (0x5000 <= adr && adr <= 0x5007) // MMC5
              || (0x5010 == adr)
              || (0x5011 == adr)
              || (0xC000 == adr)                  // 5B
              || (0xE000 == adr)
              )
            {
                return super.Write(adr, val, id);
            }

            return false;
        }

    }

    static class NESDetectorEx extends ILoopDetector
    {
        public static byte[] maskAPU = new byte[] {
            (byte) 0xff,(byte)  0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, 0x00, (byte) 0xff, (byte) 0xff,
            0x3f, 0x00, (byte) 0x8f, (byte) 0xf8
        };


        protected enum Ins
        {
            SQR_0, SQR_1, TRI, NOIZ, DPCM,
            N106_0, N106_1, N106_2, N106_3,
            N106_4, N106_5, N106_6, N106_7,
            MAX_CH
        };

        protected BasicDetector[] m_LD = new BasicDetector[13];
        protected boolean[] m_looped = new boolean[13];
        protected int m_n106_addr;
        protected int m_loop_start, m_loop_end;

        public NESDetectorEx()
        {
            int[] bufsize_table = new int[]{
            15, 15, 15, 15, 15, // SQR0, SQR1, TRI, NOIZ, DPCM
            14, 14, 14, 14,// N106[0-3]
            14, 14, 14, 14 // N106[4-7]
            };
            for (int i = 0; i < 13; i++)
                m_LD[i] = new BasicDetector(bufsize_table[i]);
        }

        @Override public void Reset()
        {
            for (int i = 0; i < 13; i++)
            {
                m_LD[i].Reset();
                m_looped[i] = false;
            }
        }

        public boolean IsLooped(int time_in_ms, int match_second, int match_interval)
        {
            boolean all_empty = true, all_looped = true;
            for (int i = 0; i < 13; i++)
            {
                if (!m_looped[i])
                {
                    m_looped[i] = m_LD[i].IsLooped(time_in_ms, match_second, match_interval);
                    if (m_looped[i])
                    {
                        m_loop_start = m_LD[i].GetLoopStart();
                        m_loop_end = m_LD[i].GetLoopEnd();
                    }
                }
                all_looped &= m_looped[i] | m_LD[i].IsEmpty();
                all_empty &= m_LD[i].IsEmpty();
            }

            return !all_empty & all_looped;
        }

        @Override public boolean Write(int adr, int val, int id)
        {
            if (0x4000 <= adr && adr < 0x4004)
                m_LD[(int)Ins.SQR_0.ordinal()].Write(adr, val & maskAPU[adr - 0x4000]);
            else if (0x4004 <= adr && adr < 0x4008)
                m_LD[(int)Ins.SQR_1.ordinal()].Write(adr, val & maskAPU[adr - 0x4000]);
            else if (0x4008 <= adr && adr < 0x400C)
                m_LD[(int)Ins.TRI.ordinal()].Write(adr, val & maskAPU[adr - 0x4000]);
            else if (0x400C <= adr && adr < 0x4010)
                m_LD[(int)Ins.NOIZ.ordinal()].Write(adr, val & maskAPU[adr - 0x4000]);
            else if (adr == 0x4012 || adr == 0x4013)
                m_LD[(int)Ins.DPCM.ordinal()].Write(adr, val);
            else if (0xF800 == adr)
                m_n106_addr = val;
            else if (0x4800 == adr)
            {
                if (0x40 <= m_n106_addr)
                {
                    m_LD[(int)Ins.N106_0.ordinal() + ((m_n106_addr >> 3) & 7)].Write(m_n106_addr, val);

                }
                if ((m_n106_addr & 0x80) != 0) m_n106_addr++;
            }
            return false;
        }

        @Override public boolean Read(int a, int b, int id/* = 0*/)
        {
            return false;
        }

        public int GetLoopStart() { return m_loop_start; }

        public int GetLoopEnd() { return m_loop_end; }

        public boolean IsEmpty()
        {
            boolean ret = true;
            for (int i = 0; i < 13; i++)
                ret &= m_LD[i].IsEmpty();
            return ret;
        }
    }
    }
