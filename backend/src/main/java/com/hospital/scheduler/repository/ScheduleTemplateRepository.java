package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Integer> {
    List<ScheduleTemplate> findByIsActiveTrue();
    List<ScheduleTemplate> findByDayOfWeek(Integer dayOfWeek);
    List<ScheduleTemplate> findBySpecialtyId(Integer specialtyId);
}
