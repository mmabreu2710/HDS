package pt.ulisboa.tecnico.hdsledger.client.services;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CheckMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CheckResponseMessage;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.client.services.UDPiService;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.text.MessageFormat;
import java.io.IOException;
import java.lang.reflect.Array;
import java.security.PublicKey;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;



public class ClientService implements UDPiService{
    
    private final ProcessConfig clientConfig;
    private final ProcessConfig[] nodesConfig;
    private final Link link;
    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());
    private int numberOfServers;
    private Map<Integer, ArrayList<CheckResponseMessage>> checkResponseMessages = new ConcurrentHashMap<>();
    private Map<Integer, Boolean> alreadyPrintedChecks = new ConcurrentHashMap<>();
    private Map<Integer, ArrayList<TransferResponseMessage>> checkResponseTransferMessages = new ConcurrentHashMap<>();
    private Map<Integer, Boolean> alreadyPrintedTransfer = new ConcurrentHashMap<>();

    private final boolean test8 = false;
    

    public ClientService(Link link, ProcessConfig config, ProcessConfig[] nodesConfig) {

        this.link = link;
        this.clientConfig = config;
        this.nodesConfig = nodesConfig;
        this.numberOfServers =nodesConfig.length - 1;      
    }

    public void listen() {
        // Thread to listen on every request
        new Thread(() -> {
            try {
                while (true) {
                    Message message = link.receive();
    
                    // Separate thread to handle each message
                    new Thread(() -> {
                        try {
                            if (message != null){
                                if ("ACK".equals(message.getType().toString())) {
                                    //save message or smth
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                                clientConfig.getId(), message.getSenderId()));

                                }

                                else if ("CHECKRESPONSE".equals(message.getType().toString())){
                                    CheckResponseMessage checkResponse = (CheckResponseMessage) message;
                                    int checkId = checkResponse.getCheckId();
                                    this.checkResponseMessages.get(checkId).add(checkResponse);
                                    

                                    
                                    Boolean alreadyPrinted = alreadyPrintedChecks.getOrDefault(checkId, Boolean.FALSE);
                                    if (this.checkResponseMessages.get(checkId).size() >= calculateQuorumSize() && !alreadyPrinted){
                                        this.alreadyPrintedChecks.put(checkId, true);
                                        LOGGER.log(Level.INFO, MessageFormat.format("{0} -Current balance of asked Wallet is {1}",
                                                    clientConfig.getId(), Integer.toString(findMostCommonResponse(this.checkResponseMessages.get(checkId)))));
                                    }
                                }

                                else if ("TRANSFER_RESPONSE".equals(message.getType().toString())){
                                    TransferResponseMessage transferResponse = (TransferResponseMessage) message;
                                    int transferId = transferResponse.getTransferId();
                                    this.checkResponseTransferMessages.get(transferId).add(transferResponse);
                                    
                                    Boolean alreadyPrinted = alreadyPrintedTransfer.getOrDefault(transferId, Boolean.FALSE);
                                    if (this.checkResponseTransferMessages.get(transferId).size() >= calculateQuorumSize() && !alreadyPrinted){
                                        this.alreadyPrintedTransfer.put(transferId, true);
                                        int mostCommonValue = findMostCommonResponseInTransfer(this.checkResponseTransferMessages.get(transferId));

                                        if (mostCommonValue == -1){
                                            LOGGER.log(Level.INFO, MessageFormat.format("{0} -Transfer Failed! Current balance is not enough to perform this transfer",
                                                    clientConfig.getId()));
                                        }
                                        else if(mostCommonValue == -2){
                                            LOGGER.log(Level.INFO, MessageFormat.format("{0} -Transfer Failed! Destination account is the same as Source account",
                                                    clientConfig.getId()));
                                        }
                                        else if(mostCommonValue == -3){
                                            LOGGER.log(Level.INFO, MessageFormat.format("{0} -Transfer Failed! Sender id PubKey not the same as Source PubKey",
                                                    clientConfig.getId()));
                                        }
                                        else{
                                            this.clientConfig.setBalance(transferResponse.getFinalAmmount());
                                            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transfer was successful! Current balance of Wallet after transfer is {1}",
                                                        clientConfig.getId(), Integer.toString(mostCommonValue)));
                                        }
                                    }
                                }

                                else {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    clientConfig.getId(), message.getSenderId()));
                                }
                        }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }).start();
    
        // Thread to handle user input
        new Thread(() -> {
            try {
                while (true) {
                    Scanner scanner = new Scanner(System.in);
                    System.out.println("Enter a message to send to the server: ");
                    String message = scanner.nextLine();
                    System.out.println("Sending message: " + message + " to the server...");
                    
                    if (message.matches("^b \\d+$")) {
                        // Handle the case where the input is "b"
                        String[] parts = message.split(" ");
                        int idToBeChecked = Integer.parseInt(parts[1]);
                        checkBalance(link.getPublicKey(Integer.toString(idToBeChecked)));

                    } else if (message.matches("^t \\d+ \\d+$")) {
                        // Handle the case where the input is like "t (id) (amount)"
                        String[] parts = message.split(" ");
                        int idToTransfer = Integer.parseInt(parts[1]);
                        int amount = Integer.parseInt(parts[2]);
                        transfer(link.getPublicKey(clientConfig.getId()), link.getPublicKey(Integer.toString(idToTransfer)), amount);
                    }

                    else{
                        System.out.println("Message: " + message + " is not valid");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
    }
    

    public void checkBalance(PublicKey key) {
        try {
            for (ProcessConfig nodeConfig : nodesConfig) {
                CheckMessage message = new CheckMessage(clientConfig.getId(), Message.Type.CHECK);
                message.setCheckId(this.checkResponseMessages.size());
                String keyString = Base64.getEncoder().encodeToString(key.getEncoded());
                message.setKey(keyString);
                link.send(nodeConfig.getId(), message);
            }
            this.checkResponseMessages.put(this.checkResponseMessages.size(), new ArrayList<CheckResponseMessage>());
            this.alreadyPrintedChecks.put(this.checkResponseMessages.size(), false);

        } catch (Exception e) { // Handle more general exception
            e.printStackTrace();
        }
    }

    public void transfer(PublicKey source, PublicKey destination, int ammount){
        try {
            for (ProcessConfig nodeConfig : nodesConfig) {
                TransferMessage message = new TransferMessage(clientConfig.getId(), Message.Type.TRANSFER);
                message.setTransferId(this.checkResponseTransferMessages.size());
                message.setAmmount(ammount);
                String keyStringSource = Base64.getEncoder().encodeToString(source.getEncoded());
                message.setSource(keyStringSource);
                String keyStringDestination = Base64.getEncoder().encodeToString(destination.getEncoded());
                message.setDestination(keyStringDestination);
                if(test8){ //to simulate byzantine behaviour
                    PublicKey new_source = link.getPublicKey(Integer.toString(2)); //server 2 as the pretend origin
                    message.setSource(Base64.getEncoder().encodeToString(new_source.getEncoded()));
                }
                link.send(nodeConfig.getId(), message);
            }
            this.checkResponseTransferMessages.put(this.checkResponseTransferMessages.size(), new ArrayList<TransferResponseMessage>());
            this.alreadyPrintedTransfer.put(this.checkResponseTransferMessages.size(), false);

        } catch (Exception e) { // Handle more general exception
            e.printStackTrace();
        }
    }
    

    public ProcessConfig getConfig() {
        return this.clientConfig;
    }

    private int calculateMaxFaultyProcesses() {
        int n = this.numberOfServers;
        return (n - 1) / 3;
    }
    private int calculateQuorumSize() { 

        int f = calculateMaxFaultyProcesses();
        int n = this.numberOfServers;
        return ((n + f) / 2) + 1 ;
    }

    public int findMostCommonResponse(ArrayList<CheckResponseMessage> responses) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        int maxCount = 0;
        int mostCommon = -1; // Initialize with an invalid value or consider throwing an exception if responses are empty

        for (CheckResponseMessage response : responses) {
            int responseValue;
            try {
                responseValue = Integer.parseInt(response.getResponse());
            } catch (NumberFormatException e) {
                // Handle the case where the response is not a valid integer
                System.err.println("Invalid integer response: " + response.getResponse());
                continue; // Skip this response
            }

            frequencyMap.put(responseValue, frequencyMap.getOrDefault(responseValue, 0) + 1);
            if (frequencyMap.get(responseValue) > maxCount) {
                maxCount = frequencyMap.get(responseValue);
                mostCommon = responseValue;
            }
        }

        return mostCommon;
    }

    public int findMostCommonResponseInTransfer(ArrayList<TransferResponseMessage> responses) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        int maxCount = 0;
        int mostCommon = -2; // Initialize with an invalid value or consider throwing an exception if responses are empty

        for (TransferResponseMessage response : responses) {
            int responseValue;
            try {
                responseValue = response.getFinalAmmount();
            } catch (NumberFormatException e) {
                // Handle the case where the response is not a valid integer
                System.err.println("Invalid integer response: " + response.getFinalAmmount());
                continue; // Skip this response
            }

            frequencyMap.put(responseValue, frequencyMap.getOrDefault(responseValue, 0) + 1);
            if (frequencyMap.get(responseValue) > maxCount) {
                maxCount = frequencyMap.get(responseValue);
                mostCommon = responseValue;
            }
        }

        return mostCommon;
    }

}