#include "utils.h"

void pretty_print(const ncnn::Mat &m, const char *name) {
    std::stringstream ss;
    ss << std::setiosflags(std::ios::fixed);
    ss << std::setprecision(4);
    std::string output;
    ss << "\n";
    if (strlen(name) > 0) {
        ss << name << ":\n";
    }
    if (m.dims == 1)
        ss << "shape is: " << "(" << m.w << ")\n";
    if (m.dims == 2)
        ss << "shape is:" << "(" << m.h << "x" << m.w << ")\n";
    if (m.dims == 3)
        ss << "shape is:" << "(" << m.c << "x" << m.h << "x" << m.w << ")\n";
    if (m.dims == 4)
        ss << "shape is:" << "(" << m.c << "x" << m.c << "x" << m.h << "x" << m.w << ")\n";

    if (m.h * m.w * m.c <= 300) {
        for (int q = 0; q < m.c; q++) {
            if (m.c > 1) ss << "\n\nchannel " << q + 1 << ":\n";
            const float *ptr = m.channel(q);
            for (int y = 0; y < m.h; y++) {
                for (int x = 0; x < m.w; x++) {
                    ss << ptr[x] << "\t";
                }
                ss << "\n";
                ptr += m.w;
            }
        }
        output = ss.str();
        LOGI("%s", output.c_str());
        return;
    }
    for (int q = 0; q < m.c; q++) {
        if (m.c > 1) ss << "\n\nchannel " << q + 1 << ":\n";
        const float *ptr = m.channel(q);
        if (m.h > 10) {
            for (int y = 0; y < 3; y++) {
                if (m.w <= 20) {
                    for (int x = 0; x < m.w; x++) {
                        ss << ptr[x] << "\t";
                    }
                } else {
                    for (int x = 0; x < 3; x++) {
                        ss << ptr[x] << "\t";
                    }
                    ss << "...\t";
                    for (int x = m.w - 3; x < m.w; x++) {
                        ss << ptr[x] << "\t";
                    }
                }
                ptr += m.w;
                ss << "\n";
            }
            ss << "...\n";
            ptr += m.w * (m.h - 6);
            for (int y = 0; y < 3; y++) {
                if (m.w <= 20) {
                    for (int x = 0; x < m.w; x++) {
                        ss << ptr[x] << "\t";
                    }
                } else {
                    for (int x = 0; x < 3; x++) {
                        ss << ptr[x] << "\t";
                    }
                    ss << "...\t";
                    for (int x = m.w - 3; x < m.w; x++) {
                        ss << ptr[x] << "\t";
                    }
                }
                ptr += m.w;
                ss << "\n";
            }

        } else {
            for (int y = 0; y < m.h; y++) {
                if (m.w <= 20) {
                    for (int x = 0; x < m.w; x++) {
                        ss << ptr[x] << "\t";
                    }
                } else {
                    for (int x = 0; x < 3; x++) {
                        ss << ptr[x] << "\t";
                    }
                    ss << "...\t";
                    for (int x = m.w - 3; x < m.w; x++) {
                        ss << ptr[x] << "\t";
                    }
                }
                ss << "\n";
                ptr += m.w;
            }
        }
    }
    output = ss.str();
    LOGI("%s", output.c_str());
}

Mat softmax(const Mat &m, const Option &opt) {
    if (m.empty()) return m;
    Mat blob = m.clone();

    int w = blob.w;
    int h = blob.h;
    int channels = blob.c;

#pragma omp parallel for num_threads(opt.num_threads)
    for (int q = 0; q < channels; q++) {
        float *ptr = blob.channel(q);

        for (int i = 0; i < h; i++) {
            float max = -FLT_MAX;
            for (int j = 0; j < w; j++) {
                max = std::max(max, ptr[j]);
            }

            float sum = 0.f;
            for (int j = 0; j < w; j++) {
                ptr[j] = static_cast<float>(exp(ptr[j] - max));
                sum += ptr[j];
            }

            for (int j = 0; j < w; j++) {
                ptr[j] /= sum;
            }

            ptr += w;
        }
    }
    return blob;
}

