package com.example.one_percent.mapper;

import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.dto.JournalEntryDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JournalEntryMapper {
    JournalEntryDTO toDto(JournalEntry entry);
    JournalEntry toEntity(JournalEntryDTO dto);
}
