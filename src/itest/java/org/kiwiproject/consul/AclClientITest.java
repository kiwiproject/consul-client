package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.kiwiproject.consul.ConsulTestcontainers.CONSUL_DOCKER_IMAGE_NAME;
import static org.kiwiproject.consul.TestUtils.randomUUIDString;

import com.google.common.net.HostAndPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.acl.ImmutablePolicy;
import org.kiwiproject.consul.model.acl.ImmutablePolicyLink;
import org.kiwiproject.consul.model.acl.ImmutableRole;
import org.kiwiproject.consul.model.acl.ImmutableRolePolicyLink;
import org.kiwiproject.consul.model.acl.ImmutableToken;
import org.kiwiproject.consul.model.acl.PolicyResponse;
import org.kiwiproject.consul.model.acl.RoleResponse;
import org.kiwiproject.consul.model.acl.Token;
import org.kiwiproject.consul.model.acl.TokenResponse;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * @implNote This does not extend {@link BaseIntegrationTest} because it needs to launch Consul with ACLs enabled
 * and to create a client that uses the ACL token when connecting.
 */
class AclClientITest {

    private static GenericContainer<?> consulContainerAcl;
    private static AclClient aclClient;

    @BeforeAll
    static void beforeAll() {
        // noinspection resource
        consulContainerAcl = new GenericContainer<>(CONSUL_DOCKER_IMAGE_NAME)
                .withCommand("agent", "-dev", "-client", "0.0.0.0", "--enable-script-checks=true")
                .withExposedPorts(8500)
                .withEnv("CONSUL_LOCAL_CONFIG",
                        """
                            {
                                "acl": {
                                    "enabled": true,
                                    "default_policy": "deny",
                                    "tokens": {
                                        "master": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
                                    }
                                }
                            }
                        """
                );
        consulContainerAcl.start();

        var aclClientHostAndPort = HostAndPort.fromParts("localhost", consulContainerAcl.getFirstMappedPort());

        var client = Consul.builder()
                .withHostAndPort(aclClientHostAndPort)
                .withAclToken("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                .withReadTimeoutMillis(Duration.ofSeconds(2).toMillis())
                .build();

        aclClient = client.aclClient();
    }

    @AfterAll
    static void afterAll() {
        consulContainerAcl.stop();
    }

    @Test
    void listPolicies() {
        assertThat(aclClient.listPolicies().stream().anyMatch(p -> Objects.equals(p.name(), "global-management"))).isTrue();
    }

    @Test
    void testCreateAndReadPolicy() {
        var policyName = randomUUIDString();
        PolicyResponse policy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());
        assertThat(policy.name()).isEqualTo(policyName);
        assertThat(policy.datacenters()).isEmpty();

        policy = aclClient.readPolicy(policy.id());
        assertThat(policy.name()).isEqualTo(policyName);
        assertThat(policy.datacenters()).isEmpty();
    }

    @Test
    void testCreateAndReadPolicy_WithDatacenters() {
        var policyName = randomUUIDString();
        ImmutablePolicy newPolicy = ImmutablePolicy.builder().name(policyName).datacenters(List.of("dc1")).build();
        PolicyResponse policy = aclClient.createPolicy(newPolicy);
        assertThat(policy.name()).isEqualTo(policyName);
        assertThat(policy.datacenters()).contains(List.of("dc1"));

        policy = aclClient.readPolicy(policy.id());
        assertThat(policy.name()).isEqualTo(policyName);
        assertThat(policy.datacenters()).contains(List.of("dc1"));
    }

    @Test
    void testCreateAndReadPolicyByName() {
        var policyName = randomUUIDString();
        PolicyResponse policy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());
        assertThat(policy.name()).isEqualTo(policyName);

