package com.example.tomatomall.configure;

import com.example.tomatomall.Util.TokenUtil;
import com.example.tomatomall.exception.TomatoMallException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @Author: DingXiaoyu
 * @Date: 0:17 2023/11/26
 * 这个类定制了一个登录的拦截器，
 * SpringBoot的拦截器标准为HandlerInterceptor接口�?
 * 这个类实现了这个接口，表示是SpringBoot标准下的�?
 * 在preHandle方法中，通过获取请求头Header中的token�?
 * 判断了token是否合法，如果不合法则抛异常�?
 * 合法则将用户信息存储到request的session中�?
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    TokenUtil tokenUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        //请求路径�?api/accounts的接口有获取用户详情和创建用户，但是后者不需要鉴权即可请�?
        //下面为助教给出的实现方式
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("/api/accounts".equals(uri) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        // 支付宝回调不需要登录验�?
        if("/api/orders/notify".equals(uri)){
            return true;
        }
        if("/api/orders/returnUrl".equals(uri)){
            return true;
        }
        if("/oss/fileoss".equals(uri)){
            return true;
        }

        //获取请求头中的token，用于验证用户是否登录(获取用户信息�?
        String token = request.getHeader("token");
        if (token != null && tokenUtil.verifyToken(token)) {
            request.getSession().setAttribute("currentAccount",tokenUtil.getAccount(token));
            // 存到 request attribute
            request.setAttribute("accountId", tokenUtil.getAccount(token).getId());
            return true;
        }else {
            throw TomatoMallException.notLogin();
        }
    }

}


