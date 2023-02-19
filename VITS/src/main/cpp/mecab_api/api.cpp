#include "api.h"

std::string utf8_encode(const std::wstring& source)
{
    return std::wstring_convert<std::codecvt_utf8<wchar_t>>().to_bytes(source);
}

std::wstring utf8_decode(const std::string& source)
{
    return std::wstring_convert<std::codecvt_utf8<wchar_t>>().from_bytes(source);
}

wstring njd_node_get_string(NJDNode* node) {
    BYTE* s = (BYTE*) NJDNode_get_string(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_pos(NJDNode* node) {
    BYTE* s = (BYTE*)NJDNode_get_pos(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_pos_group1(NJDNode* node) {
    BYTE* s = (BYTE*)NJDNode_get_pos_group1(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_pos_group2(NJDNode * node){
    BYTE* s = (BYTE*)NJDNode_get_pos_group2(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_pos_group3(NJDNode * node){
    BYTE* s = (BYTE*)NJDNode_get_pos_group3(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_ctype(NJDNode * node){
    BYTE* s = (BYTE*)NJDNode_get_ctype(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_cform(NJDNode * node){
    BYTE* s = (BYTE*)NJDNode_get_cform(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_orig(NJDNode * node){
    BYTE* s = (BYTE*)NJDNode_get_orig(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_read(NJDNode * node){
    BYTE* s = (BYTE*)NJDNode_get_read(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
wstring njd_node_get_pron(NJDNode * node){
    BYTE* s = (BYTE*)NJDNode_get_pron(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
int njd_node_get_acc(NJDNode * node){
    return NJDNode_get_acc(node);
}
int njd_node_get_mora_size(NJDNode * node){
    return NJDNode_get_mora_size(node);
}
wstring njd_node_get_chain_rule(NJDNode * node){
    BYTE* s = (BYTE*)NJDNode_get_chain_rule(node);
    string ss;
    for (int i = 0; s[i] != 0; i++) {
        ss.push_back(s[i]);
    }
    return utf8_decode(ss);
}
int njd_node_get_chain_flag(NJDNode * node){
    return NJDNode_get_chain_flag(node);
}

Feature* node2feature(NJDNode* node) {
    Feature* feature = new Feature();
    feature->string = njd_node_get_string(node);
    feature->pos = njd_node_get_pos(node);
    feature->pos_group1 = njd_node_get_pos_group1(node);
    feature->pos_group2 = njd_node_get_pos_group2(node);
    feature->pos_group3 = njd_node_get_pos_group3(node);
    feature->ctype = njd_node_get_ctype(node);
    feature->cform = njd_node_get_cform(node);
    feature->orig = njd_node_get_orig(node);
    feature->read = njd_node_get_read(node);
    feature->pron = njd_node_get_pron(node);
    feature->acc = njd_node_get_acc(node);
    feature->mora_size = njd_node_get_mora_size(node);
    feature->chain_rule = njd_node_get_chain_rule(node);
    feature->chain_flag = njd_node_get_chain_flag(node);
    return feature;
}

vector<Feature*> njd2feature(NJD* njd) {
    NJDNode* node = njd->head;
    vector<Feature*> features;
    while (node) {
        features.push_back(node2feature(node));
        node = node->next;
    }
    return features;
}

void feature2njd(NJD* njd, vector<Feature*> features) {
    NJDNode* node;
    for (auto feature : features) {
        node = new NJDNode();
        NJDNode_initialize(node);
        NJDNode_set_string(node, utf8_encode(feature->string).c_str());
        NJDNode_set_pos(node, utf8_encode(feature->pos).c_str());
        NJDNode_set_pos_group1(node, utf8_encode(feature->pos_group1).c_str());
        NJDNode_set_pos_group2(node, utf8_encode(feature->pos_group2).c_str());
        NJDNode_set_pos_group3(node, utf8_encode(feature->pos_group3).c_str());
        NJDNode_set_ctype(node, utf8_encode(feature->ctype).c_str());
        NJDNode_set_cform(node, utf8_encode(feature->cform).c_str());
        NJDNode_set_orig(node, utf8_encode(feature->orig).c_str());
        NJDNode_set_read(node, utf8_encode(feature->read).c_str());
        NJDNode_set_pron(node, utf8_encode(feature->pron).c_str());
        NJDNode_set_acc(node, feature->acc);
        NJDNode_set_mora_size(node, feature->mora_size);
        NJDNode_set_chain_rule(node, utf8_encode(feature->chain_rule).c_str());
        NJDNode_set_chain_flag(node, feature->chain_flag);
        NJD_push_node(njd, node);
    }
}

OpenJtalk::OpenJtalk(const char* path, AssetJNI* asjni) {
    mecab = new Mecab();
    njd = new NJD();
    jpcommon = new JPCommon();

    Mecab_initialize(mecab);
    NJD_initialize(njd);
    JPCommon_initialize(jpcommon);

    int r = Mecab_load(mecab, path, asjni);
    if (r != 1) {
        _clear();
        cerr << path << " not found!" << endl;
        exit(1);
    }
}

void OpenJtalk::_clear() {
    Mecab_clear(mecab);
    NJD_clear(njd);
    JPCommon_clear(jpcommon);
}

vector<Feature*> OpenJtalk::run_frontend(wstring text) {
    string u8text = utf8_encode(text);
    char buff[8192];
    text2mecab(buff, u8text.c_str());
    Mecab_analysis(mecab, buff);
    mecab2njd(njd, Mecab_get_feature(mecab), Mecab_get_size(mecab));
    njd_set_pronunciation(njd);
    njd_set_digit(njd);
    njd_set_accent_phrase(njd);
    njd_set_accent_type(njd);
    njd_set_unvoiced_vowel(njd);
    njd_set_long_vowel(njd);
    auto features = njd2feature(njd);
    NJD_refresh(njd);
    Mecab_refresh(mecab);
    return features;
}

vector<wstring> OpenJtalk::make_label(vector<Feature*> features) {
    feature2njd(njd, features);
    njd2jpcommon(jpcommon, njd);
    JPCommon_make_label(jpcommon);
    int label_size = JPCommon_get_label_size(jpcommon);
    char** label_feature = JPCommon_get_label_feature(jpcommon);
    vector<wstring> labels;
    for (int i = 0; i < label_size; i++) {
        string ts(label_feature[i], strlen(label_feature[i]));
        labels.push_back(utf8_decode(ts));
    }
    JPCommon_refresh(jpcommon);
    NJD_refresh(njd);
    return labels;
}

OpenJtalk::~OpenJtalk() {
    _clear();
}

string OpenJtalk::words_split(const char *inputs) {

    auto* tagger = (MeCab::Tagger *)mecab->tagger;
    return tagger->parse(inputs);
}
