#ifndef SYNTHESIZERTRN_H
#define SYNTHESIZERTRN_H

#include "utils.h"
#include "../openjtalk/asset_manager_api/manager.h"

class SynthesizerTrn {
private:
    Mat emb_t;
    Mat emb_g;

    Net enc_p;
    Net enc_q;
    Net dec;
    Net flow_reverse;
    Net flow;
    Net dp;

    void clear_nets();

    static bool
    load_weight(const std::string &folder, Mat &weight, const std::string &name, const int w,
                const int n);

    static bool
    load_model(const std::string &folder, Net &net, const string &name, bool multi,
               const Option &opt, AAssetManager *assetManager);

    std::vector<Mat>
    enc_p_forward(const Mat &x, bool vulkan, const Option &opt);

    std::vector<Mat> enc_q_forward(const Mat &x, const Mat &g, bool vulkan, const Option &opt);

    Mat emb_g_forward(int sid, const Option &opt);

    Mat dp_forward(const Mat &x, const Mat &x_mask, const Mat &z, const Mat &g, float noise_scale,
                   bool vulkan, const Option &opt);

    Mat flow_reverse_forward(const Mat &x, const Mat &x_mask, const Mat &g, bool vulkan,
                             const Option &opt);

    Mat flow_forward(const Mat &x, const Mat &x_mask, const Mat &g, bool vulkan, const Option &opt);

    Mat dec_forward(const Mat &x, const Mat &g, bool vulkan, const Option &opt);

public:

    bool init(const std::string &model_folder, bool voice_convert, bool multi, const int n_vocab,
              AAssetManager *assetManager, Option &opt);

    SynthesizerTrn();

    Mat forward(const Mat &x, const Option &opt, bool vulkan = false, bool multi = false,
                int sid = 0, float noise_scale = .667, float noise_scale_w = 0.8,
                float length_scale = 1);

    Mat voice_convert(const Mat &x, int raw_sid, int target_sid, const Option &opt,
                      bool vulkan = false);

    ~SynthesizerTrn();
};

#endif
