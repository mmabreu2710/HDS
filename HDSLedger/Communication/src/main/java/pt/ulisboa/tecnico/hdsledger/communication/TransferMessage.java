package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class TransferMessage extends Message{

    private String Source;
    private String destination;
    private int ammount;
    private int transferId;

    public TransferMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public String getSource() {
        return Source;
    }

    public void setSource(String source) {
        Source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public int getAmmount() {
        return ammount;
    }

    public void setAmmount(int ammount) {
        this.ammount = ammount;
    }

    public int getTransferId() {
        return transferId;
    }

    public void setTransferId(int transferId) {
        this.transferId = transferId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
