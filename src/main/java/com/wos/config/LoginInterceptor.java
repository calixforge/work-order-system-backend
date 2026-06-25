package com.wos.config;

import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.exception.BusinessException;
import com.wos.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

import static com.wos.common.RedisConstants.LOGIN_TOKEN_EXPIRE_MINUTES;
import static com.wos.common.RedisConstants.LOGIN_USER_TOKEN_KEY;

/**
 * 登录拦截器:校验请求头中的 token,通过后将 userId 存入 {@link UserContext}。
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");

        // 前端按 Bearer Token 规范传递: Authorization: Bearer <token>。
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }

        try {
            // 拦截器只负责认证并写入用户上下文;具体角色/数据权限交给 Service 层判断。
            Long userId = jwtUtil.parseToken(token);
            String currentToken = stringRedisTemplate.opsForValue().get(LOGIN_USER_TOKEN_KEY + userId);

            if (currentToken == null || !currentToken.equals(token)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已失效,请重新登录");
            }
            stringRedisTemplate.expire(LOGIN_USER_TOKEN_KEY + userId, LOGIN_TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
            UserContext.setUserId(userId);
            return true;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已失效,请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束必须清除 ThreadLocal,避免线程池复用时把上一个请求的 userId 带到下一个请求。
        UserContext.clear();
    }
}
