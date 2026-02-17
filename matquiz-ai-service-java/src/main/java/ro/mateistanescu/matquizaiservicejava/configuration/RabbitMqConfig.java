package ro.mateistanescu.matquizaiservicejava.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // --- 1. JAVA -> PYTHON (Request) ---
    public static final String QUIZ_GENERATION_QUEUE = "quiz_generation_queue";
    public static final String QUIZ_GENERATION_EXCHANGE = "quiz_generation_exchange";
    public static final String QUIZ_GENERATION_ROUTING_KEY = "quiz_generation_key";

    // --- 2. PYTHON -> JAVA (Reply) ---
    public static final String QUIZ_RESULTS_QUEUE = "quiz_results_queue";
    public static final String QUIZ_RESULTS_EXCHANGE = "quiz_results_exchange";
    public static final String QUIZ_RESULTS_ROUTING_KEY = "quiz_results_key";


    //GENERATION QUEUE

    @Bean
    public Queue generationQueue() {
        return new Queue(QUIZ_GENERATION_QUEUE);
    }

    @Bean
    public TopicExchange generationExchange() {
        return new TopicExchange(QUIZ_GENERATION_EXCHANGE);
    }

    @Bean
    public Binding generationBinding(Queue generationQueue, TopicExchange generationExchange) {
        return BindingBuilder.bind(generationQueue)
                .to(generationExchange)
                .with(QUIZ_GENERATION_ROUTING_KEY);
    }


    //RESULTS QUEUE

    @Bean
    public Queue resultsQueue() {
        return new Queue(QUIZ_RESULTS_QUEUE);
    }

    @Bean
    public TopicExchange resultsExchange() {
        return new TopicExchange(QUIZ_RESULTS_EXCHANGE);
    }

    @Bean
    public Binding resultsBinding(Queue resultsQueue, TopicExchange resultsExchange) {
        return BindingBuilder.bind(resultsQueue)
                .to(resultsExchange)
                .with(QUIZ_RESULTS_ROUTING_KEY);
    }

    // JSON Converter

    @Bean
    public MessageConverter converter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter());
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter converter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(converter);
        return factory;
    }
}
