import org.junit.Before;
import org.junit.Test;
import utils.AppUtils;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class AppUtilsTest
{
    @Test
    public void testBytesToLong() throws Exception
    {
        long testLong = 9000l;
        assertEquals(testLong, AppUtils.bytesToLong(ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(testLong).array()));
    }

    @Test
    public void testLongToBytes() throws Exception
    {
        long testLong = 9000l;
        byte[] testArray = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(testLong).array();
        assertArrayEquals(testArray,AppUtils.longToBytes(testLong));
    }

    @Test
    public void testBytesToInt() throws Exception
    {
        int testInt = 55;
        assertEquals(testInt,AppUtils.bytesToInt(ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(testInt).array()));
    }

    @Test
    public void testIntToBytes() throws Exception
    {
        int testInt = 55;
        byte[] testArray = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(testInt).array();
        assertArrayEquals(testArray,AppUtils.intToBytes(testInt));
    }
}