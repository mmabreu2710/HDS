package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CommitMessage {

    // Value
    private TransferMessage transferMessage;

    public CommitMessage(TransferMessage transferMessage) {
        this.transferMessage = transferMessage;
    }

    public TransferMessage getTransferMessage() {
        return transferMessage;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
