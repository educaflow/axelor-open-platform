package com.axelor.auth;

public class EduFlowAuthResolverRegistry {

    private static EducaFlowAuthResolver resolver;

    private EduFlowAuthResolverRegistry() {
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

