#include <jni.h>

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

extern "C" {
#include "nd_grammar.h"
#include "nd_model.h"
#include "nd_quant.h"
#include "nd_sample.h"
#include "nd_tokenizer.h"
}

namespace {

using Clock = std::chrono::steady_clock;

static double elapsed_ms(Clock::time_point start, Clock::time_point end = Clock::now()) {
    return std::chrono::duration<double, std::milli>(end - start).count();
}

class RowPool {
public:
    RowPool() {
        const unsigned cores = std::thread::hardware_concurrency();
        worker_count_ = static_cast<int>(std::clamp(cores > 1 ? cores - 1 : 1u, 1u, 3u));
        for (int i = 0; i < worker_count_; ++i) {
            workers_.emplace_back([this, i] { worker(i); });
        }
    }

    ~RowPool() {
        {
            std::lock_guard<std::mutex> lock(mu_);
            stop_ = true;
            ++generation_;
        }
        start_cv_.notify_all();
        for (auto &thread : workers_) {
            if (thread.joinable()) thread.join();
        }
    }

    void run(nd_row_fn fn, void *ctx, uint32_t nrows) {
        if (nrows < 8 || worker_count_ == 0) {
            fn(ctx, 0, nrows);
            return;
        }

        const uint32_t chunks = static_cast<uint32_t>(worker_count_ + 1);
        {
            std::lock_guard<std::mutex> lock(mu_);
            fn_ = fn;
            ctx_ = ctx;
            nrows_ = nrows;
            pending_ = worker_count_;
            ++generation_;
        }
        start_cv_.notify_all();

        fn(ctx, 0, nrows / chunks);

        std::unique_lock<std::mutex> lock(mu_);
        done_cv_.wait(lock, [this] { return pending_ == 0; });
    }

private:
    void worker(int index) {
        uint64_t seen = 0;
        for (;;) {
            nd_row_fn fn;
            void *ctx;
            uint32_t begin;
            uint32_t end;
            {
                std::unique_lock<std::mutex> lock(mu_);
                start_cv_.wait(lock, [this, &seen] {
                    return stop_ || generation_ != seen;
                });
                if (stop_) return;
                seen = generation_;
                fn = fn_;
                ctx = ctx_;
                const uint32_t chunks = static_cast<uint32_t>(worker_count_ + 1);
                begin = nrows_ * static_cast<uint32_t>(index + 1) / chunks;
                end = nrows_ * static_cast<uint32_t>(index + 2) / chunks;
            }

            fn(ctx, begin, end);

            {
                std::lock_guard<std::mutex> lock(mu_);
                --pending_;
                if (pending_ == 0) done_cv_.notify_one();
            }
        }
    }

    std::mutex mu_;
    std::condition_variable start_cv_;
    std::condition_variable done_cv_;
    std::vector<std::thread> workers_;
    nd_row_fn fn_ = nullptr;
    void *ctx_ = nullptr;
    uint32_t nrows_ = 0;
    uint64_t generation_ = 0;
    int worker_count_ = 0;
    int pending_ = 0;
    bool stop_ = false;
};

static RowPool &row_pool() {
    static RowPool pool;
    return pool;
}

static void parallel_rows(nd_row_fn fn, void *ctx, uint32_t nrows) {
    row_pool().run(fn, ctx, nrows);
}

class CallbackSink {
public:
    CallbackSink(JNIEnv *env, jobject callbacks) : env_(env), callbacks_(callbacks) {
        jclass cls = env_->GetObjectClass(callbacks_);
        on_status_ = env_->GetMethodID(
            cls, "onStatus", "(Ljava/lang/String;Ljava/lang/String;II)V");
        on_token_ = env_->GetMethodID(cls, "onToken", "([BIIDD)V");
        on_prefill_ = env_->GetMethodID(cls, "onPrefill", "(IDD)V");
        on_complete_ = env_->GetMethodID(cls, "onComplete", "(IDDDD[B)V");
        on_error_ = env_->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        env_->DeleteLocalRef(cls);
    }

    bool valid() const {
        return on_status_ && on_token_ && on_prefill_ && on_complete_ && on_error_;
    }

    void status(const char *phase, const std::string &message, int current, int total) {
        jstring jphase = env_->NewStringUTF(phase);
        jstring jmessage = env_->NewStringUTF(message.c_str());
        env_->CallVoidMethod(callbacks_, on_status_, jphase, jmessage, current, total);
        env_->DeleteLocalRef(jphase);
        env_->DeleteLocalRef(jmessage);
    }

