package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final ScheduleRepository scheduleRepository;

    public byte[] exportScheduleToExcel(Integer periodId) throws IOException {
        return exportScheduleToExcel(periodId, null, null, null, null);
    }

    public byte[] exportScheduleToExcel(Integer periodId, String shiftTypeId, Integer staffId,
                                        java.time.LocalDate startDate, java.time.LocalDate endDate) throws IOException {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        // Apply additional filters
        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            schedules = schedules.stream()
                    .filter(s -> shiftTypeId.equals(s.getShiftType().getId()))
                    .toList();
        }
        if (staffId != null) {
            schedules = schedules.stream()
                    .filter(s -> staffId.equals(s.getStaff().getId()))
                    .toList();
        }
        if (startDate != null) {
            schedules = schedules.stream()
                    .filter(s -> !s.getWorkDate().isBefore(startDate))
                    .toList();
        }
        if (endDate != null) {
            schedules = schedules.stream()
                    .filter(s -> !s.getWorkDate().isAfter(endDate))
                    .toList();
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Lich Cong Tac");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "Ngày", "Thứ", "Họ tên", "Chuyên khoa", "Loại ca", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            int rowNum = 1;
            int stt = 1;

            Map<String, List<Schedule>> groupedByDate = schedules.stream()
                    .sorted(Comparator.comparing(Schedule::getWorkDate).thenComparing(s -> s.getStaff().getFullName()))
                    .collect(Collectors.groupingBy(s -> s.getWorkDate().toString()));

            for (Map.Entry<String, List<Schedule>> entry : groupedByDate.entrySet()) {
                for (Schedule schedule : entry.getValue()) {
                    Row row = sheet.createRow(rowNum++);
                    createCell(row, 0, stt++, dataStyle);
                    createCell(row, 1, schedule.getWorkDate().format(dateFormatter), dataStyle);
                    createCell(row, 2, getDayOfWeekVietnamese(schedule.getWorkDate().getDayOfWeek().getValue()), dataStyle);
                    createCell(row, 3, schedule.getStaff().getFullName(), dataStyle);
                    createCell(row, 4, schedule.getStaff().getSpecialty() != null ? schedule.getStaff().getSpecialty().getName() : "", dataStyle);
                    createCell(row, 5, schedule.getShiftType().getName(), dataStyle);
                    createCell(row, 6, schedule.getHasConflict() ? "CÓ XUNG ĐỘT" : "", dataStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    public byte[] exportWorkloadReportToExcel(Integer periodId) throws IOException {
        return exportWorkloadReportToExcel(periodId, null, null, null);
    }

    public byte[] exportWorkloadReportToExcel(Integer periodId, String shiftTypeId, Integer staffId,
                                            java.time.LocalDate startDate) throws IOException {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            schedules = schedules.stream()
                    .filter(s -> shiftTypeId.equals(s.getShiftType().getId()))
                    .toList();
        }
        if (staffId != null) {
            schedules = schedules.stream()
                    .filter(s -> staffId.equals(s.getStaff().getId()))
                    .toList();
        }
        if (startDate != null) {
            schedules = schedules.stream()
                    .filter(s -> !s.getWorkDate().isBefore(startDate))
                    .toList();
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Thong Ke Tai");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "Họ tên", "Chuyên khoa", "Tổng số ca", "Lịch trực 24/24", "Thông tầm", "Khám dịch vụ", "Khám chuyên gia", "Có xung đột"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            Map<Integer, Map<String, Long>> staffStats = schedules.stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getStaff().getId(),
                            Collectors.groupingBy(s -> s.getShiftType().getId(), Collectors.counting())
                    ));

            Map<Integer, Staff> staffMap = schedules.stream()
                    .collect(Collectors.toMap(s -> s.getStaff().getId(), Schedule::getStaff, (a, b) -> a));

            int rowNum = 1;
            int stt = 1;
            for (Map.Entry<Integer, Staff> entry : staffMap.entrySet()) {
                Staff staff = entry.getValue();
                Map<String, Long> stats = staffStats.getOrDefault(entry.getKey(), Map.of());

                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, stt++, dataStyle);
                createCell(row, 1, staff.getFullName(), dataStyle);
                createCell(row, 2, staff.getSpecialty() != null ? staff.getSpecialty().getName() : "", dataStyle);
                createCell(row, 3, stats.values().stream().mapToLong(Long::longValue).sum(), dataStyle);
                createCell(row, 4, stats.getOrDefault(ConflictDetectionService.SHIFT_TYPE_L01, 0L).intValue(), dataStyle);
                createCell(row, 5, stats.getOrDefault(ConflictDetectionService.SHIFT_TYPE_L02, 0L).intValue(), dataStyle);
                createCell(row, 6, stats.getOrDefault(ConflictDetectionService.SHIFT_TYPE_L03, 0L).intValue(), dataStyle);
                createCell(row, 7, stats.getOrDefault(ConflictDetectionService.SHIFT_TYPE_L04, 0L).intValue(), dataStyle);
                long conflictCount = schedules.stream()
                        .filter(s -> s.getStaff().getId().equals(entry.getKey()) && s.getHasConflict())
                        .count();
                createCell(row, 8, (int) conflictCount, dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private void createCell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof Long) {
            cell.setCellValue((Long) value);
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        }
        cell.setCellStyle(style);
    }

    private String getDayOfWeekVietnamese(int day) {
        return switch (day) {
            case 1 -> "Thứ 2";
            case 2 -> "Thứ 3";
            case 3 -> "Thứ 4";
            case 4 -> "Thứ 5";
            case 5 -> "Thứ 6";
            case 6 -> "Thứ 7";
            case 7 -> "Chủ Nhật";
            default -> "";
        };
    }
}
