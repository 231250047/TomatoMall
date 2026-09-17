package com.example.tomatomall.retrieval;
import com.example.tomatomall.Util.SecurityUtil;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.repository.AccountRepository;
import com.example.tomatomall.service.serviceImpl.AccountServiceImpl;
import com.example.tomatomall.vo.AccountVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class IndexAdminBoundaryTest {
    AccountRepository repository=mock(AccountRepository.class);
    SecurityUtil security=mock(SecurityUtil.class);
    AccountServiceImpl service=new AccountServiceImpl();
    IndexAdminBoundaryTest() {
        ReflectionTestUtils.setField(service,"accountRepository",repository);
        ReflectionTestUtils.setField(service,"securityUtil",security);
        var encoder=mock(PasswordEncoder.class);when(encoder.encode(anyString())).thenReturn("encoded");
        ReflectionTestUtils.setField(service,"passwordEncoder",encoder);
    }
    @Test void publicRegistrationCannotCreateIndexAdministrator() {
        AccountVO request=new AccountVO();request.setUsername("test");request.setPassword("test");request.setRole("admin");
        var captured=org.mockito.ArgumentCaptor.forClass(Account.class);
        service.createUser(request);verify(repository).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getRole()).isEqualTo("user");
    }
    @Test void profileUpdateCannotElevateRole() {
        Account existing=new Account();existing.setId(1);existing.setRole("user");
        when(security.getCurrentAccount()).thenReturn(existing);when(repository.findById(1)).thenReturn(Optional.of(existing));
        AccountVO request=new AccountVO();request.setRole("admin");
        service.updateUser(request);
        assertThat(existing.getRole()).isEqualTo("user");
    }
}
