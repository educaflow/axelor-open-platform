package com.axelor.auth;

public class EducaFlowAuthResolverRegistry {

    private static EducaFlowAuthResolver resolver;

    private EducaFlowAuthResolverRegistry() {
    }

    public static void register(EducaFlowAuthResolver educaFlowAuthResolver) {
        if (resolver != null) {
            throw new IllegalStateException("EducaFlowAuthResolver is already registered.");
        }
        resolver = educaFlowAuthResolver;
    }

    public static EducaFlowAuthResolver get() {
        return resolver;
    }
}

