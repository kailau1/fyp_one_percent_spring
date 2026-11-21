package com.example.one_percent.repository;

import com.example.one_percent.model.DailyTip;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DailyTipRepository extends MongoRepository<DailyTip, String> {
}
