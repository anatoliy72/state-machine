package com.example.state_machine.repository;

import com.example.state_machine.model.ProcessHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessHistoryRepository extends MongoRepository<ProcessHistory, String> {
    List<ProcessHistory> findByProcessIdOrderByTimestampAsc(String processId);
}


