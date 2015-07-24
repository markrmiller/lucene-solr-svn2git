// This file was generated automatically by the Snowball to Java compiler

package org.tartarus.snowball.ext;

import org.tartarus.snowball.Among;

/**
 * This class was automatically generated by a Snowball to Java compiler
 * It implements the stemming algorithm defined by a snowball script.
 */

public class LithuanianStemmer extends org.tartarus.snowball.SnowballProgram {

    private static final long serialVersionUID = 1L;

    private final static LithuanianStemmer methodObject = new LithuanianStemmer ();

    private final static Among a_0[] = {
            new Among ( "a", -1, -1, "", methodObject ),
            new Among ( "ia", 0, -1, "", methodObject ),
            new Among ( "eria", 1, -1, "", methodObject ),
            new Among ( "osna", 0, -1, "", methodObject ),
            new Among ( "iosna", 3, -1, "", methodObject ),
            new Among ( "uosna", 3, -1, "", methodObject ),
            new Among ( "iuosna", 5, -1, "", methodObject ),
            new Among ( "ysna", 0, -1, "", methodObject ),
            new Among ( "\u0117sna", 0, -1, "", methodObject ),
            new Among ( "e", -1, -1, "", methodObject ),
            new Among ( "ie", 9, -1, "", methodObject ),
            new Among ( "enie", 10, -1, "", methodObject ),
            new Among ( "erie", 10, -1, "", methodObject ),
            new Among ( "oje", 9, -1, "", methodObject ),
            new Among ( "ioje", 13, -1, "", methodObject ),
            new Among ( "uje", 9, -1, "", methodObject ),
            new Among ( "iuje", 15, -1, "", methodObject ),
            new Among ( "yje", 9, -1, "", methodObject ),
            new Among ( "enyje", 17, -1, "", methodObject ),
            new Among ( "eryje", 17, -1, "", methodObject ),
            new Among ( "\u0117je", 9, -1, "", methodObject ),
            new Among ( "ame", 9, -1, "", methodObject ),
            new Among ( "iame", 21, -1, "", methodObject ),
            new Among ( "sime", 9, -1, "", methodObject ),
            new Among ( "ome", 9, -1, "", methodObject ),
            new Among ( "\u0117me", 9, -1, "", methodObject ),
            new Among ( "tum\u0117me", 25, -1, "", methodObject ),
            new Among ( "ose", 9, -1, "", methodObject ),
            new Among ( "iose", 27, -1, "", methodObject ),
            new Among ( "uose", 27, -1, "", methodObject ),
            new Among ( "iuose", 29, -1, "", methodObject ),
            new Among ( "yse", 9, -1, "", methodObject ),
            new Among ( "enyse", 31, -1, "", methodObject ),
            new Among ( "eryse", 31, -1, "", methodObject ),
            new Among ( "\u0117se", 9, -1, "", methodObject ),
            new Among ( "ate", 9, -1, "", methodObject ),
            new Among ( "iate", 35, -1, "", methodObject ),
            new Among ( "ite", 9, -1, "", methodObject ),
            new Among ( "kite", 37, -1, "", methodObject ),
            new Among ( "site", 37, -1, "", methodObject ),
            new Among ( "ote", 9, -1, "", methodObject ),
            new Among ( "tute", 9, -1, "", methodObject ),
            new Among ( "\u0117te", 9, -1, "", methodObject ),
            new Among ( "tum\u0117te", 42, -1, "", methodObject ),
            new Among ( "i", -1, -1, "", methodObject ),
            new Among ( "ai", 44, -1, "", methodObject ),
            new Among ( "iai", 45, -1, "", methodObject ),
            new Among ( "eriai", 46, -1, "", methodObject ),
            new Among ( "ei", 44, -1, "", methodObject ),
            new Among ( "tumei", 48, -1, "", methodObject ),
            new Among ( "ki", 44, -1, "", methodObject ),
            new Among ( "imi", 44, -1, "", methodObject ),
            new Among ( "erimi", 51, -1, "", methodObject ),
            new Among ( "umi", 44, -1, "", methodObject ),
            new Among ( "iumi", 53, -1, "", methodObject ),
            new Among ( "si", 44, -1, "", methodObject ),
            new Among ( "asi", 55, -1, "", methodObject ),
            new Among ( "iasi", 56, -1, "", methodObject ),
            new Among ( "esi", 55, -1, "", methodObject ),
            new Among ( "iesi", 58, -1, "", methodObject ),
            new Among ( "siesi", 59, -1, "", methodObject ),
            new Among ( "isi", 55, -1, "", methodObject ),
            new Among ( "aisi", 61, -1, "", methodObject ),
            new Among ( "eisi", 61, -1, "", methodObject ),
            new Among ( "tumeisi", 63, -1, "", methodObject ),
            new Among ( "uisi", 61, -1, "", methodObject ),
            new Among ( "osi", 55, -1, "", methodObject ),
            new Among ( "\u0117josi", 66, -1, "", methodObject ),
            new Among ( "uosi", 66, -1, "", methodObject ),
            new Among ( "iuosi", 68, -1, "", methodObject ),
            new Among ( "siuosi", 69, -1, "", methodObject ),
            new Among ( "usi", 55, -1, "", methodObject ),
            new Among ( "ausi", 71, -1, "", methodObject ),
            new Among ( "\u010Diausi", 72, -1, "", methodObject ),
            new Among ( "\u0105si", 55, -1, "", methodObject ),
            new Among ( "\u0117si", 55, -1, "", methodObject ),
            new Among ( "\u0173si", 55, -1, "", methodObject ),
            new Among ( "t\u0173si", 76, -1, "", methodObject ),
            new Among ( "ti", 44, -1, "", methodObject ),
            new Among ( "enti", 78, -1, "", methodObject ),
            new Among ( "inti", 78, -1, "", methodObject ),
            new Among ( "oti", 78, -1, "", methodObject ),
            new Among ( "ioti", 81, -1, "", methodObject ),
            new Among ( "uoti", 81, -1, "", methodObject ),
            new Among ( "iuoti", 83, -1, "", methodObject ),
            new Among ( "auti", 78, -1, "", methodObject ),
            new Among ( "iauti", 85, -1, "", methodObject ),
            new Among ( "yti", 78, -1, "", methodObject ),
            new Among ( "\u0117ti", 78, -1, "", methodObject ),
            new Among ( "tel\u0117ti", 88, -1, "", methodObject ),
            new Among ( "in\u0117ti", 88, -1, "", methodObject ),
            new Among ( "ter\u0117ti", 88, -1, "", methodObject ),
            new Among ( "ui", 44, -1, "", methodObject ),
            new Among ( "iui", 92, -1, "", methodObject ),
            new Among ( "eniui", 93, -1, "", methodObject ),
            new Among ( "oj", -1, -1, "", methodObject ),
            new Among ( "\u0117j", -1, -1, "", methodObject ),
            new Among ( "k", -1, -1, "", methodObject ),
            new Among ( "am", -1, -1, "", methodObject ),
            new Among ( "iam", 98, -1, "", methodObject ),
            new Among ( "iem", -1, -1, "", methodObject ),
            new Among ( "im", -1, -1, "", methodObject ),
            new Among ( "sim", 101, -1, "", methodObject ),
            new Among ( "om", -1, -1, "", methodObject ),
            new Among ( "tum", -1, -1, "", methodObject ),
            new Among ( "\u0117m", -1, -1, "", methodObject ),
            new Among ( "tum\u0117m", 105, -1, "", methodObject ),
            new Among ( "an", -1, -1, "", methodObject ),
            new Among ( "on", -1, -1, "", methodObject ),
            new Among ( "ion", 108, -1, "", methodObject ),
            new Among ( "un", -1, -1, "", methodObject ),
            new Among ( "iun", 110, -1, "", methodObject ),
            new Among ( "\u0117n", -1, -1, "", methodObject ),
            new Among ( "o", -1, -1, "", methodObject ),
            new Among ( "io", 113, -1, "", methodObject ),
            new Among ( "enio", 114, -1, "", methodObject ),
            new Among ( "\u0117jo", 113, -1, "", methodObject ),
            new Among ( "uo", 113, -1, "", methodObject ),
            new Among ( "s", -1, -1, "", methodObject ),
            new Among ( "as", 118, -1, "", methodObject ),
            new Among ( "ias", 119, -1, "", methodObject ),
            new Among ( "es", 118, -1, "", methodObject ),
            new Among ( "ies", 121, -1, "", methodObject ),
            new Among ( "is", 118, -1, "", methodObject ),
            new Among ( "ais", 123, -1, "", methodObject ),
            new Among ( "iais", 124, -1, "", methodObject ),
            new Among ( "tumeis", 123, -1, "", methodObject ),
            new Among ( "imis", 123, -1, "", methodObject ),
            new Among ( "enimis", 127, -1, "", methodObject ),
            new Among ( "omis", 123, -1, "", methodObject ),
            new Among ( "iomis", 129, -1, "", methodObject ),
            new Among ( "umis", 123, -1, "", methodObject ),
            new Among ( "\u0117mis", 123, -1, "", methodObject ),
            new Among ( "enis", 123, -1, "", methodObject ),
            new Among ( "asis", 123, -1, "", methodObject ),
            new Among ( "ysis", 123, -1, "", methodObject ),
            new Among ( "ams", 118, -1, "", methodObject ),
            new Among ( "iams", 136, -1, "", methodObject ),
            new Among ( "iems", 118, -1, "", methodObject ),
            new Among ( "ims", 118, -1, "", methodObject ),
            new Among ( "enims", 139, -1, "", methodObject ),
            new Among ( "erims", 139, -1, "", methodObject ),
            new Among ( "oms", 118, -1, "", methodObject ),
            new Among ( "ioms", 142, -1, "", methodObject ),
            new Among ( "ums", 118, -1, "", methodObject ),
            new Among ( "\u0117ms", 118, -1, "", methodObject ),
            new Among ( "ens", 118, -1, "", methodObject ),
            new Among ( "os", 118, -1, "", methodObject ),
            new Among ( "ios", 147, -1, "", methodObject ),
            new Among ( "uos", 147, -1, "", methodObject ),
            new Among ( "iuos", 149, -1, "", methodObject ),
            new Among ( "ers", 118, -1, "", methodObject ),
            new Among ( "us", 118, -1, "", methodObject ),
            new Among ( "aus", 152, -1, "", methodObject ),
            new Among ( "iaus", 153, -1, "", methodObject ),
            new Among ( "ius", 152, -1, "", methodObject ),
            new Among ( "ys", 118, -1, "", methodObject ),
            new Among ( "enys", 156, -1, "", methodObject ),
            new Among ( "erys", 156, -1, "", methodObject ),
            new Among ( "om\u00C4\u0097s", 118, -1, "", methodObject ),
            new Among ( "ot\u00C4\u0097s", 118, -1, "", methodObject ),
            new Among ( "\u0105s", 118, -1, "", methodObject ),
            new Among ( "i\u0105s", 161, -1, "", methodObject ),
            new Among ( "\u0117s", 118, -1, "", methodObject ),
            new Among ( "am\u0117s", 163, -1, "", methodObject ),
            new Among ( "iam\u0117s", 164, -1, "", methodObject ),
            new Among ( "im\u0117s", 163, -1, "", methodObject ),
            new Among ( "kim\u0117s", 166, -1, "", methodObject ),
            new Among ( "sim\u0117s", 166, -1, "", methodObject ),
            new Among ( "om\u0117s", 163, -1, "", methodObject ),
            new Among ( "\u0117m\u0117s", 163, -1, "", methodObject ),
            new Among ( "tum\u0117m\u0117s", 170, -1, "", methodObject ),
            new Among ( "at\u0117s", 163, -1, "", methodObject ),
            new Among ( "iat\u0117s", 172, -1, "", methodObject ),
            new Among ( "sit\u0117s", 163, -1, "", methodObject ),
            new Among ( "ot\u0117s", 163, -1, "", methodObject ),
            new Among ( "\u0117t\u0117s", 163, -1, "", methodObject ),
            new Among ( "tum\u0117t\u0117s", 176, -1, "", methodObject ),
            new Among ( "\u012Fs", 118, -1, "", methodObject ),
            new Among ( "\u016Bs", 118, -1, "", methodObject ),
            new Among ( "t\u0173s", 118, -1, "", methodObject ),
            new Among ( "at", -1, -1, "", methodObject ),
            new Among ( "iat", 181, -1, "", methodObject ),
            new Among ( "it", -1, -1, "", methodObject ),
            new Among ( "sit", 183, -1, "", methodObject ),
            new Among ( "ot", -1, -1, "", methodObject ),
            new Among ( "\u0117t", -1, -1, "", methodObject ),
            new Among ( "tum\u0117t", 186, -1, "", methodObject ),
            new Among ( "u", -1, -1, "", methodObject ),
            new Among ( "au", 188, -1, "", methodObject ),
            new Among ( "iau", 189, -1, "", methodObject ),
            new Among ( "\u010Diau", 190, -1, "", methodObject ),
            new Among ( "iu", 188, -1, "", methodObject ),
            new Among ( "eniu", 192, -1, "", methodObject ),
            new Among ( "siu", 192, -1, "", methodObject ),
            new Among ( "y", -1, -1, "", methodObject ),
            new Among ( "\u0105", -1, -1, "", methodObject ),
            new Among ( "i\u0105", 196, -1, "", methodObject ),
            new Among ( "\u0117", -1, -1, "", methodObject ),
            new Among ( "\u0119", -1, -1, "", methodObject ),
            new Among ( "\u012F", -1, -1, "", methodObject ),
            new Among ( "en\u012F", 200, -1, "", methodObject ),
            new Among ( "er\u012F", 200, -1, "", methodObject ),
            new Among ( "\u0173", -1, -1, "", methodObject ),
            new Among ( "i\u0173", 203, -1, "", methodObject ),
            new Among ( "er\u0173", 203, -1, "", methodObject )
    };

