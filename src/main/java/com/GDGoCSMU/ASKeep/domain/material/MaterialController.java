package com.GDGoCSMU.ASKeep.domain.material;

import com.GDGoCSMU.ASKeep.domain.common.CurrentUser;
import com.GDGoCSMU.ASKeep.domain.common.PageResponse;
import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import com.GDGoCSMU.ASKeep.domain.material.dto.MaterialPagesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MaterialController {
    private final MaterialService materialService;

    @PostMapping("/sessions/{sessionId}/materials")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<MaterialResponse> upload(@PathVariable Long sessionId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(MaterialResponse.from(materialService.upload(sessionId, CurrentUser.id(), file)));
    }

    @GetMapping("/sessions/{sessionId}/materials")
    ApiResponse<PageResponse<MaterialResponse>> list(@PathVariable Long sessionId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return ApiResponse.ok(PageResponse.from(materialService.list(sessionId, CurrentUser.id(), PageRequest.of(page, size)), MaterialResponse::from));
    }

    @GetMapping("/materials/{materialId}")
    ApiResponse<MaterialResponse> get(@PathVariable Long materialId) {
        return ApiResponse.ok(MaterialResponse.from(materialService.get(materialId, CurrentUser.id())));
    }

    @DeleteMapping("/materials/{materialId}")
    ApiResponse<Void> delete(@PathVariable Long materialId) {
        materialService.delete(materialId, CurrentUser.id());
        return ApiResponse.ok();
    }

    @GetMapping("/materials/{materialId}/pages")
    ApiResponse<MaterialPagesResponse> pages(@PathVariable Long materialId) {
        return ApiResponse.ok(materialService.pages(materialId, CurrentUser.id()));
    }

    @GetMapping(value = "/materials/{materialId}/pages/{pageNumber}/image", produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> pageImage(@PathVariable Long materialId, @PathVariable int pageNumber) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(materialService.pageImage(materialId, CurrentUser.id(), pageNumber));
    }

    @PostMapping("/materials/{materialId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<MaterialResponse> retry(@PathVariable Long materialId) {
        return ApiResponse.ok(MaterialResponse.from(materialService.retry(materialId, CurrentUser.id())));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size 범위가 올바르지 않습니다.");
    }
}
