package org.apache.lucene.util.encoding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.TreeSet;

import org.junit.Test;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.encoding.DGapIntEncoder;
import org.apache.lucene.util.encoding.EightFlagsIntEncoder;
import org.apache.lucene.util.encoding.FourFlagsIntEncoder;
import org.apache.lucene.util.encoding.IntDecoder;
import org.apache.lucene.util.encoding.IntEncoder;
import org.apache.lucene.util.encoding.NOnesIntEncoder;
import org.apache.lucene.util.encoding.SimpleIntEncoder;
import org.apache.lucene.util.encoding.SortingIntEncoder;
import org.apache.lucene.util.encoding.UniqueValuesIntEncoder;
import org.apache.lucene.util.encoding.VInt8IntEncoder;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class EncodingTest extends LuceneTestCase {

  static int[] data = null;

  private static TreeSet<Long> dataSet = new TreeSet<Long>();
  static {
    setData();
  }

  @Test
  public void testVInt8() throws Exception {
    encoderTest(new VInt8IntEncoder());
    
    // cover negative numbers;
    IntEncoder enc = new VInt8IntEncoder();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    enc.reInit(baos);
    enc.encode(-1);
    
    IntDecoder dec = enc.createMatchingDecoder();
    dec.reInit(new ByteArrayInputStream(baos.toByteArray()));
    assertEquals(-1, dec.decode());
  }
  
  @Test
  public void testSimpleInt() {
    encoderTest(new SimpleIntEncoder());
  }
  
  @Test
  public void testSortingUniqueValues() {
    encoderTest(new SortingIntEncoder(new UniqueValuesIntEncoder(new VInt8IntEncoder())));
  }

  @Test
  public void testSortingUniqueDGap() {
    encoderTest(new SortingIntEncoder(new UniqueValuesIntEncoder(new DGapIntEncoder(new VInt8IntEncoder()))));
  }

  @Test
  public void testSortingUniqueDGapEightFlags() {
    encoderTest(new SortingIntEncoder(new UniqueValuesIntEncoder(new DGapIntEncoder(new EightFlagsIntEncoder()))));
  }

  @Test
  public void testSortingUniqueDGapFourFlags() {
    encoderTest(new SortingIntEncoder(new UniqueValuesIntEncoder(new DGapIntEncoder(new FourFlagsIntEncoder()))));
  }

  @Test
  public void testSortingUniqueDGapNOnes4() {
    encoderTest(new SortingIntEncoder(new UniqueValuesIntEncoder(new DGapIntEncoder(new NOnesIntEncoder(4)))));
  }
  
  @Test
  public void testSortingUniqueDGapNOnes3() {
    encoderTest(new SortingIntEncoder(new UniqueValuesIntEncoder(new DGapIntEncoder(new NOnesIntEncoder(3)))));
  }

  private static void encoderTest(IntEncoder encoder) {

    // ensure toString is implemented
    String toString = encoder.toString();
    assertFalse(toString.startsWith(encoder.getClass().getName() + "@"));
    IntDecoder decoder = encoder.createMatchingDecoder();
    toString = decoder.toString();
    assertFalse(toString.startsWith(decoder.getClass().getName() + "@"));
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try {
      encoding(encoder, baos);
      decoding(baos, encoder.createMatchingDecoder());
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
    
    baos.reset();

    try {
      encoding(encoder, baos);
      decoding(baos, encoder.createMatchingDecoder());
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  private static void encoding(IntEncoder encoder, ByteArrayOutputStream baos) throws IOException {
    encoder.reInit(baos);
    for (int value : data) {
      encoder.encode(value);
    }
    encoder.close();

    baos.reset();
    encoder.reInit(baos);
    for (int value : data) {
      encoder.encode(value);
    }
    encoder.close();
  }

  private static void decoding(ByteArrayOutputStream baos, IntDecoder decoder)
      throws IOException, InstantiationException, IllegalAccessException {
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    decoder.reInit(bais);
    
    HashSet<Long> set = new HashSet<Long>();
    long value = 0;
    while ((value = decoder.decode()) != IntDecoder.EOS) {
      set.add(value);
    }
    assertEquals(dataSet.size(), set.size());
    assertTrue(set.equals(dataSet));
    
    set.clear();
    bais.reset();
    decoder.reInit(bais);
    value = 0;
    while ((value = decoder.decode()) != IntDecoder.EOS) {
      set.add(value);
    }
    assertEquals(dataSet.size(), set.size());
    assertTrue(set.equals(dataSet));

  }

  private static void setData() {
    data = new int[] { 2, 4, 86133, 11, 16505, 86134, 86135, 86136, 1290,
        86137, 86138, 32473, 19346, 32474, 4922, 32475, 86139, 16914,
        86140, 86141, 86142, 86143, 32478, 86144, 86145, 32480, 4884,
        4887, 32481, 86146, 16572, 86147, 16295, 165, 86148, 3183,
        21920, 21921, 21922, 555, 4006, 32484, 21925, 21926, 13775,
        86149, 13777, 85833, 85834, 13779, 13773, 13780, 75266, 17674,
        13784, 13785, 13786, 13787, 13788, 6258, 86150, 13790, 75267,
        13793, 13794, 13795, 312, 4914, 4915, 6222, 86151, 4845, 4883,
        4918, 4894, 4919, 86152, 4921, 6223, 6224, 6225, 6226, 67909,
        6229, 18170, 6230, 5198, 25625, 6231, 6232, 6233, 1808, 6234,
        6235, 6236, 41376, 6238, 6239, 67911, 6240, 86153, 6243, 6244,
        83549, 6246, 6247, 6248, 6249, 782, 444, 6251, 6250, 19863,
        28963, 310, 2234, 144, 2236, 2309, 69437, 2311, 2325, 2241,
        69438, 69439, 2244, 2245, 2246, 23504, 2314, 69440, 36603,
        2250, 2268, 2271, 2251, 2254, 2255, 2257, 2240, 36604, 84726,
        36605, 84727, 2262, 2263, 18431, 38853, 2317, 2149, 2326, 2327,
        2329, 3980, 2275, 2277, 2258, 84728, 2260, 84729, 84730, 13766,
        36607, 2282, 2283, 84731, 2284, 2286, 2287, 2337, 7424, 2288,
        2338, 3522, 2290, 84733, 32902, 371, 37708, 2096, 3065, 3066,
        375, 377, 374, 378, 2100, 86154, 381, 382, 58795, 379, 383,
        384, 385, 4449, 387, 388, 389, 390, 9052, 391, 18358, 2107,
        394, 2111, 2108, 393, 2109, 395, 86155, 86156, 397, 2113, 398,
        399, 400, 273, 274, 275, 40980, 276, 277, 31716, 279, 280,
        31717, 281, 282, 1628, 1623, 1624, 1625, 2052, 1626, 725, 727,
        728, 729, 730, 731, 1633, 733, 734, 735, 86157, 737, 738, 739,
        1634, 3563, 3564, 3565, 1667, 12461, 76276, 3567, 5413, 77622,
        5415, 5416, 5417, 5418, 107, 86158, 7784, 15363, 153, 3723,
        2713, 7786, 3835, 7787, 86159, 7789, 7791, 7792, 7794, 86160,
        7796, 86161, 6708, 7798, 7799, 7800, 7801, 7802, 7803, 1665,
        43150, 15365, 1581, 5656, 43152, 80258, 7450, 39922, 86162,
        51587, 9059, 4606, 396, 86163, 86164, 7250, 401, 403, 2860,
        33281, 2964, 408, 9119, 409, 86165, 7669, 2861, 410, 413,
        86166, 414, 415, 33282, 405, 33283, 7498, 2865, 7230, 33284,
        2866, 86167, 2867, 47518, 2868, 86168, 2869, 2870, 4712, 7096,
        28484, 6913, 6914, 6915, 6916, 37169, 37170, 7103, 28269, 6919,
        86169, 45431, 6922, 7104, 6923, 7108, 6924, 6925, 6926, 6927,
        6928, 86170, 86171, 86172, 6930, 6931, 6932, 6934, 6935, 6936,
        451, 6937, 6938, 4756, 3554, 5309, 8145, 3586, 16417, 9767,
        14126, 25854, 6580, 10174, 86173, 5519, 21309, 8561, 20938,
        10386, 86174, 781, 2030, 16419, 30323, 16420, 16421, 16424,
        86175, 86176, 86177, 28871, 86178, 28872, 63980, 6329, 49561,
        4271, 38778, 86179, 86180, 20126, 16245, 193, 195, 196, 197,
        56973, 199, 200, 201, 202, 203, 204, 56974, 56975, 205, 206,
        4662, 207, 208, 209, 210, 211, 212, 47901, 641, 642, 643, 1380,
        1079, 47902, 1381, 1081, 1082, 1083, 47903, 1382, 47904, 1087,
        47905, 965, 966, 1298, 968, 1387, 1300, 50288, 971, 972, 973,
        974, 23974, 22183, 1390, 23313, 1389, 1391, 902, 23029, 296,
        1304, 1395, 1303, 1309, 1308, 50289, 1312, 50290, 50291, 1315,
        1317, 9270, 19796, 3605, 1320, 1321, 44946, 1322, 1323, 50292,
        967, 1587, 1326, 1331, 17482, 633, 29115, 53858, 29118, 29119,
        62624, 44494, 6965, 6966, 6959, 6967, 71562, 6969, 23459,
        23460, 17464, 4225, 23461, 23462, 23463, 5893, 23464, 17467,
        17468, 23465, 12562, 1405, 1406, 1407, 960, 961, 962, 687, 963,
        86181, 86182, 5997, 10812, 11976, 11977, 1850, 577, 13393,
        10810, 13394, 65040, 86183, 3935, 3936, 3937, 710, 86184, 5785,
        5786, 29949, 5787, 5788, 283, 284, 2687, 285, 286, 287, 2689,
        288, 289, 8880, 290, 2690, 13899, 991, 292, 295, 42007, 35616,
        63103, 298, 299, 3520, 297, 9024, 303, 301, 302, 300, 31345,
        3719, 304, 305, 306, 307, 308, 368, 364, 85002, 9026, 63105,
        367, 39596, 25835, 19746, 293, 294, 26505, 85003, 18377, 56785,
        10122, 10123, 10124, 86185, 39863, 86186, 10125, 39865, 4066,
        4067, 24257, 4068, 4070, 86187, 4073, 4074, 86188, 4076, 7538,
        4077, 86189, 4078, 4079, 7540, 7541, 4084, 4085, 7542, 86190,
        4086, 86191, 4087, 4088, 86192, 7545, 44874, 7821, 44875,
        86193, 4286, 86194, 51470, 17609, 1408, 47486, 1411, 1412,
        47487, 1413, 1414, 1417, 1415, 47488, 1416, 1418, 1420, 470,
        1422, 1423, 1424, 5001, 5002, 47489, 1427, 1429, 1430, 31811,
        1432, 1433, 47490, 1435, 3753, 1437, 1439, 1440, 47491, 1443,
        47492, 1446, 5004, 5005, 1450, 47493, 353, 1452, 42145, 3103,
        3402, 3104, 3105, 4780, 3106, 3107, 3108, 12157, 3111, 42146,
        42147, 3114, 4782, 42148, 3116, 3117, 42149, 42150, 3407, 3121,
        3122, 18154, 3126, 3127, 3128, 3410, 3130, 3411, 3412, 3415,
        24241, 3417, 3418, 3449, 42151, 3421, 3422, 7587, 42152, 3424,
        3427, 3428, 3448, 3430, 3432, 42153, 42154, 41648, 1991, 407,
        57234, 411, 2862, 57235, 2863, 18368, 57236, 2874, 7350, 4115,
        2876, 2877, 17975, 86195, 4116, 2881, 2882, 2883, 2886, 463,
        870, 872, 873, 874, 875, 8783, 8784, 877, 1480, 1481, 459,
        2778, 881, 8785, 2779, 8786, 8787, 8788, 886, 887, 8789, 889,
        8790, 86196, 6920, 86197, 5080, 5081, 7395, 7396, 9395, 9396,
        1528, 42737, 805, 86198, 1209, 13595, 4126, 9680, 34368, 9682,
        86199, 86200, 174, 175, 176, 177, 178, 179, 180, 182, 183,
        1477, 31138, 186, 172, 187, 188, 189, 190, 191, 458, 871,
        31294, 31295, 27604, 31296, 31297, 882, 883, 884, 31298, 890,
        1089, 1488, 1489, 1092, 1093, 1094, 1095, 1096, 1097, 1490,
        1098, 1495, 1502, 1099, 1100, 1101, 1493, 2997, 12223, 1103,
        2654, 1498, 1499, 1500, 80615, 80616, 80617, 33359, 86201,
        9294, 1501, 86202, 1506, 1507, 23454, 38802, 38803, 1014,
        86203, 5583, 5584, 651, 74717, 5586, 5587, 5588, 5589, 74720,
        5590, 38808, 33527, 78330, 10930, 5119, 10931, 1000, 10928,
        10932, 10933, 10934, 10935, 5863, 10936, 86204, 10938, 10939,
        86205, 192, 194, 38754, 38755, 198, 38756, 38757, 38758, 2842,
        640, 22780, 22781, 1080, 86206, 86207, 1084, 1086, 1088, 63916,
        9412, 970, 9413, 9414, 9415, 9416, 9417, 1310, 7168, 7169,
        1318, 9418, 1324, 39159, 1804, 1557, 24850, 41499, 1560, 41500,
        1562, 1563, 1565, 1927, 1928, 1566, 1569, 1570, 1571, 1572,
        1573, 1574, 1575, 1576, 2674, 2677, 2678, 2679, 2946, 2682,
        2676, 2683, 2947, 1156, 1157, 1158, 1467, 1160, 1468, 1469,
        1161, 1162, 1163, 4369, 1165, 1166, 1167, 12923, 2917, 1169,
        1170, 1171, 1172, 1173, 1174, 1175, 1176, 1177, 18153, 8359,
        1178, 1164, 1191, 1180, 12924, 86208, 86209, 54817, 66962,
        2476, 86210, 86211, 41820, 41821, 41822, 41824, 1130, 1131,
        1132, 32692, 1134, 34848, 1136, 1133, 1137, 1138, 1139, 1140,
        1141, 1143, 1144, 1145, 34849, 2639, 34850, 1146, 1147, 1148,
        34851, 1150, 1151, 1152, 1153, 1154, 1155, 1678, 1679, 1680,
        1681, 40870, 2059, 1685, 1686, 32686, 14970, 1688, 1689, 86212,
        1692, 1682, 1693, 1695, 1696, 1698, 12955, 8909, 41690, 1700,
        41691, 86213, 30949, 41692, 1703, 1704, 1705, 41693, 14976,
        1708, 2071, 1709, 1710, 1711, 1712, 1727, 86214, 86215, 86216,
        1715, 86217, 1714, 1717, 1690, 41697, 86218, 1720, 86219, 2073,
        41699, 1724, 2075, 1726, 1729, 1730, 1732, 2078, 2223, 1735,
        1713, 41700, 1737, 14977, 1739, 1740, 1741, 2080, 1743, 1744,
        1745, 1746, 1747, 1748, 1749, 1750, 1751, 41701, 1752, 1753,
        1909, 86220, 2085, 1754, 19548, 86221, 19551, 5733, 3856, 5190,
        4581, 25145, 86222, 86223, 4846, 86224, 4861, 86225, 86226,
        86227, 25150, 86228, 86229, 13820, 2027, 4898, 4899, 4901,
        2135, 4902, 4868, 4904, 86230, 4905, 25155, 4907, 86231, 4909,
        4910, 4911, 4912, 86232, 6220, 81357, 86233, 2589, 73877,
        29706, 6227, 6228, 86234, 6237, 86235, 6241, 6242, 1812, 13808,
        13809, 70908, 2293, 2294, 86236, 2295, 2296, 2297, 22947,
        16511, 2299, 2300, 2301, 13097, 73079, 86237, 13099, 50121,
        86238, 86239, 13101, 86240, 2424, 4725, 4726, 4727, 4728, 4729,
        4730, 86241, 26881, 10944, 4734, 4735, 4736, 26239, 26240,
        71408, 86242, 57401, 71410, 26244, 5344, 26245, 86243, 4102,
        71414, 11091, 6736, 86244, 6737, 6738, 38152, 6740, 6741, 6742,
        6298, 6743, 6745, 6746, 20867, 6749, 20616, 86245, 9801, 65297,
        20617, 65298, 20619, 5629, 65299, 20621, 20622, 8385, 20623,
        20624, 5191, 20625, 20626, 442, 443, 445, 27837, 77681, 86246,
        27839, 86247, 86248, 41435, 66511, 2478, 2479, 2480, 2481,
        2482, 2483, 2484, 2485, 2486, 2487, 2488, 2489, 2490, 2494,
        2493, 33025, 12084, 2542, 2497, 2499, 2501, 2503, 2504, 2505,
        33026, 2506, 2507, 2508, 2509, 2511, 1787, 12080, 2513, 2514,
        3988, 3176, 3989, 2518, 2521, 9285, 2522, 2524, 2525, 3990,
        2527, 2528, 27499, 2529, 2530, 3991, 2532, 2534, 2535, 18038,
        2536, 2538, 2495, 46077, 61493, 61494, 1006, 713, 4971, 4972,
        4973, 4975, 4976, 650, 170, 7549, 7550, 7551, 7552, 7553,
        86249, 7936, 956, 11169, 11170, 1249, 1244, 1245, 1247, 2544,
        1250, 2545, 1252, 2547, 1253, 1254, 2549, 39636, 1259, 1257,
        1258, 39637, 1260, 1261, 2551, 1262, 1263, 848, 86250, 86251,
        854, 74596, 856, 1957, 86252, 855, 1959, 1961, 857, 86253, 851,
        859, 860, 862, 1964, 864, 865, 866, 867, 1965, 1966, 1967,
        1968, 1969, 86254, 1971, 1972, 1973, 1974, 1975, 1976, 1977,
        841, 1954, 842, 2978, 846, 847, 849, 850, 852, 1956, 17452,
        71941, 86255, 86256, 73665, 1471, 13690, 185, 503, 504, 2342,
        505, 506, 4378, 508, 4379, 17313, 510, 511, 512, 520, 513,
        4384, 17314, 514, 515, 46158, 17317, 518, 34269, 519, 4386,
        523, 524, 525, 46159, 528, 529, 17319, 531, 532, 533, 534, 535,
        7482, 537, 538, 5267, 536, 539, 541, 540, 19858, 17320, 17321,
        906, 907, 908, 17322, 910, 17323, 912, 15850, 913, 4398, 17324,
        86257, 278, 2948, 2949, 2950, 3007, 2951, 2952, 2953, 2954,
        2955, 3013, 35352, 3014, 3015, 2962, 3016, 33505, 39118, 3017,
        3018, 20492, 4000, 3021, 3022, 35353, 39293, 3024, 18443, 3029,
        9467, 20529, 39119, 8380, 2965, 3030, 3043, 22714, 39120, 2956,
        3035, 39121, 3037, 3038, 2688, 86258, 36675, 30894, 24505,
        8888, 13541, 49728, 27660, 9082, 27661, 365, 366, 2232, 76098,
        7233, 1494, 17391, 606, 607, 611, 610, 612, 614, 615, 613, 616,
        9117, 617, 618, 21155, 1789, 619, 620, 7636, 12019, 621, 622,
        1793, 623, 625, 624, 631, 626, 627, 21578, 21103, 628, 21579,
        629, 9122, 9123, 12189, 9289, 3168, 3169, 630, 632, 634, 21580,
        9121, 635, 636, 637, 21581, 12781, 1801, 638, 639, 1559, 24343,
        9419, 9420, 795, 796, 1611, 86259, 1612, 21551, 21552, 3741,
        1617, 3742, 1615, 1619, 1620, 6301, 3744, 1622, 67685, 8521,
        55937, 9025, 27663, 8881, 13581, 86260, 11592, 44720, 86261,
        63231, 50873, 42925, 52332, 86262, 72706, 17705, 17707, 17708,
        3401, 40217, 1248, 40218, 86263, 7098, 86264, 86265, 1264,
        86266, 1266, 1267, 1268, 1269, 86267, 1271, 1272, 1273, 1274,
        2556, 1275, 1276, 1277, 1278, 1279, 1280, 1282, 1283, 22680,
        11889, 86268, 45662, 7038, 86269, 19315, 45663, 45664, 86270,
        5855, 34002, 49245, 10447, 5663, 86271, 15429, 53877, 49249,
        86272, 86273, 86274, 60128, 60453, 60129, 5552, 31923, 43407,
        4287, 17980, 64977, 86275, 86276, 8234, 86277, 3649, 8240,
        1330, 11999, 1332, 27618, 1334, 1335, 340, 3651, 25640, 18165,
        1343, 4618, 1474, 3653, 75921, 1349, 53519, 1779, 45454, 22778,
        40153, 67677, 63826, 45455, 15128, 67678, 67679, 1792, 67680,
        3171, 47816, 45457, 9288, 59891, 67681, 25703, 35731, 35732,
        369, 35713, 35714, 35715, 34652, 35716, 31681, 35717, 12779,
        35718, 35719, 11992, 806, 807, 808, 43499, 43500, 810, 776,
        812, 813, 814, 241, 43501, 43502, 816, 755, 43503, 818, 819,
        820, 43504, 821, 822, 823, 824, 825, 826, 43505, 43506, 43507,
        828, 829, 20083, 43508, 43509, 832, 833, 834, 835, 86278,
        19984, 19985, 86279, 24125, 19986, 86280, 19988, 86281, 5414,
        86282, 85808, 5479, 5420, 5421, 5422, 5423, 63800, 86283,
        86284, 30965, 86285, 416, 1510, 5740, 5741, 81991, 86286,
        28938, 50149, 1003, 55512, 14306, 6960, 688, 86287, 14307,
        5399, 5400, 17783, 24118, 720, 86288, 44913, 24557, 667, 24876,
        6529, 24877, 24878, 24879, 24880, 31847, 20671, 4011, 171, 580,
        86289, 3863, 914, 2202, 916, 917, 918, 919, 921, 922, 923,
        7585, 925, 7586, 926, 927, 928, 7588, 929, 930, 931, 932, 933,
        934, 1875, 1876, 7589, 7590, 1878, 1879, 7591, 7592, 1882,
        1883, 1884, 2212, 7593, 1887, 1888, 1889, 1890, 1891, 1892,
        1893, 1894, 1895, 1896, 1897, 1898, 2217, 1900, 7594, 1902,
        2219, 7595, 1905, 1906, 1907, 3323, 7596, 1911, 1912, 7597,
        1914, 1915, 1916, 2226, 1919, 7598, 2227, 1920, 1921, 7599,
        7600, 4708, 1923, 355, 356, 1549, 358, 32077, 360, 32078,
        21117, 362, 19043, 71677, 5716, 86290, 49790, 86291, 86292,
        86293, 49792, 86294, 86295, 49794, 86296, 86297, 86298, 86299,
        11882, 86300, 49798, 86301, 49800, 49801, 49802, 49803, 453,
        49804, 8591, 6794, 49806, 18989, 49807, 49808, 16308, 49809,
        86302, 86303, 10105, 86304, 5285, 10106, 10107, 6557, 86305,
        23571, 10109, 38883, 10110, 5401, 86306, 67557, 16430, 67558,
        40171, 16433, 25878, 86307, 21762, 23, 86308, 86309, 21766,
        86310, 86311, 5149, 3926, 21768, 21769, 47826, 942, 46985,
        6588, 58867, 6589, 6590, 86312, 6592, 6006, 53855, 9565, 359,
        86313, 2845, 876, 879, 27556, 27557, 885, 27558, 888, 2847,
        27559, 2115, 2116, 2117, 53962, 57839, 315, 316, 317, 318, 319,
        86314, 321, 322, 2122, 323, 2123, 324, 325, 328, 326, 327,
        40542, 329, 330, 18079, 18080, 331, 1790, 7382, 332, 7380,
        7236, 23413, 23414, 18924, 18925, 333, 335, 336, 39750, 337,
        86315, 339, 341, 342, 343, 16264, 16265, 6615, 86316, 86317,
        86318, 86319, 16269, 10538, 33226, 86320, 16272, 5824, 16273,
        16274, 16276, 16277, 16278, 16279, 16280, 14517, 1547, 6463,
        3394, 49677, 659, 10380, 30013, 10382, 10378, 10379, 10383,
        10384, 10385, 86321, 4139, 13370, 13371, 86322, 86323, 11878,
        64509, 15141, 15142, 15143, 32737, 14183, 15144, 39101, 42768,
        5645, 32738, 801, 803, 804, 86324, 14707, 86325, 6601, 12402,
        712, 12403, 2936, 1447, 15477, 1410, 44872, 1550, 8614, 15478,
        15479, 15480, 15481, 4811, 3752, 1442, 15482, 8818, 1445, 5006,
        16304, 32277, 16305, 16306, 86326, 16307, 53691, 69305, 809,
        86327, 815, 26724, 69307, 43484, 63904, 86328, 13498, 827,
        86329, 831, 2857, 836, 86330, 86331, 837, 838, 839, 840, 228,
        229, 43722, 230, 231, 43723, 234, 235, 236, 237, 238, 239,
        2745, 2746, 240, 242, 243, 244, 43724, 19788, 246, 247, 21134,
        248, 250, 251, 252, 253, 254, 255, 256, 257, 258, 43725, 43726,
        41, 43727, 262, 43728, 2751, 264, 265, 266, 267, 268, 269, 270,
        271, 272, 1024, 1025, 1026, 1027, 1028, 1029, 1030, 1031, 1032,
        1033, 1034, 43729, 1035, 43730, 1037, 21821, 2926, 14388,
        10432, 14389, 14390, 14391, 14392, 86332, 14394, 14395, 2035,
        2169, 86333, 14397, 14398, 14399, 14400, 52, 14401, 14402,
        7077, 21822, 14405, 14406, 14396, 86334, 17356, 17357, 84679,
        84680, 76383, 17360, 17361, 86335, 38801, 2060, 30850, 12963,
        1684, 1687, 2061, 14978, 1694, 43387, 1697, 1699, 2067, 1701,
        1702, 1706, 43388, 43389, 76325, 1716, 1718, 26832, 1719, 1723,
        2081, 2063, 1728, 39059, 76326, 1731, 86336, 1736, 76327, 1738,
        19657, 6579, 6581, 6582, 6583, 6584, 6585, 29979, 1818, 28239,
        68, 69, 3391, 86337, 10266, 63528, 86338, 10269, 10270, 10271,
        10272, 86339, 86340, 63530, 63531, 63532, 63533, 10273, 63534,
        86341, 10681, 10682, 86342, 9673, 86343, 10683, 460, 461, 462,
        467, 4464, 4466, 3729, 471, 472, 468, 81634, 474, 81635, 475,
        476, 477, 479, 480, 81636, 81637, 482, 17442, 81638, 81639,
        484, 485, 486, 4473, 488, 489, 490, 493, 466, 494, 495, 496,
        497, 499, 500, 501, 502, 34376, 86344, 63836, 56281, 1707,
        20416, 61452, 56282, 1755, 56283, 56284, 18508, 53650, 63444,
        86345, 3579, 63445, 3677, 1979, 1980, 1981, 3132, 3147, 34090,
        1987, 12770, 1329, 80818, 80819, 1988, 23522, 1986, 15880,
        1985, 32975, 1992, 1993, 7165, 3141, 3143, 86346, 1982, 1984,
        3145, 86347, 78064, 55453, 2656, 2657, 35634, 35635, 2167,
        43479,
        // ensure there is a representative number for any # of int bytes
        1, 1 << 8 + 1, 1 << 16 + 1, 1 << 24 + 1 };
//    data = new int[]{1, 2, 3, 4};
    for (int value : data) {
      dataSet.add(new Long(value));
    }
  }

}
