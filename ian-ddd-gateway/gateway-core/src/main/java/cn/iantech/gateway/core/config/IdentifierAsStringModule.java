package cn.iantech.gateway.core.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.Collection;
import java.util.List;

/**
 * 把「标识类字段」按字符串出网的 Jackson 模块。
 *
 * <p>JSON 没有 int64：本项目的雪花 ID 约 8.7e17，远超 JavaScript 的
 * {@code Number.MAX_SAFE_INTEGER}（2^53）。若以 JSON number 出网，浏览器 {@code JSON.parse}
 * 会改写其低位，前端再把被改写的 ID 回传，后端就查不到对应记录（表现为 400 / 404）。</p>
 *
 * <p>命中规则按属性名判定、大小写敏感：等于 {@code id}，或以 {@code Id} / {@code Ids} 结尾。
 * 因此 {@code accountId / userId / roleIds / parentId} 等标识字段自动覆盖，而
 * {@code secretVersion / expiresIn / total} 等非标识数值不受影响；新增标识字段只要沿用
 * {@code xxxId} 命名即可自动生效，无需回到这里登记。</p>
 *
 * <p>只改序列化方向：入参仍声明为 {@code Long}，Jackson 默认会把字符串反序列化成数值，
 * 路径参数由 Spring 自行转换，因此 Dubbo 内部契约与控制器签名都不需要改动。</p>
 */
final class IdentifierAsStringModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    private static final String MODULE_NAME = "gateway-identifier-as-string";

    IdentifierAsStringModule() {
        super(MODULE_NAME);
        setSerializerModifier(new IdentifierSerializerModifier());
    }

    /** 按属性名把标识字段替换为字符串序列化器。 */
    private static final class IdentifierSerializerModifier extends ValueSerializerModifier {

        private static final long serialVersionUID = 1L;

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                         BeanDescription.Supplier beanDescription,
                                                         List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                if (isIdentifier(writer.getName())) {
                    writer.assignSerializer(IdentifierAsStringSerializer.INSTANCE);
                }
            }
            return beanProperties;
        }

        private static boolean isIdentifier(String name) {
            return "id".equals(name) || name.endsWith("Id") || name.endsWith("Ids");
        }
    }

    /**
     * 标识值序列化器：标量写成字符串，集合逐元素写成字符串数组。
     *
     * <p>集合必须逐元素处理——直接把整个 {@code List<Long>} 交给 {@code ToStringSerializer}
     * 会把列表当成一个字符串输出（{@code "[1, 2]"}），而不是字符串数组。</p>
     */
    private static final class IdentifierAsStringSerializer extends ValueSerializer<Object> {

        private static final IdentifierAsStringSerializer INSTANCE = new IdentifierAsStringSerializer();

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            if (value instanceof Collection<?> collection) {
                generator.writeStartArray();
                for (Object element : collection) {
                    if (element == null) {
                        generator.writeNull();
                    } else {
                        generator.writeString(element.toString());
                    }
                }
                generator.writeEndArray();
                return;
            }
            generator.writeString(value.toString());
        }
    }
}
