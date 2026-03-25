package tradingbot.agent.api.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import tradingbot.agent.infrastructure.persistence.AgentPerformanceEntity;

@Mapper(componentModel = "spring")
public interface PerformanceMapper {
    
    PerformanceMapper INSTANCE = Mappers.getMapper(PerformanceMapper.class);

    @Mapping(target = "lastUpdated", defaultExpression = "java(java.time.Instant.now())")
    PerformanceResponse toResponse(AgentPerformanceEntity entity);
}
