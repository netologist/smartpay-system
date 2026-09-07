package com.hozgan.smartpay.notification.mapper;

import com.hozgan.smartpay.notification.dto.response.NotificationLogResponse;
import com.hozgan.smartpay.notification.entity.NotificationLogEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationLogResponse toResponse(NotificationLogEntity entity);

    List<NotificationLogResponse> toResponseList(List<NotificationLogEntity> entities);
}
