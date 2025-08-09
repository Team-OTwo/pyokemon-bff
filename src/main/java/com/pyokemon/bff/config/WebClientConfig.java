package com.pyokemon.bff.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {
    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${external.services.booking-service}")
    private String bookingServiceUrl;

    @Value("${external.services.event-service}")
    private String eventServiceUrl;

    @Value("${external.services.account-service}")
    private String accountServiceUrl;

    @Value("${external.services.payment-service}")
    private String paymentServiceUrl;

    @Value("${webclient.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${webclient.response-timeout:5000}")
    private int responseTimeout;

    @Value("${webclient.read-timeout:5000}")
    private int readTimeout;

    @Value("${webclient.write-timeout:5000}")
    private int writeTimeout;

    @Value("${webclient.max-in-memory-size:2097152}")
    private int maxInMemorySize;

    private HttpClient createHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .responseTimeout(Duration.ofMillis(responseTimeout))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS)));
    }

    private ExchangeStrategies createExchangeStrategies() {
        return ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize))
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.info("Response: Status {}", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }

    @Bean
    public WebClient bookingServiceWebClient() {
        return createWebClient(bookingServiceUrl);
    }

    @Bean
    public WebClient eventServiceWebClient() {
        return createWebClient(eventServiceUrl);
    }

    @Bean
    public WebClient accountServiceWebClient() {
        return createWebClient(accountServiceUrl);
    }

    @Bean
    public WebClient paymentServiceWebClient() {
        return createWebClient(paymentServiceUrl);
    }

    private WebClient createWebClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .exchangeStrategies(createExchangeStrategies())
                .filter(logRequest())
                .filter(logResponse())
                .filter((request, next) -> next.exchange(request)
                        .retryWhen(Retry.backoff(3, Duration.ofMillis(500))))
                .build();
    }
}
