// ISherpaAidlInterface.aidl
package com.k2fsa.sherpa.ncnn;
import com.k2fsa.sherpa.ncnn.ISherpaResultAidlCallback;

// Declare any non-default types here with import statements

interface ISherpaAidlInterface {

    void initSherpa();

    void startRecord();

    void finishRecord(ISherpaResultAidlCallback callback);
}