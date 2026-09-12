package cn.iantech.id;

import org.junit.jupiter.api.Test;

import java.time.Duration;

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
}
