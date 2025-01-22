package com.example.one_percent.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document(collection = "users")
public class User {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;


}
