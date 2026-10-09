package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.dto.request.TableAreaRequest;
import com.restaurant.ireservation.dto.response.TableAreaResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
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
 * Module: Table Area Management [6]
 * Thực hiện lúc: 2026-10-08 13:50:00 (GMT+7)
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TableAreaService {

    TableAreaRepository tableAreaRepository;
    RestaurantTableRepository restaurantTableRepository;

    @Transactional(readOnly = true)
    public ResultPaginationDTO fetchTableAreas(String q, AreaName name, Pageable pageable) {
        String keyword = q != null ? q.trim().toLowerCase() : "";
        AreaName searchAreaName = null;
        if (!keyword.isEmpty()) {
            try {
                searchAreaName = AreaName.valueOf(keyword.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Not a direct match with AreaName enum value
            }
        }

        Page<TableArea> areaPage = tableAreaRepository.searchTableAreas(keyword, name, searchAreaName, pageable);
        List<TableAreaResponse> responseList = areaPage.getContent().stream()
                .map(this::toTableAreaResponse)
                .toList();

        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        paginationDTO.setResult(responseList);

        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(areaPage.getTotalPages());
        meta.setTotal(areaPage.getTotalElements());
        meta.setHasNext(areaPage.hasNext());
        meta.setHasPrevious(areaPage.hasPrevious());
        paginationDTO.setMeta(meta);

        return paginationDTO;
    }

    @Transactional(readOnly = true)
    public TableAreaResponse fetchTableAreaById(Long id) {
        TableArea tableArea = tableAreaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table area not found with id: " + id));
        return toTableAreaResponse(tableArea);
    }

    @Transactional
    public TableAreaResponse createTableArea(TableAreaRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (tableAreaRepository.existsByCode(code)) {
            throw new RequestInvalidException("Table area code already exists: " + code);
        }

        TableArea tableArea = TableArea.builder()
                .code(code)
                .name(request.getName())
                .description(request.getDescription())
                .build();

        TableArea saved = tableAreaRepository.save(tableArea);
        return toTableAreaResponse(saved);
    }

    @Transactional
    public TableAreaResponse updateTableArea(TableAreaRequest request) {
        if (request.getId() == null) {
            throw new RequestInvalidException("Id is required for updating table area");
        }

        TableArea existing = tableAreaRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Table area not found with id: " + request.getId()));

        String code = request.getCode().trim().toUpperCase();
        if (tableAreaRepository.existsByCodeAndIdNot(code, existing.getId())) {
            throw new RequestInvalidException("Table area code already exists: " + code);
        }

        existing.setCode(code);
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());

        TableArea updated = tableAreaRepository.save(existing);
        return toTableAreaResponse(updated);
    }

    @Transactional
    public void deleteTableArea(Long id) {
        TableArea existing = tableAreaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table area not found with id: " + id));

        if (restaurantTableRepository.existsByAreaId(id)) {
            throw new RequestInvalidException("Cannot delete table area because it is assigned to existing tables");
        }

        tableAreaRepository.delete(existing);
    }

    public TableAreaResponse toTableAreaResponse(TableArea area) {
        return TableAreaResponse.builder()
                .id(area.getId())
                .code(area.getCode())
                .name(area.getName())
                .description(area.getDescription())
                .createdBy(area.getCreatedBy())
                .createdDate(area.getCreatedDate())
                .lastModifiedBy(area.getLastModifiedBy())
                .lastModifiedDate(area.getLastModifiedDate())
                .build();
    }
}
