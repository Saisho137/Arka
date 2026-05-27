package com.arka.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactivecommons.async.api.DirectAsyncGateway;
import reactor.core.publisher.Mono;
import org.reactivecommons.api.domain.Command;
import org.reactivecommons.async.api.AsyncQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReactiveDirectAsyncGatewayTest {

    @Mock
    private DirectAsyncGateway directAsyncGateway;

    private ReactiveDirectAsyncGateway gateway;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        gateway = new ReactiveDirectAsyncGateway(directAsyncGateway);
    }

    @Test
    void testRunRemoteJobSendsCommand()  {
        Object command = new Object() {
            public String toString() {
                return "testCommand";
            }
        };
        when(directAsyncGateway.sendCommand(any(Command.class), any(String.class))).thenReturn(Mono.empty());

        gateway.runRemoteJob(command).block();

        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
        verify(directAsyncGateway, times(1)).sendCommand(commandCaptor.capture(), eq(ReactiveDirectAsyncGateway.TARGET_NAME));
        Command capturedCommand = commandCaptor.getValue();
        assertEquals(ReactiveDirectAsyncGateway.SOME_COMMAND_NAME, capturedCommand.getName());
    }

    @Test
    void testRequestForRemoteDataSendsQuery() {
        Object query = new Object() {
            public String toString() {
                return "testQuery";
            }
        };
        when(directAsyncGateway.requestReply(any(AsyncQuery.class), any(String.class), eq(Object.class))).thenReturn(Mono.just(new Object()));

        gateway.requestForRemoteData(query).block();

        ArgumentCaptor<AsyncQuery> queryCaptor = ArgumentCaptor.forClass(AsyncQuery.class);
        verify(directAsyncGateway, times(1)).requestReply(queryCaptor.capture(), eq(ReactiveDirectAsyncGateway.TARGET_NAME), eq(Object.class));
        AsyncQuery capturedQuery = queryCaptor.getValue();
        assertEquals(ReactiveDirectAsyncGateway.SOME_QUERY_NAME, capturedQuery.getResource());
    }
}
