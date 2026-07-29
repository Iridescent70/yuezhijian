package com.yuezhijian.server.iam;

import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Profile("sqlserver")
public class DatabaseUserDetailsService implements UserDetailsService {
    private final AccessCatalogMapper mapper;

    public DatabaseUserDetailsService(AccessCatalogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AccessUserAccount account = mapper.findUserByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        String[] authorities = Stream.concat(
                        mapper.findRoleCodesByUserId(account.id()).stream().map(code -> "ROLE_" + code),
                        mapper.findPermissionCodesByUserId(account.id()).stream())
                .distinct()
                .toArray(String[]::new);
        return User.withUsername(account.username())
                .password(account.passwordHash())
                .authorities(authorities)
                .disabled(!"ACTIVE".equals(account.status()))
                .accountLocked(account.lockedAt() != null)
                .build();
    }
}
