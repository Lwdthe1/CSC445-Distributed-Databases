package server;

import server.Packet.AppPacket;
import server.Packet.LeaderPacket;

import java.io.IOException;
import java.util.Random;

/**
 * Created by zsabin on 4/28/16.
 */
public class TimeoutThread implements Runnable
{
    public static final int MIN_TIMEOUT = 4000;
    public static final int MAX_TIMEOUT = 4500;
    private final MulticastServer server;

    public TimeoutThread(MulticastServer server)
    {
        this.server = server;
    }

    @Override
    public void run()
    {
        while (!server.isLeader() && !server.getDebugKill())
        {
            server.resetTimeout();
            long timeLeft;
            do {
                timeLeft = System.currentTimeMillis() - server.getStartTime();
                if(server.isLeader() || server.getDebugKill()){
                    server.consoleMessage("Killing Timeout Thread", 2);
                    return;
                }
            }
            while (timeLeft < server.getTimeout());

            server.consoleMessage("Server timed out", 2);
            if (server.isLeader()) {return; }
            server.changeServerState(MulticastServer.ServerState.CANIDATE);

            //start election
            server.consoleMessage("Sending Vote Requests", 2);
            AppPacket voteRequest = new AppPacket(server.getId(), AppPacket.PacketType.VOTE_REQUEST, server.getLeaderId(),
                    server.getTerm(), -1, LeaderPacket.getNextSequenceNumber(), -1,AppPacket.PacketType.VOTE_REQUEST.ordinal(), "");

            try
            {
                server.getMulticastSocket().send(voteRequest.getDatagram(server.getGroup(), server.getPort()));
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        server.consoleMessage("Killing Timeout Thread", 2);
    }
}
