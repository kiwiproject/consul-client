package com.orbitz.consul;

import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import com.google.common.net.HostAndPort;
import com.orbitz.consul.model.acl.ImmutablePolicy;
import com.orbitz.consul.model.acl.ImmutablePolicyLink;
import com.orbitz.consul.model.acl.ImmutableRole;
import com.orbitz.consul.model.acl.ImmutableRolePolicyLink;
import com.orbitz.consul.model.acl.ImmutableToken;
import com.orbitz.consul.model.acl.PolicyResponse;
import com.orbitz.consul.model.acl.RoleResponse;
import com.orbitz.consul.model.acl.Token;
import com.orbitz.consul.model.acl.TokenResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

class AclTest {

    public static GenericContainer<?> consulContainerAcl;
    static {
        consulContainerAcl = new GenericContainer<>("consul")
                .withCommand("agent", "-dev", "-client", "0.0.0.0", "--enable-script-checks=true")
                .withExposedPorts(8500)
                .withEnv("CONSUL_LOCAL_CONFIG",
                        "{\n" +
                                "  \"acl\": {\n" +
                                "    \"enabled\": true,\n" +
                                "    \"default_policy\": \"deny\",\n" +
                                "    \"tokens\": {\n" +
                                "      \"master\": \"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"\n" +
                                "    }\n" +
                                "  }\n" +
                                "}"
                );
        consulContainerAcl.start();
    }

    protected static Consul client;

    protected static HostAndPort aclClientHostAndPort = HostAndPort.fromParts("localhost", consulContainerAcl.getFirstMappedPort());

    @BeforeAll
    static void beforeClass() {
        client = Consul.builder()
                .withHostAndPort(aclClientHostAndPort)
                .withAclToken("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                .withReadTimeoutMillis(Duration.ofSeconds(2).toMillis())
                .build();
    }

    @Test
    void listPolicies() {
        AclClient aclClient = client.aclClient();
        assertThat(aclClient.listPolicies().stream().anyMatch(p -> Objects.equals(p.name(), "global-management"))).isTrue();
    }

    @Test
    void testCreateAndReadPolicy() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse policy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());
        assertThat(policy.name(), is(policyName));
        assertThat(policy.datacenters(), is(Optional.empty()));

