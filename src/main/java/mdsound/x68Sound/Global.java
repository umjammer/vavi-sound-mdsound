package mdsound.x68Sound;

import java.util.function.Function;

import mdsound.Common;

    public class Global
    {

        private static byte[] memory = null;
        public Opm opm;

        public Global()
        {
            OPMLOWPASS = OPMLOWPASS_44;
        }

        public void mountMemory(byte[] mem)
        {
            memory = mem;
        }

        public int DebugValue = 0;
        public int ErrorCode = 0;

        public final int N_CH = 8;

        public final int PRECISION_BITS = (10);
        public final int PRECISION = (1 << PRECISION_BITS);
        public final int SIZEALPHATBL_BITS = (10);
        public final int SIZEALPHATBL = (1 << SIZEALPHATBL_BITS);

        public final int SIZESINTBL_BITS = (10);
        public final int SIZESINTBL = (1 << SIZESINTBL_BITS);
        public final int MAXSINVAL = (1 << (SIZESINTBL_BITS + 2));

        public int Samprate = 44100;
        public int WaveOutSamp = 44100;
        public int OpmWait = 240;  // 24.0μｓ
        public int OpmRate = 62500;    // 入力クロック÷64

        public int[] STEPTBL = new int[11 * 12 * 64];
        public final int ALPHAZERO = (SIZEALPHATBL * 3);
        public int[] ALPHATBL = new int[ALPHAZERO + SIZEALPHATBL + 1];
        public short[] SINTBL = new short[SIZESINTBL];
        public int[] STEPTBL_O2 = new int[] {
    1299,1300,1301,1302,1303,1304,1305,1306,
    1308,1309,1310,1311,1313,1314,1315,1316,
    1318,1319,1320,1321,1322,1323,1324,1325,
    1327,1328,1329,1330,1332,1333,1334,1335,
    1337,1338,1339,1340,1341,1342,1343,1344,
    1346,1347,1348,1349,1351,1352,1353,1354,
    1356,1357,1358,1359,1361,1362,1363,1364,
    1366,1367,1368,1369,1371,1372,1373,1374,
    1376,1377,1378,1379,1381,1382,1383,1384,
    1386,1387,1388,1389,1391,1392,1393,1394,
    1396,1397,1398,1399,1401,1402,1403,1404,
    1406,1407,1408,1409,1411,1412,1413,1414,
    1416,1417,1418,1419,1421,1422,1423,1424,
    1426,1427,1429,1430,1431,1432,1434,1435,
    1437,1438,1439,1440,1442,1443,1444,1445,
    1447,1448,1449,1450,1452,1453,1454,1455,
    1458,1459,1460,1461,1463,1464,1465,1466,
    1468,1469,1471,1472,1473,1474,1476,1477,
    1479,1480,1481,1482,1484,1485,1486,1487,
    1489,1490,1492,1493,1494,1495,1497,1498,
    1501,1502,1503,1504,1506,1507,1509,1510,
    1512,1513,1514,1515,1517,1518,1520,1521,
    1523,1524,1525,1526,1528,1529,1531,1532,
    1534,1535,1536,1537,1539,1540,1542,1543,
    1545,1546,1547,1548,1550,1551,1553,1554,
    1556,1557,1558,1559,1561,1562,1564,1565,
    1567,1568,1569,1570,1572,1573,1575,1576,
    1578,1579,1580,1581,1583,1584,1586,1587,
    1590,1591,1592,1593,1595,1596,1598,1599,
    1601,1602,1604,1605,1607,1608,1609,1610,
    1613,1614,1615,1616,1618,1619,1621,1622,
    1624,1625,1627,1628,1630,1631,1632,1633,
    1637,1638,1639,1640,1642,1643,1645,1646,
    1648,1649,1651,1652,1654,1655,1656,1657,
    1660,1661,1663,1664,1666,1667,1669,1670,
    1672,1673,1675,1676,1678,1679,1681,1682,
    1685,1686,1688,1689,1691,1692,1694,1695,
    1697,1698,1700,1701,1703,1704,1706,1707,
    1709,1710,1712,1713,1715,1716,1718,1719,
    1721,1722,1724,1725,1727,1728,1730,1731,
    1734,1735,1737,1738,1740,1741,1743,1744,
    1746,1748,1749,1751,1752,1754,1755,1757,
    1759,1760,1762,1763,1765,1766,1768,1769,
    1771,1773,1774,1776,1777,1779,1780,1782,
    1785,1786,1788,1789,1791,1793,1794,1796,
    1798,1799,1801,1802,1804,1806,1807,1809,
    1811,1812,1814,1815,1817,1819,1820,1822,
    1824,1825,1827,1828,1830,1832,1833,1835,
    1837,1838,1840,1841,1843,1845,1846,1848,
    1850,1851,1853,1854,1856,1858,1859,1861,
    1864,1865,1867,1868,1870,1872,1873,1875,
    1877,1879,1880,1882,1884,1885,1887,1888,
    1891,1892,1894,1895,1897,1899,1900,1902,
    1904,1906,1907,1909,1911,1912,1914,1915,
    1918,1919,1921,1923,1925,1926,1928,1930,
    1932,1933,1935,1937,1939,1940,1942,1944,
    1946,1947,1949,1951,1953,1954,1956,1958,
    1960,1961,1963,1965,1967,1968,1970,1972,
    1975,1976,1978,1980,1982,1983,1985,1987,
    1989,1990,1992,1994,1996,1997,1999,2001,
    2003,2004,2006,2008,2010,2011,2013,2015,
    2017,2019,2021,2022,2024,2026,2028,2029,
    2032,2033,2035,2037,2039,2041,2043,2044,
    2047,2048,2050,2052,2054,2056,2058,2059,
    2062,2063,2065,2067,2069,2071,2073,2074,
    2077,2078,2080,2082,2084,2086,2088,2089,
    2092,2093,2095,2097,2099,2101,2103,2104,
    2107,2108,2110,2112,2114,2116,2118,2119,
    2122,2123,2125,2127,2129,2131,2133,2134,
    2137,2139,2141,2142,2145,2146,2148,2150,
    2153,2154,2156,2158,2160,2162,2164,2165,
    2168,2170,2172,2173,2176,2177,2179,2181,
    2185,2186,2188,2190,2192,2194,2196,2197,
    2200,2202,2204,2205,2208,2209,2211,2213,
    2216,2218,2220,2222,2223,2226,2227,2230,
    2232,2234,2236,2238,2239,2242,2243,2246,
    2249,2251,2253,2255,2256,2259,2260,2263,
    2265,2267,2269,2271,2272,2275,2276,2279,
    2281,2283,2285,2287,2288,2291,2292,2295,
    2297,2299,2301,2303,2304,2307,2308,2311,
    2315,2317,2319,2321,2322,2325,2326,2329,
    2331,2333,2335,2337,2338,2341,2342,2345,
    2348,2350,2352,2354,2355,2358,2359,2362,
    2364,2366,2368,2370,2371,2374,2375,2378,
    2382,2384,2386,2388,2389,2392,2393,2396,
    2398,2400,2402,2404,2407,2410,2411,2414,
    2417,2419,2421,2423,2424,2427,2428,2431,
    2433,2435,2437,2439,2442,2445,2446,2449,
    2452,2454,2456,2458,2459,2462,2463,2466,
    2468,2470,2472,2474,2477,2480,2481,2484,
    2488,2490,2492,2494,2495,2498,2499,2502,
    2504,2506,2508,2510,2513,2516,2517,2520,
    2524,2526,2528,2530,2531,2534,2535,2538,
    2540,2542,2544,2546,2549,2552,2553,2556,
    2561,2563,2565,2567,2568,2571,2572,2575,
    2577,2579,2581,2583,2586,2589,2590,2593,
};
        public int[] D1LTBL = new int[16];

        public int[] DT1TBL = new int[128 + 4];
        public int[] DT1TBL_org = new int[]{
    0, 0, 1, 2,
    0, 0, 1, 2,
    0, 0, 1, 2,
    0, 0, 1, 2,
    0, 1, 2, 2,
    0, 1, 2, 3,
    0, 1, 2, 3,
    0, 1, 2, 3,
    0, 1, 2, 4,
    0, 1, 3, 4,
    0, 1, 3, 4,
    0, 1, 3, 5,
    0, 2, 4, 5,
    0, 2, 4, 6,
    0, 2, 4, 6,
    0, 2, 5, 7,
    0, 2, 5, 8,
    0, 3, 6, 8,
    0, 3, 6, 9,
    0, 3, 7, 10,
    0, 4, 8, 11,
    0, 4, 8, 12,
    0, 4, 9, 13,
    0, 5, 10, 14,
    0, 5, 11, 16,
    0, 6, 12, 17,
    0, 6, 13, 19,
    0, 7, 14, 20,
    0, 8, 16, 22,
    0, 8, 16, 22,
    0, 8, 16, 22,
    0, 8, 16, 22,
    0,0,0,0
        };

        public class XR_ELE {
            public int and;
            public int add;
            public XR_ELE(int and, int add)
            {
                this.and = and;
                this.add = add;
            }
        }

        public XR_ELE[] XRTBL = new XR_ELE[]{
            new XR_ELE(4095,8),
            new XR_ELE(2047,5 ),new XR_ELE(2047,6),new XR_ELE(2047,7),new XR_ELE(2047,8),
            new XR_ELE(1023,5),new XR_ELE(1023,6),new XR_ELE(1023,7),new XR_ELE(1023,8),
            new XR_ELE(511,5 ),new XR_ELE(511,6 ),new XR_ELE(511,7 ),new XR_ELE(511,8 ),
            new XR_ELE(255,5 ),new XR_ELE(255,6 ),new XR_ELE(255,7 ),new XR_ELE(255,8 ),
            new XR_ELE(127,5  ),new XR_ELE(127,6 ),new XR_ELE(127,7 ),new XR_ELE(127,8 ),
            new XR_ELE(63,5   ),new XR_ELE(63,6  ),new XR_ELE(63,7  ),new XR_ELE(63,8  ),
            new XR_ELE(31,5   ),new XR_ELE(31,6  ),new XR_ELE(31,7  ),new XR_ELE(31,8  ),
            new XR_ELE(15,5   ),new XR_ELE(15,6  ),new XR_ELE(15,7  ),new XR_ELE(15,8  ),
            new XR_ELE(7,5    ),new XR_ELE(7,6   ),new XR_ELE(7,7   ),new XR_ELE(7,8   ),
            new XR_ELE(3,5    ),new XR_ELE(3,6   ),new XR_ELE(3,7   ),new XR_ELE(3,8   ),
            new XR_ELE(1,5    ),new XR_ELE(1,6   ),new XR_ELE(1,7   ),new XR_ELE(1,8   ),
            new XR_ELE(0,5    ),new XR_ELE(0,6   ),new XR_ELE(0,7   ),new XR_ELE(0,8   ),
            new XR_ELE(0,10   ),new XR_ELE(0,12  ),new XR_ELE(0,14  ),new XR_ELE(0,16  ),
            new XR_ELE(0,20   ),new XR_ELE(0,24  ),new XR_ELE(0,28  ),new XR_ELE(0,32  ),
            new XR_ELE(0,40   ),new XR_ELE(0,48  ),new XR_ELE(0,56  ),new XR_ELE(0,64  ),
            new XR_ELE(0,64   ),new XR_ELE(0,64  ),new XR_ELE(0,64  ),
            new XR_ELE(0,64   ),new XR_ELE(0,64  ),new XR_ELE(0,64  ),new XR_ELE(0,64),
            new XR_ELE(0,64),new XR_ELE(0,64),new XR_ELE(0,64),new XR_ELE(0,64),
            new XR_ELE(0,64   ),new XR_ELE(0,64  ),new XR_ELE(0,64  ),new XR_ELE(0,64),
            new XR_ELE(0,64),new XR_ELE(0,64),new XR_ELE(0,64),new XR_ELE(0,64),
            new XR_ELE(0,64   ),new XR_ELE(0,64  ),new XR_ELE(0,64  ),new XR_ELE(0,64),
            new XR_ELE(0,64),new XR_ELE(0,64),new XR_ELE(0,64),new XR_ELE(0,64),
            new XR_ELE( 0,64  ),new XR_ELE(0,64  ),new XR_ELE(0,64  ),new XR_ELE(0,64),
            new XR_ELE(0,64),new XR_ELE(0,64),new XR_ELE(0,64),new XR_ELE(0,64),
        };


        public int[] DT2TBL = new int[] { 0, 384, 500, 608 };
        public int[] NOISEALPHATBL = new int[ALPHAZERO + SIZEALPHATBL + 1];


        public int[] dltLTBL = new int[]{
            16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,
            73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,
            337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,
        };

        public int[] DCT = new int[] {
            -1,-1,-1,-1,2,4,6,8,
            -1,-1,-1,-1,2,4,6,8,
        };


        public int[][] ADPCMRATETBL = new int[][]{
            new int[]{ 2, 3, 4, 4},
            new int[]{0, 1, 2, 2 }
        };

        public int[] ADPCMRATEADDTBL = new int[]{
            46875, 62500, 93750, 125000, 15625*12, 15625*12, 15625*12, 0,
        };

        public int[] PCM8VOLTBL = new int[] {
            2,3,4,5,6,8,10,12,16,20,24,32,40,48,64,80,
        };

        public final int PCM8_NCH = 8;

        public static int bswapl(int adrs)
        {
            return (adrs << 24) + ((adrs << 8) & 0xff0000) + ((adrs >> 8) & 0xff00) + (adrs >> 24);
        }

        public static int bswapw(int data)
        {
            return (int)((data << 8) + (data >> 8));
        }

        public static int MemReadDefault(int adrs)
        {
            if (memory.length <= adrs) return -1;
            return memory[adrs];
        }

        public Function<Integer, Integer> MemRead = Global::MemReadDefault;

        int seed = 1;
        public int irnd()
        {
            seed = (int)(seed * 1566083941L + 1);
            return seed;
        }



        public int TotalVolume; // 音量 x/256

        //public int Semapho = 0;
        public int TimerSemapho = 0;

        public final int OPMLPF_COL = 64;
        public final int OPMLPF_ROW_44 = 441;

        public static final short[][] OPMLOWPASS_44;

        public final int OPMLPF_ROW_48 = 96;
        public static final short[][] OPMLOWPASS_48;

        static {
            try {
                OPMLOWPASS_44 = Common.readArrays("/opmlowpass_44.dat");
                OPMLOWPASS_48 = Common.readArrays("/opmlowpass_48.dat");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public int OPMLPF_ROW = OPMLPF_ROW_44;
        //public short (* OPMLOWPASS)[OPMLPF_COL] = OPMLOWPASS_44;
        public short[][] OPMLOWPASS = null;// OPMLOWPASS_44[0]; //コンストラクタで実施


        public int Betw_Time;       // 5 ms
        public int Late_Time;       // (200+Bet_time) ms
        public int Late_Samples;    // (44100*Late_Time/1000)
        public int Blk_Samples; // 44100/N_waveblk
        public int Betw_Samples_Slower; // floor(44100.0*5/1000.0-rev)
        public int Betw_Samples_Faster; // ceil(44100.0*5/1000.0+rev)
        public int Betw_Samples_VerySlower; // floor(44100.0*5/1000.0-rev)/4.0
        public int Slower_Limit, Faster_Limit;
        //public HWAVEOUT hwo = null;
        //public LPWAVEHDR lpwh = null;
        public int N_wavehdr = 0;
        //public WAVEFORMATEX wfx;
        public int TimerResolution = 1;
        //unsigned int SamplesCounter=0;
        //unsigned int SamplesCounterRev=0;
        public int nSamples;

        //public HANDLE thread_handle = null;
        public int thread_id = 0;
        public int thread_flag = 0;
        public int timer_start_flag = 0;
        //final int N_waveblk=8;
        public final int N_waveblk = 4;
        public int waveblk = 0;
        public int playingblk = 0, playingblk_next = 1;
        public int setPcmBufPtr = -1;

        public final int WM_USER = 0x0400;
        public final int THREADMES_WAVEOUTDONE = (WM_USER + 1);
        public final int THREADMES_KILL = (WM_USER + 2);

        //public void waveOutProc(HWAVEOUT hwo, (int) uMsg, DWORD dwInstance, DWORD dwParam1, DWORD dwParam2);
        //public void OpmTimeProc((int) uID, (int) uMsg, DWORD dwUser, DWORD dw1, DWORD dw2);
        //MMTIME mmt;

        //public void waveOutProc(IntPtr hwo, WinAPI.WaveOutMessage uMsg, int dwInstance, IntPtr dwParam1, int dwParam2)
        //{
        //    if (uMsg == WinAPI.WaveOutMessage.Done && thread_flag != 0)
        //    {
        //        timer_start_flag = 1;   // マルチメディアタイマーの処理を開始


        //        playingblk = (playingblk + 1) & (N_waveblk - 1);
        //        int playptr = playingblk * Blk_Samples;

        //        int genptr = (int)opm.PcmBufPtr;
        //        if (genptr < playptr)
        //        {
        //            genptr += (int)opm.PcmBufSize;
        //        }
        //        genptr -= playptr;
        //        if (genptr <= Late_Samples)
        //        {
        //            if (Late_Samples - Faster_Limit <= genptr)
        //            {
        //                // 音生成が遅れている
        //                nSamples = (int)Betw_Samples_Faster;
        //            }
        //            else
        //            {
        //                // 音生成が進みすぎている
        //                //    nSamples = Betw_Samples_VerySlower;
        //                // 音生成が遅れすぎている
        //                //    setPcmBufPtr = ((playingblk+1)&(N_waveblk-1)) * Blk_Samples;
        //                int ptr = (int)(playptr + Late_Samples + Betw_Samples_Faster);
        //                while (ptr >= opm.PcmBufSize) ptr -= opm.PcmBufSize;
        //                setPcmBufPtr = (int)ptr;
        //            }
        //        }
        //        else
        //        {
        //            if (genptr <= Late_Samples + Slower_Limit)
        //            {
        //                // 音生成が進んでいる
        //                nSamples = (int)Betw_Samples_Slower;
        //            }
        //            else
        //            {
        //                // 音生成が進みすぎている
        //                //    nSamples = Betw_Samples_VerySlower;
        //                //    setPcmBufPtr = ((playingblk+1)&(N_waveblk-1)) * Blk_Samples;
        //                int ptr = (int)(playptr + Late_Samples + Betw_Samples_Faster);
        //                while (ptr >= opm.PcmBufSize) ptr -= opm.PcmBufSize;
        //                setPcmBufPtr = (int)ptr;
        //            }
        //        }

        //        WinAPI.PostThreadMessage(thread_id, THREADMES_WAVEOUTDONE, (int)Ptr.Zero, IntPtr.Zero);
        //    }
        //}

        // マルチメディアタイマー
        public int OpmTimeProc(short[] buffer, int offset, int sampleCount)
        //public void OpmTimeProc(int uID, int uMsg, int dwUser, int dw1, int dw2)
        {

            //if (timer_start_flag==0) return sampleCount;

            //if (opm.PcmBufPtr/Blk_Samples == ((playingblk-1)&(N_waveblk-1))) return;
            if (setPcmBufPtr != -1)
            {
                opm.PcmBufPtr = (int)setPcmBufPtr;
                setPcmBufPtr = -1;
            }

            opm.PushRegs();

            if (WaveOutSamp == 44100 || WaveOutSamp == 48000)
            {
                //opm.pcmset62((int)nSamples);
                opm.pcmset62(buffer, offset, (int)sampleCount, null);
            }
            else
            {
                //opm.pcmset22((int)nSamples);
                opm.pcmset22(buffer, offset, (int)sampleCount);
            }

            //  opm.timer();
            opm.betwint();


            opm.PopRegs();


            /*
                    if (opm.adpcm.DmaReg[0x00] & 0x10) {
                        if (opm.adpcm.DmaReg[0x07] & 0x08) { // INT==1?
                            if (opm.adpcm.ErrIntProc != NULL) {
                                opm.adpcm.ErrIntProc();
                            }
                        }
                    } else if (opm.adpcm.DmaReg[0x00] & 0x10) {
                        if (opm.adpcm.DmaReg[0x07] & 0x08) { // INT==1?
                            if (opm.adpcm.IntProc != NULL) {
                                opm.adpcm.IntProc();
                            }
                        }
                    }
            */
            return sampleCount;
        }

        //public void waveOutThread()
        //{

        //    thread_flag = 1;

        //    try
        //    {
        //        while (WinAPI.GetMessage(Msg, IntPtr.Zero, 0, 0))
        //        {
        //            //if (Msg.message == THREADMES_WAVEOUTDONE)
        //            if (Msg.msg == THREADMES_WAVEOUTDONE)
        //            {

        //                WinAPI.waveOutWrite(hwo, WaveHdrList[waveblk], Marshal.SizeOf(typeof(WinAPI.WaveHdr)));

        //                ++waveblk;
        //                if (waveblk >= N_waveblk)
        //                {
        //                    waveblk = 0;
        //                }

        //            }
        //            //else if (Msg.message == THREADMES_KILL)
        //            else if (Msg.msg == THREADMES_KILL)
        //            {
        //                break;
        //            }
        //        }

        //    }
        //    catch (System.Threading.ThreadAbortException)
        //    {
        //    }

        //    WinAPI.waveOutReset(hwo);
        //    thread_flag = 0;
        //}



        //public void OpmFir_Normal(short[] p, short[] buf0, short[] buf1, int[] result)
        public void OpmFir(short[] p, short[] buf0,int buf0Ptr, short[] buf1, int buf1Ptr, int[] result)
        {
            result[0] = (int)buf0[buf0Ptr+0] * p[0]
                        + (int)buf0[buf0Ptr+1] * p[1]
                        + (int)buf0[buf0Ptr+2] * p[2]
                        + (int)buf0[buf0Ptr+3] * p[3]
                        + (int)buf0[buf0Ptr+4] * p[4]
                        + (int)buf0[buf0Ptr+5] * p[5]
                        + (int)buf0[buf0Ptr+6] * p[6]
                        + (int)buf0[buf0Ptr+7] * p[7]
                        + (int)buf0[buf0Ptr+8] * p[8]
                        + (int)buf0[buf0Ptr+9] * p[9]
                        + (int)buf0[buf0Ptr+10] * p[10]
                        + (int)buf0[buf0Ptr+11] * p[11]
                        + (int)buf0[buf0Ptr+12] * p[12]
                        + (int)buf0[buf0Ptr+13] * p[13]
                        + (int)buf0[buf0Ptr+14] * p[14]
                        + (int)buf0[buf0Ptr+15] * p[15]
                        + (int)buf0[buf0Ptr+16] * p[16]
                        + (int)buf0[buf0Ptr+17] * p[17]
                        + (int)buf0[buf0Ptr+18] * p[18]
                        + (int)buf0[buf0Ptr+19] * p[19]
                        + (int)buf0[buf0Ptr+20] * p[20]
                        + (int)buf0[buf0Ptr+21] * p[21]
                        + (int)buf0[buf0Ptr+22] * p[22]
                        + (int)buf0[buf0Ptr+23] * p[23]
                        + (int)buf0[buf0Ptr+24] * p[24]
                        + (int)buf0[buf0Ptr+25] * p[25]
                        + (int)buf0[buf0Ptr+26] * p[26]
                        + (int)buf0[buf0Ptr+27] * p[27]
                        + (int)buf0[buf0Ptr+28] * p[28]
                        + (int)buf0[buf0Ptr+29] * p[29]
                        + (int)buf0[buf0Ptr+30] * p[30]
                        + (int)buf0[buf0Ptr+31] * p[31]
                        + (int)buf0[buf0Ptr+32] * p[32]
                        + (int)buf0[buf0Ptr+33] * p[33]
                        + (int)buf0[buf0Ptr+34] * p[34]
                        + (int)buf0[buf0Ptr+35] * p[35]
                        + (int)buf0[buf0Ptr+36] * p[36]
                        + (int)buf0[buf0Ptr+37] * p[37]
                        + (int)buf0[buf0Ptr+38] * p[38]
                        + (int)buf0[buf0Ptr+39] * p[39]
                        + (int)buf0[buf0Ptr+40] * p[40]
                        + (int)buf0[buf0Ptr+41] * p[41]
                        + (int)buf0[buf0Ptr+42] * p[42]
                        + (int)buf0[buf0Ptr+43] * p[43]
                        + (int)buf0[buf0Ptr+44] * p[44]
                        + (int)buf0[buf0Ptr+45] * p[45]
                        + (int)buf0[buf0Ptr+46] * p[46]
                        + (int)buf0[buf0Ptr+47] * p[47]
                        + (int)buf0[buf0Ptr+48] * p[48]
                        + (int)buf0[buf0Ptr+49] * p[49]
                        + (int)buf0[buf0Ptr+50] * p[50]
                        + (int)buf0[buf0Ptr+51] * p[51]
                        + (int)buf0[buf0Ptr+52] * p[52]
                        + (int)buf0[buf0Ptr+53] * p[53]
                        + (int)buf0[buf0Ptr+54] * p[54]
                        + (int)buf0[buf0Ptr+55] * p[55]
                        + (int)buf0[buf0Ptr+56] * p[56]
                        + (int)buf0[buf0Ptr+57] * p[57]
                        + (int)buf0[buf0Ptr+58] * p[58]
                        + (int)buf0[buf0Ptr+59] * p[59]
                        + (int)buf0[buf0Ptr+60] * p[60]
                        + (int)buf0[buf0Ptr+61] * p[61]
                        + (int)buf0[buf0Ptr+62] * p[62]
                        + (int)buf0[buf0Ptr+63] * p[63];
            result[0] >>= (16 - 1);
            result[1] = (int)buf1[buf1Ptr+0] * p[0]
                        + (int)buf1[buf1Ptr+1] * p[1]
                        + (int)buf1[buf1Ptr+2] * p[2]
                        + (int)buf1[buf1Ptr+3] * p[3]
                        + (int)buf1[buf1Ptr+4] * p[4]
                        + (int)buf1[buf1Ptr+5] * p[5]
                        + (int)buf1[buf1Ptr+6] * p[6]
                        + (int)buf1[buf1Ptr+7] * p[7]
                        + (int)buf1[buf1Ptr+8] * p[8]
                        + (int)buf1[buf1Ptr+9] * p[9]
                        + (int)buf1[buf1Ptr+10] * p[10]
                        + (int)buf1[buf1Ptr+11] * p[11]
                        + (int)buf1[buf1Ptr+12] * p[12]
                        + (int)buf1[buf1Ptr+13] * p[13]
                        + (int)buf1[buf1Ptr+14] * p[14]
                        + (int)buf1[buf1Ptr+15] * p[15]
                        + (int)buf1[buf1Ptr+16] * p[16]
                        + (int)buf1[buf1Ptr+17] * p[17]
                        + (int)buf1[buf1Ptr+18] * p[18]
                        + (int)buf1[buf1Ptr+19] * p[19]
                        + (int)buf1[buf1Ptr+20] * p[20]
                        + (int)buf1[buf1Ptr+21] * p[21]
                        + (int)buf1[buf1Ptr+22] * p[22]
                        + (int)buf1[buf1Ptr+23] * p[23]
                        + (int)buf1[buf1Ptr+24] * p[24]
                        + (int)buf1[buf1Ptr+25] * p[25]
                        + (int)buf1[buf1Ptr+26] * p[26]
                        + (int)buf1[buf1Ptr+27] * p[27]
                        + (int)buf1[buf1Ptr+28] * p[28]
                        + (int)buf1[buf1Ptr+29] * p[29]
                        + (int)buf1[buf1Ptr+30] * p[30]
                        + (int)buf1[buf1Ptr+31] * p[31]
                        + (int)buf1[buf1Ptr+32] * p[32]
                        + (int)buf1[buf1Ptr+33] * p[33]
                        + (int)buf1[buf1Ptr+34] * p[34]
                        + (int)buf1[buf1Ptr+35] * p[35]
                        + (int)buf1[buf1Ptr+36] * p[36]
                        + (int)buf1[buf1Ptr+37] * p[37]
                        + (int)buf1[buf1Ptr+38] * p[38]
                        + (int)buf1[buf1Ptr+39] * p[39]
                        + (int)buf1[buf1Ptr+40] * p[40]
                        + (int)buf1[buf1Ptr+41] * p[41]
                        + (int)buf1[buf1Ptr+42] * p[42]
                        + (int)buf1[buf1Ptr+43] * p[43]
                        + (int)buf1[buf1Ptr+44] * p[44]
                        + (int)buf1[buf1Ptr+45] * p[45]
                        + (int)buf1[buf1Ptr+46] * p[46]
                        + (int)buf1[buf1Ptr+47] * p[47]
                        + (int)buf1[buf1Ptr+48] * p[48]
                        + (int)buf1[buf1Ptr+49] * p[49]
                        + (int)buf1[buf1Ptr+50] * p[50]
                        + (int)buf1[buf1Ptr+51] * p[51]
                        + (int)buf1[buf1Ptr+52] * p[52]
                        + (int)buf1[buf1Ptr+53] * p[53]
                        + (int)buf1[buf1Ptr+54] * p[54]
                        + (int)buf1[buf1Ptr+55] * p[55]
                        + (int)buf1[buf1Ptr+56] * p[56]
                        + (int)buf1[buf1Ptr+57] * p[57]
                        + (int)buf1[buf1Ptr+58] * p[58]
                        + (int)buf1[buf1Ptr+59] * p[59]
                        + (int)buf1[buf1Ptr+60] * p[60]
                        + (int)buf1[buf1Ptr+61] * p[61]
                        + (int)buf1[buf1Ptr+62] * p[62]
                        + (int)buf1[buf1Ptr+63] * p[63];
            result[1] >>= (16 - 1);
        }
        /*
        void OpmFir_MMX(short *p, short *buf0, short *buf1, int *result) {
            __asm {
                mov  ebx,p
                mov  ecx,buf0
                mov  edx,buf1

                movq mm0,[ebx+8*0];
                movq mm1,[ecx+8*0];
                movq mm2,[edx+8*0];
                pmaddwd mm1,mm0
                pmaddwd mm2,mm0

                movq mm0,[ebx+8*1];
                movq mm3,[ecx+8*1];
                movq mm4,[edx+8*1];
                pmaddwd mm3,mm0
                pmaddwd mm4,mm0
                paddd mm1,mm3
                paddd mm2,mm4

                movq mm0,[ebx+8*2];
                movq mm5,[ecx+8*2];
                movq mm6,[edx+8*2];
                pmaddwd mm5,mm0
                pmaddwd mm6,mm0
                paddd mm1,mm5
                paddd mm2,mm6

                movq mm0,[ebx+8*3];
                movq mm3,[ecx+8*3];
                movq mm4,[edx+8*3];
                pmaddwd mm3,mm0
                pmaddwd mm4,mm0
                paddd mm1,mm3
                paddd mm2,mm4

                movq mm0,[ebx+8*4];
                movq mm5,[ecx+8*4];
                movq mm6,[edx+8*4];
                pmaddwd mm5,mm0
                pmaddwd mm6,mm0
                paddd mm1,mm5
                paddd mm2,mm6

                movq mm0,[ebx+8*5];
                movq mm3,[ecx+8*5];
                movq mm4,[edx+8*5];
                pmaddwd mm3,mm0
                pmaddwd mm4,mm0
                paddd mm1,mm3
                paddd mm2,mm4

                movq mm0,[ebx+8*6];
                movq mm5,[ecx+8*6];
                movq mm6,[edx+8*6];
                pmaddwd mm5,mm0
                pmaddwd mm6,mm0
                paddd mm1,mm5
                paddd mm2,mm6

                movq mm0,[ebx+8*7];
                movq mm3,[ecx+8*7];
                movq mm4,[edx+8*7];
                pmaddwd mm3,mm0
                pmaddwd mm4,mm0
                paddd mm1,mm3
                paddd mm2,mm4

                movq mm0,[ebx+8*8];
                movq mm5,[ecx+8*8];
                movq mm6,[edx+8*8];
                pmaddwd mm5,mm0
                pmaddwd mm6,mm0
                paddd mm1,mm5
                paddd mm2,mm6

                movq mm0,[ebx+8*9];
                movq mm3,[ecx+8*9];
                movq mm4,[edx+8*9];
                pmaddwd mm3,mm0
                pmaddwd mm4,mm0
                paddd mm1,mm3
                paddd mm2,mm4

                movq mm0,[ebx+8*10];
                movq mm5,[ecx+8*10];
                movq mm6,[edx+8*10];
                pmaddwd mm5,mm0
                pmaddwd mm6,mm0
                paddd mm1,mm5
                paddd mm2,mm6

                movq mm0,[ebx+8*11];
                movq mm3,[ecx+8*11];
                movq mm4,[edx+8*11];
                pmaddwd mm3,mm0
                pmaddwd mm4,mm0
                paddd mm1,mm3
                paddd mm2,mm4

                movq mm0,[ebx+8*12];
                movq mm5,[ecx+8*12];
                movq mm6,[edx+8*12];
                pmaddwd mm5,mm0
                pmaddwd mm6,mm0
                paddd mm1,mm5
                paddd mm2,mm6

                movq mm0,[ebx+8*13];
                movq mm3,[ecx+8*13];
                movq mm4,[edx+8*13];
                pmaddwd mm3,mm0
                pmaddwd mm4,mm0
                paddd mm1,mm3
                paddd mm2,mm4

                movq mm0,[ebx+8*14];
                movq mm5,[ecx+8*14];
                movq mm6,[edx+8*14];
                pmaddwd mm5,mm0
                pmaddwd mm6,mm0
                paddd mm1,mm5
                paddd mm2,mm6

                movq mm0,[ebx+8*15];
                movq mm3,[ecx+8*15];
                movq mm4,[edx+8*15];
                pmaddwd mm3,mm0
                pmaddwd mm4,mm0
                paddd mm1,mm3
                paddd mm2,mm4

                mov  ecx,result

                movd eax,mm1
                psrlq mm1,32
                movd ebx,mm1
                add  eax,ebx
                sar  eax,16-1
                mov  [ecx+0],eax

                movd eax,mm2
                psrlq mm2,32
                movd ebx,mm2
                add  eax,ebx
                sar  eax,16-1
                mov  [ecx+4],eax

                emms
            }
        }
        */
        //void OpmFir_MMX(short* p, short* buf0, short* buf1, int* result)
        //{
        //    __asm {
        //        mov ebx, p

        //        mov ecx, buf0

        //        mov edx, buf1


        //        movq mm0,[ebx + 8 * 0];
        //        movq mm1,[ecx + 8 * 0];
        //        movq mm2,[edx + 8 * 0];
        //        pmaddwd mm1, mm0

        //        movq mm3,[ecx + 8 * 1];
        //        pmaddwd mm2, mm0

        //        movq mm0,[ebx + 8 * 1];

        //        movq mm4,[edx + 8 * 1];
        //        pmaddwd mm3, mm0

        //        movq mm5,[ecx + 8 * 2];
        //        pmaddwd mm4, mm0

        //        movq mm6,[edx + 8 * 2];
        //        paddd mm1, mm3

        //        movq mm0,[ebx + 8 * 2];
        //        paddd mm2, mm4


        //        pmaddwd mm5, mm0

        //        movq mm3,[ecx + 8 * 3];
        //        pmaddwd mm6, mm0

        //        movq mm4,[edx + 8 * 3];
        //        paddd mm1, mm5

        //        movq mm0,[ebx + 8 * 3];
        //        paddd mm2, mm6


        //        pmaddwd mm3, mm0

        //        movq mm5,[ecx + 8 * 4];
        //        pmaddwd mm4, mm0

        //        movq mm6,[edx + 8 * 4];
        //        paddd mm1, mm3

        //        movq mm0,[ebx + 8 * 4];
        //        paddd mm2, mm4


        //        pmaddwd mm5, mm0

        //        movq mm3,[ecx + 8 * 5];
        //        pmaddwd mm6, mm0

        //        movq mm4,[edx + 8 * 5];
        //        paddd mm1, mm5

        //        movq mm0,[ebx + 8 * 5];
        //        paddd mm2, mm6


        //        pmaddwd mm3, mm0

        //        movq mm5,[ecx + 8 * 6];
        //        pmaddwd mm4, mm0

        //        movq mm6,[edx + 8 * 6];
        //        paddd mm1, mm3

        //        movq mm0,[ebx + 8 * 6];
        //        paddd mm2, mm4


        //        pmaddwd mm5, mm0

        //        movq mm3,[ecx + 8 * 7];
        //        pmaddwd mm6, mm0

        //        movq mm4,[edx + 8 * 7];
        //        paddd mm1, mm5

        //        movq mm0,[ebx + 8 * 7];
        //        paddd mm2, mm6


        //        pmaddwd mm3, mm0

        //        movq mm5,[ecx + 8 * 8];
        //        pmaddwd mm4, mm0

        //        movq mm6,[edx + 8 * 8];
        //        paddd mm1, mm3

        //        movq mm0,[ebx + 8 * 8];
        //        paddd mm2, mm4


        //        pmaddwd mm5, mm0

        //        movq mm3,[ecx + 8 * 9];
        //        pmaddwd mm6, mm0

        //        movq mm4,[edx + 8 * 9];
        //        paddd mm1, mm5

        //        movq mm0,[ebx + 8 * 9];
        //        paddd mm2, mm6


        //        pmaddwd mm3, mm0

        //        movq mm5,[ecx + 8 * 10];
        //        pmaddwd mm4, mm0

        //        movq mm6,[edx + 8 * 10];
        //        paddd mm1, mm3

        //        movq mm0,[ebx + 8 * 10];
        //        paddd mm2, mm4


        //        pmaddwd mm5, mm0

        //        movq mm3,[ecx + 8 * 11];
        //        pmaddwd mm6, mm0

        //        movq mm4,[edx + 8 * 11];
        //        paddd mm1, mm5

        //        movq mm0,[ebx + 8 * 11];
        //        paddd mm2, mm6


        //        pmaddwd mm3, mm0

        //        movq mm5,[ecx + 8 * 12];
        //        pmaddwd mm4, mm0

        //        movq mm6,[edx + 8 * 12];
        //        paddd mm1, mm3

        //        movq mm0,[ebx + 8 * 12];
        //        paddd mm2, mm4


        //        pmaddwd mm5, mm0

        //        movq mm3,[ecx + 8 * 13];
        //        pmaddwd mm6, mm0

        //        movq mm4,[edx + 8 * 13];
        //        paddd mm1, mm5

        //        movq mm0,[ebx + 8 * 13];
        //        paddd mm2, mm6


        //        pmaddwd mm3, mm0

        //        movq mm5,[ecx + 8 * 14];
        //        pmaddwd mm4, mm0

        //        movq mm6,[edx + 8 * 14];
        //        paddd mm1, mm3

        //        movq mm0,[ebx + 8 * 14];
        //        paddd mm2, mm4


        //        pmaddwd mm5, mm0

        //        movq mm3,[ecx + 8 * 15];
        //        pmaddwd mm6, mm0

        //        movq mm4,[edx + 8 * 15];
        //        paddd mm1, mm5

        //        movq mm0,[ebx + 8 * 15];
        //        paddd mm2, mm6


        //        pmaddwd mm3, mm0

        //        pmaddwd mm4, mm0


        //        mov edi, result


        //        paddd mm1, mm3

        //        paddd mm2, mm4


        //        movd eax, mm1

        //        psrlq mm1,32

        //        movd ecx, mm2

        //        psrlq mm2,32

        //        movd ebx, mm1

        //        movd edx, mm2

        //        add eax, ebx

        //        add ecx, edx

        //        sar eax,16 - 1

        //        sar ecx,16 - 1

        //        mov[edi + 0],eax

        //     mov[edi + 4], ecx


        //        emms

        //    }
        //}


        //void (* OpmFir) (short*, short*, short*, int*) = OpmFir_Normal;

        //void DetectMMX()
        //{
        //    int flag;
        //    __asm {
        //        mov eax,1

        //        cpuid
        //        and edx,0x00800000

        //        mov flag, edx

        //    }
        //    if (flag)
        //    {
        //        OpmFir = OpmFir_MMX;
        //    }
        //    else
        //    {
        //        OpmFir = OpmFir_Normal;
        //    }
        //}



        //public final int WAVE_MAPPER = unchecked((int)(-1));
        //public final int CALLBACK_NULL = 0x00000000; /* no callback */
        //public final int CALLBACK_WINDOW = 0x00010000; /* dwCallback is a HWND */
        //public final int CALLBACK_TASK = 0x00020000; /* dwCallback is a HTASK */
        //public final int CALLBACK_FUNCTION = 0x00030000; /* dwCallback is a FARPROC */
        //public final int TIME_PERIODIC = 1;

        //public IntPtr hwo = IntPtr.Zero;
        ////public List<WinAPI.WaveHdr> WaveHdrList = new ArrayList<WinAPI.WaveHdr>();
        //public IntPtr thread_handle;

    }

