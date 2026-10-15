package com.forward.hrms.bff.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Slice of {@link com.forward.hrms.commons.web.page.PageResponse} we use
 * when reading paged endpoints from downstream services. We deliberately
 * declare our own copy here (rather than reusing PageResponse&lt;T&gt;) so
 * downstream client code only depends on the {@code content} list shape
 * — the metadata fields (page/size/totalElements/...) come back as-is
 * for the BFF to ignore or pass through, and aren't tied to the
 * downstream's exact pagination conventions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageView<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {

    public List<T> contentOrEmpty() {
        return content == null ? List.of() : content;
    }
}
