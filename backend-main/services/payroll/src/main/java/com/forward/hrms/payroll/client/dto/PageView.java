package com.forward.hrms.payroll.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Slice of {@link com.forward.hrms.commons.web.page.PageResponse} we use
 * when reading a paged endpoint from a downstream service — we only care
 * about the {@code content} list; the metadata fields come back as-is and
 * are ignored. Mirrors the BFF's same-named helper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageView<T>(List<T> content) {

    public List<T> contentOrEmpty() {
        return content == null ? List.of() : content;
    }
}
