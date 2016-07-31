package server;

import javafx.util.Pair;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpException;
import server.Packet.AppPacket;
import server.Packet.LeaderPacket;
import utils.WebService.RestCaller;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static server.Packet.AppPacket.PacketType.*;

public class MulticastServer
{
    private static DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private static final int PORT = 4446;
    private static final String GROUP_IP = "239.255.255.255";
    private Thread outgoing;
    private Thread incoming;
    private Thread heartbeat;
    private Thread timeoutThread;
    private ServerState serverState = ServerState.FOLLOWER;
    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final int serverId;
    private int leaderId;
    private int term;
    private int voteCount = 0;
    private Lock serverStateLock = new ReentrantLock();
    private Lock timeoutLock = new ReentrantLock();
    private String outgoingData;
    private long groupCount = 5;
    private int lastVotedElection = 0;
    private int timeout;
    private long startTime;
    private Random rand = new Random();
    private final Map<Integer, String> fakeDB = new HashMap<Integer, String>();

    private final Map<Integer, LeaderPacket> outgoingLocalStorage = new ConcurrentHashMap<Integer, LeaderPacket>();
    private final LinkedBlockingQueue<Pair<String, String>> linkedBlockingClientMessageQueue = new LinkedBlockingQueue<Pair<String, String>>();
    private final Map<Integer, Integer> followerStatusMap = new ConcurrentHashMap<Integer, Integer>();

    private JTextArea userConsole;
    private JScrollPane scrollpane;
    private JTextArea serverConsole;
    private JTextField userMessageInput;
    private JButton userMessageInputButton;
    private JButton serverStatusButton;
    private JButton serverKillButton;
    private JButton serverTimeoutButton;
    private boolean heartbeatDebug = false;
    private int latestLogIndex = 1;
    private boolean debugKill = false;
    private JFrame frame;
    private JComboBox<String> pidComboBox;


    public MulticastServer(int serverId, int leaderId, CountDownLatch latch, int x, int y) throws IOException
    {
        this.serverId = serverId;
        this.leaderId = leaderId;
        this.term = 0;


        if (serverId == leaderId) serverState = ServerState.LEADER;

        // Create Socket
        multicastSocket = new MulticastSocket(PORT);
        group = InetAddress.getByName(GROUP_IP);
        multicastSocket.joinGroup(group);


        try
        {
            latestLogIndex = RestCaller.getLatestIndexNumber(this);
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }

        launchGUI(latch, x, y);
    }

    public void init()
    {
        outgoing = startSendingThread();
        incoming = startReceivingThread();
        heartbeat = startHeartbeatThread();
        if (!this.isLeader())
        {
            timeoutThread = startTimeOutThread();
        }
    }

    private void launchGUI(CountDownLatch latch, int x, int y)
    {
        //1. Create the frame.
        frame = new JFrame();
        frame.setSize(925, 500);

        //2. Optional: What happens when the frame closes?
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //construct the user's UI
        JPanel userConsolePanel = constructUserConsolePanel();
        //construct the server's UI
        JPanel serverConsolePanel = constructServerConsolePanel();

        //creae the scroll panel
        JPanel scrollPanePanel = new JPanel();
        scrollPanePanel.setSize(500, 500);
        scrollPanePanel.setLayout(new BorderLayout());

        //add the user UI to the scroll panel
        scrollPanePanel.add(userConsolePanel, BorderLayout.WEST);
        //add the sever UI to the scroll panel
        scrollPanePanel.add(serverConsolePanel, BorderLayout.EAST);
        //create the scroll panel with the scroll panel
//        scrollpane = new JScrollPane(scrollPanePanel);
        //add the scroll pane to the frame's content pane.
        frame.getContentPane().add(scrollPanePanel, BorderLayout.CENTER);
        frame.setLocation(x, y);

        //show the frame
        frame.setVisible(true);
        //let the other servers start
        updateGUITitle();
        latch.countDown();
    }

