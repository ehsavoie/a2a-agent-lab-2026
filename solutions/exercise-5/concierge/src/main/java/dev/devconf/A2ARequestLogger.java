package dev.devconf;

import java.util.logging.Logger;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class A2ARequestLogger {

    private static final Logger LOG = Logger.getLogger(A2ARequestLogger.class.getName());

    void installFilter(@Observes Router router) {
        router.route().order(-1).handler(BodyHandler.create());
        router.route().order(0).handler(ctx -> {
            String path = ctx.normalizedPath();
            String method = ctx.request().method().name();
            String body = ctx.body() != null ? ctx.body().asString() : "<no body>";
            LOG.info(">>> " + method + " " + path);
            if (body != null && body.length() > 2000) {
                LOG.info(">>> Body (truncated): " + body.substring(0, 2000));
            } else {
                LOG.info(">>> Body: " + body);
            }
            ctx.next();
        });
    }
}
