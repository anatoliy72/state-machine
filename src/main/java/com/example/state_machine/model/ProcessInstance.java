// com/example/state_machine/model/ProcessInstance.java
package com.example.state_machine.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "process_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstance {

    @Id
    private String id;

    private String clientId;
    private ProcessType type;
    private ProcessState state;

    private Map<String, Object> variables;

    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version; // <-- оптимистическая блокировка
}
