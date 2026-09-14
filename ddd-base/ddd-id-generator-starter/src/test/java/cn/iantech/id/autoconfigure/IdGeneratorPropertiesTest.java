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
        assertThat(properties.getNamespace()).isEqualTo("ddd-global-id");
        assertThat(properties.getWorkerIdBitLength()).isEqualTo(10);
        assertThat(properties.getSequenceBitLength()).isEqualTo(12);
        assertThat(properties.getLeaseDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getRenewInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.workerPoolSize()).isEqualTo(1024);
        assertThat(properties.getWorkerIdBlockSize()).isEqualTo(64);
        assertThat(properties.getBusinesses()).isEmpty();
    }

    @Test
    void shouldRejectInvalidNamespace() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setNamespace("ddd:{invalid}");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("namespace");
    }

    @Test
    void shouldRejectNamespaceContainingColon() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setNamespace("ddd:id-generator");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("namespace");
    }

    @Test
    void shouldRejectBitLengthOverflow() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setWorkerIdBitLength(11);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("必须等于 22");
    }

    @Test
    void shouldRejectRenewIntervalNotShorterThanLease() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setLeaseDuration(Duration.ofSeconds(10));
        properties.setRenewInterval(Duration.ofSeconds(10));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("renew-interval");
    }

    @Test
    void shouldRejectNonPositiveWorkerIdBlockSize() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setWorkerIdBlockSize(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("worker-id-block-size 必须大于 0");
    }

    @Test
    void shouldRejectIllegalBusinessName() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setBusinesses(Map.of("Order", 0));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("业务名非法");
    }

    @Test
    void shouldRejectBusinessNameStartingWithDigit() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setBusinesses(Map.of("1order", 0));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("业务名非法");
    }

    @Test
    void shouldRejectNegativeBusinessBlockIndex() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setBusinesses(Map.of("order", -1));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("block 序号不能为负");
    }

    @Test
    void shouldRejectDuplicatedBusinessBlockIndex() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        Map<String, Integer> businesses = new LinkedHashMap<>();
        businesses.put("order", 1);
        businesses.put("user", 1);
        properties.setBusinesses(businesses);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("block 序号重复：1");
    }

    @Test
    void shouldRejectBusinessRangesBeyondWorkerPool() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setBusinesses(Map.of("order", 16));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("超出池容量");
    }

    @Test
    void shouldRejectHugeBusinessBlockIndexWithoutOverflow() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setBusinesses(Map.of("order", Integer.MAX_VALUE));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("超出池容量");
    }

    @Test
    void shouldAcceptBusinessRangesFillingWorkerPoolExactly() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setWorkerIdBitLength(6);
        properties.setSequenceBitLength(16);
        properties.setWorkerIdBlockSize(32);
        Map<String, Integer> businesses = new LinkedHashMap<>();
        businesses.put("order", 0);
        businesses.put("user", 1);
        properties.setBusinesses(businesses);

        properties.validate();

        assertThat(properties.workerPoolSize()).isEqualTo(64);
        assertThat(properties.blockStart(0)).isZero();
        assertThat(properties.blockStart(1)).isEqualTo(32);
    }

    @Test
    void shouldNormalizeNullBusinessesToEmptyMap() {
        IdGeneratorProperties properties = new IdGeneratorProperties();
        properties.setBusinesses(null);

        properties.validate();

        assertThat(properties.getBusinesses()).isEmpty();
    }
}
