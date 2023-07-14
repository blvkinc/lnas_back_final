package lk.lnas.ims.service;

import lk.lnas.ims.domain.Order;
import lk.lnas.ims.domain.OrderItem;
import lk.lnas.ims.domain.Plant;
import lk.lnas.ims.domain.Transaction;
import lk.lnas.ims.model.OrderDTO;
import lk.lnas.ims.model.OrderItemDTO;
import lk.lnas.ims.model.OrderStatus;
import lk.lnas.ims.model.report.OrderSummary;
import lk.lnas.ims.repos.OrderItemRepository;
import lk.lnas.ims.repos.OrderRepository;
import lk.lnas.ims.repos.PlantRepository;
import lk.lnas.ims.repos.TransactionRepository;
import lk.lnas.ims.util.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final TransactionRepository transactionRepository;
    private final PlantRepository plantRepository;
    private final OrderItemRepository orderItemRepository;

    public Page<OrderDTO> paginate(Specification<Order> spec, Pageable pageable) {
        return repository.findAll(spec, pageable).map(entity -> mapToDTO(entity, new OrderDTO()));
    }

    public OrderDTO get(final Long id) {
        return repository.findById(id)
                .map(order -> mapToDTO(order, new OrderDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public Long create(final OrderDTO orderDTO) {
        final Order order = new Order();
        mapToEntity(orderDTO, order);

        repository.findFirstByOrderByDocumentIndexDesc()
                .ifPresentOrElse(po -> order.setDocumentIndex(po.getDocumentIndex() + 1),
                        () -> order.setDocumentIndex(1L));

        order.setDocumentId(String.format("SO-%08d", order.getDocumentIndex()));

        Order save = repository.save(order);
        orderDTO.getItems().forEach(item -> {
            OrderItem entity = mapToEntity(item, new OrderItem());
            entity.setOrder(save);
            orderItemRepository.save(entity);
        });
        return save.getId();
    }

    public void closeOrder(final Long id) {
        final Order order = repository.findById(id)
                .orElseThrow(NotFoundException::new);

        Transaction transaction = new Transaction();
        transaction.setMethod(order.getTransactionMethod());
        transaction.setDate(LocalDateTime.now());
        transaction.setOrder(order);
        transaction.setAmount(order.getTotal());

        transactionRepository.save(transaction);

        order.setTransaction(transaction);
        order.setStatus(OrderStatus.CLOSED);
        repository.save(order);
    }

    public void update(final Long id, final OrderDTO orderDTO) {
        final Order order = repository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(orderDTO, order);
        repository.save(order);
    }

    public void delete(final Long id) {
        repository.deleteById(id);
    }

    private OrderDTO mapToDTO(final Order order, final OrderDTO orderDTO) {
        orderDTO.setId(order.getId());
        orderDTO.setStatus(order.getStatus());
        orderDTO.setType(order.getType());
        orderDTO.setSubtotal(order.getSubtotal());
        orderDTO.setDiscount(order.getDiscount());
        orderDTO.setTax(order.getTax());
        orderDTO.setShipping(order.getShipping());
        orderDTO.setTotal(order.getTotal());
        orderDTO.setTransactionMethod(order.getTransactionMethod());
        orderDTO.setTransaction(order.getTransaction() == null ? null : order.getTransaction().getId());
        orderDTO.setDocumentId(order.getDocumentId());
        orderDTO.setItems(orderItemRepository.findByOrderId(order.getId()).stream().map(orderItem -> {
            OrderItemDTO dto = new OrderItemDTO();
            dto.setId(orderItem.getId());
            dto.setPlant(orderItem.getPlant().getId());
            dto.setPrice(orderItem.getPrice());
            dto.setDiscount(orderItem.getDiscount());
            dto.setQty(orderItem.getQty());
            dto.setDescription(orderItem.getDescription());
            return dto;
        }).toList());
        return orderDTO;
    }

    private void mapToEntity(final OrderDTO orderDTO, final Order order) {
        order.setStatus(orderDTO.getStatus());
        order.setType(orderDTO.getType());
        order.setSubtotal(orderDTO.getSubtotal());
        order.setDiscount(orderDTO.getDiscount());
        order.setTax(orderDTO.getTax());
        order.setShipping(orderDTO.getShipping());
        order.setTotal(orderDTO.getTotal());
        order.setTransactionMethod(orderDTO.getTransactionMethod());
        final Transaction transaction = orderDTO.getTransaction() == null ? null : transactionRepository.findById(orderDTO.getTransaction())
                .orElseThrow(() -> new NotFoundException("transaction not found"));
        order.setTransaction(transaction);
    }

    public OrderItemDTO mapToDTO(final OrderItem orderItem, final OrderItemDTO orderItemDTO) {
        orderItemDTO.setId(orderItem.getId());
        orderItemDTO.setPlant(orderItem.getPlant().getId());
        orderItemDTO.setPrice(orderItem.getPrice());
        orderItemDTO.setDiscount(orderItem.getDiscount());
        orderItemDTO.setQty(orderItem.getQty());
        orderItemDTO.setDescription(orderItem.getDescription());
        return orderItemDTO;
    }

    public OrderItem mapToEntity(final OrderItemDTO orderItemDTO, final OrderItem orderItem) {
        orderItem.setId(orderItemDTO.getId());
        Plant plant = plantRepository.findById(orderItemDTO.getPlant())
                .orElseThrow(() -> new NotFoundException("Plant not found"));
        orderItem.setPlant(plant);
        orderItem.setPrice(orderItemDTO.getPrice());
        orderItem.setDiscount(orderItemDTO.getDiscount());
        orderItem.setQty(orderItemDTO.getQty());
        orderItem.setDescription(orderItemDTO.getDescription());

        return orderItem;
    }

    public List<OrderSummary> getWeeklyOrderSummaries() {
        OffsetDateTime currentDate = OffsetDateTime.now();
        OffsetDateTime fourWeeksAgo = currentDate.minusWeeks(4);

        List<Object[]> results = repository.findWeeklyOrderTotals(fourWeeksAgo, currentDate);
        return mapOrderSummaries(results);
    }

    public List<OrderSummary> getMonthlyOrderSummaries() {
        OffsetDateTime currentDate = OffsetDateTime.now();
        OffsetDateTime sixMonthsAgo = currentDate.minusMonths(6).withDayOfMonth(1);
        OffsetDateTime firstDayOfMonth = currentDate.withDayOfMonth(1);

        List<Object[]> results = repository.findMonthlyOrderTotals(sixMonthsAgo, firstDayOfMonth);
        return mapOrderSummaries(results);
    }


    private List<OrderSummary> mapOrderSummaries(List<Object[]> results) {
        List<OrderSummary> summaries = new ArrayList<>();
        for (Object[] result : results) {
            String date = (String) result[0];
            BigDecimal total = (BigDecimal) result[1];
            summaries.add(new OrderSummary(date, total));
        }
        return summaries;
    }


}
