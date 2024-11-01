package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class TransferResponseMessage extends Message {
    private int finalAmmount;
    private int transferId;

    public TransferResponseMessage(String senderId, Type type) {
        super(senderId, type);
    }
    
    public int getFinalAmmount() {
        return finalAmmount;
    }

    public void setFinalAmmount(int finalAmmount) {
        this.finalAmmount = finalAmmount;
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