Mat cumsum(const Mat &blob, const Option &opt) {
    if (blob.empty()) return blob;
    int w = blob.w;
    int h = blob.h;
    int c = blob.c;

    Mat res;
    res.create_like(blob);

#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < c; i++) {
        const float *ptr = blob.channel(i);
        float *outptr = res.channel(i);
        for (int j = 0; j < h; j++) {
            std::partial_sum(ptr, ptr + w, outptr);
            ptr = ptr + w;
            outptr = outptr + w;
        }
    }
    return res;
}

Mat pad(const Mat &blob, int pad_top, int pad_bottom, int pad_left, int pad_right, float pad_value,
        const Option &opt) {
    if (blob.empty()) return blob;
    Mat res;
    int pad_row = pad_left + pad_right;
    int pad_column = pad_top + pad_bottom;
    if (blob.dims == 2) {
        res.create(blob.w + pad_row, blob.h + pad_column);
    }
    if (blob.dims == 3) {
        res.create(blob.w + pad_row, blob.h + pad_column, blob.c);
    }
    res.fill(pad_value);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < blob.c; i++) {
        const float *ptr = blob.channel(i);
        float *out = res.channel(i);
        out += pad_top * res.w;
        for (int j = 0; j < blob.h; j++) {
            memcpy(out + pad_left, ptr, blob.w * sizeof(float));
            ptr += blob.w;
            out += res.w;
        }
    }
    return res;
}

Mat Slice(const Mat &blob, int top, int bottom, int left, int right, int stride_w, int stride_h,
          const Option &opt) {
    if (blob.empty()) return blob;
    Mat res;
    int res_w = ceil(float(right - left) / float(stride_w));
    int res_h = ceil(float(bottom - top) / float(stride_h));
    if (blob.dims == 2) {
        res.create(res_w, res_h);
    }
    if (blob.dims == 3) {
        res.create(res_w, res_h, blob.c);
    }
    if (stride_w == 1) {
#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < blob.c; i++) {
            const float *ptr = blob.channel(i);
            float *out = res.channel(i);
            ptr += top * blob.w;
            for (int j = 0; j < bottom - top; j += stride_h) {
                memcpy(out, ptr + left, (right - left) * sizeof(float));
                out += res.w;
                ptr += blob.w * std::min(stride_h, bottom - top - 1);
            }
        }
        return res;
    }

#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < blob.c; i++) {
        const float *ptr = blob.channel(i);
        float *out = res.channel(i);
        ptr += top * blob.w;
        for (int j = 0; j < bottom - top; j += stride_h) {
            for (int k = left, m = 0; k < right; k += stride_w, m++) {
                out[m] = ptr[std::min(k, right - 1)];
            }
            out += res.w;
            ptr += blob.w * std::min(stride_h, bottom - top - 1);
        }
    }
    return res;
}

Mat reducedims(const Mat &m) {
    if (m.empty()) return m;
    if (m.dims == 1) return m;
    if (m.dims == 2) return m.reshape(m.w * m.h);
    if (m.dims == 3) {
        if (m.c > 1) return m.reshape(m.w * m.c, m.h);
        else return m.reshape(m.w, m.h);
    }
    if (m.dims == 4) {
        return m.reshape(m.w, m.h, m.c);
    }
    return m;
}

Mat expanddims(const Mat &m) {
    if (m.empty()) return m;
    if (m.dims == 1) return m.reshape(m.w, 1);
    if (m.dims == 2) return m.reshape(m.w, m.h, 1);
    return m;
}

void set_column_value(Mat &blob, int column, float value, const Option &opt) {
    if (blob.empty()) return;
    int w = blob.w;
    int h = blob.h;
    int c = blob.c;
    if (column == -1) column = w - 1;
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < c; i++) {
        float *ptr = blob.channel(i);
        for (int j = 0; j < h; j++) {
            ptr[column] = value;
            ptr += w;
        }
    }
}

Mat softplus(const Mat &blob, const Option &opt) {
    if (blob.empty()) return blob;
    Mat res;
    res.create_like(blob);

#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < blob.c; i++) {
        const float *ptr = blob.channel(i);
        float *res_ptr = res.channel(i);
        for (int j = 0; j < blob.w * blob.h; j++) {
            res_ptr[j] = log(exp(ptr[j]) + 1);
        }
    }
    return res;
}

