package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.CheckMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CheckResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.builder.RoundChangeMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.PreparedRoundValue;
import pt.ulisboa.tecnico.hdsledger.service.Timer;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;


public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    // Nodes of server configurations
    private final ProcessConfig[] nodesConfig;

    // Current node is leader
    private ProcessConfig config;
    // Leader configuration
    private ProcessConfig leaderConfig;

    // Link to communicate with nodes
    private final Link link;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;
    // Store if already received round Change for a given <consensus, round>
    private final MessageBucket receivedRoundChange;

    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    //Timer
    private Timer timer = new Timer(5000, 2);
    //number of Servers
    private int numberOfServers;
    //number of toleratedFaults
    private int numberToleratedFaults;
    //Quorum size
    private int QuorumSize;
    // Ledger (for now, just a list of strings)
    private ArrayList<TransferMessage> ledger = new ArrayList<TransferMessage>();
    //balances of all processes
    private Map<PublicKey, Integer> balanceOfNodes = new ConcurrentHashMap<>();
    //ALL nodes clients and servers
    private final ProcessConfig[] allNodesConfig;

    private ArrayList<TransferMessage> receivedTransferMessages = new ArrayList<TransferMessage>();

    private final boolean test1 = false;
    private final boolean test2 = false;
    private final boolean test3 = false;
    private final boolean test4 = false;
    private final boolean test5 = false;
    private final boolean test6 = false;
    private final boolean test7 = false;

    public NodeService(Link link, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig, ProcessConfig[] allNodesConfig) {

        this.link = link;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;
        this.allNodesConfig = allNodesConfig;
        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
        this.receivedRoundChange = new MessageBucket(nodesConfig.length);
        this.numberOfServers = this.nodesConfig.length;
        this.numberToleratedFaults = calculateMaxFaultyProcesses();
        this.QuorumSize = calculateQuorumSize();

        try{
            for (ProcessConfig node: this.allNodesConfig){
                this.balanceOfNodes.put(link.getPublicKey(node.getId()), node.getBalance());
            }
        }catch(IOException | NoSuchAlgorithmException | InvalidKeySpecException e){
            e.printStackTrace();
        }

    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public ArrayList<TransferMessage> getLedger() {
        return this.ledger;
    }

    private boolean isLeader(String id) {
        return this.leaderConfig.getId().equals(id);
    }
 
    public ConsensusMessage createConsensusMessage(TransferMessage transferMessage, int instance, int round) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(transferMessage);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();

        return consensusMessage;
    }

    public RoundChangeMessage createRoundChangeMessage(TransferMessage transferMessage, int instance, int round, int preparationRound){

        RoundChangeMessage roundChangeMessage = new RoundChangeMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(consensusInstance.get())
                .setRound(round)
                .setPreparationRound(preparationRound)
                .setPreparedTransferMessage(transferMessage.toJson())
                .build();
        return roundChangeMessage;

    }

    /*
     * Start an instance of consensus for a value
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
    public void startConsensus(TransferMessage transferMessage) {

        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(transferMessage));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
            return;
        }

        // Only start a consensus instance if the last one was decided
        // We need to be sure that the previous value has been decided
        while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Leader broadcasts PRE-PREPARE message
        InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
        if (this.config.isLeader()) {
            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
            if (test3 && this.config.getId().equals("1")){
                transferMessage.setAmmount(1000000);
            }
            if (test7 && this.config.getId().equals("1")){
                transferMessage.setAmmount(1000000);
            }
            if (test5 && this.config.getId().equals("1")) {
                try{
                PublicKey dest = link.getPublicKey(Integer.toString(1));
                transferMessage.setDestination(Base64.getEncoder().encodeToString(dest.getEncoded()));
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            this.link.broadcastToServers(this.createConsensusMessage(transferMessage, localConsensusInstance, instance.getCurrentRound()));
        } else {
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }
        this.timer.start(instance.getCurrentRound(), this::onTimerExpired);
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     *
     * @param message Message to be handled
     */
    public void uponPrePrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        TransferMessage transferMessage = prePrepareMessage.getTransferMessage();

        if (!containsTransferMessage(transferMessage)){
            return;
        
        }

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Verify if pre-prepare was sent by leader
        if (!isLeader(senderId) && justifyPrePrepare())
            return;

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(transferMessage));

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getTransferMessage());

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(prepareMessage.toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();
        this.timer.start(this.instanceInfo.get(this.consensusInstance.get()).getCurrentRound(), this::onTimerExpired);
        this.link.broadcastToServers(consensusMessage);
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        TransferMessage transferMessage = prepareMessage.getTransferMessage();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance values
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(transferMessage));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        // Late prepare (consensus already ended for other nodes) only reply to him (as
        // an ACK)
        if (instance.getPreparedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setReplyTo(senderId)
                    .setReplyToMessageId(message.getMessageId())
                    .setMessage(instance.getCommitMessage().toJson())
                    .build();

            link.send(senderId, m);
            return;
        }

        // Find value with valid quorum
        Optional<String> preparedTransferMessageString = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedTransferMessageString.isPresent() && instance.getPreparedRound() < round) {
            Gson gson = new Gson();
            TransferMessage preparedTransferMessage = gson.fromJson(preparedTransferMessageString.get(), transferMessage.getClass());
            instance.setPreparedTransferMessage(preparedTransferMessage);
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            CommitMessage c = new CommitMessage(preparedTransferMessage);
            instance.setCommitMessage(c);

            sendersMessage.forEach(senderMessage -> {
                ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setReplyTo(senderMessage.getSenderId())
                        .setReplyToMessageId(senderMessage.getMessageId())
                        .setMessage(c.toJson())
                        .build();

                link.send(senderMessage.getSenderId(), m);
            });
        }
    }



    /*
     * Handle commit messages and decide if there is a valid quorum
     *
     * @param message Message to be handled
     */
    public synchronized void uponCommit(ConsensusMessage message) {
        this.timer.stop();
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare message
            MessageFormat.format(
                    "{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                    config.getId(), message.getSenderId(), consensusInstance, round);
            return;
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        Optional<String> commitValue = commitMessages.hasValidCommitQuorum(config.getId(),
                consensusInstance, round);
        TransferMessage ExampleTransferMessage = new TransferMessage(message.getSenderId(), Message.Type.TRANSFER);

        if (commitValue.isPresent() && instance.getCommittedRound() < round) {

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            Gson gson = new Gson();
            TransferMessage preparedTransferMessage = gson.fromJson(commitValue.get(),ExampleTransferMessage.getClass() );
            instance.setPreparedTransferMessage(preparedTransferMessage);

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized(ledger) {
                // Ensure the ledger has capacity to accommodate the current instance
                ledger.ensureCapacity(consensusInstance);
                
                // Add placeholder TransferMessage objects until the ledger has the required size
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add(new TransferMessage("0", Message.Type.TRANSFER)); // Use an appropriate constructor call
                }
                
                // Add the new TransferMessage object at the specific position
                ledger.add(consensusInstance - 1, preparedTransferMessage);
                
                // Log the current ledger state
                LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Current Ledger: {1}",
                            config.getId(), ledger.stream().map(TransferMessage::toJson).collect(Collectors.joining(", "))));
            }
            PublicKey leader = null;
            try{
                leader = link.getPublicKey(leaderConfig.getId());
            }catch(Exception e){
                e.printStackTrace();
            }
            PublicKey source = decodePubKey(preparedTransferMessage.getSource());
            PublicKey destination = decodePubKey(preparedTransferMessage.getDestination());
            int ammountInSource = balanceOfNodes.get(source);
            int ammountInDestination = balanceOfNodes.get(destination);
            ammountInSource= ammountInSource - preparedTransferMessage.getAmmount() - 1;
            ammountInDestination = ammountInDestination + preparedTransferMessage.getAmmount();
            balanceOfNodes.put(source, ammountInSource);
            balanceOfNodes.put(destination, ammountInDestination);
            int ammountToLeader = balanceOfNodes.get(leader) + 1;
            balanceOfNodes.put(leader, ammountToLeader);
            TransferResponseMessage response = new TransferResponseMessage(this.config.getId(), Message.Type.TRANSFER_RESPONSE);
            response.setFinalAmmount(ammountInSource);
            response.setTransferId(preparedTransferMessage.getTransferId());

            link.send(preparedTransferMessage.getSenderId(), response);

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, true));
            
        }
    }

    public synchronized void uponRoundChange(ConsensusMessage message) {
        InstanceInfo instance = this.instanceInfo.get(consensusInstance.get());
        int currentRound = instance.getCurrentRound();
        TransferMessage v;
        //adicionar mensagens de round Change
        this.receivedRoundChange.addMessage(message);
        if (this.config.isLeader() && justifyRoundChange()){
            if (HighestPrepared(currentRound) != null){
                v = HighestPrepared(currentRound).getPreparedTransferMessage();
            }
            else{
                v = instance.getInputTransferMessage();
            }
            if (test7 && this.config.getId().equals("1")){
                this.link.broadcastToServers(this.createConsensusMessage(v, consensusInstance.get(), instance.getCurrentRound()+1999));
            }
            else{
                this.link.broadcastToServers(this.createConsensusMessage(v, consensusInstance.get(), instance.getCurrentRound()));
            }
            
        }
        
        else if (containsfplusoneMessagesWithRoundGreaterThan()) {
            System.out.println("Started Round Change...");
            
            int lowestRound = findLowestRound(consensusInstance.get(), currentRound);
            instance.setCurrentRound(lowestRound);
            changeLeader();
            this.timer.start(instance.getCurrentRound(), this::onTimerExpired);
            this.link.broadcastToServers(this.createRoundChangeMessage(instance.getPreparedTransferMessage(), this.consensusInstance.get(), lowestRound, instance.getPreparedRound()));
            
        }
        else if (lastDecidedConsensusInstance.get() >= message.getConsensusInstance() - 1){
                int commitedRoundForMessageInstance = instanceInfo.get(message.getConsensusInstance()).getCommittedRound();
                for (ConsensusMessage commitMessage : this.commitMessages.getMessages(message.getConsensusInstance(),commitedRoundForMessageInstance).values()){
                    link.send(message.getSenderId(), commitMessage);
                }
        }
    }

    public synchronized void uponCheck(CheckMessage message) {
        PublicKey publicKey = decodePubKey(message.getKey());

        int balanceRequired = balanceOfNodes.get(publicKey);
        CheckResponseMessage response = new CheckResponseMessage(this.config.getId(), Message.Type.CHECKRESPONSE);
        if (test1 && isLeader(this.config.getId())) {
            response.setResponse(Integer.toString(balanceRequired + 100));
        }
        else if(test2 && !isLeader(this.config.getId()) && this.config.getId().equals("2")) {
            response.setResponse(Integer.toString(balanceRequired + 1000000));
        }
        else{
            response.setResponse(Integer.toString(balanceRequired));
        }
        
        response.setCheckId(message.getCheckId());
        link.send(message.getSenderId(),response);
    }
    public PublicKey decodePubKey(String codedKey){
        try{
            byte[] publicBytes = Base64.getDecoder().decode(codedKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            return publicKey;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public synchronized void uponTransfer(TransferMessage message){        
        int ammountTotransfer = message.getAmmount();
        PublicKey sourcePubKey = decodePubKey(message.getSource());
        PublicKey senderPubKey = null;
        try{
            senderPubKey= link.getPublicKey(message.getSenderId());
        }catch(Exception e){
            e.printStackTrace();
        }
        if ((ammountTotransfer + 1) > balanceOfNodes.get(sourcePubKey)){
            TransferResponseMessage response = new TransferResponseMessage(this.config.getId(), Message.Type.TRANSFER_RESPONSE);
            response.setFinalAmmount(-1);
            response.setTransferId(message.getTransferId());
            link.send(message.getSenderId(),response);
        }
        else if (message.getSource().equals(message.getDestination())){
            TransferResponseMessage response = new TransferResponseMessage(this.config.getId(), Message.Type.TRANSFER_RESPONSE);
            response.setFinalAmmount(-2);
            response.setTransferId(message.getTransferId());
            link.send(message.getSenderId(),response);
        }
        else if(!sourcePubKey.equals(senderPubKey)){
            TransferResponseMessage response = new TransferResponseMessage(this.config.getId(), Message.Type.TRANSFER_RESPONSE);
            response.setFinalAmmount(-3);
            response.setTransferId(message.getTransferId());
            link.send(message.getSenderId(),response);
        }
        else{
            if (test4 && !isLeader(this.config.getId()) && this.config.getId().equals("2")) {
                message.setAmmount(1000000);
            }   
            if (test6 && !isLeader(this.config.getId()) && this.config.getId().equals("2")) {
                try{
                PublicKey dest = link.getPublicKey(Integer.toString(2));
                message.setDestination(Base64.getEncoder().encodeToString(dest.getEncoded()));
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            receivedTransferMessages.add(message);
            startConsensus(message);
        }


    }

    @Override
    public void listen() {
        

        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        
                        Message message = link.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            
                                switch (message.getType()) {
                                    
                                    case CHECK ->
                                        uponCheck((CheckMessage) message);
                                    
                                    case TRANSFER ->
                                        uponTransfer((TransferMessage) message);

                                    case PRE_PREPARE ->
                                        uponPrePrepare((ConsensusMessage) message);

                                    case PREPARE ->
                                        uponPrepare((ConsensusMessage) message);

                                    case COMMIT ->
                                        uponCommit((ConsensusMessage) message);
                                    
                                    case ROUND_CHANGE ->
                                        uponRoundChange((ConsensusMessage) message);                                 
                                
                                    case ACK ->
                                        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                                config.getId(), message.getSenderId()));

                                    case IGNORE ->
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                        config.getId(), message.getSenderId()));

                                    default ->
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Received unknown message from {1}",
                                                        config.getId(), message.getSenderId()));

                                }
                            

                        }).start();
                          
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void onTimerExpired() {
        // Logic to execute when timer expires, which triggers round change
        try{
            System.out.println("Timer Expired Started Round Change...");
            InstanceInfo instance = this.instanceInfo.get(consensusInstance.get());
            int currentRound = instance.getCurrentRound();
            int incremmentedRound = currentRound + 1; 
            instance.setCurrentRound(incremmentedRound);
            changeLeader();
            this.timer.start(instance.getCurrentRound(), this::onTimerExpired);
            this.link.broadcastToServers(this.createRoundChangeMessage(instance.getInputTransferMessage(), this.consensusInstance.get(), incremmentedRound, instance.getPreparedRound()));
        }catch( Exception e){
            e.printStackTrace();
        }

        
    }
    private int findLowestRound(int instance, int round) {
        // Assuming there's at least one message in the list
        // and initializing with the maximum integer value
        int lowestRound = Integer.MAX_VALUE;
        Collection<ConsensusMessage> consensusMessages = this.receivedRoundChange.getMessages(instance, round).values();

        for (ConsensusMessage message : consensusMessages) {
            if (message.getRound() < lowestRound) {
                lowestRound = message.getRound();
            }
        }

        // In case there are no messages, you might want to return a default value or throw an exception
        if (lowestRound == Integer.MAX_VALUE) {
            lowestRound = -1; // or handle this scenario differently based on your application needs
        }

        return lowestRound;
    }

    private boolean containsfplusoneMessagesWithRoundGreaterThan() {
        int count = 0; // Counter for messages with round > roundValue
        InstanceInfo instance = this.instanceInfo.get(consensusInstance.get());
        int currentRound = instance.getCurrentRound();
        Collection<ConsensusMessage> consensusMessages = this.receivedRoundChange.getMessages(consensusInstance.get(), currentRound).values();

        for (ConsensusMessage message : consensusMessages) {
            if (message instanceof RoundChangeMessage) {
                if (message.getRound() > currentRound) {
                    count++; // Increment counter if message's round is greater than roundValue
                    if (count >= numberToleratedFaults) {
                        // Return true if at least f + 1 messages found
                        return true;
                    }
                }
            }
        }

        // Return false if fewer than two such messages are found
        return false;
    }

    private boolean validQuorumOfRoundChange(){
        int count = 0; // Counter for messages of current round
        InstanceInfo instance = this.instanceInfo.get(consensusInstance.get());
        int currentRound = instance.getCurrentRound();
        Collection<ConsensusMessage> consensusMessages = this.receivedRoundChange.getMessages(consensusInstance.get(), currentRound).values();

        for (ConsensusMessage message : consensusMessages) {
            if (message instanceof RoundChangeMessage) {
                if (message.getRound() == currentRound) {
                    count++; // Increment counter if message's round is greater than roundValue
                    if (count >= this.QuorumSize) {
                        // Return true if at least f + 1 messages found
                        return true;
                    }
                }
            }
        }

        // Return false if fewer than two such messages are found
        return false;
    }

    private  PreparedRoundValue HighestPrepared(int currentRound) {
        int highestPreparedRound = -1; // Initialize with -1 to indicate no rounds found yet
        String highestPreparedValue = null;
        Collection<ConsensusMessage> consensusMessages = this.receivedRoundChange.getMessages(consensusInstance.get(), currentRound).values();

        for (ConsensusMessage consensusMessage : consensusMessages) {
            if (consensusMessage instanceof RoundChangeMessage) {
                RoundChangeMessage message = (RoundChangeMessage) consensusMessage;
                int currentPreparedRound = message.getPreparationRound();
                if (currentPreparedRound > highestPreparedRound) {
                    highestPreparedRound = currentPreparedRound;
                    highestPreparedValue = message.getMessage();
                }
            }
        }

        if (highestPreparedRound == -1) {
            return null; // or handle differently if no prepared round was found
        }
        Gson gson = new Gson();
        TransferMessage exampleTransferMessage = new TransferMessage("0", Message.Type.TRANSFER);
        TransferMessage transferMessage = gson.fromJson(highestPreparedValue, exampleTransferMessage.getClass());

        return new PreparedRoundValue(highestPreparedRound, transferMessage);
    }

    private int calculateMaxFaultyProcesses() {
        int n = this.numberOfServers;
        return (n - 1) / 3;
    }
    private int calculateQuorumSize() { 
        int f = this.numberToleratedFaults;
        int n = this.numberOfServers;
        return ((n + f) / 2) + 1 ;
    }
    private boolean justifyRoundChange(){
        boolean noPreparedRoundOrValue = arePreparationRoundAndValueNull();
        if (noPreparedRoundOrValue == true){
            return true;
        }

        
        int consensusInstance = this.consensusInstance.get();
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);
        int round = instance.getCurrentRound();
        PreparedRoundValue preparedRoundValue = HighestPrepared(round);
        if (receivedRoundChange.hasValidPrepareQuorumForHighestPrepare(preparedRoundValue.getPreparedTransferMessage().toJson(), preparedRoundValue.getPreparedRound(), consensusInstance)) {
            return true;
        
        }
        return false;

    }

    private boolean justifyPrePrepare(){
        int consensusInstance = this.consensusInstance.get();
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);
        int round = instance.getCurrentRound();

        boolean justifyRoundChangeBool = false;
        
        justifyRoundChangeBool = justifyRoundChange();

        return (round == 1) || (justifyRoundChangeBool);
    }

    private boolean arePreparationRoundAndValueNull() {
        int consensusInstance = this.consensusInstance.get();
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);
        int round = instance.getCurrentRound();

        Collection<ConsensusMessage> consensusMessages = this.receivedRoundChange.getMessages(consensusInstance, round).values();

        for (ConsensusMessage consensusMessage : consensusMessages) {
            if (consensusMessage instanceof RoundChangeMessage) {
                RoundChangeMessage message = (RoundChangeMessage) consensusMessage;
                if (message.getConsensusInstance() == consensusInstance && 
                    message.getRound() == round &&
                    (message.getPreparationRound() != -1 && message.getMessage() != null)) {
                    return false;
                }
            }
        }
        // If all messages from the specified consensus instance have null preparation round and value, return true.
        return true;
    }
    private void changeLeader(){
        int consensusInstance = this.consensusInstance.get();
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);
        int round = instance.getCurrentRound();
        int leaderToBe = round % this.numberOfServers;
        if (leaderToBe == 0){
            leaderToBe = 4;
        }

        leaderConfig = findProcessConfigById(leaderToBe);
        leaderConfig.setIsLeader(true);

        if (leaderToBe == Integer.parseInt(config.getId())){
            this.config.setIsLeader(true);
        }
        else{
            this.config.setIsLeader(false);
        }
    }

    public boolean containsTransferMessage(TransferMessage transferMessage){
        for (TransferMessage message: receivedTransferMessages){
            if (transferMessage.getAmmount() == message.getAmmount()){
                if (transferMessage.getTransferId() == message.getTransferId()){
                    if (transferMessage.getSource().equals(message.getSource())){
                        if (transferMessage.getDestination().equals(message.getDestination())){
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public ProcessConfig findProcessConfigById(int Id) {
        String targetId = Integer.toString(Id);
        for (ProcessConfig config : this.nodesConfig) {
            if (config.getId().equals(targetId)) {
                return config; // Return the matching ProcessConfig
            }
        }
        return null; // No match found
    }

}
