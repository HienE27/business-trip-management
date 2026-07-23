package com.hospital.scheduler.scheduling.config.dto;

/**
 * Centralized OpenAPI example payloads for {@link com.hospital.scheduler.controller.ConfigProfileController}.
 *
 * <p>Keeping the JSON examples here (rather than inlined as {@code @ExampleObject})
 * has two benefits:
 * <ul>
 *   <li>Each example is reachable from unit tests via reflection if we ever
 *       need to validate the shape stays in sync with the actual JSON contract.</li>
 *   <li>Swagger UI renders the same example everywhere — copy/paste from
 *       {@code /swagger-ui} to Postman yields a valid request/response.</li>
 * </ul>
 *
 * <p>PR-11-03 Task B — Frontend-facing examples. These do NOT change the
 * runtime payload; they only describe what the server already returns.
 */
public final class OpenApiExamples {

    private OpenApiExamples() {}

    // ─── ConfigProfileDto (single object) ─────────────────────────────────────
    //
    // Four variants so Frontend developers can pick the closest example in
    // Swagger UI's example dropdown — useful for visual QA of read screens
    // that highlight `isSystem` / `isDefault` / `isFavorite` badges.

    /** System profile that is also the current default — appears first in lists. */
    public static final String SYSTEM_DEFAULT_PROFILE = """
            {
              "id": 1,
              "profileKey": "balanced",
              "nameVi": "Cân bằng",
              "nameEn": "Balanced",
              "description": "Cấu hình cân bằng giữa công bằng và hiệu suất",
              "category": "GENERAL",
              "icon": "balance",
              "tags": ["starter", "safe"],
              "isSystem": true,
              "isDefault": true,
              "isFavorite": false,
              "config": {
                "enabled": true,
                "algorithmType": "TABU",
                "maxIterations": 500
              },
              "createdBy": "system",
              "createdAt": "2026-05-12T08:30:00",
              "updatedAt": "2026-07-01T14:22:00"
            }
            """;

    /** System profile that the operator has starred — shows in the Favorites filter. */
    public static final String SYSTEM_FAVORITE_PROFILE = """
            {
              "id": 3,
              "profileKey": "fairness-priority",
              "nameVi": "Ưu tiên công bằng",
              "nameEn": "Fairness first",
              "description": "Tối ưu fairness giữa nhân viên — chấp nhận chạy lâu hơn",
              "category": "PERFORMANCE",
              "icon": "workspace_premium",
              "tags": ["fairness", "long-run"],
              "isSystem": true,
              "isDefault": false,
              "isFavorite": true,
              "config": {
                "enabled": true,
                "algorithmType": "CSP",
                "maxIterations": 2000
              },
              "createdBy": "system",
              "createdAt": "2026-04-01T08:00:00",
              "updatedAt": "2026-06-20T11:15:00"
            }
            """;

    /** User-created profile — fully editable, deletable, can be set as default. */
    public static final String CUSTOM_PROFILE = """
            {
              "id": 17,
              "profileKey": "noi-khoa-custom",
              "nameVi": "Khoa Nội — ca ngày",
              "nameEn": "Internal Med — day shifts",
              "description": "Ưu tiên bác sĩ nữ cho ca ngày; thứ 7 nghỉ",
              "category": "DEPARTMENT",
              "icon": "stethoscope",
              "tags": ["noi-khoa", "ngay", "female-first"],
              "isSystem": false,
              "isDefault": false,
              "isFavorite": false,
              "config": {
                "enabled": true,
                "algorithmType": "GREEDY",
                "maxIterations": 300,
                "weights": {
                  "fairness": 0.3,
                  "preference": 0.6,
                  "stability": 0.1
                }
              },
              "createdBy": "alice.nguyen",
              "createdAt": "2026-07-05T10:30:00",
              "updatedAt": "2026-07-18T16:45:00"
            }
            """;

    /** Plain system profile — read-only, not default, not starred. Most common in lists. */
    public static final String SYSTEM_PLAIN_PROFILE = """
            {
              "id": 4,
              "profileKey": "speed-optimized",
              "nameVi": "Tốc độ cao",
              "nameEn": "Speed optimized",
              "description": "Giảm thời gian chạy — chấp nhận fairness thấp hơn",
              "category": "PERFORMANCE",
              "icon": "speed",
              "tags": ["fast"],
              "isSystem": true,
              "isDefault": false,
              "isFavorite": false,
              "config": {
                "enabled": true,
                "algorithmType": "GREEDY",
                "maxIterations": 150
              },
              "createdBy": "system",
              "createdAt": "2026-04-01T08:00:00",
              "updatedAt": "2026-04-01T08:00:00"
            }
            """;

    /**
     * Backward-compat alias — kept for the {@code SINGLE_PROFILE_RESPONSE}
     * template which used {@code CONFIG_PROFILE_DTO} before PR-11-03 Task C.
     * New code should reference one of the four named variants above.
     */
    public static final String CONFIG_PROFILE_DTO = SYSTEM_DEFAULT_PROFILE;

    // ─── PageResponse<ConfigProfileDto> (list endpoint) ───────────────────────

