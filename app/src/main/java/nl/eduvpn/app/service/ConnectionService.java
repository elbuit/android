/*
 *  This file is part of eduVPN.
 *
 *     eduVPN is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     eduVPN is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with eduVPN.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.eduvpn.app.service;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.CodeVerifierUtil;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;
import net.openid.appauth.browser.BrowserBlacklist;
import net.openid.appauth.browser.VersionedBrowserMatcher;

import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import nl.eduvpn.app.MainActivity;
import nl.eduvpn.app.R;
import nl.eduvpn.app.entity.DiscoveredAPI;
import nl.eduvpn.app.entity.Instance;
import nl.eduvpn.app.utils.ErrorDialog;
import nl.eduvpn.app.utils.Log;

/**
 * The connection service takes care of building up the URLs and validating the result.
 * Created by Daniel Zolnai on 2016-10-11.
 */
public class ConnectionService {

    private static final String TAG = ConnectionService.class.getName();

    private static final String SCOPE = "config";
    private static final String RESPONSE_TYPE = ResponseTypeValues.CODE;
    private static final String REDIRECT_URI = "org.eduvpn.app:/api/callback";
    private static final String CLIENT_ID = "org.eduvpn.app";

    private static final int REQUEST_CODE_APP_AUTH = 100; // This is not used, since we only get one type of request for the redirect URL.


    private final PreferencesService _preferencesService;
    private final HistoryService _historyService;
    private final SecurityService _securityService;
    private AuthorizationService _authorizationService;
    private AuthState _authState;

    /**
     * Constructor.
     *
     * @param preferencesService The preferences service used to store temporary data.
     * @param historyService     History service for storing data for long-term usage
     * @param securityService    For security related tasks.
     */
    public ConnectionService(PreferencesService preferencesService, HistoryService historyService,
                             SecurityService securityService) {
        _preferencesService = preferencesService;
        _securityService = securityService;
        _historyService = historyService;
        _authState = preferencesService.getCurrentAuthState();
    }

    public void onStart(Activity activity) {
        if (!_preferencesService.getAppSettings().useCustomTabs()) {
            // We do not allow any custom tab implementation.
            _authorizationService = new AuthorizationService(activity, new AppAuthConfiguration.Builder()
                    .setBrowserMatcher(new BrowserBlacklist(
                            VersionedBrowserMatcher.CHROME_CUSTOM_TAB,
                            VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB)
                    )
                    .build());
        } else {
            // Default behavior
            _authorizationService = new AuthorizationService(activity);
        }
    }

    public void onStop() {
        if (_authorizationService != null) {
            _authorizationService.dispose();
            _authorizationService = null;
        }
    }


