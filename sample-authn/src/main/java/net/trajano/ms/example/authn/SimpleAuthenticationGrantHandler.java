package net.trajano.ms.example.authn;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;

import net.trajano.ms.common.beans.JwksProvider;
import net.trajano.ms.common.oauth.GrantHandler;
import net.trajano.ms.common.oauth.GrantTypes;
import net.trajano.ms.common.oauth.OAuthTokenResponse;

@Component
@Configuration
public class SimpleAuthenticationGrantHandler implements
    GrantHandler {

    private static final String BASIC = "Basic";

    @Value("${authorizationEndpoint}")
    private URI authorizationEndpoint;

    @Value("${issuer}")
    private URI issuer;

    @Autowired
    private JwksProvider jwksProvider;

    /**
     * The password required to be passed into the authorization
     */
    @Value("${passwordRequired}")
    private String passwordRequired;

    @Override
    public String getGrantTypeHandled() {

        return GrantTypes.CLIENT_CREDENTIALS;
    }

    @Override
    public OAuthTokenResponse handler(final Client jaxRsClient,
        final HttpHeaders httpHeaders,
        final MultivaluedMap<String, String> form) {

        final String authorization = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BASIC + " ")) {
            throw new NotAuthorizedException("Missing Authorization", BASIC);
        }
        final String[] decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.US_ASCII).split(":");
        try {
            final String username = URLDecoder.decode(decoded[0], "UTF-8");
            final String password = URLDecoder.decode(decoded[1], "UTF-8");

            if (!password.equals(passwordRequired)) {
                throw new NotAuthorizedException("Invalid username/password", BASIC);
            }
            final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(authorizationEndpoint.toASCIIString())
                .subject(username)
                .issuer(issuer.toASCIIString())
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .build();

            final String jwt = jwksProvider.sign(claims).serialize();

            final Form authorizationForm = new Form();
            authorizationForm.param("grant_type", GrantTypes.JWT_ASSERTION);

            authorizationForm.param("client_id", form.getFirst("client_id"));
            authorizationForm.param("client_secret", form.getFirst("client_secret"));
            authorizationForm.param("assertion", jwt);
            System.out.println(authorizationForm.asMap());
            return jaxRsClient.target(authorizationEndpoint).request().accept(MediaType.APPLICATION_JSON).post(Entity.form(authorizationForm), OAuthTokenResponse.class);
        } catch (final UnsupportedEncodingException
            | JOSEException e) {
            throw new InternalServerErrorException(e);
        }
    }

}
