package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.models.Users;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

public class UpgradeUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpgradeUserHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public UpgradeUserHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            Map<String, String> pathParams = event.getPathParameters();
            String userId = (pathParams != null) ? pathParams.get("user_id") : null;
            if (userId == null || userId.isEmpty()) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "user_id not present");
                String responseBody = objectMapper.writeValueAsString(message);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of("Content-Type", "application/json"))
                        .withBody(responseBody);
            }
            Users updatedUser = upgradeUser(userId) ;
            String responseBody = objectMapper.writeValueAsString(updatedUser);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(responseBody);
        } catch (Exception e) {
            e.printStackTrace();
            return response(500, "Transaction failed: " + e.getMessage());
        }
    }

    private Users upgradeUser(String userId) {
        try {
            // Update the paying_user field to true
            Map<String, AttributeValue> key = Map.of(
                    "user_id", AttributeValue.fromS(userId)
            );

            Map<String, AttributeValueUpdate> updates = Map.of(
                    "paying_user", AttributeValueUpdate.builder()
                            .value(AttributeValue.fromBool(true))
                            .action(AttributeAction.PUT)
                            .build()
            );

            UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                    .tableName("users")
                    .key(key)
                    .attributeUpdates(updates)
                    .returnValues(ReturnValue.ALL_NEW)
                    .build();

            UpdateItemResponse updateResponse = dynamoDb.updateItem(updateRequest);
            Map<String, AttributeValue> updatedItem = updateResponse.attributes();

            // Convert back to Users object
            Users updatedUser = new Users();
            updatedUser.setUser_id(updatedItem.get("user_id").s());
            updatedUser.setEmail_addr(updatedItem.get("email_addr").s());
            updatedUser.setUsername(updatedItem.get("username").s());
            updatedUser.setCreated_ts(Long.parseLong(updatedItem.get("created_ts").n()));
            updatedUser.setPassword_hash("***");
            updatedUser.setPaying_user(updatedItem.get("paying_user").bool());

            // (Optional) map other fields if needed

            return updatedUser;

        } catch (DynamoDbException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to upgrade user: " + e.getMessage(), e);
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String body)  {
        ResponseMessage message = new ResponseMessage(status,body,
                body);
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
    }
}
