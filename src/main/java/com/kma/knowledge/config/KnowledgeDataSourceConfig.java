package com.kma.knowledge.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 知识库独立 PostgreSQL 数据源配置
 * <p>
 * 仅在 {@code knowledge.enabled=true} 时生效，避免 PG 未就绪时影响整个应用启动。
 * <p>
 * 注意：本配置类中的所有 {@code @Bean} 方法内部均通过同配置类方法调用获取
 * {@code knowledgeDataSource}，确保不会被 Spring 的 {@code @Primary} 主数据源覆盖。
 *
 * @author party
 * @date 2026/06/30
 */
@Configuration
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@MapperScan(
    basePackages = "com.kma.knowledge.mapper",
    sqlSessionFactoryRef = "knowledgeSqlSessionFactory"
)
public class KnowledgeDataSourceConfig {

    /**
     * 知识库数据源（HikariCP）
     */
    @Bean(name = "knowledgeDataSource")
    @ConfigurationProperties(prefix = "knowledge.datasource")
    public DataSource knowledgeDataSource() {
        return new HikariDataSource();
    }

    /**
     * 知识库独立 SqlSessionFactory
     * <p>
     * 显式配置 PostgreSQL 方言、乐观锁与防全表操作插件。
     */
    @Bean
    public SqlSessionFactory knowledgeSqlSessionFactory() throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        // 通过同配置类方法调用，避免 Spring 按类型注入时命中 @Primary 主数据源
        bean.setDataSource(knowledgeDataSource());

        // 加载 knowledge 模块的 mapper.xml（如后续需要 XML 方式编写 SQL）
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        bean.setMapperLocations(resolver.getResources("classpath*:mapper/knowledge/**/*.xml"));

        // 独立 MyBatis-Plus 插件：PostgreSQL 分页 + 乐观锁 + 防全表更新/删除
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        bean.setPlugins(interceptor);

        return bean.getObject();
    }

    /**
     * 知识库事务管理器
     */
    @Bean
    public PlatformTransactionManager knowledgeTransactionManager() {
        // 通过同配置类方法调用，确保使用 knowledgeDataSource 单例
        return new DataSourceTransactionManager(knowledgeDataSource());
    }

    @Bean(name = "knowledgeTransactionTemplate")
    public TransactionTemplate knowledgeTransactionTemplate() {
        return new TransactionTemplate(knowledgeTransactionManager());
    }

    @Bean(name = "knowledgeJdbcTemplate")
    public JdbcTemplate knowledgeJdbcTemplate() {
        return new JdbcTemplate(knowledgeDataSource());
    }

    /**
     * 知识库 SqlSessionTemplate
     */
    @Bean
    public SqlSessionTemplate knowledgeSqlSessionTemplate(SqlSessionFactory knowledgeSqlSessionFactory) {
        return new SqlSessionTemplate(knowledgeSqlSessionFactory);
    }
}



