package cn.iantech.gateway.model;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.model.Response;

public final class GatewayResponses {

    private GatewayResponses() { }

    public static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(Constants.ResponseCode.SUCCESS.getCode())
                .info(Constants.ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

}
