// This file has been automatically generated, DO NOT EDIT

package org.apache.lucene.util.packed;

/*
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

/**
 * Efficient sequential read/write of packed integers.
 */
final class BulkOperationPacked37 extends BulkOperation {
    @Override
    public int blockCount() {
      return 37;
    }

    @Override
    public int valueCount() {
      return 64;
    }

    @Override
    public void decode(long[] blocks, int blocksOffset, int[] values, int valuesOffset, int iterations) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void decode(byte[] blocks, int blocksOffset, int[] values, int valuesOffset, int iterations) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void decode(long[] blocks, int blocksOffset, long[] values, int valuesOffset, int iterations) {
      assert blocksOffset + iterations * blockCount() <= blocks.length;
      assert valuesOffset + iterations * valueCount() <= values.length;
      for (int i = 0; i < iterations; ++i) {
        final long block0 = blocks[blocksOffset++];
        values[valuesOffset++] = block0 >>> 27;
        final long block1 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block0 & 134217727L) << 10) | (block1 >>> 54);
        values[valuesOffset++] = (block1 >>> 17) & 137438953471L;
        final long block2 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block1 & 131071L) << 20) | (block2 >>> 44);
        values[valuesOffset++] = (block2 >>> 7) & 137438953471L;
        final long block3 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block2 & 127L) << 30) | (block3 >>> 34);
        final long block4 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block3 & 17179869183L) << 3) | (block4 >>> 61);
        values[valuesOffset++] = (block4 >>> 24) & 137438953471L;
        final long block5 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block4 & 16777215L) << 13) | (block5 >>> 51);
        values[valuesOffset++] = (block5 >>> 14) & 137438953471L;
        final long block6 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block5 & 16383L) << 23) | (block6 >>> 41);
        values[valuesOffset++] = (block6 >>> 4) & 137438953471L;
        final long block7 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block6 & 15L) << 33) | (block7 >>> 31);
        final long block8 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block7 & 2147483647L) << 6) | (block8 >>> 58);
        values[valuesOffset++] = (block8 >>> 21) & 137438953471L;
        final long block9 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block8 & 2097151L) << 16) | (block9 >>> 48);
        values[valuesOffset++] = (block9 >>> 11) & 137438953471L;
        final long block10 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block9 & 2047L) << 26) | (block10 >>> 38);
        values[valuesOffset++] = (block10 >>> 1) & 137438953471L;
        final long block11 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block10 & 1L) << 36) | (block11 >>> 28);
        final long block12 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block11 & 268435455L) << 9) | (block12 >>> 55);
        values[valuesOffset++] = (block12 >>> 18) & 137438953471L;
        final long block13 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block12 & 262143L) << 19) | (block13 >>> 45);
        values[valuesOffset++] = (block13 >>> 8) & 137438953471L;
        final long block14 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block13 & 255L) << 29) | (block14 >>> 35);
        final long block15 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block14 & 34359738367L) << 2) | (block15 >>> 62);
        values[valuesOffset++] = (block15 >>> 25) & 137438953471L;
        final long block16 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block15 & 33554431L) << 12) | (block16 >>> 52);
        values[valuesOffset++] = (block16 >>> 15) & 137438953471L;
        final long block17 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block16 & 32767L) << 22) | (block17 >>> 42);
        values[valuesOffset++] = (block17 >>> 5) & 137438953471L;
        final long block18 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block17 & 31L) << 32) | (block18 >>> 32);
        final long block19 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block18 & 4294967295L) << 5) | (block19 >>> 59);
        values[valuesOffset++] = (block19 >>> 22) & 137438953471L;
        final long block20 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block19 & 4194303L) << 15) | (block20 >>> 49);
        values[valuesOffset++] = (block20 >>> 12) & 137438953471L;
        final long block21 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block20 & 4095L) << 25) | (block21 >>> 39);
        values[valuesOffset++] = (block21 >>> 2) & 137438953471L;
        final long block22 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block21 & 3L) << 35) | (block22 >>> 29);
        final long block23 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block22 & 536870911L) << 8) | (block23 >>> 56);
        values[valuesOffset++] = (block23 >>> 19) & 137438953471L;
        final long block24 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block23 & 524287L) << 18) | (block24 >>> 46);
        values[valuesOffset++] = (block24 >>> 9) & 137438953471L;
        final long block25 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block24 & 511L) << 28) | (block25 >>> 36);
        final long block26 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block25 & 68719476735L) << 1) | (block26 >>> 63);
        values[valuesOffset++] = (block26 >>> 26) & 137438953471L;
        final long block27 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block26 & 67108863L) << 11) | (block27 >>> 53);
        values[valuesOffset++] = (block27 >>> 16) & 137438953471L;
        final long block28 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block27 & 65535L) << 21) | (block28 >>> 43);
        values[valuesOffset++] = (block28 >>> 6) & 137438953471L;
        final long block29 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block28 & 63L) << 31) | (block29 >>> 33);
        final long block30 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block29 & 8589934591L) << 4) | (block30 >>> 60);
        values[valuesOffset++] = (block30 >>> 23) & 137438953471L;
        final long block31 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block30 & 8388607L) << 14) | (block31 >>> 50);
        values[valuesOffset++] = (block31 >>> 13) & 137438953471L;
        final long block32 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block31 & 8191L) << 24) | (block32 >>> 40);
        values[valuesOffset++] = (block32 >>> 3) & 137438953471L;
        final long block33 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block32 & 7L) << 34) | (block33 >>> 30);
        final long block34 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block33 & 1073741823L) << 7) | (block34 >>> 57);
        values[valuesOffset++] = (block34 >>> 20) & 137438953471L;
        final long block35 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block34 & 1048575L) << 17) | (block35 >>> 47);
        values[valuesOffset++] = (block35 >>> 10) & 137438953471L;
        final long block36 = blocks[blocksOffset++];
        values[valuesOffset++] = ((block35 & 1023L) << 27) | (block36 >>> 37);
        values[valuesOffset++] = block36 & 137438953471L;
      }
    }

    @Override
    public void decode(byte[] blocks, int blocksOffset, long[] values, int valuesOffset, int iterations) {
      assert blocksOffset + 8 * iterations * blockCount() <= blocks.length;
      assert valuesOffset + iterations * valueCount() <= values.length;
      for (int i = 0; i < iterations; ++i) {
        final long byte0 = blocks[blocksOffset++] & 0xFF;
        final long byte1 = blocks[blocksOffset++] & 0xFF;
        final long byte2 = blocks[blocksOffset++] & 0xFF;
        final long byte3 = blocks[blocksOffset++] & 0xFF;
        final long byte4 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = (byte0 << 29) | (byte1 << 21) | (byte2 << 13) | (byte3 << 5) | (byte4 >>> 3);
        final long byte5 = blocks[blocksOffset++] & 0xFF;
        final long byte6 = blocks[blocksOffset++] & 0xFF;
        final long byte7 = blocks[blocksOffset++] & 0xFF;
        final long byte8 = blocks[blocksOffset++] & 0xFF;
        final long byte9 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte4 & 7) << 34) | (byte5 << 26) | (byte6 << 18) | (byte7 << 10) | (byte8 << 2) | (byte9 >>> 6);
        final long byte10 = blocks[blocksOffset++] & 0xFF;
        final long byte11 = blocks[blocksOffset++] & 0xFF;
        final long byte12 = blocks[blocksOffset++] & 0xFF;
        final long byte13 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte9 & 63) << 31) | (byte10 << 23) | (byte11 << 15) | (byte12 << 7) | (byte13 >>> 1);
        final long byte14 = blocks[blocksOffset++] & 0xFF;
        final long byte15 = blocks[blocksOffset++] & 0xFF;
        final long byte16 = blocks[blocksOffset++] & 0xFF;
        final long byte17 = blocks[blocksOffset++] & 0xFF;
        final long byte18 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte13 & 1) << 36) | (byte14 << 28) | (byte15 << 20) | (byte16 << 12) | (byte17 << 4) | (byte18 >>> 4);
        final long byte19 = blocks[blocksOffset++] & 0xFF;
        final long byte20 = blocks[blocksOffset++] & 0xFF;
        final long byte21 = blocks[blocksOffset++] & 0xFF;
        final long byte22 = blocks[blocksOffset++] & 0xFF;
        final long byte23 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte18 & 15) << 33) | (byte19 << 25) | (byte20 << 17) | (byte21 << 9) | (byte22 << 1) | (byte23 >>> 7);
        final long byte24 = blocks[blocksOffset++] & 0xFF;
        final long byte25 = blocks[blocksOffset++] & 0xFF;
        final long byte26 = blocks[blocksOffset++] & 0xFF;
        final long byte27 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte23 & 127) << 30) | (byte24 << 22) | (byte25 << 14) | (byte26 << 6) | (byte27 >>> 2);
        final long byte28 = blocks[blocksOffset++] & 0xFF;
        final long byte29 = blocks[blocksOffset++] & 0xFF;
        final long byte30 = blocks[blocksOffset++] & 0xFF;
        final long byte31 = blocks[blocksOffset++] & 0xFF;
        final long byte32 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte27 & 3) << 35) | (byte28 << 27) | (byte29 << 19) | (byte30 << 11) | (byte31 << 3) | (byte32 >>> 5);
        final long byte33 = blocks[blocksOffset++] & 0xFF;
        final long byte34 = blocks[blocksOffset++] & 0xFF;
        final long byte35 = blocks[blocksOffset++] & 0xFF;
        final long byte36 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte32 & 31) << 32) | (byte33 << 24) | (byte34 << 16) | (byte35 << 8) | byte36;
        final long byte37 = blocks[blocksOffset++] & 0xFF;
        final long byte38 = blocks[blocksOffset++] & 0xFF;
        final long byte39 = blocks[blocksOffset++] & 0xFF;
        final long byte40 = blocks[blocksOffset++] & 0xFF;
        final long byte41 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = (byte37 << 29) | (byte38 << 21) | (byte39 << 13) | (byte40 << 5) | (byte41 >>> 3);
        final long byte42 = blocks[blocksOffset++] & 0xFF;
        final long byte43 = blocks[blocksOffset++] & 0xFF;
        final long byte44 = blocks[blocksOffset++] & 0xFF;
        final long byte45 = blocks[blocksOffset++] & 0xFF;
        final long byte46 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte41 & 7) << 34) | (byte42 << 26) | (byte43 << 18) | (byte44 << 10) | (byte45 << 2) | (byte46 >>> 6);
        final long byte47 = blocks[blocksOffset++] & 0xFF;
        final long byte48 = blocks[blocksOffset++] & 0xFF;
        final long byte49 = blocks[blocksOffset++] & 0xFF;
        final long byte50 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte46 & 63) << 31) | (byte47 << 23) | (byte48 << 15) | (byte49 << 7) | (byte50 >>> 1);
        final long byte51 = blocks[blocksOffset++] & 0xFF;
        final long byte52 = blocks[blocksOffset++] & 0xFF;
        final long byte53 = blocks[blocksOffset++] & 0xFF;
        final long byte54 = blocks[blocksOffset++] & 0xFF;
        final long byte55 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte50 & 1) << 36) | (byte51 << 28) | (byte52 << 20) | (byte53 << 12) | (byte54 << 4) | (byte55 >>> 4);
        final long byte56 = blocks[blocksOffset++] & 0xFF;
        final long byte57 = blocks[blocksOffset++] & 0xFF;
        final long byte58 = blocks[blocksOffset++] & 0xFF;
        final long byte59 = blocks[blocksOffset++] & 0xFF;
        final long byte60 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte55 & 15) << 33) | (byte56 << 25) | (byte57 << 17) | (byte58 << 9) | (byte59 << 1) | (byte60 >>> 7);
        final long byte61 = blocks[blocksOffset++] & 0xFF;
        final long byte62 = blocks[blocksOffset++] & 0xFF;
        final long byte63 = blocks[blocksOffset++] & 0xFF;
        final long byte64 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte60 & 127) << 30) | (byte61 << 22) | (byte62 << 14) | (byte63 << 6) | (byte64 >>> 2);
        final long byte65 = blocks[blocksOffset++] & 0xFF;
        final long byte66 = blocks[blocksOffset++] & 0xFF;
        final long byte67 = blocks[blocksOffset++] & 0xFF;
        final long byte68 = blocks[blocksOffset++] & 0xFF;
        final long byte69 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte64 & 3) << 35) | (byte65 << 27) | (byte66 << 19) | (byte67 << 11) | (byte68 << 3) | (byte69 >>> 5);
        final long byte70 = blocks[blocksOffset++] & 0xFF;
        final long byte71 = blocks[blocksOffset++] & 0xFF;
        final long byte72 = blocks[blocksOffset++] & 0xFF;
        final long byte73 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte69 & 31) << 32) | (byte70 << 24) | (byte71 << 16) | (byte72 << 8) | byte73;
        final long byte74 = blocks[blocksOffset++] & 0xFF;
        final long byte75 = blocks[blocksOffset++] & 0xFF;
        final long byte76 = blocks[blocksOffset++] & 0xFF;
        final long byte77 = blocks[blocksOffset++] & 0xFF;
        final long byte78 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = (byte74 << 29) | (byte75 << 21) | (byte76 << 13) | (byte77 << 5) | (byte78 >>> 3);
        final long byte79 = blocks[blocksOffset++] & 0xFF;
        final long byte80 = blocks[blocksOffset++] & 0xFF;
        final long byte81 = blocks[blocksOffset++] & 0xFF;
        final long byte82 = blocks[blocksOffset++] & 0xFF;
        final long byte83 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte78 & 7) << 34) | (byte79 << 26) | (byte80 << 18) | (byte81 << 10) | (byte82 << 2) | (byte83 >>> 6);
        final long byte84 = blocks[blocksOffset++] & 0xFF;
        final long byte85 = blocks[blocksOffset++] & 0xFF;
        final long byte86 = blocks[blocksOffset++] & 0xFF;
        final long byte87 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte83 & 63) << 31) | (byte84 << 23) | (byte85 << 15) | (byte86 << 7) | (byte87 >>> 1);
        final long byte88 = blocks[blocksOffset++] & 0xFF;
        final long byte89 = blocks[blocksOffset++] & 0xFF;
        final long byte90 = blocks[blocksOffset++] & 0xFF;
        final long byte91 = blocks[blocksOffset++] & 0xFF;
        final long byte92 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte87 & 1) << 36) | (byte88 << 28) | (byte89 << 20) | (byte90 << 12) | (byte91 << 4) | (byte92 >>> 4);
        final long byte93 = blocks[blocksOffset++] & 0xFF;
        final long byte94 = blocks[blocksOffset++] & 0xFF;
        final long byte95 = blocks[blocksOffset++] & 0xFF;
        final long byte96 = blocks[blocksOffset++] & 0xFF;
        final long byte97 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte92 & 15) << 33) | (byte93 << 25) | (byte94 << 17) | (byte95 << 9) | (byte96 << 1) | (byte97 >>> 7);
        final long byte98 = blocks[blocksOffset++] & 0xFF;
        final long byte99 = blocks[blocksOffset++] & 0xFF;
        final long byte100 = blocks[blocksOffset++] & 0xFF;
        final long byte101 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte97 & 127) << 30) | (byte98 << 22) | (byte99 << 14) | (byte100 << 6) | (byte101 >>> 2);
        final long byte102 = blocks[blocksOffset++] & 0xFF;
        final long byte103 = blocks[blocksOffset++] & 0xFF;
        final long byte104 = blocks[blocksOffset++] & 0xFF;
        final long byte105 = blocks[blocksOffset++] & 0xFF;
        final long byte106 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte101 & 3) << 35) | (byte102 << 27) | (byte103 << 19) | (byte104 << 11) | (byte105 << 3) | (byte106 >>> 5);
        final long byte107 = blocks[blocksOffset++] & 0xFF;
        final long byte108 = blocks[blocksOffset++] & 0xFF;
        final long byte109 = blocks[blocksOffset++] & 0xFF;
        final long byte110 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte106 & 31) << 32) | (byte107 << 24) | (byte108 << 16) | (byte109 << 8) | byte110;
        final long byte111 = blocks[blocksOffset++] & 0xFF;
        final long byte112 = blocks[blocksOffset++] & 0xFF;
        final long byte113 = blocks[blocksOffset++] & 0xFF;
        final long byte114 = blocks[blocksOffset++] & 0xFF;
        final long byte115 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = (byte111 << 29) | (byte112 << 21) | (byte113 << 13) | (byte114 << 5) | (byte115 >>> 3);
        final long byte116 = blocks[blocksOffset++] & 0xFF;
        final long byte117 = blocks[blocksOffset++] & 0xFF;
        final long byte118 = blocks[blocksOffset++] & 0xFF;
        final long byte119 = blocks[blocksOffset++] & 0xFF;
        final long byte120 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte115 & 7) << 34) | (byte116 << 26) | (byte117 << 18) | (byte118 << 10) | (byte119 << 2) | (byte120 >>> 6);
        final long byte121 = blocks[blocksOffset++] & 0xFF;
        final long byte122 = blocks[blocksOffset++] & 0xFF;
        final long byte123 = blocks[blocksOffset++] & 0xFF;
        final long byte124 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte120 & 63) << 31) | (byte121 << 23) | (byte122 << 15) | (byte123 << 7) | (byte124 >>> 1);
        final long byte125 = blocks[blocksOffset++] & 0xFF;
        final long byte126 = blocks[blocksOffset++] & 0xFF;
        final long byte127 = blocks[blocksOffset++] & 0xFF;
        final long byte128 = blocks[blocksOffset++] & 0xFF;
        final long byte129 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte124 & 1) << 36) | (byte125 << 28) | (byte126 << 20) | (byte127 << 12) | (byte128 << 4) | (byte129 >>> 4);
        final long byte130 = blocks[blocksOffset++] & 0xFF;
        final long byte131 = blocks[blocksOffset++] & 0xFF;
        final long byte132 = blocks[blocksOffset++] & 0xFF;
        final long byte133 = blocks[blocksOffset++] & 0xFF;
        final long byte134 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte129 & 15) << 33) | (byte130 << 25) | (byte131 << 17) | (byte132 << 9) | (byte133 << 1) | (byte134 >>> 7);
        final long byte135 = blocks[blocksOffset++] & 0xFF;
        final long byte136 = blocks[blocksOffset++] & 0xFF;
        final long byte137 = blocks[blocksOffset++] & 0xFF;
        final long byte138 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte134 & 127) << 30) | (byte135 << 22) | (byte136 << 14) | (byte137 << 6) | (byte138 >>> 2);
        final long byte139 = blocks[blocksOffset++] & 0xFF;
        final long byte140 = blocks[blocksOffset++] & 0xFF;
        final long byte141 = blocks[blocksOffset++] & 0xFF;
        final long byte142 = blocks[blocksOffset++] & 0xFF;
        final long byte143 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte138 & 3) << 35) | (byte139 << 27) | (byte140 << 19) | (byte141 << 11) | (byte142 << 3) | (byte143 >>> 5);
        final long byte144 = blocks[blocksOffset++] & 0xFF;
        final long byte145 = blocks[blocksOffset++] & 0xFF;
        final long byte146 = blocks[blocksOffset++] & 0xFF;
        final long byte147 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte143 & 31) << 32) | (byte144 << 24) | (byte145 << 16) | (byte146 << 8) | byte147;
        final long byte148 = blocks[blocksOffset++] & 0xFF;
        final long byte149 = blocks[blocksOffset++] & 0xFF;
        final long byte150 = blocks[blocksOffset++] & 0xFF;
        final long byte151 = blocks[blocksOffset++] & 0xFF;
        final long byte152 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = (byte148 << 29) | (byte149 << 21) | (byte150 << 13) | (byte151 << 5) | (byte152 >>> 3);
        final long byte153 = blocks[blocksOffset++] & 0xFF;
        final long byte154 = blocks[blocksOffset++] & 0xFF;
        final long byte155 = blocks[blocksOffset++] & 0xFF;
        final long byte156 = blocks[blocksOffset++] & 0xFF;
        final long byte157 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte152 & 7) << 34) | (byte153 << 26) | (byte154 << 18) | (byte155 << 10) | (byte156 << 2) | (byte157 >>> 6);
        final long byte158 = blocks[blocksOffset++] & 0xFF;
        final long byte159 = blocks[blocksOffset++] & 0xFF;
        final long byte160 = blocks[blocksOffset++] & 0xFF;
        final long byte161 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte157 & 63) << 31) | (byte158 << 23) | (byte159 << 15) | (byte160 << 7) | (byte161 >>> 1);
        final long byte162 = blocks[blocksOffset++] & 0xFF;
        final long byte163 = blocks[blocksOffset++] & 0xFF;
        final long byte164 = blocks[blocksOffset++] & 0xFF;
        final long byte165 = blocks[blocksOffset++] & 0xFF;
        final long byte166 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte161 & 1) << 36) | (byte162 << 28) | (byte163 << 20) | (byte164 << 12) | (byte165 << 4) | (byte166 >>> 4);
        final long byte167 = blocks[blocksOffset++] & 0xFF;
        final long byte168 = blocks[blocksOffset++] & 0xFF;
        final long byte169 = blocks[blocksOffset++] & 0xFF;
        final long byte170 = blocks[blocksOffset++] & 0xFF;
        final long byte171 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte166 & 15) << 33) | (byte167 << 25) | (byte168 << 17) | (byte169 << 9) | (byte170 << 1) | (byte171 >>> 7);
        final long byte172 = blocks[blocksOffset++] & 0xFF;
        final long byte173 = blocks[blocksOffset++] & 0xFF;
        final long byte174 = blocks[blocksOffset++] & 0xFF;
        final long byte175 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte171 & 127) << 30) | (byte172 << 22) | (byte173 << 14) | (byte174 << 6) | (byte175 >>> 2);
        final long byte176 = blocks[blocksOffset++] & 0xFF;
        final long byte177 = blocks[blocksOffset++] & 0xFF;
        final long byte178 = blocks[blocksOffset++] & 0xFF;
        final long byte179 = blocks[blocksOffset++] & 0xFF;
        final long byte180 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte175 & 3) << 35) | (byte176 << 27) | (byte177 << 19) | (byte178 << 11) | (byte179 << 3) | (byte180 >>> 5);
        final long byte181 = blocks[blocksOffset++] & 0xFF;
        final long byte182 = blocks[blocksOffset++] & 0xFF;
        final long byte183 = blocks[blocksOffset++] & 0xFF;
        final long byte184 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte180 & 31) << 32) | (byte181 << 24) | (byte182 << 16) | (byte183 << 8) | byte184;
        final long byte185 = blocks[blocksOffset++] & 0xFF;
        final long byte186 = blocks[blocksOffset++] & 0xFF;
        final long byte187 = blocks[blocksOffset++] & 0xFF;
        final long byte188 = blocks[blocksOffset++] & 0xFF;
        final long byte189 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = (byte185 << 29) | (byte186 << 21) | (byte187 << 13) | (byte188 << 5) | (byte189 >>> 3);
        final long byte190 = blocks[blocksOffset++] & 0xFF;
        final long byte191 = blocks[blocksOffset++] & 0xFF;
        final long byte192 = blocks[blocksOffset++] & 0xFF;
        final long byte193 = blocks[blocksOffset++] & 0xFF;
        final long byte194 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte189 & 7) << 34) | (byte190 << 26) | (byte191 << 18) | (byte192 << 10) | (byte193 << 2) | (byte194 >>> 6);
        final long byte195 = blocks[blocksOffset++] & 0xFF;
        final long byte196 = blocks[blocksOffset++] & 0xFF;
        final long byte197 = blocks[blocksOffset++] & 0xFF;
        final long byte198 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte194 & 63) << 31) | (byte195 << 23) | (byte196 << 15) | (byte197 << 7) | (byte198 >>> 1);
        final long byte199 = blocks[blocksOffset++] & 0xFF;
        final long byte200 = blocks[blocksOffset++] & 0xFF;
        final long byte201 = blocks[blocksOffset++] & 0xFF;
        final long byte202 = blocks[blocksOffset++] & 0xFF;
        final long byte203 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte198 & 1) << 36) | (byte199 << 28) | (byte200 << 20) | (byte201 << 12) | (byte202 << 4) | (byte203 >>> 4);
        final long byte204 = blocks[blocksOffset++] & 0xFF;
        final long byte205 = blocks[blocksOffset++] & 0xFF;
        final long byte206 = blocks[blocksOffset++] & 0xFF;
        final long byte207 = blocks[blocksOffset++] & 0xFF;
        final long byte208 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte203 & 15) << 33) | (byte204 << 25) | (byte205 << 17) | (byte206 << 9) | (byte207 << 1) | (byte208 >>> 7);
        final long byte209 = blocks[blocksOffset++] & 0xFF;
        final long byte210 = blocks[blocksOffset++] & 0xFF;
        final long byte211 = blocks[blocksOffset++] & 0xFF;
        final long byte212 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte208 & 127) << 30) | (byte209 << 22) | (byte210 << 14) | (byte211 << 6) | (byte212 >>> 2);
        final long byte213 = blocks[blocksOffset++] & 0xFF;
        final long byte214 = blocks[blocksOffset++] & 0xFF;
        final long byte215 = blocks[blocksOffset++] & 0xFF;
        final long byte216 = blocks[blocksOffset++] & 0xFF;
        final long byte217 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte212 & 3) << 35) | (byte213 << 27) | (byte214 << 19) | (byte215 << 11) | (byte216 << 3) | (byte217 >>> 5);
        final long byte218 = blocks[blocksOffset++] & 0xFF;
        final long byte219 = blocks[blocksOffset++] & 0xFF;
        final long byte220 = blocks[blocksOffset++] & 0xFF;
        final long byte221 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte217 & 31) << 32) | (byte218 << 24) | (byte219 << 16) | (byte220 << 8) | byte221;
        final long byte222 = blocks[blocksOffset++] & 0xFF;
        final long byte223 = blocks[blocksOffset++] & 0xFF;
        final long byte224 = blocks[blocksOffset++] & 0xFF;
        final long byte225 = blocks[blocksOffset++] & 0xFF;
        final long byte226 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = (byte222 << 29) | (byte223 << 21) | (byte224 << 13) | (byte225 << 5) | (byte226 >>> 3);
        final long byte227 = blocks[blocksOffset++] & 0xFF;
        final long byte228 = blocks[blocksOffset++] & 0xFF;
        final long byte229 = blocks[blocksOffset++] & 0xFF;
        final long byte230 = blocks[blocksOffset++] & 0xFF;
        final long byte231 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte226 & 7) << 34) | (byte227 << 26) | (byte228 << 18) | (byte229 << 10) | (byte230 << 2) | (byte231 >>> 6);
        final long byte232 = blocks[blocksOffset++] & 0xFF;
        final long byte233 = blocks[blocksOffset++] & 0xFF;
        final long byte234 = blocks[blocksOffset++] & 0xFF;
        final long byte235 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte231 & 63) << 31) | (byte232 << 23) | (byte233 << 15) | (byte234 << 7) | (byte235 >>> 1);
        final long byte236 = blocks[blocksOffset++] & 0xFF;
        final long byte237 = blocks[blocksOffset++] & 0xFF;
        final long byte238 = blocks[blocksOffset++] & 0xFF;
        final long byte239 = blocks[blocksOffset++] & 0xFF;
        final long byte240 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte235 & 1) << 36) | (byte236 << 28) | (byte237 << 20) | (byte238 << 12) | (byte239 << 4) | (byte240 >>> 4);
        final long byte241 = blocks[blocksOffset++] & 0xFF;
        final long byte242 = blocks[blocksOffset++] & 0xFF;
        final long byte243 = blocks[blocksOffset++] & 0xFF;
        final long byte244 = blocks[blocksOffset++] & 0xFF;
        final long byte245 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte240 & 15) << 33) | (byte241 << 25) | (byte242 << 17) | (byte243 << 9) | (byte244 << 1) | (byte245 >>> 7);
        final long byte246 = blocks[blocksOffset++] & 0xFF;
        final long byte247 = blocks[blocksOffset++] & 0xFF;
        final long byte248 = blocks[blocksOffset++] & 0xFF;
        final long byte249 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte245 & 127) << 30) | (byte246 << 22) | (byte247 << 14) | (byte248 << 6) | (byte249 >>> 2);
        final long byte250 = blocks[blocksOffset++] & 0xFF;
        final long byte251 = blocks[blocksOffset++] & 0xFF;
        final long byte252 = blocks[blocksOffset++] & 0xFF;
        final long byte253 = blocks[blocksOffset++] & 0xFF;
        final long byte254 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte249 & 3) << 35) | (byte250 << 27) | (byte251 << 19) | (byte252 << 11) | (byte253 << 3) | (byte254 >>> 5);
        final long byte255 = blocks[blocksOffset++] & 0xFF;
        final long byte256 = blocks[blocksOffset++] & 0xFF;
        final long byte257 = blocks[blocksOffset++] & 0xFF;
        final long byte258 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte254 & 31) << 32) | (byte255 << 24) | (byte256 << 16) | (byte257 << 8) | byte258;
        final long byte259 = blocks[blocksOffset++] & 0xFF;
        final long byte260 = blocks[blocksOffset++] & 0xFF;
        final long byte261 = blocks[blocksOffset++] & 0xFF;
        final long byte262 = blocks[blocksOffset++] & 0xFF;
        final long byte263 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = (byte259 << 29) | (byte260 << 21) | (byte261 << 13) | (byte262 << 5) | (byte263 >>> 3);
        final long byte264 = blocks[blocksOffset++] & 0xFF;
        final long byte265 = blocks[blocksOffset++] & 0xFF;
        final long byte266 = blocks[blocksOffset++] & 0xFF;
        final long byte267 = blocks[blocksOffset++] & 0xFF;
        final long byte268 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte263 & 7) << 34) | (byte264 << 26) | (byte265 << 18) | (byte266 << 10) | (byte267 << 2) | (byte268 >>> 6);
        final long byte269 = blocks[blocksOffset++] & 0xFF;
        final long byte270 = blocks[blocksOffset++] & 0xFF;
        final long byte271 = blocks[blocksOffset++] & 0xFF;
        final long byte272 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte268 & 63) << 31) | (byte269 << 23) | (byte270 << 15) | (byte271 << 7) | (byte272 >>> 1);
        final long byte273 = blocks[blocksOffset++] & 0xFF;
        final long byte274 = blocks[blocksOffset++] & 0xFF;
        final long byte275 = blocks[blocksOffset++] & 0xFF;
        final long byte276 = blocks[blocksOffset++] & 0xFF;
        final long byte277 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte272 & 1) << 36) | (byte273 << 28) | (byte274 << 20) | (byte275 << 12) | (byte276 << 4) | (byte277 >>> 4);
        final long byte278 = blocks[blocksOffset++] & 0xFF;
        final long byte279 = blocks[blocksOffset++] & 0xFF;
        final long byte280 = blocks[blocksOffset++] & 0xFF;
        final long byte281 = blocks[blocksOffset++] & 0xFF;
        final long byte282 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte277 & 15) << 33) | (byte278 << 25) | (byte279 << 17) | (byte280 << 9) | (byte281 << 1) | (byte282 >>> 7);
        final long byte283 = blocks[blocksOffset++] & 0xFF;
        final long byte284 = blocks[blocksOffset++] & 0xFF;
        final long byte285 = blocks[blocksOffset++] & 0xFF;
        final long byte286 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte282 & 127) << 30) | (byte283 << 22) | (byte284 << 14) | (byte285 << 6) | (byte286 >>> 2);
        final long byte287 = blocks[blocksOffset++] & 0xFF;
        final long byte288 = blocks[blocksOffset++] & 0xFF;
        final long byte289 = blocks[blocksOffset++] & 0xFF;
        final long byte290 = blocks[blocksOffset++] & 0xFF;
        final long byte291 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte286 & 3) << 35) | (byte287 << 27) | (byte288 << 19) | (byte289 << 11) | (byte290 << 3) | (byte291 >>> 5);
        final long byte292 = blocks[blocksOffset++] & 0xFF;
        final long byte293 = blocks[blocksOffset++] & 0xFF;
        final long byte294 = blocks[blocksOffset++] & 0xFF;
        final long byte295 = blocks[blocksOffset++] & 0xFF;
        values[valuesOffset++] = ((byte291 & 31) << 32) | (byte292 << 24) | (byte293 << 16) | (byte294 << 8) | byte295;
      }
    }

    @Override
    public void encode(int[] values, int valuesOffset, long[] blocks, int blocksOffset, int iterations) {
      assert blocksOffset + iterations * blockCount() <= blocks.length;
      assert valuesOffset + iterations * valueCount() <= values.length;
      for (int i = 0; i < iterations; ++i) {
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 27) | ((values[valuesOffset] & 0xffffffffL) >>> 10);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 54) | ((values[valuesOffset++] & 0xffffffffL) << 17) | ((values[valuesOffset] & 0xffffffffL) >>> 20);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 44) | ((values[valuesOffset++] & 0xffffffffL) << 7) | ((values[valuesOffset] & 0xffffffffL) >>> 30);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 34) | ((values[valuesOffset] & 0xffffffffL) >>> 3);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 61) | ((values[valuesOffset++] & 0xffffffffL) << 24) | ((values[valuesOffset] & 0xffffffffL) >>> 13);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 51) | ((values[valuesOffset++] & 0xffffffffL) << 14) | ((values[valuesOffset] & 0xffffffffL) >>> 23);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 41) | ((values[valuesOffset++] & 0xffffffffL) << 4) | ((values[valuesOffset] & 0xffffffffL) >>> 33);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 31) | ((values[valuesOffset] & 0xffffffffL) >>> 6);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 58) | ((values[valuesOffset++] & 0xffffffffL) << 21) | ((values[valuesOffset] & 0xffffffffL) >>> 16);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 48) | ((values[valuesOffset++] & 0xffffffffL) << 11) | ((values[valuesOffset] & 0xffffffffL) >>> 26);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 38) | ((values[valuesOffset++] & 0xffffffffL) << 1) | ((values[valuesOffset] & 0xffffffffL) >>> 36);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 28) | ((values[valuesOffset] & 0xffffffffL) >>> 9);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 55) | ((values[valuesOffset++] & 0xffffffffL) << 18) | ((values[valuesOffset] & 0xffffffffL) >>> 19);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 45) | ((values[valuesOffset++] & 0xffffffffL) << 8) | ((values[valuesOffset] & 0xffffffffL) >>> 29);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 35) | ((values[valuesOffset] & 0xffffffffL) >>> 2);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 62) | ((values[valuesOffset++] & 0xffffffffL) << 25) | ((values[valuesOffset] & 0xffffffffL) >>> 12);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 52) | ((values[valuesOffset++] & 0xffffffffL) << 15) | ((values[valuesOffset] & 0xffffffffL) >>> 22);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 42) | ((values[valuesOffset++] & 0xffffffffL) << 5) | ((values[valuesOffset] & 0xffffffffL) >>> 32);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 32) | ((values[valuesOffset] & 0xffffffffL) >>> 5);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 59) | ((values[valuesOffset++] & 0xffffffffL) << 22) | ((values[valuesOffset] & 0xffffffffL) >>> 15);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 49) | ((values[valuesOffset++] & 0xffffffffL) << 12) | ((values[valuesOffset] & 0xffffffffL) >>> 25);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 39) | ((values[valuesOffset++] & 0xffffffffL) << 2) | ((values[valuesOffset] & 0xffffffffL) >>> 35);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 29) | ((values[valuesOffset] & 0xffffffffL) >>> 8);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 56) | ((values[valuesOffset++] & 0xffffffffL) << 19) | ((values[valuesOffset] & 0xffffffffL) >>> 18);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 46) | ((values[valuesOffset++] & 0xffffffffL) << 9) | ((values[valuesOffset] & 0xffffffffL) >>> 28);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 36) | ((values[valuesOffset] & 0xffffffffL) >>> 1);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 63) | ((values[valuesOffset++] & 0xffffffffL) << 26) | ((values[valuesOffset] & 0xffffffffL) >>> 11);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 53) | ((values[valuesOffset++] & 0xffffffffL) << 16) | ((values[valuesOffset] & 0xffffffffL) >>> 21);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 43) | ((values[valuesOffset++] & 0xffffffffL) << 6) | ((values[valuesOffset] & 0xffffffffL) >>> 31);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 33) | ((values[valuesOffset] & 0xffffffffL) >>> 4);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 60) | ((values[valuesOffset++] & 0xffffffffL) << 23) | ((values[valuesOffset] & 0xffffffffL) >>> 14);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 50) | ((values[valuesOffset++] & 0xffffffffL) << 13) | ((values[valuesOffset] & 0xffffffffL) >>> 24);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 40) | ((values[valuesOffset++] & 0xffffffffL) << 3) | ((values[valuesOffset] & 0xffffffffL) >>> 34);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 30) | ((values[valuesOffset] & 0xffffffffL) >>> 7);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 57) | ((values[valuesOffset++] & 0xffffffffL) << 20) | ((values[valuesOffset] & 0xffffffffL) >>> 17);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 47) | ((values[valuesOffset++] & 0xffffffffL) << 10) | ((values[valuesOffset] & 0xffffffffL) >>> 27);
        blocks[blocksOffset++] = ((values[valuesOffset++] & 0xffffffffL) << 37) | (values[valuesOffset++] & 0xffffffffL);
      }
    }

    @Override
    public void encode(long[] values, int valuesOffset, long[] blocks, int blocksOffset, int iterations) {
      assert blocksOffset + iterations * blockCount() <= blocks.length;
      assert valuesOffset + iterations * valueCount() <= values.length;
      for (int i = 0; i < iterations; ++i) {
        blocks[blocksOffset++] = (values[valuesOffset++] << 27) | (values[valuesOffset] >>> 10);
        blocks[blocksOffset++] = (values[valuesOffset++] << 54) | (values[valuesOffset++] << 17) | (values[valuesOffset] >>> 20);
        blocks[blocksOffset++] = (values[valuesOffset++] << 44) | (values[valuesOffset++] << 7) | (values[valuesOffset] >>> 30);
        blocks[blocksOffset++] = (values[valuesOffset++] << 34) | (values[valuesOffset] >>> 3);
        blocks[blocksOffset++] = (values[valuesOffset++] << 61) | (values[valuesOffset++] << 24) | (values[valuesOffset] >>> 13);
        blocks[blocksOffset++] = (values[valuesOffset++] << 51) | (values[valuesOffset++] << 14) | (values[valuesOffset] >>> 23);
        blocks[blocksOffset++] = (values[valuesOffset++] << 41) | (values[valuesOffset++] << 4) | (values[valuesOffset] >>> 33);
        blocks[blocksOffset++] = (values[valuesOffset++] << 31) | (values[valuesOffset] >>> 6);
        blocks[blocksOffset++] = (values[valuesOffset++] << 58) | (values[valuesOffset++] << 21) | (values[valuesOffset] >>> 16);
        blocks[blocksOffset++] = (values[valuesOffset++] << 48) | (values[valuesOffset++] << 11) | (values[valuesOffset] >>> 26);
        blocks[blocksOffset++] = (values[valuesOffset++] << 38) | (values[valuesOffset++] << 1) | (values[valuesOffset] >>> 36);
        blocks[blocksOffset++] = (values[valuesOffset++] << 28) | (values[valuesOffset] >>> 9);
        blocks[blocksOffset++] = (values[valuesOffset++] << 55) | (values[valuesOffset++] << 18) | (values[valuesOffset] >>> 19);
        blocks[blocksOffset++] = (values[valuesOffset++] << 45) | (values[valuesOffset++] << 8) | (values[valuesOffset] >>> 29);
        blocks[blocksOffset++] = (values[valuesOffset++] << 35) | (values[valuesOffset] >>> 2);
        blocks[blocksOffset++] = (values[valuesOffset++] << 62) | (values[valuesOffset++] << 25) | (values[valuesOffset] >>> 12);
        blocks[blocksOffset++] = (values[valuesOffset++] << 52) | (values[valuesOffset++] << 15) | (values[valuesOffset] >>> 22);
        blocks[blocksOffset++] = (values[valuesOffset++] << 42) | (values[valuesOffset++] << 5) | (values[valuesOffset] >>> 32);
        blocks[blocksOffset++] = (values[valuesOffset++] << 32) | (values[valuesOffset] >>> 5);
        blocks[blocksOffset++] = (values[valuesOffset++] << 59) | (values[valuesOffset++] << 22) | (values[valuesOffset] >>> 15);
        blocks[blocksOffset++] = (values[valuesOffset++] << 49) | (values[valuesOffset++] << 12) | (values[valuesOffset] >>> 25);
        blocks[blocksOffset++] = (values[valuesOffset++] << 39) | (values[valuesOffset++] << 2) | (values[valuesOffset] >>> 35);
        blocks[blocksOffset++] = (values[valuesOffset++] << 29) | (values[valuesOffset] >>> 8);
        blocks[blocksOffset++] = (values[valuesOffset++] << 56) | (values[valuesOffset++] << 19) | (values[valuesOffset] >>> 18);
        blocks[blocksOffset++] = (values[valuesOffset++] << 46) | (values[valuesOffset++] << 9) | (values[valuesOffset] >>> 28);
        blocks[blocksOffset++] = (values[valuesOffset++] << 36) | (values[valuesOffset] >>> 1);
        blocks[blocksOffset++] = (values[valuesOffset++] << 63) | (values[valuesOffset++] << 26) | (values[valuesOffset] >>> 11);
        blocks[blocksOffset++] = (values[valuesOffset++] << 53) | (values[valuesOffset++] << 16) | (values[valuesOffset] >>> 21);
        blocks[blocksOffset++] = (values[valuesOffset++] << 43) | (values[valuesOffset++] << 6) | (values[valuesOffset] >>> 31);
        blocks[blocksOffset++] = (values[valuesOffset++] << 33) | (values[valuesOffset] >>> 4);
        blocks[blocksOffset++] = (values[valuesOffset++] << 60) | (values[valuesOffset++] << 23) | (values[valuesOffset] >>> 14);
        blocks[blocksOffset++] = (values[valuesOffset++] << 50) | (values[valuesOffset++] << 13) | (values[valuesOffset] >>> 24);
        blocks[blocksOffset++] = (values[valuesOffset++] << 40) | (values[valuesOffset++] << 3) | (values[valuesOffset] >>> 34);
        blocks[blocksOffset++] = (values[valuesOffset++] << 30) | (values[valuesOffset] >>> 7);
        blocks[blocksOffset++] = (values[valuesOffset++] << 57) | (values[valuesOffset++] << 20) | (values[valuesOffset] >>> 17);
        blocks[blocksOffset++] = (values[valuesOffset++] << 47) | (values[valuesOffset++] << 10) | (values[valuesOffset] >>> 27);
        blocks[blocksOffset++] = (values[valuesOffset++] << 37) | values[valuesOffset++];
      }
    }

}
