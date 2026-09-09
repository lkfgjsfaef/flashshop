package org.javaup.api;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.javaup.dto.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * API自动化测试基类
 * 使用REST Assured进行接口测试
 * 注意：此测试需要启动完整的Spring上下文和外部依赖（MySQL、Redis等）
 * 适合在CI/CD环境中运行
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseApiTest {

    @LocalServerPort
    protected int port;

    protected static final String BASE_URL = "http://localhost";

    @BeforeAll
    void setUp() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.port = port;
    }

    /**
     * 发送GET请求
     */
    protected Response get(String path) {
        return given()
                .contentType(ContentType.JSON)
                .when()
                .get(path);
    }

    /**
     * 发送GET请求（带路径参数，如 /shop/{id}）
     */
    protected Response get(String path, Object... pathParams) {
        return given()
                .contentType(ContentType.JSON)
                .when()
                .get(path, pathParams);
    }

    /**
     * 发送GET请求（带查询参数）
     */
    protected Response getWithQueryParam(String path, String paramName, Object paramValue) {
        return given()
                .contentType(ContentType.JSON)
                .queryParam(paramName, paramValue)
                .when()
                .get(path);
    }

    /**
     * 发送POST请求
     */
    protected Response post(String path, Object body) {
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(path);
    }

    /**
     * 发送PUT请求
     */
    protected Response put(String path, Object body) {
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put(path);
    }

    /**
     * 发送DELETE请求
     */
    protected Response delete(String path) {
        return given()
                .contentType(ContentType.JSON)
                .when()
                .delete(path);
    }

    /**
     * 验证响应状态码
     */
    protected void assertStatusCode(Response response, int expectedStatusCode) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatusCode);
    }

    /**
     * 验证响应成功（ok=true）
     */
    protected void assertSuccess(Response response) {
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getBoolean("ok")).isTrue();
    }

    /**
     * 验证响应失败（ok=false）
     */
    protected void assertFailure(Response response) {
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getBoolean("ok")).isFalse();
    }

    /**
     * 验证响应失败并包含错误信息
     */
    protected void assertFailureWithMessage(Response response, String expectedMessage) {
        assertFailure(response);
        assertThat(response.jsonPath().getString("errorMsg")).isEqualTo(expectedMessage);
    }

    /**
     * 获取响应中的data字段
     */
    protected <T> T getData(Response response, Class<T> clazz) {
        return response.jsonPath().getObject("data", clazz);
    }

    /**
     * 获取响应中的data字段（列表）
     */
    protected <T> java.util.List<T> getDataList(Response response, Class<T> clazz) {
        return response.jsonPath().getList("data", clazz);
    }
}