package pt.ulisboa.tecnico.hdsledger.client;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.client.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;

public class Client {
    private static String clientConfigPath = "src/main/resources/";
    private static String nodeConfigPath = "src/main/servers/regular_config.json";
    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());

    public static void main(String[] args) {
        try {
            // Command line arguments
            String id = args[0];
            clientConfigPath += args[1];

            // Create configuration instances
            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientConfigPath);
            ProcessConfig clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId().equals(id)).findAny().get();
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodeConfigPath);
            ProcessConfig[] allConfigs = nodeConfigs;
            allConfigs = Arrays.copyOf(allConfigs, allConfigs.length + 1);
            allConfigs[allConfigs.length - 1] = clientConfig;
            
        
            Link linkToNodes = new Link(clientConfig, clientConfig.getPort(), allConfigs,AppendMessage.class); //consensus??
            
            ClientService clientService = new ClientService(linkToNodes, clientConfig, allConfigs);

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; ",
            clientConfig.getId(), clientConfig.getHostname(), clientConfig.getPort()));
            
            clientService.listen();
            
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }
}