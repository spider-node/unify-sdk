package com.unify.sdk.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 路由器：在"支持本次请求"的数据源中，决定实际调用哪些、以什么顺序调用。
 * <p>
 * 传入的候选列表已按适配器 {@link SourceAdapter#order()} 排序，且只包含
 * {@link SourceAdapter#supports} 返回 {@code true} 的数据源。
 * 返回的列表中不在候选范围内的 ID 会被忽略。
 */
@FunctionalInterface
public interface Router<Q> {

    List<String> select(Q request, List<String> candidates, InvocationContext context);

    /** 默认路由：使用全部候选数据源，保持原有顺序。 */
    static <Q> Router<Q> all() {
        return (request, candidates, context) -> candidates;
    }

    /** 按给定顺序排列候选数据源，未列出的数据源排在最后。 */
    static <Q> Router<Q> prefer(String... sourceIds) {
        List<String> preferred = List.of(sourceIds);
        return (request, candidates, context) -> {
            List<String> result = new ArrayList<>(candidates.size());
            for (String id : preferred) {
                if (candidates.contains(id)) {
                    result.add(id);
                }
            }
            for (String id : candidates) {
                if (!result.contains(id)) {
                    result.add(id);
                }
            }
            return result;
        };
    }

    /** 排除指定数据源，例如临时下线某个内部接口。 */
    static <Q> Router<Q> exclude(String... sourceIds) {
        Set<String> excluded = Set.copyOf(Arrays.asList(sourceIds));
        return (request, candidates, context) -> candidates.stream()
                .filter(id -> !excluded.contains(id))
                .toList();
    }

    /** 先执行当前路由，再在结果上执行下一个路由。 */
    default Router<Q> andThen(Router<Q> next) {
        Objects.requireNonNull(next, "next");
        return (request, candidates, context) -> next.select(request, select(request, candidates, context), context);
    }
}
