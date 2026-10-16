package com.hookrelay.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class SsrfProtectionServiceTest {

    private SsrfProtectionService ssrfService;

    @BeforeEach
    void setUp() {
        ssrfService = new SsrfProtectionService();
        ReflectionTestUtils.setField(ssrfService, "enforceHttps", true);
        ReflectionTestUtils.setField(ssrfService, "blockPrivateIps", true);
    }

    @Test
    @DisplayName("Should reject HTTP scheme when HTTPS is enforced")
    void testRejectHttpScheme() {
        assertThrows(IllegalArgumentException.class, () ->
                ssrfService.validateEndpointUrl("http://api.example.com/webhook"));
    }

    @Test
    @DisplayName("Should accept valid HTTPS public endpoint")
    void testAcceptValidHttpsUrl() {
        assertDoesNotThrow(() ->
                ssrfService.validateEndpointUrl("https://httpbin.org/post"));
    }

    @Test
    @DisplayName("Should allow HTTP when enforceHttps is false")
    void testAllowHttpWhenDisabled() {
        ReflectionTestUtils.setField(ssrfService, "enforceHttps", false);
        ReflectionTestUtils.setField(ssrfService, "blockPrivateIps", false);
        assertDoesNotThrow(() ->
                ssrfService.validateEndpointUrl("http://example.com/webhook"));
    }

    @Test
    @DisplayName("Should reject empty or malformed URLs")
    void testRejectMalformedUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                ssrfService.validateEndpointUrl("not_a_valid_url"));
        assertThrows(IllegalArgumentException.class, () ->
                ssrfService.validateEndpointUrl(""));
        assertThrows(IllegalArgumentException.class, () ->
                ssrfService.validateEndpointUrl(null));
        assertThrows(IllegalArgumentException.class, () ->
                ssrfService.validateEndpointUrl("https://"));
    }

    @Test
    @DisplayName("Should detect private and loopback IPv4 addresses")
    void testPrivateIpv4Detection() throws Exception {
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("10.0.0.1")));
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("172.16.0.1")));
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("172.31.255.255")));
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("169.254.169.254"))); // AWS Metadata
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("0.0.0.0")));
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("100.64.0.1"))); // Carrier-grade NAT
    }

    @Test
    @DisplayName("Should detect IPv6 local and loopback addresses")
    void testPrivateIpv6Detection() throws Exception {
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("::1")));
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("fc00::1")));
        assertTrue(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("fe80::1")));
    }

    @Test
    @DisplayName("Should identify valid public routable IP addresses")
    void testPublicIp() throws Exception {
        assertFalse(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(SsrfProtectionService.isPrivateOrLocalAddress(InetAddress.getByName("1.1.1.1")));
    }
}
