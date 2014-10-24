/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bearchoke.platform.platform.base.config;

import com.mongodb.Mongo;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.CommandGatewayFactoryBean;
import org.axonframework.commandhandling.interceptors.BeanValidationInterceptor;
import org.axonframework.contextsupport.spring.AnnotationDriven;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.SpringAggregateSnapshotter;
import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.axonframework.eventstore.fs.SimpleEventFileResolver;
import org.axonframework.eventstore.mongo.DefaultMongoTemplate;
import org.axonframework.eventstore.mongo.MongoEventStore;
import org.axonframework.eventstore.mongo.MongoTemplate;
import org.axonframework.saga.SagaRepository;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.saga.repository.mongo.MongoSagaRepository;
import org.axonframework.saga.spring.SpringResourceInjector;
import org.axonframework.springmessaging.eventbus.SpringMessagingEventBus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.SubscribableChannel;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Bjorn Harvold
 * Date: 7/19/14
 * Time: 10:36 PM
 * Responsibility:
 */
@Configuration
public class CQRSConfig {

    @Inject
    private Environment environment;

    @Inject
    @Qualifier("webSocketInputChannel")
    private SubscribableChannel webSocketInputChannel;

    @Bean
    public CommandBus commandBus() {
        SimpleCommandBus commandBus = new SimpleCommandBus();

        List<CommandDispatchInterceptor> interceptors = new ArrayList<>(1);
        interceptors.add(new BeanValidationInterceptor());
        commandBus.setDispatchInterceptors(interceptors);

        return commandBus;
    }


    @Bean
    public CommandGateway commandGateway() {
        CommandGateway commandGateway = null;
        CommandGatewayFactoryBean factory = new CommandGatewayFactoryBean();
        factory.setCommandBus(commandBus());
//        factory.setGatewayInterface();


        try {
            factory.afterPropertiesSet();
            commandGateway = (CommandGateway) factory.getObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return commandGateway;
    }


    @Bean
    public SpringMessagingEventBus eventBus() {
        SpringMessagingEventBus eventBus = new SpringMessagingEventBus();
        eventBus.setChannel(webSocketInputChannel);

        return eventBus;
    }

    /**
     * Properties to support the 'embedded' / default mode of operation.
     * Standard mode uses in-memory databases and doesn't need any configuration to get started
     */
    @Configuration
    @Profile("in-memory")
    static class Embedded {

        @Inject
        @Qualifier("taskExecutor")
        private TaskExecutor taskExecutor;

        @Bean
        public FileSystemEventStore eventStore() {
            FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(new File("./target/events")));

            return eventStore;
        }

        @Bean
        public InMemorySagaRepository sagaRepository() {
            InMemorySagaRepository sagaRepository = new InMemorySagaRepository();

            return sagaRepository;
        }

        /* Waiting on response for: http://issues.axonframework.org/youtrack/issue/AXON-274
        @Bean(name = "snapshotter")
        public Snapshotter snapshotter() {
            AggregateSnapshotter sas = new AggregateSnapshotter();
            sas.setEventStore(eventStore());
            sas.setExecutor(taskExecutor);

            return sas;
        }
        */

//        @Bean
//        public SimpleEventBus eventBus() {
//            SimpleEventBus eventBus = new SimpleEventBus();
//            return eventBus;
//        }
    }

    /**
     * Properties to support the 'mongodb' mode of operation.
     * This mode uses MongoDb databases and basic documents for user persistence and assumes a MongoDb instance is available
     */
    @Configuration
    @Profile("mongodb")
    static class MongoDb {

        @Inject
        @Qualifier("taskExecutor")
        private TaskExecutor taskExecutor;

        @Inject
        private Mongo mongo;

        @Bean
        public MongoTemplate mongoTemplate() {
            return new DefaultMongoTemplate(mongo);
        }

        @Bean
        public org.axonframework.saga.repository.mongo.MongoTemplate mongoSagaTemplate() {
            return new org.axonframework.saga.repository.mongo.DefaultMongoTemplate(mongo);
        }

        @Bean
        public MongoEventStore eventStore() {
            MongoEventStore eventStore = new MongoEventStore(mongoTemplate());

            return eventStore;
        }

        @Bean
        public SagaRepository sagaRepository() {
            MongoSagaRepository sagaRepository = new MongoSagaRepository(mongoSagaTemplate());
            sagaRepository.setResourceInjector(new SpringResourceInjector());

            return sagaRepository;
        }

        /*
        @Bean(name = "snapshotter")
        public Snapshotter snapshotter() {
            SpringAggregateSnapshotter sas = new SpringAggregateSnapshotter();
            sas.setEventStore(eventStore());
            sas.setExecutor(taskExecutor);

            return sas;
        }
        */


    }

}