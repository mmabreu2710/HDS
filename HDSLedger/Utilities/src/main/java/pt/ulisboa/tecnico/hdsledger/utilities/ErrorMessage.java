package pt.ulisboa.tecnico.hdsledger.utilities;

import java.security.Key;

public enum ErrorMessage {
    ConfigFileNotFound("The configuration file is not available at the path supplied"),
    ConfigFileFormat("The configuration file has wrong syntax"),
    NoSuchNode("Can't send a message to a non existing node"),
    SocketSendingError("Error while sending message"),
    CannotOpenSocket("Error while opening socket"),
    KeyFileReadError("Error while reading the key file"),
    KeyFileNotFound("The key file is not available at the path supplied");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
