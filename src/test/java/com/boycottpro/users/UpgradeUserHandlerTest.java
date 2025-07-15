package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.boycottpro.models.Users;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

@ExtendWith(MockitoExtension.class)
public class UpgradeUserHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private UpgradeUserHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testUpgradeUserHandler_success() throws Exception {
        // Mock input event
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setPathParameters(Map.of("user_id", "user123"));

        // Mock response from DynamoDB
        Map<String, AttributeValue> attributes = Map.of(
                "user_id", AttributeValue.fromS("user123"),
                "email_addr", AttributeValue.fromS("email@email.com"),
                "username", AttributeValue.fromS("username"),
                "created_ts", AttributeValue.fromN("100"),
                "paying_user", AttributeValue.fromBool(true)
        );

        UpdateItemResponse updateItemResponse = UpdateItemResponse.builder()
                .attributes(attributes)
                .build();

        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(updateItemResponse);

        // Invoke handler
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        // Verify and assert
        assertEquals(200, response.getStatusCode());
        Users user = objectMapper.readValue(response.getBody(), Users.class);
        assertEquals("user123", user.getUser_id());
        assertEquals(true, user.isPaying_user());
    }

    @Test
    public void testUpgradeUserHandler_missingUserId() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setPathParameters(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        String responseBody = response.getBody();
        assert responseBody.contains("user_id not present");
    }

    @Test
    public void testUpgradeUserHandler_exceptionThrown() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setPathParameters(Map.of("user_id", "errorUser"));

        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(RuntimeException.class);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assert response.getBody().contains("Transaction failed");
    }
}