package cn.iantech.trigger.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * XXL-Job
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class XXLJob {

    @Setter(onMethod_ = @Autowired)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic:ian-mq}")
    private String topic;

    @XxlJob("demoJobHandler")
    public void doJob() {
        // 可以在任务中，调用一些业务方法逻辑的实现，如定时扫描超时未支付订单为关单处理，恢复库存
        log.info("执行任务 - XXL-Job - 01 发送一条 Kafka 消息");
        // 发送 Kafka 消息
        kafkaTemplate.send(topic, "我是测试消息");
    }

}
