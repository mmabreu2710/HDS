package pt.ulisboa.tecnico.hdsledger.communication.builder;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;

public class RoundChangeMessageBuilder {
    private final RoundChangeMessage instance;

    public RoundChangeMessageBuilder(String senderId, Message.Type type) {
        instance = new RoundChangeMessage(senderId, type);
    }

    public RoundChangeMessageBuilder setConsensusInstance(int consensusInstance) {
        instance.setConsensusInstance(consensusInstance);
        return this;
    }

    public RoundChangeMessageBuilder setRound(int round) {
        instance.setRound(round);
        return this;
    }

    public RoundChangeMessageBuilder setPreparationRound(int preparationRound) {
        instance.setPreparationRound(preparationRound);
        return this;
    }

    public RoundChangeMessageBuilder setPreparedTransferMessage(String preparedTransferMessage) {
        instance.setMessage(preparedTransferMessage);
        return this;
    }

    public RoundChangeMessage build() {
        return instance;
    }
}
