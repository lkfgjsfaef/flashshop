package org.javaup.test;

import org.javaup.mapper.BlogCommentsMapper;
import org.javaup.mapper.BlogMapper;
import org.javaup.mapper.FollowMapper;
import org.javaup.mapper.RollbackFailureLogMapper;
import org.javaup.mapper.SeckillVoucherMapper;
import org.javaup.mapper.ShopMapper;
import org.javaup.mapper.ShopTypeMapper;
import org.javaup.mapper.UserInfoMapper;
import org.javaup.mapper.UserMapper;
import org.javaup.mapper.UserPhoneMapper;
import org.javaup.mapper.VoucherMapper;
import org.javaup.mapper.VoucherOrderMapper;
import org.javaup.mapper.VoucherOrderRouterMapper;
import org.javaup.mapper.VoucherReconcileLogMapper;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 为 @WebMvcTest 切片测试提供全套 Mapper 的 mock。
 *
 * 为什么需要这个类：
 * 启动类 HmDianPingApplication 上打了 @MapperScan，@WebMvcTest 又是以该启动类为入口的，
 * 于是所有 Mapper 接口都会被注册成 Bean；但切片测试不会装 MyBatis 的自动配置，
 * 没有 sqlSessionFactory，容器一启动就报
 * "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required"。
 * 这里把 14 个 Mapper 全部替换成 Mockito mock，切片测试就能只加载 Web 层。
 */
@TestConfiguration
public class MockMapperConfig {

    @Bean
    public BlogCommentsMapper blogCommentsMapper() {
        return Mockito.mock(BlogCommentsMapper.class);
    }

    @Bean
    public BlogMapper blogMapper() {
        return Mockito.mock(BlogMapper.class);
    }

    @Bean
    public FollowMapper followMapper() {
        return Mockito.mock(FollowMapper.class);
    }

    @Bean
    public RollbackFailureLogMapper rollbackFailureLogMapper() {
        return Mockito.mock(RollbackFailureLogMapper.class);
    }

    @Bean
    public SeckillVoucherMapper seckillVoucherMapper() {
        return Mockito.mock(SeckillVoucherMapper.class);
    }

    @Bean
    public ShopMapper shopMapper() {
        return Mockito.mock(ShopMapper.class);
    }

    @Bean
    public ShopTypeMapper shopTypeMapper() {
        return Mockito.mock(ShopTypeMapper.class);
    }

    @Bean
    public UserInfoMapper userInfoMapper() {
        return Mockito.mock(UserInfoMapper.class);
    }

    @Bean
    public UserMapper userMapper() {
        return Mockito.mock(UserMapper.class);
    }

    @Bean
    public UserPhoneMapper userPhoneMapper() {
        return Mockito.mock(UserPhoneMapper.class);
    }

    @Bean
    public VoucherMapper voucherMapper() {
        return Mockito.mock(VoucherMapper.class);
    }

    @Bean
    public VoucherOrderMapper voucherOrderMapper() {
        return Mockito.mock(VoucherOrderMapper.class);
    }

    @Bean
    public VoucherOrderRouterMapper voucherOrderRouterMapper() {
        return Mockito.mock(VoucherOrderRouterMapper.class);
    }

    @Bean
    public VoucherReconcileLogMapper voucherReconcileLogMapper() {
        return Mockito.mock(VoucherReconcileLogMapper.class);
    }
}
