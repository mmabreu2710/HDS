package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CheckResponseMessage extends Message{

    private String response;

    private int checkId;

    public CheckResponseMessage(String senderId, Type type) {
        super(senderId, type);
    }
    
    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
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
