package cn.iantech.domain.user.model.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户订单业务对象。
 */
@Data
public class UserOrderBO {

    private String userId;

    private String sku;

    private String orderId;

    private BigDecimal totalAmount;

}
