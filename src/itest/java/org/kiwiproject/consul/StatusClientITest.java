package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.HostAndPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class StatusClientITest extends BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(StatusClientITest.class);

    private static final Set<InetAddress> ips = new HashSet<>();

    @BeforeAll
    static void getIps() throws RuntimeException {
        try {
            InetAddress[] externalIps = InetAddress.getAllByName(InetAddress.getLocalHost().getCanonicalHostName());
            ips.addAll(List.of(externalIps));
        } catch (UnknownHostException ex) {
           LOG.warn("Could not determine fully qualified host name. Continuing.", ex);
        }
        Enumeration<NetworkInterface> netInts;
        try {
            netInts = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netInt : Collections.list(netInts)) {
                ips.addAll(Collections.list(netInt.getInetAddresses()));
            }
        } catch (SocketException ex) {
            LOG.warn("Could not access local network adapters. Continuing", ex);
        }
        if (ips.isEmpty()) {
            throw new RuntimeException("Unable to discover any local IP addresses");
        }
    }

    private boolean isLocalIp(String ipAddress) throws UnknownHostException {
        InetAddress ip = InetAddress.getByName(ipAddress);
        return ips.contains(ip);
    }

    private void assertLocalIpAndCorrectPort(String ipAndPort) throws UnknownHostException {
        var hostAndPort = HostAndPort.fromString(ipAndPort);
        String ip = hostAndPort.getHost();
        int port = hostAndPort.getPort();
        assertThat(isLocalIp(ip)).isTrue();
        assertThat(port).isEqualTo(8300);
    }

    @Test
    void shouldGetLeader() throws UnknownHostException {
        String ipAndPort = client.statusClient().getLeader();
        assertLocalIpAndCorrectPort(ipAndPort);
    }

    @Test
    void shouldGetPeers() throws UnknownHostException {
        List<String> peers = client.statusClient().getPeers();
        for (String ipAndPort : peers) {
            assertLocalIpAndCorrectPort(ipAndPort);
        }
    }
}
