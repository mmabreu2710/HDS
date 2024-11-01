package pt.ulisboa.tecnico.hdsledger.service.models;


import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessage;

public class InstanceInfo {

    private int currentRound = 1;
    private int preparedRound = -1;
    private TransferMessage preparedTransferMessage;
    private CommitMessage commitMessage;
    private TransferMessage inputTransferMessage;
    private int committedRound = -1;

    public InstanceInfo(TransferMessage inputTransferMessage) {
        this.inputTransferMessage = inputTransferMessage;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public void setPreparedRound(int preparedRound) {
        this.preparedRound = preparedRound;
    }

   public TransferMessage getPreparedTransferMessage() {
       return preparedTransferMessage;
   }

   public void setPreparedTransferMessage(TransferMessage preparedTransferMessage) {
       this.preparedTransferMessage = preparedTransferMessage;
   }

    public TransferMessage getInputTransferMessage() {
        return inputTransferMessage;
    }

    public void setInputTransferMessage(TransferMessage inputTransferMessage) {
        this.inputTransferMessage = inputTransferMessage;
    }

    public int getCommittedRound() {
        return committedRound;
    }

    public void setCommittedRound(int committedRound) {
        this.committedRound = committedRound;
    }

    public CommitMessage getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(CommitMessage commitMessage) {
        this.commitMessage = commitMessage;
    }
}