    void token(const char *bytes, size_t size, int token_id, int index,
               double elapsed, double delta) {
        jbyteArray data = env_->NewByteArray(static_cast<jsize>(size));
        if (size > 0) {
            env_->SetByteArrayRegion(data, 0, static_cast<jsize>(size),
                                     reinterpret_cast<const jbyte *>(bytes));
        }
        env_->CallVoidMethod(callbacks_, on_token_, data, token_id, index, elapsed, delta);
        env_->DeleteLocalRef(data);
    }

    void prefill(int tokens, double milliseconds, double tps) {
        env_->CallVoidMethod(callbacks_, on_prefill_, tokens, milliseconds, tps);
    }

    void complete(int tokens, double decode_ms, double decode_tps, double ttft_ms,
                  double confidence, const std::string &raw) {
        jbyteArray bytes = env_->NewByteArray(static_cast<jsize>(raw.size()));
        if (!raw.empty()) {
            env_->SetByteArrayRegion(bytes, 0, static_cast<jsize>(raw.size()),
                                     reinterpret_cast<const jbyte *>(raw.data()));
        }
        env_->CallVoidMethod(callbacks_, on_complete_, tokens, decode_ms, decode_tps,
                             ttft_ms, confidence, bytes);
        env_->DeleteLocalRef(bytes);
    }

