import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;


public class BroadcastServer {
    private final InetAddress addr;
    private final int port;
    private Selector selector;
    private final Map<SocketChannel, List<byte[]>> dataMap;

    private final Map<SocketAddress,SocketChannel> user;
    private final Map<String,SocketAddress> userName;
    private final ByteBuffer buffer = ByteBuffer.allocate(8192);
    private final ByteBuffer newBuffer = ByteBuffer.allocate(8192);
    StringBuilder src;
    boolean flag=false;

    SocketAddress remoteAddr;
    int c=0;

    public BroadcastServer(InetAddress addr, int port) throws IOException {
        this.addr = addr;
        this.port = port;
        dataMap = new HashMap<SocketChannel,List<byte[]>>();
        user=new HashMap<SocketAddress,SocketChannel>();
        userName=new HashMap<String, SocketAddress>();
        startServer();
    }

    private void startServer() throws IOException {
        // create selector  and channel
        selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        // bind to port
        InetSocketAddress listenAddr = new InetSocketAddress(addr, port);
        serverChannel.socket().bind(listenAddr);
        log("Echo server ready. Ctrl-C to stop.");
        // processing
        while (true) {
            // wait for events
            selector.select();
            // wakeup to work on selected keys
            Iterator keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = (SelectionKey) keys.next();
                // this is necessary to prevent the same key from coming up
                // again the next time around.
                keys.remove();
                if (! key.isValid()) {
                    continue;
                }
                if (key.isAcceptable()) {
                    accept(key);
                }
                else if (key.isReadable()) {
                    read(key);
                }
                else if (key.isWritable()) {
                    write(key);
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        // write welcome message
        channel.write(ByteBuffer.wrap("Welcome, this is the broadcast chat server\r\n".getBytes("US-ASCII")));
        Socket socket = channel.socket();
        remoteAddr = socket.getRemoteSocketAddress();
        log("Connected to: " + remoteAddr);
        // register channel with selector for further IO
        dataMap.put(channel, new ArrayList<byte[]>());
        channel.register(selector, SelectionKey.OP_READ);
        user.put(remoteAddr,channel);
        flag=false;
        src=new StringBuilder();

    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel;
        channel=(SocketChannel) key.channel();
        int numRead = -1;
        try {
            numRead = channel.read(buffer);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        if (numRead == -1) {
            dataMap.remove(channel);
            Socket socket = channel.socket();
            SocketAddress remoteAddr = socket.getRemoteSocketAddress();
            log("Connection closed by client: " + remoteAddr);

            wentOffline(remoteAddr,channel);
            channel.close();
            key.cancel();
            return;
        }
        CharBuffer charBuffer = buffer.asCharBuffer();
        buffer.flip();
        while( buffer.hasRemaining() ) {
            char c = (char) buffer.get();
            if (c == ':') {
                flag = true;
            }
            else if (c != ':'  && flag == false) {
                src.append(c);
            }
            else if(flag==true){
                newBuffer.putChar( c );
            }
            if (c == '\n') {
                log(src.toString());
                if(!userName.containsKey(src.toString())) {
                    userName.put(src.toString(), remoteAddr);
                }
                byte[] data = new byte[newBuffer.capacity() - newBuffer.remaining()];
                System.arraycopy(newBuffer.array(), 0, data, 0, newBuffer.capacity() - newBuffer.remaining());
                doEcho(key, data);
                newBuffer.clear();
            }
        }
        byte[] data = new byte[numRead];
        System.arraycopy(buffer.array(), 0, data, 0, numRead);
        buffer.clear();
    }
    private void wentOffline(SocketAddress remoteAddr,SocketChannel channel) throws IOException {
        System.out.println(user);
        String username="";
        for (Map.Entry<String,SocketAddress > entry : userName.entrySet()) {
            if (entry.getValue().equals(remoteAddr)) {
                 username=entry.getKey();
                 break;
            }
        }
        user.remove(remoteAddr);
        String status=username+ "  went offline!!!";
        for (Map.Entry<SocketAddress,SocketChannel> entry : user.entrySet()) {
            SocketChannel channel1=entry.getValue();
            channel1.write(ByteBuffer.wrap(status.getBytes()));
        }
        src=new StringBuilder();
        flag=false;
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel channel;
        Date d2=new Date();
        channel=user.get(userName.get(src.toString()));
        List<byte[]> pendingData = dataMap.get(channel);
        Iterator<byte[]> items = pendingData.iterator();
        String sname=d2.toString()+" - "+ src.toString()+":";
        System.out.println(userName);
        while (items.hasNext()) {
            byte[] item = items.next();
            items.remove();
            for (Map.Entry<SocketAddress,SocketChannel> entry : user.entrySet()) {
                SocketChannel channel1=entry.getValue();
                channel1.write(ByteBuffer.wrap(sname.getBytes()));
                channel1.write(ByteBuffer.wrap(item));
            }
        }
        channel.register(selector, SelectionKey.OP_READ);
        src=new StringBuilder();
        flag=false;
    }

    private void doEcho(SelectionKey key, byte[] data) throws IOException {
        SocketChannel channel;//(SocketChannel) key.channel();
        channel=user.get(userName.get(src.toString()));
        List<byte[]> pendingData = dataMap.get(channel);
        System.out.println(data.length);
        pendingData.add(data);
        channel.register(selector, SelectionKey.OP_WRITE);
    }

    private static <E> void log(E  s) {
        System.out.println(s);
    }

    public static void main(String[] args) throws Exception {
        new BroadcastServer(null, 12345);
    }
}
//client can be run through telnet command ----- command is telnet ip port