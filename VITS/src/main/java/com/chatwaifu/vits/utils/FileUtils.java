/*
 * Copyright (c) 2013, David Brodsky. All rights reserved.
 *
 *	This program is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *	
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *	
 *	You should have received a copy of the GNU General Public License
 *	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.chatwaifu.vits.utils;

import android.content.res.AssetManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class FileUtils {

    static final String TAG = "FileUtils";
//
//    public static void copy(File src, File dst) throws IOException {
//        InputStream in = new FileInputStream(src);
//        OutputStream out = new FileOutputStream(dst);
//
//        // Transfer bytes from in to out
//        byte[] buf = new byte[1024];
//        int len;
//        while ((len = in.read(buf)) > 0) {
//            out.write(buf, 0, len);
//        }
//        in.close();
//        out.close();
//    }
//
//    /**
//     * Delete a directory and all its contents
//     */
//    public static void deleteDirectory(File fileOrDirectory) {
//        File[] children = fileOrDirectory.listFiles();
//        if (fileOrDirectory.isDirectory() && children != null) {
//            for (File child : children) {
//                deleteDirectory(child);
//            }
//        }
//        fileOrDirectory.delete();
//    }
//
//    /**
//     * 递归拷贝Asset目录中的文件到rootDir中
//     * Recursively copy the files in the Asset directory to rootDir
//     * @param assets
//     * @param path
//     * @param rootDir
//     * @throws IOException
//     */
//    public static void copyAssets(AssetManager assets, String path, String rootDir, byte[] buffer) throws IOException {
//        if (isAssetsDir(assets, path)) {
//            File dir = new File(rootDir + File.separator + path);
//            if (!dir.exists() && !dir.mkdirs()) {
//                throw new IllegalStateException("mkdir failed");
//            }
//            for (String s : assets.list(path)) {
//                copyAssets(assets, path + "/" + s, rootDir, buffer);
//            }
//        } else {
//            InputStream input = assets.open(path);
//            File dest = new File(rootDir, path);
//            copyToFileOrThrow(input, dest, buffer);
//        }
//    }
//
//    public static boolean isAssetsDir(AssetManager assets, String path) {
//        try {
//
//            String[] files = assets.list(path);
//            return files != null && files.length > 0;
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return false;
//    }
//
//    public static int DEFAULT_BUFFER = 64 * 1024;
//    public static void copyToFileOrThrow(InputStream inputStream, File destFile, byte[] buffer)
//            throws IOException {
//        if (destFile.exists()) {
//            return;
//
//        }
//        File file = destFile.getParentFile();
//        if (file != null && !file.exists()) {
//            file.mkdirs();
//        }
//        FileOutputStream out = new FileOutputStream(destFile);
//        try {
//            if(buffer == null || buffer.length == 0) {
//                buffer = new byte[DEFAULT_BUFFER];
//            }
//            // byte[] buffer = new byte[BUFFER];
//            int bytesRead;
//            while ((bytesRead = inputStream.read(buffer)) >= 0) {
//                out.write(buffer, 0, bytesRead);
//            }
//        } finally {
//            out.flush();
//            try {
//                out.getFD().sync();
//            } catch (IOException e) {
//            }
//            out.close();
//        }
//    }
}