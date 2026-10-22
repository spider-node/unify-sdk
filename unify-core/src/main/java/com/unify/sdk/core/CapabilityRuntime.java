package com.unify.sdk.core;

import com.unify.sdk.api.Capability;
import com.unify.sdk.api.ErrorCode;
import com.unify.sdk.api.UnifiedException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一项能力在运行期的完整信息：配置及按优先级排好序的数据源绑定。
 */
final class CapabilityRuntime<Q, R> {

    private final Capability<Q, R> capability;
    private final CapabilityConfig<Q, R> config;
    private final List<SourceBinding<Q, R>> bindings;

    CapabilityRuntime(Capability<Q, R> capability, CapabilityConfig<Q, R> config, List<SourceBinding<Q, R>> bindings) {
        this.capability = capability;
        this.config = config;
        this.bindings = List.copyOf(bindings);
    }

    Capability<Q, R> capability() {
        return capability;
    }

    CapabilityConfig<Q, R> config() {
        return config;
    }

    /**
     * 选出本次请求要调用的数据源：先按适配器 {@code supports} 过滤，再交给路由器决定范围和顺序，
     * 最后把调用方指定的优先数据源（若可用）提到首位。
     */
    List<SourceBinding<Q, R>> select(Q request, InvocationContext context) {
        Map<String, SourceBinding<Q, R>> supported = new LinkedHashMap<>();
        for (SourceBinding<Q, R> binding : bindings) {
            boolean ok;
            try {
                ok = binding.adapter().supports(request, context);
            } catch (RuntimeException e) {
                throw new UnifiedException(ErrorCode.INTERNAL_ERROR,
                        "Adapter for source '" + binding.sourceId() + "' failed in supports(): " + e.getMessage(),
                        capability.name(), List.of(), e);
            }
            if (ok) {
                supported.put(binding.sourceId(), binding);
            }
        }
        if (supported.isEmpty()) {
            return List.of();
        }

        List<String> routed;
        try {
            routed = config.router().select(request, List.copyOf(supported.keySet()), context);
        } catch (RuntimeException e) {
            throw new UnifiedException(ErrorCode.INTERNAL_ERROR, "Router failed: " + e.getMessage(),
                    capability.name(), List.of(), e);
        }

        List<SourceBinding<Q, R>> selected = new ArrayList<>();
        if (routed != null) {
            for (String sourceId : routed) {
                SourceBinding<Q, R> binding = supported.remove(sourceId);
                if (binding != null) {
                    selected.add(binding);
                }
            }
        }

        Optional<String> preferred = context.options().preferredSource();
        if (preferred.isPresent()) {
            for (int i = 1; i < selected.size(); i++) {
                if (selected.get(i).sourceId().equals(preferred.get())) {
                    selected.add(0, selected.remove(i));
                    break;
                }
            }
        }
        return selected;
    }
}
