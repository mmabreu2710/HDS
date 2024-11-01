package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CheckMessage extends Message{
    // Value
    private String key;

    private int checkId;

    public CheckMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }

    public int getCheckId() {
        return checkId;
    }

    public void setCheckId(int checkId) {
        this.checkId = checkId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
