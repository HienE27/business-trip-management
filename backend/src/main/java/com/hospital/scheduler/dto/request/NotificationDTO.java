package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {

    // BUGFIX (was RBAC#1): previously `@NotNull` was missing, so a request
    // body without `recipientId` (e.g. admin UI only filling title+message)
    // slipped past validation, then `staffRepository.findById(null)` threw
    // `IllegalArgumentException` deep in JPA, surfaced as HTTP 500. Now the
    // bean validator rejects missing/invalid IDs at the controller boundary
    // with a clean 400 Bad Request and a Vietnamese error message.
    @NotNull(message = "ID nhân sự nhận không được để trống")
    @Positive(message = "ID nhân sự nhận phải là số dương")
    private Integer recipientId;

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 100, message = "Tiêu đề không quá 100 ký tự")
    private String title;

    @NotBlank(message = "Nội dung không được để trống")
    @Size(max = 1000, message = "Nội dung không quá 1000 ký tự")
    private String message;

    public NotificationDTO(String title, String message) {
        this.title = title;
        this.message = message;
    }
}
