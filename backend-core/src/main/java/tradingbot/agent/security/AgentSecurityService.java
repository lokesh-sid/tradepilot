package tradingbot.agent.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.repository.AgentRepository;

@Service("agentSecurity")
public class AgentSecurityService {

    private final AgentRepository agentRepository;

    public AgentSecurityService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public boolean isOwner(String agentId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return agentRepository.findById(new AgentId(agentId))
                .map(Agent::getOwnerId)
                .map(ownerId -> ownerId.equals(authentication.getName()))
                .orElse(false);
    }
}
