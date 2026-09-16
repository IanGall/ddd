package cn.iantech.infrastructure.persistent.po;

import cn.iantech.domain.user.model.entity.UserOrderBO;
import cn.iantech.infrastructure.id.AuthIdBusiness;
import cn.iantech.mysql.annotation.IdGenerator;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@AutoMapper(target = UserOrderBO.class)
@IdGenerator(AuthIdBusiness.USER_ORDER)
public class UserOrderPO {

    /**
     * 主键，由 {@code @IdGenerator} 在 insert 时填充。
     *
     * <p>不用分片内自增：{@code user_order_0..3} 各自计数会跨分片重号，而这张表的主键需要全局唯一。
     * 表按 {@code user_id} 路由，与 id 无关，因此应用侧提前生成主键不影响分片路由。
     */
    private Long id;
    /** 用户姓名 */
    private String userName;
    /** 用户编号 */
    private String userId;
    /** 用户电话 */
    private String userMobile;
    /** 商品编号 */
    private String sku;
    /** 商品名称 */
    private String skuName;
    /** 订单ID */
    private String orderId;
    /** 商品数量 */
    private int quantity;
    /** 商品价格 */
    private BigDecimal unitPrice;
    /** 折扣金额 */
    private BigDecimal discountAmount;
    /** 费率金额 */
    private BigDecimal tax;
    /** 支付金额 */
    private BigDecimal totalAmount;
    /** 订单日期 */
    private LocalDateTime orderDate;
    /** 订单状态 */
    private int orderStatus;
    /** 逻辑删单 */
    private int isDelete;
    /** 唯一索引 */
    private String uuid;
    /** 设备地址 */
    private String ipv4;
    /** 设备地址 */
    private byte[] ipv6;
    /** 扩展数据 */
    private String extData;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建时间 */
    private LocalDateTime createTime;

}
