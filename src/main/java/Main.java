import server.MulticastServer;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Main
{
    private static MulticastServer[] servers = new MulticastServer[5];

    public static void main(String[] args) throws IOException, InterruptedException
    {
        //create the new multicast serversin group

        /*we use a countdown latch for aesthetics only.
        We want the guis to show up in our desktop navigation tray
        in order from serverId 0 through the last.*/
        final CountDownLatch[] latches = {new CountDownLatch(1)};

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    servers[0] = new MulticastServer(0, 3, latches[0], 0, 0);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
        System.out.println("before1");
        latches[0].await();
        System.out.println("after1");
        latches[0] = new CountDownLatch(1);
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    servers[1] = new MulticastServer(1, 3, latches[0], 950, 0);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
        System.out.println("before2");
        latches[0].await();
        System.out.println("after2");
        latches[0] = new CountDownLatch(1);
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    servers[2] = new MulticastServer(2, 3, latches[0], 0, 550);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
        latches[0].await();

        latches[0] = new CountDownLatch(1);
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    servers[3] = new MulticastServer(3, 3, latches[0], 950, 550);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
        latches[0].await();

        latches[0] = new CountDownLatch(1);
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    servers[4] = new MulticastServer(4, 3, latches[0], 475, 225);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();

        latches[0].await();
        for (int i = 0; i < servers.length; i++)
        {
            System.out.println(servers[i]);
            servers[i].init();
        }


        System.out.println("All servers' GUI's created and displaying.");
    }
}
