package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Integer> {
    List<FileAttachment> findByRefTypeAndRefId(FileAttachment.RefType refType, Integer refId);
}
