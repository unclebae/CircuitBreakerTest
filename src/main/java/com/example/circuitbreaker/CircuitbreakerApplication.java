package com.example.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreaker;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

@SpringBootApplication
public class CircuitbreakerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CircuitbreakerApplication.class, args);
    }

    /**
     * 신규 CircuitBreaker 를 생성한다.
     * 아래 코드를 보면 circuitBreaker 에서 timeDuration 으로 시간에 대한 정책을 추가하였습니다.
     * Duration.ofSecond(5) 를 통해서 5초의 시간이 지나면 서킷 브레이커가 동작하도록 정책을 설정합니다.
     * @return
     */
    @Bean
    ReactiveCircuitBreakerFactory circuitBreakerFactory() {
        final ReactiveResilience4JCircuitBreakerFactory factory = new ReactiveResilience4JCircuitBreakerFactory();
        factory.configureDefault(new Function<String, Resilience4JConfigBuilder.Resilience4JCircuitBreakerConfiguration>() {
            @Override
            public Resilience4JConfigBuilder.Resilience4JCircuitBreakerConfiguration apply(String s) {
                return new Resilience4JConfigBuilder(s)
                        .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build())
                        .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                        .build();
            }
        });
        return factory;
    }
}

/**
 * 컨트롤러 를 작성합니다.
 */
@RestController
class FailingRestController {

    private final FailingService failingService;
    private final ReactiveCircuitBreaker circuitBreaker;


    /**
     * 생성자에서 서비스를 wire 하고
     * 리액티브 서킷 브레이커도 함께 wire 합니다.
     * @param failingService 서비스
     * @param cbf 서킷 브레이커
     */
    FailingRestController(FailingService failingService,
                          ReactiveCircuitBreakerFactory cbf) {
        this.failingService = failingService;
        this.circuitBreaker = cbf.create("greet");
    }

    /**
     * GET 메소드 생성
     * /greet?name=XXX 로 요청을 한다.
     * circuitBreaker.run 을 통해서 호출하고 있음을 확인하자.
     * @param name 파라미터
     * @return 응답값을 전달한다.
     */
    @GetMapping("/greet")
    Publisher<String> greet(@RequestParam Optional<String> name) {
        final Mono<String> results = this.failingService.greet(name);
        return this.circuitBreaker.run(results, throwable -> Mono.just("Hello world! this is fallback"));
    }
}

/**
 * 서비스를 생성한다.
 */
@Log4j2
@Service
class FailingService {

    /**
     * 파라미터를 받아서 결과 값으로 "Hello XXX! (in X )" 형태로 반환한다.
     * delayElement 를 통해서 지정된 시간동안 대기하다가 응답을 반환하는 샘플 예제이다.
     * @param name 파라미터로 전달받은 이름
     * @return 인사 문구를 반환한다.
     */
    Mono<String> greet(Optional<String> name) {
        long seconds = (long) (Math.random() * 10);
        return name
                .map(str -> {
                    final String msg = "Hello " + str + "! (in " + seconds + ")";
                    log.info(msg);
                    return Mono.just(msg);
                })
                .orElse(Mono.error(new NullPointerException()))
                .delayElement(Duration.ofSeconds( seconds ));
    }

}