package com.aiwriter.ai.rag;

/**
 * 文本向量化接口（RAG 的 E 层）。
 *
 * <p>抽象出来是为了两件事：
 * <ul>
 *   <li><b>可替换</b>：本地哈希向量做默认实现（零依赖、可离线跑测试），
 *       线上换成真 embedding 服务时只换实现；</li>
 *   <li><b>可测试</b>：向量检索的正确性不依赖外部 API，CI 里也能跑。</li>
 * </ul>
 */
public interface EmbeddingModel {

    /** 把文本转成向量（实现内部应做归一化，方便用点积当余弦）。 */
    float[] embed(String text);

    /** 实现名，用于日志与指标。 */
    String name();
}