    private JPanel constructUserConsolePanel()
    {
        userConsole = new JTextArea();
        userConsole.setSize(500, 500);
        userConsole.setLineWrap(true);
        userConsole.setWrapStyleWord(true);
        userConsole.setEditable(false);

        userMessageInput = new JTextField(35);

        userMessageInputButton = new JButton("Send");
        userMessageInputButton.setSize(50, 100);

        userMessageInput.addKeyListener(new KeyAdapter()
        {
            public boolean waitingForClientMessageToSend;

            @Override
            public void keyTyped(KeyEvent keyEvent)
            {
                super.keyTyped(keyEvent);
                //the enter key returns a '\n' new line char
                if (keyEvent.getKeyChar() == '\n')
                {
                    userMessageInputButton.doClick();
                }
            }
        });

        userMessageInputButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                userMessageInput.setEditable(false);
                String textFromGUI = userMessageInput.getText();
                String pidFromGUI = (String) pidComboBox.getSelectedItem();
                addClientMessage(pidFromGUI, textFromGUI);
                consoleMessage(textFromGUI, 1);
                userMessageInput.setText("");
                userMessageInput.setEditable(true);
            }
        });

        userMessageInputButton.addMouseListener(new MouseListener()
        {
            @Override
            public void mouseClicked(MouseEvent mouseEvent)
            {
                userMessageInputButton.doClick();
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {

            }
        });

        //add a listener to the userConsole's document to know when text has been added to it
        userConsole.getDocument().addDocumentListener(new DocumentListener()
        {

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                //System.out.println("REMOVE UPDATE");
            }

            @Override
            public void insertUpdate(DocumentEvent e)
            {
                //System.out.println("INSERT UPDATE " + e);
               /* if(e.toString().contains("javax.swing.text.AbstractDocument")) {
                    consolePrompt("What do you want to do: ");
                }*/
                userConsole.setCaretPosition(userConsole.getDocument().getLength());
                //scrollToBottom(scrollpane);
            }

            @Override
            public void changedUpdate(DocumentEvent arg0)
            {
                //System.out.println("CHANGE UPDATE");
            }
        });

        final JButton sendPhoto = new JButton(("Send Photo"));
        sendPhoto.setSize(50, 100);
        sendPhoto.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JFileChooser chooser = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter(
                        "JPG & GIF Images", "jpg", "gif", "png");
                chooser.setFileFilter(filter);
                int returnVal = chooser.showOpenDialog(sendPhoto);
                if (returnVal == JFileChooser.APPROVE_OPTION)
                {
                    photoSend(convertPicture(chooser.getSelectedFile().getAbsolutePath()));
                }
            }
        });

        JPanel userConsolePanel = new JPanel();
        userConsolePanel.setSize(500, 500);
        userConsolePanel.setLayout(new BorderLayout());
        userConsolePanel.add(new JLabel("User Console"), BorderLayout.NORTH);

        JPanel userControlsPanel = new JPanel();
        userControlsPanel.setSize(500, 500);
        userControlsPanel.setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new BorderLayout());
        try
        {
            pidComboBox = new JComboBox<String>(RestCaller.getAllPid(this));
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        inputPanel.add(pidComboBox, BorderLayout.WEST);
        JPanel innerInputPanel = new JPanel(new BorderLayout());

        innerInputPanel.add(userMessageInput, BorderLayout.WEST);
        innerInputPanel.add(userMessageInputButton, BorderLayout.EAST);
        innerInputPanel.add(sendPhoto, BorderLayout.AFTER_LAST_LINE);

        inputPanel.add(innerInputPanel, BorderLayout.EAST);


        userControlsPanel.add(inputPanel, BorderLayout.EAST);

        userConsolePanel.add(new JScrollPane(userConsole), BorderLayout.CENTER);
        userConsolePanel.add(userControlsPanel, BorderLayout.SOUTH);

        return userConsolePanel;
    }

    private JPanel constructServerConsolePanel()
    {
        serverConsole = new JTextArea();
        serverConsole.setSize(400, 500);
        serverConsole.setLineWrap(true);
        serverConsole.setWrapStyleWord(true);
        serverConsole.setEditable(false);

        serverStatusButton = new JButton("Status");
        serverStatusButton.setSize(50, 100);

        serverStatusButton.addMouseListener(new MouseListener()
        {
            @Override
            public void mouseClicked(MouseEvent mouseEvent)
            {
                consoleMessage("Show Status", 2);
                debugStatus();
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {

            }
        });
        serverTimeoutButton = new JButton("Timeout");
        serverTimeoutButton.setSize(50, 100);

        serverTimeoutButton.addMouseListener(new MouseListener()
        {
            @Override
            public void mouseClicked(MouseEvent mouseEvent)
            {
                consoleMessage("Timeout Server", 2);
                debugTimeout();
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {

            }
        });

        //add a listener to the userConsole's document to know when text has been added to it
        serverConsole.getDocument().addDocumentListener(new DocumentListener()
        {

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                //System.out.println("REMOVE UPDATE");
            }

            @Override
            public void insertUpdate(DocumentEvent e)
            {
                //System.out.println("INSERT UPDATE " + e);
               /* if(e.toString().contains("javax.swing.text.AbstractDocument")) {
                    consolePrompt("What do you want to do: ");
                }*/
//                serverConsole.setCaretPosition(userConsole.getDocument().getLength());
//                scrollToBottom(scrollpane);
            }

            @Override
            public void changedUpdate(DocumentEvent arg0)
            {
                //System.out.println("CHANGE UPDATE");
            }
        });

        final JButton heartbeatButton = new JButton("heartBeat " + heartbeatDebug);
        heartbeatButton.setSize(50, 100);
        heartbeatButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                heartbeatDebug = !heartbeatDebug;
                heartbeatButton.setText("heartbeat " + heartbeatDebug);
            }
        });

        serverKillButton = new JButton("Kill");
        serverKillButton.setSize(50, 100);
        serverKillButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    if (serverKillButton.getText().equals("Kill"))
                    {
                        debugKill = true;
                        serverKillButton.setText("Restart");
                        multicastSocket.leaveGroup(group);
                        updateGUITitle();

                    }
                    else
                    {
                        debugKill = false;
                        multicastSocket.joinGroup(group);
                        serverKillButton.setText("Kill");
                        outgoing = startSendingThread();
                        incoming = startReceivingThread();
                        heartbeat = startHeartbeatThread();
                        if (!serverState.equals(ServerState.LEADER))
                            timeoutThread = startTimeOutThread();
                    }
                }
                catch (IOException e1)
                {
                    e1.printStackTrace();
                }
            }
        });

        JButton deleteButton = new JButton("Delete");
        deleteButton.setSize(50, 100);
        deleteButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                deleteAllFromDB();
            }
        });


        JPanel serverControlsPanel = new JPanel();
        serverControlsPanel.setSize(500, 500);
        serverControlsPanel.setLayout(new BorderLayout());

        JPanel westControlsPanel = new JPanel();
        westControlsPanel.setSize(250, 500);
        westControlsPanel.setLayout(new BorderLayout());

        JPanel centralControlsPanel = new JPanel();
        centralControlsPanel.setSize(250, 500);
        centralControlsPanel.setLayout(new BorderLayout());

        westControlsPanel.add(serverStatusButton, BorderLayout.WEST);
        centralControlsPanel.add(serverTimeoutButton, BorderLayout.BEFORE_FIRST_LINE);

        centralControlsPanel.add(deleteButton, BorderLayout.WEST);
        centralControlsPanel.add(heartbeatButton, BorderLayout.CENTER);
        centralControlsPanel.add(serverKillButton, BorderLayout.AFTER_LAST_LINE);

        serverControlsPanel.add(westControlsPanel, BorderLayout.WEST);
        serverControlsPanel.add(centralControlsPanel, BorderLayout.CENTER);

        JPanel serverConsolePanel = new JPanel();
        serverConsolePanel.setSize(500, 500);
        serverConsolePanel.setLayout(new BorderLayout());
        serverConsolePanel.add(new JLabel("Server " + serverId + " Console"), BorderLayout.NORTH);

        serverConsolePanel.add(new JScrollPane(serverConsole), BorderLayout.CENTER);
        serverConsolePanel.add(serverControlsPanel, BorderLayout.SOUTH);
        return serverConsolePanel;
    }

    private void deleteAllFromDB()
    {
        try
        {
            RestCaller.deleteAll(this);
            latestLogIndex = 0;
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void photoSend(String base)
    {
        try
        {
            AppPacket outgoingPacket = new AppPacket(getId(), AppPacket.PacketType.PICTURE, getLeaderId(), getTerm(), -1, LeaderPacket.getNextSequenceNumber(), -1, PICTURE.ordinal(), base);
            getOutgoingLocalStorage().put(outgoingPacket.getSequenceNumber(), new LeaderPacket(outgoingPacket));
            consoleMessage("Photo is being sent from leader", 1);
            getMulticastSocket().send(outgoingPacket.getDatagram(getGroup(), getPort()));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    public String convertPicture(String fileName)
    {
        //35kb cap
        String encodedFile = "";
        try
        {
            File f = new File(fileName);
            byte[] fileBytes = loadFile(f);
            byte[] encoded = Base64.encodeBase64(fileBytes);
            encodedFile = new String(encoded);
            return encodedFile;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return encodedFile;
    }

    private static byte[] loadFile(File file) throws IOException
    {
        InputStream is = new FileInputStream(file);

        long length = file.length();
        if (length > Integer.MAX_VALUE)
        {
            // File is too large
        }
        byte[] bytes = new byte[(int) length];

        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0)
        {
            offset += numRead;
        }

        if (offset < bytes.length)
        {
            throw new IOException("Could not completely read file " + file.getName());
        }

        is.close();
        return bytes;
    }

    private void scrollToBottom(JScrollPane scrollPane)
    {
        final JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
        AdjustmentListener downScroller = new AdjustmentListener()
        {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e)
            {
                Adjustable adjustable = e.getAdjustable();
                adjustable.setValue(adjustable.getMaximum());
                verticalBar.removeAdjustmentListener(this);
            }
        };
        verticalBar.addAdjustmentListener(downScroller);
    }

    private void addClientMessage(String pID, String textFromGUI)
    {
        //System.out.println("ADDING CM " + textFromGUI);
        try
        {
            linkedBlockingClientMessageQueue.put(new Pair<String, String>(pID, textFromGUI));
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        //System.out.println("ADDED CM " + textFromGUI);
    }

    private Thread startHeartbeatThread()
    {
        Thread heartbeat = new Thread(new MulticastHeartbeatSender(this));
        heartbeat.start();
        consoleMessage("started heartbeat thread", 2);
        return heartbeat;
    }

    private Thread startSendingThread()
    {

        Thread outgoing = new Thread(new MulticastServerSender(this));
        outgoing.start();
        consoleMessage("started outgoing thread", 2);
        return outgoing;
    }

    private Thread startReceivingThread()
    {

        Thread incoming = new Thread(new MulticastServerReceiver(this));
        incoming.start();
        consoleMessage("started incoming thread", 2);
        return incoming;
    }

    private Thread startTimeOutThread()
    {
        Thread timeOut = new Thread(new TimeoutThread(this));
        timeOut.start();
        consoleMessage("started timeout thread", 2);
        return timeOut;
    }

    private void debugStatus()
    {
        try
        {
            consoleMessage("\n----------------------------- START SERVER STATUS ---------------------------\n", 2);
            consoleMessage("\tstate = " + serverState, 2);
            consoleMessage("\tid = " + serverId, 2);
            consoleMessage("\tleaderId = " + leaderId, 2);
            consoleMessage("\tterm = " + term, 2);
            consoleMessage("\tContents of FakeDB", 2);

            consoleMessage("\n\t------------------- Start Server DB Logs -------------------", 2);
            for (String current : RestCaller.getAllLogs(this))
            {
                consoleMessage(current, 2);
            }
            consoleMessage("\n\t------------------- END Server DB Logs -------------------", 2);
            consoleMessage("\n----------------------------- END SERVER STATUS ---------------------------\n", 2);
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void debugTimeout()
    {
        //changeServerState(ServerState.CANIDATE);
        resetTimeout(0);
    }

    public int getMajority()
    {
        return (int) Math.floor(groupCount / 2);
    }

    public ServerState getServerState()
    {
        serverStateLock.lock();
        ServerState state = serverState;
        serverStateLock.unlock();
        return state;
    }

    public boolean isFollower()
    {
        return getServerState().equals(ServerState.FOLLOWER);
    }

    public boolean isLeader()
    {
        return getServerState().equals(ServerState.LEADER);
    }

    public boolean isCandidate()
    {
        return getServerState().equals(ServerState.CANIDATE);
    }

    public Map<String, AppPacket> getIncomingLocalStorage()
    {
        return incomingLocalStorage;
    }

    private final Map<String, AppPacket> incomingLocalStorage = new ConcurrentHashMap<String, AppPacket>();

    public Map<Integer, LeaderPacket> getOutgoingLocalStorage()
    {
        return outgoingLocalStorage;
    }


    /**
     * Used for servers who are receiving packets from the leader.
     * This is a follower of the leader.
     * <p/>
     * It receives commit requests and commands from leading servers, in that order,
     * acks to confirm its agreement that the sender is the leader of the group,
     * and if so, it replicates the leader's incoming log to its own db when it receives the commit command from leader.
     */
    public void followerParse(AppPacket receivedPacket)
    {
        if (receivedPacket.getTerm() < term)
        {
            return;
        }
        try
        {
            // make sure the packet is from the leader
            if (receivedPacket.getServerId() == leaderId)
            {
                //consoleMessage("Received Valid Packet", 2);
                resetTimeout();
                if (receivedPacket.getTerm() > term)
                {
                    term = (int) receivedPacket.getTerm();
                }
                switch (receivedPacket.getType())
                {
                    case ACK:
                        consoleError("SHOULDNT SEE THIS", 2);
                        break;
                    case COMMENT:
                        AppPacket ackPacket = new AppPacket(serverId, ACK, leaderId, term, groupCount, receivedPacket.getSequenceNumber(), receivedPacket.getLogIndex(), ACK.ordinal(), "");
                        incomingLocalStorage.put(getIncomingStorageKey(receivedPacket), receivedPacket);
                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
                        consoleMessage("Acking commit request confirmation for " + receivedPacket.toString(), 2);
                        break;
                    case COMMIT:
                        AppPacket localPacketFromIncomingStorage = incomingLocalStorage.get(getIncomingStorageKey(receivedPacket));
                        String receivedLogIndex = receivedPacket.getLogIndex() + "";
                        String actualDataFromIncomingStorage = localPacketFromIncomingStorage.getReadableData();
                        if (AppPacket.PacketType.fromInt(receivedPacket.getDataType()).equals(COMMENT))
                        {
                            int split = actualDataFromIncomingStorage.indexOf(" ");
                            String pid = actualDataFromIncomingStorage.substring(0, split + 1);
                            actualDataFromIncomingStorage = actualDataFromIncomingStorage.substring(split + 1, actualDataFromIncomingStorage.length());
                            RestCaller.postLog(this, receivedLogIndex, localPacketFromIncomingStorage.getType(), actualDataFromIncomingStorage, pid,false);
                            consoleMessage("Committed Packet: #%s" + pid + " " + localPacketFromIncomingStorage.toString(), 2);
                        }
                        else if (AppPacket.PacketType.fromInt(receivedPacket.getDataType()).equals(PICTURE))
                        {
                            int pid = RestCaller.postLog(this, receivedLogIndex, localPacketFromIncomingStorage.getType(), receivedPacket.getReadableData(),false).getKey();
                            pidComboBox.addItem(pid + "");
                            consoleMessage("Committed Packet: #%s" + receivedPacket.getReadableData(), 2);
                        }
                        else
                        {
                            consoleError("big issue type was :" + AppPacket.PacketType.fromInt(receivedPacket.getDataType()), 1);
                        }
                        latestLogIndex = receivedPacket.getLogIndex();
                        break;
                    case HEARTBEAT:
                        parseHeartbeat(receivedPacket);
                        break;
                    case PICTURE:
                        AppPacket ack = new AppPacket(serverId, ACK, leaderId, term, groupCount, receivedPacket.getSequenceNumber(), receivedPacket.getLogIndex(), ACK.ordinal(), "");
                        incomingLocalStorage.put(getIncomingStorageKey(receivedPacket), receivedPacket);
                        multicastSocket.send(ack.getDatagram(group, PORT));
                        consoleMessage("Acking commit request confirmation for " + receivedPacket.toString() + " (Type photo)", 2);
                        break;
                }
            }
            else
            {
                switch (receivedPacket.getType())
                {
                    case VOTE_REQUEST:
                    {
                        consoleMessage("Vote Request has been recieved from server " + receivedPacket
                                .getServerId() + " for term " + receivedPacket.getTerm(), 2);
                        if (receivedPacket.getTerm() > term && lastVotedElection < receivedPacket.getTerm())
                        {
                            resetTimeout();
                            leaderId = -1;
                            lastVotedElection = (int) receivedPacket.getTerm();
                            AppPacket votePacket = new AppPacket(serverId, AppPacket.PacketType.VOTE,
                                    leaderId, lastVotedElection, groupCount, 0, 0, AppPacket.PacketType.VOTE.ordinal(), Integer.toString(receivedPacket.getServerId()));
                            multicastSocket.send(votePacket.getDatagram(group, PORT));
                            consoleMessage("voting in term " + term + " for server " + receivedPacket
                                    .getServerId(), 2);
                        }
                        break;
                    }
                    case VOTE:
                    {
                        //IGNORE ALL VOTES
                        break;

                    }
                    default:
                    {

                        if (receivedPacket.getTerm() == term)
                        {
                            //consoleMessage("Packet Type: " + receivedPacket.getType(),2);
                            //consoleMessage("Received a Non-VoteRequest Packet w/ term = our current term. Term Recieved: " + receivedPacket.getTerm() + " || Recieved from server: " + receivedPacket.getServerId(),2);
                        }
                        term = (int) receivedPacket.getTerm();

                        break;
                    }
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void candidateParse(AppPacket receivedPacket)
    {
        if (receivedPacket.getTerm() < term)
        {
            return;
        }
        switch (receivedPacket.getType())
        {
            case VOTE:
            {
                /* Only accepts votes from the current term that are directed at this server*/
                String data = new String(receivedPacket.getData());
                data = data.trim();
                consoleMessage("Vote recieved from server: " + receivedPacket.getServerId() + " for server: " +
                        data, 2);
                if (receivedPacket.getTerm() == term && serverId == Integer.parseInt(data))
                {
                    consoleMessage("vote accepted for term: " + term, 2);
                    voteCount++;
                    consoleMessage("Current Vote count: " + voteCount, 2);
                    if (voteCount >= getMajority() + 1)
                    {
                        consoleMessage("Majority vote: " + voteCount, 2);
                        consoleMessage("Election won", 2);
                        changeServerState(ServerState.LEADER);
                    }
                }
                break;
            }
            case HEARTBEAT:
            case COMMENT:
            case PICTURE:
            case GPS:
            case COMMIT:
            {
                if (receivedPacket.getTerm() >= term)
                {
                    // a new leader has been elected; defer to that leader
                    consoleMessage("Higher or equal term detected: switching to follower state", 2);
                    changeServerState(ServerState.FOLLOWER);
                    followerParse(receivedPacket);
                }
                break;
            }
        }
    }

    private void parseHeartbeat(AppPacket receivedPacket) throws IOException
    {

        try
        {
            if (!receivedPacket.getData().equals(""))
            {
                if (receivedPacket.getLogIndex() == latestLogIndex + 1)
                {

                    latestLogIndex = receivedPacket.getLogIndex();
                    if (AppPacket.PacketType.fromInt(receivedPacket.getDataType()).equals(COMMENT))
                    {
                        int split = receivedPacket.getReadableData().indexOf(" ");
                        String pid = receivedPacket.getReadableData().substring(0, split + 1);
                        String comment = receivedPacket.getReadableData().substring(split + 1, receivedPacket.getReadableData().length());
                        RestCaller.postLog(this, latestLogIndex + "", AppPacket.PacketType.fromInt(receivedPacket.getDataType()), comment, pid,false);
                    }
                    else if (AppPacket.PacketType.fromInt(receivedPacket.getDataType()).equals(PICTURE))
                    {
                        RestCaller.postLog(this, latestLogIndex + "", AppPacket.PacketType.fromInt(receivedPacket.getDataType()), receivedPacket.getReadableData(),false);
                    }
                }
                else if (receivedPacket.getHighest() < latestLogIndex)
                {
                    latestLogIndex = (int) receivedPacket.getHighest();
                    RestCaller.rollBack(this);
                    updateComboBox();
                }
                System.out.println(serverId + " receivedPacket.getReadableData() = " + receivedPacket.getReadableData());
                AppPacket heartbeatAckPacket = new AppPacket(serverId, AppPacket.PacketType.HEARTBEAT_ACK, leaderId, term, groupCount, -1, latestLogIndex, HEARTBEAT_ACK.ordinal(), latestLogIndex + "");

                if (heartbeatDebug)
                {
                    consoleMessage("Send HeartbeatAck: with latest index " + getLatestLogIndex(), 2);
                }
                multicastSocket.send(heartbeatAckPacket.getDatagram(group, PORT));
            }
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
    }

    private void updateComboBox()
    {
        try
        {
            pidComboBox.removeAllItems();
            String[] pids = RestCaller.getAllPid(this);
            for (int i = 0; i < pids.length; i++)
            {
                pidComboBox.addItem(pids[i]);
            }
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    private String getIncomingStorageKey(AppPacket receivedPacket)
    {
        return receivedPacket.getLeaderId() + " " + receivedPacket.getSequenceNumber() + " " + receivedPacket.getTerm();
    }


    /**
     * @param receivedPacket
     */
    public void leaderParse(AppPacket receivedPacket)
    {
        try
        {
            System.out.println("leader parse " + serverId);
            System.out.println("leader parse " + receivedPacket.getReadableData());
            switch (receivedPacket.getType())
            {
                case ACK:
                    if (receivedPacket.getTerm() < term)
                    {
                        return;
                    }
                    LeaderPacket ackedLeaderPacket = outgoingLocalStorage.get(receivedPacket.getSequenceNumber());

                    Pair<Integer, String> data = ackedLeaderPacket.confirm(getMajority(), this);
                    //make sure the log index returned from committing is valid
                    if (data.getKey() > -1)
                    {
                        if (AppPacket.PacketType.fromInt(ackedLeaderPacket.getPacket().getDataType()).equals(PICTURE))
                        {
                            pidComboBox.addItem(data.getKey() + "");
                        }
                        consoleMessage("\nLeader Committed " + ackedLeaderPacket.toString() + "\n", 2);
                        latestLogIndex++;
                        //all is well. The log was committed to this leader's persistent db at the committedLogIndex.
                        //send the commit command to all followers if necessary.

                        //we send the current term number of the leader because if it doesn't match what the followers have this packet stored as, they should not commit it to their db
                        System.out.println("********************* data " + data.getKey());
                        System.out.println("********************* data " + data.getValue());
                        AppPacket commitPacket = new AppPacket(serverId, COMMIT, leaderId, term, groupCount, ackedLeaderPacket.getSequenceNumber(), data.getKey(), ackedLeaderPacket.getPacket().getDataType(), data.getValue());
                        if (term == ackedLeaderPacket.getTerm())
                        {
                            //send the commit command to all followers of this leader
                            multicastSocket.send(commitPacket.getDatagram(group, PORT));
                        }
                        else
                        {
                            //this leader is on the wrong term and thus may or may not be the leader anymore
                            consoleError("Leader is in the wrong term. Cant commit", 2);
                        }
                    }
                    break;

                case HEARTBEAT_ACK:
                    if (receivedPacket.getTerm() < term)
                    {
                        return;
                    }
                    followerStatusMap.put(receivedPacket.getServerId(), receivedPacket.getLogIndex());
                    if (heartbeatDebug)
                    {
                        consoleMessage("received HeartbeatAck from " + receivedPacket.getServerId() + " with latest log index of " + receivedPacket.getLogIndex(), 2);
                    }
                    break;
                case COMMENT:
                    AppPacket redirectPacket = new AppPacket(serverId, receivedPacket.getType(), leaderId, term, getLatestLogIndex(), -1, -1, COMMENT.ordinal(), receivedPacket.getReadableData());
                    getOutgoingLocalStorage().put(redirectPacket.getSequenceNumber(), new LeaderPacket(redirectPacket));

                    consoleMessage("Sending " + redirectPacket.toString(), 2);
                    getMulticastSocket().send(redirectPacket.getDatagram(getGroup(), getPort()));
                    clearOutgoingData();
                    break;
                case PICTURE:
                    AppPacket redirPacket = new AppPacket(serverId, receivedPacket.getType(), leaderId, term, getLatestLogIndex(), -1, -1, PICTURE.ordinal(), receivedPacket.getReadableData());
                    getOutgoingLocalStorage().put(redirPacket.getSequenceNumber(), new LeaderPacket(redirPacket));

                    consoleMessage("Sending Photo " + redirPacket.toString(), 2);
                    getMulticastSocket().send(redirPacket.getDatagram(getGroup(), getPort()));
                    clearOutgoingData();
                    break;
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
    }

    public void changeServerState(ServerState nextState)
    {
        // A candidate changing to a candidate indicates their candidacy failed and they are starting a new election
        if (nextState != ServerState.CANIDATE && nextState == getServerState())
        {
            return;
        }

        try
        {
            if (serverStateLock.tryLock(100, TimeUnit.MILLISECONDS))
            {
                consoleMessage("changing state to: " + nextState, 2);
                if (nextState == ServerState.LEADER)
                {
                    consoleError("Became Leader", 1);
                    leaderId = serverId;
                    timeoutThread = null;
                    heartbeat = startHeartbeatThread();
                    consoleError("Leader after heartbeat", 1);

                }
                else
                {
                    if (timeoutThread == null)
                    {
                        timeoutThread = startTimeOutThread();
                    }
                }
                if (nextState == ServerState.CANIDATE) // start new election
                {
                    consoleError("Became Candidate", 1);
                    voteCount = 1;
                    term++;
                    leaderId = -1;
                }
                else if (nextState == ServerState.FOLLOWER)
                {
                    consoleError("Became Follower", 1);
                    voteCount = 0;
                }
                serverState = nextState;
                updateGUITitle();
                serverStateLock.unlock();
            }
            else
            {
                consoleMessage("Thread locked, cannot change state", 2);
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public String getOutgoingData()
    {
        return outgoingData;
    }

    public int getLeaderId()
    {
        return leaderId;
    }

    public int getTerm()
    {
        return term;
    }

    public InetAddress getGroup()
    {
        return group;
    }

    public int getPort()
    {
        return PORT;
    }

    public int getId()
    {
        return serverId;
    }

    public int getTimeout()
    {
        return timeout;
    }

    public long getStartTime()
    {
        timeoutLock.lock();
        long start = startTime;
        timeoutLock.unlock();
        return start;
    }

    public MulticastSocket getMulticastSocket()
    {
        return multicastSocket;
    }

    public void clearOutgoingData()
    {
        outgoingData = "";
    }

    public Map<Integer, Integer> getFollowerStatusMap()
    {
        return followerStatusMap;
    }

    public boolean getHeartbeatDebug()
    {
        return heartbeatDebug;
    }

    public int getLatestLogIndex()
    {
        return latestLogIndex;
    }

    ;

    public boolean filterPacket(AppPacket packet)
    {
        if (packet.getServerId() == serverId) /* Filter packets from itself */
        {
            return false;
        }
        else if (packet.getTerm() < term) /* Packet from obsolete term */
        {
            return false;
        }
        else if (packet.getTerm() == term)
        {
            return true;
        }
        else /* Packet.term > termNum: A new term has begun.*/
        {
            if (leaderId == -1) /* We don't know the leader of the current term so we accept all packets by
                default */
            {
                return true;
            }
            return packet.getServerId() == packet.getLeaderId(); /* Accept packet if it is from the new leader */
        }
    }

    public void updateStateAndLeader(AppPacket packet)
    {
        if (packet.getTerm() > term) /* A new term has begun. Update leader and term fields accordingly */
        {

            consoleMessage("Received packet of higher term. Term num: " + packet.getTerm() + " From Server: " + packet.getServerId(), 2);
            leaderId = (int) packet.getLeaderId();
            changeServerState(ServerState.FOLLOWER);
            updateGUITitle();

        }
    }

    private void updateGUITitle()
    {
        consoleMessage(serverState.toString(), 1);
        if (debugKill)
        {
            frame.setTitle("*DEAD* Server #" + serverId + " | " + leaderId);
        }
        else if (serverState == ServerState.LEADER)
        {
            frame.setTitle("*Leader* Server #" + serverId + " | " + leaderId);
        }
        else
        {
            frame.setTitle("Server #" + serverId + " | " + leaderId);
        }
    }

    public void resetTimeout()
    {
        int timeout = rand.nextInt(TimeoutThread.MAX_TIMEOUT - TimeoutThread.MIN_TIMEOUT) + TimeoutThread.MIN_TIMEOUT;
//        consoleMessage("timeout = " + timeout, 1);
        resetTimeout(timeout);
    }

    private void resetTimeout(int timeout)
    {
        timeoutLock.lock();
        this.timeout = timeout;
        startTime = System.currentTimeMillis();
        timeoutLock.unlock();
    }


    public Pair<String, String> getClientMessageToSend()
    {
        try
        {
            Pair<String, String> take = linkedBlockingClientMessageQueue.take();
            return take;
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public boolean getDebugKill()
    {
        return debugKill;
    }

    public void clearFollowerStatusMap()
    {
        followerStatusMap.clear();
    }


    public enum ServerState
    {
        LEADER(),
        CANIDATE(),
        FOLLOWER();
    }

    public void consoleMessage(String s, int which)
    {
        if (userConsole != null && serverConsole != null)
        {
            if (s != null && !s.trim().isEmpty())
            {
                //m stands for message
                switch (which)
                {
                    case 1:
                        userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|m> " + s);
                        break;
                    case 2:
                        serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|m> " + s);
                        break;
                }
            }
        }
    }

    public void consoleError(String s, int which)
    {

        if (userConsole != null && serverConsole != null)
        {
            //e stands for error
            if (s != null && !s.trim().isEmpty())
            {
                switch (which)
                {
                    case 1:
                        userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|e> " + s);
                        break;
                    case 2:
                        serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|e> " + s);
                        break;
                }
            }
        }
    }

    protected void consolePrompt(String s, int which)
    {
        //p stands for prompt
        if (s != null && !s.trim().isEmpty())
        {
            switch (which)
            {
                case 1:
                    userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|p> " + s);
                    break;
                case 2:
                    serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|p> " + s);
                    break;
            }
        }
    }

    public static String getCurrentDateTime(DateFormat dateFormat)
    {
        dateFormat = dateFormat != null ? dateFormat : DEFAULT_DATE_FORMAT;
        return dateFormat.format(new Date());
    }
}
