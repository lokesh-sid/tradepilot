package tradingbot.agent.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AgentAlreadyExistsException extends RuntimeException {
    public AgentAlreadyExistsException(String message) {
        super(message);
    }
}
