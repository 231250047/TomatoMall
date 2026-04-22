package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.Util.SecurityUtil;
import com.example.tomatomall.Util.TokenUtil;
import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.AccountRepository;
import com.example.tomatomall.service.AccountService;
import com.example.tomatomall.vo.AccountVO;
import com.example.tomatomall.vo.PartAccountVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    SecurityUtil securityUtil;

    @Autowired
    TokenUtil tokenUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;  // 自动注入 BCryptPasswordEncoder 实现


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createUser(AccountVO accountVO) {
        // 先做快速检查以尽早返回，但依赖 DB 唯一约束作为最终防�?
        Account account = accountRepository.findByUsername(accountVO.getUsername());
        if(account != null) {
            throw TomatoMallException.usernameAlreadyExists();
        }
        Account newAccount = accountVO.toPO();
        String rawPassword = newAccount.getPassword();
        String encodedPassword = passwordEncoder.encode(rawPassword);
        newAccount.setPassword(encodedPassword);

        try {
            // 使用 saveAndFlush 以便尽早触发唯一约束异常(若存在�?
            accountRepository.saveAndFlush(newAccount);
            return "注册成功";
        } catch (DataIntegrityViolationException ex) {
            // 转换为业务异常，事务会回�?
            throw TomatoMallException.usernameAlreadyExists();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String login(String username, String password) {
        Account account = accountRepository.findByUsername(username);
        if(account == null) {
            throw TomatoMallException.usernameError();
        }
        if(!passwordEncoder.matches(password, account.getPassword())) {
            throw TomatoMallException.passwordError();
        }
        return tokenUtil.getToken(account);
    }

    @Override
    @Transactional(readOnly = true)
    public PartAccountVO getUser() {
        Account account = securityUtil.getCurrentAccount();
        if(account == null) {
            throw TomatoMallException.notLogin();
        }
        return new PartAccountVO(account);
    }

    @Override
    @Transactional(readOnly = true)
    public PartAccountVO getUser(String username) {
        Account account = accountRepository.findByUsername(username);
        if (account == null) {
            throw TomatoMallException.usernameError();
        }
        return new PartAccountVO(account);
    }

    @Override
    @Transactional(readOnly = true)
    public PartAccountVO getUserByName(String name) {
        Account account = accountRepository.findByName(name);
        if (account == null) {
            throw TomatoMallException.usernameError(); // 可根据需要替换为更合适的异常
        }
        return new PartAccountVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateUser(AccountVO accountVO) {
        Account current = securityUtil.getCurrentAccount();
        if(current == null) {
            throw TomatoMallException.notLogin();
        }

        // 重新从数据库加载受管实体，避免使用可能为 detached �?securityUtil 对象，保证在同一事务内变更并 flush
        Optional<Account> optional = accountRepository.findById(current.getId());
        Account account = optional.orElseThrow(() -> TomatoMallException.notLogin());

        try {
            if(accountVO.getAvatar() != null
                    && !accountVO.getAvatar().isEmpty()) {
                account.setAvatar(accountVO.getAvatar());
            }
            if(accountVO.getUsername() != null
                    && !accountVO.getUsername().equals(account.getUsername())
                    && !accountVO.getUsername().isEmpty()) {
                // 显式检查用户名是否被占�?
                Account exist = accountRepository.findByUsername(accountVO.getUsername());
                if (exist != null && !exist.getId().equals(account.getId())) {
                    throw TomatoMallException.usernameAlreadyExists();
                }
                account.setUsername(accountVO.getUsername());
            }
            if(accountVO.getPassword() != null
                    && !accountVO.getPassword().isEmpty()) {

                String rawPassword = accountVO.getPassword();
                String encodedPassword = passwordEncoder.encode(rawPassword);
                account.setPassword(encodedPassword);
            }
            if(accountVO.getName() != null
                    && !accountVO.getName().equals(account.getName())
                    && !accountVO.getName().isEmpty()) {
                account.setName(accountVO.getName());
            }
            if(accountVO.getRole() != null
                    && !accountVO.getRole().isEmpty()) {
                account.setRole(accountVO.getRole());
            }
            if(accountVO.getTelephone() != null
                    && !accountVO.getTelephone().equals(account.getTelephone())
                    && !accountVO.getTelephone().isEmpty()) {
                account.setTelephone(accountVO.getTelephone());
            }
            if(accountVO.getEmail() != null
                    && !accountVO.getEmail().equals(account.getEmail())
                    && !accountVO.getEmail().isEmpty()) {
                account.setEmail(accountVO.getEmail());
            }
            if(accountVO.getLocation() != null
                    && !accountVO.getLocation().equals(account.getLocation())
                    && !accountVO.getLocation().isEmpty()) {
                account.setLocation(accountVO.getLocation());
            }

            // 保存并立�?flush，若实体上有 @Version 会触发乐观锁异常
            accountRepository.saveAndFlush(account);
            return "更新成功";
        } catch (OptimisticLockingFailureException e) {
            // 乐观锁冲�?-> 转换为业务级异常，调用者可提示重试
            throw TomatoMallException.concurrentUpdate();
        } catch (DataIntegrityViolationException e) {
            // 唯一约束�?DB 级别异常
            throw TomatoMallException.usernameAlreadyExists();
        }
    }
}
