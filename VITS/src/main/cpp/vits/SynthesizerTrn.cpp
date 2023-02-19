#include <datareader.h>
#include "SynthesizerTrn.h"
#include "custom_layers.h"
#include "../mecab_api/api.h"

DEFINE_LAYER_CREATOR(expand_as)

DEFINE_LAYER_CREATOR(Flip)

DEFINE_LAYER_CREATOR(Transpose)

DEFINE_LAYER_CREATOR(PRQTransform)

DEFINE_LAYER_CREATOR(ResidualReverse)

DEFINE_LAYER_CREATOR(Embedding)

DEFINE_LAYER_CREATOR(SequenceMask)

DEFINE_LAYER_CREATOR(Attention)

DEFINE_LAYER_CREATOR(ExpandDim)

DEFINE_LAYER_CREATOR(SamePadding)

DEFINE_LAYER_CREATOR(ReduceDim)

DEFINE_LAYER_CREATOR(ZerosLike)

DEFINE_LAYER_CREATOR(RandnLike)

bool SynthesizerTrn::load_model(const std::string &folder, bool multi, Net &net,
                                const string &name, const Option &opt) {
    LOGI("loading %s...\n", name.c_str());
    net.register_custom_layer("Tensor.expand_as", expand_as_layer_creator);
    net.register_custom_layer("modules.Transpose", Transpose_layer_creator);
    net.register_custom_layer("Embedding", Embedding_layer_creator);
    net.register_custom_layer("modules.SequenceMask", SequenceMask_layer_creator);
    net.register_custom_layer("attentions.Attention", Attention_layer_creator);
    net.register_custom_layer("attentions.ExpandDim", ExpandDim_layer_creator);
    net.register_custom_layer("attentions.SamePadding", SamePadding_layer_creator);
    net.register_custom_layer("torch.flip", Flip_layer_creator);
    net.register_custom_layer("modules.ResidualReverse", ResidualReverse_layer_creator);
    net.register_custom_layer("modules.ReduceDims", ReduceDim_layer_creator);
    net.register_custom_layer("modules.PRQTransform", PRQTransform_layer_creator);
    net.register_custom_layer("torch.zeros_like", ZerosLike_layer_creator);
    net.register_custom_layer("modules.RandnLike", RandnLike_layer_creator);

    net.opt = opt;
    std::string bin_path = join_path(folder, name + ".ncnn.bin");
    std::string param_path;
    if (multi) param_path = "multi/" + name + ".ncnn.param";
    else param_path = "single/" + name + ".ncnn.param";
    bool param_success = !net.load_param(assetManager, param_path.c_str());
    bool bin_success = !net.load_model(bin_path.c_str());
    if (param_success && bin_success) {
        LOGI("%s loaded!", name.c_str());
        return true;
    }
    if (!param_success) LOGE("param load fail");
    if (!bin_success) LOGE("bin load fail");
    return false;
}

void SynthesizerTrn::clear_nets(){
    emb_t.release();
    emb_g.release();
    enc_p.clear();
    enc_q.clear();
    dec.clear();
    flow_reverse.clear();
    flow.clear();
    dp.clear();
}

bool SynthesizerTrn::load_weight(const std::string &folder, Mat &weight, const std::string &name,
                                 const int w,
                                 const int n) {
    LOGI("loading %s...\n", "text embedding");
    std::string path = join_path(folder, name + ".bin");
    FILE *fp = fopen(path.c_str(), "rb");
    if (fp != nullptr) {
        fseek(fp, 0, SEEK_END);
        auto file_size = ftell(fp);
        auto emb_length = file_size / sizeof(float);
        if (emb_length % w != 0) return false;
        int h = int(emb_length / w);
        if (n != -1 && h != n) return false;
        fseek(fp, 0, SEEK_SET);
        weight.create(w, h);
        fread(weight, sizeof(float), emb_length, fp);
        fclose(fp);
        LOGI("text embedding loaded!");
        return true;
    }
    LOGE("text embedding load fail");
    return false;
}

