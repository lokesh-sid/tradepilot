package tradingbot.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for application instance identification.
 * Provides unique instance identification for load balancing and monitoring.
 */
@Component
public class InstanceConfig {
    
    @Value("${app.instance.id:#{null}}")
    private String configuredInstanceId;
    
    @Value("${app.instance.zone:default}")
    private String availabilityZone;
    
    private String instanceId;
    private String hostName;
    private String ipAddress;
    
    @PostConstruct
    public void initialize() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            this.hostName = localHost.getHostName();
            this.ipAddress = localHost.getHostAddress();
            this.instanceId = configuredInstanceId != null ? 
                configuredInstanceId : generateInstanceId();
        } catch (UnknownHostException e) {
            this.hostName = "unknown";
            this.ipAddress = "unknown";
            this.instanceId = UUID.randomUUID().toString();
        }
    }
    
    private String generateInstanceId() {
        return hostName + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Gets the unique instance identifier.
     * @return instance ID
     */
    public String getInstanceId() { 
        return instanceId; 
    }
    
    /**
     * Gets the host name.
     * @return host name
     */
    public String getHostName() { 
        return hostName; 
    }
    
    /**
     * Gets the IP address.
     * @return IP address
     */
    public String getIpAddress() { 
        return ipAddress; 
    }
    
    /**
     * Gets the availability zone.
     * @return availability zone
     */
    public String getAvailabilityZone() { 
        return availabilityZone; 
    }
}