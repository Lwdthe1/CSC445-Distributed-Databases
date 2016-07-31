import server.Packet.AppPacket;

import java.net.DatagramPacket;
import java.net.InetAddress;

import static org.junit.Assert.*;

/**
 * Created by dean on 4/20/16.
 */
public class AppPacketTest
{

    @org.junit.Before
    public void setUp() throws Exception
    {

    }

    @org.junit.Test
    public void testPacket() throws Exception
    {
        AppPacket testPacket1 = new AppPacket(1, AppPacket.PacketType.PICTURE, 5, 23, 5, 22, 55,"DATA");
        DatagramPacket testDatagram = testPacket1.getDatagram(InetAddress.getByName("wolf.cs.oswego.edu"), 4445);
        AppPacket testPacket2 = new AppPacket(testDatagram.getData());

        assertEquals(new String(testPacket1.getData()),new String(testPacket2.getData()));
        assertEquals(testPacket1.getLeaderId(),testPacket2.getLeaderId());
        assertEquals(testPacket1.getMember(),testPacket2.getMember());
        assertEquals(testPacket1.getSequenceNumber(),testPacket2.getSequenceNumber());
        assertEquals(testPacket1.getServerId(),testPacket2.getServerId());
        assertEquals(testPacket1.getTerm(),testPacket2.getTerm());
        assertEquals(testPacket1.getType(),testPacket2.getType());
        assertEquals(testPacket1.getLogIndex(),testPacket2.getLogIndex());
    }

    @org.junit.Test
    public void testGetDatagram() throws Exception
    {

    }

    @org.junit.Test
    public void testGetData() throws Exception
    {

    }

    @org.junit.Test
    public void testGetServerId() throws Exception
    {

    }

    @org.junit.Test
    public void testGetType() throws Exception
    {

    }

    @org.junit.Test
    public void testGetLeaderId() throws Exception
    {

    }

    @org.junit.Test
    public void testGetTerm() throws Exception
    {

    }

    @org.junit.Test
    public void testGetMember() throws Exception
    {

    }

    @org.junit.Test
    public void testGetSeq() throws Exception
    {

    }
}