Mat searchsorted(Mat &bin_locations, const Mat &inputs, const Option &opt) {
    if (bin_locations.empty() || inputs.empty()) return {};
    float eps = 1e-6;
    int h = bin_locations.h;
    int w = bin_locations.w;
    int c = bin_locations.c;

#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < c; i++) {
        float *ptr = bin_locations.channel(i);
        for (int j = 0; j < h; j++) {
            float v = ptr[w - 1] + eps;
            ptr[w - 1] = v;
        }
        ptr += w;
    }

    Mat inputs_ge;
    inputs_ge.create_like(bin_locations);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < c; i++) {
        float *ge_ptr = inputs_ge.channel(i);
        const float *inputs_ptr = inputs.channel(i);
        float *bin_ptr = bin_locations.channel(i);
        for (int j = 0; j < h; j++) {
            for (int k = 0; k < w; k++) {
                if (inputs_ptr[j] >= bin_ptr[k]) ge_ptr[k] = 1;
                else ge_ptr[k] = 0;
            }
            ge_ptr += w;
            bin_ptr += w;
        }
    }

    Mat res;
    res.create_like(inputs); // 100x1
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < c; i++) {
        float *res_ptr = res.channel(i);
        float *ge_ptr = inputs_ge.channel(i);
        for (int j = 0; j < h; j++) {
            float value = 0;
            for (int k = 0; k < w; k++) {
                value += ge_ptr[k];
            }
            res_ptr[j] = value - 1;
            ge_ptr += w;
        }
    }

    return res;
}

Mat gather(Mat &blob, Mat &index, const Option &opt) {
    if (blob.empty()) return blob;
    Mat res;
    res.create_like(index);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < blob.c; i++) {
        const float *ptr = blob.channel(i);
        const float *idx_ptr = index.channel(i);
        float *outptr = res.channel(i);
        for (int j = 0; j < blob.h; j++) {
            int k = int(idx_ptr[j]);
            outptr[j] = ptr[k];
            ptr += blob.w;
        }
    }
    return res;
}

Mat matplus(const Mat &m1, const Mat &m2, const Option &opt) {
    if (m1.empty() || m2.empty()) return {};
    Mat res;
    res.create_like(m1);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p1 = m1.channel(i);
        const float *p2 = m2.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = p1[j] + p2[j];
        }
    }
    return res;
}

Mat matminus(const Mat &m1, const Mat &m2, const Option &opt) {
    if (m1.empty() || m2.empty()) return {};
    Mat res;
    res.create_like(m1);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p1 = m1.channel(i);
        const float *p2 = m2.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = p1[j] - p2[j];
        }
    }
    return res;
}

Mat matdiv(const Mat &m1, const Mat &m2, const Option &opt) {
    if (m1.empty() || m2.empty()) return {};
    Mat res;
    res.create_like(m1);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p1 = m1.channel(i);
        const float *p2 = m2.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = p1[j] / p2[j];
        }
    }
    return res;
}

Mat matproduct(const Mat &m1, const Mat &m2, const Option &opt) {
    if (m1.empty() || m2.empty()) return {};
    Mat res;
    res.create_like(m1);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p1 = m1.channel(i);
        const float *p2 = m2.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = p1[j] * p2[j];
        }
    }
    return res;
}

Mat product(const Mat &m, float value, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = p[j] * value;
        }
    }
    return res;
}

Mat matpow(const Mat &m, float value, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = pow(p[j], value);
        }
    }
    return res;
}

Mat matexp(const Mat &m, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = exp(p[j]);
        }
    }
    return res;
}

Mat ceil(const Mat &m, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = ceil(p[j]);
        }
    }
    return res;
}

Mat sum(const Mat &m, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    res.create(1);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        float summed = 0;
        for (int j = 0; j < m.w * m.h; j++) {
            summed += p[j];
        }
        out[0] = summed;
    }
    return res;
}

Mat div(const Mat &m, float value, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = p[j] / value;
        }
    }
    return res;
}

Mat matsqrt(const Mat &m, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = sqrt(p[j]);
        }
    }
    return res;
}

