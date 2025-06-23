package com.axelor.web.servlet;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class EduFlowIncludeFilter implements Filter {

    private static final String BASE_PATH = "/includes/";
    private static final List<String> ALLOWED_FILES = List.of(
            BASE_PATH + "head.start.include.html",
            BASE_PATH + "head.end.include.html",
            BASE_PATH + "body.start.include.html",
            BASE_PATH + "body.end.include.html"
    );
    private static final String MIME_TYPE = "text/html";

    private ServletContext servletContext;

    @Override
    public void init(FilterConfig filterConfig) {
        this.servletContext = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String relativePath = uri.substring(contextPath.length());

        if (!relativePath.endsWith(".include.html") || !ALLOWED_FILES.contains(relativePath)) {
            chain.doFilter(req, res);
            return;
        }

        try (InputStream is = servletContext.getResourceAsStream(relativePath)) {
            if (is == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Include not found: " + relativePath);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MIME_TYPE);

            try (OutputStream os = response.getOutputStream()) {
                is.transferTo(os);
            }

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to serve include");
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    @SuppressWarnings("unused")
    private boolean isValidReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        return referer != null && referer.startsWith(request.getScheme() + "://" + request.getServerName());
    }
}