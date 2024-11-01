package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class PrePrepareMessage {
    
    // Value
    private TransferMessage transferMessage;

    public PrePrepareMessage(TransferMessage transferMessage) {
        this.transferMessage = transferMessage;
    }

    public TransferMessage getTransferMessage() {
        return transferMessage;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}   
