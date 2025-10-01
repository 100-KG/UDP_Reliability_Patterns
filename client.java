import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class client {
    private static void receiveFile(DatagramSocket client, String filename) throws Exception{
        FileOutputStream fos = new FileOutputStream("received_" + filename);
        HashMap<Integer, byte[]>  receivedPackets = new HashMap<>();
        int expectedSeq = 0;
        while(true){
            byte[] buf = new byte[1033];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            client.receive(dp);

            ByteBuffer bb = ByteBuffer.wrap(dp.getData(), 0, dp.getLength());
            int seqNum = bb.getInt();
            int checksum = bb.getInt();
            boolean isLast = bb.get() == 1;
            byte[] data = new byte[dp.getLength() - 9];
            bb.get(data);

            if (isLast && !receivedPackets.containsKey(expectedSeq)){
                System.out.println("File transfer complete.");
                break;
            }
            //check checksum
            if(computeChecksum(data) != checksum){
                System.out.println("Checksum wrong for sequence " + seqNum + ", ignoring packet.");
                continue;
            }
            //send ack
            sendACK(client, dp.getAddress(), dp.getPort(), seqNum);

            if(seqNum == -1){
                System.out.println("Error: " + new String(data));
                break;
            }   

            //store packets
            if(!receivedPackets.containsKey(seqNum)){
                receivedPackets.put(seqNum, data);
                System.out.println("Received packet with sequence: " + seqNum);
            }

            //write packets in order
            while(receivedPackets.containsKey(expectedSeq)){
                byte[] orderedData = receivedPackets.remove(expectedSeq);
                fos.write(orderedData);
                expectedSeq++;
            }

        }
        fos.close();
    }

    private static void sendACK(DatagramSocket client, InetAddress address, int port, int seqNum) throws Exception{
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(seqNum);
        DatagramPacket ACKdp = new DatagramPacket(buf.array(), buf.array().length, address, port);
        client.send(ACKdp);
        System.out.println("ACK packet sended with sequence: " + seqNum);
    }

    private static int computeChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum;
    }
    public static void main(String[] args) throws Exception {
        DatagramSocket client = new DatagramSocket();
        String filename = "text.txt";

        byte[] filenamebyte = filename.getBytes();
        DatagramPacket dp = new DatagramPacket(filenamebyte, filenamebyte.length, InetAddress.getByName("localhost"), 2207);
        client.send(dp);
        System.out.println("Waiting requested file: " + filename);

        receiveFile(client, filename);
        client.close();
    }
}
