package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    private static String nodesConfigPath = "src/main/resources/";
    private static String clientConfigPath = "src/main/client/client_config.json";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            nodesConfigPath += args[1];

            // Create configuration instances
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();
            ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id)).findAny().get();
            ProcessConfig[] allConfigs = nodeConfigs;
            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientConfigPath);
            allConfigs = Arrays.copyOf(allConfigs, allConfigs.length + clientConfigs.length);
            //allConfigs[allConfigs.length - 1] = clientConfig;
            /*for (int i = clientConfigs.length-1; i > 0; i--) {
                allConfigs[allConfigs.length - 1 - i] = clientConfigs[i];
            }*/
            for (int i = 0; i < clientConfigs.length; i++) {
                allConfigs[allConfigs.length - 1 - i] = clientConfigs[i];
            }
            System.out.println("Current Balance:" + nodeConfig.getBalance());
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; is leader: {3}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
                    nodeConfig.isLeader()));

            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), allConfigs,
                    ConsensusMessage.class); //em vez de allConfigs devia ser nodeCofigs não podemos tratar cliete como server, mas se calhar nãoporque o link não inicia o service

            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(linkToNodes, nodeConfig, leaderConfig,
                    nodeConfigs, allConfigs);

            nodeService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
