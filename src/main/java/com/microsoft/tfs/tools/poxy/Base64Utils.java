/*
 * Poxy: a simple HTTP proxy for testing.
 *
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

public class Base64Utils
{
    final static byte[] Base64Table = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63,
        52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1,  0, -1, -1,
        -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1,
        -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
        41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };

    static byte[] decode(String base64)
    {
        if (base64 == null || (base64.length() % 4) != 0)
        {
            throw new IllegalArgumentException("Invalid base64 input");
        }

        int outputLength = base64.length() / 4 * 3;
        int base64Idx = base64.length() - 1;

        while (base64.charAt(base64Idx--) == '=')
        {
            outputLength--;
        }

        byte[] output = new byte[outputLength];

        int i = 0, j = 0;
        byte[] b = new byte[4];

        while (i < base64.length())
        {
            b[0] = Base64Table[base64.charAt(i++)];
            b[1] = Base64Table[base64.charAt(i++)];
            b[2] = Base64Table[base64.charAt(i++)];
            b[3] = Base64Table[base64.charAt(i++)];
            
            if (b[0] < 0 || b[1] < 0 || b[2] < 0 || b[3] < 0)
            {
                throw new IllegalArgumentException("Invalid base64 input");                
            }

            output[j++] = (byte) ((b[0] << 2) | (b[1] >> 4));
            
            if (b[2] > 0)
            {
                output[j++] = (byte) ((b[1] << 4) | (b[2] >> 2));
                
                if (b[3] > 0)
                {
                    output[j++] = (byte) ((b[2] << 6) | (b[3]));
                }
            }        
        }

        return output;
    }
}