bool SynthesizerTrn::init(const std::string &model_folder, bool voice_convert, bool multi,
                          const int n_vocab, AssetJNI *assetJni, Option &opt) {
    clear_nets();
    assetManager = AAssetManager_fromJava(assetJni->env, assetJni->assetManager);

    if (voice_convert) {
        if (load_weight(model_folder, emb_t, "emb_t", 192, n_vocab) &&
            load_weight(model_folder, emb_g, "emb_g", 256, -1) &&
            load_model(model_folder, multi, enc_q, "enc_q", opt) &&
            load_model(model_folder, multi, dec, "dec", opt) &&
            load_model(model_folder, multi, flow, "flow", opt) &&
            load_model(model_folder, multi, flow_reverse, "flow.reverse", opt))
            return true;
    } else if (multi) {
        if (load_weight(model_folder, emb_t, "emb_t", 192, n_vocab) &&
            load_weight(model_folder, emb_g, "emb_g", 256, -1) &&
            load_model(model_folder, multi, enc_p, "enc_p", opt) &&
            load_model(model_folder, multi, enc_q, "enc_q", opt) &&
            load_model(model_folder, multi, dec, "dec", opt) &&
            load_model(model_folder, multi, flow, "flow", opt) &&
            load_model(model_folder, multi, flow_reverse, "flow.reverse", opt) &&
            load_model(model_folder, multi, dp, "dp", opt))
            return true;
    } else {
        if (load_weight(model_folder, emb_t, "emb_t", 192, n_vocab) &&
            load_model(model_folder, multi, enc_p, "enc_p", opt) &&
            load_model(model_folder, multi, dec, "dec", opt) &&
            load_model(model_folder, multi, flow_reverse, "flow.reverse", opt) &&
            load_model(model_folder, multi, dp, "dp", opt))
            return true;
    }

    return false;
}

