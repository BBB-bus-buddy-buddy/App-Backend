package capston2024.bustracker.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import java.util.HashMap;
import java.util.Map;

public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final OAuth2AuthorizationRequestResolver defaultResolver;

    public CustomAuthorizationRequestResolver(ClientRegistrationRepository repo, String baseUri) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, baseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(request);
        return customizeAuthorizationRequest(req, request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(request, clientRegistrationId);
        return customizeAuthorizationRequest(req, request);
    }

    // In CustomAuthorizationRequestResolver
    private OAuth2AuthorizationRequest customizeAuthorizationRequest(OAuth2AuthorizationRequest req, HttpServletRequest request) {
        if (req == null) return null;

        String appType = request.getParameter("app");

        // Create a custom state that includes the app type
        String originalState = req.getState();
        String customState = originalState;
        if (appType != null) {
            // Use a format that can be easily parsed later, e.g. originalState + "__app:" + appType
            customState = originalState + "__app:" + appType;
        }

        return OAuth2AuthorizationRequest.from(req)
                .state(customState)
                .build();
    }
}
