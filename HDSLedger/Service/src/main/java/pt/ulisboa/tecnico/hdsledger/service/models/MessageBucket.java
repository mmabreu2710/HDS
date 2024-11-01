package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.service.PreparedRoundValue;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());
    // Quorum size
    private final int quorumSize;
    // Instance -> Round -> Sender ID -> Consensus message
    private final Map<Integer, Map<Integer, Map<String, ConsensusMessage>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    /*
     * Add a message to the bucket
     * 
     * @param consensusInstance
     * 
     * @param message
     */
    public void addMessage(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
    }

    public void addRoundChangeMessage(RoundChangeMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
    }

    public Optional<String> hasValidPrepareQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            Gson gson = new Gson();
            String transferMessageString = gson.toJson(prepareMessage.getTransferMessage());
            frequency.put(transferMessageString, frequency.getOrDefault(transferMessageString, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public boolean hasValidPrepareQuorumForHighestPrepare(String value, int preparationRound, int instance) {
        if (!bucket.containsKey(instance) || !bucket.get(instance).containsKey(preparationRound)) {
            return false; // Early exit if no messages for the instance and round
        }

        long count = bucket.get(instance).get(preparationRound).values().stream()
            .map(ConsensusMessage::deserializePrepareMessage) // Convert ConsensusMessage to PrepareMessage
            .filter(prepareMessage -> value.equals(prepareMessage.getTransferMessage().toJson())) // Filter by value
            .count(); // Count matching messages

        return count >= quorumSize; // Check if count meets or exceeds quorum size
    }

    public Optional<String> hasValidCommitQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            Gson gson = new Gson();
            String transferMessageString = gson.toJson(commitMessage.getTransferMessage());
            frequency.put(transferMessageString, frequency.getOrDefault(transferMessageString, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Map<String, ConsensusMessage> getMessages(int instance, int round) {
        // Check if the bucket contains the instance key
        if (!bucket.containsKey(instance)) {
            return new HashMap<>(); // Return an empty map if the instance does not exist
        }
        
        Map<Integer, Map<String, ConsensusMessage>> rounds = bucket.get(instance);
        // Check if the rounds map contains the round key
        if (!rounds.containsKey(round)) {
            return new HashMap<>(); // Return an empty map if the round does not exist
        }
        
        // If both keys exist, return the map of messages for the given round
        return rounds.get(round);
    }
}