    private final static Among a_1[] = {
            new Among ( "ing", -1, -1, "", methodObject ),
            new Among ( "aj", -1, -1, "", methodObject ),
            new Among ( "iaj", 1, -1, "", methodObject ),
            new Among ( "iej", -1, -1, "", methodObject ),
            new Among ( "oj", -1, -1, "", methodObject ),
            new Among ( "ioj", 4, -1, "", methodObject ),
            new Among ( "uoj", 4, -1, "", methodObject ),
            new Among ( "iuoj", 6, -1, "", methodObject ),
            new Among ( "auj", -1, -1, "", methodObject ),
            new Among ( "\u0105j", -1, -1, "", methodObject ),
            new Among ( "i\u0105j", 9, -1, "", methodObject ),
            new Among ( "\u0117j", -1, -1, "", methodObject ),
            new Among ( "\u0173j", -1, -1, "", methodObject ),
            new Among ( "i\u0173j", 12, -1, "", methodObject ),
            new Among ( "ok", -1, -1, "", methodObject ),
            new Among ( "iok", 14, -1, "", methodObject ),
            new Among ( "iuk", -1, -1, "", methodObject ),
            new Among ( "uliuk", 16, -1, "", methodObject ),
            new Among ( "u\u010Diuk", 16, -1, "", methodObject ),
            new Among ( "i\u0161k", -1, -1, "", methodObject ),
            new Among ( "iul", -1, -1, "", methodObject ),
            new Among ( "yl", -1, -1, "", methodObject ),
            new Among ( "\u0117l", -1, -1, "", methodObject ),
            new Among ( "am", -1, -1, "", methodObject ),
            new Among ( "dam", 23, -1, "", methodObject ),
            new Among ( "jam", 23, -1, "", methodObject ),
            new Among ( "zgan", -1, -1, "", methodObject ),
            new Among ( "ain", -1, -1, "", methodObject ),
            new Among ( "esn", -1, -1, "", methodObject ),
            new Among ( "op", -1, -1, "", methodObject ),
            new Among ( "iop", 29, -1, "", methodObject ),
            new Among ( "ias", -1, -1, "", methodObject ),
            new Among ( "ies", -1, -1, "", methodObject ),
            new Among ( "ais", -1, -1, "", methodObject ),
            new Among ( "iais", 33, -1, "", methodObject ),
            new Among ( "os", -1, -1, "", methodObject ),
            new Among ( "ios", 35, -1, "", methodObject ),
            new Among ( "uos", 35, -1, "", methodObject ),
            new Among ( "iuos", 37, -1, "", methodObject ),
            new Among ( "aus", -1, -1, "", methodObject ),
            new Among ( "iaus", 39, -1, "", methodObject ),
            new Among ( "\u0105s", -1, -1, "", methodObject ),
            new Among ( "i\u0105s", 41, -1, "", methodObject ),
            new Among ( "\u0119s", -1, -1, "", methodObject ),
            new Among ( "ut\u0117ait", -1, -1, "", methodObject ),
            new Among ( "ant", -1, -1, "", methodObject ),
            new Among ( "iant", 45, -1, "", methodObject ),
            new Among ( "siant", 46, -1, "", methodObject ),
            new Among ( "int", -1, -1, "", methodObject ),
            new Among ( "ot", -1, -1, "", methodObject ),
            new Among ( "uot", 49, -1, "", methodObject ),
            new Among ( "iuot", 50, -1, "", methodObject ),
            new Among ( "yt", -1, -1, "", methodObject ),
            new Among ( "\u0117t", -1, -1, "", methodObject ),
            new Among ( "yk\u0161t", -1, -1, "", methodObject ),
            new Among ( "iau", -1, -1, "", methodObject ),
            new Among ( "dav", -1, -1, "", methodObject ),
            new Among ( "sv", -1, -1, "", methodObject ),
            new Among ( "\u0161v", -1, -1, "", methodObject ),
            new Among ( "yk\u0161\u010D", -1, -1, "", methodObject ),
            new Among ( "\u0119", -1, -1, "", methodObject ),
            new Among ( "\u0117j\u0119", 60, -1, "", methodObject )
    };

