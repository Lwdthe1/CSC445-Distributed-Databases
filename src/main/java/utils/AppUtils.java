package utils;

import java.nio.ByteBuffer;

public class AppUtils
{
    private AppUtils()
    {
        throw new AssertionError("Class should not be instantiated");
    }

    public static long bytesToLong(byte[] bytes)
    {

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getLong();
    }

    public static byte[] longToBytes(long x)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public static int bytesToInt(byte[] bytes)
    {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getInt();
    }

    public static byte[] intToBytes(int x)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(x);
        return buffer.array();
    }


}
