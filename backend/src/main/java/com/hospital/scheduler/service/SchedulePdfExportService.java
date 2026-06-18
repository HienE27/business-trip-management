package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchedulePdfExportService {

    private final ScheduleRepository scheduleRepository;

    public byte[] exportScheduleToPdf(Integer periodId) throws IOException {
        return exportScheduleToPdf(periodId, null, null, null, null);
    }

    public byte[] exportScheduleToPdf(Integer periodId, String shiftTypeId, Integer staffId,
                                      java.time.LocalDate startDate, java.time.LocalDate endDate) throws IOException {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        // Apply the same filters as the Excel export for consistency.
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

        schedules = schedules.stream()
                .sorted(Comparator.comparing(Schedule::getWorkDate).thenComparing(s -> s.getStaff().getFullName()))
                .toList();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(document, baos);
            document.open();

            var titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            var textFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            var headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            document.add(new Paragraph("Báo cáo lịch công tác", titleFont));
            document.add(new Paragraph("Kỳ lịch ID: " + periodId, textFont));
            document.add(new Paragraph("Tổng số lịch: " + schedules.size(), textFont));
            document.add(new Paragraph(" ", textFont));

            PdfPTable table = new PdfPTable(new float[]{0.7f, 1.3f, 1f, 2f, 1.8f, 2f, 1.4f});
            table.setWidthPercentage(100);

            addPdfHeaderCell(table, "STT", headerFont);
            addPdfHeaderCell(table, "Ngày", headerFont);
            addPdfHeaderCell(table, "Thứ", headerFont);
            addPdfHeaderCell(table, "Họ tên", headerFont);
            addPdfHeaderCell(table, "Chuyên khoa", headerFont);
            addPdfHeaderCell(table, "Loại ca", headerFont);
            addPdfHeaderCell(table, "Ghi chú", headerFont);

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            int stt = 1;
            for (Schedule schedule : schedules) {
                addPdfCell(table, String.valueOf(stt++), textFont);
                addPdfCell(table, schedule.getWorkDate().format(dateFormatter), textFont);
                addPdfCell(table, getDayOfWeekVietnamese(schedule.getWorkDate().getDayOfWeek().getValue()), textFont);
                addPdfCell(table, schedule.getStaff().getFullName(), textFont);
                addPdfCell(table, schedule.getStaff().getSpecialty() != null ? schedule.getStaff().getSpecialty().getName() : "", textFont);
                addPdfCell(table, schedule.getShiftType().getName(), textFont);
                addPdfCell(table, schedule.getHasConflict() ? "Có xung đột" : "", textFont);
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IOException("Không thể tạo PDF lịch công tác", ex);
        }
    }

    private void addPdfHeaderCell(PdfPTable table, String value, com.lowagie.text.Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addPdfCell(PdfPTable table, String value, com.lowagie.text.Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(5);
        table.addCell(cell);
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
