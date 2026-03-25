package tradingbot.agent.infrastructure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByAgentIdOrderByTimestampAsc(String agentId);
    void deleteByAgentId(String agentId);
}
