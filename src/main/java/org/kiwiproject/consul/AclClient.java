package org.kiwiproject.consul;

import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.model.acl.Policy;
import org.kiwiproject.consul.model.acl.PolicyResponse;
import org.kiwiproject.consul.model.acl.Role;
import org.kiwiproject.consul.model.acl.RoleListResponse;
import org.kiwiproject.consul.model.acl.RoleResponse;
import org.kiwiproject.consul.model.acl.Token;
import org.kiwiproject.consul.model.acl.TokenListResponse;
import org.kiwiproject.consul.model.acl.TokenResponse;
import org.kiwiproject.consul.monitoring.ClientEventCallback;
import org.kiwiproject.consul.option.Options;
import org.kiwiproject.consul.option.RoleOptions;
import org.kiwiproject.consul.option.TokenQueryOptions;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

import java.util.List;
import java.util.Map;

public class AclClient extends BaseClient {

    private static final String CLIENT_NAME = "acl";

    private final Api api;

    AclClient(Retrofit retrofit, ClientConfig config, ClientEventCallback eventCallback) {
        super(CLIENT_NAME, config, eventCallback);
        this.api = retrofit.create(Api.class);
    }

    public PolicyResponse createPolicy(Policy policy) {
        return http.extract(api.createPolicy(policy));
    }

    public PolicyResponse readPolicy(String id) {
        return http.extract(api.readPolicy(id));
    }

    public PolicyResponse readPolicyByName(String name) {
        return http.extract(api.readPolicyByName(name));
    }

    public PolicyResponse updatePolicy(String id, Policy policy) {
        return http.extract(api.updatePolicy(id, policy));
    }

    public void deletePolicy(String id) {
        http.extract(api.deletePolicy(id));
    }

    public List<PolicyResponse> listPolicies() {
        return http.extract(api.listPolicies());
    }

    public TokenResponse createToken(Token token) {
        return http.extract(api.createToken(token));
    }

    public TokenResponse cloneToken(String id, Token token) {
        return http.extract(api.cloneToken(id, token));
    }

    public TokenResponse readToken(String id) {
        return http.extract(api.readToken(id));
    }

    public TokenResponse readSelfToken() {
        return http.extract(api.readToken("self"));
    }

    public TokenResponse updateToken(String id, Token token) {
        return http.extract(api.updateToken(id, token));
    }

    public List<TokenListResponse> listTokens() {
        return listTokens(Options.BLANK_TOKEN_QUERY_OPTIONS);
    }

    public List<TokenListResponse> listTokens(TokenQueryOptions queryOptions) {
        return http.extract(api.listTokens(queryOptions.toQuery()));
    }

    public void deleteToken(String id) {
        http.extract(api.deleteToken(id));
    }

    public RoleResponse createRole(Role token) {
        return http.extract(api.createRole(token));
    }

    public RoleResponse readRole(String id) {
        return http.extract(api.readRole(id));
    }

    public RoleResponse readRoleByName(String name) {
        return http.extract(api.readRoleByName(name));
    }

    public RoleResponse updateRole(String id, Role role) {
        return http.extract(api.updateRole(id, role));
    }

    public List<RoleListResponse> listRoles() {
        return listRoles(Options.BLANK_ROLE_OPTIONS);
    }

    public List<RoleListResponse> listRoles(RoleOptions roleOptions) {
        return http.extract(api.listRoles(roleOptions.toQuery()));
    }

    public void deleteRole(String id) {
        http.extract(api.deleteRole(id));
    }

    interface Api {

        @PUT("acl/policy")
        Call<PolicyResponse> createPolicy(@Body Policy policy);

        @GET("acl/policy/{id}")
        Call<PolicyResponse> readPolicy(@Path("id") String id);

        @GET("acl/policy/name/{name}")
        Call<PolicyResponse> readPolicyByName(@Path("name") String name);

        @PUT("acl/policy/{id}")
        Call<PolicyResponse> updatePolicy(@Path("id") String id, @Body Policy policy);

        @DELETE("acl/policy/{id}")
        Call<Void> deletePolicy(@Path("id") String id);

        @GET("acl/policies")
        Call<List<PolicyResponse>> listPolicies();

        @PUT("acl/token")
        Call<TokenResponse> createToken(@Body Token token);

        @PUT("acl/token/{id}/clone")
        Call<TokenResponse> cloneToken(@Path("id") String id, @Body Token token);

        @GET("acl/token/{id}")
        Call<TokenResponse> readToken(@Path("id") String id);

        @PUT("acl/token/{id}")
        Call<TokenResponse> updateToken(@Path("id") String id, @Body Token token);

        @GET("acl/tokens")
        Call<List<TokenListResponse>> listTokens(@QueryMap Map<String, Object> query);

        @DELETE("acl/token/{id}")
        Call<Void> deleteToken(@Path("id") String id);

        @PUT("acl/role")
        Call<RoleResponse> createRole(@Body Role role);

        @GET("acl/role/{id}")
        Call<RoleResponse> readRole(@Path("id") String id);

        @GET("acl/role/name/{name}")
        Call<RoleResponse> readRoleByName(@Path("name") String name);

        @PUT("acl/role/{id}")
        Call<RoleResponse> updateRole(@Path("id") String id, @Body Role role);

        @DELETE("acl/role/{id}")
        Call<Void> deleteRole(@Path("id") String id);

        @GET("acl/roles")
        Call<List<RoleListResponse>> listRoles(@QueryMap Map<String, Object> query);
    }

}
