package com.example.order.config;

import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer.group}")
    private String producerGroup;

    @Value("${rocketmq.topics.order-events}")
    private String orderTopic;

    @Bean
    public TransactionMQProducer transactionMQProducer(TransactionListener transactionListener) {
        TransactionMQProducer producer = new TransactionMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setTransactionListener(transactionListener);
        producer.setSendMsgTimeout(3000);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }

    @Bean
    public RocketMQTemplate rocketMQTemplate(TransactionMQProducer transactionMQProducer) {
        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(transactionMQProducer);
        return template;
    }

    public String getOrderTopic() {
        return orderTopic;
    }
}