    void error(const std::string &message) {
        jstring value = env_->NewStringUTF(message.c_str());
        env_->CallVoidMethod(callbacks_, on_error_, value);
        env_->DeleteLocalRef(value);
    }

private:
    JNIEnv *env_;
    jobject callbacks_;
    jmethodID on_status_ = nullptr;
    jmethodID on_token_ = nullptr;
    jmethodID on_prefill_ = nullptr;
    jmethodID on_complete_ = nullptr;
    jmethodID on_error_ = nullptr;
};

static std::mutex g_inference_mu;
static nd_model g_model{};
static nd_grammar g_grammar{};
static uint8_t *g_blob = nullptr;
static bool g_model_open = false;
static bool g_ready = false;
static std::string g_tools_json;

static jstring error_string(JNIEnv *env, const std::string &message) {
    return env->NewStringUTF(message.c_str());
}

static std::string take_bytes(JNIEnv *env, jbyteArray value) {
    if (!value) return {};
    const jsize size = env->GetArrayLength(value);
    std::string result(static_cast<size_t>(size), '\0');
    if (size > 0) {
        env->GetByteArrayRegion(value, 0, size,
                                reinterpret_cast<jbyte *>(result.data()));
    }
    return result;
}

static int prime_prefix(CallbackSink &sink, std::string &error) {
    std::vector<uint32_t> ids(1024);
    const std::string prefix =
        "<|im_start|>user\n<tools>" + g_tools_json +
        "</tools>\nlocation: here";

    nd_model_reset(&g_model);
    ids[0] = ND_BOS_ID;
    int encoded = nd_tok_encode(&g_model.tok, prefix.data(), prefix.size(), ids.data() + 1,
                                static_cast<uint32_t>(ids.size() - 1));
    if (encoded < 0) {
        error = "The fixed tool prefix is too long for the tokenizer buffer.";
        return -1;
    }

    const int total = encoded + 1;
    sink.status("priming", "Priming the tool schema", 0, total);
    for (int i = 0; i < total; ++i) {
        nd_model_step_hidden(&g_model, ids[static_cast<size_t>(i)]);
        if ((i + 1) % 4 == 0 || i + 1 == total) {
            sink.status("priming", "Priming the tool schema", i + 1, total);
        }
    }

    if (nd_model_snapshot(&g_model) != 0) {
        error = "The model could not snapshot its primed prefix.";
        return -1;
    }
    sink.status("ready", "Ready", total, total);
    return 0;
}

static int configure_tools(JNIEnv *env, jbyteArray tools_json, CallbackSink &sink,
                           std::string &error) {
    const std::string raw_tools = take_bytes(env, tools_json);
    std::vector<char> compact(raw_tools.size() + 1);
    const int compact_len = nd_json_compact(raw_tools.data(), raw_tools.size(), compact.data(),
                                            compact.size());
    if (compact_len < 0) {
        error = "The skill schemas could not be compacted.";
        return -1;
    }

    nd_grammar compiled{};
    const char *grammar_error = nullptr;
    if (nd_grammar_compile(&compiled, compact.data(), static_cast<size_t>(compact_len),
                           &grammar_error) != 0) {
        const std::string message = grammar_error ? grammar_error : "unknown grammar error";
        error = "Skill grammar failed: " + message;
        return -1;
    }

    g_tools_json.assign(compact.data(), static_cast<size_t>(compact_len));
    g_grammar = compiled;
    if (prime_prefix(sink, error) != 0)
        return -1;
    return 0;
}

static int schema_prefix_token_count(JNIEnv *env, jbyteArray tools_json) {
    const std::string raw_tools = take_bytes(env, tools_json);
    std::vector<char> compact(raw_tools.size() + 1);
    const int compact_len = nd_json_compact(raw_tools.data(), raw_tools.size(), compact.data(),
                                            compact.size());
    if (compact_len < 0)
        return -1;
    const std::string prefix =
        "<|im_start|>user\n<tools>" +
        std::string(compact.data(), static_cast<size_t>(compact_len)) +
        "</tools>\nlocation: here";
    std::vector<uint32_t> ids(2048);
    const int encoded = nd_tok_encode(&g_model.tok, prefix.data(), prefix.size(), ids.data(),
                                      static_cast<uint32_t>(ids.size()));
    return encoded < 0 ? -1 : encoded + 1; /* include the explicit BOS used by priming */
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_amanrai_agave_nativebridge_NativeBridge_initialize(
    JNIEnv *env, jobject, jbyteArray model_bytes, jbyteArray tools_json, jobject callbacks) {
    std::lock_guard<std::mutex> lock(g_inference_mu);
    CallbackSink sink(env, callbacks);
    if (!sink.valid()) return error_string(env, "Native callback methods could not be resolved.");
    if (!model_bytes) return error_string(env, "The bundled model asset is missing.");

    g_ready = false;
    if (g_model_open) {
        nd_model_close(&g_model);
        g_model_open = false;
    }
    std::free(g_blob);
    g_blob = nullptr;

    const jsize model_size = env->GetArrayLength(model_bytes);
    if (model_size <= 0) return error_string(env, "The bundled model is empty.");

    sink.status("loading", "Copying model weights into native RAM", 0, model_size);
    g_blob = static_cast<uint8_t *>(std::malloc(static_cast<size_t>(model_size)));
    if (!g_blob) return error_string(env, "There is not enough native RAM for the model weights.");
    env->GetByteArrayRegion(model_bytes, 0, model_size,
                            reinterpret_cast<jbyte *>(g_blob));
    sink.status("loading", "Opening Needle 2", model_size, model_size);

    const int model_rc = nd_model_open(&g_model, g_blob, static_cast<size_t>(model_size));
    if (model_rc != 0) {
        std::free(g_blob);
        g_blob = nullptr;
        return error_string(env, "Needle 2 model open failed (code " +
                                 std::to_string(model_rc) + ").");
    }
    g_model_open = true;
    nd_parallel_rows = parallel_rows;

    std::string prime_error;
    if (configure_tools(env, tools_json, sink, prime_error) != 0) {
        nd_model_close(&g_model);
        g_model_open = false;
        std::free(g_blob);
        g_blob = nullptr;
        return error_string(env, prime_error);
    }

    g_ready = true;
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_amanrai_agave_nativebridge_NativeBridge_configureTools(
    JNIEnv *env, jobject, jbyteArray tools_json, jobject callbacks) {
    std::lock_guard<std::mutex> lock(g_inference_mu);
    CallbackSink sink(env, callbacks);
    if (!sink.valid()) return error_string(env, "Native callback methods could not be resolved.");
    if (!g_model_open) return error_string(env, "Needle 2 is not loaded yet.");

    g_ready = false;
    std::string error;
    if (configure_tools(env, tools_json, sink, error) != 0)
        return error_string(env, error);
    g_ready = true;
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_amanrai_agave_nativebridge_NativeBridge_schemaPrefixTokenCount(
    JNIEnv *env, jobject, jbyteArray tools_json) {
    std::lock_guard<std::mutex> lock(g_inference_mu);
    if (!g_model_open) return -1;
    return schema_prefix_token_count(env, tools_json);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_amanrai_agave_nativebridge_NativeBridge_generate(
    JNIEnv *env, jobject, jbyteArray query_value, jboolean show_thinking,
    jboolean require_tool_call, jint max_new_tokens, jobject callbacks) {
    std::lock_guard<std::mutex> lock(g_inference_mu);
    CallbackSink sink(env, callbacks);
    if (!sink.valid()) return error_string(env, "Native callback methods could not be resolved.");
    if (!g_ready) return error_string(env, "Needle 2 is not ready yet.");

    const std::string query = take_bytes(env, query_value);
    if (query.empty()) return error_string(env, "Enter a request first.");

    const auto request_start = Clock::now();
    nd_model_rewind(&g_model);

    nd_grammar active_grammar = g_grammar;
    active_grammar.require_call = require_tool_call ? 1 : 0;
    nd_sampler sampler;
    nd_sampler_init(&sampler, &g_model.tok, &active_grammar);

    std::string normalized_query = query;
    std::transform(normalized_query.begin(), normalized_query.end(), normalized_query.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    const bool needs_location_context =
        normalized_query.find("time") != std::string::npos ||
        normalized_query.find("weather") != std::string::npos ||
        normalized_query.find("temperature") != std::string::npos ||
        normalized_query.find("forecast") != std::string::npos;
    const std::string location_context = needs_location_context
        ? "\ndefault location: here"
        : "";
    const std::string suffix =
        "\n" + query + location_context + "<|im_end|>\n<|im_start|>assistant\n";
    std::vector<uint32_t> ids(1024);
    const int prompt_tokens = nd_tok_encode_ex(
        &g_model.tok, suffix.data(), suffix.size(), ids.data(),
        static_cast<uint32_t>(ids.size()), 0);
    if (prompt_tokens < 0) return error_string(env, "The request is too long.");

    const auto prefill_start = Clock::now();
    const float *hidden = nullptr;
    for (int i = 0; i < prompt_tokens; ++i) {
        hidden = nd_model_step_hidden(&g_model, ids[static_cast<size_t>(i)]);
        sink.status("reading", "Reading request", i + 1, prompt_tokens);
    }
    const double prefill_ms = elapsed_ms(prefill_start);
    const double prefill_tps = prefill_ms > 0.0
        ? static_cast<double>(prompt_tokens) / (prefill_ms / 1000.0)
        : 0.0;
    sink.prefill(prompt_tokens, prefill_ms, prefill_tps);

    std::string output;
    int produced = 0;
    double ttft_ms = -1.0;
    Clock::time_point last_token_time = request_start;

    const auto decode_start = Clock::now();

    auto emit_token = [&](uint32_t id) {
        char piece[512];
        const int size = nd_tok_decode_ex(&g_model.tok, &id, 1, piece, sizeof(piece), 0);
        if (size < 0) return;
        const auto now = Clock::now();
        const double elapsed = elapsed_ms(request_start, now);
        const double delta = produced == 0 ? elapsed : elapsed_ms(last_token_time, now);
        if (produced == 0) ttft_ms = elapsed;
        last_token_time = now;
        output.append(piece, static_cast<size_t>(size));
        sink.token(piece, static_cast<size_t>(size), static_cast<int>(id), produced,
                   elapsed, delta);
        ++produced;
    };

    if (!show_thinking) {
        uint32_t forced[8];
        uint32_t count = 0;
        forced[count++] = ND_THINK_START_ID;
        forced[count++] = ND_THINK_END_ID;
        uint32_t newline_ids[4];
        const int newline_count = nd_tok_encode_ex(
            &g_model.tok, "\n", 1, newline_ids, 4, 0);
        for (int i = 0; i < newline_count; ++i) forced[count++] = newline_ids[i];
        forced[count++] = ND_TOOL_CALL_START_ID;

        for (uint32_t i = 0; i < count; ++i) {
            nd_sample_accept(&sampler, forced[i]);
            emit_token(forced[i]);
            hidden = nd_model_step_hidden(&g_model, forced[i]);
        }
    }

    if (!hidden) return error_string(env, "The request produced no prompt state.");

    sink.status("generating", "Generating", 0, std::max(1, max_new_tokens));
    for (int i = 0; i < std::max(1, max_new_tokens); ++i) {
        const uint32_t id = nd_sample_hidden(&g_model, &sampler, hidden);
        if (id == static_cast<uint32_t>(-1)) {
            sink.error("The grammar rejected every available token.");
            break;
        }
        if (id == ND_EOS_ID || id == ND_IM_END_ID) break;

        nd_sample_accept(&sampler, id);
        emit_token(id);
        sink.status("generating", "Generating", produced,
                    std::max(1, max_new_tokens));
        hidden = nd_model_step_hidden(&g_model, id);
    }

    const double decode_ms = elapsed_ms(decode_start);
    const double decode_tps = decode_ms > 0.0
        ? static_cast<double>(produced) / (decode_ms / 1000.0)
        : 0.0;
    const double confidence = static_cast<double>(nd_model_confidence(&g_model));
    sink.complete(produced, decode_ms, decode_tps, ttft_ms, confidence, output);
    sink.status("ready", "Ready", 1, 1);
    return nullptr;
}
