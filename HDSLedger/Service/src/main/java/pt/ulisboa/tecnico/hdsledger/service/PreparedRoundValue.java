package pt.ulisboa.tecnico.hdsledger.service;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessage;

public class PreparedRoundValue {
    private int preparedRound;
    private TransferMessage preparedTransferMessage;

    public PreparedRoundValue(int preparedRound, TransferMessage preparedTransferMessage) {
        this.preparedRound = preparedRound;
        this.preparedTransferMessage = preparedTransferMessage;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public TransferMessage getPreparedTransferMessage() {
        return preparedTransferMessage;
    }
}
