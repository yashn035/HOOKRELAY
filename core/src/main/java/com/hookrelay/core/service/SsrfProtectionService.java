package com.hookrelay.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Service
public class SsrfProtectionService {

    @Value("${hookrelay.security.enforce-https:true}")
    private boolean enforceHttps;

    @Value("${hookrelay.security.block-private-ips:true}")
    private boolean blockPrivateIps;

    public void validateEndpointUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("Endpoint URL cannot be empty");
        }

        URI uri;
        try {
            uri = URI.create(urlString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed endpoint URI: " + e.getMessage(), e);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL must contain a scheme");
        }

        if (enforceHttps && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Endpoint destination must use HTTPS for secure delivery");
        } else if (!enforceHttps && !"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Endpoint destination scheme must be http or https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Endpoint host cannot be empty");
        }

        if (blockPrivateIps) {
            validateHostNotPrivate(host);
        }
    }

    public void validateHostNotPrivate(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateOrLocalAddress(address)) {
                    throw new SecurityException("SSRF Protection: Destination resolves to private/reserved IP: " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve endpoint host: " + host, e);
        }
    }

    public static boolean isPrivateOrLocalAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
            return true;
        }

        if (addr.isSiteLocalAddress()) {
            return true;
        }

        byte[] ip = addr.getAddress();
        if (ip.length == 4) {
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;

            // 10.0.0.0/8
            if (b0 == 10) return true;
            // 172.16.0.0/12
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) return true;
            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true;
            // 169.254.0.0/16 (Link Local / Cloud Metadata like 169.254.169.254)
            if (b0 == 169 && b1 == 254) return true;
            // 0.0.0.0/8
            if (b0 == 0) return true;
            // 100.64.0.0/10 (Carrier-grade NAT)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return true;
        } else if (ip.length == 16) {
            // IPv6 Unique Local Address fc00::/7
            if ((ip[0] & 0xFE) == 0xFC) return true;
            // IPv6 Link-Local fe80::/10
            if ((ip[0] & 0xFF) == 0xFE && (ip[1] & 0xC0) == 0x80) return true;
        }

        return false;
    }
}
