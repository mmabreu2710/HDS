package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RoundChangeMessage extends ConsensusMessage{

    private int preparationRound;


    public RoundChangeMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public int getPreparationRound() {
        return preparationRound;
    }

    public void setPreparationRound(int preparationRound) {
        this.preparationRound = preparationRound;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

}   
