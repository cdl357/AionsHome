/*
 * 本地模型 JNI（llama.cpp 绑定，AionsHome 本地模型队列项）
 * - 句柄 = llama_slot*（model + ctx + vocab 三件套）
 * - 文本一律走 byte[]（Java 侧 UTF-8 编解码），绕开 JNI Modified-UTF8 对中文的坑
 * - 生成是阻塞循环：每个 token 回调一次 sink.onToken，sink 返回 false 即用户停止
 * - 每次生成前清 KV（prompt 由 Kotlin 侧拼完整上下文）
 */
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "llama.h"

typedef struct {
    struct llama_model * model;
    struct llama_context * ctx;
    const struct llama_vocab * vocab;
} llama_slot;

static void throwIse(JNIEnv * env, const char * msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (cls != NULL) (*env)->ThrowNew(env, cls, msg);
}

JNIEXPORT void JNICALL
Java_com_aion_chat_compose_data_LocalLlamaEngine_nativeBackendInit(JNIEnv * env, jclass cls) {
    llama_backend_init();
}

JNIEXPORT jlong JNICALL
Java_com_aion_chat_compose_data_LocalLlamaEngine_nativeLoadModel(
        JNIEnv * env, jclass cls, jbyteArray pathBytes, jint nCtx, jint nThreads) {
    jsize len = (*env)->GetArrayLength(env, pathBytes);
    char * path = (char *) malloc(len + 1);
    if (path == NULL) { throwIse(env, "oom"); return 0; }
    (*env)->GetByteArrayRegion(env, pathBytes, 0, len, (jbyte *) path);
    path[len] = '\0';

    struct llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;   // 纯 CPU；移动端 ggml-cpu 自带 i8mm 加速
    struct llama_model * model = llama_model_load_from_file(path, mp);
    free(path);
    if (model == NULL) { throwIse(env, "模型加载失败（GGUF 格式或内存不足）"); return 0; }

    struct llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) nCtx;
    cp.n_batch = (uint32_t) nCtx;
    cp.n_seq_max = 1;
    cp.n_threads = nThreads > 0 ? nThreads : 4;
    cp.n_threads_batch = cp.n_threads;
    struct llama_context * ctx = llama_init_from_model(model, cp);
    if (ctx == NULL) {
        llama_model_free(model);
        throwIse(env, "上下文创建失败（内存不足）");
        return 0;
    }
    llama_slot * slot = (llama_slot *) malloc(sizeof(llama_slot));
    if (slot == NULL) {
        llama_free(ctx);
        llama_model_free(model);
        throwIse(env, "oom");
        return 0;
    }
    slot->model = model;
    slot->ctx = ctx;
    slot->vocab = llama_model_get_vocab(model);
    return (jlong) (intptr_t) slot;
}

JNIEXPORT void JNICALL
Java_com_aion_chat_compose_data_LocalLlamaEngine_nativeFree(JNIEnv * env, jclass cls, jlong handle) {
    llama_slot * slot = (llama_slot *) (intptr_t) handle;
    if (slot == NULL) return;
    if (slot->ctx != NULL) llama_free(slot->ctx);
    if (slot->model != NULL) llama_model_free(slot->model);
    free(slot);
}

/**
 * 阻塞生成。sink.onToken(byte[]) 返回 false 时提前停止。
 * 返回：生成的 token 数；-1 参数/内部错误。
 */
JNIEXPORT jint JNICALL
Java_com_aion_chat_compose_data_LocalLlamaEngine_nativeGenerate(
        JNIEnv * env, jclass cls, jlong handle,
        jbyteArray promptBytes, jint maxTokens, jfloat temp, jobject sink) {
    llama_slot * slot = (llama_slot *) (intptr_t) handle;
    if (slot == NULL || slot->ctx == NULL || slot->vocab == NULL) return -1;

    jsize plen = (*env)->GetArrayLength(env, promptBytes);
    char * prompt = (char *) malloc(plen + 1);
    if (prompt == NULL) return -1;
    (*env)->GetByteArrayRegion(env, promptBytes, 0, plen, (jbyte *) prompt);
    prompt[plen] = '\0';

    // tokenize（不自动加 BOS：模板由 Kotlin 侧拼，Qwen 类模板自带 <|im_start|>）
    int n_prompt = llama_tokenize(slot->vocab, prompt, plen, NULL, 0, false, true);
    llama_token * toks = NULL;
    if (n_prompt <= 0) { free(prompt); return -1; }
    toks = (llama_token *) malloc(n_prompt * sizeof(llama_token));
    if (toks == NULL) { free(prompt); return -1; }
    n_prompt = llama_tokenize(slot->vocab, prompt, plen, toks, n_prompt, false, true);
    free(prompt);
    if (n_prompt <= 0) { free(toks); return -1; }

    llama_memory_clear(llama_get_memory(slot->ctx), true);

    jclass sinkCls = (*env)->GetObjectClass(env, sink);
    jmethodID onToken = (*env)->GetMethodID(env, sinkCls, "onToken", "([B)Z");
    if (onToken == NULL) { free(toks); return -1; }

    struct llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    struct llama_sampler * smpl = llama_sampler_chain_init(sp);
    if (temp <= 0.01f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(12345));
    }

    jint produced = 0;
    llama_token cur = 0;
    char piece[256];
    jbyteArray out = NULL;
    int guard = 0;
    for (int i = 0; i < n_prompt; i++) {
        if (++guard > 100000) break;
        cur = toks[i];
        if (llama_decode(slot->ctx, llama_batch_get_one(&cur, 1))) { free(toks); llama_sampler_free(smpl); return produced > 0 ? produced : -1; }
    }
    free(toks);

    while (produced < maxTokens && ++guard <= 100000) {
        llama_token next = llama_sampler_sample(smpl, slot->ctx, -1);
        if (llama_vocab_is_eog(slot->vocab, next)) break;
        int n = llama_token_to_piece(slot->vocab, next, piece, sizeof(piece), 0, true);
        if (n > 0) {
            out = (*env)->NewByteArray(env, n);
            if (out == NULL) break;
            (*env)->SetByteArrayRegion(env, out, 0, n, (jbyte *) piece);
            jboolean keep = (*env)->CallBooleanMethod(env, sink, onToken, out);
            (*env)->DeleteLocalRef(env, out);
            out = NULL;
            if (!keep) break;
        }
        llama_sampler_accept(smpl, next);
        if (llama_decode(slot->ctx, llama_batch_get_one(&next, 1))) break;
        produced++;
    }
    llama_sampler_free(smpl);
    return produced;
}

JNIEXPORT void JNICALL
Java_com_aion_chat_compose_data_LocalLlamaEngine_nativeBackendFree(JNIEnv * env, jclass cls) {
    llama_backend_free();
}