float matmax(const Mat &m, const Option &opt) {
    if (m.empty()) return 0;
    if (m.w * m.h * m.c == 1) return m[0];
    float max = -FLT_MAX;
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < m.c; i++) {
        const float *p = m.channel(i);
        for (int j = 0; j < m.w * m.h; j++) {
            if (p[j] >= max) max = p[j];
        }
    }
    return max;
}

Mat expand(const Mat &m, int w, int h, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    if (m.dims > 2) res.create(w, h, m.c);
    else res.create(w, h);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < m.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        if (m.w == 1 && h == m.h) {
            for (int j = 0; j < m.h; j++) {
                for (int k = 0; k < w; k++) {
                    out[k] = p[j];
                }
                out += res.w;
            }
        }
        if (m.h == 1 && w == m.w) {
            for (int j = 0; j < h; j++) {
                memcpy(out, p, m.w * sizeof(float));
                out += res.w;
            }
        }
    }
    return res;
}

Mat randn(int w, int h, const Option &opt, int c) {
    Mat res;
    if (c == 0) res.create(w, h);
    else res.create(w, h, c);
    std::mt19937 generator(std::time(nullptr));
    std::normal_distribution<float> distribution(0.0, 1.0);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        float *ptr = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            float rand_float = distribution(generator);
            ptr[j] = rand_float;
        }
    }
    return res;
}

Mat sequence_mask(const Mat &length, const Option &opt, float max_length_) {
    if (length.empty()) return {};
    int max_length;
    if (max_length_ == 0) {
        max_length = int(matmax(length, opt));
    } else {
        max_length = int(max_length_);
    }
    Mat x(max_length, 1);
    float *p = x.channel(0);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < max_length; i++) {
        p[i] = float(i);
    }
    Mat res(x.w, length.w);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < x.c; i++) {
        float *out = res.channel(i);
        float *p1 = x.channel(i);
        const float *p2 = length.channel(i);
        for (int j = 0; j < res.h; j++) {
            for (int k = 0; k < res.w; k++) {
                if (p1[k] < p2[j]) out[k] = 1;
                else out[k] = 0;
            }
            out += res.w;
        }
    }
    return res;
}

Mat generate_path(const Mat &duration, const Mat &mask, const Option &opt) {
    if (duration.empty() || mask.empty()) return {};
    Mat cum_duration = cumsum(duration, opt);
    auto t_y = float(mask.h);
    Mat path = sequence_mask(cum_duration, opt, t_y);
    Mat padded_path = pad(path, 1, 0, 0, 0, 0, opt);
    padded_path = Slice(padded_path, 0, padded_path.h - 1,
                        0, padded_path.w, 1, 1, opt);
    path = matminus(path, padded_path, opt);
    path = matproduct(reducedims(mattranspose(path, opt)), mask, opt);
    return path;
}

Mat mattranspose(const Mat &m, const Option &opt) {
    if (m.empty()) return m;
    int w = m.w;
    int h = m.h;
    int c = m.c;

    int target_h = w;
    int target_w = h;

    Mat res(target_w, target_h, c);

#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < c; i++) {
        const float *ptr = m.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < w; j++) {
            for (int k = 0; k < h; k++) {
                int index = j + k * w;
                out[k] = ptr[index];
            }
            out += h;
        }
    }
    return res;
}

Mat matmul(const Mat &m1, const Mat &m2, const Option &opt) {
    if (m1.empty() || m2.empty()) return {};
    Mat res;
    res.create(m2.w, m1.h, m1.c);

#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < m1.c; i++) {
        // get row1
        const float *p1 = m1.channel(i);
        const float *p2 = m2.channel(i);
        float *p = res.channel(i);
        for (int j = 0; j < m1.h; j++) {
            // iterate m2 columns
            for (int k = 0; k < m2.w; k++) {
                // sum m1 row and m2 columns
                float sum = 0;
                for (int n = 0; n < m2.h; n++) {
                    sum += p1[n] * p2[k + n * m2.w];
                }
                p[0] = sum;
                p++;
            }
            p1 += m1.w;
        }
    }
    return res;
}

