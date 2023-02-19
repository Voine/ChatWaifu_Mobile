//
// Created by LZC on 2023/1/17.
//

#ifndef MOERENG_AUDIO_H
#define MOERENG_AUDIO_H

#include <cstdint>
#include <vector>
#include <string>

// convert pcm audio to wav format
char* PCMToWavFormat(float* audio, size_t length, int sampling_rate);

#endif //MOERENG_AUDIO_H