    public static final String PAGE_RESPONSE = """
            {
              "success": true,
              "data": {
                "items": [
                  {
                    "id": 1,
                    "profileKey": "balanced",
                    "nameVi": "Cân bằng",
                    "nameEn": "Balanced",
                    "category": "GENERAL",
                    "icon": "balance",
                    "isSystem": true,
                    "isDefault": true,
                    "isFavorite": false,
                    "createdBy": "system"
                  },
                  {
                    "id": 3,
                    "profileKey": "fairness-priority",
                    "nameVi": "Ưu tiên công bằng",
                    "nameEn": "Fairness first",
                    "category": "PERFORMANCE",
                    "icon": "workspace_premium",
                    "isSystem": true,
                    "isDefault": false,
                    "isFavorite": true,
                    "createdBy": "system"
                  },
                  {
                    "id": 17,
                    "profileKey": "noi-khoa-custom",
                    "nameVi": "Khoa Nội — ca ngày",
                    "nameEn": "Internal Med — day shifts",
                    "category": "DEPARTMENT",
                    "icon": "stethoscope",
                    "isSystem": false,
                    "isDefault": false,
                    "isFavorite": false,
                    "createdBy": "alice.nguyen"
                  }
                ],
                "page": 0,
                "size": 20,
                "totalItems": 135,
                "totalPages": 7,
                "hasNext": true,
                "hasPrev": false,
                "sort": "nameVi,ASC"
              },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    public static final String EMPTY_PAGE_RESPONSE = """
            {
              "success": true,
              "data": {
                "items": [],
                "page": 0,
                "size": 20,
                "totalItems": 0,
                "totalPages": 0,
                "hasNext": false,
                "hasPrev": false,
                "sort": "nameVi,ASC"
              },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    // ─── Single DTO wrapped in ApiResponse ────────────────────────────────────

    public static final String SINGLE_PROFILE_RESPONSE = """
            {
              "success": true,
              "data": {
                "id": 1,
                "profileKey": "balanced",
                "nameVi": "Cân bằng",
                "nameEn": "Balanced",
                "category": "GENERAL",
                "isSystem": true,
                "isDefault": true,
                "isFavorite": false
              },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    // ─── ConfigDomain (apply endpoint) ────────────────────────────────────────

    public static final String CONFIG_DOMAIN = """
            {
              "success": true,
              "data": {
                "enabled": true,
                "algorithmType": "TABU",
                "maxIterations": 500,
                "tabuTenure": 10,
                "timeoutSeconds": 30,
                "weights": {
                  "fairness": 0.5,
                  "preference": 0.3,
                  "stability": 0.2
                }
              },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    // ─── ProfileComparisonDto (compare endpoint) ─────────────────────────────

    public static final String PROFILE_COMPARISON = """
            {
              "success": true,
              "data": {
                "profileA": null,
                "profileB": {
                  "id": 7,
                  "profileKey": "max-throughput",
                  "nameVi": "Hiệu suất cao",
                  "isSystem": false,
                  "isDefault": false,
                  "isFavorite": true
                },
                "differences": [
                  {
                    "fieldPath": "maxIterations",
                    "oldValue": "500",
                    "newValue": "1000"
                  },
                  {
                    "fieldPath": "weights.fairness",
                    "oldValue": "0.5",
                    "newValue": "0.2"
                  }
                ]
              },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    // ─── Error envelopes ─────────────────────────────────────────────────────

    public static final String ERROR_400_VALIDATION = """
            {
              "success": false,
              "message": "Dữ liệu không hợp lệ",
              "data": {
                "errors": {
                  "nameVi": "Tên profile (VI) không được để trống",
                  "tags": "tags must not exceed 16 entries"
                },
                "requestId": "a1b2c3d4-1234-5678-9abc-def012345678"
              },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    public static final String ERROR_400_SORT = """
            {
              "success": false,
              "message": "Trường sort không hợp lệ: 'password'. Các trường được phép: [nameVi, nameEn, category, isFavorite, createdAt, updatedAt]",
              "data": { "requestId": "a1b2c3d4-1234-5678-9abc-def012345679" },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    public static final String ERROR_403_IMMUTABLE = """
            {
              "success": false,
              "message": "Không thể thay đổi profile hệ thống: balanced",
              "data": { "requestId": "a1b2c3d4-1234-5678-9abc-def01234567a" },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    public static final String ERROR_404_NOT_FOUND = """
            {
              "success": false,
              "message": "Không tìm thấy profile với id: 999",
              "data": { "requestId": "a1b2c3d4-1234-5678-9abc-def01234567b" },
              "timestamp": "2026-07-21T09:15:00"
            }
            """;

    // ─── Request payloads ────────────────────────────────────────────────────

    public static final String CREATE_PROFILE_REQUEST = """
            {
              "nameVi": "Cấu hình mới cho khoa Nội",
              "nameEn": "New config for Internal Medicine",
              "description": "Ưu tiên bác sĩ nữ, ca ngày",
              "category": "DEPARTMENT",
              "icon": "stethoscope",
              "tags": ["noi-khoa", "ngay"]
            }
            """;

    public static final String UPDATE_PROFILE_REQUEST = """
            {
              "nameVi": "Cấu hình mới cho khoa Nội (đã cập nhật)",
              "description": "Bổ sung: ca đêm ưu tiên bác sĩ nam",
              "tags": ["noi-khoa", "ngay", "dem"]
            }
            """;

    public static final String DUPLICATE_REQUEST = """
            {
              "name": "Sao chép cấu hình khoa Nội"
            }
            """;

    public static final String COMPARE_REQUEST = """
            {
              "profileIdA": 1,
              "profileIdB": 7
            }
            """;

    public static final String IMPORT_REQUEST = """
            {
              "json": "{\\"profileKey\\":\\"imported\\",\\"nameVi\\":\\"Imported\\",\\"configJson\\":\\"{}\\"}"
            }
            """;
}