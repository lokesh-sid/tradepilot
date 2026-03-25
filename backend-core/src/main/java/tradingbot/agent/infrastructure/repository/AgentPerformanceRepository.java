package tradingbot.agent.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tradingbot.agent.infrastructure.persistence.AgentPerformanceEntity;

@Repository
public interface AgentPerformanceRepository extends JpaRepository<AgentPerformanceEntity, String> {
}
