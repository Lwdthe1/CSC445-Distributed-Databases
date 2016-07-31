package server;

import javafx.util.Pair;
import server.MulticastServer;
import server.Packet.AppPacket;
import server.Packet.LeaderPacket;

import java.io.IOException;

/**
 * Created by Lincoln W Daniel on 4/22/2016.
 */
public class MulticastServerSender implements Runnable
{
    private final MulticastServer server;

    public MulticastServerSender(MulticastServer server)
    {
        this.server = server;
    }

    @Override
    public void run()
    {
        while (!server.getDebugKill())
        {
            Pair<String,String> clientMessageToSend = server.getClientMessageToSend();
            if (clientMessageToSend != null && !clientMessageToSend.getValue().isEmpty() && !clientMessageToSend.getKey().isEmpty())
            {
                try
                {
                    AppPacket outgoingPacket = new AppPacket(server.getId(), AppPacket.PacketType.COMMENT, server.getLeaderId(), server.getTerm(), -1, LeaderPacket.getNextSequenceNumber(), -1,AppPacket.PacketType.COMMENT.ordinal(), clientMessageToSend.getKey() + " " +clientMessageToSend.getValue());
                    server.getOutgoingLocalStorage().put(outgoingPacket.getSequenceNumber(), new LeaderPacket(outgoingPacket));

                    server.consoleMessage("Sending " + outgoingPacket.toString(), 2);
                    server.getMulticastSocket().send(outgoingPacket.getDatagram(server.getGroup(), server.getPort()));
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}