        policy = aclClient.readPolicy(policy.id());
        assertThat(policy.name(), is(policyName));
        assertThat(policy.datacenters(), is(Optional.empty()));
    }

    @Test
    void testCreateAndReadPolicy_WithDatacenters() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        ImmutablePolicy newPolicy = ImmutablePolicy.builder().name(policyName).datacenters(List.of("dc1")).build();
        PolicyResponse policy = aclClient.createPolicy(newPolicy);
        assertThat(policy.name(), is(policyName));
        assertThat(policy.datacenters(), is(Optional.of(List.of("dc1"))));

        policy = aclClient.readPolicy(policy.id());
        assertThat(policy.name(), is(policyName));
        assertThat(policy.datacenters(), is(Optional.of(List.of("dc1"))));
    }

    @Test
    void testCreateAndReadPolicyByName() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse policy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());
        assertThat(policy.name(), is(policyName));

        policy = aclClient.readPolicyByName(policy.name());
        assertThat(policy.name(), is(policyName));
    }

    @Test
    void testUpdatePolicy() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        String newPolicyName = randomUUIDString();
        aclClient.updatePolicy(createdPolicy.id(), ImmutablePolicy.builder().name(newPolicyName).build());

        PolicyResponse updatedPolicy = aclClient.readPolicy(createdPolicy.id());
        assertThat(updatedPolicy.name(), is(newPolicyName));
    }

    @Test
    void testDeletePolicy() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        int oldPolicyCount = aclClient.listPolicies().size();
        aclClient.deletePolicy(createdPolicy.id());
        int newPolicyCount = aclClient.listPolicies().size();

        assertThat(newPolicyCount, is(oldPolicyCount - 1));
    }

    @Test
    void testCreateAndReadToken() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        String tokenDescription = randomUUIDString();
        TokenResponse createdToken = aclClient.createToken(ImmutableToken.builder().description(tokenDescription).local(false).addPolicies(ImmutablePolicyLink.builder().id(createdPolicy.id()).build()).build());

        TokenResponse readToken = aclClient.readToken(createdToken.accessorId());

        assertThat(readToken.description(), is(tokenDescription));
        assertThat(readToken.policies().get(0).name().get(), is(policyName));
    }

    @Test
    void testCreateAndCloneTokenWithNewDescription() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        String tokenDescription = randomUUIDString();
        TokenResponse createdToken = aclClient.createToken(
                ImmutableToken.builder()
                        .description(tokenDescription)
                        .local(false)
                        .addPolicies(
                                ImmutablePolicyLink.builder()
                                        .id(createdPolicy.id())
                                        .build()
                        ).build());

        String updatedTokenDescription = randomUUIDString();
        Token updateToken =
                ImmutableToken.builder()
                        .id(createdToken.accessorId())
                        .description(updatedTokenDescription)
                        .build();

        TokenResponse readToken = aclClient.cloneToken(createdToken.accessorId(), updateToken);

        assertThat(readToken.accessorId(), not(createdToken.accessorId()));
        assertThat(readToken.description(), is(updatedTokenDescription));
    }

    @Test
    void testCreateAndReadTokenWithCustomIds() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        String tokenId = randomUUIDString();
        String tokenSecretId = randomUUIDString();
        Token token = ImmutableToken.builder()
                .id(tokenId)
                .secretId(tokenSecretId)
                .local(false)
                .addPolicies(
                        ImmutablePolicyLink.builder()
                                .id(createdPolicy.id())
                                .build()
                ).build();
        TokenResponse createdToken = aclClient.createToken(token);

        TokenResponse readToken = aclClient.readToken(createdToken.accessorId());

        assertThat(readToken.accessorId(), is(tokenId));
        assertThat(readToken.secretId(), is(tokenSecretId));
    }

    @Test
    void testReadSelfToken() {
        AclClient aclClient = client.aclClient();

        TokenResponse selfToken = aclClient.readSelfToken();
        assertThat(selfToken.description(), is("Initial Management Token"));
    }

    @Test
    void testUpdateToken() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        ImmutableToken newToken = ImmutableToken.builder()
                .description("none")
                .local(false)
                .addPolicies(ImmutablePolicyLink.builder().id(createdPolicy.id()).build())
                .build();
        TokenResponse createdToken = aclClient.createToken(newToken);

        String newDescription = randomUUIDString();
        ImmutableToken tokenUpdates = ImmutableToken.builder()
                .id(createdToken.accessorId())
                .local(false)
                .description(newDescription)
                .build();
        TokenResponse updatedToken = aclClient.updateToken(createdToken.accessorId(), tokenUpdates);
        assertThat(updatedToken.description(), is(newDescription));

        TokenResponse readToken = aclClient.readToken(createdToken.accessorId());
        assertThat(readToken.description(), is(newDescription));
    }

    @Test
    void testListTokens() {
        AclClient aclClient = client.aclClient();

        assertThat(aclClient.listTokens().stream().anyMatch(p -> Objects.equals(p.description(), "Anonymous Token"))).isTrue();
        assertThat(aclClient.listTokens().stream().anyMatch(p -> Objects.equals(p.description(), "Initial Management Token"))).isTrue();
    }

    @Test
    void testDeleteToken() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());
        TokenResponse createdToken = aclClient.createToken(ImmutableToken.builder().description(randomUUIDString()).local(false).addPolicies(ImmutablePolicyLink.builder().id(createdPolicy.id()).build()).build());

        int oldTokenCount = aclClient.listTokens().size();
        aclClient.deleteToken(createdToken.accessorId());

        int newTokenCount = aclClient.listTokens().size();
        assertThat(newTokenCount, is(oldTokenCount - 1));
    }

    @Test
    void testListRoles() {
        AclClient aclClient = client.aclClient();

        String roleName1 = randomUUIDString();
        String roleName2 = randomUUIDString();
        aclClient.createRole(ImmutableRole.builder().name(roleName1).build());
        aclClient.createRole(ImmutableRole.builder().name(roleName2).build());

        assertThat(aclClient.listRoles().stream().anyMatch(p -> Objects.equals(p.name(), roleName1))).isTrue();
        assertThat(aclClient.listRoles().stream().anyMatch(p -> Objects.equals(p.name(), roleName2))).isTrue();
    }

    @Test
    void testCreateAndReadRole() {
        AclClient aclClient = client.aclClient();

        String roleName = randomUUIDString();
        RoleResponse role = aclClient.createRole(ImmutableRole.builder().name(roleName).build());

        RoleResponse roleResponse = aclClient.readRole(role.id());
        assertThat(roleResponse.id()).isEqualTo(role.id());
    }

    @Test
    void testCreateAndReadRoleByName() {
        AclClient aclClient = client.aclClient();

        String roleName = randomUUIDString();
        RoleResponse role = aclClient.createRole(ImmutableRole.builder().name(roleName).build());

        RoleResponse roleResponse = aclClient.readRoleByName(role.name());
        assertThat(roleResponse.name()).isEqualTo(role.name());
    }

    @Test
    void testCreateAndReadRoleWithPolicy() {
        AclClient aclClient = client.aclClient();

        String policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        String roleName = randomUUIDString();
        RoleResponse role = aclClient.createRole(
                ImmutableRole.builder()
                        .name(roleName)
                        .addPolicies(
                                ImmutableRolePolicyLink.builder()
                                .id(createdPolicy.id())
                                .build()
                        )
                        .build());

        RoleResponse roleResponse = aclClient.readRole(role.id());
        assertThat(roleResponse.id()).isEqualTo(role.id());
        assertThat(roleResponse.policies().size()).isEqualTo(1);
        assertThat(roleResponse.policies().get(0).id().isPresent()).isTrue();
        assertThat(roleResponse.policies().get(0).id().get()).isEqualTo(createdPolicy.id());
    }

    @Test
    void testUpdateRole() {
        AclClient aclClient = client.aclClient();

        String roleName = randomUUIDString();
        String roleDescription = randomUUIDString();
        RoleResponse role = aclClient.createRole(
                ImmutableRole.builder()
                        .name(roleName)
                        .description(roleDescription)
                        .build());

        RoleResponse roleResponse = aclClient.readRole(role.id());
        assertThat(roleResponse.description()).isEqualTo(roleDescription);

        String roleNewDescription = randomUUIDString();
        RoleResponse updatedRoleResponse = aclClient.updateRole(roleResponse.id(),
                ImmutableRole.builder()
                        .name(roleName)
                        .description(roleNewDescription)
                        .build());

        assertThat(updatedRoleResponse.description()).isEqualTo(roleNewDescription);
    }

    @Test
    void testDeleteRole() {
        AclClient aclClient = client.aclClient();

        String roleName = randomUUIDString();
        RoleResponse role = aclClient.createRole(
                ImmutableRole.builder()
                        .name(roleName)
                        .build());

        RoleResponse roleResponse = aclClient.readRole(role.id());
        assertThat(roleResponse.name()).isEqualTo(roleName);

        String id = roleResponse.id();
        aclClient.deleteRole(id);

        assertThatExceptionOfType(ConsulException.class).isThrownBy(() -> aclClient.readRole(id));
    }

}
