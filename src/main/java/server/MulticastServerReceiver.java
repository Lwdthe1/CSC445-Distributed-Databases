package server;

import com.sun.org.apache.xpath.internal.operations.Mult;
import server.MulticastServer;
import server.Packet.AppPacket;

import java.io.IOException;
import java.net.DatagramPacket;

/**
 * Created by Lincoln W Daniel on 4/22/2016.
 */
public class MulticastServerReceiver implements Runnable
{
    private final MulticastServer server;

    public MulticastServerReceiver(MulticastServer server)
    {
        this.server = server;
    }

    @Override
    public void run()
    {
        try
        {
            DatagramPacket packet;
            AppPacket receivedPacket;
            byte[] buf;

            // Need to create some way to end the program
            boolean sentinel = true;
            while (!server.getDebugKill())
            {
                buf = new byte[AppPacket.PACKET_SIZE];
                packet = new DatagramPacket(buf, buf.length, server.getGroup(), server.getPort());
                server.getMulticastSocket().receive(packet);
                receivedPacket = new AppPacket(packet.getData());
                server.updateStateAndLeader(receivedPacket);
                if(receivedPacket.getServerId() != server.getId())
                {

                    if (server.isLeader())
                    {
                        System.out.println("calling leader parse " + server.getId());
                        server.leaderParse(receivedPacket);
                    } else if (server.isFollower())
                    {
                        server.followerParse(receivedPacket);
                    } else if (server.isCandidate())
                    {
                        server.candidateParse(receivedPacket);
                    }
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}