    private final static Among a_2[] = {
            new Among ( "ojime", -1, 9, "", methodObject ),
            new Among ( "\u0117jime", -1, 5, "", methodObject ),
            new Among ( "avime", -1, 8, "", methodObject ),
            new Among ( "okate", -1, 11, "", methodObject ),
            new Among ( "aite", -1, 1, "", methodObject ),
            new Among ( "uote", -1, 4, "", methodObject ),
            new Among ( "asius", -1, 7, "", methodObject ),
            new Among ( "okat\u0117s", -1, 10, "", methodObject ),
            new Among ( "ait\u0117s", -1, 2, "", methodObject ),
            new Among ( "uot\u0117s", -1, 3, "", methodObject ),
            new Among ( "esiu", -1, 6, "", methodObject )
    };

    private final static Among a_3[] = {
            new Among ( "\u010D", -1, 1, "", methodObject ),
            new Among ( "d\u017E", -1, 2, "", methodObject )
    };

    private final static Among a_4[] = {
            new Among ( "gd", -1, 1, "", methodObject )
    };

    private static final char g_v[] = {17, 65, 16, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16, 0, 64, 1, 0, 64, 0, 0, 0, 0, 0, 0, 0, 4, 4 };

    private boolean B_CHANGE;
    private int I_s;
    private int I_p2;
    private int I_p1;