        policy = aclClient.readPolicyByName(policy.name());
        assertThat(policy.name()).isEqualTo(policyName);
    }

    @Test
    void testUpdatePolicy() {
        var policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        var newPolicyName = randomUUIDString();
        var policyResponse = aclClient.updatePolicy(createdPolicy.id(), ImmutablePolicy.builder().name(newPolicyName).build());
        assertThat(policyResponse.id()).isEqualTo(createdPolicy.id());
        assertThat(policyResponse.name()).isEqualTo(newPolicyName);

        PolicyResponse updatedPolicy = aclClient.readPolicy(createdPolicy.id());
        assertThat(updatedPolicy.name()).isEqualTo(newPolicyName);
    }

    @Test
    void testDeletePolicy() {
        var policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        var oldPolicyCount = aclClient.listPolicies().size();
        aclClient.deletePolicy(createdPolicy.id());
        var newPolicyCount = aclClient.listPolicies().size();

        assertThat(newPolicyCount).isEqualTo(oldPolicyCount - 1);
    }

    @Test
    void testCreateAndReadToken() {
        var policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        var tokenDescription = randomUUIDString();
        TokenResponse createdToken = aclClient.createToken(ImmutableToken.builder()
                .description(tokenDescription)
                .local(false)
                .addPolicies(ImmutablePolicyLink.builder().id(createdPolicy.id()).build())
                .build());

        TokenResponse readToken = aclClient.readToken(createdToken.accessorId());

        assertThat(readToken.description()).isEqualTo(tokenDescription);

        assertThat(readToken.policies().get(0).name()).contains(policyName);
    }

    @Test
    void testCreateAndCloneTokenWithNewDescription() {
        var policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        var tokenDescription = randomUUIDString();
        TokenResponse createdToken = aclClient.createToken(
                ImmutableToken.builder()
                        .description(tokenDescription)
                        .local(false)
                        .addPolicies(
                                ImmutablePolicyLink.builder()
                                        .id(createdPolicy.id())
                                        .build()
                        ).build());

        var updatedTokenDescription = randomUUIDString();
        Token updateToken =
                ImmutableToken.builder()
                        .id(createdToken.accessorId())
                        .description(updatedTokenDescription)
                        .build();

        TokenResponse readToken = aclClient.cloneToken(createdToken.accessorId(), updateToken);

        assertThat(readToken.accessorId()).isNotEqualTo(createdToken.accessorId());
        assertThat(readToken.description()).isEqualTo(updatedTokenDescription);
    }

    @Test
    void testCreateAndReadTokenWithCustomIds() {
        var policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        var tokenId = randomUUIDString();
        var tokenSecretId = randomUUIDString();
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

        assertThat(readToken.accessorId()).isEqualTo(tokenId);
        assertThat(readToken.secretId()).isEqualTo(tokenSecretId);
    }

    @Test
    void testReadSelfToken() {
        TokenResponse selfToken = aclClient.readSelfToken();
        assertThat(selfToken.description()).isEqualTo("Initial Management Token");
    }

    @Test
    void testUpdateToken() {
        var policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        ImmutableToken newToken = ImmutableToken.builder()
                .description("none")
                .local(false)
                .addPolicies(ImmutablePolicyLink.builder().id(createdPolicy.id()).build())
                .build();
        TokenResponse createdToken = aclClient.createToken(newToken);

        var newDescription = randomUUIDString();
        ImmutableToken tokenUpdates = ImmutableToken.builder()
                .id(createdToken.accessorId())
                .local(false)
                .description(newDescription)
                .build();
        TokenResponse updatedToken = aclClient.updateToken(createdToken.accessorId(), tokenUpdates);
        assertThat(updatedToken.description()).isEqualTo(newDescription);

        TokenResponse readToken = aclClient.readToken(createdToken.accessorId());
        assertThat(readToken.description()).isEqualTo(newDescription);
    }

    @Test
    void testListTokens() {
        assertThat(aclClient.listTokens().stream().anyMatch(p -> Objects.equals(p.description(), "Anonymous Token"))).isTrue();
        assertThat(aclClient.listTokens().stream().anyMatch(p -> Objects.equals(p.description(), "Initial Management Token"))).isTrue();
    }

    @Test
    void testDeleteToken() {
        var policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());
        TokenResponse createdToken = aclClient.createToken(ImmutableToken.builder().description(randomUUIDString()).local(false).addPolicies(ImmutablePolicyLink.builder().id(createdPolicy.id()).build()).build());

        int oldTokenCount = aclClient.listTokens().size();
        aclClient.deleteToken(createdToken.accessorId());

        int newTokenCount = aclClient.listTokens().size();
        assertThat(newTokenCount).isEqualTo(oldTokenCount - 1);
    }

    @Test
    void testListRoles() {
        var roleName1 = randomUUIDString();
        var roleName2 = randomUUIDString();
        aclClient.createRole(ImmutableRole.builder().name(roleName1).build());
        aclClient.createRole(ImmutableRole.builder().name(roleName2).build());

        assertThat(aclClient.listRoles().stream().anyMatch(p -> Objects.equals(p.name(), roleName1))).isTrue();
        assertThat(aclClient.listRoles().stream().anyMatch(p -> Objects.equals(p.name(), roleName2))).isTrue();
    }

    @Test
    void testCreateAndReadRole() {
        var roleName = randomUUIDString();
        RoleResponse role = aclClient.createRole(ImmutableRole.builder().name(roleName).build());

        RoleResponse roleResponse = aclClient.readRole(role.id());
        assertThat(roleResponse.id()).isEqualTo(role.id());
    }

    @Test
    void testCreateAndReadRoleByName() {
        var roleName = randomUUIDString();
        RoleResponse role = aclClient.createRole(ImmutableRole.builder().name(roleName).build());

        RoleResponse roleResponse = aclClient.readRoleByName(role.name());
        assertThat(roleResponse.name()).isEqualTo(role.name());
    }

    @Test
    void testCreateAndReadRoleWithPolicy() {
        var policyName = randomUUIDString();
        PolicyResponse createdPolicy = aclClient.createPolicy(ImmutablePolicy.builder().name(policyName).build());

        var roleName = randomUUIDString();
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
        assertThat(roleResponse.policies()).hasSize(1);
        assertThat(roleResponse.policies().get(0).id()).isPresent();
        assertThat(roleResponse.policies().get(0).id()).contains(createdPolicy.id());
    }

    @Test
    void testUpdateRole() {
        var roleName = randomUUIDString();
        var roleDescription = randomUUIDString();
        RoleResponse role = aclClient.createRole(
                ImmutableRole.builder()
                        .name(roleName)
                        .description(roleDescription)
                        .build());

        RoleResponse roleResponse = aclClient.readRole(role.id());
        assertThat(roleResponse.description()).isEqualTo(roleDescription);

        var roleNewDescription = randomUUIDString();
        RoleResponse updatedRoleResponse = aclClient.updateRole(roleResponse.id(),
                ImmutableRole.builder()
                        .name(roleName)
                        .description(roleNewDescription)
                        .build());

        assertThat(updatedRoleResponse.description()).isEqualTo(roleNewDescription);
    }

    @Test
    void testDeleteRole() {
        var roleName = randomUUIDString();
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
