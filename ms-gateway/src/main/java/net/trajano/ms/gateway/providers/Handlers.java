package net.trajano.ms.gateway.providers;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import net.trajano.ms.gateway.internal.Conversions;

@Configuration
@Component
public class Handlers {

    @Value("${authorizationEndpoint}")
    private URI authorizationEndpoint;

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private WebClient webClient;

    /**
     * This handler goes through the authorization to prepopulate the
     * X-JWT-Assertion header.
     *
     * @return
     */
    public Handler<RoutingContext> authorizationHandler() {

        return context -> {

        };
    }

    /**
     * This handler deals with refreshing the OAuth token.
     *
     * @return
     */
    public Handler<RoutingContext> refreshHandler() {

        return context -> {
            final HttpServerRequest contextRequest = context.request();
            final HttpServerResponse contextResponse = context.response();

            contextRequest
                .setExpectMultipart(true)
                .handler(buf -> {
                })
                .endHandler(v -> {
                    final String grantType = contextRequest.getFormAttribute("grant_type");
                    if (grantType == null) {
                        contextResponse.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .setStatusCode(400)
                            .setStatusMessage("Bad Request")
                            .end(new JsonObject()
                                .put("error", "invalid_grant")
                                .put("error_description", "Missing grant type")
                                .toBuffer());
                        return;
                    }

                    if (!"refresh_token".equals(grantType)) {
                        contextResponse.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .setStatusCode(400)
                            .setStatusMessage("Bad Request")
                            .end(new JsonObject()
                                .put("error", "unsupported_grant_type")
                                .put("error_description", "Unsupported grant type")
                                .toBuffer());
                        return;
                    }
                    final String refreshToken = contextRequest.getFormAttribute("refresh_token");
                    if (refreshToken == null || refreshToken.length() > 100) {
                        contextResponse.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .setStatusCode(400)
                            .setStatusMessage("Bad Request")
                            .end(new JsonObject()
                                .put("error", "invalid_request")
                                .put("error_description", "Missing grant")
                                .toBuffer());
                        return;
                    }

                    final HttpClientRequest authorizationRequest = httpClient.post(Conversions.toRequestOptions(authorizationEndpoint), authorizationResponse -> {
                        // Trust the authorization endpoint
                        authorizationResponse.bodyHandler(buffer -> {
                            contextResponse.setStatusCode(authorizationResponse.statusCode());
                            contextResponse.setStatusMessage(authorizationResponse.statusMessage());
                            authorizationResponse.headers().forEach(h -> contextResponse.putHeader(h.getKey(), h.getValue()));
                            contextResponse.end(buffer);
                        });
                    });
                    authorizationRequest
                        .putHeader(HttpHeaders.AUTHORIZATION, contextRequest.getHeader(HttpHeaders.AUTHORIZATION))
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .putHeader(HttpHeaders.ACCEPT, "application/json")
                        .end("grant_type=refresh_token&refresh_token=" + refreshToken);
                });

        };
    }
}