void mask_fill(Mat &m, const Mat &mask, const char *condition, float condition_value, float value,
               const Option &opt) {
    if (m.empty() || mask.empty()) return;
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < m.c; i++) {
        float *ptr = m.channel(i);
        const float *m_ptr = mask.channel(i);
        for (int j = 0; j < m.w * m.h; j++) {
            if (!strcmp(condition, "=") && m_ptr[i] == condition_value) {
                ptr[i] = value;
                continue;
            }
            if (!strcmp(condition, ">") && m_ptr[i] > condition_value) {
                ptr[i] = value;
                continue;
            }
            if (!strcmp(condition, "<") && m_ptr[i] < condition_value) {
                ptr[i] = value;
                continue;
            }
            if (!strcmp(condition, ">=") && m_ptr[i] >= condition_value) {
                ptr[i] = value;
                continue;
            }
            if (!strcmp(condition, "<=") && m_ptr[i] <= condition_value) {
                ptr[i] = value;
                continue;
            }
        }
    }
}

Mat get_relative_embeddings(const Mat &relative_embeddings, int length, int window_size,
                            const Option &opt) {
    int pad_length = std::max(length - window_size - 1, 0);
    int slice_start_position = std::max(window_size + 1 - length, 0);
    int slice_end_position = slice_start_position + 2 * length - 1;
    Mat padded_relative_embeddings;
    if (pad_length > 0) {
        padded_relative_embeddings = pad(relative_embeddings, pad_length,
                                         pad_length, 0, 0, 0, opt);
    } else {
        padded_relative_embeddings = relative_embeddings.clone();
    }
    // slicing
    Mat used_relative_embeddings = Slice(padded_relative_embeddings, slice_start_position,
                                         slice_end_position, 0, padded_relative_embeddings.w,
                                         1, 1, opt);
    return used_relative_embeddings;
}

Mat matmul_with_relative_keys(const Mat &x, const Mat &y, const Option &opt) {
    if (x.empty() || y.empty()) return {};
    // concat
    Mat y_;
    y_.create(y.w, y.h, y.c * 2);
    const float *y_ptr = y.channel(0);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < y_.c; i++) {
        float *out_ptr = y_.channel(i);
        memcpy(out_ptr, y_ptr, y.w * y.h * sizeof(float));
    }
    Mat y_t = mattranspose(y_, opt);
    return matmul(x, y_t, opt);
}

Mat relative_position_to_absolute_position(const Mat &x, const Option &opt) {
    if (x.empty()) return x;
    int heads = x.c;
    int length = x.h;
    // padding
    Mat x_pad = pad(x, 0, 0, 0, 1, 0, opt);
    Mat x_flat = x_pad.reshape(length * 2 * length, heads, 1); // 2x20000
    // padding
    Mat x_flat_pad = pad(x_flat, 0, 0, 0,
                         length - 1, 0, opt);
    // reshape
    x_flat_pad = x_flat_pad.reshape(2 * length - 1, length + 1, heads);
    // slice
    Mat x_final = Slice(x_flat_pad, 0, length, length - 1,
                        x_flat_pad.w, 1, 1, opt);
    return x_final;
}

Mat absolute_position_to_relative_position(const Mat &x, const Option &opt) {
    if (x.empty()) return {};
    int heads = x.c;
    int length = x.h;
    Mat x_pad = pad(x, 0, 0, 0, length - 1, 0, opt);
    Mat x_flat = x_pad.reshape(length * length + length * (length - 1), heads, 1);

    Mat x_flat_pad = pad(x_flat, 0, 0, length, 0, 0, opt);
    Mat x_final = x_flat_pad.reshape(2 * length, length, heads);
    x_final = Slice(x_final, 0, x_final.h, 1, x_final.w, 1, 1, opt);
    return x_final;
}

Mat matmul_with_relative_values(const Mat &x, const Mat &y, const Option &opt) {
    if (x.empty() || y.empty()) return {};
    // concat
    Mat y_;
    y_.create(y.w, y.h, y.c * 2);
    const auto *y_p = (const float *) y;
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < y_.c; i++) {
        float *p = y_.channel(i);
        memcpy(p, y_p, y.w * y.h * sizeof(float));
    }
    return matmul(x, y_, opt);
}

