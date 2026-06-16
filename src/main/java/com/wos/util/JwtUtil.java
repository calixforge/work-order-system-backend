package com.wos.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 工具:生成与校验 token。
 * 依赖外部配置(密钥、有效期),因此声明为 Spring Bean 注入使用,而非静态工具类。
 */
@Component
public class JwtUtil {

    /** 签名密钥,只存于服务端(配置在 application-local.yml) */
    @Value("${jwt.secret}")
    private String secret;

    /** token 有效期(毫秒) */
    @Value("${jwt.ttl}")
    private Long ttl;

    /**
     * 生成 token,载荷中携带 userId。
     */
    public String createToken(Long userId) {
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + ttl))
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 校验并解析 token,返回 userId;验签失败或已过期时抛出异常。
     */
    public Long parseToken(String token) {
        DecodedJWT decoded = JWT.require(Algorithm.HMAC256(secret))
                .build()
                .verify(token);
        return decoded.getClaim("userId").asLong();
    }
}
