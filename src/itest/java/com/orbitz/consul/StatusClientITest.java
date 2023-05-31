package com.orbitz.consul;

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

import static org.assertj.core.api.Assertions.assertThat;

class StatusClientITest extends BaseIntegrationTest {

    private static Logger LOG = LoggerFactory.getLogger(StatusClientITest.class);

    private static Set<InetAddress> ips = new HashSet<>();

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
                for (InetAddress inetAddress : Collections.list(netInt.getInetAddresses())) {
                    ips.add(inetAddress);
                }
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

    private static final String IP_PORT_DELIM = ":";

    private String getIp(String ipAndPort) {
        return ipAndPort.substring(0, ipAndPort.indexOf(IP_PORT_DELIM));
    }

    private int getPort(String ipAndPort) {
        return Integer.valueOf(ipAndPort.substring(ipAndPort.indexOf(IP_PORT_DELIM) + 1));
    }

    private void assertLocalIpAndCorrectPort(String ipAndPort) throws UnknownHostException {
        String ip = getIp(ipAndPort);
        int port = getPort(ipAndPort);
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
