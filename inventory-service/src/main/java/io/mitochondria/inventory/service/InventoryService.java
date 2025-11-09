package io.mitochondria.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mitochondria.inventory.event.InventoryRejectedEvent;
import io.mitochondria.inventory.event.InventoryReservedEvent;
import io.mitochondria.inventory.model.OutboxEvent;
import io.mitochondria.inventory.model.ProcessedOrderId;
import io.mitochondria.inventory.repository.InventoryRepository;
import io.mitochondria.inventory.repository.OutboxEventRepository;
import io.mitochondria.inventory.repository.ProcessedOrderIdRepository;
import io.mitochondria.order.event.OrderPlacedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class InventoryService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);
    private final InventoryRepository inventoryRepository;
    private final ProcessedOrderIdRepository processedOrderIdRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public InventoryService(
        InventoryRepository inventoryRepository,
        ProcessedOrderIdRepository processedOrderIdRepository,
        OutboxEventRepository outboxEventRepository,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper
    ) {
        this.inventoryRepository = inventoryRepository;
        this.processedOrderIdRepository = processedOrderIdRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-placed")
    public void reserveInventory(OrderPlacedEvent orderPlacedEvent) {
        transactionTemplate.executeWithoutResult(status -> {
            processOrderInTransaction(orderPlacedEvent);
        });
    }

    private void processOrderInTransaction(OrderPlacedEvent orderPlacedEvent) {
        try {
            processedOrderIdRepository.save(new ProcessedOrderId(
                orderPlacedEvent.orderId()
            ));
        } catch (DataIntegrityViolationException e) {
            logger.info("Order {} already processed", orderPlacedEvent.orderId());
            return;
        }

        int count = inventoryRepository.deductStock(orderPlacedEvent.productName(), orderPlacedEvent.quantity());
        String topic = (count > 0) ? "inventory-reserved" : "inventory-rejected";
        Object event = (count > 0)
            ? new InventoryReservedEvent(orderPlacedEvent.orderId(), orderPlacedEvent.email())
            : new InventoryRejectedEvent(orderPlacedEvent.orderId(), orderPlacedEvent.email());
        String json;

        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed for order: " + orderPlacedEvent.orderId(), e);
        }

        OutboxEvent outboxEvent = new OutboxEvent(
            orderPlacedEvent.orderId(),
            topic,
            json
        );

        outboxEventRepository.save(outboxEvent);
    }
}