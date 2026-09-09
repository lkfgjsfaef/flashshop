package org.javaup.service.impl;

import org.javaup.mapper.VoucherOrderRouterMapper;

import org.javaup.dto.GetVoucherOrderRouterDto;
import org.javaup.dto.UserDTO;
import org.javaup.entity.VoucherOrderRouter;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.ChainWrapperMocks;
import org.javaup.test.TestDataFactory;
import org.javaup.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VoucherOrderRouterServiceImpl 单元测试
 * 覆盖场景：查询秒杀订单路由（存在/不存在）
 */
class VoucherOrderRouterServiceImplTest extends BaseUnitTest {
    // ServiceImpl 的 baseMapper：不注入的话，走到 getById/list/save 等
    // 真实 DAO 方法时会抛 "baseMapper can not be null"
    @Mock
    private VoucherOrderRouterMapper voucherOrderRouterMapper;


    @Spy
    @InjectMocks
    private VoucherOrderRouterServiceImpl voucherOrderRouterService;

    private Long userId = 100L;
    private Long voucherId = 1L;
    private Long orderId = 200L;

    @BeforeEach
    void setUp() {
        UserHolder.saveUser(TestDataFactory.createUserDTO(userId));
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==================== get 测试 ====================

    @Nested
    @DisplayName("get - 查询秒杀订单路由")
    class GetTests {

        @Test
        @DisplayName("路由记录存在 → 返回订单ID")
        void should_returnOrderId_when_routerExists() {
            // given
            VoucherOrderRouter router = TestDataFactory.createVoucherOrderRouter(userId, voucherId, orderId);
            GetVoucherOrderRouterDto dto = new GetVoucherOrderRouterDto();
            dto.setVoucherId(voucherId);

            doReturn(ChainWrapperMocks.lambdaQueryChain(List.of(router)))
                    .when(voucherOrderRouterService).lambdaQuery();

            // when
            Long result = voucherOrderRouterService.get(dto);

            // then
            assertThat(result).isEqualTo(orderId);
        }

        @Test
        @DisplayName("路由记录不存在 → 返回null")
        void should_returnNull_when_routerNotFound() {
            // given
            GetVoucherOrderRouterDto dto = new GetVoucherOrderRouterDto();
            dto.setVoucherId(voucherId);

            doReturn(ChainWrapperMocks.lambdaQueryChain(Collections.emptyList()))
                    .when(voucherOrderRouterService).lambdaQuery();

            // when
            Long result = voucherOrderRouterService.get(dto);

            // then
            assertThat(result).isNull();
        }
    }

    // ==================== Mock辅助类 ====================

}