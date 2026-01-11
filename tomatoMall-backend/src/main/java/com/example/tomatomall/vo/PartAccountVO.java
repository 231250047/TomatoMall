package com.example.tomatomall.vo;

import com.example.tomatomall.po.Account;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用于在用户登录成功后返回给前端的用户信息，隐藏部分敏感信息
 */

@Getter
@Setter
@NoArgsConstructor
public class PartAccountVO {
    private Integer id;
    private String username;
//    private String password;
    private String name;
    private String avatar;
    private String role;
    private String telephone;
    private String email;
    private String location;

    public PartAccountVO(Account account) {
        this.id = account.getId();
        this.username = account.getUsername();
//        this.password = account.getPassword();
        this.name = account.getName();
        this.avatar = account.getAvatar();
        this.role = account.getRole();
        this.telephone = account.getTelephone();
        this.email = account.getEmail();
        this.location = account.getLocation();
    }

}
