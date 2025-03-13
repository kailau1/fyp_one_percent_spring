package com.example.one_percent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document(collection = "users")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;

}
