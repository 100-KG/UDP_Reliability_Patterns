import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class server {
    private static void sendFile(DatagramSocket server, String filename, InetAddress address, int port) throws Exception{      
        File file = new File(filename);
        if(!file.exists()){
            String message = "File not found!";
            System.out.println("File not found: " + filename);
            byte[] errorBytes = message.getBytes();
            sendPacket(server, address, port, -1, errorBytes, false);
            return;
        }

        FileInputStream fis = new FileInputStream(file);
        byte[] fileBuf = new byte[1024];
        int seqNum = 0;
        
        while(true){
            int bytesRead = fis.read(fileBuf);
            if (bytesRead == -1){
                sendPacket(server, address, port, seqNum, new byte[0], true);
                break;
            }

            byte[] data = Arrays.copyOf(fileBuf, bytesRead);
            boolean sent = false;
            int retries = 0;

            while(!sent && retries < 5){
                sendPacket(server, address, port, seqNum, data, false);

                try {
                    server.setSoTimeout(5000);
                    byte[] ackBuf = new byte[4];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                    server.receive(ackPacket);

                    ByteBuffer bb = ByteBuffer.wrap(ackBuf);
                    int ackSeq = bb.getInt();
                    if(ackSeq == seqNum){
                        System.out.println("Received ACK for sequence " + seqNum);
                        sent = true;
                        seqNum++;
                    }
                } catch (SocketTimeoutException e){
                    System.out.println("Timeout for sequence " + seqNum + ", retrying...");
                    retries++;
                }
            }
            if(retries >= 5){
                System.out.println("Max retries reached for sequence: " + seqNum + ", terminated.");
                break;
            }
        }
        fis.close();
        server.setSoTimeout(0);
    }

    private static void sendPacket (DatagramSocket server, InetAddress address, int port, int sequenceNumber, byte[] data, boolean isLast) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(9 + data.length);
        buf.putInt(sequenceNumber);
        buf.putInt(computeChecksum(data));
        buf.put((byte) (isLast ? 1 : 0));
        buf.put(data);

        DatagramPacket packet = new DatagramPacket(buf.array(), buf.array().length, address, port);
        server.send(packet);
        System.out.println("Sent packet with sequence: " + sequenceNumber + (isLast ? " (last)" : ""));
    }

    // private static void sendIgnorePacket(DatagramSocket server, InetAddress address, int port, int sequenceNumber) throws Exception {
    //     ByteBuffer buf = ByteBuffer.allocate(9 + data.length);
    // }

    private static int computeChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF); // Treat byte as unsigned
        }
        return sum;
    }
    
    public static void main(String[] args) throws Exception{
        int port = 2207;
        DatagramSocket server = new DatagramSocket(port);
        System.out.println("UDP server is running on " + port);
        while(true){
            byte[] buf = new byte[1024];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            server.receive(dp);
            String filename = new String(dp.getData(),0, dp.getLength()).trim();
            if(filename != ""){
                System.out.println("Received request for file: " + filename);
                sendFile(server, filename, dp.getAddress(), dp.getPort());
            }
        }
    }
}