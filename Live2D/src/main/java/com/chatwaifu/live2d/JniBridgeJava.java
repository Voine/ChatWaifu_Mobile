/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.chatwaifu.live2d;

import android.app.Activity;
import android.content.Context;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JniBridgeJava {

    private static final String LIBRARY_NAME = "chatwaifu-live2d";
    private static Activity _activityInstance;
    private static Context _context;
    private static Live2DLoadInterface _loadCallback;


    static {
        System.loadLibrary(LIBRARY_NAME);
    }

    // Native -----------------------------------------------------------------

    public static native void nativeOnStart();

    public static native void nativeOnPause();

    public static native void nativeOnStop();

    public static native void nativeOnDestroy();

    public static native void nativeOnSurfaceCreated();

    public static native void nativeOnSurfaceChanged(int width, int height);

    public static native void nativeOnDrawFrame();

    public static native void nativeOnTouchesBegan(float pointX, float pointY);

    public static native void nativeOnTouchesEnded(float pointX, float pointY);

    public static native void nativeOnTouchesMoved(float pointX, float pointY);

    public static native void nativeProjectChangeTo(String modelPath, String modelJsonFileName);

    public static native void nativeApplyExpression(String expressionName);

    public static native void needRenderBack(boolean back);

    public static native void nativeProjectScale(float scale);

    public static native void nativeProjectTransformX(float transform);

    public static native void nativeProjectTransformY(float transform);

    public static native void nativeAutoBlinkEyes(boolean enabled);

    public static native void nativeProjectMouthForm(float value);

    public static native void nativeProjectMouthOpenY(float value);

    // Java -----------------------------------------------------------------

    public static void SetContext(Context context) {
        _context = context;
    }

    public static void SetActivityInstance(Activity activity) {
        _activityInstance = activity;
    }

    public static void setLive2DLoadInterface(Live2DLoadInterface loadInterface) {
        _loadCallback = loadInterface;
    }

    public static byte[] LoadFile(String filePath) {
        InputStream fileData = null;
        try {
            if (filePath.startsWith("/")) {
                fileData = new FileInputStream(filePath);
            } else {
                fileData = _context.getAssets().open(filePath);
            }
            int fileSize = fileData.available();
            byte[] fileBuffer = new byte[fileSize];
            fileData.read(fileBuffer, 0, fileSize);
            return fileBuffer;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (fileData != null) {
                    fileData.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void MoveTaskToBack() {
        _activityInstance.moveTaskToBack(true);
    }

    public static void OnLoadError() {
        if (_loadCallback != null) {
            _loadCallback.onLoadError();
        }
    }

    public static void OnLoadDone() {
        if (_loadCallback != null) {
            _loadCallback.onLoadDone();
        }
    }

    public static void OnLoadOneMotion(String motionGroup, int index, String motionName) {
        if (_loadCallback != null) {
            _loadCallback.onLoadOneMotion(motionGroup, index, motionName);
        }
    }

    public static void OnLoadOneExpression(String expressionName, int index) {
        if (_loadCallback != null) {
            _loadCallback.onLoadOneExpression(expressionName, index);
        }
    }

    public interface Live2DLoadInterface {
        void onLoadError();

        void onLoadDone();

        void onLoadOneMotion(String motionGroup, int index, String motionName);

        void onLoadOneExpression(String expressionName, int index);
    }
}
