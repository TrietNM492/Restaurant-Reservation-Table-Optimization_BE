package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.ChangeTableStatusRequest;
import com.restaurant.ireservation.dto.request.TableRequest;
import com.restaurant.ireservation.dto.response.TableAreaResponse;
import com.restaurant.ireservation.dto.response.TableResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.TableAreaRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Module: Table Management [3]
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TableService {

    RestaurantTableRepository restaurantTableRepository;
    TableAreaRepository tableAreaRepository;
    ReservationTableRepository reservationTableRepository;
    TableAreaService tableAreaService;

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
}
