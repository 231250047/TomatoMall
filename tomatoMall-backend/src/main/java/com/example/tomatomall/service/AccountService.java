package com.example.tomatomall.service;

import com.example.tomatomall.vo.AccountVO;
import com.example.tomatomall.vo.PartAccountVO;

public interface AccountService {
    String createUser(AccountVO accountVO);

    String login(String username, String password);

    PartAccountVO getUser();

    PartAccountVO getUser(String username); // 新增:根�?username 获取部分用户信息

    PartAccountVO getUserByName(String name); // 新增:根�?name 获取部分用户信息

    String updateUser(AccountVO accountVO);
}
