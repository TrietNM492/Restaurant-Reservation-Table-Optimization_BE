package com.restaurant.ireservation.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResultPaginationDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    Meta meta;
    Object result;

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Meta {
//        Trang nao
        int page;
//        Kich thuoc trang(so luong phan tu)
        int pageSize;
//        Co tong bao nhieu trang
        int pages;
//        Tong so luong phan tu
        long total;

        boolean hasNext;
        boolean hasPrevious;
//        int nextCursor;
    }
}
