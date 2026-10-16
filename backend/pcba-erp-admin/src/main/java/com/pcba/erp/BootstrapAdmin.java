package com.pcba.erp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bootstrap a fresh database only; existing accounts are never reset. */
@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password;

    public BootstrapAdmin(JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${pcba.bootstrap.admin-password:}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.execute("SELECT pg_advisory_xact_lock(724120261015)");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class) != 0) return;
        if (password == null || password.length() < 16 || password.startsWith("replace-")) {
            throw new IllegalStateException("首次初始化必须配置至少 16 位的 BOOTSTRAP_ADMIN_PASSWORD");
        }
        jdbc.update("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, password, status, del_flag, create_by) "
                + "VALUES (1, 100, 'admin', '管理员', ?, 1, '0', 'bootstrap')", encoder.encode(password));
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (1, 1) ON CONFLICT DO NOTHING");
        jdbc.execute("SELECT setval(pg_get_serial_sequence('sys_user','user_id'), "
                + "GREATEST((SELECT MAX(user_id) FROM sys_user), 1))");
    }
}