std::vector<Mat>
SynthesizerTrn::enc_p_forward(const Mat &x, bool vulkan, const Option &opt) {
    Mat length(1);
    length[0] = float(x.w);
    Extractor ex = enc_p.create_extractor();
    ex.set_num_threads(opt.num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", length);
    ex.input("in2", emb_t);
    Mat out0, out1, out2, out3;
    ex.extract("out0", out0);
    ex.extract("out1", out1);
    ex.extract("out2", out2);
    ex.extract("out3", out3);
    std::vector<Mat> outputs{
            out0, out1, out2, out3
    };
    return outputs;
}

std::vector<Mat>
SynthesizerTrn::enc_q_forward(const Mat &x, const Mat &g, bool vulkan, const Option &opt) {
    Mat length(1);
    length[0] = float(x.w);
    Extractor ex = enc_q.create_extractor();
    ex.set_num_threads(opt.num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", length);
    ex.input("in2", g);
    Mat out0, out1;
    ex.extract("out0", out0);
    ex.extract("out1", out1);
    return std::vector<Mat>{out0, out1};
}

Mat SynthesizerTrn::emb_g_forward(int sid, const Option &opt) {
    Mat sid_mat(1);
    sid_mat[0] = (float) sid;
    Mat out = embedding(sid_mat, emb_g, opt);
    return out;
}

Mat SynthesizerTrn::dp_forward(const Mat &x, const Mat &x_mask, const Mat &z, const Mat &g,
                               float noise_scale,
                               bool vulkan, const Option &opt) {
    Mat out;
    Extractor ex = dp.create_extractor();
    ex.set_num_threads(opt.num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", x_mask);
    ex.input("in2", z);

    Mat noise;
    noise.create_like(z);
    noise.fill(noise_scale);

    if (!g.empty()) {
        ex.input("in3", noise);
        ex.input("in4", g);
    } else {
        ex.input("in3", noise);
    }
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::flow_reverse_forward(const Mat &x, const Mat &x_mask, const Mat &g, bool vulkan,
                                         const Option &opt) {
    Extractor ex = flow_reverse.create_extractor();
    ex.set_num_threads(opt.num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", x_mask);
    if (!g.empty()) ex.input("in2", g);
    Mat out;
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::flow_forward(const Mat &x, const Mat &x_mask, const Mat &g, bool vulkan,
                                 const Option &opt) {
    Extractor ex = flow.create_extractor();
    ex.set_num_threads(opt.num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", x_mask);
    ex.input("in2", g);
    Mat out;
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::dec_forward(const Mat &x, const Mat &g, bool vulkan, const Option &opt) {
    Extractor ex = dec.create_extractor();
    ex.set_num_threads(opt.num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    if (!g.empty()) ex.input("in1", g);
    Mat out;
    ex.extract("out0", out);
    return out;
}

SynthesizerTrn::SynthesizerTrn() = default;

// c++ implementation of SynthesizerTrn
Mat SynthesizerTrn::forward(const Mat &data, const Option &opt, bool vulkan, bool multi,
                            int sid, float noise_scale, float noise_scale_w, float length_scale) {
    LOGI("processing...\n");
    // enc_p
    auto enc_p_out = enc_p_forward(data, vulkan, opt);
    Mat x = enc_p_out[0];
    Mat m_p = enc_p_out[1];
    Mat logs_p = enc_p_out[2];
    Mat x_mask = enc_p_out[3];

    Mat g;
    if (multi) {
        g = reducedims(mattranspose(emb_g_forward(sid, opt), opt));
    }

    Mat z = randn(x.w, 2, opt, 1);

    Mat logw = dp_forward(x, x_mask, z, g, noise_scale_w, vulkan, opt);

    Mat w = product(matproduct(matexp(logw, opt), x_mask, opt), length_scale, opt);

    Mat w_ceil = ceil(w, opt);

    Mat summed = sum(w_ceil, opt);

    if (summed[0] < 1) summed[0] = 1;

    Mat y_mask = sequence_mask(summed, opt, summed[0]);

    y_mask = mattranspose(y_mask, opt);
    y_mask = reducedims(y_mask);

    Mat attn_mask = matmul(y_mask, x_mask, opt);
    attn_mask = reducedims(attn_mask);

    Mat attn = generate_path(w_ceil, attn_mask, opt);

    m_p = matmul(attn, reducedims(mattranspose(m_p, opt)), opt);
    m_p = reducedims(mattranspose(m_p, opt));

    logs_p = matmul(attn, reducedims(mattranspose(logs_p, opt)), opt);
    logs_p = reducedims(mattranspose(logs_p, opt));

    Mat m_p_rand = randn(m_p.w, m_p.h, opt);

    Mat z_p = matplus(m_p,
                      product(matproduct(m_p_rand, matexp(logs_p, opt), opt), noise_scale, opt),
                      opt);

    z = flow_reverse_forward(expanddims(z_p), mattranspose(expanddims(y_mask), opt), expanddims(g),
                             vulkan, opt);

    y_mask = mattranspose(y_mask, opt);

    y_mask = expand(y_mask, z.w, z.h, opt);

    Mat o = dec_forward(reducedims(matproduct(z, y_mask, opt)), expanddims(g),
                        vulkan, opt);
    LOGI("finished!\n");
    return o;
}

Mat SynthesizerTrn::voice_convert(const Mat &audio, int raw_sid, int target_sid,
                                  const Option &opt, bool vulkan) {

    LOGI("start converting...\n");
    // stft transform
    auto spec = stft(audio, 1024, 256, 1024, opt)[0];
    spec = matsqrt(Plus(matpow(spec, 2, opt), 1e-6, opt), opt);

    // voice conversion
    auto g_src = mattranspose(emb_g_forward(raw_sid, opt), opt);
    auto g_tgt = mattranspose(emb_g_forward(target_sid, opt), opt);
    auto enc_q_out = enc_q_forward(spec, g_src, vulkan, opt);
    auto z = expanddims(enc_q_out[0]);
    auto y_mask = enc_q_out[1];
    auto z_p = flow_forward(z, y_mask, g_src, vulkan, opt);
    auto z_hat = flow_reverse_forward(z_p, y_mask, g_tgt, vulkan, opt);
    y_mask = expand(y_mask, z_hat.w, z_hat.h, opt);
    auto o_hat = dec_forward(reducedims(matproduct(z_hat, y_mask, opt)), g_tgt,
                             vulkan, opt);
    LOGI("voice converted!\n");
    return o_hat;
}


SynthesizerTrn::~SynthesizerTrn() = default;
