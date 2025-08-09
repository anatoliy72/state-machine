package com.example.state_machine.repository;

import com.example.state_machine.model.ProcessInstance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessInstanceRepository extends MongoRepository<ProcessInstance, String> {
}
