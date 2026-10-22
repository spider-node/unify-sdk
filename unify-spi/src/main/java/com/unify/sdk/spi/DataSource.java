package com.unify.sdk.spi;

/**
 * 内部数据源，由数据提供者实现。
 * <p>
 * 一个数据源对应一个内部接口（或提供者自行聚合的一组接口），使用该接口自己的请求结构 {@code SQ}
 * 和响应结构 {@code SR}。数据如何获取完全由提供者决定，SDK 不感知。
 *
 * <h2>实现约定</h2>
 * <ul>
 *     <li>必须线程安全，SDK 会并发调用同一实例。</li>
 *     <li>只返回内部结构，不要转换成统一结构，转换由适配器负责。</li>
 *     <li>查不到数据时返回 {@code null} 或该接口约定的"空"结构，由适配器决定如何解释。</li>
 *     <li>调用失败时抛出 {@link DataSourceException}，并尽量标明是否可重试；其他运行时异常也会被 SDK 捕获。</li>
 *     <li>建议参考 {@link SourceContext#deadline()} 设置底层调用的超时时间。</li>
 * </ul>
 *
 * @param <SQ> 内部接口的请求结构
 * @param <SR> 内部接口的响应结构
 */
public interface DataSource<SQ, SR> {

    /** 数据源唯一标识，例如 {@code "legacy-crm"}。适配器通过该 ID 与数据源绑定。 */
    String sourceId();

    /**
     * 调用内部接口获取数据。
     *
     * @param request 已由适配器转换好的内部请求
     * @param context 调用上下文（追踪 ID、截止时间、透传属性）
     */
    SR fetch(SQ request, SourceContext context) throws DataSourceException;
}
