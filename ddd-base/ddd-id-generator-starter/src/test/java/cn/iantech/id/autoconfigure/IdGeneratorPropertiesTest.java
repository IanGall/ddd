package cn.iantech.id.autoconfigure;

import cn.iantech.id.IdGenerationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdGeneratorPropertiesTest {

    @Test
    void shouldUseSafeDistributedDefaults() {
        IdGeneratorProperties properties = new IdGeneratorProperties();

        assertThat(properties.isEnabled()).isTrue();
        // 命名空间为空表示「由自动装配从 spring.application.name 派生」，不再是共享的默认值
        assertThat(properties.getNamespace()).isNull();
        assertThat(properties.getWorkerIdBitLength()).isEqualTo(10);
        assertThat(properties.getSequenceBitLength()).isEqualTo(12);
        assertThat(properties.getLeaseDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getRenewInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.workerPoolSize()).isEqualTo(1024);
        assertThat(properties.getBusinesses()).isEmpty();
    }

    @Test
    void shouldRejectMissingNamespace() {
        IdGeneratorProperties properties = new IdGeneratorProperties();

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("namespace");
    }

    @Test
    void shouldRejectInvalidNamespace() {
        IdGeneratorProperties properties = valid();
        properties.setNamespace("ddd:{invalid}");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("namespace");
    }

    @Test
    void shouldRejectNamespaceContainingColon() {
        IdGeneratorProperties properties = valid();
        properties.setNamespace("ddd:id-generator");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("namespace");
    }

    @Test
    void shouldRejectWorkerAndSequenceBitsExceedingLimitWithoutBusinesses() {
        IdGeneratorProperties properties = valid();
        properties.setWorkerIdBitLength(11);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("之和不得超过 22");
    }

    @Test
    void shouldRejectRenewIntervalNotShorterThanLease() {
        IdGeneratorProperties properties = valid();
        properties.setLeaseDuration(Duration.ofSeconds(10));
        properties.setRenewInterval(Duration.ofSeconds(10));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("renew-interval");
    }

    @Test
    void shouldRejectIllegalBusinessName() {
        IdGeneratorProperties properties = valid();
        properties.setBusinesses(Map.of("Order", 12));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("业务名非法");
    }

    @Test
    void shouldRejectBusinessNameStartingWithDigit() {
        IdGeneratorProperties properties = valid();
        properties.setBusinesses(Map.of("1order", 12));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("业务名非法");
    }

    @Test
    void shouldRejectBusinessSequenceBitLengthOutOfRange() {
        IdGeneratorProperties properties = valid();
        properties.setBusinesses(Map.of("order", 2));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("业务 order 的 sequence-bit-length 必须在 3 到 21 之间");
    }

    @Test
    void shouldRejectMissingBusinessSequenceBitLength() {
        IdGeneratorProperties properties = valid();
        Map<String, Integer> businesses = new LinkedHashMap<>();
        businesses.put("order", null);
        properties.setBusinesses(businesses);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("业务 order 的 sequence-bit-length");
    }

    @Test
    void shouldRejectBusinessSequenceBitsExceedingLimit() {
        IdGeneratorProperties properties = valid();
        properties.setWorkerIdBitLength(14);
        properties.setBusinesses(Map.of("order", 12));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("业务 order 的序列位长过大");
    }

    @Test
    void shouldAcceptBusinessSequenceBitsFillingBudgetExactly() {
        IdGeneratorProperties properties = valid();
        properties.setWorkerIdBitLength(10);
        properties.setBusinesses(Map.of("order", 12));

        properties.validate();

        assertThat(properties.workerPoolSize()).isEqualTo(1024);
        assertThat(properties.sequenceBitLengthFor("order")).isEqualTo(12);
    }

    @Test
    void shouldAllowBusinessesToUseSmallerBudgetThanTheLimit() {
        IdGeneratorProperties properties = valid();
        properties.setWorkerIdBitLength(6);
        properties.setSequenceBitLength(16);
        properties.setBusinesses(Map.of("order", 10));

        properties.validate();

        assertThat(properties.workerPoolSize()).isEqualTo(64);
        assertThat(properties.sequenceBitLengthFor("order")).isEqualTo(10);
    }

    @Test
    void shouldNormalizeNullBusinessesToEmptyMap() {
        IdGeneratorProperties properties = valid();
        properties.setBusinesses(null);

        properties.validate();

        assertThat(properties.getBusinesses()).isEmpty();
    }

    private IdGeneratorProperties valid() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setNamespace("ddd-global-id");
        return properties;
    }
}