Mat zeros_like(const Mat &x, const Option &opt) {
    if (x.empty()) return x;
    Mat out = x.clone();
    out.fill(0);
    return out;
}

Mat concat(const Mat &m1, const Mat &m2, const Option &opt) {
    if (m1.empty() || m2.empty()) return {};
    Mat res(m1.w, m1.h + m2.h);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < m1.c; i++) {
        const float *p1 = m1.channel(i);
        const float *p2 = m2.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.h; j++) {
            if (j < m1.h) {
                memcpy(out, p1, m1.w * sizeof(float));
                p1 += m1.w;
            } else {
                memcpy(out, p2, m2.w * sizeof(float));
                p2 += m2.w;
            }
            out += res.w;
        }
    }
    return res;
}

std::string join_path(const std::string &folder, const std::string &file) {
    if (folder.find_last_of('/') || folder.find_last_of('\\')) {
        return folder + file;
    } else {
        return folder + "/" + file;
    }
}

std::vector<std::complex<fftpack_real>>
rfft1d(const fftpack_real *data, const fftpack_int size, const Option &opt) {
    std::vector<std::complex<float>> res;

    auto *rfin = new fftpack_real[size];

    memcpy(rfin, data, size * sizeof(fftpack_real));

    auto *wsave = new fftpack_real[size * 3];

    rffti(size, wsave);
    rfftf(size, rfin, wsave);

#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < size; i++) {
        if (i == 0 || i == size - 1) {
            res.emplace_back(rfin[i], 0);
            continue;
        }
        if (i % 2 == 1 && i < size) {
            res.emplace_back(rfin[i], rfin[i + 1]);
        }
    }
    delete[] rfin;
    delete[] wsave;
    return res;
}

std::vector<Mat> rfft(const Mat &m, const Option &opt) {
    if (m.empty()) return {};
    Mat real, image;
    if (m.dims == 2) {
        real.create(m.w / 2 + 1, m.h);
        image.create(m.w / 2 + 1, m.h);
    }
    if (m.dims == 3) {
        real.create(m.w / 2 + 1, m.h, m.c);
        image.create(m.w / 2 + 1, m.h, m.c);
    }
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < m.c; i++) {
        const float *ptr = m.channel(i);
        float *real_ptr = real.channel(i);
        float *imag_ptr = image.channel(i);
        for (int j = 0; j < m.h; j++) {
            auto *input = new float[m.w];

            memcpy(input, ptr, m.w * sizeof(float));
            auto rfft_out = rfft1d(input, m.w, opt);

            // get real
            std::vector<float> real_v;
            std::transform(rfft_out.begin(), rfft_out.end(), std::back_inserter(real_v),
                           [](const std::complex<float> &c) { return c.real(); });

            // assign real
            memcpy(real_ptr, real_v.data(), real_v.size() * sizeof(float));

            // get image
            std::vector<float> image_v;
            std::transform(rfft_out.begin(), rfft_out.end(), std::back_inserter(image_v),
                           [](const std::complex<float> &c) { return c.imag(); });

            // assign image
            memcpy(imag_ptr, image_v.data(), image_v.size() * sizeof(float));

            delete[] input;

            ptr += m.w;
            real_ptr += real.w;
            imag_ptr += image.w;
        }
    }
    return std::vector<Mat>{real, image};
}

Mat hanning_window(const int n, const Option &opt) {
    Mat res(n, 1);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        float *ptr = res.channel(i);
        for (int j = 0; j < n; j++) {
            ptr[j] = float(0.5 - 0.5 * cos(2 * PI * j / (float(n - 1))));
        }
    }
    return res;
}

Mat as_strides(const Mat &x, const int h, const int w, const Option &opt) {
    if (x.empty()) return {};
    Mat res(h, w);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < x.c; i++) {
        const float *x_ptr = x.channel(i);
        float *ptr = res.channel(i);
        for (int j = 0; j < w; j++) {
            memcpy(ptr, x_ptr + j, res.w * sizeof(float));
            ptr += res.w;
        }
    }
    res = reducedims(mattranspose(res, opt));
    return res;
}

