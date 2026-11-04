package com.GDGoCSMU.ASKeep.domain.material.dto;

import java.util.List;
import java.util.stream.IntStream;

public record MaterialPagesResponse(Long materialId, int pageCount, List<Page> pages) {
    public static MaterialPagesResponse of(Long materialId, int pageCount) {
        return new MaterialPagesResponse(materialId, pageCount, IntStream.rangeClosed(1, pageCount)
                .mapToObj(number -> new Page(number, "/api/v1/materials/" + materialId + "/pages/" + number + "/image"))
                .toList());
    }

    public record Page(int pageNumber, String imageUrl) {}
}
