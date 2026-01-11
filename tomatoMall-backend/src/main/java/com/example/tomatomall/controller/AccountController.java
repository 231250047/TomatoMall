package com.example.tomatomall.controller;

import com.example.tomatomall.service.AccountService;

import com.example.tomatomall.vo.AccountVO;
import com.example.tomatomall.vo.PartAccountVO;
import com.example.tomatomall.vo.Response;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Resource
    AccountService accountService;

    /**
     * 获取用户详情
     * 注意此处只显示部分用户信息
     * 使用PartAccountVO实现
     */
    @GetMapping("/{username}")
    public Response<PartAccountVO> getUser(@PathVariable("username") String username) {
        PartAccountVO account = accountService.getUserByName(username);
        return Response.buildSuccess(account);
    }
    @GetMapping()
    public Response<PartAccountVO> getUser() {
        PartAccountVO account = accountService.getUser();
        return Response.buildSuccess(account);
    }
    /**
     * 创建新的用户
     */
    @PostMapping()
    public Response<String> createUser(@RequestBody AccountVO accountVO) {
//        if (result == false) {
//            return Response.buildFailure("用户名已存在", "400");
//        }
        return Response.buildSuccess(accountService.createUser(accountVO));
    }

    /**
     * 更新用户信息
     */
    @PutMapping()
    public Response<String> updateUser(@RequestBody AccountVO accountVO) {
        return Response.buildSuccess(accountService.updateUser(accountVO));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Response<String> login(@RequestBody Map<String, String> loginRequest) {
        // 获取用户名和密码
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        return Response.buildSuccess(accountService.login(username, password));
    }
}
