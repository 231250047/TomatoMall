package com.example.tomatomall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

//@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
// The default PgVector auto-configuration would use the commerce MySQL DataSource.
// ProductVectorIndex owns its separate PostgreSQL connection pool instead.
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class,
        org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration.class})
@EnableJpaRepositories(basePackages = "com.example.tomatomall.repository")
@EntityScan(basePackages = "com.example.tomatomall.po")
public class TomatoMallApplication {
    public static void main(String[] args) {
        SpringApplication.run(TomatoMallApplication.class, args);
    }

}
