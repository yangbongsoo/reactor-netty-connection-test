package spring;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.*;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.util.ResourceLeakDetector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@EnableAutoConfiguration(exclude = {PropertyPlaceholderAutoConfiguration.class, GsonAutoConfiguration.class})
@SpringBootApplication
public class DisruptorRunner {

    // https://t1mek1ller.github.io/2020/03/02/bugs-reactor-netty/
    // http://localhost:8123/actuator/metrics
    // http://localhost:8123/actuator/metrics/reactor.netty.connection.provider.disruptorFixedConnectionPool.pending.connections
    // http://localhost:8123/actuator/metrics/reactor.netty.connection.provider.disruptorFixedConnectionPool.idle.connections
    // http://localhost:8123/actuator/metrics/reactor.netty.connection.provider.disruptorFixedConnectionPool.active.connections
    // http://localhost:8123/actuator/metrics/reactor.netty.connection.provider.disruptorFixedConnectionPool.total.connections
    // http://localhost:8123/actuator/metrics/jvm.threads.states
    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        SpringApplication.run(DisruptorRunner.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> routes(Requester requester) {
        return route(GET("/check/*"), request -> ServerResponse.ok().bodyValue("I'm alive!"))
                .andRoute(GET("req/{count}"), request -> {
                    String countRaw = request.pathVariable("count");
                    Integer count = Integer.valueOf(countRaw);
                    return requester.request(count).flatMap(i -> ServerResponse.ok().bodyValue(i));
                });
    }

    @Bean
    WebClient webClient(WebClient.Builder builder) {
        ConnectionProvider provider = ConnectionProvider.fixed("disruptorFixedConnectionPool", 100, 500);
        HttpClient httpClient = HttpClient.create(provider)
                .metrics(true)
                .wiretap(true)
                .followRedirect(false)
                .compress(true)
                .secure();
        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "netty-disruptor");
    }
}
