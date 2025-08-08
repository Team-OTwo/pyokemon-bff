package com.pyokemon.bff.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${external.services.booking-service}")
    private String bookingServiceUrl;

    @Value("${external.services.event-service}")
    private String eventServiceUrl;

    @Value("${external.services.account-service}")
    private String accountServiceUrl;

    @Value("${external.services.payment-service}")
    private String paymentServiceUrl;

    private HttpClient createHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofMillis(5000))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS)));
    }

    private ExchangeStrategies createExchangeStrategies() {
        return ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            System.out.println("Request: " + clientRequest.method() + " " + clientRequest.url());
            return Mono.just(clientRequest);
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
                .build();
    }
}
