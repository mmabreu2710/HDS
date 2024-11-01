package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

public class SignedMessage implements Serializable {

    private Message message;
    private String sign;

    public SignedMessage(Message message, String sign) {
        this.message = message;
        this.sign = sign;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String getSignature() {
        return sign;
    }

    public void setSignature(String signature) {
        this.sign = signature;
    }
}
