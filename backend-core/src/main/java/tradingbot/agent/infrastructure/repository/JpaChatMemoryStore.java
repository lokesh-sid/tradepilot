package tradingbot.agent.infrastructure.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

@Component
public class JpaChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository repository;

    public JpaChatMemoryStore(ChatMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(Object memoryId) {
        String agentId = (String) memoryId;
        return repository.findByAgentIdOrderByTimestampAsc(agentId).stream()
                .map(ChatMessageEntity::getMessage)
                .toList();
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String agentId = (String) memoryId;
        
        repository.deleteByAgentId(agentId);
        
        Instant now = Instant.now();
        List<ChatMessageEntity> entities = messages.stream()
                .map(msg -> new ChatMessageEntity(agentId, msg, now))
                .toList();
        
        repository.saveAll(entities);
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        String agentId = (String) memoryId;
        repository.deleteByAgentId(agentId);
    }
}
