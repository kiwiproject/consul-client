package com.orbitz.consul.failover;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.Consul;
import org.junit.jupiter.api.Test;

import java.util.List;

class FailoverTest extends BaseIntegrationTest {

    /**
     * @implNote Without modifying the production code to allow inspection of interception results, there is no
	 * way I can see to truly verify whether the blacklisting occurred based on the timeout period. And I don't
	 * think it's a good idea to modify the production code to allow that, so this test does the best it can.
	 * In addition, since we're not modifying the production code, there is no "hook" point that would allow
	 * using Awaitility, so we have to stick with an actual sleep. I did, however, modify the timeout and sleep
	 * time to be much less than the original 5 seconds. Now the test runs in a little under 500 millis instead
	 * of over 5 seconds. The main way to verify is to inspect the logged output and check that the first two
	 * bogus target hosts are tried on both attempts to get peers.
     */
    @Test
    void testFailover() throws InterruptedException {

		// Create a set of targets
		var port = consulContainer.getFirstMappedPort();
		var targets = List.of(
			HostAndPort.fromParts("1.2.3.4", port),
			HostAndPort.fromParts("3.4.5.6", port),
			HostAndPort.fromParts("localhost", port)
		);

		// Create our consul instance
		var consulBuilder = Consul.builder();
		int blacklistTimeInMillis = 200;
		consulBuilder.withMultipleHostAndPort(targets, blacklistTimeInMillis);
		consulBuilder.withConnectTimeoutMillis(50);

		// Create the client
		Consul client = consulBuilder.build();

		// Get the peers (should fail through 1.2.3.4 and 3.4.5.6 into localhost)
		List<String> peers1 = client.statusClient().getPeers();
		assertThat(peers1).isNotEmpty();

		Thread.sleep(blacklistTimeInMillis + 1);

		// Get the peers again (should fail through 1.2.3.4 and 3.4.5.6 into localhost since the blacklist timeout has expired)
		List<String> peers2 = client.statusClient().getPeers();
		assertThat(peers2).isNotEmpty().isEqualTo(peers1);
	}
}
