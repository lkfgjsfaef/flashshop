package org.javaup.test;

import cn.hutool.core.date.LocalDateTimeUtil;
import org.javaup.dto.CancelVoucherOrderDto;
import org.javaup.dto.GetVoucherOrderDto;
import org.javaup.dto.LoginFormDTO;
import org.javaup.dto.UserDTO;
import org.javaup.entity.*;
import org.javaup.enums.OrderStatus;
import org.javaup.model.SeckillVoucherFullModel;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试数据工厂 — 统一构造测试数据，避免重复代码
 */
public final class TestDataFactory {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private TestDataFactory() {
        // 工具类，禁止实例化
    }

    // ==================== 实体构造 ====================

    public static User createUser() {
        return createUser(nextId());
    }

    public static User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setNickName("测试用户_" + id);
        user.setPhone("13800138000");
        user.setIcon("https://example.com/avatar.png");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return user;
    }

    public static UserInfo createUserInfo(Long userId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(nextId());
        userInfo.setUserId(userId);
        userInfo.setLevel(1);
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setUpdateTime(LocalDateTime.now());
        return userInfo;
    }

    public static UserInfo createUserInfoWithLevel(Long userId, Integer level) {
        UserInfo info = createUserInfo(userId);
        info.setLevel(level);
        return info;
    }

    public static Shop createShop() {
        return createShop(nextId());
    }

    public static Shop createShop(Long id) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName("测试店铺_" + id);
        // Shop 实体只有 typeId 字段，没有 type —— 早期这里多写了一个 setType
        shop.setTypeId(1L);
        shop.setAddress("测试地址");
        shop.setX(116.397128);
        shop.setY(39.916527);
        shop.setCreateTime(LocalDateTime.now());
        shop.setUpdateTime(LocalDateTime.now());
        return shop;
    }

    public static Voucher createVoucher() {
        return createVoucher(nextId());
    }

    public static Voucher createVoucher(Long id) {
        Voucher voucher = new Voucher();
        voucher.setId(id);
        voucher.setShopId(nextId());
        voucher.setTitle("测试优惠券");
        voucher.setSubTitle("满100减10");
        voucher.setRules("全场通用");
        voucher.setPayValue(9000L);
        voucher.setActualValue(10000L);
        voucher.setType(1);
        voucher.setStatus(1);
        voucher.setCreateTime(LocalDateTime.now());
        voucher.setUpdateTime(LocalDateTime.now());
        return voucher;
    }

    public static SeckillVoucher createSeckillVoucher(Long voucherId) {
        SeckillVoucher sv = new SeckillVoucher();
        sv.setId(nextId());
        sv.setVoucherId(voucherId);
        sv.setStock(100);
        sv.setBeginTime(LocalDateTime.now().minusHours(1));
        sv.setEndTime(LocalDateTime.now().plusHours(1));
        sv.setCreateTime(LocalDateTime.now());
        sv.setUpdateTime(LocalDateTime.now());
        return sv;
    }

    public static SeckillVoucherFullModel createSeckillVoucherFullModel(Long voucherId) {
        SeckillVoucherFullModel model = new SeckillVoucherFullModel();
        model.setVoucherId(voucherId);
        model.setShopId(nextId());
        model.setStock(100);
        model.setBeginTime(LocalDateTimeUtil.now().minusHours(1));
        model.setEndTime(LocalDateTimeUtil.now().plusHours(1));
        model.setStatus(1);
        return model;
    }

    public static VoucherOrder createVoucherOrder(Long userId, Long voucherId) {
        VoucherOrder order = new VoucherOrder();
        order.setId(nextId());
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(OrderStatus.NORMAL.getCode());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }

    public static Follow createFollow(Long userId, Long followUserId) {
        Follow follow = new Follow();
        follow.setId(nextId());
        follow.setUserId(userId);
        follow.setFollowUserId(followUserId);
        follow.setCreateTime(LocalDateTime.now());
        return follow;
    }

    public static Blog createBlog(Long userId) {
        Blog blog = new Blog();
        blog.setId(nextId());
        blog.setUserId(userId);
        blog.setTitle("测试笔记");
        blog.setContent("测试内容");
        blog.setLiked(0);
        blog.setCreateTime(LocalDateTime.now());
        blog.setUpdateTime(LocalDateTime.now());
        return blog;
    }

    // ==================== DTO构造 ====================

    public static UserDTO createUserDTO(Long id) {
        UserDTO dto = new UserDTO();
        dto.setId(id);
        dto.setNickName("测试用户_" + id);
        dto.setIcon("https://example.com/avatar.png");
        return dto;
    }

    public static LoginFormDTO createLoginForm(String phone, String code) {
        LoginFormDTO form = new LoginFormDTO();
        form.setPhone(phone);
        form.setCode(code);
        return form;
    }

    public static CancelVoucherOrderDto createCancelVoucherOrderDto(Long voucherId) {
        CancelVoucherOrderDto dto = new CancelVoucherOrderDto();
        dto.setVoucherId(voucherId);
        return dto;
    }

    public static GetVoucherOrderDto createGetVoucherOrderDto(Long orderId) {
        GetVoucherOrderDto dto = new GetVoucherOrderDto();
        dto.setOrderId(orderId);
        return dto;
    }

    // ==================== 实体构造（补充） ====================

    public static RollbackFailureLog createRollbackFailureLog(Long voucherId, Long userId, Long orderId) {
        RollbackFailureLog log = new RollbackFailureLog();
        log.setId(nextId());
        log.setVoucherId(voucherId);
        log.setUserId(userId);
        log.setOrderId(orderId);
        log.setTraceId(nextId());
        log.setDetail("测试回滚失败详情");
        log.setResultCode(10005);
        log.setRetryAttempts(3);
        log.setSource("redis_voucher_data");
        log.setCreateTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        return log;
    }

    public static VoucherReconcileLog createVoucherReconcileLog(Long orderId, Long userId, Long voucherId) {
        VoucherReconcileLog log = new VoucherReconcileLog();
        log.setId(nextId());
        log.setOrderId(orderId);
        log.setUserId(userId);
        log.setVoucherId(voucherId);
        log.setMessageId("msg-" + nextId());
        log.setDetail("测试对账日志");
        log.setBeforeQty(100);
        log.setChangeQty(-1);
        log.setAfterQty(99);
        log.setTraceId(nextId());
        log.setLogType(-1); // DEDUCT
        log.setBusinessType(1);
        log.setReconciliationStatus(1);
        log.setCreateTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        return log;
    }

    public static VoucherOrderRouter createVoucherOrderRouter(Long userId, Long voucherId, Long orderId) {
        VoucherOrderRouter router = new VoucherOrderRouter();
        router.setId(nextId());
        router.setUserId(userId);
        router.setVoucherId(voucherId);
        router.setOrderId(orderId);
        router.setCreateTime(LocalDateTime.now());
        router.setUpdateTime(LocalDateTime.now());
        return router;
    }

    // ==================== 工具方法 ====================

    public static Long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }

    public static void resetIdGenerator() {
        ID_GENERATOR.set(1);
    }
}