    private void copy_from(LithuanianStemmer other) {
        B_CHANGE = other.B_CHANGE;
        I_s = other.I_s;
        I_p2 = other.I_p2;
        I_p1 = other.I_p1;
        super.copy_from(other);
    }

    private boolean r_R1() {
        if (!(I_p1 <= cursor))
        {
            return false;
        }
        return true;
    }

    private boolean r_step1() {
        int v_1;
        int v_2;
        // (, line 48
        // setlimit, line 49
        v_1 = limit - cursor;
        // tomark, line 49
        if (cursor < I_p1)
        {
            return false;
        }
        cursor = I_p1;
        v_2 = limit_backward;
        limit_backward = cursor;
        cursor = limit - v_1;
        // (, line 49
        // [, line 49
        ket = cursor;
        // substring, line 49
        if (find_among_b(a_0, 206) == 0)
        {
            limit_backward = v_2;
            return false;
        }
        // ], line 49
        bra = cursor;
        limit_backward = v_2;
        // call R1, line 49
        if (!r_R1())
        {
            return false;
        }
        // delete, line 235
        slice_del();
        return true;
    }

    private boolean r_step2() {
        int v_1;
        int v_2;
        int v_3;
        // repeat, line 238
        replab0: while(true)
        {
            v_1 = limit - cursor;
            lab1: do {
                // (, line 238
                // setlimit, line 239
                v_2 = limit - cursor;
                // tomark, line 239
                if (cursor < I_p1)
                {
                    break lab1;
                }
                cursor = I_p1;
                v_3 = limit_backward;
                limit_backward = cursor;
                cursor = limit - v_2;
                // (, line 239
                // [, line 239
                ket = cursor;
                // substring, line 239
                if (find_among_b(a_1, 62) == 0)
                {
                    limit_backward = v_3;
                    break lab1;
                }
                // ], line 239
                bra = cursor;
                limit_backward = v_3;
                // delete, line 309
                slice_del();
                continue replab0;
            } while (false);
            cursor = limit - v_1;
            break replab0;
        }
        return true;
    }

