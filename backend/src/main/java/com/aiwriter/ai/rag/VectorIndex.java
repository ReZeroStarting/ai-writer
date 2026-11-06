package com.aiwriter.ai.rag;

import com.aiwriter.model.FrameworkElement;

import java.util.List;

/**
 * 向量索引接口（RAG 的检索层）。
 *
 * <p>默认没有实现（走 {@link ElementRanker} 的内存余弦）；把
 * {@code ai.rag.vector-store} 配成 {@code qdrant} 时，Spring 会注入
 * {@link QdrantVectorIndex}，检索改走向量库。
 *
 * <p>抽成接口是为了两件事：<b>可替换</b>（Qdrant / pgvector / Milvus 换实现即可）和
 * <b>可测试</b>（单元测试用假实现，不依赖容器）。
 */
public interface VectorIndex {

    /** 检索与 query 最相关的元素 id，按相似度降序。 */
    List<String> search(Long frameworkId, String query, int limit);

    /** 写入/更新一条元素向量。 */
    void upsert(FrameworkElement element);

    /** 删除一条元素向量（元素被弃用时调用）。 */
    void delete(String elementId);

    /** 全量重建某个框架的索引，返回写入条数。 */
    int reindex(Long frameworkId, List<FrameworkElement> elements);
}
