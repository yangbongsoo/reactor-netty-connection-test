package spring;

import static java.util.stream.Collectors.*;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
class Requester {
    private static final Logger logger = LoggerFactory.getLogger(Requester.class);

    private final WebClient webClient;
    private final String url;

    Requester(WebClient webClient, @Value("${target.url}") String url) {
        this.webClient = webClient;
        this.url = url;
    }

    Mono<String> request(Integer count) {
        logger.info("Scheduling {} reqs to {}", count, url);
        Flux<String> results = Flux.range(0, count)
                .flatMap(integer -> webClient.post().uri(url).bodyValue("[]").exchange()
                        .flatMap(clientResponse -> {
                            HttpStatus httpStatus = clientResponse.statusCode();
                            return clientResponse.releaseBody().then(Mono.just(httpStatus.toString()));
                        })
                        .timeout(Duration.ofSeconds(1))
                        .onErrorResume(throwable -> {
                            logger.warn("Got error", throwable);
                            return Mono.just(throwable.toString());
                        })
                );
        return results.collectList().map(l -> l.parallelStream()
                .collect(toConcurrentMap(k -> k, v -> 1, Integer::sum)).toString());
    }
}
