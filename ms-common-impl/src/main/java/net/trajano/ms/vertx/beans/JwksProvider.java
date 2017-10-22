package net.trajano.ms.vertx.beans;

import net.trajano.ms.core.CryptoOps;
import org.jose4j.jwa.Algorithm;
import org.jose4j.jwk.*;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jws.JsonWebSignatureAlgorithm;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.ws.rs.InternalServerErrorException;
import java.security.KeyPairGenerator;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static net.trajano.ms.core.Qualifiers.JWKS_CACHE;

@Component
public class JwksProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JwksProvider.class);

    /**
     * Maximum number of keys to keep in the cache.
     */
    public static final int MAX_NUMBER_OF_KEYS = 5;

    public static final int MIN_NUMBER_OF_KEYS = 2;

    @Autowired(required = false)
    private CacheManager cm;

    /**
     * This is a cache of JWKs. If this is not provided a default one is used.
     */
    private Cache jwksCache;

    private KeyPairGenerator keyPairGenerator;

    /**
     * Random source. Use ThreadLocalRandom since these will not be used for
     * secure keys.
     */
    private Random random = ThreadLocalRandom.current();

    @Autowired
    private CryptoOps cryptoOps;

    /**
     * Builds JWKS if necessary after 60 seconds, but only builds
     * {@value #MIN_NUMBER_OF_KEYS} at a time.
     */
    @Scheduled(fixedDelay = 60000)
    public void buildJwks() {

        int nCreated = 0;
        for (int i = 0; i < MAX_NUMBER_OF_KEYS; ++i) {
            final String cacheKey = String.valueOf(i);
            final JsonWebKey jwk = jwksCache.get(cacheKey, JsonWebKey.class);
            if (jwk == null && nCreated < MIN_NUMBER_OF_KEYS) {
                RsaJsonWebKey newJwk = buildNewRsaKey();
                jwksCache.putIfAbsent(cacheKey, newJwk);
                ++nCreated;
                LOG.debug("Created new JWK kid={}", newJwk.getKeyId());
            }
        }

    }

    private RsaJsonWebKey buildNewRsaKey() {

        try {
            RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
            rsaJsonWebKey.setKeyId(cryptoOps.newToken());
            rsaJsonWebKey.setAlgorithm(AlgorithmIdentifiers.RSA_USING_SHA512);
            rsaJsonWebKey.setUse("sig");
            return rsaJsonWebKey;
        } catch (JoseException e) {
            throw new InternalServerErrorException(e);
        }
    }

    /**
     * Gets a single signing key.
     *
     * @return an RSA web key that supports signing.
     */
    public RsaJsonWebKey getASigningKey() {

        final JsonWebKeySet keySet = getKeySet();
        if (LOG.isDebugEnabled()) {
            LOG.debug(keySet.toJson());
        }
        return (RsaJsonWebKey) keySet.findJsonWebKey(null, "RSA", "sig", null);
    }

    /**
     * Builds the keys from the cache.
     *
     * @return
     */
    public JsonWebKeySet getKeySet() {

        final JsonWebKeySet set = new JsonWebKeySet();
        for (int i = 0; i < MAX_NUMBER_OF_KEYS; ++i) {
            final String cacheKey = String.valueOf(i);
            final JsonWebKey jwk = jwksCache.get(cacheKey, JsonWebKey.class);
            if (jwk != null) {
                set.addJsonWebKey(jwk);
            }
        }
        return set;
    }

    @PostConstruct
    public void init() {

        if (cm == null) {
            LOG.warn("A org.springframework.cache.CacheManager was not provided an in-memory cache will be used");
            cm = new ConcurrentMapCacheManager(JWKS_CACHE);
        }

        jwksCache = cm.getCache(JWKS_CACHE);
        if (jwksCache == null) {
            LOG.warn("A no cache named {} was not provided by the cache manager an in-memory cache will be used", JWKS_CACHE);
            jwksCache = new ConcurrentMapCacheManager(JWKS_CACHE).getCache(JWKS_CACHE);
        }

        LOG.debug("cache={}", this.jwksCache);
        buildJwks();
    }

    @Autowired(required = false)
    @Qualifier(JWKS_CACHE)
    public void setJwksCache(final Cache jwksCache) {

        this.jwksCache = jwksCache;
    }

    public JwtConsumer buildConsumer() {

        return buildConsumer(null, null);
    }

    public JwtConsumer buildConsumer(HttpsJwks jwks,
        String audience) {

        final JwtConsumerBuilder builder = new JwtConsumerBuilder()
            .setRequireJwtId();
        if (jwks != null) {
            builder
                .setVerificationKeyResolver(new HttpsJwksVerificationKeyResolver(jwks));
        }
        if (audience != null) {
            builder
                .setExpectedAudience(audience);
        } else {
            builder.setSkipDefaultAudienceValidation();
        }
        return builder.build();
    }

}
