package com.axelor.auth;

public class EduFlowAuthResolverRegistry {

    private static EduFlowAuthResolver resolver;

    private EduFlowAuthResolverRegistry() {
    }

    public static void register(EduFlowAuthResolver eduFlowAuthResolver) {
        if (resolver != null) {
            throw new IllegalStateException("EduFlowAuthResolver is already registered.");
        }
        resolver = eduFlowAuthResolver;
    }

    public static EduFlowAuthResolver get() {
        return resolver;
    }
}