    private boolean r_fix_conflicts() {
        int among_var;
        // (, line 312
        // [, line 313
        ket = cursor;
        // substring, line 313
        among_var = find_among_b(a_2, 11);
        if (among_var == 0)
        {
            return false;
        }
        // ], line 313
        bra = cursor;
        switch(among_var) {
            case 0:
                return false;
            case 1:
                // (, line 315
                // <-, line 315
                slice_from("ait\u0117");
                // set CHANGE, line 315
                B_CHANGE = true;
                break;
            case 2:
                // (, line 317
                // <-, line 317
                slice_from("ait\u0117");
                // set CHANGE, line 317
                B_CHANGE = true;
                break;
            case 3:
                // (, line 320
                // <-, line 320
                slice_from("uot\u0117");
                // set CHANGE, line 320
                B_CHANGE = true;
                break;
            case 4:
                // (, line 322
                // <-, line 322
                slice_from("uot\u0117");
                // set CHANGE, line 322
                B_CHANGE = true;
                break;
            case 5:
                // (, line 325
                // <-, line 325
                slice_from("\u0117jimas");
                // set CHANGE, line 325
                B_CHANGE = true;
                break;
            case 6:
                // (, line 328
                // <-, line 328
                slice_from("esys");
                // set CHANGE, line 328
                B_CHANGE = true;
                break;
            case 7:
                // (, line 330
                // <-, line 330
                slice_from("asys");
                // set CHANGE, line 330
                B_CHANGE = true;
                break;
            case 8:
                // (, line 334
                // <-, line 334
                slice_from("avimas");
                // set CHANGE, line 334
                B_CHANGE = true;
                break;
            case 9:
                // (, line 335
                // <-, line 335
                slice_from("ojimas");
                // set CHANGE, line 335
                B_CHANGE = true;
                break;
            case 10:
                // (, line 338
                // <-, line 338
                slice_from("okat\u0117");
                // set CHANGE, line 338
                B_CHANGE = true;
                break;
            case 11:
                // (, line 340
                // <-, line 340
                slice_from("okat\u0117");
                // set CHANGE, line 340
                B_CHANGE = true;
                break;
        }
        return true;
    }

