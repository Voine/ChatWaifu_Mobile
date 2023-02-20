/*
 * Copyright (c) 2015-2017 BiliBili Inc.
 */

package com.chatwaifu.translate.baidu;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Yann Chou
 * @email zhouyanbin1029@gmail.com
 * @create 2015-10-31 23:35
 */
public class DigestUtils {
    public static final char HEX_DIGITS[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String md5(String source) {
        return md5(source.getBytes());
    }

    public static String md5(byte[] source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return signDigest(source, digest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String signDigest(byte[] source, MessageDigest digest) {
        digest.update(source);
        byte[] data = digest.digest();
        int j = data.length;
        char str[] = new char[j * 2];
        int k = 0;
        for (byte byte0 : data) {
            str[k++] = HEX_DIGITS[byte0 >>> 4 & 0xf];
            str[k++] = HEX_DIGITS[byte0 & 0xf];
        }
        return new String(str).toLowerCase();
    }
}
