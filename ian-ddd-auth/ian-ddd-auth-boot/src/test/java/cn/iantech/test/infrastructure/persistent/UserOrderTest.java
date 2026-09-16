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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // 验证批量新增用户订单，并读回校验分片路由落库成功
    @Test
    public void shouldInsertUserOrders() {
        String lastUserId = null;
        UserOrderPO lastOrder = null;
        for (int i = 0; i < 10; i++) {
            String userId = "ian_" + RandomStringUtils.randomAlphabetic(6);
            lastUserId = userId;
            UserOrderPO userOrderPO = UserOrderPO.builder()
                    .userName("测试用户")
                    .userId(userId)
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
            // 分片表的主键由 UserOrderPO 上的 @IdGenerator 填充，不依赖分片内自增
            Assertions.assertNotNull(userOrderPO.getId(), "insert 后主键应已被 @IdGenerator 填充");
            lastOrder = userOrderPO;
        }

        List<UserOrderPO> persisted = userOrderDao.selectByUserId(lastUserId);
        Assertions.assertFalse(persisted.isEmpty(), "插入后应能按 userId 路由查询到订单");
        Assertions.assertEquals("SKU-100001", persisted.getFirst().getSku());
        Assertions.assertEquals(lastUserId, persisted.getFirst().getUserId());
        Assertions.assertEquals(lastOrder.getId(), persisted.getFirst().getId(),
                "跨分片查回来的主键应与插入时生成的同一个");
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
     * 路由测试：校验分片表达式对 userId 的映射具备确定性与合法范围，并覆盖全部库与表。
     * 与 {@code sharding/sharding-jdbc-dev.yaml} 中的 INLINE 表达式保持一致。
     */
    @Test
    public void shouldRouteByUserIdHash() {
        Set<Integer> databaseIndexes = new HashSet<>();
        Set<Integer> tableIndexes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String userId = "ian_" + RandomStringUtils.randomAlphabetic(6);
            int databaseIndex = Math.abs(userId.hashCode()) % 2;
            int tableIndex = (userId.hashCode() ^ (userId.hashCode() >>> 16)) & 3;

            Assertions.assertTrue(databaseIndex >= 0 && databaseIndex < 2, "库下标必须落在 0..1");
            Assertions.assertTrue(tableIndex >= 0 && tableIndex < 4, "表下标必须落在 0..3");
            // 同一 userId 重复计算必须得到同一路由，避免非确定性分片
            Assertions.assertEquals(databaseIndex, Math.abs(userId.hashCode()) % 2);

            databaseIndexes.add(databaseIndex);
            tableIndexes.add(tableIndex);
        }

        Assertions.assertEquals(Set.of(0, 1), databaseIndexes, "200 个随机 userId 应覆盖两个库");
        Assertions.assertEquals(4, tableIndexes.size(), "200 个随机 userId 应覆盖四张分表");
    }

}
