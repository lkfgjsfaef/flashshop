package org.javaup.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 单元测试基类
 * 使用Mockito严格模式，允许未使用的stub（提升测试可读性）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BaseUnitTest {

    @BeforeEach
    void setUp() {
        // 子类可重写此方法进行额外初始化
    }
}