package com.example.marklong.global.entity;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
public abstract class SoftDeleteEntity extends BaseEntity {
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
    public void delete() {
        deletedAt = LocalDateTime.now();
    }
}
