package hello;

import java.util.Arrays;
import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.websocket.SubProtocolWebSocketHandler;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.handler.AnnotationMethodMessageHandler;
import org.springframework.messaging.simp.handler.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.handler.SimpleUserQueueSuffixResolver;
import org.springframework.messaging.simp.handler.UserDestinationMessageHandler;
import org.springframework.messaging.simp.stomp.StompProtocolHandler;
import org.springframework.messaging.support.channel.ExecutorSubscribableChannel;
import org.springframework.messaging.support.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.support.converter.MessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.sockjs.SockJsHttpRequestHandler;
import org.springframework.web.socket.sockjs.SockJsService;
import org.springframework.web.socket.sockjs.transport.handler.DefaultSockJsService;

@Configuration
public class StompConfig {
    private final MessageConverter<?> messageConverter = new MappingJackson2MessageConverter();

    private final SimpleUserQueueSuffixResolver userQueueSuffixResolver = new SimpleUserQueueSuffixResolver();


    @Bean
    public SimpleUrlHandlerMapping handlerMapping() {

        SockJsService sockJsService = new DefaultSockJsService(taskScheduler());
        HttpRequestHandler requestHandler = new SockJsHttpRequestHandler(sockJsService, webSocketHandler());

        SimpleUrlHandlerMapping hm = new SimpleUrlHandlerMapping();
        hm.setOrder(-1);
        hm.setUrlMap(Collections.singletonMap("/hello/**", requestHandler));
        return hm;
    }

    // WebSocketHandler supporting STOMP messages

    @Bean
    public WebSocketHandler webSocketHandler() {

        StompProtocolHandler stompHandler = new StompProtocolHandler();
        stompHandler.setUserQueueSuffixResolver(this.userQueueSuffixResolver);

        SubProtocolWebSocketHandler webSocketHandler = new SubProtocolWebSocketHandler(dispatchChannel());
        webSocketHandler.setDefaultProtocolHandler(stompHandler);
        webSocketHandlerChannel().subscribe(webSocketHandler);

        return webSocketHandler;
    }

    // MessageHandler for processing messages by delegating to @Controller annotated methods

    @Bean
    public AnnotationMethodMessageHandler annotationMessageHandler() {

        AnnotationMethodMessageHandler handler =
                new AnnotationMethodMessageHandler(dispatchMessagingTemplate(), webSocketHandlerChannel());

        handler.setDestinationPrefixes(Arrays.asList("/app/"));
        handler.setMessageConverter(this.messageConverter);
        dispatchChannel().subscribe(handler);
        return handler;
    }

    // MessageHandler that acts as a "simple" message broker
    // See DispatcherServletInitializer for enabling/disabling the "simple-broker" profile

    @Bean
    public SimpleBrokerMessageHandler simpleBrokerMessageHandler() {
        SimpleBrokerMessageHandler handler = new SimpleBrokerMessageHandler(webSocketHandlerChannel());
        handler.setDestinationPrefixes(Arrays.asList("/topic/", "/queue/"));
        dispatchChannel().subscribe(handler);
        return handler;
    }

    // MessageHandler that resolves destinations prefixed with "/user/{user}"
    // See the Javadoc of UserDestinationMessageHandler for details

    @Bean
    public UserDestinationMessageHandler userMessageHandler() {
        UserDestinationMessageHandler handler = new UserDestinationMessageHandler(
                dispatchMessagingTemplate(), this.userQueueSuffixResolver);
        dispatchChannel().subscribe(handler);
        return handler;
    }

    // MessagingTemplate (and MessageChannel) to dispatch messages to for further processing
    // All MessageHandler beans above subscribe to this channel

    @Bean
    public SimpMessageSendingOperations dispatchMessagingTemplate() {
        SimpMessagingTemplate template = new SimpMessagingTemplate(dispatchChannel());
        template.setMessageConverter(this.messageConverter);
        return template;
    }

    @Bean
    public SubscribableChannel dispatchChannel() {
        return new ExecutorSubscribableChannel(asyncExecutor());
    }

    // Channel for sending STOMP messages to connected WebSocket sessions (mostly for internal use)

    @Bean
    public SubscribableChannel webSocketHandlerChannel() {
        return new ExecutorSubscribableChannel(asyncExecutor());
    }

    // Executor for message passing via MessageChannel

    @Bean
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setCorePoolSize(8);
        executor.setThreadNamePrefix("MessageChannel-");
        return executor;
    }

    // Task executor for use in SockJS (heartbeat frames, session timeouts)

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setThreadNamePrefix("SockJS-");
        taskScheduler.setPoolSize(4);
        return taskScheduler;
    }

}