    /**
     * Initiates a connection to the VPN provider instance.
     *
     * @param activity      The current activity.
     * @param instance      The instance to connect to.
     * @param discoveredAPI The discovered API which has the URL.
     */
    public void initiateConnection(@NonNull final Activity activity, @NonNull final Instance instance, @NonNull final DiscoveredAPI discoveredAPI) {
        Observable.defer(new Callable<ObservableSource<AuthorizationRequest>>() {
            @Override
            public ObservableSource<AuthorizationRequest> call() throws Exception {
                String stateString = _securityService.generateSecureRandomString(32);
                _preferencesService.currentInstance(instance);
                _preferencesService.storeCurrentDiscoveredAPI(discoveredAPI);
                AuthorizationServiceConfiguration serviceConfig = _buildAuthConfiguration(discoveredAPI);

                AuthorizationRequest.Builder authRequestBuilder =
                        new AuthorizationRequest.Builder(
                                serviceConfig, // the authorization service configuration
                                CLIENT_ID, // the client ID, typically pre-registered and static
                                ResponseTypeValues.CODE, // the response_type value: we want a code
                                Uri.parse(REDIRECT_URI)) // the redirect URI to which the auth response is sent
                                .setScope(SCOPE)
                                .setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier()) // Use S256 challenge method if possible
                                .setResponseType(RESPONSE_TYPE)
                                .setState(stateString);
                return Observable.just(authRequestBuilder.build());
            }
        }).subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<AuthorizationRequest>() {
                    @Override
                    public void accept(AuthorizationRequest authorizationRequest) throws Exception {
                        if (_authorizationService == null) {
                            throw new RuntimeException("Please call onStart() on this service from your activity!");
                        }
                        _authorizationService.performAuthorizationRequest(
                                authorizationRequest,
                                PendingIntent.getActivity(activity, REQUEST_CODE_APP_AUTH, new Intent(activity, MainActivity.class), 0));

                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        ErrorDialog.show(activity, R.string.error_dialog_title, throwable.getMessage());
                    }
                });
    }

    /**
     * Parses the authorization response and retrieves the access token.
     *
     * @param authorizationResponse The auth response to process.
     * @param activity              The current activity.
     */
    public void parseAuthorizationResponse(@NonNull final AuthorizationResponse authorizationResponse, final Activity activity) {
        Log.i(TAG, "Got auth response: " + authorizationResponse.jsonSerializeString());
        _authorizationService.performTokenRequest(
                authorizationResponse.createTokenExchangeRequest(),
                new AuthorizationService.TokenResponseCallback() {
                    @Override
                    public void onTokenRequestCompleted(TokenResponse tokenResponse, AuthorizationException ex) {
                        if (tokenResponse != null) {
                            // exchange succeeded
                            _processTokenExchangeResponse(authorizationResponse, tokenResponse, activity);
                        } else {
                            // authorization failed, check ex for more details
                            ErrorDialog.show(activity,
                                    R.string.authorization_error_title,
                                    activity.getString(R.string.authorization_error_message, ex.error, ex.code, ex.getMessage()));
                        }
                    }
                });

    }

    /**
     * Process the the exchanged token.
     *
     * @param authorizationResponse The authorization response.
     * @param tokenResponse         The response from the token exchange updated into the authentication state
     * @param activity              The current activity.
     */
    private void _processTokenExchangeResponse(AuthorizationResponse authorizationResponse, TokenResponse tokenResponse, Activity activity) {
        AuthState authState = new AuthState(authorizationResponse, tokenResponse, null);
        String accessToken = authState.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            ErrorDialog.show(activity, R.string.error_dialog_title, R.string.error_access_token_missing);
            return;
        }
        // Save the authentication state.
        _preferencesService.storeCurrentAuthState(authState);
        // Save the access token for later use.
        _historyService.cacheAuthenticationState(_preferencesService.getCurrentInstance(), authState);
        Toast.makeText(activity, R.string.provider_added_new_configs_available, Toast.LENGTH_LONG).show();
    }

    /**
     * Returns a single which emits a fresh access token which is to be used with the API.
     *
     * @return The access token used to authenticate in an emitter.
     */
    public Single<String> getFreshAccessToken() {
        final Single<String> publishSubject;
        if (!_authState.getNeedsTokenRefresh() && _authState.getAccessToken() != null) {
            publishSubject = Single.just(_authState.getAccessToken());
            return publishSubject;
        }
        publishSubject = Single.create(new SingleOnSubscribe<String>() {
            @Override
            public void subscribe(@io.reactivex.annotations.NonNull final SingleEmitter<String> singleEmitter) throws Exception {
                _authState.performActionWithFreshTokens(_authorizationService, new AuthState.AuthStateAction() {
                    @Override
                    public void execute(@Nullable String accessToken, @Nullable String idToken, @Nullable AuthorizationException ex) {
                        if (accessToken != null) {
                            _preferencesService.storeCurrentAuthState(_authState);
                            _historyService.refreshAuthState(_authState);
                            singleEmitter.onSuccess(accessToken);
                        } else {
                            singleEmitter.onError(ex);
                        }
                    }
                });
            }
        });

        return publishSubject;
    }

    /**
     * Sets and saved the current access token to use with the requests.
     *
     * @param authState The access token and its refresh token to use for getting resources.
     */
    public void setAuthState(AuthState authState) {
        _authState = authState;
        _preferencesService.storeCurrentAuthState(authState);
    }

    /**
     * Builds the authorization service configuration.
     *
     * @param discoveredAPI The discovered API URLs.
     * @return The configuration for the authorization service.
     */
    private AuthorizationServiceConfiguration _buildAuthConfiguration(DiscoveredAPI discoveredAPI) {
        return new AuthorizationServiceConfiguration(
                Uri.parse(discoveredAPI.getAuthorizationEndpoint()),
                Uri.parse(discoveredAPI.getTokenEndpoint()),
                null);
    }
}