package org.apache.lucene.util;

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

import java.io.File;
import java.io.IOException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MergeScheduler;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.codecs.CodecProvider;
import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.Directory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Random;

public class _TestUtil {

  /** Returns temp dir, containing String arg in its name;
   *  does not create the directory. */
  public static File getTempDir(String desc) {
    return new File(LuceneTestCaseJ4.TEMP_DIR, desc + "." + new Random().nextLong());
  }

  public static void rmDir(File dir) throws IOException {
    if (dir.exists()) {
      File[] files = dir.listFiles();
      for (int i = 0; i < files.length; i++) {
        if (!files[i].delete()) {
          throw new IOException("could not delete " + files[i]);
        }
      }
      dir.delete();
    }
  }

  public static void rmDir(String dir) throws IOException {
    rmDir(new File(dir));
  }

  public static void syncConcurrentMerges(IndexWriter writer) {
    syncConcurrentMerges(writer.getConfig().getMergeScheduler());
  }

  public static void syncConcurrentMerges(MergeScheduler ms) {
    if (ms instanceof ConcurrentMergeScheduler)
      ((ConcurrentMergeScheduler) ms).sync();
  }

  /** This runs the CheckIndex tool on the index in.  If any
   *  issues are hit, a RuntimeException is thrown; else,
   *  true is returned. */
  public static boolean checkIndex(Directory dir) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);

    CheckIndex checker = new CheckIndex(dir);
    checker.setInfoStream(new PrintStream(bos));
    CheckIndex.Status indexStatus = checker.checkIndex();
    if (indexStatus == null || indexStatus.clean == false) {
      System.out.println("CheckIndex failed");
      System.out.println(bos.toString());
      throw new RuntimeException("CheckIndex failed");
    } else
      return true;
  }

  /** Use only for testing.
   *  @deprecated -- in 3.0 we can use Arrays.toString
   *  instead */
  @Deprecated
  public static String arrayToString(int[] array) {
    StringBuilder buf = new StringBuilder();
    buf.append("[");
    for(int i=0;i<array.length;i++) {
      if (i > 0) {
        buf.append(" ");
      }
      buf.append(array[i]);
    }
    buf.append("]");
    return buf.toString();
  }

  /** Use only for testing.
   *  @deprecated -- in 3.0 we can use Arrays.toString
   *  instead */
  @Deprecated
  public static String arrayToString(Object[] array) {
    StringBuilder buf = new StringBuilder();
    buf.append("[");
    for(int i=0;i<array.length;i++) {
      if (i > 0) {
        buf.append(" ");
      }
      buf.append(array[i]);
    }
    buf.append("]");
    return buf.toString();
  }
  /** start and end are BOTH inclusive */
  public static int nextInt(Random r, int start, int end) {
    return start + r.nextInt(end-start+1);
  }

  /** Returns random string, including full unicode range. */
  public static String randomUnicodeString(Random r) {
    return randomUnicodeString(r, 20);
  }

  public static String randomUnicodeString(Random r, int maxLength) {
    final int end = r.nextInt(maxLength);
    if (end == 0) {
      // allow 0 length
      return "";
    }
    final char[] buffer = new char[end];
    for (int i = 0; i < end; i++) {

      int t = r.nextInt(5);

      //buffer[i] = (char) (97 + r.nextInt(26));

      /*
      if (0 == t && i < end - 1) {
        // hi
        buffer[i++] = (char) 0xd800;
        // lo
        buffer[i] = (char) 0xdc00;
      } else if (t <= 3) {
        buffer[i] = 'a';
      }  else if (4 == t) {
        buffer[i] = 0xe000;
      }
      */

      if (0 == t && i < end - 1) {
        // Make a surrogate pair
        // High surrogate
        buffer[i++] = (char) nextInt(r, 0xd800, 0xdbff);
        // Low surrogate
        buffer[i] = (char) nextInt(r, 0xdc00, 0xdfff);
      }
      else if (t <= 1) buffer[i] = (char) r.nextInt(0x80);
      else if (2 == t) buffer[i] = (char) nextInt(r, 0x80, 0x800);
      else if (3 == t) buffer[i] = (char) nextInt(r, 0x800, 0xd7ff);
      else if (4 == t) buffer[i] = (char) nextInt(r, 0xe000, 0xffff);
    }
    return new String(buffer, 0, end);
  }

  private static final int[] blockStarts = {
    0x0000, 0x0080, 0x0100, 0x0180, 0x0250, 0x02B0, 0x0300, 0x0370, 0x0400, 
    0x0500, 0x0530, 0x0590, 0x0600, 0x0700, 0x0750, 0x0780, 0x07C0, 0x0800, 
    0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00, 0x0C80, 0x0D00, 
    0x0D80, 0x0E00, 0x0E80, 0x0F00, 0x1000, 0x10A0, 0x1100, 0x1200, 0x1380, 
    0x13A0, 0x1400, 0x1680, 0x16A0, 0x1700, 0x1720, 0x1740, 0x1760, 0x1780, 
    0x1800, 0x18B0, 0x1900, 0x1950, 0x1980, 0x19E0, 0x1A00, 0x1A20, 0x1B00, 
    0x1B80, 0x1C00, 0x1C50, 0x1CD0, 0x1D00, 0x1D80, 0x1DC0, 0x1E00, 0x1F00, 
    0x2000, 0x2070, 0x20A0, 0x20D0, 0x2100, 0x2150, 0x2190, 0x2200, 0x2300, 
    0x2400, 0x2440, 0x2460, 0x2500, 0x2580, 0x25A0, 0x2600, 0x2700, 0x27C0, 
    0x27F0, 0x2800, 0x2900, 0x2980, 0x2A00, 0x2B00, 0x2C00, 0x2C60, 0x2C80, 
    0x2D00, 0x2D30, 0x2D80, 0x2DE0, 0x2E00, 0x2E80, 0x2F00, 0x2FF0, 0x3000, 
    0x3040, 0x30A0, 0x3100, 0x3130, 0x3190, 0x31A0, 0x31C0, 0x31F0, 0x3200, 
    0x3300, 0x3400, 0x4DC0, 0x4E00, 0xA000, 0xA490, 0xA4D0, 0xA500, 0xA640, 
    0xA6A0, 0xA700, 0xA720, 0xA800, 0xA830, 0xA840, 0xA880, 0xA8E0, 0xA900, 
    0xA930, 0xA960, 0xA980, 0xAA00, 0xAA60, 0xAA80, 0xABC0, 0xAC00, 0xD7B0, 
    0xD800, 0xDB80, 0xDC00, 0xE000, 0xF900, 0xFB00, 0xFB50, 0xFE00, 0xFE10, 
    0xFE20, 0xFE30, 0xFE50, 0xFE70, 0xFF00, 0xFFF0, 
    0x10000, 0x10080, 0x10100, 0x10140, 0x10190, 0x101D0, 0x10280, 0x102A0, 
    0x10300, 0x10330, 0x10380, 0x103A0, 0x10400, 0x10450, 0x10480, 0x10800, 
    0x10840, 0x10900, 0x10920, 0x10A00, 0x10A60, 0x10B00, 0x10B40, 0x10B60, 
    0x10C00, 0x10E60, 0x11080, 0x12000, 0x12400, 0x13000, 0x1D000, 0x1D100, 
    0x1D200, 0x1D300, 0x1D360, 0x1D400, 0x1F000, 0x1F030, 0x1F100, 0x1F200, 
    0x20000, 0x2A700, 0x2F800, 0xE0000, 0xE0100, 0xF0000, 0x100000
  };
  
  private static final int[] blockEnds = {
    0x007F, 0x00FF, 0x017F, 0x024F, 0x02AF, 0x02FF, 0x036F, 0x03FF, 0x04FF, 
    0x052F, 0x058F, 0x05FF, 0x06FF, 0x074F, 0x077F, 0x07BF, 0x07FF, 0x083F, 
    0x097F, 0x09FF, 0x0A7F, 0x0AFF, 0x0B7F, 0x0BFF, 0x0C7F, 0x0CFF, 0x0D7F, 
    0x0DFF, 0x0E7F, 0x0EFF, 0x0FFF, 0x109F, 0x10FF, 0x11FF, 0x137F, 0x139F, 
    0x13FF, 0x167F, 0x169F, 0x16FF, 0x171F, 0x173F, 0x175F, 0x177F, 0x17FF, 
    0x18AF, 0x18FF, 0x194F, 0x197F, 0x19DF, 0x19FF, 0x1A1F, 0x1AAF, 0x1B7F, 
    0x1BBF, 0x1C4F, 0x1C7F, 0x1CFF, 0x1D7F, 0x1DBF, 0x1DFF, 0x1EFF, 0x1FFF, 
    0x206F, 0x209F, 0x20CF, 0x20FF, 0x214F, 0x218F, 0x21FF, 0x22FF, 0x23FF, 
    0x243F, 0x245F, 0x24FF, 0x257F, 0x259F, 0x25FF, 0x26FF, 0x27BF, 0x27EF, 
    0x27FF, 0x28FF, 0x297F, 0x29FF, 0x2AFF, 0x2BFF, 0x2C5F, 0x2C7F, 0x2CFF, 
    0x2D2F, 0x2D7F, 0x2DDF, 0x2DFF, 0x2E7F, 0x2EFF, 0x2FDF, 0x2FFF, 0x303F, 
    0x309F, 0x30FF, 0x312F, 0x318F, 0x319F, 0x31BF, 0x31EF, 0x31FF, 0x32FF, 
    0x33FF, 0x4DBF, 0x4DFF, 0x9FFF, 0xA48F, 0xA4CF, 0xA4FF, 0xA63F, 0xA69F, 
    0xA6FF, 0xA71F, 0xA7FF, 0xA82F, 0xA83F, 0xA87F, 0xA8DF, 0xA8FF, 0xA92F, 
    0xA95F, 0xA97F, 0xA9DF, 0xAA5F, 0xAA7F, 0xAADF, 0xABFF, 0xD7AF, 0xD7FF, 
    0xDB7F, 0xDBFF, 0xDFFF, 0xF8FF, 0xFAFF, 0xFB4F, 0xFDFF, 0xFE0F, 0xFE1F, 
    0xFE2F, 0xFE4F, 0xFE6F, 0xFEFF, 0xFFEF, 0xFFFF, 
    0x1007F, 0x100FF, 0x1013F, 0x1018F, 0x101CF, 0x101FF, 0x1029F, 0x102DF, 
    0x1032F, 0x1034F, 0x1039F, 0x103DF, 0x1044F, 0x1047F, 0x104AF, 0x1083F, 
    0x1085F, 0x1091F, 0x1093F, 0x10A5F, 0x10A7F, 0x10B3F, 0x10B5F, 0x10B7F, 
    0x10C4F, 0x10E7F, 0x110CF, 0x123FF, 0x1247F, 0x1342F, 0x1D0FF, 0x1D1FF, 
    0x1D24F, 0x1D35F, 0x1D37F, 0x1D7FF, 0x1F02F, 0x1F09F, 0x1F1FF, 0x1F2FF, 
    0x2A6DF, 0x2B73F, 0x2FA1F, 0xE007F, 0xE01EF, 0xFFFFF, 0x10FFFF
  };
  
  /** Returns random string, all codepoints within the same unicode block. */
  public static String randomRealisticUnicodeString(Random r) {
    return randomRealisticUnicodeString(r, 20);
  }
  
  /** Returns random string, all codepoints within the same unicode block. */
  public static String randomRealisticUnicodeString(Random r, int maxLength) {
    final int end = r.nextInt(maxLength);
    final int block = r.nextInt(blockStarts.length);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < end; i++)
      sb.appendCodePoint(nextInt(r, blockStarts[block], blockEnds[block]));
    return sb.toString();
  }

  /** gets a random multiplier, which you should use when writing
   *  random tests: multiply it by the number of iterations */
  public static int getRandomMultiplier() {
    return Integer.parseInt(System.getProperty("random.multiplier", "1"));
  }

  /** gets the codec to run tests with */
  public static String getTestCodec() {
    return System.getProperty("tests.codec", "Standard");
  }

  public static CodecProvider alwaysCodec(final Codec c) {
    return new CodecProvider() {
      @Override
      public Codec getWriter(SegmentWriteState state) {
        return c;
      }

      @Override
      public Codec lookup(String name) {
        // can't do this until we fix PreFlexRW to not
        //impersonate PreFlex:
        //return CodecProvider.getDefault().lookup(name);
        return c;
      }
    };
  }

  /** Return a CodecProvider that can read any of the
   *  default codecs, but always writes in the specified
   *  codec. */
  public static CodecProvider alwaysCodec(final String codec) {
    return alwaysCodec(CodecProvider.getDefault().lookup(codec));
  }
}
