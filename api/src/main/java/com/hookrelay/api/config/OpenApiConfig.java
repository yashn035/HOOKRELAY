package com.hookrelay.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hookRelayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HookRelay API")
                        .description("""
                                ### Enterprise Multi-Tenant Webhook Delivery Platform
                                
                                HookRelay reliably ingests events, fans out to subscribed endpoints, guarantees delivery with exponential backoff and jitter, and protects tenant reliability with circuit breakers and SSRF defenses.
                                
                                #### HMAC-SHA256 Signature Verification
                                Every delivery includes the header:
                                `X-HookRelay-Signature: t=<timestamp>,v1=<hex_hmac>`
                                
                                Where `hex_hmac = HMAC_SHA256(timestamp + "." + raw_body, secret)`.
                                
                                ##### Node.js Verification Snippet:
                                ```javascript
                                const crypto = require('crypto');
                                function verifySignature(rawBody, header, secret) {
                                  const parts = Object.fromEntries(header.split(',').map(p => p.trim().split('=')));
                                  const expected = crypto.createHmac('sha256', secret)
                                                         .update(`${parts.t}.${rawBody}`)
                                                         .digest('hex');
                                  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(parts.v1));
                                }
                                ```
                                
                                ##### Python Verification Snippet:
                                ```python
                                import hmac, hashlib
                                def verify_signature(raw_body: str, header: str, secret: str) -> bool:
                                    parts = dict(p.strip().split('=') for p in header.split(','))
                                    expected = hmac.new(secret.encode(), f"{parts['t']}.{raw_body}".encode(), hashlib.sha256).hexdigest()
                                    return hmac.compare_digest(expected, parts['v1'])
                                ```
                                
                                ##### Java Verification Snippet:
                                ```java
                                Mac mac = Mac.getInstance("HmacSHA256");
                                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                                String expected = HexFormat.of().formatHex(mac.doFinal((t + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
                                boolean valid = MessageDigest.isEqual(expected.getBytes(), v1.getBytes());
                                ```
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("HookRelay Infrastructure Team").url("https://github.com/hookrelay/hookrelay"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token for dashboard users"))
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Tenant ingestion API key in format `Bearer hr_live_...` or `X-API-Key: hr_live_...`")));
    }
}
