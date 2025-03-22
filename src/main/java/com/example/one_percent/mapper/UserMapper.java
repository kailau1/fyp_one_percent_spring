package com.example.one_percent.mapper;

import com.example.one_percent.model.User;
import com.example.one_percent.dto.UserDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDTO toDto(User user);
    User toEntity(UserDTO userDTO);
}
