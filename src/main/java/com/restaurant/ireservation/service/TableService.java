package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.KitchenOrder;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.ChangeTableStatusRequest;
import com.restaurant.ireservation.dto.request.TableRequest;
import com.restaurant.ireservation.dto.response.AreaFloorPlanResponse;
import com.restaurant.ireservation.dto.response.TableAreaResponse;
import com.restaurant.ireservation.dto.response.TableResponse;
import com.restaurant.ireservation.dto.response.TableStatusSummaryResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.TableAreaRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module: Table Management [3]
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TableService {

    RestaurantTableRepository restaurantTableRepository;
    TableAreaRepository tableAreaRepository;
    ReservationTableRepository reservationTableRepository;
    TableAreaService tableAreaService;
    OrderRepository orderRepository;
    KitchenOrderRepository kitchenOrderRepository;
    ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public ResultPaginationDTO fetchTables(String q, Long areaId, TableStatus statusCode, Pageable pageable) {
        String keyword = q != null ? q.trim().toLowerCase() : "";

        Page<RestaurantTable> tablePage = restaurantTableRepository.searchTables(keyword, areaId, statusCode, pageable);
        List<TableResponse> responseList = tablePage.getContent().stream()
                .map(this::toTableResponse)
                .toList();

        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        paginationDTO.setResult(responseList);

        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(tablePage.getTotalPages());
        meta.setTotal(tablePage.getTotalElements());
        meta.setHasNext(tablePage.hasNext());
        meta.setHasPrevious(tablePage.hasPrevious());
        paginationDTO.setMeta(meta);

        return paginationDTO;
    }

    @Transactional(readOnly = true)
    public TableResponse fetchTableById(Long id) {
        RestaurantTable table = restaurantTableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));
        return toTableResponse(table);
    }

    @Transactional
    public TableResponse createTable(TableRequest request) {
        if (restaurantTableRepository.existsByTableNumber(request.getTableNumber())) {
            throw new RequestInvalidException("Table number already exists: " + request.getTableNumber());
        }

        TableArea area = tableAreaRepository.findById(request.getAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("Table area not found with id: " + request.getAreaId()));

        TableStatus status = request.getStatusCode() != null ? request.getStatusCode() : TableStatus.AVAILABLE;

        RestaurantTable table = RestaurantTable.builder()
                .tableNumber(request.getTableNumber())
                .capacity(request.getCapacity())
                .area(area)
                .statusCode(status)
                .description(request.getDescription())
                .build();

        RestaurantTable saved = restaurantTableRepository.save(table);
        return toTableResponse(saved);
    }

    @Transactional
    public TableResponse updateTable(TableRequest request) {
        if (request.getId() == null) {
            throw new RequestInvalidException("Id is required for updating table");
        }

        RestaurantTable existing = restaurantTableRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + request.getId()));

        if (restaurantTableRepository.existsByTableNumberAndIdNot(request.getTableNumber(), existing.getId())) {
            throw new RequestInvalidException("Table number already exists: " + request.getTableNumber());
        }

        TableArea area = tableAreaRepository.findById(request.getAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("Table area not found with id: " + request.getAreaId()));

        existing.setTableNumber(request.getTableNumber());
        existing.setCapacity(request.getCapacity());
        existing.setArea(area);
        if (request.getStatusCode() != null) {
            existing.setStatusCode(request.getStatusCode());
        }
        existing.setDescription(request.getDescription());

        RestaurantTable updated = restaurantTableRepository.save(existing);
        return toTableResponse(updated);
    }

    @Transactional
    public TableResponse changeTableStatus(ChangeTableStatusRequest request) {
        if (request.getId() == null) {
            throw new RequestInvalidException("Id is required for changing table status");
        }

        RestaurantTable existing = restaurantTableRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + request.getId()));

        existing.setStatusCode(request.getStatusCode());
        RestaurantTable updated = restaurantTableRepository.save(existing);

        // When table transitions to AVAILABLE or CLEANING, complete active orders on that table
        if (request.getStatusCode() == TableStatus.AVAILABLE || request.getStatusCode() == TableStatus.CLEANING) {
            completeActiveOrdersForTable(existing);
        }

        return toTableResponse(updated);
    }

    @Transactional
    public TableResponse toggleLockTable(Long id) {
        RestaurantTable existing = restaurantTableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        if (existing.getStatusCode() == TableStatus.LOCKED) {
            existing.setStatusCode(TableStatus.AVAILABLE);
        } else {
            existing.setStatusCode(TableStatus.LOCKED);
        }

        RestaurantTable updated = restaurantTableRepository.save(existing);
        return toTableResponse(updated);
    }

    @Transactional
    public void deleteTable(Long id) {
        RestaurantTable existing = restaurantTableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        if (reservationTableRepository.existsByTableId(id)) {
            throw new RequestInvalidException("Cannot delete table because it has associated reservations");
        }

        restaurantTableRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public TableStatusSummaryResponse fetchTableStatusSummary() {
        long totalTables = restaurantTableRepository.count();
        long availableTables = restaurantTableRepository.countByStatusCode(TableStatus.AVAILABLE);
        long reservedTables = restaurantTableRepository.countByStatusCode(TableStatus.RESERVED);
        long inUseTables = restaurantTableRepository.countByStatusCode(TableStatus.IN_USE);
        long cleaningTables = restaurantTableRepository.countByStatusCode(TableStatus.CLEANING);
        long lockedTables = restaurantTableRepository.countByStatusCode(TableStatus.LOCKED);

        Map<TableStatus, Long> statusCounts = new HashMap<>();
        for (TableStatus status : TableStatus.values()) {
            statusCounts.put(status, restaurantTableRepository.countByStatusCode(status));
        }

        List<TableArea> areas = tableAreaRepository.findAll();
        List<TableStatusSummaryResponse.AreaTableStatusSummary> areaSummaries = areas.stream()
                .map(area -> {
                    List<RestaurantTable> tables = restaurantTableRepository.findByAreaId(area.getId());
                    long areaTotal = tables.size();
                    long areaAvailable = tables.stream().filter(t -> t.getStatusCode() == TableStatus.AVAILABLE).count();
                    long areaInUse = tables.stream().filter(t -> t.getStatusCode() == TableStatus.IN_USE).count();
                    long areaReserved = tables.stream().filter(t -> t.getStatusCode() == TableStatus.RESERVED).count();
                    long areaCleaning = tables.stream().filter(t -> t.getStatusCode() == TableStatus.CLEANING).count();
                    long areaLocked = tables.stream().filter(t -> t.getStatusCode() == TableStatus.LOCKED).count();

                    return TableStatusSummaryResponse.AreaTableStatusSummary.builder()
                            .areaId(area.getId())
                            .areaCode(area.getCode())
                            .areaName(area.getName() != null ? area.getName().name() : null)
                            .totalTables(areaTotal)
                            .availableTables(areaAvailable)
                            .inUseTables(areaInUse)
                            .reservedTables(areaReserved)
                            .cleaningTables(areaCleaning)
                            .lockedTables(areaLocked)
                            .build();
                })
                .toList();

        return TableStatusSummaryResponse.builder()
                .totalTables(totalTables)
                .availableTables(availableTables)
                .reservedTables(reservedTables)
                .inUseTables(inUseTables)
                .cleaningTables(cleaningTables)
                .lockedTables(lockedTables)
                .statusCounts(statusCounts)
                .areaSummaries(areaSummaries)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AreaFloorPlanResponse> fetchFloorPlan() {
        List<TableArea> areas = tableAreaRepository.findAll();
        return areas.stream().map(area -> {
            List<RestaurantTable> tables = restaurantTableRepository.findByAreaIdOrderByTableNumberAsc(area.getId());
            List<TableResponse> tableResponses = tables.stream().map(this::toTableResponse).toList();
            return AreaFloorPlanResponse.builder()
                    .areaId(area.getId())
                    .areaCode(area.getCode())
                    .areaName(area.getName() != null ? area.getName().name() : null)
                    .areaDescription(area.getDescription())
                    .tables(tableResponses)
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<TableResponse> fetchTablesByArea(Long areaId) {
        if (!tableAreaRepository.existsById(areaId)) {
            throw new ResourceNotFoundException("Table area not found with id: " + areaId);
        }
        List<RestaurantTable> tables = restaurantTableRepository.findByAreaIdOrderByTableNumberAsc(areaId);
        return tables.stream().map(this::toTableResponse).toList();
    }

    public TableResponse toTableResponse(RestaurantTable table) {
        TableAreaResponse areaResponse = null;
        if (table.getArea() != null) {
            areaResponse = tableAreaService.toTableAreaResponse(table.getArea());
        }

        return TableResponse.builder()
                .id(table.getId())
                .tableNumber(table.getTableNumber())
                .capacity(table.getCapacity())
                .statusCode(table.getStatusCode())
                .description(table.getDescription())
                .area(areaResponse)
                .createdBy(table.getCreatedBy())
                .createdDate(table.getCreatedDate())
                .lastModifiedBy(table.getLastModifiedBy())
                .lastModifiedDate(table.getLastModifiedDate())
                .build();
    }

    private void completeActiveOrdersForTable(RestaurantTable table) {
        if (orderRepository == null || table == null || table.getId() == null) {
            return;
        }

        List<OrderStatus> closedStatuses = List.of(OrderStatus.COMPLETED);
        List<Order> activeOrders = orderRepository.findActiveOrdersByTableId(table.getId(), closedStatuses);

        for (Order order : activeOrders) {
            order.setOrderStatus(OrderStatus.COMPLETED);
            orderRepository.save(order);
            log.info("[TableService] Table #{} status changed to {}. Closed Order #{} -> COMPLETED.",
                    table.getTableNumber(), table.getStatusCode(), order.getOrderNo());

            if (order.getReservation() != null && order.getReservation().getReservationStatus() != ReservationStatus.CANCELLED) {
                order.getReservation().setReservationStatus(ReservationStatus.COMPLETED);
                if (reservationRepository != null) {
                    reservationRepository.save(order.getReservation());
                }
            }

            if (kitchenOrderRepository != null) {
                List<OrderKitchenStatus> activeKitchenStatuses = List.of(
                        OrderKitchenStatus.PENDING, OrderKitchenStatus.QUEUED,
                        OrderKitchenStatus.COOKING, OrderKitchenStatus.PREPARING,
                        OrderKitchenStatus.READY, OrderKitchenStatus.ON_HOLD
                );
                List<KitchenOrder> activeTickets = kitchenOrderRepository.findByOrderIdAndStatusIn(order.getId(), activeKitchenStatuses);
                for (KitchenOrder ticket : activeTickets) {
                    ticket.setStatus(OrderKitchenStatus.SERVED);
                    ticket.setActualServedTime(Instant.now());
                    kitchenOrderRepository.save(ticket);
                }
            }
        }
    }
}
