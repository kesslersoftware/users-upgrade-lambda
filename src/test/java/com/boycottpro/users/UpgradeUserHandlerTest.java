package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.models.UserBoycotts;
import com.boycottpro.models.UserCauses;
import com.boycottpro.models.Users;
import com.boycottpro.users.model.UpgradeUserForm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        // Mock path parameters
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Path param "s" since client calls /users/s
        event.setPathParameters(Map.of("user_id", "s"));

        // Build UpgradeUserForm input JSON
        UpgradeUserForm form = new UpgradeUserForm();
        form.setUser_boycotts(List.of(
                new UserBoycotts("user123", "comp123", "company name", "cause123",
                        "cause desc", "comp123-cause123", null,
                        "1754141635140")
        ));
        form.setUser_causes(List.of(
                new UserCauses("user123", "cause123", "cause desc", "1754141635140")
        ));

        String bodyJson = new ObjectMapper().writeValueAsString(form);
        event.setBody(bodyJson);

        // Mock DynamoDB batchWriteItem (assume success with no unprocessed items)
        when(dynamoDb.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(BatchWriteItemResponse.builder().unprocessedItems(Collections.emptyMap()).build());

        // Mock user updateItem to set paying_user = true
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

        // Assert response
        assertEquals(200, response.getStatusCode());

        ResponseMessage message = new ObjectMapper().readValue(response.getBody(), ResponseMessage.class);
        assertEquals("User upgraded to premium successfully!", message.getMessage());

        // Verify DynamoDB was called for both batchWriteItem and updateItem
        verify(dynamoDb, times(2)).batchWriteItem(any(BatchWriteItemRequest.class));
        verify(dynamoDb, times(1)).updateItem(any(UpdateItemRequest.class));
    }


    @Test
    public void testUpgradeUserHandler_missingUserId() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        //authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Path param "s" since client calls /users/s
        event.setPathParameters(Map.of("sub","s"));
        UpgradeUserForm form = new UpgradeUserForm();
        form.setUser_boycotts(List.of(
                new UserBoycotts("user123", "comp123", "company name", "cause123",
                        "cause desc", "comp123-cause123", null,
                        "1754141635140")
        ));
        form.setUser_causes(List.of(
                new UserCauses("user123", "cause123", "cause desc", "1754141635140")
        ));

        String bodyJson = new ObjectMapper().writeValueAsString(form);
        event.setBody(bodyJson);
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(401, response.getStatusCode());
        String responseBody = response.getBody();
        assert responseBody.contains("Unauthorized");
    }

    @Test
    public void testUpgradeUserHandler_exceptionThrown() throws JsonProcessingException {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Path param "s" since client calls /users/s
        event.setPathParameters(Map.of("sub","s"));
        UpgradeUserForm form = new UpgradeUserForm();
        form.setUser_boycotts(List.of(
                new UserBoycotts("user123", "comp123", "company name", "cause123",
                        "cause desc", "comp123-cause123", null,
                        "1754141635140")
        ));
        form.setUser_causes(List.of(
                new UserCauses("user123", "cause123", "cause desc", "1754141635140")
        ));

        String bodyJson = new ObjectMapper().writeValueAsString(form);
        event.setBody(bodyJson);
        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(RuntimeException.class);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assert response.getBody().contains("Transaction failed");
    }
}