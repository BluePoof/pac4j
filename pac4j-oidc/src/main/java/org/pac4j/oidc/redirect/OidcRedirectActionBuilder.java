package org.pac4j.oidc.redirect;

import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.client.RedirectAction;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.redirect.RedirectActionBuilder;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.InitializableWebObject;
import org.pac4j.oidc.config.OidcConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Redirect to the OpenID Connect provider.
 *
 * @author Jerome Leleu
 * @since 1.9.2
 */
public class OidcRedirectActionBuilder extends InitializableWebObject implements RedirectActionBuilder {

    private static final Logger logger = LoggerFactory.getLogger(OidcRedirectActionBuilder.class);

    private OidcConfiguration configuration;

    private Map<String, String> authParams;

    public OidcRedirectActionBuilder() {}

    public OidcRedirectActionBuilder(final OidcConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void internalInit(final WebContext context) {
        CommonHelper.assertNotNull("configuration", configuration);

        this.authParams = new HashMap<>();
        final String scope = configuration.getScope();
        // add scope
        if(StringUtils.isNotBlank(scope)){
            this.authParams.put("scope", scope);
        } else {
            // default values
            this.authParams.put("scope", "openid profile email");
        }
        this.authParams.put("response_type", "code");
        this.authParams.put("redirect_uri", configuration.getCallbackUrl());
        // add custom values
        this.authParams.putAll(configuration.getCustomParams());
        // Override with required values
        this.authParams.put("client_id", configuration.getClientId());
    }

    @Override
    public RedirectAction redirect(final WebContext context) throws HttpAction {
        final Map<String, String> params = new HashMap<>(this.authParams);

        addStateAndNonceParameters(context, params);

        final String location = buildAuthenticationRequestUrl(params);
        logger.debug("Authentication request url: {}", location);

        return RedirectAction.redirect(location);
    }

    protected void addStateAndNonceParameters(final WebContext context, final Map<String, String> params) {
        // Init state for CSRF mitigation
        State state = new State();
        params.put("state", state.getValue());
        context.setSessionAttribute(OidcConfiguration.STATE_ATTRIBUTE, state);
        // Init nonce for replay attack mitigation
        if (configuration.isUseNonce()) {
            Nonce nonce = new Nonce();
            params.put("nonce", nonce.getValue());
            context.setSessionAttribute(OidcConfiguration.NONCE_ATTRIBUTE, nonce.getValue());
        }
    }

    protected String buildAuthenticationRequestUrl(final Map<String, String> params) {
        // Build authentication request query string
        final String queryString;
        try {
            queryString = AuthenticationRequest.parse(params).toQueryString();
        } catch (Exception e) {
            throw new TechnicalException(e);
        }
        return configuration.getProviderMetadata().getAuthorizationEndpointURI().toString() + "?" + queryString;
    }

    public OidcConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(final OidcConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String toString() {
        return CommonHelper.toString(this.getClass(), "configuration", configuration);
    }
}
