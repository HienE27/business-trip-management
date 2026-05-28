package com.hospital.scheduler.entity;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StaffRoleId implements Serializable {
    private Integer staffId;
    private Integer roleId;
}
