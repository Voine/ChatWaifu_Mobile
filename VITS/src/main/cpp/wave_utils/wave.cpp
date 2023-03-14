//
// Created by LZC on 2023/1/17.
//

#include "wave.h"

char* PCMToWavFormat(float* audio, size_t length, int sampling_rate) {
    if (length == 0) return {};

    size_t audioLen = length * 4;
    size_t dataLen = audioLen + 36;
    int channels = 1;
    int sample_bit = sizeof(float) * 8;
    long bit_rate = sampling_rate * channels * 16 / 8;

    //std::vector<char> wave;
    char* wave = new char[dataLen + 8];

    // add wave head
    // RIFF/WAVE header
    wave[0] = 'R';
    wave[1] = 'I';
    wave[2] = 'F';
    wave[3] = 'F';
    wave[4] = (char)(dataLen & 0xff);
    wave[5] = (char)((dataLen >> 8) & 0xff);
    wave[6] = (char)((dataLen >> 16) & 0xff);
    wave[7] = (char)((dataLen >> 24) & 0xff);

    //WAVE
    wave[8] = 'W';
    wave[9] = 'A';
    wave[10] = 'V';
    wave[11] = 'E';

    //'fmt' chunk
    wave[12] = 'f';
    wave[13] = 'm';
    wave[14] = 't';
    wave[15] = ' ';

    // 4 chars: size of'fmt' chunk
    wave[16] = 16;
    wave[17] = 0;
    wave[18] = 0;
    wave[19] = 0;

    // encoding
    wave[20] = 3; // IEEE float
    wave[21] = 0;

    // channel
    wave[22] = (char)channels;
    wave[23] = 0;

    // sample rate
    wave[24] = (char)(sampling_rate & 0xff);
    wave[25] = (char)((sampling_rate >> 8) & 0xff);
    wave[26] = (char)((sampling_rate >> 16) & 0xff);
    wave[27] = (char)((sampling_rate >> 24) & 0xff);

    // byteRate
    wave[28] = (char)(bit_rate & 0xff);
    wave[29] = (char)((bit_rate >> 8) & 0xff);
    wave[30] = (char)((bit_rate >> 16) & 0xff);
    wave[31] = (char)((bit_rate >> 24) & 0xff);

    // block align
    wave[32] = (char)(channels * sample_bit / 8);
    wave[33] = 0;

    // bits per sample
    wave[34] = (char) sample_bit;
    wave[35] = 0;

    //data
    wave[36] = 'd';
    wave[37] = 'a';
    wave[38] = 't';
    wave[39] = 'a';

    wave[40] = (char)(audioLen & 0xff);
    wave[41] = (char)((audioLen >> 8) & 0xff);
    wave[42] = (char)((audioLen >> 16) & 0xff);
    wave[43] = (char)((audioLen >> 24) & 0xff);

    // add wave data
    memcpy(wave + 44, audio, audioLen);
    return wave;
}
