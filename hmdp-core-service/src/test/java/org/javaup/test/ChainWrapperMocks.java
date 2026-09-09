package org.javaup.test;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * MyBatis-Plus 链式调用（ChainWrapper）的 mock 工具。
 *
 * 背景：Service 里的链式写法返回的是 ChainWrapper 系列，而不是 QueryWrapper 系列：
 *
 * <pre>
 *   service.query()        → QueryChainWrapper&lt;T&gt;
 *   service.lambdaQuery()  → LambdaQueryChainWrapper&lt;T&gt;
 *   service.update()       → UpdateChainWrapper&lt;T&gt;
 *   service.lambdaUpdate() → LambdaUpdateChainWrapper&lt;T&gt;
 * </pre>
 *
 * 两者很容易搞混：QueryWrapper 只是「条件构造器」，没有 list()/one()/count()；
 * ChainWrapper 才是「条件构造器 + 执行器」。早前测试里自己 new 了一堆 MockXxxWrapper
 * 去 extends QueryWrapper，导致类型对不上、@Override 也找不到父方法。
 *
 * 这里统一按真实类型 mock，并用自定义 Answer 实现：
 *   1. 链式中间方法（eq/orderBy/last/set ...）返回 mock 自身，保证调用链不断裂；
 *   2. 终结方法（list/one/count/update/remove）按传入的 result 返回预设值。
 */
public final class ChainWrapperMocks {

    private ChainWrapperMocks() {
    }

    // ==================== query() ====================

    @SuppressWarnings("unchecked")
    public static <T> QueryChainWrapper<T> queryChain(Object result) {
        AtomicReference<QueryChainWrapper<T>> self = new AtomicReference<>();
        Answer<Object> answer = chainAnswer(self, result);
        QueryChainWrapper<T> wrapper = mock(QueryChainWrapper.class, withSettings().defaultAnswer(answer));
        self.set(wrapper);
        return wrapper;
    }

    // ==================== lambdaQuery() ====================

    @SuppressWarnings("unchecked")
    public static <T> LambdaQueryChainWrapper<T> lambdaQueryChain(Object result) {
        AtomicReference<LambdaQueryChainWrapper<T>> self = new AtomicReference<>();
        Answer<Object> answer = chainAnswer(self, result);
        LambdaQueryChainWrapper<T> wrapper = mock(LambdaQueryChainWrapper.class, withSettings().defaultAnswer(answer));
        self.set(wrapper);
        return wrapper;
    }

    // ==================== update() ====================

    @SuppressWarnings("unchecked")
    public static <T> UpdateChainWrapper<T> updateChain(boolean result) {
        AtomicReference<UpdateChainWrapper<T>> self = new AtomicReference<>();
        Answer<Object> answer = chainAnswer(self, result);
        UpdateChainWrapper<T> wrapper = mock(UpdateChainWrapper.class, withSettings().defaultAnswer(answer));
        self.set(wrapper);
        return wrapper;
    }

    // ==================== lambdaUpdate() ====================

    @SuppressWarnings("unchecked")
    public static <T> LambdaUpdateChainWrapper<T> lambdaUpdateChain(boolean result) {
        AtomicReference<LambdaUpdateChainWrapper<T>> self = new AtomicReference<>();
        Answer<Object> answer = chainAnswer(self, result);
        LambdaUpdateChainWrapper<T> wrapper = mock(LambdaUpdateChainWrapper.class, withSettings().defaultAnswer(answer));
        self.set(wrapper);
        return wrapper;
    }

    // ==================== 核心：自定义 Answer ====================

    /**
     * @param result 终结方法要返回的数据，语义按类型自动推断：
     *               - List    → list() 返回它，one() 返回首个元素，count() 返回 size
     *               - Number  → count() 返回其 long 值（「查数量」场景专用）
     *               - Boolean → update()/remove() 返回它
     *               - null    → list() 空列表、one() null、count() 0
     *               - 其他    → 单个实体：list() 返回只含它的列表，one() 返回它，count() 返回 1
     */
    private static Answer<Object> chainAnswer(AtomicReference<?> selfRef, Object result) {
        return invocation -> {
            String name = invocation.getMethod().getName();
            Class<?> returnType = invocation.getMethod().getReturnType();
            Object self = selfRef.get();

            // 1) 终结方法优先 —— 必须放在「返回自身」判断之前。
            //    因为 one() 泛型擦除后返回 Object，会被 rt.isInstance(self) 误判成链式方法。
            switch (name) {
                case "list":
                    return asList(result);
                case "one":
                    return asOne(result);
                case "count":
                    return asCount(result);
                case "update":
                case "remove":
                    return asBoolean(result);
                case "exists":
                    return asCount(result) > 0;
                case "page":
                    // 分页查询：query().orderByDesc("liked").page(new Page<>(...))
                    // 如果外部传进来的就是 IPage，直接返回它；否则给一个空页
                    return asPage(result);
                default:
                    break;
            }

            // 2) 返回自身类型的方法 = 链式中间方法，返回 mock 自身
            if (self != null && returnType.isInstance(self)) {
                return self;
            }

            // 3) 其余给安全的默认值，避免 NPE / 拆箱异常
            if (returnType == boolean.class) return false;
            if (returnType == long.class) return 0L;
            if (returnType == int.class) return 0;
            if (returnType == List.class) return Collections.emptyList();
            return null;
        };
    }

    // ==================== 结果转换 ====================

    /**
     * 分页结果。
     * 传进来的 result 本身就是 IPage 时直接返回（分页场景最常用）；
     * 否则包一个只含 result 的单元素页，保证 page.getRecords() 不为空指针。
     */
    private static Object asPage(Object result) {
        if (result instanceof com.baomidou.mybatisplus.core.metadata.IPage) {
            return result;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Object> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        page.setRecords(asList(result));
        page.setTotal(asList(result).size());
        return page;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object result) {
        if (result == null) return Collections.emptyList();
        if (result instanceof List) return (List<Object>) result;
        if (result instanceof com.baomidou.mybatisplus.core.metadata.IPage) {
            return (List<Object>) ((com.baomidou.mybatisplus.core.metadata.IPage<?>) result).getRecords();
        }
        return Collections.singletonList(result);
    }

    private static Object asOne(Object result) {
        if (result == null) return null;
        if (result instanceof List) {
            List<?> list = (List<?>) result;
            return list.isEmpty() ? null : list.get(0);
        }
        // 数量 / 布尔值不是实体对象，不参与 one() 语义
        if (result instanceof Number || result instanceof Boolean) return null;
        return result;
    }

    private static long asCount(Object result) {
        if (result == null) return 0L;
        if (result instanceof Number) return ((Number) result).longValue();
        if (result instanceof List) return ((List<?>) result).size();
        return 1L;
    }

    private static boolean asBoolean(Object result) {
        if (result instanceof Boolean) return (Boolean) result;
        if (result instanceof Number) return ((Number) result).longValue() > 0;
        return result != null;
    }
}