Mat frame(const Mat &x, const int frame_length, const int hop_length, const Option &opt) {
    if (x.empty()) return {};
    // x: [1,n]
    int x_w_trimmed = x.w - (frame_length - 1);
    auto xw = as_strides(x, x_w_trimmed, frame_length, opt);
    xw = mattranspose(xw, opt);
    xw = Slice(xw, 0, xw.h, 0, xw.w, hop_length, 1, opt);
    return reducedims(xw);
}

std::vector<Mat>
stft(const Mat &y, const int filter_length, const int hop_length, const int win_length,
     const Option &opt) {
    if (y.empty()) return {};
    // hann window
    auto fft_window = hanning_window(win_length, opt);
    fft_window = reducedims(mattranspose(fft_window, opt));
    int pad_window_size = (filter_length - win_length) / 2;

    // pad window
    fft_window = pad(fft_window, 0, 0,
                     pad_window_size, pad_window_size, 0, opt);

    // pad y
    int pad_y_size = filter_length / 2;
    Mat y_padded = pad(y, 0, 0, pad_y_size, pad_y_size, 0, opt);

    // get frames
    auto y_frames = frame(y_padded, filter_length, hop_length, opt);

    int n_columns = MAX_MEM_BLOCK / (2 * sizeof(float) * (y_frames.h / 2 + 1));
    n_columns = std::max(n_columns, 1);

    Mat real, imag;

    // stft
#pragma omp parallel for num_threads(opt.num_threads)
    for (int bl_s = 0; bl_s < y_frames.w; bl_s += n_columns) {
        int bl_t = std::min(bl_s + n_columns, y_frames.w);
        auto y_frame_sliced = Slice(y_frames, 0, y_frames.h,
                                    bl_s, bl_t, 1, 1, opt);
        auto expaned_fft_window = expand(fft_window, y_frame_sliced.w, y_frame_sliced.h, opt);
        auto fft_input = matproduct(expaned_fft_window, y_frame_sliced, opt);
        auto fft_output = rfft(reducedims(mattranspose(fft_input, opt)), opt);
        if (real.empty()) real = fft_output[0];
        else real = concat(real, fft_output[0], opt);
        if (imag.empty()) imag = fft_output[1];
        else imag = concat(imag, fft_output[1], opt);
    }
    real = reducedims(mattranspose(real, opt));
    imag = reducedims(mattranspose(imag, opt));
    return std::vector<Mat>{real, imag};
}

Mat Plus(const Mat &m, float value, const Option &opt) {
    if (m.empty()) return m;
    Mat res;
    res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < res.c; i++) {
        const float *p = m.channel(i);
        float *out = res.channel(i);
        for (int j = 0; j < res.w * res.h; j++) {
            out[j] = p[j] + value;
        }
    }
    return res;
}

Mat embedding(const Mat &x, const Mat &weight, const Option &opt) {
    if (x.empty() || weight.empty()) return {};
    Mat output(weight.w, x.w * x.h);
    if (output.empty()) return {};

#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < x.c; i++){
        const float *x_p = x.channel(i);
        const float *w_p = weight.channel(i);
        float *ptr = output.channel(i);
        for (int j = 0; j < x.w * x.h; j++) {
            int word_index = static_cast<int>(x_p[j]);
            memcpy(ptr, w_p + weight.w * word_index, weight.w * sizeof(float));
            ptr += output.w;
        }
    }
    return output;
}

Mat flip(const Mat &x, const Option &opt, int dim) {
    Mat output;
    output.create_like(x);
#pragma omp parallel for num_threads(opt.num_threads)
    for (int i = 0; i < x.c; i++) {
        const float *p = x.channel(i);
        float *t = output.channel(i);
        if (dim == 1) {
            p += x.w * x.h;
            for (int j = 0; j < x.h; j++) {
                memcpy(t, p - x.w, sizeof(float) * x.w);
                p -= x.w;
                t += x.w;
            }
        } else {
            for (int j = 0; j < x.h; j++) {
                // reverse
                for (int k = 0; k < x.w / 2; ++k) {
                    float tmp = p[x.w - k - 1];
                    t[x.w - k - 1] = p[k];
                    t[k] = tmp;
                }
                t[x.w / 2] = p[x.w / 2];
                p += x.w;
                t += x.w;
            }
        }
    }
    return output;
}
