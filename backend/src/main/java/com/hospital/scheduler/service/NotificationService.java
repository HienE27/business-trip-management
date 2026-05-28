package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.response.NotificationResponse;
import com.hospital.scheduler.entity.Notification;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.NotificationRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StaffRepository staffRepository;

    public List<NotificationResponse> getNotificationsByStaff(Integer staffId) {
        return notificationRepository.findByStaffIdOrderByCreatedAtDesc(staffId).stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getUnreadNotifications(Integer staffId) {
        return notificationRepository.findUnreadByStaffId(staffId).stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public long countUnreadNotifications(Integer staffId) {
        return notificationRepository.countUnreadByStaffId(staffId);
    }

    public NotificationResponse markAsRead(Integer notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo với ID: " + notificationId));

        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);
        return NotificationResponse.fromEntity(saved);
    }

    public void markAllAsRead(Integer staffId) {
        List<Notification> unreadNotifications = notificationRepository.findUnreadByStaffId(staffId);
        for (Notification notification : unreadNotifications) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
        }
        notificationRepository.saveAll(unreadNotifications);
    }

    public NotificationResponse createNotification(Integer staffId, NotificationDTO dto) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + staffId));

        Notification notification = Notification.builder()
                .staff(staff)
                .title(dto.getTitle())
                .message(dto.getMessage())
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        return NotificationResponse.fromEntity(saved);
    }

    public void createNotificationForAllStaff(String title, String message) {
        List<Staff> allStaff = staffRepository.findAll();
        for (Staff staff : allStaff) {
            Notification notification = Notification.builder()
                    .staff(staff)
                    .title(title)
                    .message(message)
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
        }
    }

    public void deleteNotification(Integer notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new ResourceNotFoundException("Không tìm thấy thông báo với ID: " + notificationId);
        }
        notificationRepository.deleteById(notificationId);
    }
}
