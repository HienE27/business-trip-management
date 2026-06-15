package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.StaffRequest;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.util.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StaffImportParser {

    public void parseFile(MultipartFile file, String extension,
                          List<StaffRequest> requests, List<String> errorMessages) {
        if ("csv".equalsIgnoreCase(extension)) {
            parseCsvFile(file, requests, errorMessages);
        } else {
            parseExcelFile(file, requests, errorMessages);
        }
    }

    private void parseCsvFile(MultipartFile file, List<StaffRequest> requests, List<String> errorMessages) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            Map<String, Integer> colMap = new HashMap<>();
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (lineNum == 1) {
                    List<String> headers = parseCsvLine(line);
                    for (int i = 0; i < headers.size(); i++) {
                        String h = StringUtils.clean(headers.get(i)).toLowerCase();
                        colMap.put(h, i);
                    }
                    continue;
                }
                if (line.trim().isEmpty()) continue;
                List<String> columns = parseCsvLine(line);
                parseRow(columns, colMap, lineNum, requests, errorMessages);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc tệp CSV: " + e.getMessage());
        }
    }

    private void parseExcelFile(MultipartFile file, List<StaffRequest> requests, List<String> errorMessages) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("Tệp Excel không có dòng tiêu đề");
            }
            Map<String, Integer> colMap = new HashMap<>();
            for (int cellNum = 0; cellNum < headerRow.getLastCellNum(); cellNum++) {
                Cell cell = headerRow.getCell(cellNum);
                if (cell == null) continue;
                colMap.put(getCellStringValue(cell).trim().toLowerCase(), cellNum);
            }

            int lastRow = sheet.getLastRowNum();
            for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                List<String> columns = new ArrayList<>();
                for (int cellNum = 0; cellNum < headerRow.getLastCellNum(); cellNum++) {
                    columns.add(getCellStringValue(row.getCell(cellNum)));
                }
                parseRow(columns, colMap, rowNum + 1, requests, errorMessages);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc tệp Excel: " + e.getMessage());
        }
    }

    private void parseRow(List<String> columns, Map<String, Integer> colMap,
                         int lineNum, List<StaffRequest> requests, List<String> errorMessages) {
        StaffRequest req = new StaffRequest();

        if (hasCol(colMap, "id", columns)) {
            String idStr = getCol(colMap, "id", columns);
            if (!idStr.isEmpty()) {
                try {
                    req.setId(Integer.parseInt(idStr));
                } catch (NumberFormatException e) {
                    errorMessages.add("Dòng " + lineNum + " - Cột ID: ID không đúng định dạng số");
                }
            }
        }
        req.setUsername(getCol(colMap, "username", columns));
        req.setFullName(getCol(colMap, "họ tên", columns));
        req.setEmail(getCol(colMap, "email", columns));
        req.setPhone(getCol(colMap, "số điện thoại", columns));
        req.setSpecialtyName(getCol(colMap, "chuyên khoa", columns));

        String maxShiftsStr = getCol(colMap, "max ca/tháng", columns);
        if (!maxShiftsStr.isEmpty()) {
            try {
                req.setMaxShiftsPerMonth(Integer.parseInt(maxShiftsStr));
            } catch (NumberFormatException e) {
                errorMessages.add("Dòng " + lineNum + " - Cột Max ca/tháng: Phải là định dạng số");
                req.setMaxShiftsPerMonth(5);
            }
        } else {
            req.setMaxShiftsPerMonth(5);
        }

        String rolesStr = getCol(colMap, "vai trò", columns);
        List<String> rolesList = new ArrayList<>();
        if (!rolesStr.isEmpty()) {
            for (String r : rolesStr.split(",")) {
                rolesList.add(r.trim());
            }
        }
        req.setRoles(rolesList);
        req.setStatus(getCol(colMap, "trạng thái", columns));

        requests.add(req);
    }

    private boolean hasCol(Map<String, Integer> colMap, String key, List<String> columns) {
        Integer idx = colMap.get(key);
        return idx != null && idx < columns.size();
    }

    private String getCol(Map<String, Integer> colMap, String key, List<String> columns) {
        Integer idx = colMap.get(key);
        if (idx == null || idx >= columns.size()) return "";
        return StringUtils.clean(columns.get(idx));
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        if (line.startsWith("\uFEFF")) {
            line = line.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',') {
                if (inQuotes) {
                    sb.append(c);
                } else {
                    values.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString());
        return values;
    }
}