    private boolean r_fix_chdz() {
        int among_var;
        // (, line 346
        // [, line 347
        ket = cursor;
        // substring, line 347
        among_var = find_among_b(a_3, 2);
        if (among_var == 0)
        {
            return false;
        }
        // ], line 347
        bra = cursor;
        switch(among_var) {
            case 0:
                return false;
            case 1:
                // (, line 348
                // <-, line 348
                slice_from("t");
                // set CHANGE, line 348
                B_CHANGE = true;
                break;
            case 2:
                // (, line 349
                // <-, line 349
                slice_from("d");
                // set CHANGE, line 349
                B_CHANGE = true;
                break;
        }
        return true;
    }

    private boolean r_fix_gd() {
        int among_var;
        // (, line 353
        // [, line 354
        ket = cursor;
        // substring, line 354
        among_var = find_among_b(a_4, 1);
        if (among_var == 0)
        {
            return false;
        }
        // ], line 354
        bra = cursor;
        switch(among_var) {
            case 0:
                return false;
            case 1:
                // (, line 355
                // <-, line 355
                slice_from("g");
                // set CHANGE, line 355
                B_CHANGE = true;
                break;
        }
        return true;
    }

    public boolean stem() {
        int v_1;
        int v_2;
        int v_3;
        int v_8;
        int v_9;
        int v_10;
        int v_11;
        int v_12;
        int v_13;
        // (, line 362
        I_p1 = limit;
        I_p2 = limit;
        I_s = (getCurrent().length());
        // do, line 368
        v_1 = cursor;
        lab0: do {
            // (, line 368
            // try, line 370
            v_2 = cursor;
            lab1: do {
                // (, line 370
                // test, line 370
                v_3 = cursor;
                // literal, line 370
                if (!(eq_s(1, "a")))
                {
                    cursor = v_2;
                    break lab1;
                }
                cursor = v_3;
                if (!(I_s > 6))
                {
                    cursor = v_2;
                    break lab1;
                }
                // hop, line 370
                {
                    int c = cursor + 1;
                    if (0 > c || c > limit)
                    {
                        cursor = v_2;
                        break lab1;
                    }
                    cursor = c;
                }
            } while (false);
            // gopast, line 372
            golab2: while(true)
            {
                lab3: do {
                    if (!(in_grouping(g_v, 97, 371)))
                    {
                        break lab3;
                    }
                    break golab2;
                } while (false);
                if (cursor >= limit)
                {
                    break lab0;
                }
                cursor++;
            }
            // gopast, line 372
            golab4: while(true)
            {
                lab5: do {
                    if (!(out_grouping(g_v, 97, 371)))
                    {
                        break lab5;
                    }
                    break golab4;
                } while (false);
                if (cursor >= limit)
                {
                    break lab0;
                }
                cursor++;
            }
            // setmark p1, line 372
            I_p1 = cursor;
            // gopast, line 373
            golab6: while(true)
            {
                lab7: do {
                    if (!(in_grouping(g_v, 97, 371)))
                    {
                        break lab7;
                    }
                    break golab6;
                } while (false);
                if (cursor >= limit)
                {
                    break lab0;
                }
                cursor++;
            }
            // gopast, line 373
            golab8: while(true)
            {
                lab9: do {
                    if (!(out_grouping(g_v, 97, 371)))
                    {
                        break lab9;
                    }
                    break golab8;
                } while (false);
                if (cursor >= limit)
                {
                    break lab0;
                }
                cursor++;
            }
            // setmark p2, line 373
            I_p2 = cursor;
        } while (false);
        cursor = v_1;
        // backwards, line 377
        limit_backward = cursor; cursor = limit;
        // (, line 377
        // do, line 378
        v_8 = limit - cursor;
        lab10: do {
            // call fix_conflicts, line 378
            if (!r_fix_conflicts())
            {
                break lab10;
            }
        } while (false);
        cursor = limit - v_8;
        // do, line 379
        v_9 = limit - cursor;
        lab11: do {
            // call step1, line 379
            if (!r_step1())
            {
                break lab11;
            }
        } while (false);
        cursor = limit - v_9;
        // do, line 380
        v_10 = limit - cursor;
        lab12: do {
            // call fix_chdz, line 380
            if (!r_fix_chdz())
            {
                break lab12;
            }
        } while (false);
        cursor = limit - v_10;
        // do, line 381
        v_11 = limit - cursor;
        lab13: do {
            // call step2, line 381
            if (!r_step2())
            {
                break lab13;
            }
        } while (false);
        cursor = limit - v_11;
        // do, line 382
        v_12 = limit - cursor;
        lab14: do {
            // call fix_chdz, line 382
            if (!r_fix_chdz())
            {
                break lab14;
            }
        } while (false);
        cursor = limit - v_12;
        // do, line 383
        v_13 = limit - cursor;
        lab15: do {
            // call fix_gd, line 383
            if (!r_fix_gd())
            {
                break lab15;
            }
        } while (false);
        cursor = limit - v_13;
        cursor = limit_backward;                    return true;
    }

    public boolean equals( Object o ) {
        return o instanceof LithuanianStemmer;
    }

    public int hashCode() {
        return LithuanianStemmer.class.getName().hashCode();
    }



}
