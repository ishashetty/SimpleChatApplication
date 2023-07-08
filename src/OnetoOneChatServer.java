import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.util.*;

public class OnetoOneChatServer {
    private final InetAddress addr;
    private final int port;
    private Selector selector;
    private final Map<SocketChannel, List<byte[]>> dataMap;
    private final Map<SocketAddress,SocketChannel> user;
    private final Map<String,SocketChannel> userdet;
    private final Map<String,SocketAddress> userName;
    private final ByteBuffer buffer = ByteBuffer.allocate(8192);
    private final ByteBuffer newBuffer = ByteBuffer.allocate(8192);
    StringBuilder dest;
    StringBuilder src;
    boolean hyphen=false,flag=false;
    SocketAddress remoteAddr;
    int c=0;
//    private  SocketChannel channel;

    public OnetoOneChatServer(InetAddress addr, int port) throws IOException {
        this.addr = addr;
        this.port = port;
        dataMap = new HashMap<SocketChannel,List<byte[]>>();
        user=new HashMap<SocketAddress,SocketChannel>();
        userName=new HashMap<String, SocketAddress>();
        userdet=new HashMap<String,SocketChannel>();
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
        channel.write(ByteBuffer.wrap("Welcome, this is the chat server\r\n".getBytes("US-ASCII")));
        Socket socket = channel.socket();
        remoteAddr = socket.getRemoteSocketAddress();
        log("Connected to: " + remoteAddr);
        // register channel with selector for further IO
        dataMap.put(channel, new ArrayList<byte[]>());
        channel.register(selector, SelectionKey.OP_READ);
        user.put(remoteAddr,channel);
        log(channel.toString());
        hyphen=false;
        flag=false;
        src=new StringBuilder();
        dest=new StringBuilder();

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
            else if(c=='-') {
                hyphen = true;
            }
            else if (c != ':' && flag == false && hyphen==true) {
                dest.append(c);
            }
            else if (c != '-'  && flag == false && hyphen==false) {
              //  newBuffer.putChar(c);
                src.append(c);
            }
            else if(flag==true && hyphen==true){
                newBuffer.putChar( c );
            }
             if (c == '\n') {
                log(src.toString()+" "+dest.toString());

                if(!userdet.containsKey(src.toString())) {
//                    userName.put(src.toString(), remoteAddr);
                    userdet.put(src.toString(),channel);
                }
                byte[] data = new byte[newBuffer.capacity() - newBuffer.remaining()];
                System.arraycopy(newBuffer.array(), 0, data, 0, newBuffer.capacity() - newBuffer.remaining());
                doEcho(key, data);
                newBuffer.clear();
            }
        }
        //log(dest)
        byte[] data = new byte[numRead];
        System.arraycopy(buffer.array(), 0, data, 0, numRead);
        buffer.clear();
    }

    private void write(SelectionKey key) throws IOException {
        log(dest.toString());
        System.out.println(userName);
        SocketChannel channel;
        if(userdet.containsKey(dest.toString())){
            channel=userdet.get(dest.toString());
        }
        else
            channel=(SocketChannel)key.channel();
         Date d2=new Date();
        List<byte[]> pendingData = dataMap.get(channel);
        Iterator<byte[]> items = pendingData.iterator();
        String sname=d2.toString()+" - "+src.toString()+"--";
        channel.write(ByteBuffer.wrap(sname.getBytes()));
        while (items.hasNext()) {
            byte[] item = items.next();
            items.remove();
            channel.write(ByteBuffer.wrap(item));
        }
        channel.register(selector, SelectionKey.OP_READ);
        hyphen=false;
        flag=false;
        src=new StringBuilder();
        dest=new StringBuilder();
        //key.interestOps(SelectionKey.OP_READ);
    }

    private void doEcho(SelectionKey key, byte[] data) throws IOException {
        SocketChannel channel;//(
        if(userdet.containsKey(dest.toString())) {
            channel=userdet.get(dest.toString());
        }
        else channel=(SocketChannel) key.channel();
        List<byte[]> pendingData = dataMap.get(channel);
        pendingData.add(data);
        channel.register(selector, SelectionKey.OP_WRITE);
        dest=new StringBuilder();
        //key.interestOps(SelectionKey.OP_WRITE);
    }

    private static void log(String s) {
        System.out.println(s);
    }

    public static void main(String[] args) throws Exception {
        new OnetoOneChatServer(null, 12345);
    }
}
//client can be run through telnet command ----- command is telnet ip port