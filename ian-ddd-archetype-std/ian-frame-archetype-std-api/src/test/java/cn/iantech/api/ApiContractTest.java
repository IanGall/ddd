package cn.iantech.api;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RPC 契约边界校验。
 *
 * <p>网关与标准服务只通过 {@code *-api} 交换数据，因此该模块的方法签名必须完全落在
 * {@code cn.iantech.api} / JDK 类型内，且契约 DTO 必须可序列化、可反序列化。
 * 契约若泄漏实现包或引入不可反序列化类型，将在本测试失败而不是在联调时才暴露。</p>
 */
class ApiContractTest {

    private static final List<Class<?>> SERVICES = List.of(
            IAuthService.class,
            IRbacService.class,
            ICustomerService.class,
            IChannelCredentialService.class,
            IPlatformAccountService.class);

    @Test
    void serviceSignaturesMustStayInsideApiBoundary() {
        for (Class<?> service : SERVICES) {
            for (Method method : service.getDeclaredMethods()) {
                assertInBoundary(service, method, method.getReturnType());
                for (Parameter parameter : method.getParameters()) {
                    assertInBoundary(service, method, parameter.getParameterizedType());
                }
            }
        }
    }

    @Test
    void contractDtosMustBeSerializableAndDeserializable() {
        Set<Class<?>> dtos = new LinkedHashSet<>();
        for (Class<?> service : SERVICES) {
            for (Method method : service.getDeclaredMethods()) {
                collectApiTypes(method.getReturnType(), dtos);
                for (Parameter parameter : method.getParameters()) {
                    collectApiTypes(parameter.getParameterizedType(), dtos);
                }
            }
        }

        assertTrue(!dtos.isEmpty(), "应至少收集到契约 DTO");
        for (Class<?> dto : dtos) {
            assertTrue(Serializable.class.isAssignableFrom(dto),
                    dto.getName() + " 作为 RPC 契约类型必须实现 Serializable");
            boolean hasNoArgConstructor = Arrays.stream(dto.getDeclaredConstructors())
                    .anyMatch(constructor -> constructor.getParameterCount() == 0);
            assertTrue(hasNoArgConstructor,
                    dto.getName() + " 必须提供无参构造以便反序列化");
        }
    }

    private void assertInBoundary(Class<?> service, Method method, Type type) {
        assertTrue(isInBoundary(type),
                "%s#%s 的契约类型越界：%s（只允许 JDK 与 cn.iantech.api 类型）"
                        .formatted(service.getSimpleName(), method.getName(), type.getTypeName()));
    }

    private boolean isInBoundary(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isPrimitive() || clazz == Void.TYPE) {
                return true;
            }
            if (clazz.isArray()) {
                return isInBoundary(clazz.getComponentType());
            }
            return isJdkOrApi(clazz);
        }
        if (type instanceof ParameterizedType parameterized) {
            return isInBoundary(parameterized.getRawType())
                    && Arrays.stream(parameterized.getActualTypeArguments()).allMatch(this::isInBoundary);
        }
        if (type instanceof GenericArrayType array) {
            return isInBoundary(array.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getUpperBounds()).allMatch(this::isInBoundary)
                    && Arrays.stream(wildcard.getLowerBounds()).allMatch(this::isInBoundary);
        }
        if (type instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds()).allMatch(this::isInBoundary);
        }
        return false;
    }

    private boolean isJdkOrApi(Class<?> type) {
        String packageName = type.getPackageName();
        return packageName.startsWith("java.") || packageName.startsWith("cn.iantech.api");
    }

    private void collectApiTypes(Type type, Set<Class<?>> collected) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                collectApiTypes(clazz.getComponentType(), collected);
            } else if (clazz.getPackageName().startsWith("cn.iantech.api.model")) {
                collected.add(clazz);
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectApiTypes(argument, collected);
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            collectApiTypes(array.getGenericComponentType(), collected);
        }
    }
}
