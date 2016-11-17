package com.croconaut.ratemebuddy.utils;

/**
 * String XOR String
 * by Lubo Roba (-Xi-)
 * first version: 1.0 (16.10.2016 21:04)
 * last change: v 1.0 (16.10.2016 21:04)
 */

public class XorString
{
    public String xoring(String a, String b)
    {
        String strReturnValue;

        if ((a.length() > 0) && (b.length() > 0))  // Validation
        {
            char[] chars = new char[a.length()];

            for (int i = 0; i < chars.length; i++)
            {
                chars[i] = getChar(getIndex(a.charAt(i)) ^ getIndex(b.charAt(i % b.length())));
            }
            strReturnValue = new String(chars);
        }
        else
        {
            throw new IllegalArgumentException();
        }

        return strReturnValue;
    }

    private static int getIndex(char c)
    {
        return "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".indexOf(c);
    }

    private static char getChar(int index)
    {
        return "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".charAt(index);
    }
}
