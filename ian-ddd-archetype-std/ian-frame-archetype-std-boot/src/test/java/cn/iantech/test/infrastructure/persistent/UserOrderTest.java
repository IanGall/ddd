package cn.iantech.test.infrastructure.persistent;

import cn.iantech.domain.user.model.entity.UserOrderBO;
import cn.iantech.infrastructure.persistent.dao.IUserOrderDao;
import cn.iantech.infrastructure.persistent.po.UserOrderPO;
import io.github.linpeilie.Converter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 单元测试
 */
@Slf4j
@SpringBootTest
@ActiveProfiles({"dev", "autotest"})
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
public class UserOrderTest {

    @Resource
    private IUserOrderDao userOrderDao;
    @Resource
    private Converter converter;

    // 验证根据用户 ID 查询订单
    @Test
    public void shouldSelectOrdersByUserId() {
        List<UserOrderPO> list = userOrderDao.selectByUserId("ian_FOawiP");
        log.info("测试结果：{}", list);
        UserOrderPO po = list.getFirst();
        UserOrderBO bo = converter.convert(po, UserOrderBO.class);

        Assertions.assertNotNull(bo);
        Assertions.assertEquals(bo.getUserId(), po.getUserId());
        Assertions.assertEquals(bo.getSku(), po.getSku());
        Assertions.assertEquals(bo.getOrderId(), po.getOrderId());
        Assertions.assertEquals(bo.getTotalAmount(), po.getTotalAmount());
    }

    // 验证批量新增用户订单
    @Test
    public void shouldInsertUserOrders() {
        for (int i = 0; i < 10; i++) {
            UserOrderPO userOrderPO = UserOrderPO.builder()
                    .userName("测试用户")
                    .userId("ian_" + RandomStringUtils.randomAlphabetic(6))
                    .userMobile("+86 13800000000")
                    .sku("SKU-100001")
                    .skuName("示例商品")
                    .orderId(RandomStringUtils.randomNumeric(11))
                    .quantity(1)
                    .unitPrice(BigDecimal.valueOf(128))
                    .discountAmount(BigDecimal.valueOf(50))
                    .tax(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.valueOf(78))
                    .orderDate(LocalDateTime.now())
                    .orderStatus(0)
                    .isDelete(0)
                    .uuid(UUID.randomUUID().toString().replace("-", ""))
                    .ipv4("127.0.0.1")
                    .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334".getBytes())
                    .extData("{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}")
                    .build();

            userOrderDao.insert(userOrderPO);
        }
    }

    // 验证持久化对象能够转换为领域对象
    @Test
    public void shouldConvertUserOrderPoToBo() {
        UserOrderPO po = UserOrderPO.builder()
                .userId("ian_test_001")
                .sku("sku_1001")
                .orderId("order_20260214")
                .totalAmount(BigDecimal.valueOf(88.90))
                .build();

        UserOrderBO bo = converter.convert(po, UserOrderBO.class);
        Assertions.assertNotNull(bo);
        Assertions.assertEquals(po.getUserId(), bo.getUserId());
        Assertions.assertEquals(po.getSku(), bo.getSku());
        Assertions.assertEquals(po.getOrderId(), bo.getOrderId());
        Assertions.assertEquals(po.getTotalAmount(), bo.getTotalAmount());
    }

    /**
     * 路由测试
    */
    @Test
    public void shouldRouteByUserIdHash() {
        for (int i = 0; i < 50; i++) {
            String user_id = "ian_" + RandomStringUtils.randomAlphabetic(6);
            log.info("测试结果 {}", (user_id.hashCode() ^ (user_id.hashCode()) >>> 16) & 3);
        }
    }

}
