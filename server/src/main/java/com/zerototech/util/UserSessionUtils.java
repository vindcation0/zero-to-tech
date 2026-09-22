package com.zerototech.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.UUID;

/**
 * 用户会话与长效 Cookie 识别工具类
 */
public class UserSessionUtils {

    public static final String USER_COOKIE_NAME = "user_id";
    // Cookie 有效期：1 年 (365 天)
    public static final int COOKIE_MAX_AGE = 365 * 24 * 60 * 60;

    /**
     * 获取当前用户的唯一标识，若不存在则生成新 UUID 写入 Cookie 与 Session
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @return 用户的唯一 ID
     */
    public static String getOrCreateUserId(HttpServletRequest request, HttpServletResponse response) {
        // 1. 优先从 Session 中读取
        HttpSession session = request.getSession();
        Object sessionUserId = session.getAttribute(USER_COOKIE_NAME);
        if (sessionUserId instanceof String uid && !uid.isBlank()) {
            return uid;
        }

        // 2. 从请求携带的 Cookie 列表中查找
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (USER_COOKIE_NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    String existingId = c.getValue().trim();
                    session.setAttribute(USER_COOKIE_NAME, existingId);
                    return existingId;
                }
            }
        }

        // 3. 全新用户：生成唯一 UUID
        String newUserId = UUID.randomUUID().toString().replace("-", "");

        // 4. 将新 UUID 植入客户端 Cookie（长效 1 年）
        Cookie userCookie = new Cookie(USER_COOKIE_NAME, newUserId);
        userCookie.setPath("/");
        userCookie.setMaxAge(COOKIE_MAX_AGE);
        userCookie.setHttpOnly(false); // 前端可读，方便开发者排查与确认
        response.addCookie(userCookie);

        // 5. 存入当前 Session 缓存
        session.setAttribute(USER_COOKIE_NAME, newUserId);

        return newUserId;
    }
}
