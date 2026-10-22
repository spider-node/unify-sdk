package com.unify.sdk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;

/**
 * 常用合并器。
 */
public final class Mergers {

    private Mergers() {
    }

    /** 取第一个非空结果（按路由顺序）。 */
    public static <R> ResultMerger<R> firstNonNull() {
        return (results, context) -> {
            for (SourcedResult<R> result : results) {
                if (result.data() != null) {
                    return result.data();
                }
            }
            return null;
        };
    }

    /**
     * 按路由顺序两两归并非空结果：{@code combiner.apply(已合并结果, 下一个结果)}。
     * 适合"字段级取值优先级"的场景：在 combiner 中对每个字段选择非空值即可。
     */
    public static <R> ResultMerger<R> reduce(BinaryOperator<R> combiner) {
        Objects.requireNonNull(combiner, "combiner");
        return (results, context) -> {
            R merged = null;
            for (SourcedResult<R> result : results) {
                R data = result.data();
                if (data == null) {
                    continue;
                }
                merged = merged == null ? data : combiner.apply(merged, data);
            }
            return merged;
        };
    }

    /** 统一结果为列表时，按路由顺序拼接所有列表。 */
    public static <E> ResultMerger<List<E>> concatLists() {
        return (results, context) -> {
            List<E> merged = new ArrayList<>();
            for (SourcedResult<List<E>> result : results) {
                if (result.data() != null) {
                    merged.addAll(result.data());
                }
            }
            return merged;
        };
    }
}
