package com.example.one_percent.repository;

import com.example.one_percent.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
}
