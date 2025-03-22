package com.example.one_percent.mapper;

import com.example.one_percent.dto.HabitDTO;
import com.example.one_percent.model.Habit;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HabitMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "userId", target = "userId")
    @Mapping(source = "habitName", target = "habitName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "completed", target = "completed")
    @Mapping(source = "habitType", target = "habitType")
    @Mapping(source = "trigger", target = "trigger")
    @Mapping(source = "action", target = "action")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "lastUpdated", target = "lastUpdated")
    HabitDTO toDto(Habit habit);

    @InheritInverseConfiguration
    Habit toEntity(HabitDTO habitDTO);
}
