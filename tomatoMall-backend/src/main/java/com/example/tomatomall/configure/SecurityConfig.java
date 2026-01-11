package com.example.tomatomall.configure;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 返回 BCryptPasswordEncoder 实例
        return new BCryptPasswordEncoder();
    }

    //处理密码加密问题
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .authorizeHttpRequests(auth -> auth
//                        .anyRequest().permitAll()  // 允许所有请求，无需springboot身份认证
//                )
//                .csrf(csrf -> csrf.disable());  // 禁用 CSRF 防护（如果需要）
//
//        return http.build();
//    }

}
