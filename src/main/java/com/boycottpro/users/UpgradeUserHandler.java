package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.models.UserBoycotts;
import com.boycottpro.models.UserCauses;
import com.boycottpro.models.Users;
import com.boycottpro.users.model.UpgradeUserForm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            UpgradeUserForm form = objectMapper.readValue(event.getBody(), UpgradeUserForm.class);
            if (form == null || form.getUser_boycotts() == null || form.getUser_causes() == null) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "Invalid request body or missing fields");
                String responseBody = objectMapper.writeValueAsString(message);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of("Content-Type", "application/json"))
                        .withBody(responseBody);
            }
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
            if (updatedUser == null) {
                return response(404, "User not found or upgrade failed");
            }
            // Insert user boycotts and causes if provided
            if (form.getUser_boycotts() != null && !form.getUser_boycotts().isEmpty()) {
                for(UserBoycotts boycott : form.getUser_boycotts()) {
                    boycott.setUser_id(userId);
                    String companyId = boycott.getCompany_id();
                    if(boycott.getPersonal_reason()!=null && !boycott.getPersonal_reason().isEmpty()) {
                        String companyCauseId = boycott.getPersonal_reason() + "#" + companyId;
                        boycott.setCompany_cause_id(companyCauseId);
                    } else {
                        String causeId = boycott.getCause_id();
                        String companyCauseId = companyId + "#" + causeId;
                        boycott.setCompany_cause_id(companyCauseId);
                    }
                }
                insertUserBoycotts(form.getUser_boycotts());
            }
            if (form.getUser_causes() != null && !form.getUser_causes().isEmpty()) {
                for(UserCauses userCauses : form.getUser_causes()) {
                    userCauses.setUser_id(userId);
                }
                insertUserCauses(form.getUser_causes());
            }
            String response = "User upgraded to premium successfully!";
            return response(200, response);
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

    private void batchWrite(String tableName, List<WriteRequest> writeRequests) {
        final int BATCH_SIZE = 25;
        for (int i = 0; i < writeRequests.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, writeRequests.size());
            List<WriteRequest> batch = writeRequests.subList(i, end);

            Map<String, List<WriteRequest>> requestItems = new HashMap<>();
            requestItems.put(tableName, batch);

            BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                    .requestItems(requestItems)
                    .build();

            dynamoDb.batchWriteItem(batchRequest);
        }
    }
    private void insertUserBoycotts(List<UserBoycotts> records) {
        if (records == null || records.isEmpty()) return;

        List<WriteRequest> writeRequests = new ArrayList<>();

        for (UserBoycotts boycott : records) {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("user_id", AttributeValue.fromS(boycott.getUser_id()));
            item.put("company_id", AttributeValue.fromS(boycott.getCompany_id()));
            if (boycott.getCause_id() != null) {
                item.put("cause_id", AttributeValue.fromS(boycott.getCause_id()));
            }
            if (boycott.getCompany_cause_id() != null) {
                item.put("company_cause_id", AttributeValue.fromS(boycott.getCompany_cause_id()));
            }
            if (boycott.getCompany_name() != null) {
                item.put("company_name", AttributeValue.fromS(boycott.getCompany_name()));
            }
            if (boycott.getCause_desc() != null) {
                item.put("cause_desc", AttributeValue.fromS(boycott.getCause_desc()));
            }
            if (boycott.getPersonal_reason() != null) {
                item.put("personal_reason", AttributeValue.fromS(boycott.getPersonal_reason()));
            }
            if (boycott.getTimestamp() != null) {
                item.put("timestamp", AttributeValue.fromS(boycott.getTimestamp()));
            }

            writeRequests.add(WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(item).build())
                    .build());
        }

        batchWrite("user_boycotts", writeRequests);
    }

    private void insertUserCauses(List<UserCauses> records) {
        if (records == null || records.isEmpty()) return;

        List<WriteRequest> writeRequests = new ArrayList<>();

        for (UserCauses cause : records) {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("user_id", AttributeValue.fromS(cause.getUser_id()));
            item.put("cause_id", AttributeValue.fromS(cause.getCause_id()));
            if (cause.getCause_desc() != null) {
                item.put("cause_desc", AttributeValue.fromS(cause.getCause_desc()));
            }
            if (cause.getTimestamp() != null) {
                item.put("timestamp", AttributeValue.fromS(cause.getTimestamp()));
            }

            writeRequests.add(WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(item).build())
                    .build());
        }

        batchWrite("user_causes", writeRequests);
    }
}
