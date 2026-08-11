package com.ktb.chatapp.config;

import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import com.corundumstudio.socketio.namespace.Namespace;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.corundumstudio.socketio.store.MemoryStoreFactory;
import com.corundumstudio.socketio.store.RedissonStoreFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ktb.chatapp.websocket.socketio.ChatDataStore;
import com.ktb.chatapp.websocket.socketio.LocalChatDataStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.util.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIOConfig {

    @Value("${socketio.server.host:localhost}")
    private String host;

    @Value("${socketio.server.port:5002}")
    private Integer port;

    @Value("${socketio.server.origin:*}")
    private String origin;

    @Value("${socketio.server.accept-backlog:100}")
    private int acceptBacklog;

    @Value("${socketio.redis.enabled:false}")
    private boolean redisStoreEnabled;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.cluster.nodes:}")
    private String redisClusterNodes;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Socket.IO nodes share rooms and broadcasts through this Redisson client when enabled.
     * The StoreFactory owns its lifecycle, so it is intentionally not destroyed separately.
     */
    @Bean(destroyMethod = "")
    @ConditionalOnProperty(name = "socketio.redis.enabled", havingValue = "true")
    public RedissonClient socketIORedissonClient() {
        Config config = new Config();
        List<String> clusterAddresses = redisClusterAddresses();
        if (clusterAddresses.isEmpty()) {
            var singleServer = config.useSingleServer()
                    .setAddress("redis://" + redisHost + ":" + redisPort);
            if (StringUtils.hasText(redisPassword)) {
                singleServer.setPassword(redisPassword);
            }
            log.info("Socket.IO Redis store using standalone Redis at {}:{}", redisHost, redisPort);
        } else {
            var clusterServers = config.useClusterServers()
                    .addNodeAddress(clusterAddresses.toArray(String[]::new));
            if (StringUtils.hasText(redisPassword)) {
                clusterServers.setPassword(redisPassword);
            }
            log.info("Socket.IO Redis store using Redis Cluster seed nodes: {}", clusterAddresses);
        }

        return Redisson.create(config);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SocketIOServer socketIOServer(
            AuthTokenListener authTokenListener,
            MeterRegistry meterRegistry,
            org.springframework.beans.factory.ObjectProvider<RedissonClient> redissonClientProvider
    ) {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname(host);
        config.setPort(port);
        
        var socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpNoDelay(false);
        socketConfig.setAcceptBackLog(acceptBacklog);
        socketConfig.setTcpSendBufferSize(4096);    //나중에 수정
        socketConfig.setTcpReceiveBufferSize(4096);
        config.setSocketConfig(socketConfig);

        config.setOrigin(origin);

        // Socket.IO settings
        config.setPingTimeout(60000);
        config.setPingInterval(25000);
        config.setUpgradeTimeout(10000);

        config.setJsonSupport(new JacksonJsonSupport(new JavaTimeModule()));
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redisStoreEnabled && redissonClient != null) {
            config.setStoreFactory(new RedissonStoreFactory(redissonClient));
            log.info("Socket.IO Redis store enabled");
        } else {
            config.setStoreFactory(new MemoryStoreFactory());
            log.warn("Socket.IO is using an in-memory store; cross-node broadcasts are disabled");
        }

        log.info("Socket.IO server configured on {}:{} with accept backlog {}, {} boss threads and {} worker threads",
                 host, port, acceptBacklog, config.getBossThreads(), config.getWorkerThreads());
        var socketIOServer = new SocketIOServer(config);
        socketIOServer.getNamespace(Namespace.DEFAULT_NAME).addAuthTokenListener(authTokenListener);
        socketIOServer.getNamespace(Namespace.DEFAULT_NAME).addEventInterceptor((client, name, data, ack) -> {
            // 이벤트 발생 빈도 수집
            Counter.builder("socketio.events.total")
                .description("Total Socket.IO events received")
                .tag("event_type", name)
                .register(meterRegistry)
                .increment();
        });
        
        return socketIOServer;
    }

    private List<String> redisClusterAddresses() {
        if (!StringUtils.hasText(redisClusterNodes)) {
            return List.of();
        }

        return Arrays.stream(redisClusterNodes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::toRedisAddress)
                .toList();
    }

    private String toRedisAddress(String node) {
        if (node.startsWith("redis://") || node.startsWith("rediss://")) {
            return node;
        }
        return "redis://" + node;
    }
    
    /**
     * SpringAnnotationScanner는 BeanPostProcessor로서
     * ApplicationContext 초기화 초기에 등록되고,
     * 내부에서 사용하는 SocketIOServer는 Lazy로 지연되어
     * 다른 Bean들의 초기화 과정에 간섭하지 않게 한다.
     */
    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    public BeanPostProcessor springAnnotationScanner(@Lazy SocketIOServer socketIOServer) {
        return new SpringAnnotationScanner(socketIOServer);
    }
    
    // 인메모리 저장소, 단일 노드 환경에서만 사용
    @Bean
    @ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
    public ChatDataStore chatDataStore() {
        return new LocalChatDataStore();
    }
}
